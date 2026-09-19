package com.example.camera.core

import android.content.ContentValues
import android.content.Context
import android.graphics.Rect
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.RggbChannelVector
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import android.util.Range
import android.view.Surface
import com.example.camera.model.AspectRatio
import com.example.camera.model.CinemaBitrate
import com.example.camera.model.CinemaSettings
import com.example.camera.model.VideoCodec
import com.example.camera.model.VideoResolution
import java.io.File
import kotlin.math.max
import kotlin.math.min

class CinemaCapturePipeline(
    private val context: Context,
    private val backgroundHandler: Handler
) {
    interface CinemaCallback {
        fun onCinemaRecordingStarted()
        fun onCinemaRecordingStopped(uri: Uri)
        fun onError(message: String)
    }

    private var cinemaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentVideoUri: Uri? = null
    private var currentVideoFile: File? = null

    fun createCinemaRecorderSurface(
        characteristics: CameraCharacteristics,
        settings: CinemaSettings,
        deviceOrientation: Int
    ): Surface? {
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

            val filename = "CINEMA_${System.currentTimeMillis()}.mp4"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Camera")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                currentVideoUri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                val pfd = context.contentResolver.openFileDescriptor(currentVideoUri!!, "rw")
                if (pfd != null) {
                    recorder.setOutputFile(pfd.fileDescriptor)
                }
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera")
                dir.mkdirs()
                currentVideoFile = File(dir, filename)
                recorder.setOutputFile(currentVideoFile!!.absolutePath)
            }

            val width: Int
            val height: Int
            if (settings.resolution == VideoResolution.RES_4K) {
                width = 3840
                height = 2160
            } else {
                width = 1920
                height = 1080
            }

            recorder.setVideoSize(width, height)
            recorder.setVideoFrameRate(settings.fps)

            // Bitrates: Standard (35 Mbps), High (60 Mbps), Maximum (100 Mbps)
            val bitrate = settings.bitrate.mbps * 1_000_000
            recorder.setVideoEncodingBitRate(bitrate)

            // Cinema Codec
            if (settings.codec == VideoCodec.H265 && MediaCodecHelper.isH265Supported()) {
                recorder.setVideoEncoder(MediaRecorder.VideoEncoder.HEVC)
            } else {
                recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            }

            // High Fidelity Audio
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(256_000)
            recorder.setAudioSamplingRate(48_000)

            val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            val orientationHint = (sensorOrientation + deviceOrientation + 360) % 360
            recorder.setOrientationHint(orientationHint)

            recorder.prepare()
            cinemaRecorder = recorder
            return recorder.surface
        } catch (e: Exception) {
            Log.e("CinemaCapturePipeline", "Failed to prepare Cinema MediaRecorder", e)
            recorder.release()
            return null
        }
    }

    fun applyCinemaSettings(
        builder: CaptureRequest.Builder,
        settings: CinemaSettings,
        characteristics: CameraCharacteristics,
        zoomFactor: Float
    ) {
        // Explicit manual control bypasses auto algorithms
        val isManualExposure = settings.isManualIso || settings.isManualShutter

        if (isManualExposure) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, settings.isoValue)
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, settings.shutterNanos)
        } else {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, settings.manualEv)
        }

        // Manual or Auto Focus
        if (settings.isManualFocus) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
            val minFocus = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
            val dist = settings.focusDistance.coerceIn(0f, minFocus)
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, dist)
        } else {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
        }

        // Manual or Auto White Balance (Kelvin color temperature)
        if (settings.isManualWb) {
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF)
            val gains = kelvinToRggbVector(settings.wbKelvin)
            builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, gains)
        } else {
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO)
        }

        // Real Stabilization
        if (settings.isStabilizationEnabled) {
            val stabModes = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) ?: intArrayOf()
            if (stabModes.contains(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)) {
                builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)
            }
            val oisModes = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) ?: intArrayOf()
            if (oisModes.contains(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)) {
                builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)
            }
        }

        // Cinema frame rate (24, 25, 30 FPS)
        val targetFps = settings.fps
        val fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: emptyArray()
        val matchRange = fpsRanges.firstOrNull { it.upper == targetFps && it.lower == targetFps }
            ?: fpsRanges.firstOrNull { it.upper == targetFps }
        if (matchRange != null) {
            builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, matchRange)
        }

        // Digital Zoom Crop
        applyZoom(builder, characteristics, zoomFactor)
    }

    private fun applyZoom(
        builder: CaptureRequest.Builder,
        characteristics: CameraCharacteristics,
        zoomFactor: Float
    ) {
        val sensorRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return
        if (zoomFactor <= 1.0f) {
            builder.set(CaptureRequest.SCALER_CROP_REGION, sensorRect)
            return
        }

        val maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
        val clampedZoom = zoomFactor.coerceIn(1.0f, maxZoom)

        val cropW = (sensorRect.width() / clampedZoom).toInt()
        val cropH = (sensorRect.height() / clampedZoom).toInt()
        val left = sensorRect.left + (sensorRect.width() - cropW) / 2
        val top = sensorRect.top + (sensorRect.height() - cropH) / 2

        val cropRegion = Rect(left, top, left + cropW, top + cropH)
        builder.set(CaptureRequest.SCALER_CROP_REGION, cropRegion)
    }

    /**
     * Converts color temperature in Kelvin (2000K - 10000K) to RGGB White Balance gains.
     */
    private fun kelvinToRggbVector(kelvin: Int): RggbChannelVector {
        val temp = (kelvin / 100f).coerceIn(20f, 100f)

        val red: Float = if (temp <= 66f) {
            255f
        } else {
            val r = temp - 60f
            329.698727446f * Math.pow(r.toDouble(), -0.1332047592).toFloat()
        }

        val green: Float = if (temp <= 66f) {
            99.4708025861f * Math.log(temp.toDouble()).toFloat() - 161.1195681661f
        } else {
            val g = temp - 60f
            288.1221695283f * Math.pow(g.toDouble(), -0.0755148492).toFloat()
        }

        val blue: Float = if (temp >= 66f) {
            255f
        } else if (temp <= 19f) {
            0f
        } else {
            val b = temp - 10f
            138.5177312231f * Math.log(b.toDouble()).toFloat() - 305.0447927307f
        }

        val rNorm = red.coerceIn(0f, 255f) / 255f
        val gNorm = green.coerceIn(0f, 255f) / 255f
        val bNorm = blue.coerceIn(0f, 255f) / 255f

        // Relative gains where G is ~1.0
        val rGain = (rNorm / (gNorm + 0.001f)).coerceIn(0.5f, 4.0f)
        val bGain = (bNorm / (gNorm + 0.001f)).coerceIn(0.5f, 4.0f)

        return RggbChannelVector(rGain, 1.0f, 1.0f, bGain)
    }

    fun startCinemaRecording(callback: CinemaCallback) {
        val recorder = cinemaRecorder ?: return
        try {
            recorder.start()
            isRecording = true
            callback.onCinemaRecordingStarted()
        } catch (e: Exception) {
            callback.onError("Start cinema recording failed: ${e.message}")
        }
    }

    fun stopCinemaRecording(callback: CinemaCallback) {
        if (!isRecording) return
        val recorder = cinemaRecorder ?: return
        try {
            recorder.stop()
            recorder.reset()
            recorder.release()
            cinemaRecorder = null
            isRecording = false

            currentVideoUri?.let { uri ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                    }
                    context.contentResolver.update(uri, values, null, null)
                }
                callback.onCinemaRecordingStopped(uri)
            } ?: run {
                currentVideoFile?.let { file ->
                    val uri = Uri.fromFile(file)
                    callback.onCinemaRecordingStopped(uri)
                }
            }
        } catch (e: Exception) {
            callback.onError("Stop cinema recording failed: ${e.message}")
        }
    }

    fun release() {
        if (isRecording) {
            try {
                cinemaRecorder?.stop()
            } catch (ignored: Exception) {}
        }
        cinemaRecorder?.release()
        cinemaRecorder = null
        isRecording = false
    }
}
