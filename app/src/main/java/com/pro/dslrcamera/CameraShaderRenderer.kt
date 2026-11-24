package com.pro.dslrcamera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.net.Uri
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig as JavaxEGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs

enum class SpecialShaderMode {
    NONE,
    SIN_CITY,
    DOUBLE_EXPOSURE
}

enum class DoubleExposureBlendMode(val label: String, val shortDescription: String) {
    HIGH_KEY_SILHOUETTE("SILHOUETTE", "Dark silhouette reveals second shot"),
    ADDITIVE("ADDITIVE", "Classic film light addition"),
    SCREEN("SCREEN", "Luminous overlap blend"),
    MULTIPLY("MULTIPLY", "Dense dark overlap")
}

enum class SinCityNeutralMode(
    val label: String,
    val satMin: Float,
    val satMax: Float,
    val feather: Float,
    val liftBlack: Float
) {
    STRICT_POP("STRICT POP", satMin = 0.22f, satMax = 0.42f, feather = 0.005f, liftBlack = 0.0f),
    NOIR_FADE("NOIR FADE", satMin = 0.22f, satMax = 0.42f, feather = 0.007f, liftBlack = 0.08f),
    SOFT_EDGE("SOFT EDGE", satMin = 0.18f, satMax = 0.38f, feather = 0.012f, liftBlack = 0.0f)
}

