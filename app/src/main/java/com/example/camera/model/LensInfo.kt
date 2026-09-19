package com.example.camera.model

data class LensInfo(
    val id: String,
    val facing: Int, // CameraCharacteristics.LENS_FACING_BACK, FRONT, EXTERNAL
    val focalLength: Float,
    val equivalent35mm: Float,
    val aperture: Float,
    val baseZoomFactor: Float,
    val displayName: String,
    val isMacroCapable: Boolean = false,
    val minFocusDistance: Float = 0f,
    val maxDigitalZoom: Float = 1.0f,
    val isPhysicalOfLogical: Boolean = false,
    val logicalCameraId: String? = null
)
