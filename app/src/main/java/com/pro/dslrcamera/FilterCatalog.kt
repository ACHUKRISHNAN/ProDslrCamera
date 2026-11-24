package com.pro.dslrcamera

enum class GrainLevel(val label: String, val intensity: Float) {
    OFF("OFF", 0f),
    LOW("LOW", 0.04f),
    MED("MED", 0.09f),
    HIGH("HIGH", 0.16f)
}

enum class PixelBitDepth(val label: String) {
    OFF("OFF"),
    BIT_1("1-BIT"),
    BIT_4("4-BIT"),
    BIT_8("8-BIT"),
    BIT_16("16-BIT"),
    BIT_128("128-BIT")
}

enum class FilmEffectType(val label: String) {
    OFF("OFF"),
    HALATION("HALATION"),
    LIGHT_LEAK("LIGHT LEAK")
}

enum class RippedEffectType(val label: String) {
    OFF("OFF"),
    EDGE_TEAR("EDGE TEAR"),
    COLLAGE("COLLAGE")
}

data class FilmPreset(
    val code: String,
    val name: String,
    val brand: String,
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,
    val warmth: Float = 0.0f,
    val specialMode: SpecialShaderMode = SpecialShaderMode.NONE,
    val pixelBitDepth: PixelBitDepth = PixelBitDepth.OFF,
    val snapFocusMeters: Float? = null
)

object FilterCatalog {
    val categories = listOf("ALL", "B&W", "DSLR", "LEICA", "FUJI", "PAPERSHOOT", "SPECIAL")

