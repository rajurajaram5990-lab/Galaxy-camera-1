package com.example.camera.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.camera.core.AudioMeterManager
import com.example.camera.core.AudioMeterState
import com.example.camera.core.Camera2Manager
import com.example.camera.core.CameraState
import com.example.camera.model.AspectRatio
import com.example.camera.model.CameraCapabilities
import com.example.camera.model.CameraMode
import com.example.camera.model.CinemaLut
import com.example.camera.model.CinemaProfile
import com.example.camera.model.CinemaSettings
import com.example.camera.model.FlashMode
import com.example.camera.model.LensInfo
import com.example.camera.model.PhotoSettings
import com.example.camera.model.VideoCodec
import com.example.camera.model.VideoResolution
import com.example.camera.model.VideoSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    val cameraManager = Camera2Manager(application)
    val audioMeter = AudioMeterManager()

    val cameraState: StateFlow<CameraState> = cameraManager.cameraState
    val capabilities: StateFlow<CameraCapabilities> = cameraManager.capabilities
    val activeLens: StateFlow<LensInfo?> = cameraManager.activeLens
    val currentZoom: StateFlow<Float> = cameraManager.currentZoom
    val isRecording: StateFlow<Boolean> = cameraManager.isRecording
    val lastThumbnail: StateFlow<Bitmap?> = cameraManager.lastThumbnail
    val statusMessage: StateFlow<String?> = cameraManager.statusMessage
    val audioMeterState: StateFlow<AudioMeterState> = audioMeter.meterState

    private val _currentMode = MutableStateFlow(CameraMode.PHOTO)
    val currentMode: StateFlow<CameraMode> = _currentMode.asStateFlow()

    private val _photoSettings = MutableStateFlow(PhotoSettings())
    val photoSettings: StateFlow<PhotoSettings> = _photoSettings.asStateFlow()

    private val _videoSettings = MutableStateFlow(VideoSettings())
    val videoSettings: StateFlow<VideoSettings> = _videoSettings.asStateFlow()

    private val _cinemaSettings = MutableStateFlow(CinemaSettings())
    val cinemaSettings: StateFlow<CinemaSettings> = _cinemaSettings.asStateFlow()

    private val _histogramData = MutableStateFlow(FloatArray(32))
    val histogramData: StateFlow<FloatArray> = _histogramData.asStateFlow()

    private val _recordingTimerSeconds = MutableStateFlow(0)
    val recordingTimerSeconds: StateFlow<Int> = _recordingTimerSeconds.asStateFlow()

    private val _photoCountdownSeconds = MutableStateFlow(0)
    val photoCountdownSeconds: StateFlow<Int> = _photoCountdownSeconds.asStateFlow()

    private val _showLutSelector = MutableStateFlow(false)
    val showLutSelector: StateFlow<Boolean> = _showLutSelector.asStateFlow()

    private val _showSettingsDialog = MutableStateFlow(false)
    val showSettingsDialog: StateFlow<Boolean> = _showSettingsDialog.asStateFlow()

    private val _activeManualControl = MutableStateFlow<String?>(null) // "ISO", "SHUTTER", "WB", "FOCUS", "EV"
    val activeManualControl: StateFlow<String?> = _activeManualControl.asStateFlow()

    private var recordingTimerJob: Job? = null

    init {
        viewModelScope.launch {
            isRecording.collectLatest { recording ->
                if (recording) {
                    _recordingTimerSeconds.value = 0
                    recordingTimerJob = viewModelScope.launch {
                        while (true) {
                            delay(1000)
                            _recordingTimerSeconds.value += 1
                        }
                    }
                } else {
                    recordingTimerJob?.cancel()
                    _recordingTimerSeconds.value = 0
                }
            }
        }
    }

    fun onResume() {
        cameraManager.openCamera()
        if (_currentMode.value == CameraMode.CINEMA || _currentMode.value == CameraMode.VIDEO) {
            audioMeter.start()
        }
    }

    fun onPause() {
        audioMeter.stop()
        cameraManager.closeCamera(preserveActiveLens = true)
    }

    override fun onCleared() {
        super.onCleared()
        audioMeter.stop()
        cameraManager.release()
    }

    fun setMode(mode: CameraMode) {
        if (_currentMode.value == mode) return
        _currentMode.value = mode
        cameraManager.switchMode(mode)

        if (mode == CameraMode.CINEMA || mode == CameraMode.VIDEO) {
            audioMeter.start()
        } else {
            audioMeter.stop()
        }
    }

    fun switchLens(lens: LensInfo) {
        cameraManager.switchLens(lens)
    }

    fun setZoom(zoom: Float) {
        cameraManager.setZoom(zoom)
    }

    // Photo settings updates
    fun updatePhotoFlash(flashMode: FlashMode) {
        _photoSettings.value = _photoSettings.value.copy(flashMode = flashMode)
        cameraManager.photoSettings = _photoSettings.value
        cameraManager.startModeSession()
    }

    fun togglePhotoHdr() {
        if (!capabilities.value.isHdrSceneSupported) return
        _photoSettings.value = _photoSettings.value.copy(isHdrEnabled = !_photoSettings.value.isHdrEnabled)
        cameraManager.photoSettings = _photoSettings.value
        cameraManager.startModeSession()
    }

    fun togglePhotoRaw() {
        if (!capabilities.value.isRawSupported) return
        _photoSettings.value = _photoSettings.value.copy(isRawEnabled = !_photoSettings.value.isRawEnabled)
        cameraManager.photoSettings = _photoSettings.value
        cameraManager.startModeSession()
    }

    fun setPhotoAspectRatio(ratio: AspectRatio) {
        _photoSettings.value = _photoSettings.value.copy(aspectRatio = ratio)
        cameraManager.photoSettings = _photoSettings.value
    }

    fun cyclePhotoTimer() {
        val next = when (_photoSettings.value.timerSeconds) {
            0 -> 3
            3 -> 10
            else -> 0
        }
        _photoSettings.value = _photoSettings.value.copy(timerSeconds = next)
    }

    // Video settings updates
    fun toggleVideoTorch() {
        _videoSettings.value = _videoSettings.value.copy(isTorchOn = !_videoSettings.value.isTorchOn)
        cameraManager.videoSettings = _videoSettings.value
        cameraManager.startModeSession()
    }

    fun setVideoResolution(res: VideoResolution) {
        if (res == VideoResolution.RES_4K && !capabilities.value.is4kSupported) return
        _videoSettings.value = _videoSettings.value.copy(resolution = res)
        cameraManager.videoSettings = _videoSettings.value
    }

    fun setVideoFps(fps: Int) {
        if (!capabilities.value.supportedVideoFps.contains(fps)) return
        _videoSettings.value = _videoSettings.value.copy(fps = fps)
        cameraManager.videoSettings = _videoSettings.value
        cameraManager.startModeSession()
    }

    fun toggleVideoStabilization() {
        if (!capabilities.value.isVideoStabilizationSupported && !capabilities.value.isOpticalStabilizationSupported) return
        _videoSettings.value = _videoSettings.value.copy(isStabilizationEnabled = !_videoSettings.value.isStabilizationEnabled)
        cameraManager.videoSettings = _videoSettings.value
        cameraManager.startModeSession()
    }

    fun setVideoCodec(codec: VideoCodec) {
        if (codec == VideoCodec.H265 && !capabilities.value.isH265Supported) return
        _videoSettings.value = _videoSettings.value.copy(codec = codec)
        cameraManager.videoSettings = _videoSettings.value
    }

    fun setVideoAspectRatio(ratio: AspectRatio) {
        _videoSettings.value = _videoSettings.value.copy(aspectRatio = ratio)
        cameraManager.videoSettings = _videoSettings.value
    }

    // Cinema settings updates
    fun setCinemaFps(fps: Int) {
        if (!capabilities.value.supportedVideoFps.contains(fps)) return
        _cinemaSettings.value = _cinemaSettings.value.copy(fps = fps)
        cameraManager.cinemaSettings = _cinemaSettings.value
        cameraManager.startModeSession()
    }

    fun setCinemaResolution(res: VideoResolution) {
        if (res == VideoResolution.RES_4K && !capabilities.value.is4kSupported) return
        _cinemaSettings.value = _cinemaSettings.value.copy(resolution = res)
        cameraManager.cinemaSettings = _cinemaSettings.value
    }

    fun setCinemaProfile(profile: CinemaProfile) {
        _cinemaSettings.value = _cinemaSettings.value.copy(profile = profile)
        cameraManager.cinemaSettings = _cinemaSettings.value
    }

    fun setCinemaCodec(codec: VideoCodec) {
        if (codec == VideoCodec.H265 && !capabilities.value.isH265Supported) return
        _cinemaSettings.value = _cinemaSettings.value.copy(codec = codec)
        cameraManager.cinemaSettings = _cinemaSettings.value
    }

    fun setCinemaLut(lut: CinemaLut) {
        _cinemaSettings.value = _cinemaSettings.value.copy(lut = lut)
        cameraManager.cinemaSettings = _cinemaSettings.value
    }

    fun setCinemaLutIntensity(intensity: Int) {
        _cinemaSettings.value = _cinemaSettings.value.copy(lutIntensity = intensity.coerceIn(0, 100))
        cameraManager.cinemaSettings = _cinemaSettings.value
    }

    fun setCinemaAspectRatio(ratio: AspectRatio) {
        _cinemaSettings.value = _cinemaSettings.value.copy(aspectRatio = ratio)
        cameraManager.cinemaSettings = _cinemaSettings.value
    }

    fun toggleCinemaStabilization() {
        if (!capabilities.value.isVideoStabilizationSupported && !capabilities.value.isOpticalStabilizationSupported) return
        _cinemaSettings.value = _cinemaSettings.value.copy(isStabilizationEnabled = !_cinemaSettings.value.isStabilizationEnabled)
        cameraManager.cinemaSettings = _cinemaSettings.value
        cameraManager.startModeSession()
    }

    fun toggleFocusPeaking() {
        _cinemaSettings.value = _cinemaSettings.value.copy(showFocusPeaking = !_cinemaSettings.value.showFocusPeaking)
        cameraManager.cinemaSettings = _cinemaSettings.value
    }

    fun toggleZebra() {
        _cinemaSettings.value = _cinemaSettings.value.copy(showZebra = !_cinemaSettings.value.showZebra)
        cameraManager.cinemaSettings = _cinemaSettings.value
    }

    fun toggleHistogram() {
        _cinemaSettings.value = _cinemaSettings.value.copy(showHistogram = !_cinemaSettings.value.showHistogram)
        cameraManager.cinemaSettings = _cinemaSettings.value
    }

    // Cinema Manual Controls
    fun setManualIso(enabled: Boolean, value: Int = _cinemaSettings.value.isoValue) {
        _cinemaSettings.value = _cinemaSettings.value.copy(isManualIso = enabled, isoValue = value)
        cameraManager.cinemaSettings = _cinemaSettings.value
        cameraManager.startModeSession()
    }

    fun setManualShutter(enabled: Boolean, nanos: Long = _cinemaSettings.value.shutterNanos) {
        _cinemaSettings.value = _cinemaSettings.value.copy(isManualShutter = enabled, shutterNanos = nanos)
        cameraManager.cinemaSettings = _cinemaSettings.value
        cameraManager.startModeSession()
    }

    fun setManualWb(enabled: Boolean, kelvin: Int = _cinemaSettings.value.wbKelvin) {
        _cinemaSettings.value = _cinemaSettings.value.copy(isManualWb = enabled, wbKelvin = kelvin)
        cameraManager.cinemaSettings = _cinemaSettings.value
        cameraManager.startModeSession()
    }

    fun setManualFocus(enabled: Boolean, dist: Float = _cinemaSettings.value.focusDistance) {
        _cinemaSettings.value = _cinemaSettings.value.copy(isManualFocus = enabled, focusDistance = dist)
        cameraManager.cinemaSettings = _cinemaSettings.value
        cameraManager.startModeSession()
    }

    fun setManualEv(ev: Int) {
        _cinemaSettings.value = _cinemaSettings.value.copy(manualEv = ev)
        cameraManager.cinemaSettings = _cinemaSettings.value
        cameraManager.startModeSession()
    }

    fun setActiveManualControl(control: String?) {
        _activeManualControl.value = if (_activeManualControl.value == control) null else control
    }

    fun updateHistogram(bins: FloatArray) {
        _histogramData.value = bins
    }

    fun openLutSelector() { _showLutSelector.value = true }
    fun closeLutSelector() { _showLutSelector.value = false }

    fun openSettingsDialog() { _showSettingsDialog.value = true }
    fun closeSettingsDialog() { _showSettingsDialog.value = false }

    // Shutter Trigger
    fun onShutterClicked() {
        when (_currentMode.value) {
            CameraMode.PHOTO -> {
                val timer = _photoSettings.value.timerSeconds
                if (timer > 0) {
                    viewModelScope.launch {
                        _photoCountdownSeconds.value = timer
                        for (sec in timer downTo 1) {
                            _photoCountdownSeconds.value = sec
                            delay(1000)
                        }
                        _photoCountdownSeconds.value = 0
                        cameraManager.takePhoto { _, _ -> }
                    }
                } else {
                    cameraManager.takePhoto { _, _ -> }
                }
            }
            CameraMode.VIDEO -> {
                cameraManager.toggleVideoRecording { _, _ -> }
            }
            CameraMode.CINEMA -> {
                cameraManager.toggleCinemaRecording { _, _ -> }
            }
        }
    }
}
