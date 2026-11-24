package com.pro.dslrcamera.model

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.geometry.Offset

enum class ExposureMode(val label: String) {
    M("M"), A("A"), S("S"), P("P"), AUTO("AUTO"), BULB("B")
}

enum class ControlParameter(val title: String) {
    ISO("ISO:"),
    APERTURE("Aperture:"),
    SHUTTER("Shutter Speed:"),
    WB("WB:"),
    FOCUS("Focus:")
}

enum class FilmPreset(val displayName: String, val id: Int) {
    PROVIA("Provia (Standard)", 0),
    VELVIA("Velvia (Vivid)", 1),
    ASTIA("Astia (Soft)", 2),
    CLASSIC_CHROME("Classic Chrome", 3),
    CLASSIC_NEG("Classic Neg", 4),
    ACROS("Acros (B&W)", 5),
    ETERNA("Eterna (Cinema)", 6)
}

enum class GridOverlayMode(val label: String) {
    NONE("Off"),
    RULE_OF_THIRDS("3x3 Grid"),
    GOLDEN_SPIRAL("Golden Spiral")
}



data class ShutterOption(val label: String, val nanos: Long, val isBulb: Boolean = false)

object CameraStepTables {
    val isoValues = listOf(100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250, 1600, 2000, 2500, 3200, 4000, 5000, 6400)
    val apertureValues = listOf("f/1.4", "f/1.8", "f/2.0", "f/2.8", "f/4.0", "f/5.6", "f/8.0", "f/11", "f/16")

    val shutterValues = listOf(
        ShutterOption("1/4000", 250_000L),
        ShutterOption("1/2000", 500_000L),
        ShutterOption("1/1000", 1_000_000L),
        ShutterOption("1/500", 2_000_000L),
        ShutterOption("1/250", 4_000_000L),
        ShutterOption("1/125", 8_000_000L),
        ShutterOption("1/60", 16_666_666L),
        ShutterOption("1/30", 33_333_333L),
        ShutterOption("1/15", 66_666_666L),
        ShutterOption("1/8", 125_000_000L),
        ShutterOption("1/4", 250_000_000L),
        ShutterOption("1/2", 500_000_000L),
        ShutterOption("1s", 1_000_000_000L),
        ShutterOption("2s", 2_000_000_000L),
        ShutterOption("4s", 4_000_000_000L),
        ShutterOption("8s", 8_000_000_000L),
        ShutterOption("15s", 15_000_000_000L),
        ShutterOption("30s", 30_000_000_000L),
        ShutterOption("BULB", 30_000_000_000L, isBulb = true)
    )

    val wbKelvinValues = (2500..8000 step 250).toList()

    val focusLabels = listOf("AF-S") + (1..20).map { step ->
        if (step == 20) "∞" else String.format("%.2fm", 0.10f * step)
    }
}

data class CameraUiState(
    val exposureMode: ExposureMode = ExposureMode.M,
    val activeParameter: ControlParameter = ControlParameter.SHUTTER,
    val isoIndex: Int = 6,
    val apertureIndex: Int = 3,
    val shutterIndex: Int = 4,
    val wbIndex: Int = 12,
    val focusIndex: Int = 0,
    val isFlashOn: Boolean = false,
    val isRawEnabled: Boolean = true,
    val isVideoMode: Boolean = false,
    val isRecordingVideo: Boolean = false,
    val isFrontCamera: Boolean = false,
    val zoomRatio: Float = 1.0f,
    val isBulbActive: Boolean = false,
    val bulbElapsedMs: Long = 0L,
    val focusReticleNorm: Offset = Offset(0.5f, 0.5f),
    val isFocusLocked: Boolean = false,
    val showQuickMenu: Boolean = false,
    val gridMode: GridOverlayMode = GridOverlayMode.NONE,
    val mirrorSpiral: Boolean = false,
    val activeFilmPreset: FilmPreset = FilmPreset.PROVIA,
    val lastCapturedUri: Uri? = null,
    val lastCapturedThumb: Bitmap? = null,
    val freeStorageGb: String = "5.9GB",
    val batteryPercent: Int = 88
)
