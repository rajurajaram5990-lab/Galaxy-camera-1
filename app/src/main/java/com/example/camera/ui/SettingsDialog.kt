package com.example.camera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import com.example.camera.model.CameraCapabilities

@Composable
fun SettingsDialog(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val isOpen by viewModel.showSettingsDialog.collectAsState()
    if (!isOpen) return

    val capabilities by viewModel.capabilities.collectAsState()
    val activeLens by viewModel.activeLens.collectAsState()

    Dialog(onDismissRequest = { viewModel.closeSettingsDialog() }) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                .testTag("settings_dialog"),
            color = Color(0xFF14171D)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Hardware Capabilities",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    IconButton(onClick = { viewModel.closeSettingsDialog() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xAAFFFFFF))
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Active Lens Info
                    item {
                        SectionHeader("ACTIVE SENSOR")
                        InfoRow("Camera ID", activeLens?.id ?: "N/A")
                        InfoRow("Sensor Role", activeLens?.displayName ?: "Standard")
                        InfoRow("Focal Length", "${activeLens?.focalLength ?: 0f} mm")
                        InfoRow("35mm Equivalent", "${activeLens?.equivalent35mm ?: 0f} mm")
                        InfoRow("Aperture", "f/${activeLens?.aperture ?: 0f}")
                        InfoRow("Base Zoom Factor", "${activeLens?.baseZoomFactor ?: 1f}x")
                    }

                    // Physical / Logical Lenses
                    item {
                        SectionHeader("DETECTED PHYSICAL LENSES")
                        capabilities.availableLenses.forEach { lens ->
                            val label = "${lens.displayName} (ID: ${lens.id})"
                            val details = "${lens.focalLength}mm · f/${lens.aperture} · ${lens.baseZoomFactor}x"
                            InfoRow(label, details)
                        }
                    }

                    // Photo & Sensor Capabilities
                    item {
                        SectionHeader("PHOTO CAPABILITIES")
                        InfoRow("Real DNG RAW Support", if (capabilities.isRawSupported) "YES (Full DNG)" else "No Hardware RAW")
                        InfoRow("Real Multi-Frame HDR", if (capabilities.isHdrSceneSupported) "YES (Scene Mode)" else "Not supported")
                        InfoRow("Physical Flash", if (capabilities.isFlashSupported) "YES" else "None")
                        InfoRow("Digital Zoom Range", "1.0x – ${capabilities.maxDigitalZoom}x")
                    }

                    // Video & Codec Capabilities
                    item {
                        SectionHeader("VIDEO & ENCODER HARDWARE")
                        InfoRow("4K UHD (3840x2160)", if (capabilities.is4kSupported) "Supported" else "1080p Maximum")
                        InfoRow("H.265 / HEVC Hardware", if (capabilities.isH265Supported) "Hardware Encoder Ready" else "Software / H.264 Only")
                        InfoRow("Supported Video FPS", capabilities.supportedVideoFps.joinToString(", ") { "${it}fps" })
                        InfoRow("Optical Stabilization (OIS)", if (capabilities.isOpticalStabilizationSupported) "Hardware OIS Active" else "Not supported")
                        InfoRow("Electronic Stabilization (EIS)", if (capabilities.isVideoStabilizationSupported) "Hardware EIS Active" else "Not supported")
                    }

                    // Manual Controls Range
                    item {
                        SectionHeader("MANUAL EXPOSURE RANGES")
                        InfoRow("ISO Range", "${capabilities.isoRange.start} – ${capabilities.isoRange.endInclusive}")
                        val minExposureUs = capabilities.exposureTimeRange.start / 1000
                        val maxExposureMs = capabilities.exposureTimeRange.endInclusive / 1_000_000
                        InfoRow("Shutter Speed Range", "${minExposureUs}µs – ${maxExposureMs}ms")
                        InfoRow("EV Compensation", "${capabilities.exposureCompensationRange.start} to +${capabilities.exposureCompensationRange.endInclusive} (step ${capabilities.exposureCompensationStep})")
                        InfoRow("Manual Focus Distance", "0.0 (Inf) – ${capabilities.minFocusDistance} (Macro)")
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFFFFB300),
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = Color(0x99FFFFFF))
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White)
    }
}
