package com.pro.dslrcamera.camera

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.RggbChannelVector
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Range
import android.util.Size
import android.view.Surface
import androidx.compose.ui.geometry.Offset
import com.pro.dslrcamera.model.*
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class Camera2Controller(
    private val context: Context,
    private val onPhotoSaved: (Uri, Bitmap) -> Unit
) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewSurface: Surface? = null
    private var jpegReader: ImageReader? = null
    private var rawReader: ImageReader? = null

    private var cameraId: String = "0"
    private lateinit var characteristics: CameraCharacteristics
    private var sensorArraySize: Rect = Rect(0, 0, 4000, 3000)
    private var minFocusDistance: Float = 10f
    private var isoRange: Range<Int> = Range(100, 6400)
    private var exposureRange: Range<Long> = Range(100_000L, 30_000_000_000L)
    private var maxZoom: Float = 8.0f

    private var latestRawImage: Image? = null
    private var latestCaptureResult: TotalCaptureResult? = null

    private val bgThread = HandlerThread("Camera2Bg").apply { start() }
    private val bgHandler = Handler(bgThread.looper)
    private val executor = Executors.newSingleThreadExecutor()

    var currentState = CameraUiState()

    @SuppressLint("MissingPermission")
    fun openCamera(surfaceTexture: SurfaceTexture, isFront: Boolean = false) {
        closeCamera()
        cameraId = selectCameraId(isFront)
        characteristics = cameraManager.getCameraCharacteristics(cameraId)
        sensorArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            ?: Rect(0, 0, 4000, 3000)
        minFocusDistance = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 10f
        isoRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) ?: Range(100, 6400)
        exposureRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE) ?: Range(100_000L, 30_000_000_000L)
        maxZoom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.upper ?: 8f
        } else {
            characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 8f
        }

        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        val previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture::class.java), 1920, 1080)
        surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
        previewSurface = Surface(surfaceTexture)

        val jpegSize = map.getOutputSizes(ImageFormat.JPEG).maxByOrNull { it.width * it.height } ?: Size(4000, 3000)
        jpegReader = ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, 2).apply {
            setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                processCapturedJpeg(image)
            }, bgHandler)
        }

        val rawSizes = map.getOutputSizes(ImageFormat.RAW_SENSOR)
        if (!rawSizes.isNullOrEmpty()) {
            val rawSize = rawSizes.maxByOrNull { it.width * it.height }!!
            rawReader = ImageReader.newInstance(rawSize.width, rawSize.height, ImageFormat.RAW_SENSOR, 2).apply {
                setOnImageAvailableListener({ reader ->
                    latestRawImage?.close()
                    latestRawImage = reader.acquireLatestImage()
                    tryWriteDngIfReady()
                }, bgHandler)
            }
        }

        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                createCaptureSession()
            }
            override fun onDisconnected(camera: CameraDevice) { camera.close() }
            override fun onError(camera: CameraDevice, error: Int) { camera.close() }
        }, bgHandler)
    }

    private fun createCaptureSession() {
        val device = cameraDevice ?: return
        val pSurface = previewSurface ?: return
        val outputs = mutableListOf(OutputConfiguration(pSurface), OutputConfiguration(jpegReader!!.surface))
        rawReader?.let { outputs.add(OutputConfiguration(it.surface)) }

        val sessionConfig = SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR,
            outputs,
            executor,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    updateRepeatingPreview()
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {}
            }
        )
        device.createCaptureSession(sessionConfig)
    }

    fun applyState(newState: CameraUiState) {
        val cameraChanged = newState.isFrontCamera != currentState.isFrontCamera
        currentState = newState
        if (!cameraChanged) {
            updateRepeatingPreview()
        }
    }

    fun updateRepeatingPreview() {
        val device = cameraDevice ?: return
        val session = captureSession ?: return
        val pSurface = previewSurface ?: return

        try {
            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(pSurface)
                applyManualParameters(this, isCapture = false)
            }
            session.setRepeatingRequest(builder.build(), null, bgHandler)
        } catch (_: Exception) {}
    }

    private fun applyManualParameters(builder: CaptureRequest.Builder, isCapture: Boolean) {
        val state = currentState

        if (state.isFlashOn && !state.isFrontCamera) {
            builder.set(CaptureRequest.FLASH_MODE, if (isCapture) CaptureRequest.FLASH_MODE_SINGLE else CaptureRequest.FLASH_MODE_TORCH)
        } else {
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
        }

        val clampedZoom = state.zoomRatio.coerceIn(0.5f, maxZoom)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, clampedZoom.coerceAtLeast(1.0f))
        }

        when (state.exposureMode) {
            ExposureMode.M, ExposureMode.BULB -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                val iso = CameraStepTables.isoValues[state.isoIndex].coerceIn(isoRange.lower, isoRange.upper)
                val rawNanos = CameraStepTables.shutterValues[state.shutterIndex].nanos
                val effectiveNanos = if (isCapture) {
                    rawNanos.coerceIn(exposureRange.lower, exposureRange.upper)
                } else {
                    min(rawNanos, 66_666_666L).coerceIn(exposureRange.lower, exposureRange.upper)
                }
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, iso)
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, effectiveNanos)
            }
            ExposureMode.S -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                val rawNanos = CameraStepTables.shutterValues[state.shutterIndex].nanos
                val effectiveNanos = if (isCapture) rawNanos else min(rawNanos, 66_666_666L)
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, effectiveNanos.coerceIn(exposureRange.lower, exposureRange.upper))
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, 400.coerceIn(isoRange.lower, isoRange.upper))
            }
            else -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }
        }

        val kelvin = CameraStepTables.wbKelvinValues[state.wbIndex]
        builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
        builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
        builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, kelvinToRggbGains(kelvin))

        if (state.focusIndex == 0) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        } else {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            val ratio = (20 - state.focusIndex) / 19f
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, ratio * minFocusDistance)
        }
    }

    fun triggerTapToFocus(normPoint: Offset) {
        val device = cameraDevice ?: return
        val session = captureSession ?: return
        val pSurface = previewSurface ?: return

        val cx = (normPoint.x.coerceIn(0f, 1f) * sensorArraySize.width()).roundToInt()
        val cy = (normPoint.y.coerceIn(0f, 1f) * sensorArraySize.height()).roundToInt()
        val halfBox = 150
        val rect = Rect(
            max(0, cx - halfBox),
            max(0, cy - halfBox),
            min(sensorArraySize.width(), cx + halfBox),
            min(sensorArraySize.height(), cy + halfBox)
        )
        val meteringRegion = MeteringRectangle(rect, MeteringRectangle.METERING_WEIGHT_MAX - 1)

        try {
            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(pSurface)
                applyManualParameters(this, isCapture = false)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(meteringRegion))
                set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(meteringRegion))
                set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
            }
            session.capture(builder.build(), null, bgHandler)
        } catch (_: Exception) {}
    }

    fun captureStillPhoto() {
        val device = cameraDevice ?: return
        val session = captureSession ?: return
        val jSurface = jpegReader?.surface ?: return

        try {
            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(jSurface)
                if (currentState.isRawEnabled && rawReader != null) {
                    addTarget(rawReader!!.surface)
                }
                applyManualParameters(this, isCapture = true)
                set(CaptureRequest.JPEG_QUALITY, 98.toByte())
            }

            session.capture(builder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    latestCaptureResult = result
                    tryWriteDngIfReady()
                    updateRepeatingPreview()
                }
            }, bgHandler)
        } catch (_: Exception) {}
    }

    private fun processCapturedJpeg(image: Image) {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        image.close()

        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        bitmap = applyFilmColorMatrixToBitmap(bitmap, currentState.activeFilmPreset)

        val uri = saveBitmapToMediaStore(bitmap)
        val thumb = Bitmap.createScaledBitmap(bitmap, 160, 160, true)
        if (uri != null) {
            onPhotoSaved(uri, thumb)
        }
    }

    private fun tryWriteDngIfReady() {
        if (!currentState.isRawEnabled) return
        val raw = latestRawImage ?: return
        val result = latestCaptureResult ?: return
        latestRawImage = null
        latestCaptureResult = null

        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "DSLR_${System.currentTimeMillis()}.dng")
                put(MediaStore.Images.Media.MIME_TYPE, "image/x-adobe-dng")
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/ProDSLR")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    DngCreator(characteristics, result).use { dngCreator ->
                        dngCreator.writeImage(out, raw)
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            raw.close()
        }
    }

    private fun saveBitmapToMediaStore(bitmap: Bitmap): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "DSLR_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/ProDSLR")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        context.contentResolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 96, out)
        }
        return uri
    }

    private fun applyFilmColorMatrixToBitmap(src: Bitmap, preset: FilmPreset): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint()
        val cm = ColorMatrix()
        when (preset) {
            FilmPreset.PROVIA -> cm.setSaturation(1.04f)
            FilmPreset.VELVIA -> cm.setSaturation(1.38f)
            FilmPreset.ASTIA -> cm.setSaturation(1.08f)
            FilmPreset.CLASSIC_CHROME -> cm.setSaturation(0.74f)
            FilmPreset.CLASSIC_NEG -> cm.setSaturation(0.88f)
            FilmPreset.ACROS -> cm.setSaturation(0f)
            FilmPreset.ETERNA -> cm.setSaturation(0.72f)
        }
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }



    private fun kelvinToRggbGains(kelvin: Int): RggbChannelVector {
        val t = ((kelvin - 2500f) / 5500f).coerceIn(0f, 1f)
        val redGain = 1.2f + (t * 1.4f)
        val blueGain = 2.6f - (t * 1.4f)
        return RggbChannelVector(redGain, 1.0f, 1.0f, blueGain)
    }

    private fun selectCameraId(front: Boolean): String {
        val targetFacing = if (front) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
        return cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == targetFacing
        } ?: cameraManager.cameraIdList.first()
    }

    private fun chooseOptimalSize(choices: Array<Size>, targetW: Int, targetH: Int): Size {
        return choices.filter { it.width <= 1920 && it.height <= 1080 }
            .maxByOrNull { it.width * it.height } ?: choices.first()
    }

    fun closeCamera() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        jpegReader?.close()
        rawReader?.close()
    }
}
