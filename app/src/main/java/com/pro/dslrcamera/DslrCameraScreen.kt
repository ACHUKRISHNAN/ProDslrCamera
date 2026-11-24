package com.pro.dslrcamera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.MediaActionSound
import android.media.MediaScannerConnection
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Size as AndroidSize
import android.view.Surface
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

enum class ExposureMode(val shortLabel: String) {
    M("M"),
    A("A"),
    S("S"),
    P("P"),
    B("B"),
    AUTO("AUTO")
}

enum class ActiveDialParam(val label: String) {
    ISO("ISO"),
    SHUTTER("SHUTTER"),
    EV("EV"),
    WB("WB"),
    FOCUS("FOCUS"),
    ZOOM("ZOOM"),
    PRESET("PRESET"),
    EFFECT("GRAIN")
}

enum class CompositionGridType(val label: String) {
    OFF("OFF"),
    THIRDS("3X3"),
    GOLDEN("GOLDEN SPIRAL"),
    DIAGONAL("DIAG"),
    CENTER("CENTER")
}

enum class CameraFlashMode(val label: String) {
    OFF("OFF"),
    AUTO("AUTO"),
    ON("ON"),
    TORCH("TORCH")
}

enum class LensRole(val label: String, val baseZoom: Float) {
    ULTRA_WIDE("ULTRA WIDE", 0.5f),
    WIDE("WIDE", 1.0f),
    TELEPHOTO("TELEPHOTO", 3.0f),
    FRONT("FRONT", 1.0f)
}

data class BoundLens(
    val role: LensRole,
    val facing: Int,
    val focalLength: Float,
    val cameraId: String,
    val displayName: String,
    val hasTelephoto: Boolean
)

private data class MpOption(
    val shortLabel: String,
    val detailLabel: String,
    val resolution: AndroidSize?
)

private const val TAG = "DslrCameraScreen"

private data class SinCityQuickColor(
    val label: String,
    val hueDegrees: Float,
    val color: Color
)

private val sinCityQuickColors = listOf(
    SinCityQuickColor("RED", 0f, Color(0xFFEF4444)),
    SinCityQuickColor("YEL", 56f, Color(0xFFFACC15)),
    SinCityQuickColor("GRN", 132f, Color(0xFF22C55E)),
    SinCityQuickColor("BLU", 215f, Color(0xFF3B82F6))
)

private fun imageProxyToOrientedBitmap(imageProxy: ImageProxy, isFront: Boolean): Bitmap? {
    return try {
        val planeProxy = imageProxy.planes.firstOrNull() ?: return null
        val buffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val rawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        if (rotationDegrees == 0 && !isFront) {
            return rawBitmap
        }

        val matrix = Matrix().apply {
            if (rotationDegrees != 0) {
                postRotate(rotationDegrees.toFloat())
            }
            if (isFront) {
                postScale(-1f, 1f)
            }
        }

        val oriented = Bitmap.createBitmap(
            rawBitmap,
            0,
            0,
            rawBitmap.width,
            rawBitmap.height,
            matrix,
            true
        )
        if (oriented !== rawBitmap && !rawBitmap.isRecycled) {
            rawBitmap.recycle()
        }
        oriented
    } catch (e: Exception) {
        Log.e(TAG, "Failed converting ImageProxy to Bitmap", e)
        null
    } finally {
        imageProxy.close()
    }
}

@OptIn(ExperimentalCamera2Interop::class)
private fun resolveBackLensSelection(
    context: Context,
    cameraProvider: ProcessCameraProvider,
    desiredRole: LensRole
): Pair<CameraSelector, BoundLens> {
    val camManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val backEntries = cameraProvider.availableCameraInfos
        .filter { info -> info.lensFacing == CameraSelector.LENS_FACING_BACK }
        .mapNotNull { info ->
            try {
                val camId = Camera2CameraInfo.from(info).cameraId
                val chars = camManager.getCameraCharacteristics(camId)
                val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.minOrNull() ?: 5.0f
                Triple(info, focal, camId)
            } catch (e: Exception) {
                Log.w(TAG, "Failed inspecting back camera", e)
                null
            }
        }
        .sortedBy { it.second }

    val hasTelephoto = backEntries.size >= 3 || (backEntries.size == 2 && (backEntries.last().second - backEntries.first().second) > 4.0f)

    if (backEntries.isEmpty()) {
        val fallbackSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
        return fallbackSelector to BoundLens(
            role = LensRole.WIDE,
            facing = CameraSelector.LENS_FACING_BACK,
            focalLength = 5.0f,
            cameraId = "0",
            displayName = "WIDE",
            hasTelephoto = false
        )
    }

    val chosen = when (desiredRole) {
        LensRole.ULTRA_WIDE -> backEntries.first()
        LensRole.TELEPHOTO -> if (hasTelephoto) backEntries.last() else backEntries.maxByOrNull { it.second } ?: backEntries.last()
        LensRole.WIDE, LensRole.FRONT -> {
            if (backEntries.size >= 3) {
                backEntries[1]
            } else if (backEntries.size == 2 && !hasTelephoto) {
                backEntries.last()
            } else {
                backEntries.maxByOrNull { it.second } ?: backEntries.first()
            }
        }
    }

    val resolvedRole = when {
        desiredRole == LensRole.TELEPHOTO && hasTelephoto -> LensRole.TELEPHOTO
        backEntries.size >= 2 && chosen.third == backEntries.first().third && chosen.second < 3.5f -> LensRole.ULTRA_WIDE
        else -> LensRole.WIDE
    }

    val selector = CameraSelector.Builder()
        .addCameraFilter { infos -> infos.filter { it == chosen.first }.ifEmpty { infos } }
        .build()

    return selector to BoundLens(
        role = resolvedRole,
        facing = CameraSelector.LENS_FACING_BACK,
        focalLength = chosen.second,
        cameraId = chosen.third,
        displayName = resolvedRole.label,
        hasTelephoto = hasTelephoto
    )
}

@OptIn(ExperimentalCamera2Interop::class)
private fun querySupportedMpOptionsForCameraId(context: Context, cameraId: String): List<MpOption> {
    return try {
        val camManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val chars = camManager.getCameraCharacteristics(cameraId)

        val allSizes = LinkedHashSet<AndroidSize>()

        val standardMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        standardMap?.getOutputSizes(ImageFormat.JPEG)?.let { allSizes.addAll(it) }
        standardMap?.getHighResolutionOutputSizes(ImageFormat.JPEG)?.let { allSizes.addAll(it) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val maxResMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION)
            maxResMap?.getOutputSizes(ImageFormat.JPEG)?.let { allSizes.addAll(it) }
            maxResMap?.getHighResolutionOutputSizes(ImageFormat.JPEG)?.let { allSizes.addAll(it) }
        }

        val meaningful = allSizes
            .filter { it.width.toLong() * it.height.toLong() >= 2_000_000L }
            .sortedByDescending { it.width.toLong() * it.height.toLong() }

        if (meaningful.isEmpty()) {
            return listOf(MpOption("MAX", "MAX", null))
        }

        val seenLabels = HashSet<String>()
        val result = mutableListOf<MpOption>()
        for (sz in meaningful) {
            val mpExact = (sz.width.toLong() * sz.height.toLong()) / 1_000_000.0
            val shortLabel = if (mpExact >= 10.0 && abs(mpExact - mpExact.roundToInt()) >= 0.35) {
                String.format(Locale.US, "%.1fMP", mpExact)
            } else {
                "${mpExact.roundToInt().coerceAtLeast(1)}MP"
            }
            if (seenLabels.add(shortLabel)) {
                result.add(
                    MpOption(
                        shortLabel = shortLabel,
                        detailLabel = "$shortLabel (${sz.width}x${sz.height})",
                        resolution = sz
                    )
                )
            }
        }
        result
    } catch (e: Exception) {
        Log.w(TAG, "Failed querying supported JPEG MP sizes for cameraId=$cameraId", e)
        listOf(MpOption("MAX", "MAX", null))
    }
}

