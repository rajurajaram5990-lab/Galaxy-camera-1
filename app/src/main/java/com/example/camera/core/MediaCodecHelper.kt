package com.example.camera.core

import android.media.MediaCodecInfo
import android.media.MediaCodecList

object MediaCodecHelper {

    fun isH265Supported(): Boolean {
        return hasEncoderForMime("video/hevc")
    }

    fun isH264Supported(): Boolean {
        return hasEncoderForMime("video/avc")
    }

    fun canEncode4K(mime: String): Boolean {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (info in codecList.codecInfos) {
            if (!info.isEncoder) continue
            val types = info.supportedTypes
            for (type in types) {
                if (type.equals(mime, ignoreCase = true)) {
                    val caps = info.getCapabilitiesForType(type)
                    val videoCaps = caps.videoCapabilities ?: continue
                    if (videoCaps.isSizeSupported(3840, 2160)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun hasEncoderForMime(mime: String): Boolean {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (info in codecList.codecInfos) {
            if (!info.isEncoder) continue
            for (type in info.supportedTypes) {
                if (type.equals(mime, ignoreCase = true)) {
                    return true
                }
            }
        }
        return false
    }
}
