package com.example.camera.model

enum class CinemaProfile(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val gammaCurve: Float, // Power/curve exponent
    val blackLevelLift: Float, // Shadow lift (toe)
    val highlightCompression: Float, // Shoulder roll-off factor
    val isTrueSensorLog: Boolean = false
) {
    STANDARD(
        id = "standard",
        displayName = "Standard",
        subtitle = "Rec.709 Natural Broadcast",
        gammaCurve = 1.0f,
        blackLevelLift = 0.0f,
        highlightCompression = 0.0f
    ),
    CINEMATIC(
        id = "cinematic",
        displayName = "Cinematic",
        subtitle = "Filmic S-Curve & Soft Highlights",
        gammaCurve = 1.15f,
        blackLevelLift = 0.03f,
        highlightCompression = 0.25f
    ),
    FLAT(
        id = "flat",
        displayName = "Flat",
        subtitle = "Low Contrast Wide Latitude",
        gammaCurve = 0.82f,
        blackLevelLift = 0.08f,
        highlightCompression = 0.40f
    ),
    LOG_STYLE(
        id = "log_style",
        displayName = "LOG-Style",
        subtitle = "Logarithmic Curve (Software Tone Mapping)",
        gammaCurve = 0.65f,
        blackLevelLift = 0.14f,
        highlightCompression = 0.55f,
        isTrueSensorLog = false
    );

    val labelWithDisclaimers: String
        get() = if (this == LOG_STYLE && !isTrueSensorLog) {
            "LOG-Style (Curve)"
        } else displayName
}