@OptIn(ExperimentalCamera2Interop::class)
@Composable
fun DslrCameraScreen() {
    val context: Context = LocalContext.current
    val appContext: Context = remember(context) { context.applicationContext }
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val ioExecutor = remember { Executors.newSingleThreadExecutor() }

    val shutterSound: MediaActionSound = remember {
        MediaActionSound().apply {
            load(MediaActionSound.SHUTTER_CLICK)
        }
    }
    DisposableEffect(shutterSound) {
        onDispose {
            shutterSound.release()
        }
    }

    var glSurfaceView by remember { mutableStateOf<GLSurfaceView?>(null) }
    var viewfinderSize by remember { mutableStateOf(IntSize.Zero) }
    var previewBufferSize by remember { mutableStateOf(AndroidSize(1440, 1080)) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var cameraInfo by remember { mutableStateOf<CameraInfo?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var activeSurfaceTexture by remember { mutableStateOf<SurfaceTexture?>(null) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }

    var desiredLensRole by remember { mutableStateOf(LensRole.WIDE) }
    var boundLens by remember {
        mutableStateOf(
            BoundLens(
                role = LensRole.WIDE,
                facing = CameraSelector.LENS_FACING_BACK,
                focalLength = 5.0f,
                cameraId = "0",
                displayName = "WIDE",
                hasTelephoto = false
            )
        )
    }
    var minFocusDiopter by remember { mutableFloatStateOf(10.0f) }

    var mpOptions by remember { mutableStateOf(listOf(MpOption("MAX", "MAX", null))) }
    var selectedMpIndex by remember { mutableIntStateOf(0) }
    var showMpMenu by remember { mutableStateOf(false) }

    var flashMode by remember { mutableStateOf(CameraFlashMode.OFF) }
    var hasFlashUnit by remember { mutableStateOf(true) }

    var focusTapOffset by remember { mutableStateOf<Offset?>(null) }
    var focusLockedSuccess by remember { mutableStateOf<Boolean?>(null) }
    var focusReticleToken by remember { mutableIntStateOf(0) }

    var showGallery by remember { mutableStateOf(false) }
    var galleryRefreshKey by remember { mutableIntStateOf(0) }

    val renderer: CameraShaderRenderer = remember {
        CameraShaderRenderer(
            onSurfaceTextureReady = { surfaceTexture: SurfaceTexture ->
                activeSurfaceTexture = surfaceTexture
            },
            onRequestRender = {
                glSurfaceView?.requestRender()
            }
        )
    }

    DisposableEffect(lifecycleOwner, glSurfaceView) {
        val view = glSurfaceView
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> view?.onPause()
                Lifecycle.Event.ON_RESUME -> {
                    view?.onResume()
                    view?.requestRender()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(renderer, ioExecutor) {
        onDispose {
            renderer.clearDoubleExposureFrame()
            ioExecutor.shutdown()
        }
    }

    fun triggerTickHaptic() {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun showToast(message: String) {
        ContextCompat.getMainExecutor(appContext).execute {
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Toast: $message")
        }
    }

    LaunchedEffect(focusReticleToken) {
        if (focusReticleToken > 0) {
            delay(2500L)
            focusTapOffset = null
            focusLockedSuccess = null
        }
    }

    val zoomSteps: List<Float> = remember { (5..80).map { idx: Int -> idx / 10f } }
    val zoomLabels: List<String> = remember(zoomSteps) {
        zoomSteps.map { ratio: Float -> String.format(Locale.US, "%.1fx", ratio) }
    }
    var zoomIndex by remember { mutableIntStateOf(5) }

    LaunchedEffect(activeSurfaceTexture, lensFacing, desiredLensRole, selectedMpIndex, lifecycleOwner) {
        val st: SurfaceTexture = activeSurfaceTexture ?: return@LaunchedEffect
        val isFront = (lensFacing == CameraSelector.LENS_FACING_FRONT)
        renderer.isFrontCamera = isFront

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                val selectorAndLens: Pair<CameraSelector, BoundLens> = if (isFront) {
                    val frontSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()
                    val frontInfo = cameraProvider.availableCameraInfos.firstOrNull { it.lensFacing == CameraSelector.LENS_FACING_FRONT }
                    val frontId = frontInfo?.let { Camera2CameraInfo.from(it).cameraId } ?: "1"
                    frontSelector to BoundLens(
                        role = LensRole.FRONT,
                        facing = CameraSelector.LENS_FACING_FRONT,
                        focalLength = 2.0f,
                        cameraId = frontId,
                        displayName = "FRONT",
                        hasTelephoto = false
                    )
                } else {
                    resolveBackLensSelection(context, cameraProvider, desiredLensRole)
                }

                boundLens = selectorAndLens.second

                val previewResolutionSelector: ResolutionSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    .build()

                val preview: Preview = Preview.Builder()
                    .setResolutionSelector(previewResolutionSelector)
                    .build()
                    .also { prev: Preview ->
                        prev.setSurfaceProvider { request ->
                            val res = request.resolution
                            previewBufferSize = res
                            renderer.cameraAspectRatio = if (res.height > 0) {
                                min(res.width, res.height).toFloat() / max(res.width, res.height).toFloat()
                            } else {
                                3f / 4f
                            }
                            st.setDefaultBufferSize(res.width, res.height)
                            val surface = Surface(st)
                            request.provideSurface(surface, ioExecutor) {
                                surface.release()
                            }
                        }
                    }

                val queriedMp = querySupportedMpOptionsForCameraId(context, boundLens.cameraId)
                mpOptions = queriedMp
                if (selectedMpIndex >= queriedMp.size) {
                    selectedMpIndex = 0
                }

                val chosenMp = queriedMp.getOrNull(selectedMpIndex)
                val captureSelectorBuilder = ResolutionSelector.Builder()
                    .setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)

                if (chosenMp?.resolution != null) {
                    captureSelectorBuilder.setResolutionStrategy(
                        ResolutionStrategy(
                            chosenMp.resolution,
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                } else {
                    captureSelectorBuilder.setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                }

                val initialCaptureFlash = when (flashMode) {
                    CameraFlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                    CameraFlashMode.ON -> ImageCapture.FLASH_MODE_ON
                    CameraFlashMode.OFF, CameraFlashMode.TORCH -> ImageCapture.FLASH_MODE_OFF
                }

                val captureUseCase: ImageCapture = ImageCapture.Builder()
                    .setResolutionSelector(captureSelectorBuilder.build())
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setFlashMode(initialCaptureFlash)
                    .build()

                cameraProvider.unbindAll()
                val cam = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selectorAndLens.first,
                    preview,
                    captureUseCase
                )
                cameraControl = cam.cameraControl
                cameraInfo = cam.cameraInfo
                imageCapture = captureUseCase
                hasFlashUnit = cam.cameraInfo.hasFlashUnit()

                if (!hasFlashUnit) {
                    flashMode = CameraFlashMode.OFF
                } else {
                    cam.cameraControl.enableTorch(flashMode == CameraFlashMode.TORCH)
                }

                try {
                    val camManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    val chars = camManager.getCameraCharacteristics(boundLens.cameraId)
                    val hwMinFocus = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
                    if (hwMinFocus != null && hwMinFocus > 0f) {
                        minFocusDiopter = hwMinFocus
                    }
                    Log.d(
                        TAG,
                        "Bound lens=${boundLens.displayName} (id=${boundLens.cameraId}, focal=${boundLens.focalLength}) mpOptions=${queriedMp.map { it.detailLabel }}"
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Could not query LENS_INFO_MINIMUM_FOCUS_DISTANCE", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind camera use cases", e)
                showToast("Camera bind error: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    LaunchedEffect(flashMode, cameraControl, imageCapture, hasFlashUnit) {
        val ctrl = cameraControl ?: return@LaunchedEffect
        val cap = imageCapture
        if (!hasFlashUnit) {
            ctrl.enableTorch(false)
            cap?.flashMode = ImageCapture.FLASH_MODE_OFF
            return@LaunchedEffect
        }
        when (flashMode) {
            CameraFlashMode.OFF -> {
                ctrl.enableTorch(false)
                cap?.flashMode = ImageCapture.FLASH_MODE_OFF
            }
            CameraFlashMode.AUTO -> {
                ctrl.enableTorch(false)
                cap?.flashMode = ImageCapture.FLASH_MODE_AUTO
            }
            CameraFlashMode.ON -> {
                ctrl.enableTorch(false)
                cap?.flashMode = ImageCapture.FLASH_MODE_ON
            }
            CameraFlashMode.TORCH -> {
                cap?.flashMode = ImageCapture.FLASH_MODE_OFF
                ctrl.enableTorch(true)
            }
        }
    }

    var exposureMode by remember { mutableStateOf(ExposureMode.M) }
    var activeParam by remember { mutableStateOf(ActiveDialParam.SHUTTER) }
    var isSelectingParam by remember { mutableStateOf(false) }
    val selectableParams: List<ActiveDialParam> = remember { ActiveDialParam.entries.toList() }

    var activeGrid by remember { mutableStateOf(CompositionGridType.OFF) }
    var showAllGridsMenu by remember { mutableStateOf(false) }
    var isPresetsExpanded by remember { mutableStateOf(true) }

    val isoValues = listOf("50", "100", "200", "400", "800", "1600", "3200", "6400")
    val isoNumbers = listOf(50, 100, 200, 400, 800, 1600, 3200, 6400)
    var isoIndex by remember { mutableIntStateOf(1) }

    val shutterLabels = listOf(
        "1/8000", "1/6400", "1/4000", "1/3200", "1/2000", "1/1600",
        "1/1000", "1/800", "1/500", "1/400", "1/320", "1/250",
        "1/200", "1/160", "1/125", "1/100", "1/80", "1/60",
        "1/30", "1/15", "1/8", "1/4", "1/2", "1s", "2s"
    )
    val shutterNanos = listOf(
        125_000L, 156_250L, 250_000L, 312_500L, 500_000L, 625_000L,
        1_000_000L, 1_250_000L, 2_000_000L, 2_500_000L, 3_125_000L, 4_000_000L,
        5_000_000L, 6_250_000L, 8_000_000L, 10_000_000L, 12_500_000L, 16_666_666L,
        33_333_333L, 66_666_666L, 125_000_000L, 250_000_000L, 500_000_000L, 1_000_000_000L, 2_000_000_000L
    )
    var shutterIndex by remember { mutableIntStateOf(6) }

    val evLabels = listOf("-2.0", "-1.7", "-1.3", "-1.0", "-0.7", "-0.3", "0.0", "+0.3", "+0.7", "+1.0", "+1.3", "+1.7", "+2.0")
    val evSteps = listOf(-6, -5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 6)
    var evIndex by remember { mutableIntStateOf(6) }

    val wbLabels = listOf("AUTO", "2800K", "3200K", "4000K", "5200K", "6500K", "7500K")
    var wbIndex by remember { mutableIntStateOf(0) }

    val focusLabels = listOf(
        "AUTO", "0.03m", "0.06m", "0.10m", "0.15m", "0.20m", "0.30m", "0.40m", "0.50m",
        "0.70m", "1.0m", "1.5m", "2.0m", "3.0m", "5.0m", "8.0m", "15m", "INF"
    )
    val focusDiopters = listOf(
        0f, 33.3f, 16.67f, 10.0f, 6.67f, 5.0f, 3.33f, 2.5f, 2.0f,
        1.43f, 1.0f, 0.67f, 0.5f, 0.33f, 0.2f, 0.125f, 0.067f, 0.0f
    )
    var focusIndex by remember { mutableIntStateOf(0) }

    fun applyZoomRatio(requestedRatio: Float) {
        val nearestIdx: Int = zoomSteps.indices.minByOrNull { idx: Int -> abs(zoomSteps[idx] - requestedRatio) } ?: 5
        zoomIndex = nearestIdx

        if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            val targetRole = when {
                requestedRatio < 1.0f -> LensRole.ULTRA_WIDE
                requestedRatio >= 3.0f && boundLens.hasTelephoto -> LensRole.TELEPHOTO
                else -> LensRole.WIDE
            }
            if (targetRole != desiredLensRole) {
                desiredLensRole = targetRole
            }
        }

        val zoomState = cameraInfo?.zoomState?.value
        val minZ = zoomState?.minZoomRatio ?: 1.0f
        val maxZ = zoomState?.maxZoomRatio ?: 8.0f
        val relativeRatio = when (boundLens.role) {
            LensRole.ULTRA_WIDE -> requestedRatio / 0.5f
            LensRole.TELEPHOTO -> requestedRatio / 3.0f
            LensRole.WIDE, LensRole.FRONT -> requestedRatio
        }.coerceIn(minZ, maxZ)

        cameraControl?.setZoomRatio(relativeRatio)
        glSurfaceView?.requestRender()
    }

    LaunchedEffect(cameraInfo, boundLens) {
        val currentTarget: Float = zoomSteps[zoomIndex]
        val zoomState = cameraInfo?.zoomState?.value ?: return@LaunchedEffect
        val relativeRatio = when (boundLens.role) {
            LensRole.ULTRA_WIDE -> currentTarget / 0.5f
            LensRole.TELEPHOTO -> currentTarget / 3.0f
            LensRole.WIDE, LensRole.FRONT -> currentTarget
        }.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
        cameraControl?.setZoomRatio(relativeRatio)
        glSurfaceView?.requestRender()
    }

    val allPresets: List<FilmPreset> = remember { FilterCatalog.presets }

    var selectedCategory by remember { mutableStateOf("ALL") }
    val visiblePresets: List<FilmPreset> = remember(selectedCategory, allPresets) {
        if (selectedCategory == "ALL") allPresets
        else allPresets.filter { preset: FilmPreset -> preset.brand == selectedCategory || preset.code == "NORMAL" }
    }
    var selectedPreset by remember { mutableStateOf(allPresets.first()) }
    var isBypassed by remember { mutableStateOf(false) }

    var sinCityHueDegrees by remember { mutableFloatStateOf(0f) }
    var sinCityNeutralMode by remember { mutableStateOf(SinCityNeutralMode.STRICT_POP) }

    var dblBlendMode by remember { mutableStateOf(DoubleExposureBlendMode.HIGH_KEY_SILHOUETTE) }
    var dblStrength by remember { mutableFloatStateOf(0.85f) }
    var hasShotOne by remember { mutableStateOf(false) }
    var lockedPrimaryBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var grainLevel by remember { mutableStateOf(GrainLevel.OFF) }
    var pixelBitDepth by remember { mutableStateOf(PixelBitDepth.OFF) }
    var filmEffect by remember { mutableStateOf(FilmEffectType.OFF) }
    var rippedEffect by remember { mutableStateOf(RippedEffectType.OFF) }

    var isBulbRunning by remember { mutableStateOf(false) }
    var bulbElapsedTenths by remember { mutableIntStateOf(0) }

    LaunchedEffect(isBulbRunning) {
        if (isBulbRunning) {
            bulbElapsedTenths = 0
            while (isBulbRunning) {
                delay(100L)
                bulbElapsedTenths += 1
            }
        }
    }

    LaunchedEffect(selectedPreset) {
        pixelBitDepth = selectedPreset.pixelBitDepth
        if (selectedPreset.specialMode != SpecialShaderMode.DOUBLE_EXPOSURE) {
            hasShotOne = false
            lockedPrimaryBitmap?.let { if (!it.isRecycled) it.recycle() }
            lockedPrimaryBitmap = null
            renderer.clearDoubleExposureFrame()
        }
    }

    fun triggerTapAndFocus(tapOffset: Offset) {
        val ctrl = cameraControl ?: return
        val w = viewfinderSize.width.toFloat()
        val h = viewfinderSize.height.toFloat()
        if (w <= 0f || h <= 0f) return

        focusTapOffset = tapOffset
        focusLockedSuccess = null
        focusReticleToken += 1
        triggerTickHaptic()

        if (focusIndex != 0) {
            focusIndex = 0
        }

        try {
            val c2Ctrl = Camera2CameraControl.from(ctrl)
            val clearAfBuilder = CaptureRequestOptions.Builder()
                .clearCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE)
                .clearCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE)
            c2Ctrl.addCaptureRequestOptions(clearAfBuilder.build())
        } catch (e: Exception) {
            Log.w(TAG, "Could not clear AF overrides before tap focus", e)
        }

        val viewAspect = w / h
        val sensorAspect = renderer.cameraAspectRatio.coerceIn(0.4f, 1.0f)
        var normX = (tapOffset.x / w).coerceIn(0f, 1f)
        var normY = (tapOffset.y / h).coerceIn(0f, 1f)
        if (viewAspect < sensorAspect) {
            val scaleX = viewAspect / sensorAspect
            normX = (0.5f + (normX - 0.5f) * scaleX).coerceIn(0f, 1f)
        } else if (viewAspect > sensorAspect) {
            val scaleY = sensorAspect / viewAspect
            normY = (0.5f + (normY - 0.5f) * scaleY).coerceIn(0f, 1f)
        }

        val factory = SurfaceOrientedMeteringPointFactory(1.0f, 1.0f)
        val afPoint = factory.createPoint(normX, normY, 0.14f)
        val aePoint = factory.createPoint(normX, normY, 0.24f)

        val actionBuilder = FocusMeteringAction.Builder(afPoint, FocusMeteringAction.FLAG_AF)
        if (exposureMode !in listOf(ExposureMode.M, ExposureMode.S)) {
            actionBuilder.addPoint(aePoint, FocusMeteringAction.FLAG_AE)
        }
        actionBuilder.setAutoCancelDuration(10, TimeUnit.SECONDS)

        ctrl.cancelFocusAndMetering()
        val future = ctrl.startFocusAndMetering(actionBuilder.build())
        future.addListener({
            try {
                val result = future.get()
                focusLockedSuccess = result.isFocusSuccessful
                focusReticleToken += 1
            } catch (e: Exception) {
                Log.w(TAG, "Tap and focus failed", e)
                focusLockedSuccess = false
                focusReticleToken += 1
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    LaunchedEffect(
        exposureMode,
        isoIndex,
        shutterIndex,
        evIndex,
        focusIndex,
        selectedPreset,
        flashMode,
        cameraControl,
        minFocusDiopter
    ) {
        val ctrl = cameraControl ?: return@LaunchedEffect
        val c2Ctrl = Camera2CameraControl.from(ctrl)
        val optionsBuilder = CaptureRequestOptions.Builder()

        optionsBuilder.setCaptureRequestOption(
            CaptureRequest.EDGE_MODE,
            CaptureRequest.EDGE_MODE_HIGH_QUALITY
        )
        optionsBuilder.setCaptureRequestOption(
            CaptureRequest.NOISE_REDUCTION_MODE,
            CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY
        )
        optionsBuilder.setCaptureRequestOption(
            CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
            CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON
        )

        val flashNeedsAe = flashMode == CameraFlashMode.AUTO || flashMode == CameraFlashMode.ON

        when (exposureMode) {
            ExposureMode.M -> {
                if (flashNeedsAe) {
                    val aeFlash = if (flashMode == CameraFlashMode.ON) {
                        CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH
                    } else {
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                    }
                    optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, aeFlash)
                } else {
                    optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                    optionsBuilder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, isoNumbers[isoIndex])
                    val previewShutter = minOf(shutterNanos[shutterIndex], 66_666_666L)
                    optionsBuilder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, previewShutter)
                }
            }
            ExposureMode.A -> {
                val aeMode = when (flashMode) {
                    CameraFlashMode.ON -> CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH
                    CameraFlashMode.AUTO -> CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                    else -> CaptureRequest.CONTROL_AE_MODE_ON
                }
                optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, aeMode)
                ctrl.setExposureCompensationIndex(evSteps[evIndex])
            }
            ExposureMode.S -> {
                if (flashNeedsAe) {
                    val aeFlash = if (flashMode == CameraFlashMode.ON) {
                        CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH
                    } else {
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                    }
                    optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, aeFlash)
                } else {
                    optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                    val previewShutter = minOf(shutterNanos[shutterIndex], 66_666_666L)
                    optionsBuilder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, previewShutter)
                    optionsBuilder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, 400)
                }
            }
            ExposureMode.P -> {
                val aeMode = when (flashMode) {
                    CameraFlashMode.ON -> CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH
                    CameraFlashMode.AUTO -> CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                    else -> CaptureRequest.CONTROL_AE_MODE_ON
                }
                optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, aeMode)
                ctrl.setExposureCompensationIndex(evSteps[evIndex])
            }
            ExposureMode.B -> {
                optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                ctrl.setExposureCompensationIndex(evSteps[evIndex])
            }
            ExposureMode.AUTO -> {
                val aeMode = when (flashMode) {
                    CameraFlashMode.ON -> CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH
                    CameraFlashMode.AUTO -> CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                    else -> CaptureRequest.CONTROL_AE_MODE_ON
                }
                optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, aeMode)
                ctrl.setExposureCompensationIndex(0)
            }
        }

        if (focusIndex == 0 && selectedPreset.snapFocusMeters == null) {
            optionsBuilder.clearCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE)
            optionsBuilder.clearCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE)
        } else {
            val rawDiopter = if (selectedPreset.snapFocusMeters != null) {
                1.0f / selectedPreset.snapFocusMeters!!
            } else {
                focusDiopters[focusIndex]
            }
            val clampedDiopter = rawDiopter.coerceIn(0f, minFocusDiopter)
            optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            optionsBuilder.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, clampedDiopter)
        }

        try {
            c2Ctrl.setCaptureRequestOptions(optionsBuilder.build())
        } catch (e: Exception) {
            Log.w(TAG, "CaptureRequest option not supported on device", e)
        }
    }

    LaunchedEffect(
        selectedPreset,
        isBypassed,
        sinCityHueDegrees,
        sinCityNeutralMode,
        dblBlendMode,
        dblStrength,
        grainLevel,
        pixelBitDepth,
        filmEffect,
        rippedEffect
    ) {
        renderer.activePreset = selectedPreset
        renderer.bypassToNormal = isBypassed
        renderer.sinCityTargetHue = sinCityHueDegrees
        renderer.sinCityTolerance = 0.032f
        renderer.sinCityNeutralMode = sinCityNeutralMode
        renderer.doubleExposureBlendMode = dblBlendMode
        renderer.doubleExposureStrength = dblStrength
        renderer.grainLevel = grainLevel
        renderer.pixelBitDepth = pixelBitDepth
        renderer.filmEffect = filmEffect
        renderer.rippedEffect = rippedEffect
        glSurfaceView?.requestRender()
    }

    fun writeBitmapToDefaultCameraFolder(bitmapToSave: Bitmap, filenameBase: String): Boolean {
        if (bitmapToSave.isRecycled || bitmapToSave.width <= 0 || bitmapToSave.height <= 0) {
            Log.e(TAG, "Invalid bitmap passed to save: recycled=${bitmapToSave.isRecycled}, ${bitmapToSave.width}x${bitmapToSave.height}")
            return false
        }

        val resolver = appContext.contentResolver
        val fileName = "$filenameBase.jpg"
        val relativePath = CameraShaderRenderer.DEFAULT_CAMERA_RELATIVE_PATH
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            Log.e(TAG, "MediaStore insert returned null for $fileName")
            return false
        }

        return try {
            val outStream = resolver.openOutputStream(uri)
            if (outStream == null) {
                Log.e(TAG, "openOutputStream returned null for $uri")
                resolver.delete(uri, null, null)
                return false
            }

            val compressed = outStream.use { out ->
                val ok = bitmapToSave.compress(Bitmap.CompressFormat.JPEG, 98, out)
                out.flush()
                ok
            }
            if (!compressed) {
                Log.e(TAG, "Bitmap.compress returned false for $uri")
                resolver.delete(uri, null, null)
                return false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val publishValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, publishValues, null, null)
            }

            val expectedPath = File(Environment.getExternalStorageDirectory(), "$relativePath/$fileName").absolutePath
            MediaScannerConnection.scanFile(
                appContext,
                arrayOf(expectedPath),
                arrayOf("image/jpeg")
            ) { scannedPath, scannedUri ->
                Log.d(TAG, "Media scanned: path=$scannedPath uri=$scannedUri")
            }

            ContextCompat.getMainExecutor(appContext).execute {
                galleryRefreshKey += 1
            }

            Log.i(TAG, "Saved image successfully (${bitmapToSave.width}x${bitmapToSave.height}): uri=$uri path=$expectedPath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing image to DCIM/Camera", e)
            resolver.delete(uri, null, null)
            false
        }
    }

    fun resetDoubleExposureState() {
        hasShotOne = false
        lockedPrimaryBitmap?.let { if (!it.isRecycled) it.recycle() }
        lockedPrimaryBitmap = null
        renderer.clearDoubleExposureFrame()
    }

    fun capturePhotoNow() {
        val activeCode = (if (isBypassed) "NORMAL" else selectedPreset.code).replace(" ", "_")
        val modeCode = exposureMode.name
        val isDoubleExposureActive = !isBypassed && selectedPreset.specialMode == SpecialShaderMode.DOUBLE_EXPOSURE
        val isFront = (lensFacing == CameraSelector.LENS_FACING_FRONT)

        if (isDoubleExposureActive) {
            renderer.captureCurrentFrame { capturedBitmap: Bitmap? ->
                if (capturedBitmap == null || capturedBitmap.isRecycled) {
                    showToast("Could not grab camera frame")
                    return@captureCurrentFrame
                }

                if (!hasShotOne) {
                    val snapshot = capturedBitmap.copy(Bitmap.Config.ARGB_8888, false)
                    renderer.lockDoubleExposureFrame(snapshot)
                    lockedPrimaryBitmap = snapshot
                    hasShotOne = true
                    if (!capturedBitmap.isRecycled) capturedBitmap.recycle()
                    showToast("Shot 1/2 locked — frame second scene")
                    return@captureCurrentFrame
                }

                ioExecutor.execute {
                    try {
                        val filenameBase = "IMG_${modeCode}_${activeCode}_${System.currentTimeMillis()}"
                        val firstShot = lockedPrimaryBitmap
                        if (firstShot != null && !firstShot.isRecycled) {
                            val blended: Bitmap = renderer.blendDoubleExposure(
                                primaryFrame = firstShot,
                                secondaryFrame = capturedBitmap,
                                blendMode = dblBlendMode,
                                strength = dblStrength
                            )
                            val saved = writeBitmapToDefaultCameraFolder(blended, filenameBase)
                            if (!blended.isRecycled) blended.recycle()
                            if (!firstShot.isRecycled) firstShot.recycle()
                            ContextCompat.getMainExecutor(appContext).execute {
                                hasShotOne = false
                                lockedPrimaryBitmap = null
                                renderer.clearDoubleExposureFrame()
                            }
                            if (saved) {
                                showToast("Saved Double Exposure: $filenameBase.jpg")
                            } else {
                                showToast("Failed saving $filenameBase.jpg")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Double exposure save failed", e)
                        showToast("Save error: ${e.message}")
                    } finally {
                        if (!capturedBitmap.isRecycled) capturedBitmap.recycle()
                    }
                }
            }
            return
        }

        val captureUseCase = imageCapture
        if (captureUseCase != null) {
            captureUseCase.flashMode = when (flashMode) {
                CameraFlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                CameraFlashMode.ON -> ImageCapture.FLASH_MODE_ON
                CameraFlashMode.OFF, CameraFlashMode.TORCH -> ImageCapture.FLASH_MODE_OFF
            }
            captureUseCase.takePicture(
                ioExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val rawBitmap = imageProxyToOrientedBitmap(image, isFront)
                        if (rawBitmap == null || rawBitmap.isRecycled) {
                            showToast("Could not decode camera photo")
                            return
                        }
                        try {
                            val filenameBase = "IMG_${modeCode}_${activeCode}_${System.currentTimeMillis()}"
                            val saved = writeBitmapToDefaultCameraFolder(rawBitmap, filenameBase)
                            if (saved) {
                                showToast("Saved (${rawBitmap.width}x${rawBitmap.height}): $filenameBase.jpg")
                            } else {
                                showToast("Failed saving $filenameBase.jpg")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "ImageCapture save failed", e)
                            showToast("Save error: ${e.message}")
                        } finally {
                            if (!rawBitmap.isRecycled) rawBitmap.recycle()
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "ImageCapture failed, falling back to viewport", exception)
                        renderer.captureCurrentFrame { fallbackBitmap: Bitmap? ->
                            if (fallbackBitmap == null || fallbackBitmap.isRecycled) {
                                showToast("Capture error: ${exception.message}")
                                return@captureCurrentFrame
                            }
                            ioExecutor.execute {
                                try {
                                    val filenameBase = "IMG_${modeCode}_${activeCode}_${System.currentTimeMillis()}"
                                    val saved = writeBitmapToDefaultCameraFolder(fallbackBitmap, filenameBase)
                                    if (saved) {
                                        showToast("Saved to Camera: $filenameBase.jpg")
                                    } else {
                                        showToast("Failed saving $filenameBase.jpg")
                                    }
                                } finally {
                                    if (!fallbackBitmap.isRecycled) fallbackBitmap.recycle()
                                }
                            }
                        }
                    }
                }
            )
        } else {
            renderer.captureCurrentFrame { capturedBitmap: Bitmap? ->
                if (capturedBitmap == null || capturedBitmap.isRecycled) {
                    showToast("Could not grab camera frame")
                    return@captureCurrentFrame
                }
                ioExecutor.execute {
                    try {
                        val filenameBase = "IMG_${modeCode}_${activeCode}_${System.currentTimeMillis()}"
                        val saved = writeBitmapToDefaultCameraFolder(capturedBitmap, filenameBase)
                        if (saved) {
                            showToast("Saved to Camera: $filenameBase.jpg")
                        } else {
                            showToast("Failed saving $filenameBase.jpg")
                        }
                    } finally {
                        if (!capturedBitmap.isRecycled) capturedBitmap.recycle()
                    }
                }
            }
        }
    }

    val presetIdx = visiblePresets.indexOfFirst { preset: FilmPreset -> preset.code == selectedPreset.code }.coerceAtLeast(0)
    val paramNames: List<String> = remember(selectableParams) {
        selectableParams.map { param: ActiveDialParam -> param.name }
    }
    val paramIdx = selectableParams.indexOf(activeParam).coerceAtLeast(0)

    val dialState: Triple<List<String>, Int, Boolean> = if (isSelectingParam) {
        Triple(paramNames, paramIdx, false)
    } else {
        when (activeParam) {
            ActiveDialParam.ISO -> Triple(
                isoValues,
                isoIndex,
                exposureMode in listOf(ExposureMode.AUTO, ExposureMode.P, ExposureMode.A)
            )
            ActiveDialParam.SHUTTER -> Triple(
                shutterLabels,
                shutterIndex,
                exposureMode in listOf(ExposureMode.AUTO, ExposureMode.P, ExposureMode.A, ExposureMode.B)
            )
            ActiveDialParam.EV -> Triple(
                evLabels,
                evIndex,
                exposureMode in listOf(ExposureMode.M, ExposureMode.S, ExposureMode.AUTO)
            )
            ActiveDialParam.WB -> Triple(wbLabels, wbIndex, false)
            ActiveDialParam.FOCUS -> Triple(focusLabels, focusIndex, false)
            ActiveDialParam.ZOOM -> Triple(zoomLabels, zoomIndex, false)
            ActiveDialParam.PRESET -> Triple(
                visiblePresets.map { preset: FilmPreset -> preset.code },
                presetIdx,
                false
            )
            ActiveDialParam.EFFECT -> Triple(
                GrainLevel.entries.map { grain: GrainLevel -> grain.label },
                GrainLevel.entries.indexOf(grainLevel),
                false
            )
        }
    }
    val valuesList: List<String> = dialState.first
    val currentIdx: Int = dialState.second
    val locked: Boolean = dialState.third

    fun applyIndexSelection(targetIdx: Int) {
        val clamped = targetIdx.coerceIn(0, valuesList.lastIndex)
        val changed = clamped != currentIdx
        if (isSelectingParam) {
            selectableParams.getOrNull(clamped)?.let { activeParam = it }
        } else {
            when (activeParam) {
                ActiveDialParam.ISO -> isoIndex = clamped
                ActiveDialParam.SHUTTER -> shutterIndex = clamped
                ActiveDialParam.EV -> evIndex = clamped
                ActiveDialParam.WB -> wbIndex = clamped
                ActiveDialParam.FOCUS -> focusIndex = clamped
                ActiveDialParam.ZOOM -> applyZoomRatio(zoomSteps[clamped])
                ActiveDialParam.PRESET -> {
                    visiblePresets.getOrNull(clamped)?.let {
                        selectedPreset = it
                        isBypassed = false
                    }
                }
                ActiveDialParam.EFFECT -> grainLevel = GrainLevel.entries[clamped]
            }
        }
        if (changed) triggerTickHaptic()
    }

    if (showGallery) {
        CapturedGalleryScreen(
            refreshTrigger = galleryRefreshKey,
            onManualRefresh = { galleryRefreshKey += 1 },
            onClose = { showGallery = false },
            onDeletePhoto = { item ->
                ioExecutor.execute {
                    try {
                        appContext.contentResolver.delete(item.uri, null, null)
                        ContextCompat.getMainExecutor(appContext).execute {
                            galleryRefreshKey += 1
                            showToast("Deleted ${item.displayName}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed deleting ${item.uri}", e)
                        showToast("Could not delete photo")
                    }
                }
            }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { viewfinderSize = it }
    ) {
        AndroidView(
            factory = { ctx: Context ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(3)
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                    glSurfaceView = this
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        triggerTapAndFocus(offset)
                    }
                }
        )

        CompositionGridOverlay(gridType = activeGrid)

        focusTapOffset?.let { tapPos ->
            val pulseScale = if (focusLockedSuccess == null) {
                val transition = rememberInfiniteTransition(label = "af_pulse")
                val animated by transition.animateFloat(
                    initialValue = 1.15f,
                    targetValue = 0.92f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 360),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "af_pulse_scale"
                )
                animated
            } else {
                1.0f
            }

            val reticleColor = when (focusLockedSuccess) {
                true -> Color(0xFF4ADE80)
                false -> Color(0xFFF87171)
                null -> CameraAmberAccent
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val baseHalf = 36.dp.toPx() * pulseScale
                    val tick = 9.dp.toPx()
                    drawRect(
                        color = reticleColor,
                        topLeft = Offset(tapPos.x - baseHalf, tapPos.y - baseHalf),
                        size = Size(baseHalf * 2f, baseHalf * 2f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawLine(reticleColor, Offset(tapPos.x - baseHalf, tapPos.y), Offset(tapPos.x - baseHalf + tick, tapPos.y), 2.dp.toPx())
                    drawLine(reticleColor, Offset(tapPos.x + baseHalf - tick, tapPos.y), Offset(tapPos.x + baseHalf, tapPos.y), 2.dp.toPx())
                    drawLine(reticleColor, Offset(tapPos.x, tapPos.y - baseHalf), Offset(tapPos.x, tapPos.y - baseHalf + tick), 2.dp.toPx())
                    drawLine(reticleColor, Offset(tapPos.x, tapPos.y + baseHalf - tick), Offset(tapPos.x, tapPos.y + baseHalf), 2.dp.toPx())
                }

                val statusText = when (focusLockedSuccess) {
                    true -> "AF LOCK"
                    false -> "AF MISS"
                    null -> "AF..."
                }
                Text(
                    text = statusText,
                    color = reticleColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.offset {
                        IntOffset(
                            x = (tapPos.x - 24.dp.toPx()).roundToInt(),
                            y = (tapPos.y + 42.dp.toPx()).roundToInt()
                        )
                    }
                )
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CameraHudDark, RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF3F3F46), RoundedCornerShape(12.dp))
                    .padding(horizontal = 7.dp, vertical = 6.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    HudBadge(
                        label = "ISO",
                        value = if (exposureMode == ExposureMode.AUTO || exposureMode == ExposureMode.P || exposureMode == ExposureMode.A) "AUTO" else isoValues[isoIndex],
                        active = !isSelectingParam && activeParam == ActiveDialParam.ISO,
                        onClick = {
                            triggerTickHaptic()
                            isSelectingParam = false
                            activeParam = ActiveDialParam.ISO
                        }
                    )
                    HudBadge(
                        label = "SHUTTER",
                        value = when (exposureMode) {
                            ExposureMode.B -> "BULB"
                            ExposureMode.A, ExposureMode.P, ExposureMode.AUTO -> "AUTO"
                            else -> shutterLabels[shutterIndex]
                        },
                        active = !isSelectingParam && activeParam == ActiveDialParam.SHUTTER,
                        onClick = {
                            triggerTickHaptic()
                            isSelectingParam = false
                            activeParam = ActiveDialParam.SHUTTER
                        }
                    )
                    HudBadge(
                        label = "EV",
                        value = if (exposureMode == ExposureMode.M || exposureMode == ExposureMode.S || exposureMode == ExposureMode.AUTO) "MAN" else evLabels[evIndex],
                        active = !isSelectingParam && activeParam == ActiveDialParam.EV,
                        onClick = {
                            triggerTickHaptic()
                            isSelectingParam = false
                            activeParam = ActiveDialParam.EV
                        }
                    )
                    HudBadge(
                        label = "FOCUS",
                        value = if (selectedPreset.snapFocusMeters != null) "SNAP" else focusLabels[focusIndex],
                        active = !isSelectingParam && activeParam == ActiveDialParam.FOCUS,
                        onClick = {
                            triggerTickHaptic()
                            isSelectingParam = false
                            activeParam = ActiveDialParam.FOCUS
                        }
                    )
                    HudBadge(
                        label = "ZOOM",
                        value = zoomLabels[zoomIndex],
                        active = !isSelectingParam && activeParam == ActiveDialParam.ZOOM,
                        onClick = {
                            triggerTickHaptic()
                            isSelectingParam = false
                            activeParam = ActiveDialParam.ZOOM
                        }
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val currentMpLabel = mpOptions.getOrNull(selectedMpIndex)?.shortLabel ?: "MAX"
                    Text(
                        text = currentMpLabel,
                        color = if (showMpMenu || selectedMpIndex == 0) CameraAmberAccent else Color(0xFF38BDF8),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (showMpMenu) CameraAmberAccent.copy(alpha = 0.25f) else Color(0xFF27272A))
                            .clickable {
                                triggerTickHaptic()
                                showMpMenu = !showMpMenu
                                showAllGridsMenu = false
                            }
                            .padding(horizontal = 5.dp, vertical = 5.dp)
                    )

                    val flashActive = hasFlashUnit && flashMode != CameraFlashMode.OFF
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (flashActive) CameraAmberAccent.copy(alpha = 0.22f) else Color.Transparent)
                            .clickable(enabled = hasFlashUnit) {
                                triggerTickHaptic()
                                val modes = CameraFlashMode.entries
                                flashMode = modes[(modes.indexOf(flashMode) + 1) % modes.size]
                                showToast("Flash: ${flashMode.label}")
                            }
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = when (flashMode) {
                                CameraFlashMode.OFF -> Icons.Default.FlashOff
                                CameraFlashMode.AUTO -> Icons.Default.FlashAuto
                                CameraFlashMode.ON -> Icons.Default.FlashOn
                                CameraFlashMode.TORCH -> Icons.Default.FlashlightOn
                            },
                            contentDescription = "Flash Mode",
                            tint = when {
                                !hasFlashUnit -> Color(0xFF52525B)
                                flashActive -> CameraAmberAccent
                                else -> Color.LightGray
                            },
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = if (hasFlashUnit) flashMode.label else "N/A",
                            color = when {
                                !hasFlashUnit -> Color(0xFF52525B)
                                flashActive -> CameraAmberAccent
                                else -> Color.LightGray
                            },
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (showAllGridsMenu || activeGrid != CompositionGridType.OFF) CameraAmberAccent.copy(alpha = 0.22f) else Color.Transparent)
                            .clickable {
                                triggerTickHaptic()
                                showAllGridsMenu = !showAllGridsMenu
                                showMpMenu = false
                            }
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridOn,
                            contentDescription = "All Photography Grids",
                            tint = if (showAllGridsMenu || activeGrid != CompositionGridType.OFF) CameraAmberAccent else Color.LightGray,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = activeGrid.label,
                            color = if (showAllGridsMenu || activeGrid != CompositionGridType.OFF) CameraAmberAccent else Color.LightGray,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    IconButton(
                        onClick = {
                            triggerTickHaptic()
                            flashMode = CameraFlashMode.OFF
                            selectedMpIndex = 0
                            desiredLensRole = LensRole.WIDE
                            applyZoomRatio(1.0f)
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Flip Back/Front Camera",
                            tint = CameraAmberAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (showMpMenu) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CameraHudDark, RoundedCornerShape(10.dp))
                        .border(1.dp, CameraAmberAccent.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${boundLens.displayName} • ${mpOptions.size} MODES • ${mpOptions.getOrNull(selectedMpIndex)?.detailLabel ?: "MAX"}",
                        color = CameraAmberAccent,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(mpOptions.indices.toList()) { idx: Int ->
                            val opt = mpOptions[idx]
                            val isSelected = selectedMpIndex == idx
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) CameraAmberAccent else Color(0xFF27272A))
                                    .clickable {
                                        triggerTickHaptic()
                                        selectedMpIndex = idx
                                        showMpMenu = false
                                        showToast("Resolution: ${opt.detailLabel}")
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = opt.detailLabel,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CameraHudDark, RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF3F3F46), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .clickable {
                                triggerTickHaptic()
                                isPresetsExpanded = !isPresetsExpanded
                            }
                            .padding(vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = if (isPresetsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isPresetsExpanded) "Collapse Presets" else "Expand Presets",
                            tint = CameraAmberAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${exposureMode.shortLabel} • ${boundLens.displayName} • ${if (isBypassed) "BYPASS" else selectedPreset.name}",
                            color = CameraAmberAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (selectedPreset.specialMode == SpecialShaderMode.DOUBLE_EXPOSURE && !isBypassed) {
                            Text(
                                text = if (hasShotOne) "DBL 1/2" else "DBL 0/2",
                                color = if (hasShotOne) Color(0xFF4ADE80) else CameraAmberAccent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color(0xFF27272A))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            Text(
                                text = boundLens.displayName,
                                color = when (boundLens.role) {
                                    LensRole.ULTRA_WIDE -> Color(0xFF38BDF8)
                                    LensRole.TELEPHOTO -> Color(0xFFFBBF24)
                                    else -> CameraAmberAccent
                                },
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color(0xFF27272A))
                                    .clickable {
                                        triggerTickHaptic()
                                        when (boundLens.role) {
                                            LensRole.ULTRA_WIDE -> applyZoomRatio(1.0f)
                                            LensRole.WIDE -> if (boundLens.hasTelephoto) applyZoomRatio(3.0f) else applyZoomRatio(0.5f)
                                            LensRole.TELEPHOTO -> applyZoomRatio(0.5f)
                                            LensRole.FRONT -> Unit
                                        }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (exposureMode == ExposureMode.B) {
                            val mins = bulbElapsedTenths / 600
                            val secs = (bulbElapsedTenths % 600) / 10
                            val tenths = bulbElapsedTenths % 10
                            Text(
                                text = String.format(Locale.US, "BULB %02d:%02d.%d", mins, secs, tenths),
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color(0xFF991B1B))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = if (isBypassed) "BYPASS ON" else "BYPASS",
                            color = if (isBypassed) Color.Black else Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (isBypassed) CameraAmberAccent else Color(0xFF27272A))
                                .clickable {
                                    triggerTickHaptic()
                                    isBypassed = !isBypassed
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (isPresetsExpanded) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        contentPadding = PaddingValues(vertical = 1.dp)
                    ) {
                        items(FilterCatalog.categories) { category: String ->
                            val active = selectedCategory == category
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (active) CameraAmberAccent else Color(0xFF27272A))
                                    .clickable {
                                        triggerTickHaptic()
                                        selectedCategory = category
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = category,
                                    color = if (active) Color.Black else Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        contentPadding = PaddingValues(vertical = 1.dp)
                    ) {
                        items(visiblePresets) { preset: FilmPreset ->
                            val active = !isBypassed && selectedPreset.code == preset.code
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (active) CameraAmberAccent else Color(0xFF18181B))
                                    .border(
                                        width = 1.dp,
                                        color = if (active) CameraAmberAccent else Color(0xFF3F3F46),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        triggerTickHaptic()
                                        selectedPreset = preset
                                        isBypassed = false
                                        activeParam = ActiveDialParam.PRESET
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = preset.code,
                                    color = if (active) Color.Black else Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            if ((selectedPreset.pixelBitDepth != PixelBitDepth.OFF || pixelBitDepth != PixelBitDepth.OFF) && !isBypassed) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier
                        .background(CameraHudDark, RoundedCornerShape(10.dp))
                        .border(1.dp, CameraAmberAccent.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "PIXEL:",
                        color = Color(0xFFA1A1AA),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    listOf(
                        PixelBitDepth.BIT_1,
                        PixelBitDepth.BIT_4,
                        PixelBitDepth.BIT_8,
                        PixelBitDepth.BIT_16,
                        PixelBitDepth.BIT_128
                    ).forEach { bit: PixelBitDepth ->
                        val active = pixelBitDepth == bit
                        Text(
                            text = bit.label,
                            color = if (active) CameraAmberAccent else Color.White,
                            fontSize = 9.sp,
                            fontWeight = if (active) FontWeight.Black else FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.clickable {
                                triggerTickHaptic()
                                pixelBitDepth = bit
                            }
                        )
                    }
                }
            }

            if (showAllGridsMenu) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CameraHudDark, RoundedCornerShape(10.dp))
                        .border(1.dp, CameraAmberAccent.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    CompositionGridType.entries.forEach { grid: CompositionGridType ->
                        val isSelected = activeGrid == grid
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) CameraAmberAccent else Color.Transparent)
                                .clickable {
                                    triggerTickHaptic()
                                    activeGrid = grid
                                    showAllGridsMenu = false
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = grid.label,
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            if (selectedPreset.specialMode == SpecialShaderMode.SIN_CITY && !isBypassed) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CameraHudDark, RoundedCornerShape(10.dp))
                        .border(1.dp, CameraAmberAccent.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "HUE ${sinCityHueDegrees.toInt()}°",
                            color = CameraAmberAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            sinCityQuickColors.forEach { item: SinCityQuickColor ->
                                val selected = abs(sinCityHueDegrees - item.hueDegrees) < 4f
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(item.color)
                                        .border(
                                            width = if (selected) 2.dp else 1.dp,
                                            color = if (selected) Color.White else Color.White.copy(alpha = 0.35f),
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            triggerTickHaptic()
                                            sinCityHueDegrees = item.hueDegrees
                                        }
                                )
                            }
                        }
                    }

                    Slider(
                        value = sinCityHueDegrees,
                        onValueChange = { hue: Float -> sinCityHueDegrees = hue },
                        valueRange = 0f..360f,
                        modifier = Modifier.height(22.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = CameraAmberAccent,
                            activeTrackColor = CameraAmberAccent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.22f)
                        )
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SinCityNeutralMode.entries.forEach { mode: SinCityNeutralMode ->
                            val isSelected = sinCityNeutralMode == mode
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) CameraAmberAccent else Color(0xFF27272A))
                                    .clickable {
                                        triggerTickHaptic()
                                        sinCityNeutralMode = mode
                                    }
                                    .padding(vertical = 5.dp)
                            ) {
                                Text(
                                    text = mode.label,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            if (selectedPreset.specialMode == SpecialShaderMode.DOUBLE_EXPOSURE && !isBypassed) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CameraHudDark, RoundedCornerShape(10.dp))
                        .border(1.dp, CameraAmberAccent.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "BLEND ${(dblStrength * 100).toInt()}% • ${dblBlendMode.shortDescription}",
                            color = CameraAmberAccent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )

                        if (hasShotOne) {
                            Text(
                                text = "RESET 1/2",
                                color = Color(0xFFF87171),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF27272A))
                                    .clickable {
                                        triggerTickHaptic()
                                        resetDoubleExposureState()
                                        showToast("Cleared Shot 1")
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Slider(
                        value = dblStrength,
                        onValueChange = { value: Float -> dblStrength = value },
                        valueRange = 0.15f..1.0f,
                        modifier = Modifier.height(22.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = CameraAmberAccent,
                            activeTrackColor = CameraAmberAccent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.22f)
                        )
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        DoubleExposureBlendMode.entries.forEach { mode: DoubleExposureBlendMode ->
                            val isSelected = dblBlendMode == mode
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) CameraAmberAccent else Color(0xFF27272A))
                                    .clickable {
                                        triggerTickHaptic()
                                        dblBlendMode = mode
                                    }
                                    .padding(vertical = 5.dp)
                            ) {
                                Text(
                                    text = mode.label,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            MovingLensZoomRuler(
                zoomSteps = zoomSteps,
                currentIndex = zoomIndex,
                isActive = !isSelectingParam && activeParam == ActiveDialParam.ZOOM,
                onSelectIndex = { idx: Int ->
                    val clamped = idx.coerceIn(0, zoomSteps.lastIndex)
                    if (clamped != zoomIndex) triggerTickHaptic()
                    isSelectingParam = false
                    activeParam = ActiveDialParam.ZOOM
                    applyZoomRatio(zoomSteps[clamped])
                }
            )

            SpacedCircularArcValues(
                title = if (isSelectingParam) "SELECT PARAMETER" else activeParam.label,
                values = valuesList,
                selectedIndex = currentIdx,
                isLocked = !isSelectingParam && locked,
                isSelectingParam = isSelectingParam,
                onSelectIndex = { idx: Int -> applyIndexSelection(idx) },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                RotatingModeDialWithFixedTopLine(
                    currentMode = exposureMode,
                    onModeSelected = { newMode: ExposureMode ->
                        if (newMode != exposureMode) triggerTickHaptic()
                        isSelectingParam = false
                        exposureMode = newMode
                        activeParam = when (newMode) {
                            ExposureMode.M -> ActiveDialParam.SHUTTER
                            ExposureMode.A -> ActiveDialParam.EV
                            ExposureMode.S -> ActiveDialParam.SHUTTER
                            ExposureMode.P -> ActiveDialParam.EV
                            ExposureMode.B -> ActiveDialParam.EV
                            ExposureMode.AUTO -> ActiveDialParam.ZOOM
                        }
                    }
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val latestPhoto by produceState<CapturedPhotoItem?>(
                        initialValue = null,
                        key1 = galleryRefreshKey
                    ) {
                        value = queryCameraPhotos(appContext).firstOrNull()
                    }
                    val latestThumb by produceState<Bitmap?>(
                        initialValue = null,
                        key1 = latestPhoto?.uri
                    ) {
                        value = latestPhoto?.let { loadPhotoBitmap(appContext, it.uri, maxDim = 240) }
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF18181B))
                            .border(1.5.dp, CameraAmberAccent.copy(alpha = 0.75f), RoundedCornerShape(10.dp))
                            .clickable {
                                triggerTickHaptic()
                                galleryRefreshKey += 1
                                showGallery = true
                            }
                    ) {
                        if (latestThumb != null) {
                            Image(
                                bitmap = latestThumb!!.asImageBitmap(),
                                contentDescription = "View Taken Photos",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = "ROLL",
                                color = CameraAmberAccent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(78.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF141416))
                            .border(2.5.dp, Color(0xFFD4D4D8), CircleShape)
                            .clickable {
                                triggerTickHaptic()
                                shutterSound.play(MediaActionSound.SHUTTER_CLICK)
                                if (exposureMode == ExposureMode.B) {
                                    if (!isBulbRunning) {
                                        isBulbRunning = true
                                        showToast("Bulb exposure started")
                                    } else {
                                        isBulbRunning = false
                                        capturePhotoNow()
                                    }
                                } else {
                                    capturePhotoNow()
                                }
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = when {
                                            isBulbRunning -> listOf(Color(0xFFEF4444), Color(0xFF991B1B))
                                            hasShotOne -> listOf(Color(0xFFFBBF24), CameraAmberAccent)
                                            else -> listOf(Color(0xFFF87171), Color(0xFFDC2626))
                                        }
                                    )
                                )
                        )
                    }
                }

                RotatingJogWheelWithTicks(
                    enabled = isSelectingParam || !locked,
                    isSelectingParam = isSelectingParam,
                    currentIndex = currentIdx,
                    maxIndex = valuesList.lastIndex.coerceAtLeast(0),
                    onCenterClick = {
                        triggerTickHaptic()
                        isSelectingParam = !isSelectingParam
                    },
                    onSelectIndex = { targetIdx: Int -> applyIndexSelection(targetIdx) }
                )
            }
        }
    }
}

