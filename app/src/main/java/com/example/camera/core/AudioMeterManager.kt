package com.example.camera.core

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

data class AudioMeterState(
    val levelDb: Float = -60f, // -60 dB to 0 dB
    val normalizedLevel: Float = 0f, // 0.0 to 1.0
    val peakDb: Float = -60f,
    val isClipping: Boolean = false
)

class AudioMeterManager {

    private val _meterState = MutableStateFlow(AudioMeterState())
    val meterState: StateFlow<AudioMeterState> = _meterState.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private var peakDbHold = -60f
    private var peakHoldFrames = 0

    @SuppressLint("MissingPermission")
    fun start() {
        if (recordingJob != null) return

        recordingJob = scope.launch {
            val sampleRate = 44100
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = max(minBufSize, 2048)

            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.CAMCORDER,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    audioRecord?.release()
                    audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        bufferSize
                    )
                }

                if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecord?.startRecording()
                    val audioBuffer = ShortArray(bufferSize / 2)

                    while (isActive) {
                        val read = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: -1
                        if (read > 0) {
                            var sumSq = 0.0
                            var maxSample = 0
                            for (i in 0 until read) {
                                val s = audioBuffer[i].toInt()
                                sumSq += s * s
                                val absS = if (s < 0) -s else s
                                if (absS > maxSample) maxSample = absS
                            }
                            val rms = sqrt(sumSq / read)
                            val db = if (rms > 1.0) {
                                (20.0 * log10(rms / 32767.0)).toFloat().coerceIn(-60f, 0f)
                            } else {
                                -60f
                            }

                            if (db > peakDbHold) {
                                peakDbHold = db
                                peakHoldFrames = 30
                            } else {
                                if (peakHoldFrames > 0) {
                                    peakHoldFrames--
                                } else {
                                    peakDbHold = (peakDbHold - 1.5f).coerceAtLeast(-60f)
                                }
                            }

                            val norm = ((db + 60f) / 60f).coerceIn(0f, 1f)
                            val isClip = maxSample >= 32000

                            _meterState.value = AudioMeterState(
                                levelDb = db,
                                normalizedLevel = norm,
                                peakDb = peakDbHold,
                                isClipping = isClip
                            )
                        }
                        delay(40) // ~25 updates/sec
                    }
                }
            } catch (e: Exception) {
                // Audio recording unavailable or permission missing
            } finally {
                cleanUp()
            }
        }
    }

    fun stop() {
        recordingJob?.cancel()
        recordingJob = null
        cleanUp()
        _meterState.value = AudioMeterState()
    }

    private fun cleanUp() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (ignored: Exception) {}
        audioRecord = null
    }
}
