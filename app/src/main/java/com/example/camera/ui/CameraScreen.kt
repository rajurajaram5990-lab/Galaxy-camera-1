package com.example.camera.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.camera.model.CameraMode

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] == true
        hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (hasCameraPermission) {
            viewModel.onResume()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission || !hasAudioPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    // Lifecycle observer
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (hasCameraPermission) {
                    viewModel.onResume()
                }
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.onPause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Status message snackbar
    val statusMsg = viewModel.statusMessage.value
    LaunchedEffect(statusMsg) {
        statusMsg?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("camera_screen"),
        containerColor = Color(0xFF07080A),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (!hasCameraPermission) {
            // Permission Request State
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color(0xFF0C0E12)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Camera & Microphone Access Required",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "This app requires direct Camera2 hardware access to operate Photo, Video and Cinema capture pipelines.",
                        fontSize = 13.sp,
                        color = Color(0xAAFFFFFF),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                    Button(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                        modifier = Modifier.testTag("grant_permissions_button")
                    ) {
                        Text("Grant Permissions", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // Main Camera UI
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top Bar with mode-specific controls
                    TopControlBar(
                        viewModel = viewModel,
                        modifier = Modifier.statusBarsPadding()
                    )

                    // Viewfinder area (Strictly aspect-ratio framed, never stretched)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        ViewfinderContainer(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Cinema Assist Overlay (Audio Meter, Histogram, Assist Toggles)
                        CinemaAssistOverlay(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Zoom slider with detected real physical lenses
                    ZoomSlider(
                        viewModel = viewModel,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    // Cinema Manual Controls (ISO, Shutter, WB, Focus, EV)
                    if (viewModel.currentMode.value == CameraMode.CINEMA) {
                        ManualControlsBar(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Bottom Bar (Strictly ONLY 3 modes: PHOTO | VIDEO | CINEMA, Shutter, Gallery, Flip)
                    BottomControlBar(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Modal Overlays
                LutSelectorSheet(viewModel = viewModel)
                SettingsDialog(viewModel = viewModel)
            }
        }
    }
}
