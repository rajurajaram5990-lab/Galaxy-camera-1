package com.example.camera.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import kotlin.math.roundToInt

@Composable
fun ManualControlsBar(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.cinemaSettings.collectAsState()
    val capabilities by viewModel.capabilities.collectAsState()
    val activeControl by viewModel.activeManualControl.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xCC07080A))
            .padding(vertical = 4.dp)
    ) {
        // Horizontal row of Manual control chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ISO Chip
            ManualChip(
                label = "ISO",
                valueText = if (settings.isManualIso) "${settings.isoValue}" else "AUTO",
                isActive = settings.isManualIso,
                isSelected = activeControl == "ISO",
                testTag = "manual_iso_chip",
                onClick = { viewModel.setActiveManualControl("ISO") }
            )

            // Shutter Chip
            val shutterFrac = if (settings.shutterNanos > 0) {
                val denom = (1_000_000_000.0 / settings.shutterNanos).roundToInt()
                "1/${denom}s"
            } else "1/48s"
            ManualChip(
                label = "SHUTTER",
                valueText = if (settings.isManualShutter) shutterFrac else "180° / AUTO",
                isActive = settings.isManualShutter,
                isSelected = activeControl == "SHUTTER",
                testTag = "manual_shutter_chip",
                onClick = { viewModel.setActiveManualControl("SHUTTER") }
            )

            // WB Chip
            ManualChip(
                label = "WB",
                valueText = if (settings.isManualWb) "${settings.wbKelvin}K" else "AUTO",
                isActive = settings.isManualWb,
                isSelected = activeControl == "WB",
                testTag = "manual_wb_chip",
                onClick = { viewModel.setActiveManualControl("WB") }
            )

            // Focus Chip
            ManualChip(
                label = "FOCUS",
                valueText = if (settings.isManualFocus) String.format("%.1fm", 1f / (settings.focusDistance + 0.01f)) else "AF-C",
                isActive = settings.isManualFocus,
                isSelected = activeControl == "FOCUS",
                testTag = "manual_focus_chip",
                onClick = { viewModel.setActiveManualControl("FOCUS") }
            )

            // EV Chip
            ManualChip(
                label = "EV",
                valueText = String.format("%+.1f", settings.manualEv * capabilities.exposureCompensationStep),
                isActive = settings.manualEv != 0,
                isSelected = activeControl == "EV",
                testTag = "manual_ev_chip",
                onClick = { viewModel.setActiveManualControl("EV") }
            )
        }

        // Active slider panel
        AnimatedVisibility(visible = activeControl != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xDD121418))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                when (activeControl) {
                    "ISO" -> IsoSlider(viewModel)
                    "SHUTTER" -> ShutterSlider(viewModel)
                    "WB" -> WbSlider(viewModel)
                    "FOCUS" -> FocusSlider(viewModel)
                    "EV" -> EvSlider(viewModel)
                }
            }
        }
    }
}

@Composable
private fun ManualChip(
    label: String,
    valueText: String,
    isActive: Boolean,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isSelected -> Color(0x33FFB300)
                    isActive -> Color(0x22FFFFFF)
                    else -> Color(0x14FFFFFF)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color(0xFFFFB300) else Color(0x88FFFFFF)
        )
        Text(
            text = valueText,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isActive || isSelected) Color(0xFFFFB300) else Color.White
        )
    }
}

@Composable
private fun IsoSlider(viewModel: CameraViewModel) {
    val settings by viewModel.cinemaSettings.collectAsState()
    val caps by viewModel.capabilities.collectAsState()
    val minIso = caps.isoRange.start.toFloat()
    val maxIso = caps.isoRange.endInclusive.toFloat().coerceAtLeast(1600f)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ISO SENSITIVITY", fontSize = 10.sp, color = Color(0xAAFFFFFF))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (settings.isManualIso) "${settings.isoValue}" else "AUTO",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFB300)
                )
                Text(
                    text = if (settings.isManualIso) " [Switch to AUTO]" else " [Tap to Manual]",
                    fontSize = 10.sp,
                    color = Color(0x88FFFFFF),
                    modifier = Modifier.clickable {
                        viewModel.setManualIso(!settings.isManualIso)
                    }
                )
            }
        }
        Slider(
            value = settings.isoValue.toFloat().coerceIn(minIso, maxIso),
            onValueChange = { viewModel.setManualIso(true, it.toInt()) },
            valueRange = minIso..maxIso,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFFB300),
                activeTrackColor = Color(0xFFFFB300)
            )
        )
    }
}

