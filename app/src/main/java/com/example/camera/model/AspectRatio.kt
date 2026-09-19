package com.example.camera.model

enum class AspectRatio(
    val id: String,
    val displayName: String,
    val ratioValue: Float // width / height in portrait orientation
) {
    RATIO_3_4("3:4", "3:4", 3f / 4f),
    RATIO_4_3("4:3", "3:4", 3f / 4f),
    RATIO_9_16("9:16", "9:16", 9f / 16f),
    RATIO_16_9("16:9", "9:16", 9f / 16f),
    RATIO_1_1("1:1", "1:1", 1f),
    RATIO_FULL("FULL", "Full", 0f),
    RATIO_2_39_1("2.39:1", "2.39:1", 1f / 2.39f);

    companion object {
        fun forMode(mode: CameraMode): List<AspectRatio> = when (mode) {
            CameraMode.PHOTO -> listOf(RATIO_3_4, RATIO_9_16, RATIO_1_1, RATIO_FULL)
            CameraMode.VIDEO -> listOf(RATIO_9_16, RATIO_3_4)
            CameraMode.CINEMA -> listOf(RATIO_9_16, RATIO_2_39_1)
        }

        fun defaultForMode(mode: CameraMode): AspectRatio = when (mode) {
            CameraMode.PHOTO -> RATIO_3_4
            CameraMode.VIDEO -> RATIO_9_16
            CameraMode.CINEMA -> RATIO_9_16
        }
    }
}