@Composable
private fun CompositionGridOverlay(gridType: CompositionGridType) {
    if (gridType == CompositionGridType.OFF) return

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val lineColor = Color.White.copy(alpha = 0.28f)
        val accentColor = CameraAmberAccent.copy(alpha = 0.72f)

        when (gridType) {
            CompositionGridType.OFF -> Unit
            CompositionGridType.THIRDS -> {
                drawLine(lineColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 1.2f)
                drawLine(lineColor, Offset(2f * w / 3f, 0f), Offset(2f * w / 3f, h), strokeWidth = 1.2f)
                drawLine(lineColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 1.2f)
                drawLine(lineColor, Offset(0f, 2f * h / 3f), Offset(w, 2f * h / 3f), strokeWidth = 1.2f)
            }
            CompositionGridType.GOLDEN -> {
                val phi = 1.61803398875
                val b = ln(phi) / (PI / 2.0)
                val spiralPath = Path()
                val scale = min(w, h) * 0.24
                val originX = w * 0.62
                val originY = h * 0.44
                val steps = 160
                for (i in 0..steps) {
                    val t = (i.toDouble() / steps.toDouble()) * (3.0 * PI)
                    val r = scale * exp(-b * t)
                    val px = (originX - r * cos(t)).toFloat()
                    val py = (originY - r * sin(t)).toFloat()
                    if (i == 0) spiralPath.moveTo(px, py) else spiralPath.lineTo(px, py)
                }
                drawPath(path = spiralPath, color = accentColor, style = Stroke(width = 2.2f))
                drawLine(lineColor, Offset(w * 0.382f, 0f), Offset(w * 0.382f, h), strokeWidth = 1f)
                drawLine(lineColor, Offset(w * 0.618f, 0f), Offset(w * 0.618f, h), strokeWidth = 1f)
                drawLine(lineColor, Offset(0f, h * 0.382f), Offset(w, h * 0.382f), strokeWidth = 1f)
                drawLine(lineColor, Offset(0f, h * 0.618f), Offset(w, h * 0.618f), strokeWidth = 1f)
            }
            CompositionGridType.DIAGONAL -> {
                drawLine(lineColor, Offset(0f, 0f), Offset(w, h), strokeWidth = 1.2f)
                drawLine(lineColor, Offset(w, 0f), Offset(0f, h), strokeWidth = 1.2f)
            }
            CompositionGridType.CENTER -> {
                drawLine(lineColor, Offset(w / 2f - 28f, h / 2f), Offset(w / 2f + 28f, h / 2f), strokeWidth = 1.5f)
                drawLine(lineColor, Offset(w / 2f, h / 2f - 28f), Offset(w / 2f, h / 2f + 28f), strokeWidth = 1.5f)
                drawCircle(
                    color = accentColor,
                    radius = 18f,
                    center = Offset(w / 2f, h / 2f),
                    style = Stroke(width = 1.4f)
                )
            }
        }
    }
}