@Composable
private fun ShutterSlider(viewModel: CameraViewModel) {
    val settings by viewModel.cinemaSettings.collectAsState()
    // Standard cinema shutter angles: 1/24, 1/48, 1/60, 1/96, 1/120, 1/240, 1/500, 1/1000
    val denominators = listOf(24, 30, 48, 50, 60, 96, 120, 240, 500, 1000, 2000, 4000)
    val currentDenom = (1_000_000_000.0 / settings.shutterNanos).roundToInt()
    val sliderIdx = denominators.indexOfFirst { it >= currentDenom }.coerceAtLeast(0)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("SHUTTER SPEED", fontSize = 10.sp, color = Color(0xAAFFFFFF))
            Text(
                text = if (settings.isManualShutter) "1/${denominators[sliderIdx]}s" else "180° SHUTTER (AUTO)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFB300)
            )
        }
        Slider(
            value = sliderIdx.toFloat(),
            onValueChange = {
                val idx = it.roundToInt().coerceIn(0, denominators.size - 1)
                val nanos = 1_000_000_000L / denominators[idx]
                viewModel.setManualShutter(true, nanos)
            },
            valueRange = 0f..(denominators.size - 1).toFloat(),
            steps = denominators.size - 2,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFFB300),
                activeTrackColor = Color(0xFFFFB300)
            )
        )
    }
}

@Composable
private fun WbSlider(viewModel: CameraViewModel) {
    val settings by viewModel.cinemaSettings.collectAsState()
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("WHITE BALANCE (KELVIN)", fontSize = 10.sp, color = Color(0xAAFFFFFF))
            Text(
                text = if (settings.isManualWb) "${settings.wbKelvin}K" else "AUTO",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFB300)
            )
        }
        Slider(
            value = settings.wbKelvin.toFloat(),
            onValueChange = { viewModel.setManualWb(true, it.toInt()) },
            valueRange = 2500f..9500f,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFFB300),
                activeTrackColor = Color(0xFFFFB300)
            )
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("3200K Tungsten", fontSize = 9.sp, color = Color(0x66FFFFFF))
            Text("5600K Daylight", fontSize = 9.sp, color = Color(0x66FFFFFF))
            Text("7500K Shade", fontSize = 9.sp, color = Color(0x66FFFFFF))
        }
    }
}

@Composable
private fun FocusSlider(viewModel: CameraViewModel) {
    val settings by viewModel.cinemaSettings.collectAsState()
    val caps by viewModel.capabilities.collectAsState()
    val maxDist = caps.minFocusDistance.coerceAtLeast(5.0f)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("MANUAL FOCUS", fontSize = 10.sp, color = Color(0xAAFFFFFF))
            Text(
                text = if (settings.isManualFocus) "MANUAL" else "AF-CONTINUOUS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFB300)
            )
        }
        Slider(
            value = settings.focusDistance.coerceIn(0f, maxDist),
            onValueChange = { viewModel.setManualFocus(true, it) },
            valueRange = 0f..maxDist,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFFB300),
                activeTrackColor = Color(0xFFFFB300)
            )
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("∞ Infinity", fontSize = 9.sp, color = Color(0x66FFFFFF))
            Text("Macro Close-Up", fontSize = 9.sp, color = Color(0x66FFFFFF))
        }
    }
}

@Composable
private fun EvSlider(viewModel: CameraViewModel) {
    val settings by viewModel.cinemaSettings.collectAsState()
    val caps by viewModel.capabilities.collectAsState()
    val minEv = caps.exposureCompensationRange.start.toFloat()
    val maxEv = caps.exposureCompensationRange.endInclusive.toFloat()
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("EXPOSURE COMPENSATION", fontSize = 10.sp, color = Color(0xAAFFFFFF))
            Text(
                text = String.format("%+.1f EV", settings.manualEv * caps.exposureCompensationStep),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFB300)
            )
        }
        Slider(
            value = settings.manualEv.toFloat(),
            onValueChange = { viewModel.setManualEv(it.roundToInt()) },
            valueRange = minEv..maxEv,
            steps = (maxEv - minEv).toInt() - 1,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFFB300),
                activeTrackColor = Color(0xFFFFB300)
            )
        )
    }
}
