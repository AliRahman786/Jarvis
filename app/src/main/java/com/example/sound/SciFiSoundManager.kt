package com.example.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object SciFiSoundManager {
    private val scope = CoroutineScope(Dispatchers.Default)

    private var lastOpenTime = 0L
    private var lastCloseTime = 0L
    private var isCurrentlyOpen = false

    private val openChirpBytes: ByteArray by lazy { generateChirp(850f, 1450f, 28) }
    private val closeChirpBytes: ByteArray by lazy { generateChirp(1250f, 650f, 26) }

    fun playMicOpenSound() {
        // Silenced completely: zero high-frequency chirps or beeps to protect operator's ears
        isCurrentlyOpen = true
        lastOpenTime = System.currentTimeMillis()
    }

    fun playMicCloseSound() {
        // Silenced completely: zero high-frequency chirps or beeps to protect operator's ears
        isCurrentlyOpen = false
        lastCloseTime = System.currentTimeMillis()
    }

    private fun playPcm(pcm: ByteArray) {
        scope.launch {
            try {
                val sampleRate = 44100
                val minBuffer = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = maxOf(minBuffer, pcm.size)

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(pcm, 0, pcm.size)
                audioTrack.play()

                delay(120)
                try {
                    audioTrack.stop()
                    audioTrack.release()
                } catch (_: Exception) {}
            } catch (_: Exception) {}
        }
    }

    private fun generateChirp(startFreq: Float, endFreq: Float, durationMs: Int): ByteArray {
        val sampleRate = 44100
        val numSamples = (sampleRate * durationMs) / 1000
        val pcm = ByteArray(numSamples * 2)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / numSamples
            val freq = startFreq + (endFreq - startFreq) * t
            val phase = 2.0 * PI * freq * (i.toDouble() / sampleRate)
            val envelope = 0.5 * (1.0 - cos(2.0 * PI * t))
            val sample = (sin(phase) * envelope * 22000.0).toInt().coerceIn(-32767, 32767).toShort()

            val idx = i * 2
            pcm[idx] = (sample.toInt() and 0xFF).toByte()
            pcm[idx + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        return pcm
    }
}
