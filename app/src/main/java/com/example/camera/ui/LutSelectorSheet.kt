package com.example.camera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.example.camera.model.CinemaLut

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LutSelectorSheet(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val isOpen by viewModel.showLutSelector.collectAsState()
    if (!isOpen) return

    val currentSettings by viewModel.cinemaSettings.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    val categories = remember { CinemaLut.values().map { it.category }.distinct() }

    ModalBottomSheet(
        onDismissRequest = { viewModel.closeLutSelector() },
        sheetState = sheetState,
        containerColor = Color(0xFF101216),
        contentColor = Color.White,
        modifier = modifier.testTag("lut_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Cinema Color Grading (LUT)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                IconButton(onClick = { viewModel.closeLutSelector() }) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xAAFFFFFF))
                }
            }

            // 0 - 100% Intensity Slider
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x22FFFFFF))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "LUT Intensity: ${currentSettings.lutIntensity}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFFB300)
                    )
                    Text(
                        text = "Skin-Tone Safe: ${(currentSettings.lut.skinProtection * 100).toInt()}%",
                        fontSize = 11.sp,
                        color = Color(0xAAFFFFFF)
                    )
                }

                Slider(
                    value = currentSettings.lutIntensity.toFloat(),
                    onValueChange = { viewModel.setCinemaLutIntensity(it.toInt()) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFFB300),
                        activeTrackColor = Color(0xFFFFB300),
                        inactiveTrackColor = Color(0x33FFFFFF)
                    ),
                    modifier = Modifier.testTag("lut_intensity_slider")
                )
            }

            // Category Filter Pills
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    CategoryPill(
                        title = "All Looks",
                        isSelected = selectedCategory == null,
                        onClick = { selectedCategory = null }
                    )
                }
                items(categories) { cat ->
                    CategoryPill(
                        title = cat,
                        isSelected = selectedCategory == cat,
                        onClick = { selectedCategory = cat }
                    )
                }
            }

            // List of LUT items
            val filteredLuts = if (selectedCategory == null) {
                CinemaLut.values().toList()
            } else {
                CinemaLut.values().filter { it.category == selectedCategory }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredLuts) { lut ->
                    val isSelected = currentSettings.lut == lut
                    LutCard(
                        lut = lut,
                        isSelected = isSelected,
                        onClick = { viewModel.setCinemaLut(lut) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryPill(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) Color(0xFFFFB300) else Color(0x1AFFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.Black else Color.White
        )
    }
}

@Composable
private fun LutCard(
    lut: CinemaLut,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Color(0x33FFB300) else Color(0x14FFFFFF))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) Color(0xFFFFB300) else Color(0x22FFFFFF),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
            .testTag("lut_card_${lut.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Dual Color Swatch showing Shadow Tint and Highlight Tint
        Row(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .border(1.dp, Color(0x66FFFFFF), CircleShape)
        ) {
            val shadowColor = Color(
                (lut.shadowTintR * 255).toInt().coerceIn(0, 255),
                (lut.shadowTintG * 255).toInt().coerceIn(0, 255),
                (lut.shadowTintB * 255).toInt().coerceIn(0, 255)
            )
            val highlightColor = Color(
                (lut.highlightTintR * 255).toInt().coerceIn(0, 255),
                (lut.highlightTintG * 255).toInt().coerceIn(0, 255),
                (lut.highlightTintB * 255).toInt().coerceIn(0, 255)
            )
            Box(modifier = Modifier.weight(1f).height(36.dp).background(shadowColor))
            Box(modifier = Modifier.weight(1f).height(36.dp).background(highlightColor))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = lut.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color(0xFFFFB300) else Color.White
            )
            Text(
                text = lut.description,
                fontSize = 11.sp,
                color = Color(0xAAFFFFFF),
                maxLines = 2
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFFB300))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("ACTIVE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
    }
}
