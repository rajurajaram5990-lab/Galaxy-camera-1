package com.example.camera.core

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.Surface
import com.example.camera.model.AspectRatio
import com.example.camera.model.FlashMode
import com.example.camera.model.PhotoSettings
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

class PhotoCapturePipeline(
    private val context: Context,
    private val backgroundHandler: Handler
) {
    interface PhotoCallback {
        fun onPhotoSaved(uri: Uri, thumbnail: Bitmap)
        fun onError(message: String)
    }

    private var jpegReader: ImageReader? = null
    private var rawReader: ImageReader? = null
    private var dngCreator: DngCreator? = null
    private var captureSession: CameraCaptureSession? = null

    fun setupImageReaders(
        characteristics: CameraCharacteristics,
        photoSettings: PhotoSettings
    ): List<Surface> {
        val surfaces = mutableListOf<Surface>()
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return surfaces

        // Find largest JPEG size
        val jpegSizes = map.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()
        val largestJpeg = jpegSizes.maxByOrNull { it.width * it.height } ?: Size(1920, 1080)

        jpegReader?.close()
        jpegReader = ImageReader.newInstance(largestJpeg.width, largestJpeg.height, ImageFormat.JPEG, 2)
        surfaces.add(jpegReader!!.surface)

        // RAW setup if supported and enabled
        val caps = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
        if (photoSettings.isRawEnabled && caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)) {
            val rawSizes = map.getOutputSizes(ImageFormat.RAW_SENSOR) ?: emptyArray()
            val largestRaw = rawSizes.maxByOrNull { it.width * it.height }
            if (largestRaw != null) {
                rawReader?.close()
                rawReader = ImageReader.newInstance(largestRaw.width, largestRaw.height, ImageFormat.RAW_SENSOR, 2)
                surfaces.add(rawReader!!.surface)
            }
        }

        return surfaces
    }

    fun applyPreviewSettings(
        builder: CaptureRequest.Builder,
        settings: PhotoSettings,
        characteristics: CameraCharacteristics,
        zoomFactor: Float
    ) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)

        // Flash & AE
        val flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
        if (flashAvailable) {
            when (settings.flashMode) {
                FlashMode.AUTO -> {
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                    builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF)
                }
                FlashMode.ON -> {
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
                    builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF)
                }
                FlashMode.OFF -> {
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF)
                }
            }
        }

        // HDR Scene mode if supported
        val sceneModes = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES) ?: intArrayOf()
        if (settings.isHdrEnabled && sceneModes.contains(CaptureRequest.CONTROL_SCENE_MODE_HDR)) {
            builder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_HDR)
            builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_USE_SCENE_MODE)
        }

        // Noise reduction & tone mapping
        if (settings.highQualityNoiseReduction) {
            builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
            builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
            builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_HIGH_QUALITY)
        } else {
            builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_FAST)
            builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_FAST)
        }

        // Zoom crop region
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

    fun captureStillPicture(
        cameraDevice: CameraDevice,
        session: CameraCaptureSession,
        characteristics: CameraCharacteristics,
        settings: PhotoSettings,
        zoomFactor: Float,
        deviceOrientation: Int,
        callback: PhotoCallback
    ) {
        try {
            val captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            val reader = jpegReader ?: return
            captureBuilder.addTarget(reader.surface)

            val rawR = rawReader
            if (settings.isRawEnabled && rawR != null) {
                captureBuilder.addTarget(rawR.surface)
            }

            applyPreviewSettings(captureBuilder, settings, characteristics, zoomFactor)

            // Calculate rotation
            val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val jpegOrientation = (sensorOrientation + deviceOrientation + 360) % 360
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation)

            reader.setOnImageAvailableListener({ imgReader ->
                val image = imgReader.acquireLatestImage() ?: return@setOnImageAvailableListener
                backgroundHandler.post {
                    processAndSaveJpeg(image, settings.aspectRatio, jpegOrientation, callback)
                }
            }, backgroundHandler)

            if (settings.isRawEnabled && rawR != null) {
                rawR.setOnImageAvailableListener({ rReader ->
                    val rawImage = rReader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    // Wait for result callback to save DNG
                }, backgroundHandler)
            }

            session.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)
                    // DNG creation if raw available
                    if (settings.isRawEnabled && rawReader != null) {
                        try {
                            dngCreator = DngCreator(characteristics, result)
                        } catch (e: Exception) {
                            Log.e("PhotoPipeline", "DngCreator init error", e)
                        }
                    }
                }
            }, backgroundHandler)

        } catch (e: Exception) {
            callback.onError("Capture failed: ${e.message}")
        }
    }

    private fun processAndSaveJpeg(
        image: Image,
        aspectRatio: AspectRatio,
        orientation: Int,
        callback: PhotoCallback
    ) {
        try {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            image.close()

            val rawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (rawBitmap == null) {
                callback.onError("Could not decode image")
                return
            }

            // Rotate if necessary
            val matrix = Matrix()
            if (orientation != 0) {
                matrix.postRotate(orientation.toFloat())
            }
            val orientedBitmap = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)

            // Crop according to selected Aspect Ratio to ensure Preview Framing = Saved Output Framing
            val croppedBitmap = cropToAspectRatio(orientedBitmap, aspectRatio)

            // Save to MediaStore
            val filename = "IMG_${System.currentTimeMillis()}.jpg"
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Camera")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }

                // Create thumbnail
                val thumb = Bitmap.createScaledBitmap(croppedBitmap, 128, 128, true)
                callback.onPhotoSaved(uri, thumb)
            } else {
                callback.onError("Failed to create MediaStore entry")
            }

        } catch (e: Exception) {
            callback.onError("Failed to save photo: ${e.message}")
        }
    }

    private fun cropToAspectRatio(src: Bitmap, ratio: AspectRatio): Bitmap {
        if (ratio == AspectRatio.RATIO_FULL) return src

        val targetRatio = ratio.ratioValue
        val srcRatio = src.width.toFloat() / src.height.toFloat()

        if (kotlin.math.abs(srcRatio - targetRatio) < 0.01f) return src

        val cropW: Int
        val cropH: Int
        if (srcRatio > targetRatio) {
            cropH = src.height
            cropW = (src.height * targetRatio).toInt()
        } else {
            cropW = src.width
            cropH = (src.width / targetRatio).toInt()
        }

        val startX = (src.width - cropW) / 2
        val startY = (src.height - cropH) / 2

        return Bitmap.createBitmap(src, startX.coerceAtLeast(0), startY.coerceAtLeast(0), cropW, cropH)
    }

    fun close() {
        jpegReader?.close()
        jpegReader = null
        rawReader?.close()
        rawReader = null
        dngCreator?.close()
        dngCreator = null
    }
}
