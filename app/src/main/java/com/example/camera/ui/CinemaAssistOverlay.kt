package com.example.camera.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.model.CameraMode

@Composable
fun CinemaAssistOverlay(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    if (viewModel.currentMode.value != CameraMode.CINEMA) return

    val settings = viewModel.cinemaSettings.value
    val audioState = viewModel.audioMeterState.value
    val histogramData = viewModel.histogramData.value

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Top-Left: Audio Meter (Vertical dual-channel L/R dB bar)
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x99000000))
                .padding(6.dp)
                .testTag("cinema_audio_meter"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("AUDIO CH1/CH2", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xAAFFFFFF))

            Row(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .height(64.dp)
                    .width(36.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Current RMS level channel
                AudioMeterBar(
                    db = audioState.levelDb,
                    isClipping = audioState.isClipping,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                // Peak hold channel
                AudioMeterBar(
                    db = audioState.peakDb,
                    isClipping = audioState.isClipping,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }

            Text(
                text = "${audioState.levelDb.toInt()} dB",
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (audioState.isClipping) Color(0xFFFF3B30) else Color(0xCCFFFFFF),
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // Top-Right: Real-time Histogram HUD
        if (settings.showHistogram) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x99000000))
                    .padding(6.dp)
                    .testTag("cinema_histogram_hud"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("LUMA HISTOGRAM", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xAAFFFFFF))

                Canvas(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .width(100.dp)
                        .height(48.dp)
                ) {
                    val barWidth = size.width / histogramData.size.toFloat()
                    for (i in histogramData.indices) {
                        val barHeight = histogramData[i] * size.height
                        val x = i * barWidth
                        val y = size.height - barHeight
                        val col = when {
                            i < 10 -> Color(0xCC34C759) // Shadows
                            i < 22 -> Color(0xCCFFCC00) // Midtones
                            else -> Color(0xCCFF3B30)   // Highlights
                        }
                        drawRect(
                            color = col,
                            topLeft = Offset(x, y),
                            size = Size(barWidth - 0.5f, barHeight)
                        )
                    }
                }
            }
        }

        // Bottom-Right: Quick Assist Toggle Buttons (HIST, PEAK, ZEBRA)
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x99000000))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AssistToggleButton(
                label = "HIST",
                isActive = settings.showHistogram,
                testTag = "toggle_hist_btn",
                onClick = { viewModel.toggleHistogram() }
            )
            AssistToggleButton(
                label = "PEAK",
                isActive = settings.showFocusPeaking,
                testTag = "toggle_peak_btn",
                onClick = { viewModel.toggleFocusPeaking() }
            )
            AssistToggleButton(
                label = "ZEBRA",
                isActive = settings.showZebra,
                testTag = "toggle_zebra_btn",
                onClick = { viewModel.toggleZebra() }
            )
        }
    }
}

@Composable
private fun AudioMeterBar(
    db: Float,
    isClipping: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Background track
        drawRect(Color(0x33FFFFFF), Offset.Zero, size)

        // dB ranges: -60 dB to 0 dB
        // -60 to -18 dB: Green
        // -18 to -6 dB: Yellow/Amber
        // -6 to 0 dB: Red
        val normalized = ((db + 60f) / 60f).coerceIn(0f, 1f)
        val fillHeight = h * normalized
        val startY = h - fillHeight

        val fillColor = when {
            db >= -6f -> Color(0xFFFF3B30)
            db >= -18f -> Color(0xFFFFCC00)
            else -> Color(0xFF34C759)
        }

        drawRect(fillColor, Offset(0f, startY), Size(w, fillHeight))
    }
}

@Composable
private fun AssistToggleButton(
    label: String,
    isActive: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) Color(0xFFFFB300) else Color(0x22FFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (isActive) Color.Black else Color.White
        )
    }
}