    val presets: List<FilmPreset> = listOf(
        // Standard DSLR
        FilmPreset(
            code = "NORMAL",
            name = "Standard DSLR",
            brand = "DSLR",
            contrast = 1.0f,
            saturation = 1.0f,
            warmth = 0.0f
        ),

        // Black & White Looks
        FilmPreset(
            code = "BW-NOIR",
            name = "High-Contrast Noir",
            brand = "B&W",
            contrast = 1.38f,
            saturation = 0.0f,
            warmth = -0.02f
        ),
        FilmPreset(
            code = "BW-TRI-X",
            name = "Classic Silver Tri-X",
            brand = "B&W",
            contrast = 1.24f,
            saturation = 0.0f,
            warmth = 0.0f
        ),
        FilmPreset(
            code = "BW-SOFT",
            name = "Soft Portrait Mono",
            brand = "B&W",
            contrast = 0.90f,
            saturation = 0.0f,
            warmth = 0.01f
        ),
        FilmPreset(
            code = "BW-WARM",
            name = "Warm Selenium Mono",
            brand = "B&W",
            contrast = 1.16f,
            saturation = 0.0f,
            warmth = 0.08f
        ),
        FilmPreset(
            code = "BW-COOL",
            name = "Cold Cyanotype Mono",
            brand = "B&W",
            contrast = 1.18f,
            saturation = 0.0f,
            warmth = -0.09f
        ),
        FilmPreset(
            code = "BW-RED",
            name = "Dramatic Sky Red Filter",
            brand = "B&W",
            contrast = 1.32f,
            saturation = 0.0f,
            warmth = -0.01f
        ),

        // Special Modes
        FilmPreset(
            code = "SIN-CITY",
            name = "Sin City Color Pop",
            brand = "SPECIAL",
            contrast = 1.22f,
            saturation = 1.15f,
            warmth = 0.0f,
            specialMode = SpecialShaderMode.SIN_CITY
        ),
        FilmPreset(
            code = "DBL-EXP",
            name = "Double Exposure",
            brand = "SPECIAL",
            contrast = 1.06f,
            saturation = 1.04f,
            warmth = 0.0f,
            specialMode = SpecialShaderMode.DOUBLE_EXPOSURE
        ),

        // Leica Looks
        FilmPreset(
            code = "BLE",
            name = "Leica Bleach",
            brand = "LEICA",
            contrast = 1.26f,
            saturation = 0.56f,
            warmth = -0.03f
        ),
        FilmPreset(
            code = "TEL",
            name = "Leica Teal",
            brand = "LEICA",
            contrast = 1.10f,
            saturation = 0.94f,
            warmth = -0.07f
        ),
        FilmPreset(
            code = "BRS",
            name = "Leica Brass",
            brand = "LEICA",
            contrast = 1.14f,
            saturation = 0.92f,
            warmth = 0.08f
        ),
        FilmPreset(
            code = "CHR",
            name = "Leica Chrome",
            brand = "LEICA",
            contrast = 1.16f,
            saturation = 0.86f,
            warmth = 0.02f
        ),
        FilmPreset(
            code = "CLS",
            name = "Leica Classic",
            brand = "LEICA",
            contrast = 1.08f,
            saturation = 0.96f,
            warmth = 0.03f
        ),
        FilmPreset(
            code = "CNT",
            name = "Leica Contemporary",
            brand = "LEICA",
            contrast = 1.12f,
            saturation = 1.06f,
            warmth = 0.01f
        ),
        FilmPreset(
            code = "SEP",
            name = "Leica Sepia",
            brand = "LEICA",
            contrast = 1.12f,
            saturation = 0.22f,
            warmth = 0.10f
        ),
        FilmPreset(
            code = "BLU",
            name = "Leica Blue",
            brand = "LEICA",
            contrast = 1.10f,
            saturation = 0.96f,
            warmth = -0.09f
        ),
        FilmPreset(
            code = "SEL",
            name = "Leica Selenium",
            brand = "LEICA",
            contrast = 1.18f,
            saturation = 0.08f,
            warmth = -0.02f
        ),
        FilmPreset(
            code = "ETN",
            name = "Leica Eternal",
            brand = "LEICA",
            contrast = 0.96f,
            saturation = 0.90f,
            warmth = 0.03f
        ),
        FilmPreset(
            code = "SLV",
            name = "Leica Silver",
            brand = "LEICA",
            contrast = 1.24f,
            saturation = 0.0f,
            warmth = 0.0f
        ),
        FilmPreset(
            code = "CINE",
            name = "Leica Cine",
            brand = "LEICA",
            contrast = 1.14f,
            saturation = 0.92f,
            warmth = -0.02f
        ),
        FilmPreset(
            code = "PURE",
            name = "Leica Pure",
            brand = "LEICA",
            contrast = 1.02f,
            saturation = 1.0f,
            warmth = 0.0f
        ),
        FilmPreset(
            code = "WLM",
            name = "Greg Williams",
            brand = "LEICA",
            contrast = 1.28f,
            saturation = 0.0f,
            warmth = 0.0f
        ),
        FilmPreset(
            code = "LEICA-M",
            name = "Leica Summilux",
            brand = "LEICA",
            contrast = 1.12f,
            saturation = 1.05f,
            warmth = 0.03f
        ),

        // Fujifilm Film Simulations
        FilmPreset(
            code = "FA1",
            name = "Fuji Astia 100F",
            brand = "FUJI",
            contrast = 0.96f,
            saturation = 1.04f,
            warmth = 0.02f
        ),
        FilmPreset(
            code = "FF5",
            name = "Fuji Fortia SP",
            brand = "FUJI",
            contrast = 1.18f,
            saturation = 1.38f,
            warmth = 0.04f
        ),
        FilmPreset(
            code = "FN16",
            name = "Fuji Neopan 1600",
            brand = "FUJI",
            contrast = 1.26f,
            saturation = 0.0f,
            warmth = 0.0f
        ),
        FilmPreset(
            code = "FV5",
            name = "Fuji Velvia 50",
            brand = "FUJI",
            contrast = 1.20f,
            saturation = 1.32f,
            warmth = 0.03f
        ),
        FilmPreset(
            code = "FT6",
            name = "Fuji T64",
            brand = "FUJI",
            contrast = 1.06f,
            saturation = 1.02f,
            warmth = -0.10f
        ),
        FilmPreset(
            code = "FP8",
            name = "Fuji Pro 800Z",
            brand = "FUJI",
            contrast = 0.95f,
            saturation = 1.06f,
            warmth = 0.02f
        ),
        FilmPreset(
            code = "FP4",
            name = "Fuji Pro 400H",
            brand = "FUJI",
            contrast = 0.92f,
            saturation = 0.94f,
            warmth = -0.02f
        ),
        FilmPreset(
            code = "FP2",
            name = "Fuji Pro 160S",
            brand = "FUJI",
            contrast = 0.90f,
            saturation = 0.92f,
            warmth = 0.01f
        ),
        FilmPreset(
            code = "FP1",
            name = "Fuji Pro 160C",
            brand = "FUJI",
            contrast = 1.05f,
            saturation = 1.06f,
            warmth = 0.01f
        ),
        FilmPreset(
            code = "FR1",
            name = "Fuji Provia 100F",
            brand = "FUJI",
            contrast = 1.06f,
            saturation = 1.08f,
            warmth = 0.0f
        ),
        FilmPreset(
            code = "FR4",
            name = "Fuji Provia 400F",
            brand = "FUJI",
            contrast = 1.08f,
            saturation = 1.06f,
            warmth = 0.01f
        ),
        FilmPreset(
            code = "FR4X",
            name = "Fuji Provia 400X",
            brand = "FUJI",
            contrast = 1.12f,
            saturation = 1.14f,
            warmth = 0.02f
        ),
        FilmPreset(
            code = "FS16",
            name = "Fuji Superia 1600",
            brand = "FUJI",
            contrast = 1.10f,
            saturation = 1.04f,
            warmth = 0.03f
        ),
        FilmPreset(
            code = "FS4",
            name = "Fuji Superia X-TRA 400",
            brand = "FUJI",
            contrast = 1.06f,
            saturation = 1.10f,
            warmth = -0.01f
        ),
        FilmPreset(
            code = "FS1",
            name = "Fuji Superia 100",
            brand = "FUJI",
            contrast = 1.04f,
            saturation = 1.08f,
            warmth = 0.01f
        ),
        FilmPreset(
            code = "FUJI-CC",
            name = "Fujifilm Classic Chrome",
            brand = "FUJI",
            contrast = 1.08f,
            saturation = 0.88f,
            warmth = -0.02f
        ),

        // PaperShoot Camera Looks
        FilmPreset(
            code = "PS-COLOR",
            name = "PaperShoot Color",
            brand = "PAPERSHOOT",
            contrast = 0.96f,
            saturation = 0.88f,
            warmth = 0.04f
        ),
        FilmPreset(
            code = "PS-MONO",
            name = "PaperShoot B&W",
            brand = "PAPERSHOOT",
            contrast = 1.20f,
            saturation = 0.0f,
            warmth = 0.0f
        ),
        FilmPreset(
            code = "PS-SEPIA",
            name = "PaperShoot Sepia",
            brand = "PAPERSHOOT",
            contrast = 0.98f,
            saturation = 0.28f,
            warmth = 0.12f
        ),
        FilmPreset(
            code = "PS-BLUE",
            name = "PaperShoot Cool Blue",
            brand = "PAPERSHOOT",
            contrast = 1.04f,
            saturation = 1.06f,
            warmth = -0.11f
        ),
        FilmPreset(
            code = "PS-SOFT",
            name = "PaperShoot Softday",
            brand = "PAPERSHOOT",
            contrast = 0.88f,
            saturation = 0.84f,
            warmth = 0.03f
        ),
        FilmPreset(
            code = "PS-BURG",
            name = "PaperShoot Burgundy",
            brand = "PAPERSHOOT",
            contrast = 1.08f,
            saturation = 0.95f,
            warmth = 0.07f
        ),
        FilmPreset(
            code = "PS-TEAL-ORG",
            name = "PaperShoot Teal & Orange",
            brand = "PAPERSHOOT",
            contrast = 1.14f,
            saturation = 1.16f,
            warmth = -0.04f
        )
    )
}