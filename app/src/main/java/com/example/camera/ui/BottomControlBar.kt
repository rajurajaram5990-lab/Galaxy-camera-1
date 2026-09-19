package com.example.camera.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import android.hardware.camera2.CameraCharacteristics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.model.CameraMode

@Composable
fun BottomControlBar(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val mode = viewModel.currentMode.value
    val isRecording = viewModel.isRecording.value
    val recordingSeconds = viewModel.recordingTimerSeconds.value
    val lastThumbnail = viewModel.lastThumbnail.value
    val activeLens = viewModel.activeLens.value
    val availableLenses = viewModel.capabilities.value.availableLenses

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF07080A))
            .padding(bottom = 16.dp, top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Strictly 3 Modes Selector: PHOTO | VIDEO | CINEMA
        Row(
            modifier = Modifier
                .padding(bottom = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0x33000000))
                .padding(horizontal = 6.dp, vertical = 3.dp)
                .testTag("mode_selector"),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CameraMode.values().forEach { m ->
                val isSelected = m == mode
                Text(
                    text = m.name,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        isSelected && m == CameraMode.CINEMA -> Color(0xFFFFB300)
                        isSelected -> Color.White
                        else -> Color(0x66FFFFFF)
                    },
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (!isRecording) {
                                viewModel.setMode(m)
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("mode_tab_${m.name.lowercase()}")
                )
            }
        }

        // Recording Live Duration & Tally Counter
        if (isRecording) {
            val mins = recordingSeconds / 60
            val secs = recordingSeconds % 60
            val timeText = String.format("%02d:%02d", mins, secs)

            val infiniteTransition = rememberInfiniteTransition()
            val alpha by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 0.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500),
                    repeatMode = RepeatMode.Reverse
                )
            )

            Row(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x99FF3B30))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .testTag("recording_indicator"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .alpha(alpha)
                        .clip(CircleShape)
                        .background(Color.White)
                )
                Text(
                    text = "REC $timeText",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 2. Main Shutter and Controls Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Gallery / Last Media Thumbnail
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x22FFFFFF))
                    .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(12.dp))
                    .clickable { /* Gallery view */ }
                    .testTag("gallery_thumbnail_button"),
                contentAlignment = Alignment.Center
            ) {
                if (lastThumbnail != null) {
                    Image(
                        bitmap = lastThumbnail.asImageBitmap(),
                        contentDescription = "Last media thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(52.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Gallery",
                        tint = Color(0x66FFFFFF),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Center: Master Shutter Button
            ShutterButton(
                mode = mode,
                isRecording = isRecording,
                onClick = { viewModel.onShutterClicked() }
            )

            // Right: Camera Facing Flip (Front / Back)
            IconButton(
                onClick = {
                    if (!isRecording) {
                        val currentFacing = activeLens?.facing ?: CameraCharacteristics.LENS_FACING_BACK
                        val targetFacing = if (currentFacing == CameraCharacteristics.LENS_FACING_BACK) {
                            CameraCharacteristics.LENS_FACING_FRONT
                        } else {
                            CameraCharacteristics.LENS_FACING_BACK
                        }
                        val targetLens = availableLenses.firstOrNull { it.facing == targetFacing }
                        if (targetLens != null) {
                            viewModel.switchLens(targetLens)
                        }
                    }
                },
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color(0x22FFFFFF))
                    .testTag("switch_facing_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Switch Camera Facing",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun ShutterButton(
    mode: CameraMode,
    isRecording: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .border(3.dp, Color.White, CircleShape)
            .padding(5.dp)
            .testTag("master_shutter_button"),
        contentAlignment = Alignment.Center
    ) {
        when (mode) {
            CameraMode.PHOTO -> {
                // Crisp White Shutter Disc
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(onClick = onClick)
                        .testTag("photo_shutter_disc")
                )
            }
            CameraMode.VIDEO -> {
                // Red Video Record Button (circle when idle, rounded square when recording)
                Box(
                    modifier = Modifier
                        .size(if (isRecording) 30.dp else 58.dp)
                        .clip(if (isRecording) RoundedCornerShape(8.dp) else CircleShape)
                        .background(Color(0xFFFF3B30))
                        .clickable(onClick = onClick)
                        .testTag("video_record_disc")
                )
            }
            CameraMode.CINEMA -> {
                // Cinema Anamorphic Shutter Button (Gold rim + Red Core)
                Box(
                    modifier = Modifier
                        .size(if (isRecording) 32.dp else 58.dp)
                        .clip(if (isRecording) RoundedCornerShape(6.dp) else RoundedCornerShape(16.dp))
                        .background(if (isRecording) Color(0xFFFF3B30) else Color(0xFFE50914))
                        .border(2.dp, Color(0xFFFFB300), if (isRecording) RoundedCornerShape(6.dp) else RoundedCornerShape(16.dp))
                        .clickable(onClick = onClick)
                        .testTag("cinema_shutter_disc")
                )
            }
        }
    }
}
