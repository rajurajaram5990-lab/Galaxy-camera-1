package com.example.camera.core

import android.content.ContentValues
import android.content.Context
import android.graphics.Rect
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import com.example.camera.model.AspectRatio
import com.example.camera.model.VideoCodec
import com.example.camera.model.VideoResolution
import com.example.camera.model.VideoSettings
import java.io.File

class VideoCapturePipeline(
    private val context: Context,
    private val backgroundHandler: Handler
) {
    interface VideoCallback {
        fun onRecordingStarted()
        fun onRecordingStopped(uri: Uri)
        fun onError(message: String)
    }

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentVideoUri: Uri? = null
    private var currentVideoFile: File? = null

    fun createRecorderSurface(
        characteristics: CameraCharacteristics,
        settings: VideoSettings,
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

            // Setup output file / MediaStore
            val filename = "VID_${System.currentTimeMillis()}.mp4"
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

            // Resolution & aspect ratio
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

            // Bitrate: 4K needs 50-80 Mbps, 1080p needs 20-30 Mbps
            val bitrate = if (settings.resolution == VideoResolution.RES_4K) {
                60_000_000
            } else {
                settings.bitrateMbps * 1_000_000
            }
            recorder.setVideoEncodingBitRate(bitrate)

            // Codec: H.264 or H.265 (HEVC)
            if (settings.codec == VideoCodec.H265 && MediaCodecHelper.isH265Supported()) {
                recorder.setVideoEncoder(MediaRecorder.VideoEncoder.HEVC)
            } else {
                recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            }

            // Audio configuration
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(192_000)
            recorder.setAudioSamplingRate(48_000)

            // Orientation hint: handles sensor orientation + device orientation to prevent 90° rotation!
            val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            val orientationHint = (sensorOrientation + deviceOrientation + 360) % 360
            recorder.setOrientationHint(orientationHint)

            recorder.prepare()
            mediaRecorder = recorder
            return recorder.surface
        } catch (e: Exception) {
            Log.e("VideoCapturePipeline", "Failed to prepare MediaRecorder", e)
            recorder.release()
            return null
        }
    }

    fun applyPreviewSettings(
        builder: CaptureRequest.Builder,
        settings: VideoSettings,
        characteristics: CameraCharacteristics,
        zoomFactor: Float
    ) {
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)

        // Torch
        if (settings.isTorchOn) {
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
        } else {
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
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

        // FPS Range
        val fps = settings.fps
        val fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: emptyArray()
        val matchRange = fpsRanges.firstOrNull { it.upper == fps && it.lower >= minOf(15, fps / 2) }
            ?: fpsRanges.firstOrNull { it.upper == fps }
        if (matchRange != null) {
            builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, matchRange)
        }

        // Zoom
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

    fun startRecording(callback: VideoCallback) {
        val recorder = mediaRecorder ?: return
        try {
            recorder.start()
            isRecording = true
            callback.onRecordingStarted()
        } catch (e: Exception) {
            callback.onError("Start recording failed: ${e.message}")
        }
    }

    fun stopRecording(callback: VideoCallback) {
        if (!isRecording) return
        val recorder = mediaRecorder ?: return
        try {
            recorder.stop()
            recorder.reset()
            recorder.release()
            mediaRecorder = null
            isRecording = false

            // Finalize MediaStore pending flag
            currentVideoUri?.let { uri ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                    }
                    context.contentResolver.update(uri, values, null, null)
                }
                callback.onRecordingStopped(uri)
            } ?: run {
                currentVideoFile?.let { file ->
                    val uri = Uri.fromFile(file)
                    callback.onRecordingStopped(uri)
                }
            }
        } catch (e: Exception) {
            callback.onError("Stop recording failed: ${e.message}")
        }
    }

    fun release() {
        if (isRecording) {
            try {
                mediaRecorder?.stop()
            } catch (ignored: Exception) {}
        }
        mediaRecorder?.release()
        mediaRecorder = null
        isRecording = false
    }
}
