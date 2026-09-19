package com.example.camera.core

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.params.DynamicRangeProfiles
import android.media.MediaRecorder
import android.os.Build
import android.util.Range
import android.util.Size
import com.example.camera.model.CameraCapabilities
import com.example.camera.model.LensInfo
import kotlin.math.atan
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

object CameraCharacteristicsHelper {

    fun scanAllLenses(cameraManager: CameraManager): List<LensInfo> {
        val lenses = mutableListOf<LensInfo>()
        val cameraIds = try {
            cameraManager.cameraIdList
        } catch (e: Exception) {
            emptyArray()
        }

        var mainBackFocalLength = 0f

        // First pass: find main back camera focal length to compute relative zoom factors
        for (id in cameraIds) {
            try {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING) ?: continue
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    if (focalLengths != null && focalLengths.isNotEmpty()) {
                        mainBackFocalLength = focalLengths[0]
                        break
                    }
                }
            } catch (e: Exception) {
                // Continue scanning
            }
        }
        if (mainBackFocalLength <= 0f) mainBackFocalLength = 4.3f // Typical wide default

        for (id in cameraIds) {
            try {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING) ?: continue
                val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) ?: floatArrayOf(4.3f)
                val apertures = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES) ?: floatArrayOf(1.8f)
                val sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                val minFocusDist = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
                val maxZoom = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f

                val focalLength = if (focalLengths.isNotEmpty()) focalLengths[0] else 4.3f
                val aperture = if (apertures.isNotEmpty()) apertures[0] else 1.8f

                val eq35mm = if (sensorSize != null && sensorSize.width > 0f) {
                    (focalLength * (36.0f / sensorSize.width))
                } else {
                    focalLength * 7.5f
                }

                val baseZoom = if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    (focalLength / mainBackFocalLength).coerceAtLeast(0.5f)
                } else {
                    1.0f
                }

                val displayName = when {
                    facing == CameraCharacteristics.LENS_FACING_FRONT -> "Selfie Front"
                    eq35mm < 24f -> String.format("%.1fx Ultra-Wide", baseZoom)
                    eq35mm > 50f -> String.format("%.1fx Telephoto", baseZoom)
                    minFocusDist >= 10f -> String.format("%.1fx Macro", baseZoom)
                    else -> String.format("%.1fx Wide", baseZoom)
                }

                val isMacro = minFocusDist >= 10f

                lenses.add(
                    LensInfo(
                        id = id,
                        facing = facing,
                        focalLength = focalLength,
                        equivalent35mm = eq35mm,
                        aperture = aperture,
                        baseZoomFactor = ((baseZoom * 10).roundToInt() / 10f).coerceAtLeast(0.5f),
                        displayName = displayName,
                        isMacroCapable = isMacro,
                        minFocusDistance = minFocusDist,
                        maxDigitalZoom = maxZoom,
                        isPhysicalOfLogical = false
                    )
                )

                // Check for physical cameras within logical multi-camera (Android 9+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
                    if (caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)) {
                        val physicalIds = chars.physicalCameraIds
                        for (pid in physicalIds) {
                            if (lenses.none { it.id == pid }) {
                                try {
                                    val pChars = cameraManager.getCameraCharacteristics(pid)
                                    val pFocal = pChars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: focalLength
                                    val pAperture = pChars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.firstOrNull() ?: aperture
                                    val pSensor = pChars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                                    val pEq35 = if (pSensor != null && pSensor.width > 0f) pFocal * (36f / pSensor.width) else pFocal * 7.5f
                                    val pZoom = (pFocal / mainBackFocalLength).coerceAtLeast(0.5f)
                                    val pName = when {
                                        pEq35 < 24f -> String.format("%.1fx Ultra-Wide", pZoom)
                                        pEq35 > 50f -> String.format("%.1fx Telephoto", pZoom)
                                        else -> String.format("%.1fx Wide", pZoom)
                                    }
                                    lenses.add(
                                        LensInfo(
                                            id = pid,
                                            facing = facing,
                                            focalLength = pFocal,
                                            equivalent35mm = pEq35,
                                            aperture = pAperture,
                                            baseZoomFactor = ((pZoom * 10).roundToInt() / 10f),
                                            displayName = pName,
                                            isPhysicalOfLogical = true,
                                            logicalCameraId = id
                                        )
                                    )
                                } catch (ignored: Exception) {}
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore inaccessible camera
            }
        }

        // Sort lenses by facing (back first, ultra-wide to telephoto, then front)
        return lenses.sortedWith(compareBy({ it.facing }, { it.baseZoomFactor }))
    }

    fun getCapabilities(cameraManager: CameraManager, cameraId: String): CameraCapabilities {
        return try {
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

            val isRawSupported = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)

            val sceneModes = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES) ?: intArrayOf()
            val isHdrSupported = sceneModes.contains(CameraCharacteristics.CONTROL_SCENE_MODE_HDR)

            val flashAvailable = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false

            // Supported video resolutions
            var is4k = false
            var is1080p = false
            if (map != null) {
                val recordSizes = map.getOutputSizes(MediaRecorder::class.java) ?: emptyArray()
                for (size in recordSizes) {
                    if (size.width >= 3840 && size.height >= 2160) is4k = true
                    if (size.width >= 1920 && size.height >= 1080) is1080p = true
                }
            }

            // Video FPS ranges
            val fpsRanges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: emptyArray()
            val supportedFps = mutableSetOf<Int>()
            for (range in fpsRanges) {
                val maxFps = range.upper
                if (maxFps in listOf(24, 25, 30, 60)) {
                    supportedFps.add(maxFps)
                }
            }
            if (supportedFps.isEmpty()) supportedFps.add(30)

            // Stabilization
            val stabModes = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) ?: intArrayOf()
            val isVideoStab = stabModes.contains(CameraCharacteristics.CONTROL_VIDEO_STABILIZATION_MODE_ON)
            val oisModes = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) ?: intArrayOf()
            val isOis = oisModes.contains(CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_ON)

            // Codecs
            val isH265 = MediaCodecHelper.isH265Supported()
            val isH264 = MediaCodecHelper.isH264Supported()
            if (is4k && !MediaCodecHelper.canEncode4K("video/avc") && !MediaCodecHelper.canEncode4K("video/hevc")) {
                is4k = false // Cannot encode 4K in hardware
            }

            // Zoom range
            val maxDigitalZoom = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
            var minZoomRatio = 1.0f
            var maxZoomRatio = maxDigitalZoom
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val ratioRange = chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
                if (ratioRange != null) {
                    minZoomRatio = ratioRange.lower
                    maxZoomRatio = ratioRange.upper
                }
            }

            // Exposure controls
            val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) ?: Range(100, 1600)
            val expRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE) ?: Range(1_000_000L, 1_000_000_000L)
            val evRange = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE) ?: Range(-4, 4)
            val evStepRational = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
            val evStep = if (evStepRational != null && evStepRational.denominator != 0) {
                evStepRational.numerator.toFloat() / evStepRational.denominator.toFloat()
            } else 0.5f

            val minFocus = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f

            // Check true sensor 10-bit LOG support on Android 13+
            var isTrueLog = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val profiles = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES)
                if (profiles != null) {
                    val supportedProfiles = profiles.supportedProfiles
                    if (supportedProfiles.contains(DynamicRangeProfiles.HLG10) || supportedProfiles.contains(DynamicRangeProfiles.HDR10)) {
                        isTrueLog = true
                    }
                }
            }

            CameraCapabilities(
                isRawSupported = isRawSupported,
                isHdrSceneSupported = isHdrSupported,
                isFlashSupported = flashAvailable,
                is4kSupported = is4k,
                is1080pSupported = is1080p,
                supportedVideoFps = supportedFps.toList().sorted(),
                isVideoStabilizationSupported = isVideoStab,
                isOpticalStabilizationSupported = isOis,
                isH265Supported = isH265,
                isH264Supported = isH264,
                maxDigitalZoom = maxDigitalZoom,
                minZoomRatio = minZoomRatio,
                maxZoomRatio = maxZoomRatio,
                isoRange = isoRange.lower..isoRange.upper,
                exposureTimeRange = expRange.lower..expRange.upper,
                exposureCompensationRange = evRange.lower..evRange.upper,
                exposureCompensationStep = evStep,
                minFocusDistance = minFocus,
                availableLenses = scanAllLenses(cameraManager),
                isTrueSensorLogSupported = isTrueLog
            )
        } catch (e: Exception) {
            CameraCapabilities()
        }
    }
}
