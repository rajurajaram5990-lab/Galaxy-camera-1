package com.example.camera.model

enum class FlashMode(val displayName: String) {
    AUTO("Auto"),
    ON("On"),
    OFF("Off")
}

data class PhotoSettings(
    val flashMode: FlashMode = FlashMode.AUTO,
    val isHdrEnabled: Boolean = false,
    val isRawEnabled: Boolean = false,
    val aspectRatio: AspectRatio = AspectRatio.RATIO_3_4,
    val timerSeconds: Int = 0, // 0 = off, 3, 10
    val highQualityNoiseReduction: Boolean = true
)
