package com.example.camera.model

enum class CinemaBitrate(val displayName: String, val mbps: Int) {
    STANDARD("Standard (35 Mbps)", 35),
    HIGH("High (60 Mbps)", 60),
    MAXIMUM("Maximum (100 Mbps)", 100)
}

data class CinemaSettings(
    val resolution: VideoResolution = VideoResolution.RES_1080P,
    val fps: Int = 24, // Cinema 24 FPS standard
    val profile: CinemaProfile = CinemaProfile.CINEMATIC,
    val codec: VideoCodec = VideoCodec.H265,
    val bitrate: CinemaBitrate = CinemaBitrate.HIGH,
    val aspectRatio: AspectRatio = AspectRatio.RATIO_16_9,
    val lut: CinemaLut = CinemaLut.OPPENHEIMER,
    val lutIntensity: Int = 85, // 0..100%
    val isStabilizationEnabled: Boolean = true,
    
    // Manual exposure controls
    val isManualIso: Boolean = false,
    val isoValue: Int = 200,
    val isManualShutter: Boolean = false,
    val shutterNanos: Long = 1_000_000_000L / 48L, // 180-degree shutter at 24fps = 1/48s
    val isManualWb: Boolean = false,
    val wbKelvin: Int = 5600, // Daylight
    val isManualFocus: Boolean = false,
    val focusDistance: Float = 0f, // 0 = infinity, higher = closer
    val manualEv: Int = 0, // Exposure compensation index
    
    // Cinema Assist Tools
    val showHistogram: Boolean = true,
    val showZebra: Boolean = false,
    val zebraThreshold: Float = 0.90f, // 90% IRE overexposure highlight stripes
    val showFocusPeaking: Boolean = false,
    val showAudioMeter: Boolean = true
)
