package com.example.camera.model

enum class AspectRatio(
    val id: String,
    val displayName: String,
    val ratioValue: Float // width / height in portrait orientation (or height / width in landscape)
) {
    RATIO_4_3("4:3", "4:3", 3f / 4f),
    RATIO_16_9("16:9", "16:9", 9f / 16f),
    RATIO_1_1("1:1", "1:1", 1f),
    RATIO_FULL("FULL", "Full", 0f),
    RATIO_9_16("9:16", "9:16", 9f / 16f),
    RATIO_2_39_1("2.39:1", "2.39:1", 1f / 2.39f);

    companion object {
        fun forMode(mode: CameraMode): List<AspectRatio> = when (mode) {
            CameraMode.PHOTO -> listOf(RATIO_4_3, RATIO_16_9, RATIO_1_1, RATIO_FULL)
            CameraMode.VIDEO -> listOf(RATIO_16_9, RATIO_9_16)
            CameraMode.CINEMA -> listOf(RATIO_16_9, RATIO_2_39_1)
        }

        fun defaultForMode(mode: CameraMode): AspectRatio = when (mode) {
            CameraMode.PHOTO -> RATIO_4_3
            CameraMode.VIDEO -> RATIO_16_9
            CameraMode.CINEMA -> RATIO_16_9
        }
    }
}
