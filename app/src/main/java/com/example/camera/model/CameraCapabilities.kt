package com.example.camera.model

data class CameraCapabilities(
    val isRawSupported: Boolean = false,
    val isHdrSceneSupported: Boolean = false,
    val isFlashSupported: Boolean = false,
    val is4kSupported: Boolean = false,
    val is1080pSupported: Boolean = true,
    val supportedVideoFps: List<Int> = listOf(30),
    val isVideoStabilizationSupported: Boolean = false,
    val isOpticalStabilizationSupported: Boolean = false,
    val isH265Supported: Boolean = false,
    val isH264Supported: Boolean = true,
    val maxDigitalZoom: Float = 1.0f,
    val minZoomRatio: Float = 1.0f,
    val maxZoomRatio: Float = 1.0f,
    val isoRange: ClosedRange<Int> = 100..1600,
    val exposureTimeRange: ClosedRange<Long> = (1_000_000L / 1000L)..(1_000_000_000L), // 1/1000s to 1s
    val exposureCompensationRange: ClosedRange<Int> = -4..4,
    val exposureCompensationStep: Float = 0.5f,
    val minFocusDistance: Float = 0f, // 0 means fixed focus, > 0 means autofocus supported
    val availableLenses: List<LensInfo> = emptyList(),
    val isTrueSensorLogSupported: Boolean = false
)