@Composable
private fun MovingLensZoomRuler(
    zoomSteps: List<Float>,
    currentIndex: Int,
    isActive: Boolean,
    onSelectIndex: (Int) -> Unit
) {
    val safeIdx = currentIndex.coerceIn(0, zoomSteps.lastIndex)
    val currentRatio = zoomSteps[safeIdx]
    val ratioText = String.format(Locale.US, "%.1fx", currentRatio)
    val progress = (safeIdx.toFloat() / zoomSteps.lastIndex.coerceAtLeast(1)).coerceIn(0f, 1f)

    val latestIndex by rememberUpdatedState(safeIdx)
    val latestOnSelectIndex by rememberUpdatedState(onSelectIndex)
    var dragStartIdx by remember { mutableIntStateOf(safeIdx) }
    var cumulativeDragX by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF1E1F24), Color(0xFF0E0F12))
                )
            )
            .border(
                width = 1.dp,
                color = if (isActive) CameraAmberAccent.copy(alpha = 0.7f) else Color(0xFF34363E),
                shape = RoundedCornerShape(14.dp)
            )
            .pointerInput(zoomSteps) {
                detectDragGestures(
                    onDragStart = {
                        dragStartIdx = latestIndex
                        cumulativeDragX = 0f
                    },
                    onDragEnd = { cumulativeDragX = 0f },
                    onDragCancel = { cumulativeDragX = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        cumulativeDragX += dragAmount.x
                        val usableWidth = (size.width - 48f).coerceAtLeast(120f)
                        val exactDelta = (cumulativeDragX / usableWidth) * zoomSteps.lastIndex
                        val target = (dragStartIdx + exactDelta.toInt()).coerceIn(0, zoomSteps.lastIndex)
                        if (target != latestIndex) {
                            latestOnSelectIndex(target)
                        }
                    }
                )
            }
            .pointerInput(zoomSteps) {
                detectTapGestures { offset ->
                    val paddingX = 24f
                    val usableW = (size.width - paddingX * 2f).coerceAtLeast(1f)
                    val fraction = ((offset.x - paddingX) / usableW).coerceIn(0f, 1f)
                    val idx = (fraction * zoomSteps.lastIndex).roundToInt().coerceIn(0, zoomSteps.lastIndex)
                    latestOnSelectIndex(idx)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val paddingX = 24f
            val usableW = (w - paddingX * 2f).coerceAtLeast(1f)
            val totalTicks = 45
            val shiftPx = (progress - 0.5f) * usableW * 0.65f

            for (i in 0..totalTicks) {
                val frac = i.toFloat() / totalTicks.toFloat()
                val rawX = paddingX + frac * usableW - shiftPx
                if (rawX < paddingX - 6f || rawX > w - paddingX + 6f) continue
                val isMajor = i % 15 == 0
                val isMedium = i % 5 == 0
                val tickHeight = when {
                    isMajor -> h * 0.44f
                    isMedium -> h * 0.30f
                    else -> h * 0.18f
                }
                val tickColor = if (isMajor) Color(0xFFE4E4E7) else Color(0xFF71717A)
                drawLine(
                    color = tickColor,
                    start = Offset(rawX, h - tickHeight),
                    end = Offset(rawX, h),
                    strokeWidth = if (isMajor) 2f else 1.2f
                )
            }

            val indicatorX = paddingX + progress * usableW
            drawLine(
                color = Color(0xFFEF4444),
                start = Offset(indicatorX, 4f),
                end = Offset(indicatorX, h),
                strokeWidth = 2.4f
            )
        }

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp)
        ) {
            Text(
                text = "0.5x",
                color = Color(0xFFA1A1AA),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "1x",
                color = Color(0xFFA1A1AA),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, start = 20.dp, end = 20.dp)
        ) {
            val offsetDp = ((progress - 0.5f) * 180f).roundToInt().dp
            Text(
                text = ratioText,
                color = if (isActive) CameraAmberAccent else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = offsetDp)
            )
        }
    }
}

