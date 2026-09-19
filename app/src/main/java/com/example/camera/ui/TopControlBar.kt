package com.example.camera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.HdrOn
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.model.AspectRatio
import com.example.camera.model.CameraMode
import com.example.camera.model.FlashMode
import com.example.camera.model.VideoCodec
import com.example.camera.model.VideoResolution

@Composable
fun TopControlBar(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val mode by viewModel.currentMode.collectAsState()
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color(0xFF0C0E12))
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (mode) {
            CameraMode.PHOTO -> PhotoTopControls(viewModel)
            CameraMode.VIDEO -> VideoTopControls(viewModel)
            CameraMode.CINEMA -> CinemaTopControls(viewModel)
        }

        // Settings Icon button (always present)
        IconButton(
            onClick = { viewModel.openSettingsDialog() },
            modifier = Modifier.testTag("top_settings_button")
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = Color(0xCCFFFFFF),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun PhotoTopControls(viewModel: CameraViewModel) {
    val settings by viewModel.photoSettings.collectAsState()
    val caps by viewModel.capabilities.collectAsState()

    // 1. Flash
    IconButton(
        onClick = {
            val next = when (settings.flashMode) {
                FlashMode.AUTO -> FlashMode.ON
                FlashMode.ON -> FlashMode.OFF
                FlashMode.OFF -> FlashMode.AUTO
            }
            viewModel.updatePhotoFlash(next)
        },
        enabled = caps.isFlashSupported,
        modifier = Modifier.testTag("photo_flash_button")
    ) {
        val (icon, tint) = when (settings.flashMode) {
            FlashMode.AUTO -> Pair(Icons.Default.FlashAuto, Color(0xFFFFB300))
            FlashMode.ON -> Pair(Icons.Default.FlashOn, Color(0xFFFFB300))
            FlashMode.OFF -> Pair(Icons.Default.FlashOff, Color(0x66FFFFFF))
        }
        Icon(
            imageVector = icon,
            contentDescription = "Flash ${settings.flashMode.displayName}",
            tint = if (caps.isFlashSupported) tint else Color(0x33FFFFFF),
            modifier = Modifier.size(22.dp)
        )
    }

    // 2. HDR (real hardware supported only)
    TopPillButton(
        label = "HDR",
        isActive = settings.isHdrEnabled,
        isEnabled = caps.isHdrSceneSupported,
        testTag = "photo_hdr_button",
        onClick = { viewModel.togglePhotoHdr() }
    )

    // 3. RAW (real DNG supported only)
    TopPillButton(
        label = "RAW",
        isActive = settings.isRawEnabled,
        isEnabled = caps.isRawSupported,
        testTag = "photo_raw_button",
        onClick = { viewModel.togglePhotoRaw() }
    )

    // 4. Ratio (3:4 default, 9:16, 1:1, Full)
    TopPillButton(
        label = settings.aspectRatio.displayName,
        isActive = true,
        testTag = "photo_ratio_button",
        onClick = {
            val ratios = AspectRatio.forMode(CameraMode.PHOTO)
            val idx = ratios.indexOf(settings.aspectRatio)
            val next = ratios[(idx + 1) % ratios.size]
            viewModel.setPhotoAspectRatio(next)
        }
    )

    // 5. Timer (Off, 3s, 10s)
    TopPillButton(
        label = if (settings.timerSeconds == 0) "Timer Off" else "${settings.timerSeconds}s",
        isActive = settings.timerSeconds > 0,
        testTag = "photo_timer_button",
        onClick = { viewModel.cyclePhotoTimer() }
    )
}

@Composable
private fun VideoTopControls(viewModel: CameraViewModel) {
    val settings by viewModel.videoSettings.collectAsState()
    val caps by viewModel.capabilities.collectAsState()

    // 1. Torch
    IconButton(
        onClick = { viewModel.toggleVideoTorch() },
        enabled = caps.isFlashSupported,
        modifier = Modifier.testTag("video_torch_button")
    ) {
        Icon(
            imageVector = if (settings.isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
            contentDescription = "Torch",
            tint = if (settings.isTorchOn) Color(0xFFFFB300) else Color(0x88FFFFFF),
            modifier = Modifier.size(22.dp)
        )
    }

    // 2. Resolution (1080p / 4K)
    TopPillButton(
        label = settings.resolution.displayName,
        isActive = true,
        isEnabled = true,
        testTag = "video_res_button",
        onClick = {
            if (caps.is4kSupported) {
                val next = if (settings.resolution == VideoResolution.RES_1080P) VideoResolution.RES_4K else VideoResolution.RES_1080P
                viewModel.setVideoResolution(next)
            }
        }
    )

    // 3. FPS (24, 30, 60 if supported)
    TopPillButton(
        label = "${settings.fps} FPS",
        isActive = true,
        isEnabled = caps.supportedVideoFps.size > 1,
        testTag = "video_fps_button",
        onClick = {
            val list = caps.supportedVideoFps
            if (list.isNotEmpty()) {
                val idx = list.indexOf(settings.fps)
                val next = list[(idx + 1) % list.size]
                viewModel.setVideoFps(next)
            }
        }
    )

    // 4. Real Stabilization
    TopPillButton(
        label = "STAB",
        isActive = settings.isStabilizationEnabled,
        isEnabled = caps.isVideoStabilizationSupported || caps.isOpticalStabilizationSupported,
        testTag = "video_stab_button",
        onClick = { viewModel.toggleVideoStabilization() }
    )

    // 5. Codec (H.264 / H.265)
    TopPillButton(
        label = settings.codec.displayName,
        isActive = true,
        isEnabled = caps.isH265Supported,
        testTag = "video_codec_button",
        onClick = {
            if (caps.isH265Supported) {
                val next = if (settings.codec == VideoCodec.H264) VideoCodec.H265 else VideoCodec.H264
                viewModel.setVideoCodec(next)
            }
        }
    )

    // 6. Aspect Ratio (9:16 / 3:4)
    TopPillButton(
        label = settings.aspectRatio.displayName,
        isActive = true,
        testTag = "video_ratio_button",
        onClick = {
            val ratios = AspectRatio.forMode(CameraMode.VIDEO)
            val idx = ratios.indexOf(settings.aspectRatio)
            val next = ratios[(idx + 1) % ratios.size]
            viewModel.setVideoAspectRatio(next)
        }
    )
}

@Composable
private fun CinemaTopControls(viewModel: CameraViewModel) {
    val settings by viewModel.cinemaSettings.collectAsState()
    val caps by viewModel.capabilities.collectAsState()

    // 1. FPS (24, 25, 30)
    TopPillButton(
        label = "${settings.fps}p",
        isActive = true,
        testTag = "cinema_fps_button",
        accentColor = Color(0xFFFFB300),
        onClick = {
            val cinemaFpsList = caps.supportedVideoFps.filter { it in listOf(24, 25, 30) }
            val choices = if (cinemaFpsList.isNotEmpty()) cinemaFpsList else listOf(24, 30)
            val idx = choices.indexOf(settings.fps)
            val next = choices[(idx + 1) % choices.size]
            viewModel.setCinemaFps(next)
        }
    )

    // 2. Resolution (1080p / 4K)
    TopPillButton(
        label = settings.resolution.displayName,
        isActive = true,
        testTag = "cinema_res_button",
        accentColor = Color(0xFFFFB300),
        onClick = {
            if (caps.is4kSupported) {
                val next = if (settings.resolution == VideoResolution.RES_1080P) VideoResolution.RES_4K else VideoResolution.RES_1080P
                viewModel.setCinemaResolution(next)
            }
        }
    )

    // 3. Profile (Standard | Cinematic | Flat | LOG-Style)
    TopPillButton(
        label = settings.profile.labelWithDisclaimers,
        isActive = true,
        testTag = "cinema_profile_button",
        accentColor = Color(0xFFFFB300),
        onClick = {
            val profiles = com.example.camera.model.CinemaProfile.values()
            val idx = profiles.indexOf(settings.profile)
            val next = profiles[(idx + 1) % profiles.size]
            viewModel.setCinemaProfile(next)
        }
    )

    // 4. Codec
    TopPillButton(
        label = settings.codec.displayName,
        isActive = true,
        testTag = "cinema_codec_button",
        accentColor = Color(0xFFFFB300),
        onClick = {
            if (caps.isH265Supported) {
                val next = if (settings.codec == VideoCodec.H264) VideoCodec.H265 else VideoCodec.H264
                viewModel.setCinemaCodec(next)
            }
        }
    )

    // 5. LUT Selector button
    TopPillButton(
        label = "LUT: ${settings.lut.title}",
        isActive = true,
        testTag = "cinema_lut_button",
        accentColor = Color(0xFFFFB300),
        onClick = { viewModel.openLutSelector() }
    )

    // 6. Ratio (9:16 / 2.39:1)
    TopPillButton(
        label = settings.aspectRatio.displayName,
        isActive = true,
        testTag = "cinema_ratio_button",
        accentColor = Color(0xFFFFB300),
        onClick = {
            val ratios = AspectRatio.forMode(CameraMode.CINEMA)
            val idx = ratios.indexOf(settings.aspectRatio)
            val next = ratios[(idx + 1) % ratios.size]
            viewModel.setCinemaAspectRatio(next)
        }
    )

    // 7. Real Stabilization
    TopPillButton(
        label = "OIS/EIS",
        isActive = settings.isStabilizationEnabled,
        isEnabled = caps.isVideoStabilizationSupported || caps.isOpticalStabilizationSupported,
        testTag = "cinema_stab_button",
        accentColor = Color(0xFFFFB300),
        onClick = { viewModel.toggleCinemaStabilization() }
    )
}

@Composable
fun TopPillButton(
    label: String,
    isActive: Boolean,
    isEnabled: Boolean = true,
    accentColor: Color = Color(0xFFFFB300),
    testTag: String = "",
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    !isEnabled -> Color(0x11FFFFFF)
                    isActive -> Color(0x2AFFFFFF)
                    else -> Color(0x14FFFFFF)
                }
            )
            .clickable(enabled = isEnabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                !isEnabled -> Color(0x33FFFFFF)
                isActive -> accentColor
                else -> Color(0xAAFFFFFF)
            }
        )
    }
}
