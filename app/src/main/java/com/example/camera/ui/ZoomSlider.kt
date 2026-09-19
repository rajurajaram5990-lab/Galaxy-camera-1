package com.example.camera.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.model.CameraMode
import kotlin.math.roundToInt

@Composable
fun ZoomSlider(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val currentZoom = viewModel.currentZoom.value
    val capabilities = viewModel.capabilities.value
    val activeLens = viewModel.activeLens.value
    val mode = viewModel.currentMode.value

    var isSliderExpanded by remember { mutableStateOf(false) }

    val accentColor = if (mode == CameraMode.CINEMA) Color(0xFFFFB300) else Color.White
    val lensesForFacing = capabilities.availableLenses.filter { it.facing == activeLens?.facing }

    val minZoom = capabilities.minZoomRatio.coerceAtLeast(0.5f)
    val maxZoom = capabilities.maxDigitalZoom.coerceAtLeast(1.0f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Quick Real Lens Selector Buttons
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0x66000000))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Show buttons for each real physical/logical lens
            if (lensesForFacing.size > 1) {
                lensesForFacing.forEach { lens ->
                    val isSelected = activeLens?.id == lens.id
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) Color(0x33FFFFFF) else Color.Transparent)
                            .clickable {
                                viewModel.switchLens(lens)
                            }
                            .testTag("lens_button_${lens.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${lens.baseZoomFactor}x",
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) accentColor else Color(0xCCFFFFFF)
                        )
                    }
                }
            }

            // Current Zoom Readout Badge (tap to toggle smooth slider)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x22FFFFFF))
                    .clickable { isSliderExpanded = !isSliderExpanded }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("zoom_readout_badge"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = String.format("%.1fx", currentZoom),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }
        }

        // Expanded Smooth Zoom Slider (physical switching + SCALER_CROP_REGION)
        AnimatedVisibility(visible = isSliderExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x88000000))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Smooth Zoom (Physical + Sensor Crop)",
                    fontSize = 10.sp,
                    color = Color(0x88FFFFFF)
                )

                Slider(
                    value = currentZoom,
                    onValueChange = { newZoom ->
                        viewModel.setZoom(newZoom)
                    },
                    valueRange = minZoom..maxZoom,
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = Color(0x33FFFFFF)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("smooth_zoom_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${minZoom}x", fontSize = 10.sp, color = Color(0x66FFFFFF))
                    Text("1.0x", fontSize = 10.sp, color = Color(0x66FFFFFF))
                    Text("${maxZoom.roundToInt()}x", fontSize = 10.sp, color = Color(0x66FFFFFF))
                }
            }
        }
    }
}