@Composable
private fun SpacedCircularArcValues(
    title: String,
    values: List<String>,
    selectedIndex: Int,
    isLocked: Boolean,
    isSelectingParam: Boolean,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeIdx = selectedIndex.coerceIn(0, (values.size - 1).coerceAtLeast(0))
    val arcItems = remember(values, safeIdx) {
        (-2..2).mapNotNull { offset: Int ->
            val idx = safeIdx + offset
            if (idx in values.indices) Triple(offset, idx, values[idx]) else null
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .pointerInput(values, safeIdx) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    val step = (-dragAmount / 24f).roundToInt()
                    if (step != 0) {
                        onSelectIndex((safeIdx + step).coerceIn(0, values.lastIndex))
                    }
                }
            }
    ) {
        Text(
            text = if (isLocked) "$title (AUTO)" else title,
            color = if (isSelectingParam) Color(0xFF38BDF8) else CameraAmberAccent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(width = 12.dp, height = 7.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                }
                drawPath(path = path, color = CameraAmberAccent)
            }
        }

        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
        ) {
            arcItems.forEach { (offset: Int, idx: Int, label: String) ->
                val isCenter = offset == 0
                val thetaRad = (offset * 0.24f).toDouble()
                val arcRadiusPx = 140f
                val xOffsetPx = (offset * 155f).roundToInt()
                val yOffsetPx = ((1.0 - cos(thetaRad)) * arcRadiusPx * 0.45f).roundToInt()
                val itemScale = when (abs(offset)) {
                    0 -> 1.08f
                    1 -> 0.94f
                    else -> 0.82f
                }
                val itemAlpha = when (abs(offset)) {
                    0 -> 1f
                    1 -> 0.82f
                    else -> 0.56f
                }

                Text(
                    text = label,
                    color = when {
                        isCenter && isLocked -> Color(0xFF71717A)
                        isCenter -> CameraAmberAccent
                        else -> Color(0xFFD4D4D8)
                    },
                    fontSize = if (isCenter) 13.sp else 11.sp,
                    fontWeight = if (isCenter) FontWeight.Black else FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset { IntOffset(x = xOffsetPx, y = yOffsetPx) }
                        .scale(itemScale)
                        .alpha(itemAlpha)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSelectIndex(idx) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun RotatingModeDialWithFixedTopLine(
    currentMode: ExposureMode,
    onModeSelected: (ExposureMode) -> Unit
) {
    val modes = ExposureMode.entries
    val stepAngleDeg = 360f / modes.size
    val initialIndex = modes.indexOf(currentMode).coerceAtLeast(0)
    var dialAngleDeg by remember { mutableFloatStateOf(-initialIndex * stepAngleDeg) }
    var lastTouchAngleDeg by remember { mutableStateOf<Float?>(null) }

    val latestCurrentMode by rememberUpdatedState(currentMode)
    val latestOnModeSelected by rememberUpdatedState(onModeSelected)

    LaunchedEffect(currentMode) {
        val idx = modes.indexOf(currentMode).coerceAtLeast(0)
        val targetAngle = -idx * stepAngleDeg
        val currentStep = ((dialAngleDeg / stepAngleDeg).roundToInt() % modes.size + modes.size) % modes.size
        if (currentStep != idx) {
            dialAngleDeg = targetAngle
        }
    }

    fun syncModeFromAngle(angleDeg: Float) {
        val rawStep = (-angleDeg / stepAngleDeg).roundToInt()
        val wrappedIdx = ((rawStep % modes.size) + modes.size) % modes.size
        val mode = modes[wrappedIdx]
        if (mode != latestCurrentMode) {
            latestOnModeSelected(mode)
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(Color(0xFF141519))
            .border(1.5.dp, Color(0xFF3F3F46), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        lastTouchAngleDeg = Math.toDegrees(
                            atan2((offset.y - center.y).toDouble(), (offset.x - center.x).toDouble())
                        ).toFloat()
                    },
                    onDragEnd = { lastTouchAngleDeg = null },
                    onDragCancel = { lastTouchAngleDeg = null },
                    onDrag = { change, _ ->
                        change.consume()
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val currAngle = Math.toDegrees(
                            atan2(
                                (change.position.y - center.y).toDouble(),
                                (change.position.x - center.x).toDouble()
                            )
                        ).toFloat()
                        val prevAngle = lastTouchAngleDeg
                        if (prevAngle != null) {
                            var delta = currAngle - prevAngle
                            if (delta > 180f) delta -= 360f
                            if (delta < -180f) delta += 360f
                            dialAngleDeg += delta
                            syncModeFromAngle(dialAngleDeg)
                        }
                        lastTouchAngleDeg = currAngle
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val dx = offset.x - center.x
                    val curIdx = modes.indexOf(latestCurrentMode).coerceAtLeast(0)
                    val nextIdx = if (dx >= 0f) {
                        (curIdx + 1) % modes.size
                    } else {
                        (curIdx - 1 + modes.size) % modes.size
                    }
                    dialAngleDeg = -nextIdx * stepAngleDeg
                    latestOnModeSelected(modes[nextIdx])
                }
            }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            val orbitRadiusPx = 68f
            modes.forEachIndexed { idx: Int, mode: ExposureMode ->
                val angleRad = ((-90.0 + idx * stepAngleDeg + dialAngleDeg) * (PI / 180.0))
                val xPx = (cos(angleRad) * orbitRadiusPx).roundToInt()
                val yPx = (sin(angleRad) * orbitRadiusPx).roundToInt()
                val isUnderFixedLine = mode == currentMode

                Text(
                    text = mode.shortLabel,
                    color = if (isUnderFixedLine) CameraAmberAccent else Color(0xFFA1A1AA),
                    fontSize = if (isUnderFixedLine) 11.sp else 9.sp,
                    fontWeight = if (isUnderFixedLine) FontWeight.Black else FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset { IntOffset(x = xPx, y = yPx) }
                )
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawLine(
                color = CameraAmberAccent,
                start = Offset(center.x, 3f),
                end = Offset(center.x, 18f),
                strokeWidth = 2.6f
            )
            drawCircle(
                color = Color(0xFF27272A),
                radius = size.minDimension * 0.20f,
                center = center,
                style = Stroke(width = 1.4f)
            )
        }
    }
}

@Composable
private fun RotatingJogWheelWithTicks(
    enabled: Boolean,
    isSelectingParam: Boolean,
    currentIndex: Int,
    maxIndex: Int,
    onCenterClick: () -> Unit,
    onSelectIndex: (Int) -> Unit
) {
    val ringColor = if (isSelectingParam) Color(0xFF38BDF8) else CameraAmberAccent
    var wheelAngleDeg by remember { mutableFloatStateOf(0f) }
    var dragStartWheelAngleDeg by remember { mutableFloatStateOf(0f) }
    var dragStartIndex by remember { mutableIntStateOf(currentIndex) }
    var lastTouchAngleDeg by remember { mutableStateOf<Float?>(null) }
    val degreesPerStep = 24f

    val latestEnabled by rememberUpdatedState(enabled)
    val latestCurrentIndex by rememberUpdatedState(currentIndex)
    val latestMaxIndex by rememberUpdatedState(maxIndex)
    val latestOnSelectIndex by rememberUpdatedState(onSelectIndex)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(Color(0xFF141519))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (!latestEnabled) return@detectDragGestures
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val startAngle = Math.toDegrees(
                            atan2((offset.y - center.y).toDouble(), (offset.x - center.x).toDouble())
                        ).toFloat()
                        lastTouchAngleDeg = startAngle
                        dragStartWheelAngleDeg = wheelAngleDeg
                        dragStartIndex = latestCurrentIndex
                    },
                    onDragEnd = {
                        lastTouchAngleDeg = null
                    },
                    onDragCancel = {
                        lastTouchAngleDeg = null
                    },
                    onDrag = { change, _ ->
                        if (!latestEnabled) return@detectDragGestures
                        change.consume()
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val currAngle = Math.toDegrees(
                            atan2(
                                (change.position.y - center.y).toDouble(),
                                (change.position.x - center.x).toDouble()
                            )
                        ).toFloat()
                        val prevAngle = lastTouchAngleDeg
                        if (prevAngle != null) {
                            var frameDelta = currAngle - prevAngle
                            if (frameDelta > 180f) frameDelta -= 360f
                            if (frameDelta < -180f) frameDelta += 360f
                            wheelAngleDeg += frameDelta

                            val totalRotationFromStart = wheelAngleDeg - dragStartWheelAngleDeg
                            val stepOffset = (totalRotationFromStart / degreesPerStep).toInt()
                            val targetIdx = (dragStartIndex + stepOffset).coerceIn(0, latestMaxIndex)
                            if (targetIdx != latestCurrentIndex) {
                                latestOnSelectIndex(targetIdx)
                            }
                        }
                        lastTouchAngleDeg = currAngle
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val outerR = size.minDimension / 2f - 4f
            val innerR = outerR - 9f
            val ticks = 36
            val rotRad = wheelAngleDeg * (PI / 180.0)
            for (i in 0 until ticks) {
                val rad = (i * (360f / ticks)) * (PI / 180.0) + rotRad
                val isMajor = i % 6 == 0
                drawLine(
                    color = when {
                        !enabled -> Color(0xFF3F3F46)
                        isMajor -> ringColor
                        else -> Color(0xFFA1A1AA)
                    },
                    start = Offset(
                        center.x + (cos(rad) * innerR).toFloat(),
                        center.y + (sin(rad) * innerR).toFloat()
                    ),
                    end = Offset(
                        center.x + (cos(rad) * outerR).toFloat(),
                        center.y + (sin(rad) * outerR).toFloat()
                    ),
                    strokeWidth = if (isMajor) 2f else 1.4f
                )
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(Color(0xFF18191E))
                .border(1.8.dp, if (enabled) ringColor else Color(0xFF52525B), CircleShape)
                .clickable { onCenterClick() }
        ) {
            Text(
                text = if (isSelectingParam) "PARAM" else "VALUE",
                color = if (enabled) ringColor else Color(0xFF71717A),
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun HudBadge(
    label: String,
    value: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) CameraAmberAccent.copy(alpha = 0.2f) else Color.Transparent)
            .border(
                width = if (active) 1.dp else 0.dp,
                color = if (active) CameraAmberAccent else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = Color(0xFFA1A1AA),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = if (active) CameraAmberAccent else Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
    }
}