class CameraShaderRenderer(
    private val onSurfaceTextureReady: (SurfaceTexture) -> Unit,
    private val onRequestRender: (() -> Unit)? = null
) : GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "CameraShaderRenderer"
        val DEFAULT_CAMERA_RELATIVE_PATH: String = "${Environment.DIRECTORY_DCIM}/Camera"
    }

    @Volatile var isFrontCamera: Boolean = false
    @Volatile var mirrorFrontCamera: Boolean = false
    @Volatile var activePreset: FilmPreset = FilterCatalog.presets.first()
    @Volatile var bypassToNormal: Boolean = false
    @Volatile var cameraAspectRatio: Float = 3f / 4f

    // Hue in degrees [0..360]: 0 = Red, 56 = Yellow, 132 = Green, 215 = Blue
    @Volatile var sinCityTargetHue: Float = 0f
    @Volatile var sinCityTolerance: Float = 0.032f
    @Volatile var sinCityNeutralMode: SinCityNeutralMode = SinCityNeutralMode.STRICT_POP

    // Double Exposure state
    @Volatile var doubleExposureBlendMode: DoubleExposureBlendMode = DoubleExposureBlendMode.HIGH_KEY_SILHOUETTE
    @Volatile var doubleExposureStrength: Float = 0.85f
    @Volatile private var hasLockedDoubleExposure: Boolean = false
    private val pendingLockBitmap = AtomicReference<Bitmap?>(null)
    @Volatile private var clearDoubleExposureRequested: Boolean = false
    private var dblTextureId = 0

    @Volatile var grainLevel: GrainLevel = GrainLevel.OFF
    @Volatile var pixelBitDepth: PixelBitDepth = PixelBitDepth.OFF
    @Volatile var filmEffect: FilmEffectType = FilmEffectType.OFF
    @Volatile var rippedEffect: RippedEffectType = RippedEffectType.OFF

    private var viewportWidth = 0
    private var viewportHeight = 0
    private val pendingCaptureCallback = AtomicReference<((Bitmap?) -> Unit)?>(null)

    private var oesTextureId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var programId = 0
    private val texMatrix = FloatArray(16)

    private val quadCoords = floatArrayOf(
        -1f,  1f,
        -1f, -1f,
        1f,  1f,
        1f, -1f
    )
    private val quadTexCoords = floatArrayOf(
        0f, 1f,
        0f, 0f,
        1f, 1f,
        1f, 0f
    )

    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(quadCoords.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(quadCoords)
            position(0)
        }
    private val texCoordBuffer: FloatBuffer = ByteBuffer.allocateDirect(quadTexCoords.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(quadTexCoords)
            position(0)
        }

    private val vertexShaderCode = """
        #version 300 es
        layout(location = 0) in vec4 aPosition;
        layout(location = 1) in vec2 aTexCoord;
        uniform mat4 uTexMatrix;
        uniform vec2 uAspectScale;
        uniform int uFlipHorizontal;
        out vec2 vTexCoord;
        out vec2 vCleanCoord;
        void main() {
            gl_Position = aPosition;

            vec2 centered = (aTexCoord - 0.5) * uAspectScale + 0.5;
            if (uFlipHorizontal == 1) {
                centered.x = 1.0 - centered.x;
            }
            vCleanCoord = clamp(centered, 0.0, 1.0);

            vec4 transformed = uTexMatrix * vec4(vCleanCoord, 0.0, 1.0);
            vTexCoord = transformed.xy;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        #version 300 es
        #extension GL_OES_EGL_image_external_essl3 : require
        precision highp float;

        in vec2 vTexCoord;
        in vec2 vCleanCoord;

        uniform samplerExternalOES uCamTexture;
        uniform sampler2D uDblTexture;
        uniform int uDblActive;
        uniform int uDblBlendMode;
        uniform float uDblStrength;

        uniform int uSinCityActive;
        uniform float uTargetHue;
        uniform float uHueTolerance;
        uniform float uEdgeFeather;
        uniform float uSatMin;
        uniform float uSatMax;
        uniform float uLiftBlack;

        uniform float uPixelGridSize;
        uniform float uColorLevels;

        uniform float uContrast;
        uniform float uSaturation;
        uniform float uWarmth;

        out vec4 fragColor;

        vec3 rgb2hsv(vec3 c) {
            float cMax = max(c.r, max(c.g, c.b));
            float cMin = min(c.r, min(c.g, c.b));
            float delta = cMax - cMin;
            float h = 0.0;
            if (delta > 0.000001) {
                if (cMax == c.r) {
                    h = mod((c.g - c.b) / delta, 6.0);
                } else if (cMax == c.g) {
                    h = (c.b - c.r) / delta + 2.0;
                } else {
                    h = (c.r - c.g) / delta + 4.0;
                }
                h /= 6.0;
                if (h < 0.0) h += 1.0;
            }
            float s = (cMax <= 0.000001) ? 0.0 : (delta / cMax);
            return vec3(h, s, cMax);
        }

        float circularHueDist(float a, float b) {
            float diff = abs(a - b);
            return min(diff, 1.0 - diff);
        }

        vec3 applyColorAdjust(vec3 c) {
            vec3 outCol = c;
            outCol.r = clamp(outCol.r + uWarmth * 0.5, 0.0, 1.0);
            outCol.b = clamp(outCol.b - uWarmth * 0.5, 0.0, 1.0);
            outCol = clamp((outCol - 0.5) * uContrast + 0.5, 0.0, 1.0);
            return clamp(outCol, 0.0, 1.0);
        }

        vec3 toMonoContrast(vec3 c, float contrastAmt, float bgLift) {
            float luma = dot(c, vec3(0.299, 0.587, 0.114));
            float lifted = clamp(luma + bgLift, 0.0, 1.0);
            float highC = clamp((lifted - 0.5) * contrastAmt + 0.5, 0.0, 1.0);
            return vec3(highC);
        }

        vec3 blendDoubleExp(vec3 shot1, vec3 shot2, int mode, float strength) {
            if (mode == 0) {
                vec3 mono1 = toMonoContrast(shot1, 1.45, 0.12);
                vec3 mono2 = toMonoContrast(shot2, 1.28, 0.0);
                float lum1 = mono1.r;

                float brightMask = smoothstep(0.42, 0.86, lum1);
                vec3 insideSubject = mix(mono1, mono2, clamp(strength, 0.0, 1.0));
                vec3 whiteBackdrop = vec3(clamp(smoothstep(0.55, 0.92, lum1) * 0.15 + 0.85, 0.0, 1.0));
                return mix(insideSubject, whiteBackdrop, brightMask);
            } else if (mode == 1) {
                return clamp(shot1 + shot2 * strength, 0.0, 1.0);
            } else if (mode == 2) {
                vec3 screened = 1.0 - (1.0 - shot1) * (1.0 - shot2);
                return mix(shot1, screened, strength);
            } else {
                vec3 multiplied = shot1 * shot2;
                return mix(shot1, multiplied, strength);
            }
        }

        void main() {
            vec2 sampleUv = vTexCoord;
            if (uPixelGridSize > 1.0) {
                sampleUv = (floor(sampleUv * uPixelGridSize) + 0.5) / uPixelGridSize;
            }

            vec3 camCol = texture(uCamTexture, sampleUv).rgb;
            camCol = applyColorAdjust(camCol);

            if (uColorLevels > 1.0) {
                camCol = floor(camCol * uColorLevels + 0.0001) / max(1.0, uColorLevels - 1.0);
                camCol = clamp(camCol, 0.0, 1.0);
            }

            if (uSinCityActive == 1) {
                vec3 hsv = rgb2hsv(camCol);
                float chroma = max(camCol.r, max(camCol.g, camCol.b)) - min(camCol.r, min(camCol.g, camCol.b));
                float hDist = circularHueDist(hsv.x, uTargetHue);
                float hueMask = 1.0 - smoothstep(
                    max(0.0, uHueTolerance - uEdgeFeather),
                    uHueTolerance + uEdgeFeather,
                    hDist
                );
                float satMask = smoothstep(uSatMin, uSatMax, hsv.y);
                float chromaMask = smoothstep(0.08, 0.22, chroma);
                float valMask = smoothstep(0.06, 0.16, hsv.z) * (1.0 - smoothstep(0.95, 1.0, hsv.z));
                float mask = clamp(hueMask * satMask * chromaMask * valMask, 0.0, 1.0);

                float luma = dot(camCol, vec3(0.299, 0.587, 0.114));
                float lifted = luma * (1.0 - uLiftBlack) + uLiftBlack;
                vec3 bw = vec3(clamp((lifted - 0.5) * 1.24 + 0.5, 0.0, 1.0));

                vec3 popped = mix(vec3(luma), camCol, 1.12);
                camCol = mix(bw, popped, mask);
            } else {
                float luma = dot(camCol, vec3(0.299, 0.587, 0.114));
                camCol = mix(vec3(luma), camCol, uSaturation);
            }

            if (uDblActive == 1) {
                vec2 dblUv = vec2(vCleanCoord.x, 1.0 - vCleanCoord.y);
                vec3 lockedShot = texture(uDblTexture, dblUv).rgb;
                camCol = blendDoubleExp(lockedShot, camCol, uDblBlendMode, uDblStrength);
            }

            fragColor = vec4(clamp(camCol, 0.0, 1.0), 1.0);
        }
    """.trimIndent()

    private fun computeAspectScale(): Pair<Float, Float> {
        if (viewportWidth <= 0 || viewportHeight <= 0) return 1f to 1f
        val viewAspect = viewportWidth.toFloat() / viewportHeight.toFloat()
        val camAspect = cameraAspectRatio.coerceAtLeast(0.1f)
        return if (viewAspect > camAspect) {
            1f to (camAspect / viewAspect)
        } else {
            (viewAspect / camAspect) to 1f
        }
    }

    fun lockDoubleExposureFrame(bitmap: Bitmap) {
        val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val prev = pendingLockBitmap.getAndSet(copy)
        prev?.recycle()
        hasLockedDoubleExposure = true
        onRequestRender?.invoke()
    }

    fun clearDoubleExposureFrame() {
        clearDoubleExposureRequested = true
        hasLockedDoubleExposure = false
        val prev = pendingLockBitmap.getAndSet(null)
        prev?.recycle()
        onRequestRender?.invoke()
    }

    fun blendDoubleExposure(
        primaryFrame: Bitmap,
        secondaryFrame: Bitmap,
        blendMode: DoubleExposureBlendMode,
        strength: Float
    ): Bitmap {
        val w = secondaryFrame.width
        val h = secondaryFrame.height
        val scaledPrimary = if (primaryFrame.width != w || primaryFrame.height != h) {
            Bitmap.createScaledBitmap(primaryFrame, w, h, true)
        } else {
            primaryFrame
        }

        val total = w * h
        val p1 = IntArray(total)
        val p2 = IntArray(total)
        scaledPrimary.getPixels(p1, 0, w, 0, 0, w, h)
        secondaryFrame.getPixels(p2, 0, w, 0, 0, w, h)

        val out = IntArray(total)
        val s = strength.coerceIn(0f, 1f)
        val inv255 = 1f / 255f

        for (i in 0 until total) {
            val c1 = p1[i]
            val c2 = p2[i]

            val r1 = ((c1 ushr 16) and 0xFF) * inv255
            val g1 = ((c1 ushr 8) and 0xFF) * inv255
            val b1 = (c1 and 0xFF) * inv255

            val r2 = ((c2 ushr 16) and 0xFF) * inv255
            val g2 = ((c2 ushr 8) and 0xFF) * inv255
            val b2 = (c2 and 0xFF) * inv255

            val (rOut, gOut, bOut) = when (blendMode) {
                DoubleExposureBlendMode.HIGH_KEY_SILHOUETTE -> {
                    val lum1Raw = 0.299f * r1 + 0.587f * g1 + 0.114f * b1
                    val lum1 = ((lum1Raw + 0.12f).coerceIn(0f, 1f) - 0.5f) * 1.45f + 0.5f
                    val lum1Clamped = lum1.coerceIn(0f, 1f)

                    val lum2Raw = 0.299f * r2 + 0.587f * g2 + 0.114f * b2
                    val mono2 = ((lum2Raw - 0.5f) * 1.28f + 0.5f).coerceIn(0f, 1f)

                    val brightMask = smoothstep(0.42f, 0.86f, lum1Clamped)
                    val insideVal = lum1Clamped + (mono2 - lum1Clamped) * s
                    val backdropVal = smoothstep(0.55f, 0.92f, lum1Clamped) * 0.15f + 0.85f
                    val outVal = (insideVal + (backdropVal - insideVal) * brightMask).coerceIn(0f, 1f)
                    Triple(outVal, outVal, outVal)
                }
                DoubleExposureBlendMode.ADDITIVE -> Triple(
                    (r1 + r2 * s).coerceIn(0f, 1f),
                    (g1 + g2 * s).coerceIn(0f, 1f),
                    (b1 + b2 * s).coerceIn(0f, 1f)
                )
                DoubleExposureBlendMode.SCREEN -> {
                    val sr = 1f - (1f - r1) * (1f - r2)
                    val sg = 1f - (1f - g1) * (1f - g2)
                    val sb = 1f - (1f - b1) * (1f - b2)
                    Triple(
                        (r1 + (sr - r1) * s).coerceIn(0f, 1f),
                        (g1 + (sg - g1) * s).coerceIn(0f, 1f),
                        (b1 + (sb - b1) * s).coerceIn(0f, 1f)
                    )
                }
                DoubleExposureBlendMode.MULTIPLY -> Triple(
                    (r1 + ((r1 * r2) - r1) * s).coerceIn(0f, 1f),
                    (g1 + ((g1 * g2) - g1) * s).coerceIn(0f, 1f),
                    (b1 + ((b1 * b2) - b1) * s).coerceIn(0f, 1f)
                )
            }

            out[i] = 0xFF000000.toInt() or
                    (((rOut * 255f).toInt().coerceIn(0, 255)) shl 16) or
                    (((gOut * 255f).toInt().coerceIn(0, 255)) shl 8) or
                    ((bOut * 255f).toInt().coerceIn(0, 255))
        }

        if (scaledPrimary !== primaryFrame) {
            scaledPrimary.recycle()
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    fun captureCurrentFrame(onCaptured: (Bitmap?) -> Unit) {
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            Log.e(TAG, "Viewport not ready ($viewportWidth x $viewportHeight)")
            onCaptured(null)
            return
        }
        val previous = pendingCaptureCallback.getAndSet(onCaptured)
        previous?.invoke(null)
        onRequestRender?.invoke()
    }

    private fun readViewportBitmap(): Bitmap? {
        val w = viewportWidth
        val h = viewportHeight
        if (w <= 0 || h <= 0) return null

        return try {
            val pixelBuffer = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
            GLES20.glFinish()
            GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuffer)

            val rawInts = IntArray(w * h)
            pixelBuffer.asIntBuffer().get(rawInts)

            val flipped = IntArray(w * h)
            for (y in 0 until h) {
                val srcRow = y * w
                val dstRow = (h - 1 - y) * w
                for (x in 0 until w) {
                    val rgba = rawInts[srcRow + x]
                    val r = rgba and 0xFF
                    val g = (rgba ushr 8) and 0xFF
                    val b = (rgba ushr 16) and 0xFF
                    val a = (rgba ushr 24) and 0xFF
                    flipped[dstRow + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            Bitmap.createBitmap(flipped, w, h, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            Log.e(TAG, "glReadPixels capture failed", e)
            null
        }
    }

    private fun isBlueBand(hueDegrees: Float): Boolean {
        val wrapped = ((hueDegrees % 360f) + 360f) % 360f
        return wrapped in 160f..265f
    }

    private fun effectiveTargetHue(hueDegrees: Float): Float {
        val wrapped = ((hueDegrees % 360f) + 360f) % 360f
        val centered = if (isBlueBand(wrapped) && abs(wrapped - 215f) < 6f) 210f else wrapped
        return centered / 360f
    }

    private fun effectiveTolerance(hueDegrees: Float): Float {
        return if (isBlueBand(hueDegrees)) {
            maxOf(sinCityTolerance, 0.14f)
        } else {
            sinCityTolerance
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: JavaxEGLConfig?) {
        val textures = IntArray(2)
        GLES20.glGenTextures(2, textures, 0)
        oesTextureId = textures[0]
        dblTextureId = textures[1]

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, dblTextureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val st = SurfaceTexture(oesTextureId).apply {
            setOnFrameAvailableListener {
                onRequestRender?.invoke()
            }
        }
        surfaceTexture = st
        onSurfaceTextureReady(st)

        programId = createProgram(vertexShaderCode, fragmentShaderCode)
        Matrix.setIdentityM(texMatrix, 0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val st = surfaceTexture ?: return
        st.updateTexImage()
        st.getTransformMatrix(texMatrix)

        if (clearDoubleExposureRequested) {
            clearDoubleExposureRequested = false
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, dblTextureId)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 1, 1, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        }

        val toUpload = pendingLockBitmap.getAndSet(null)
        if (toUpload != null) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, dblTextureId)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, toUpload, 0)
            toUpload.recycle()
        }

        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (programId == 0) return
        GLES20.glUseProgram(programId)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, dblTextureId)

        val preset = if (bypassToNormal) FilterCatalog.presets.first() else activePreset
        val isSinCity = !bypassToNormal && preset.specialMode == SpecialShaderMode.SIN_CITY
        val isDblActive = !bypassToNormal && preset.specialMode == SpecialShaderMode.DOUBLE_EXPOSURE && hasLockedDoubleExposure
        val dblBlendInt = when (doubleExposureBlendMode) {
            DoubleExposureBlendMode.HIGH_KEY_SILHOUETTE -> 0
            DoubleExposureBlendMode.ADDITIVE -> 1
            DoubleExposureBlendMode.SCREEN -> 2
            DoubleExposureBlendMode.MULTIPLY -> 3
        }

        val mode = sinCityNeutralMode
        val wrappedHue = ((sinCityTargetHue % 360f) + 360f) % 360f
        val normalizedTargetHue = effectiveTargetHue(wrappedHue)
        val activeTol = effectiveTolerance(wrappedHue)

        val bitDepth = if (bypassToNormal) {
            PixelBitDepth.OFF
        } else if (preset.pixelBitDepth != PixelBitDepth.OFF) {
            preset.pixelBitDepth
        } else {
            pixelBitDepth
        }

        val (gridSize, colorLevels) = when (bitDepth) {
            PixelBitDepth.OFF -> 0f to 0f
            PixelBitDepth.BIT_1 -> 64f to 2f
            PixelBitDepth.BIT_4 -> 96f to 4f
            PixelBitDepth.BIT_8 -> 140f to 8f
            PixelBitDepth.BIT_16 -> 220f to 16f
            PixelBitDepth.BIT_128 -> 360f to 64f
        }

        val (scaleX, scaleY) = computeAspectScale()
        val flipHorizontal = if (isFrontCamera && mirrorFrontCamera) 1 else 0

        GLES20.glUniform2f(GLES20.glGetUniformLocation(programId, "uAspectScale"), scaleX, scaleY)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(programId, "uFlipHorizontal"), flipHorizontal)

        GLES20.glUniform1i(GLES20.glGetUniformLocation(programId, "uCamTexture"), 0)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(programId, "uDblTexture"), 1)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(programId, "uDblActive"), if (isDblActive) 1 else 0)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(programId, "uDblBlendMode"), dblBlendInt)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(programId, "uDblStrength"), doubleExposureStrength)

        GLES20.glUniform1i(GLES20.glGetUniformLocation(programId, "uSinCityActive"), if (isSinCity) 1 else 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(programId, "uTargetHue"), normalizedTargetHue)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(programId, "uHueTolerance"), activeTol)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(programId, "uEdgeFeather"), mode.feather)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(programId, "uSatMin"), mode.satMin)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(programId, "uSatMax"), mode.satMax)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(programId, "uLiftBlack"), mode.liftBlack)

        GLES20.glUniform1f(GLES20.glGetUniformLocation(programId, "uPixelGridSize"), gridSize)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(programId, "uColorLevels"), colorLevels)

        GLES20.glUniform1f(GLES20.glGetUniformLocation(programId, "uContrast"), preset.contrast)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(programId, "uSaturation"), preset.saturation)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(programId, "uWarmth"), preset.warmth)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(programId, "uTexMatrix"), 1, false, texMatrix, 0)

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, vertexBuffer)

        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 0, texCoordBuffer)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)

        val callback = pendingCaptureCallback.getAndSet(null)
        if (callback != null) {
            val captured = readViewportBitmap()
            callback(captured)
        }
    }

    fun processFullResolutionBitmap(source: Bitmap): Bitmap {
        val preset = if (bypassToNormal) FilterCatalog.presets.first() else activePreset
        val isSinCity = !bypassToNormal && preset.specialMode == SpecialShaderMode.SIN_CITY

        val bitDepth = if (bypassToNormal) {
            PixelBitDepth.OFF
        } else if (preset.pixelBitDepth != PixelBitDepth.OFF) {
            preset.pixelBitDepth
        } else {
            pixelBitDepth
        }

        if (!isSinCity && preset.code == "NORMAL" && bitDepth == PixelBitDepth.OFF && grainLevel == GrainLevel.OFF) {
            return source
        }

        val width = source.width
        val height = source.height
        val totalPixels = width * height
        val pixels = IntArray(totalPixels)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val mode = sinCityNeutralMode
        val wrappedHue = ((sinCityTargetHue % 360f) + 360f) % 360f
        val targetHue = effectiveTargetHue(wrappedHue)
        val tol = effectiveTolerance(wrappedHue)
        val feather = mode.feather
        val satMin = mode.satMin
        val satMax = mode.satMax
        val liftBlack = mode.liftBlack
        val hsv = if (isSinCity) FloatArray(3) else null

        val levels = when (bitDepth) {
            PixelBitDepth.OFF -> 0f
            PixelBitDepth.BIT_1 -> 2f
            PixelBitDepth.BIT_4 -> 4f
            PixelBitDepth.BIT_8 -> 8f
            PixelBitDepth.BIT_16 -> 16f
            PixelBitDepth.BIT_128 -> 64f
        }
        val hasLevels = levels > 1f
        val levelDenom = if (hasLevels) (levels - 1f).coerceAtLeast(1f) else 1f

        val warmShift = preset.warmth * 0.5f
        val contrast = preset.contrast
        val saturation = preset.saturation
        val inv255 = 1f / 255f

        for (i in 0 until totalPixels) {
            val c = pixels[i]
            val a = c and 0xFF000000.toInt()
            var r = ((c ushr 16) and 0xFF) * inv255
            var g = ((c ushr 8) and 0xFF) * inv255
            var b = (c and 0xFF) * inv255

            if (warmShift != 0f) {
                r = (r + warmShift).coerceIn(0f, 1f)
                b = (b - warmShift).coerceIn(0f, 1f)
            }
            if (contrast != 1f) {
                r = ((r - 0.5f) * contrast + 0.5f).coerceIn(0f, 1f)
                g = ((g - 0.5f) * contrast + 0.5f).coerceIn(0f, 1f)
                b = ((b - 0.5f) * contrast + 0.5f).coerceIn(0f, 1f)
            }

            if (hasLevels) {
                r = (kotlin.math.floor(r * levels + 0.0001f) / levelDenom).coerceIn(0f, 1f)
                g = (kotlin.math.floor(g * levels + 0.0001f) / levelDenom).coerceIn(0f, 1f)
                b = (kotlin.math.floor(b * levels + 0.0001f) / levelDenom).coerceIn(0f, 1f)
            }

            if (isSinCity && hsv != null) {
                android.graphics.Color.RGBToHSV(
                    (r * 255f).toInt().coerceIn(0, 255),
                    (g * 255f).toInt().coerceIn(0, 255),
                    (b * 255f).toInt().coerceIn(0, 255),
                    hsv
                )
                val hNorm = hsv[0] / 360f
                val sNorm = hsv[1]
                val vNorm = hsv[2]
                val chroma = maxOf(r, maxOf(g, b)) - minOf(r, minOf(g, b))

                val rawDiff = abs(hNorm - targetHue)
                val hDist = minOf(rawDiff, 1f - rawDiff)
                val hueMask = 1f - smoothstep((tol - feather).coerceAtLeast(0f), tol + feather, hDist)
                val satMask = smoothstep(satMin, satMax, sNorm)
                val chromaMask = smoothstep(0.08f, 0.22f, chroma)
                val valMask = smoothstep(0.06f, 0.16f, vNorm) * (1f - smoothstep(0.95f, 1.0f, vNorm))
                val mask = (hueMask * satMask * chromaMask * valMask).coerceIn(0f, 1f)

                val luma = 0.299f * r + 0.587f * g + 0.114f * b
                val lifted = luma * (1f - liftBlack) + liftBlack
                val bw = ((lifted - 0.5f) * 1.24f + 0.5f).coerceIn(0f, 1f)
                val satR = (luma + (r - luma) * 1.12f).coerceIn(0f, 1f)
                val satG = (luma + (g - luma) * 1.12f).coerceIn(0f, 1f)
                val satB = (luma + (b - luma) * 1.12f).coerceIn(0f, 1f)

                r = bw + (satR - bw) * mask
                g = bw + (satG - bw) * mask
                b = bw + (satB - bw) * mask
            } else if (saturation != 1f) {
                val lumaBase = 0.299f * r + 0.587f * g + 0.114f * b
                r = (lumaBase + (r - lumaBase) * saturation).coerceIn(0f, 1f)
                g = (lumaBase + (g - lumaBase) * saturation).coerceIn(0f, 1f)
                b = (lumaBase + (b - lumaBase) * saturation).coerceIn(0f, 1f)
            }

            pixels[i] = a or
                    (((r * 255f).toInt().coerceIn(0, 255)) shl 16) or
                    (((g * 255f).toInt().coerceIn(0, 255)) shl 8) or
                    ((b * 255f).toInt().coerceIn(0, 255))
        }

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    fun saveProcessedBitmap(
        context: Context,
        source: Bitmap,
        displayName: String = "IMG_${System.currentTimeMillis()}"
    ): Uri? {
        val processed = processFullResolutionBitmap(source)
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, DEFAULT_CAMERA_RELATIVE_PATH)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            Log.e(TAG, "Failed to create MediaStore entry for $displayName")
            if (processed !== source) processed.recycle()
            return null
        }

        return try {
            val stream: OutputStream? = resolver.openOutputStream(uri)
            if (stream == null) {
                Log.e(TAG, "Failed to open output stream for $uri")
                resolver.delete(uri, null, null)
                null
            } else {
                stream.use { out ->
                    val ok = processed.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    if (!ok) throw IllegalStateException("Bitmap compress returned false")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val doneValues = ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }
                    resolver.update(uri, doneValues, null, null)
                }
                uri
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving image to default camera folder", e)
            resolver.delete(uri, null, null)
            null
        } finally {
            if (processed !== source) processed.recycle()
        }
    }

    private fun createProgram(vs: String, fs: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vs)
        if (vertexShader == 0) return 0
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fs)
        if (fragmentShader == 0) return 0

        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.e(TAG, "Program link error: ${GLES20.glGetProgramInfoLog(program)}")
            GLES20.glDeleteProgram(program)
            return 0
        }
        return program
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            Log.e(TAG, "Shader compile error ($type): ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        if (edge1 <= edge0) return if (x >= edge1) 1f else 0f
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}