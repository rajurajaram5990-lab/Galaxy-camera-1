package com.example.camera.ui

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.camera.gl.CinemaGlSurfaceView
import com.example.camera.model.AspectRatio
import com.example.camera.model.CameraMode
import com.example.camera.model.CinemaSettings

@Composable
fun ViewfinderContainer(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentMode by viewModel.currentMode.collectAsState()
    val photoSettings by viewModel.photoSettings.collectAsState()
    val videoSettings by viewModel.videoSettings.collectAsState()
    val cinemaSettings by viewModel.cinemaSettings.collectAsState()
    val countdown by viewModel.photoCountdownSeconds.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()

    // Active aspect ratio based on mode: 3:4 for PHOTO, 9:16 for VIDEO and CINEMA (unless customized)
    val activeRatio = when (currentMode) {
        CameraMode.PHOTO -> photoSettings.aspectRatio
        CameraMode.VIDEO -> videoSettings.aspectRatio
        CameraMode.CINEMA -> cinemaSettings.aspectRatio
    }

    BoxWithConstraints(
        modifier = modifier
            .background(Color(0xFF07080A))
            .testTag("viewfinder_container"),
        contentAlignment = Alignment.Center
    ) {
        val containerWidth = maxWidth
        val containerHeight = maxHeight

        // Compute exact frame size conforming to selected aspect ratio (NOT full screen)
        val (frameWidth, frameHeight) = calculateFrameDimensions(
            containerWidthDp = containerWidth.value,
            containerHeightDp = containerHeight.value,
            aspectRatio = activeRatio
        )

        Box(
            modifier = Modifier
                .width(frameWidth.dp)
                .height(frameHeight.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black)
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(4.dp))
                .testTag("viewfinder_frame"),
            contentAlignment = Alignment.Center
        ) {
            // Embed preview surface based on pipeline
            if (currentMode == CameraMode.CINEMA) {
                // CINEMA Mode: Dedicated OpenGL ES shader pipeline
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("cinema_gl_surface_view"),
                    factory = { ctx ->
                        CinemaGlSurfaceView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            this.currentSettings = cinemaSettings
                            this.listener = object : CinemaGlSurfaceView.SurfaceListener {
                                override fun onSurfaceReady(surface: Surface, width: Int, height: Int) {
                                    viewModel.cameraManager.setCinemaGlSurface(surface)
                                }
                                override fun onHistogramData(bins: FloatArray) {
                                    viewModel.updateHistogram(bins)
                                }
                            }
                        }
                    },
                    update = { view ->
                        view.currentSettings = cinemaSettings
                    }
                )
            } else {
                // PHOTO and VIDEO Mode: Standard hardware TextureView
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("standard_texture_view"),
                    factory = { ctx ->
                        TextureView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                                    val surface = Surface(st)
                                    viewModel.cameraManager.setPreviewSurface(surface, st)
                                    viewModel.cameraManager.configureTextureTransform(this@apply, width, height, activeRatio)
                                }

                                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
                                    viewModel.cameraManager.configureTextureTransform(this@apply, width, height, activeRatio)
                                }

                                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                    viewModel.cameraManager.setPreviewSurface(null, null)
                                    return true
                                }

                                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                            }
                        }
                    },
                    update = { tv ->
                        viewModel.cameraManager.configureTextureTransform(tv, tv.width, tv.height, activeRatio)
                    }
                )
            }

            // Cinema Matte overlay for 2.39:1 anamorphic ratio or framing guides
            ViewfinderReticles(
                aspectRatio = activeRatio,
                mode = currentMode,
                isRecording = isRecording
            )

            // Timer countdown overlay
            AnimatedVisibility(
                visible = countdown > 0,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x66000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$countdown",
                        color = Color(0xFFFFB300),
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewfinderReticles(
    aspectRatio: AspectRatio,
    mode: CameraMode,
    isRecording: Boolean
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Subtle rule-of-thirds grid
        val gridColor = Color(0x1AFFFFFF)
        drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 1f)
        drawLine(gridColor, Offset(w * 2f / 3f, 0f), Offset(w * 2f / 3f, h), strokeWidth = 1f)
        drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 1f)
        drawLine(gridColor, Offset(0f, h * 2f / 3f), Offset(w, h * 2f / 3f), strokeWidth = 1f)

        // Center crosshair
        val cx = w / 2f
        val cy = h / 2f
        val reticleColor = if (isRecording) Color(0xFFFF3B30) else Color(0x66FFFFFF)
        val arm = 12.dp.toPx()
        drawLine(reticleColor, Offset(cx - arm, cy), Offset(cx - 3.dp.toPx(), cy), strokeWidth = 1.5f)
        drawLine(reticleColor, Offset(cx + 3.dp.toPx(), cy), Offset(cx + arm, cy), strokeWidth = 1.5f)
        drawLine(reticleColor, Offset(cx, cy - arm), Offset(cx, cy - 3.dp.toPx()), strokeWidth = 1.5f)
        drawLine(reticleColor, Offset(cx, cy + 3.dp.toPx()), Offset(cx, cy + arm), strokeWidth = 1.5f)

        // 4 Corner brackets
        val cornerArm = 16.dp.toPx()
        val cornerColor = if (mode == CameraMode.CINEMA) Color(0xFFFFB300) else Color(0x99FFFFFF)
        val inset = 6.dp.toPx()
        // Top-Left
        drawLine(cornerColor, Offset(inset, inset), Offset(inset + cornerArm, inset), strokeWidth = 2f)
        drawLine(cornerColor, Offset(inset, inset), Offset(inset, inset + cornerArm), strokeWidth = 2f)
        // Top-Right
        drawLine(cornerColor, Offset(w - inset, inset), Offset(w - inset - cornerArm, inset), strokeWidth = 2f)
        drawLine(cornerColor, Offset(w - inset, inset), Offset(w - inset, inset + cornerArm), strokeWidth = 2f)
        // Bottom-Left
        drawLine(cornerColor, Offset(inset, h - inset), Offset(inset + cornerArm, h - inset), strokeWidth = 2f)
        drawLine(cornerColor, Offset(inset, h - inset), Offset(inset, h - inset - cornerArm), strokeWidth = 2f)
        // Bottom-Right
        drawLine(cornerColor, Offset(w - inset, h - inset), Offset(w - inset - cornerArm, h - inset), strokeWidth = 2f)
        drawLine(cornerColor, Offset(w - inset, h - inset), Offset(w - inset, h - inset - cornerArm), strokeWidth = 2f)

        // Cinema 2.39:1 scope matte guides if cinema mode in 16:9
        if (mode == CameraMode.CINEMA && aspectRatio == AspectRatio.RATIO_16_9) {
            val scopeHeight = w / 2.39f
            val barHeight = (h - scopeHeight) / 2f
            if (barHeight > 0f) {
                drawRect(Color(0x88000000), Offset(0f, 0f), Size(w, barHeight))
                drawRect(Color(0x88000000), Offset(0f, h - barHeight), Size(w, barHeight))
                drawLine(Color(0x44FFB300), Offset(0f, barHeight), Offset(w, barHeight), strokeWidth = 1f)
                drawLine(Color(0x44FFB300), Offset(0f, h - barHeight), Offset(w, h - barHeight), strokeWidth = 1f)
            }
        }
    }
}

private fun calculateFrameDimensions(
    containerWidthDp: Float,
    containerHeightDp: Float,
    aspectRatio: AspectRatio
): Pair<Float, Float> {
    if (aspectRatio == AspectRatio.RATIO_FULL) {
        return Pair(containerWidthDp, containerHeightDp)
    }

    // In portrait orientation:
    // RatioValue is width / height (e.g. 3/4 for 4:3, 9/16 for 16:9, 1/1 for 1:1, 1/2.39 for 2.39:1)
    val targetAspect = aspectRatio.ratioValue

    val maxWidth = containerWidthDp
    val maxHeight = containerHeightDp

    var fitWidth = maxWidth
    var fitHeight = fitWidth / targetAspect

    if (fitHeight > maxHeight) {
        fitHeight = maxHeight
        fitWidth = fitHeight * targetAspect
    }

    return Pair(fitWidth, fitHeight)
}
