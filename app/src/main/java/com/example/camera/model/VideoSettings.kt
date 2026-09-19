package com.example.camera.model

enum class VideoResolution(val displayName: String, val width: Int, val height: Int) {
    RES_1080P("1080p", 1920, 1080),
    RES_4K("4K", 3840, 2160)
}

enum class VideoCodec(val displayName: String, val mimeType: String) {
    H264("H.264", "video/avc"),
    H265("H.265", "video/hevc")
}

data class VideoSettings(
    val isTorchOn: Boolean = false,
    val resolution: VideoResolution = VideoResolution.RES_1080P,
    val fps: Int = 30,
    val isStabilizationEnabled: Boolean = true,
    val codec: VideoCodec = VideoCodec.H264,
    val aspectRatio: AspectRatio = AspectRatio.RATIO_16_9,
    val bitrateMbps: Int = 30
)
