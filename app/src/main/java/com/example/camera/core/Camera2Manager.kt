package com.example.camera.core

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import com.example.camera.gl.CinemaGlSurfaceView
import com.example.camera.model.AspectRatio
import com.example.camera.model.CameraCapabilities
import com.example.camera.model.CameraMode
import com.example.camera.model.CinemaSettings
import com.example.camera.model.LensInfo
import com.example.camera.model.PhotoSettings
import com.example.camera.model.VideoSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class CameraState {
    object Idle : CameraState()
    object Opening : CameraState()
    object Ready : CameraState()
    data class Error(val message: String) : CameraState()
}

class Camera2Manager(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    // Background Thread
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    // State
    private val _cameraState = MutableStateFlow<CameraState>(CameraState.Idle)
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    private val _capabilities = MutableStateFlow(CameraCapabilities())
    val capabilities: StateFlow<CameraCapabilities> = _capabilities.asStateFlow()

    private val _activeLens = MutableStateFlow<LensInfo?>(null)
    val activeLens: StateFlow<LensInfo?> = _activeLens.asStateFlow()

    private val _currentZoom = MutableStateFlow(1.0f)
    val currentZoom: StateFlow<Float> = _currentZoom.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationSec = MutableStateFlow(0)
    val recordingDurationSec: StateFlow<Int> = _recordingDurationSec.asStateFlow()

    private val _lastThumbnail = MutableStateFlow<Bitmap?>(null)
    val lastThumbnail: StateFlow<Bitmap?> = _lastThumbnail.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Pipelines
    var photoPipeline: PhotoCapturePipeline? = null
    var videoPipeline: VideoCapturePipeline? = null
    var cinemaPipeline: CinemaCapturePipeline? = null

    // Camera2 Device & Session
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null

    // Surfaces
    private var normalPreviewSurface: Surface? = null
    private var cinemaGlSurface: Surface? = null
    private var activeSurfaceTexture: SurfaceTexture? = null

    var currentMode = CameraMode.PHOTO
    var photoSettings = PhotoSettings()
    var videoSettings = VideoSettings()
    var cinemaSettings = CinemaSettings()

    var deviceOrientation = 0 // degrees

    private val scope = CoroutineScope(Dispatchers.Main)

    init {
        startBackgroundThread()
        val lenses = CameraCharacteristicsHelper.scanAllLenses(cameraManager)
        val defaultLens = lenses.firstOrNull { it.facing == CameraCharacteristics.LENS_FACING_BACK }
            ?: lenses.firstOrNull()
        _activeLens.value = defaultLens
        if (defaultLens != null) {
            _capabilities.value = CameraCharacteristicsHelper.getCapabilities(cameraManager, defaultLens.id)
        }
    }

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("Camera2Background").apply { start() }
            backgroundHandler = Handler(backgroundThread!!.looper)
            photoPipeline = PhotoCapturePipeline(context, backgroundHandler!!)
            videoPipeline = VideoCapturePipeline(context, backgroundHandler!!)
            cinemaPipeline = CinemaCapturePipeline(context, backgroundHandler!!)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
        } catch (ignored: Exception) {}
        backgroundThread = null
        backgroundHandler = null
    }

    @SuppressLint("MissingPermission")
    fun openCamera(lens: LensInfo? = _activeLens.value) {
        val targetLens = lens ?: _activeLens.value ?: return
        _activeLens.value = targetLens
        _capabilities.value = CameraCharacteristicsHelper.getCapabilities(cameraManager, targetLens.id)

        closeCamera(preserveActiveLens = true)
        _cameraState.value = CameraState.Opening

        try {
            cameraManager.openCamera(targetLens.id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    _cameraState.value = CameraState.Ready
                    startModeSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                    _cameraState.value = CameraState.Idle
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    val msg = "Camera open error: code $error"
                    _cameraState.value = CameraState.Error(msg)
                    _statusMessage.value = msg
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            _cameraState.value = CameraState.Error(e.message ?: "Failed to open camera")
        }
    }

    fun setPreviewSurface(surface: Surface?, surfaceTexture: SurfaceTexture? = null) {
        normalPreviewSurface = surface
        activeSurfaceTexture = surfaceTexture
        if (cameraDevice != null && currentMode != CameraMode.CINEMA) {
            startModeSession()
        }
    }

    fun setCinemaGlSurface(surface: Surface?) {
        cinemaGlSurface = surface
        if (cameraDevice != null && currentMode == CameraMode.CINEMA) {
            startModeSession()
        }
    }

    fun switchMode(newMode: CameraMode) {
        if (currentMode == newMode) return
        currentMode = newMode
        if (cameraDevice != null) {
            startModeSession()
        }
    }

    fun switchLens(lens: LensInfo) {
        if (_activeLens.value?.id == lens.id) return
        _activeLens.value = lens
        _currentZoom.value = lens.baseZoomFactor
        openCamera(lens)
    }

    fun setZoom(zoom: Float) {
        val caps = _capabilities.value
        val clamped = zoom.coerceIn(caps.minZoomRatio, caps.maxDigitalZoom.coerceAtLeast(1f))
        _currentZoom.value = clamped

        // Check if a physical lens switch is appropriate for logical multi-camera systems
        val availableLenses = caps.availableLenses.filter { it.facing == _activeLens.value?.facing }
        if (availableLenses.size > 1) {
            // Find best physical lens for this zoom range
            val bestLens = availableLenses
                .filter { it.baseZoomFactor <= clamped }
                .maxByOrNull { it.baseZoomFactor }
            if (bestLens != null && bestLens.id != _activeLens.value?.id) {
                switchLens(bestLens)
                return
            }
        }

        // Apply digital zoom to current preview session
        updatePreviewZoom()
    }

    private fun updatePreviewZoom() {
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return
        val lens = _activeLens.value ?: return
        val chars = try {
            cameraManager.getCameraCharacteristics(lens.id)
        } catch (e: Exception) {
            return
        }

        val relativeZoom = (_currentZoom.value / lens.baseZoomFactor).coerceAtLeast(1.0f)

        when (currentMode) {
            CameraMode.PHOTO -> photoPipeline?.applyPreviewSettings(builder, photoSettings, chars, relativeZoom)
            CameraMode.VIDEO -> videoPipeline?.applyPreviewSettings(builder, videoSettings, chars, relativeZoom)
            CameraMode.CINEMA -> cinemaPipeline?.applyCinemaSettings(builder, cinemaSettings, chars, relativeZoom)
        }

        try {
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: Exception) {
            Log.e("Camera2Manager", "Error updating zoom request", e)
        }
    }

    fun startModeSession() {
        val device = cameraDevice ?: return
        val lens = _activeLens.value ?: return
        val chars = try {
            cameraManager.getCameraCharacteristics(lens.id)
        } catch (e: Exception) {
            return
        }

        try {
            captureSession?.close()
            captureSession = null

            val relativeZoom = (_currentZoom.value / lens.baseZoomFactor).coerceAtLeast(1.0f)

            when (currentMode) {
                CameraMode.PHOTO -> {
                    val pSurf = normalPreviewSurface ?: return
                    val readerSurfaces = photoPipeline?.setupImageReaders(chars, photoSettings) ?: emptyList()
                    val targets = mutableListOf(pSurf).apply { addAll(readerSurfaces) }

                    val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    builder.addTarget(pSurf)
                    photoPipeline?.applyPreviewSettings(builder, photoSettings, chars, relativeZoom)
                    previewRequestBuilder = builder

                    device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            captureSession = session
                            try {
                                session.setRepeatingRequest(builder.build(), null, backgroundHandler)
                            } catch (e: Exception) {
                                Log.e("Camera2Manager", "Error repeating preview request", e)
                            }
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            _statusMessage.value = "Photo session configuration failed"
                        }
                    }, backgroundHandler)
                }

                CameraMode.VIDEO -> {
                    val pSurf = normalPreviewSurface ?: return
                    val targets = mutableListOf(pSurf)

                    val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                    builder.addTarget(pSurf)
                    videoPipeline?.applyPreviewSettings(builder, videoSettings, chars, relativeZoom)
                    previewRequestBuilder = builder

                    device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            captureSession = session
                            try {
                                session.setRepeatingRequest(builder.build(), null, backgroundHandler)
                            } catch (e: Exception) {
                                Log.e("Camera2Manager", "Error repeating video request", e)
                            }
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            _statusMessage.value = "Video session configuration failed"
                        }
                    }, backgroundHandler)
                }

                CameraMode.CINEMA -> {
                    val glSurf = cinemaGlSurface ?: return
                    val targets = mutableListOf(glSurf)

                    val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                    builder.addTarget(glSurf)
                    cinemaPipeline?.applyCinemaSettings(builder, cinemaSettings, chars, relativeZoom)
                    previewRequestBuilder = builder

                    device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            captureSession = session
                            try {
                                session.setRepeatingRequest(builder.build(), null, backgroundHandler)
                            } catch (e: Exception) {
                                Log.e("Camera2Manager", "Error repeating cinema request", e)
                            }
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            _statusMessage.value = "Cinema session configuration failed"
                        }
                    }, backgroundHandler)
                }
            }
        } catch (e: Exception) {
            Log.e("Camera2Manager", "Failed to start mode session: ${e.message}", e)
        }
    }

    fun takePhoto(callback: (Boolean, String) -> Unit) {
        val device = cameraDevice ?: return callback(false, "Camera not ready")
        val session = captureSession ?: return callback(false, "Session not ready")
        val lens = _activeLens.value ?: return
        val chars = cameraManager.getCameraCharacteristics(lens.id)
        val relativeZoom = (_currentZoom.value / lens.baseZoomFactor).coerceAtLeast(1.0f)

        photoPipeline?.captureStillPicture(
            device,
            session,
            chars,
            photoSettings,
            relativeZoom,
            deviceOrientation,
            object : PhotoCapturePipeline.PhotoCallback {
                override fun onPhotoSaved(uri: Uri, thumbnail: Bitmap) {
                    _lastThumbnail.value = thumbnail
                    _statusMessage.value = "Photo saved: ${uri.lastPathSegment}"
                    callback(true, "Saved")
                }

                override fun onError(message: String) {
                    _statusMessage.value = message
                    callback(false, message)
                }
            }
        )
    }

    fun toggleVideoRecording(callback: (Boolean, String) -> Unit) {
        if (_isRecording.value) {
            videoPipeline?.stopRecording(object : VideoCapturePipeline.VideoCallback {
                override fun onRecordingStarted() {}
                override fun onRecordingStopped(uri: Uri) {
                    _isRecording.value = false
                    _statusMessage.value = "Video saved: ${uri.lastPathSegment}"
                    callback(true, "Saved")
                    startModeSession()
                }
                override fun onError(message: String) {
                    _statusMessage.value = message
                    callback(false, message)
                }
            })
        } else {
            val device = cameraDevice ?: return callback(false, "Camera not ready")
            val lens = _activeLens.value ?: return
            val chars = cameraManager.getCameraCharacteristics(lens.id)
            val pSurf = normalPreviewSurface ?: return

            val recSurf = videoPipeline?.createRecorderSurface(chars, videoSettings, deviceOrientation)
                ?: return callback(false, "Failed to create recorder surface")

            val targets = listOf(pSurf, recSurf)
            val relativeZoom = (_currentZoom.value / lens.baseZoomFactor).coerceAtLeast(1.0f)

            try {
                val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                builder.addTarget(pSurf)
                builder.addTarget(recSurf)
                videoPipeline?.applyPreviewSettings(builder, videoSettings, chars, relativeZoom)

                device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
                            videoPipeline?.startRecording(object : VideoCapturePipeline.VideoCallback {
                                override fun onRecordingStarted() {
                                    _isRecording.value = true
                                    callback(true, "Recording started")
                                }
                                override fun onRecordingStopped(uri: Uri) {}
                                override fun onError(message: String) {
                                    _statusMessage.value = message
                                    callback(false, message)
                                }
                            })
                        } catch (e: Exception) {
                            callback(false, e.message ?: "Failed to start recording")
                        }
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        callback(false, "Session config failed")
                    }
                }, backgroundHandler)
            } catch (e: Exception) {
                callback(false, e.message ?: "Record error")
            }
        }
    }

    fun toggleCinemaRecording(callback: (Boolean, String) -> Unit) {
        if (_isRecording.value) {
            cinemaPipeline?.stopCinemaRecording(object : CinemaCapturePipeline.CinemaCallback {
                override fun onCinemaRecordingStarted() {}
                override fun onCinemaRecordingStopped(uri: Uri) {
                    _isRecording.value = false
                    _statusMessage.value = "Cinema clip saved: ${uri.lastPathSegment}"
                    callback(true, "Saved")
                    startModeSession()
                }
                override fun onError(message: String) {
                    _statusMessage.value = message
                    callback(false, message)
                }
            })
        } else {
            val device = cameraDevice ?: return callback(false, "Camera not ready")
            val lens = _activeLens.value ?: return
            val chars = cameraManager.getCameraCharacteristics(lens.id)
            val glSurf = cinemaGlSurface ?: return

            val recSurf = cinemaPipeline?.createCinemaRecorderSurface(chars, cinemaSettings, deviceOrientation)
                ?: return callback(false, "Failed to create cinema recorder surface")

            val targets = listOf(glSurf, recSurf)
            val relativeZoom = (_currentZoom.value / lens.baseZoomFactor).coerceAtLeast(1.0f)

            try {
                val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                builder.addTarget(glSurf)
                builder.addTarget(recSurf)
                cinemaPipeline?.applyCinemaSettings(builder, cinemaSettings, chars, relativeZoom)

                device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
                            cinemaPipeline?.startCinemaRecording(object : CinemaCapturePipeline.CinemaCallback {
                                override fun onCinemaRecordingStarted() {
                                    _isRecording.value = true
                                    callback(true, "Cinema Recording Started")
                                }
                                override fun onCinemaRecordingStopped(uri: Uri) {}
                                override fun onError(message: String) {
                                    _statusMessage.value = message
                                    callback(false, message)
                                }
                            })
                        } catch (e: Exception) {
                            callback(false, e.message ?: "Failed to start cinema recording")
                        }
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        callback(false, "Cinema session config failed")
                    }
                }, backgroundHandler)
            } catch (e: Exception) {
                callback(false, e.message ?: "Cinema record error")
            }
        }
    }

    fun configureTextureTransform(textureView: TextureView, viewWidth: Int, viewHeight: Int, aspectRatio: AspectRatio) {
        val lens = _activeLens.value ?: return
        val chars = try {
            cameraManager.getCameraCharacteristics(lens.id)
        } catch (e: Exception) {
            return
        }

        val sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        // Handle display rotation and aspect ratio fitting
        matrix.postRotate(sensorOrientation.toFloat(), centerX, centerY)
        textureView.setTransform(matrix)
    }

    fun closeCamera(preserveActiveLens: Boolean = false) {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        if (!preserveActiveLens) {
            _activeLens.value = null
        }
        _cameraState.value = CameraState.Idle
    }

    fun release() {
        closeCamera()
        photoPipeline?.close()
        videoPipeline?.release()
        cinemaPipeline?.release()
        stopBackgroundThread()
    }
}
