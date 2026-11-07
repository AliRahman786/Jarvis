package com.example.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Zero-Beep Continuous Neural Voice Receptor (Gemini Live / ChatGPT Voice Mode Architecture).
 *
 * Key Guarantees:
 * 1. NEVER calls AudioManager.setStreamVolume or adjustStreamVolume on STREAM_SYSTEM or any stream,
 *    completely eliminating automatic toggling of the Android Quick Settings Silent/Mute button.
 * 2. Uses a persistent silent AudioRecord stream (16kHz 16-bit mono PCM) with hardware
 *    AcousticEchoCanceler, NoiseSuppressor, and Adaptive Voice Activity Detection (VAD) when a
 *    cloud audio transcriber (Gemini multimodal audio) is available — producing ZERO system start/stop
 *    beeps when turning the mic on, during silence, or when transitioning back to listening after
 *    answering a command.
 */
class VoiceInputManager(private val context: Context) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val receptorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var continuousRecordJob: Job? = null
    private var activeAudioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _audioRmsDb = MutableStateFlow(0f)
    val audioRmsDb: StateFlow<Float> = _audioRmsDb.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private var onResultCallback: ((String) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    /**
     * Optional cloud WAV audio transcriber (e.g. GeminiProvider.transcribeAudioWav).
     * When configured and returning non-null, VoiceInputManager uses the 100% silent
     * AudioRecord + VAD pipeline so Android's SpeechRecognizer chime never plays.
     */
    var cloudAudioTranscriber: (suspend (wavBytes: ByteArray, languageCode: String) -> String?)? = null

    /**
     * Returns true if a valid cloud API key is configured for silent AudioRecord + VAD transcription.
     */
    var canUseSilentContinuousRecorder: () -> Boolean = { false }

    var onBargeInInterruptCallback: ((String) -> Unit)? = null

    private var currentLanguageCode: String = "en-US"
    private var isPersistentMicLocked: Boolean = false

    @Volatile
    var isComputing: Boolean = false

    @Volatile
    var isJarvisSpeaking: Boolean = false

    @Volatile
    var lastJarvisSpokenText: String = ""

    @Volatile
    var lastSpeakingEndTimeMs: Long = 0L

    private val explicitBargeInKeywords = setOf(
        "stop", "wait", "hold", "listen", "jarvis", "quiet", "pause",
        "cancel", "enough", "no", "actually", "instead", "change", "ok", "okay", "hey"
    )

    fun isAvailable(): Boolean {
        val hasMicFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        return hasMicFeature || SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening(
        languageCode: String = "en-US",
        persistentLock: Boolean = false,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isAvailable()) {
            onError("Speech recognition hardware or service unavailable on this device.")
            return
        }

        currentLanguageCode = languageCode
        isPersistentMicLocked = persistentLock
        onResultCallback = onResult
        onErrorCallback = onError

        if (isComputing || isJarvisSpeaking) {
            return
        }

        // Prefer 100% silent continuous AudioRecord + VAD pipeline (like Gemini Live & ChatGPT Voice)
        if (canUseSilentContinuousRecorder() && cloudAudioTranscriber != null && hasRecordAudioPermission()) {
            if (continuousRecordJob?.isActive == true) {
                _isListening.value = true
                return
            }
            if (startSilentAudioRecordLoop()) {
                return
            }
        }

        if (_isListening.value) {
            return
        }

        launchRecognizerInternal()
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Starts a persistent, zero-beep AudioRecord loop at 16kHz 16-bit mono PCM.
     * Never plays OS start/stop tones and never modifies Quick Settings or AudioManager volume.
     */
    @SuppressLint("MissingPermission")
    private fun startSilentAudioRecordLoop(): Boolean {
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufSize <= 0) {
            return false
        }

        val bufferSizeInBytes = maxOf(minBufSize * 2, 6400)
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSizeInBytes
            )
        } catch (_: Exception) {
            null
        } ?: return false

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            try {
                recorder.release()
            } catch (_: Exception) {}
            return false
        }

        // Attach hardware AcousticEchoCanceler & NoiseSuppressor if supported by the device DSP
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(recorder.audioSessionId)?.apply {
                    enabled = true
                }
            }
        } catch (_: Exception) {}

        try {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(recorder.audioSessionId)?.apply {
                    enabled = true
                }
            }
        } catch (_: Exception) {}

        try {
            recorder.startRecording()
        } catch (_: Exception) {
            try {
                recorder.release()
            } catch (_: Exception) {}
            return false
        }

        activeAudioRecord = recorder
        _isListening.value = true

        continuousRecordJob?.cancel()
        continuousRecordJob = receptorScope.launch {
            // 20ms frame = 320 shorts (640 bytes) at 16,000 Hz
            val frameShorts = ShortArray(320)
            val preRollFrames = ArrayDeque< ByteArray >(16) // ~320ms pre-speech buffer
            val utteranceOut = ByteArrayOutputStream()

            var isCapturingSpeech = false
            var consecutiveVoiceFrames = 0
            var silenceFramesAfterSpeech = 0
            var totalSpeechFrames = 0
            var adaptiveNoiseFloor = 180.0

            try {
                while (isActive && (isPersistentMicLocked || _isListening.value)) {
                    val readCount = recorder.read(frameShorts, 0, frameShorts.size)
                    if (readCount <= 0) continue

                    val now = System.currentTimeMillis()
                    // Gate microphone during TTS playback, computing, or 650ms acoustic decay after speaking
                    if (isJarvisSpeaking || isComputing || (now - lastSpeakingEndTimeMs) < 650L) {
                        _audioRmsDb.value = 0f
                        if (isCapturingSpeech) {
                            isCapturingSpeech = false
                            consecutiveVoiceFrames = 0
                            silenceFramesAfterSpeech = 0
                            totalSpeechFrames = 0
                            utteranceOut.reset()
                            preRollFrames.clear()
                            _partialTranscript.value = ""
                        }
                        continue
                    }

                    // Ensure _isListening reflects active silent readiness after JARVIS finishes speaking
                    if (!_isListening.value && isPersistentMicLocked) {
                        _isListening.value = true
                    }

                    // Compute RMS amplitude of the 20ms frame
                    var sumSquares = 0.0
                    for (i in 0 until readCount) {
                        val s = frameShorts[i].toDouble()
                        sumSquares += s * s
                    }
                    val rms = sqrt(sumSquares / readCount)

                    // Convert RMS to normalized 0..1 visual amplitude for the 3D HUD Orb
                    val dbNormalized = if (rms > 80.0) {
                        ((20.0 * log10(rms / 80.0)) / 38.0).toFloat().coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    _audioRmsDb.value = dbNormalized

                    // Convert current frame shorts to little-endian PCM bytes
                    val frameBytes = ByteArray(readCount * 2)
                    ByteBuffer.wrap(frameBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(frameShorts, 0, readCount)

                    val dynamicSpeechThreshold = maxOf(460.0, adaptiveNoiseFloor * 2.6)
                    val isVoiceFrame = rms >= dynamicSpeechThreshold

                    if (!isCapturingSpeech) {
                        // Slowly adapt ambient noise floor during non-speech
                        if (rms < dynamicSpeechThreshold) {
                            adaptiveNoiseFloor = (adaptiveNoiseFloor * 0.96) + (rms.coerceIn(90.0, 650.0) * 0.04)
                        }
                        if (preRollFrames.size >= 16) {
                            preRollFrames.removeFirst()
                        }
                        preRollFrames.addLast(frameBytes)

                        if (isVoiceFrame) {
                            consecutiveVoiceFrames++
                            if (consecutiveVoiceFrames >= 4) { // ~80ms of clear voice onset
                                isCapturingSpeech = true
                                silenceFramesAfterSpeech = 0
                                totalSpeechFrames = consecutiveVoiceFrames
                                utteranceOut.reset()
                                for (pre in preRollFrames) {
                                    utteranceOut.write(pre)
                                }
                                preRollFrames.clear()
                                _partialTranscript.value = "Listening..."
                            }
                        } else {
                            consecutiveVoiceFrames = 0
                        }
                    } else {
                        utteranceOut.write(frameBytes)
                        totalSpeechFrames++

                        if (rms >= maxOf(340.0, adaptiveNoiseFloor * 1.9)) {
                            silenceFramesAfterSpeech = 0
                        } else {
                            silenceFramesAfterSpeech++
                        }

                        // Complete utterance after ~860ms (43 frames) of silence or 14 seconds (700 frames) max
                        if (silenceFramesAfterSpeech >= 43 || totalSpeechFrames >= 700) {
                            val capturedPcm = utteranceOut.toByteArray()
                            val capturedSpeechFrames = totalSpeechFrames

                            isCapturingSpeech = false
                            consecutiveVoiceFrames = 0
                            silenceFramesAfterSpeech = 0
                            totalSpeechFrames = 0
                            utteranceOut.reset()
                            _audioRmsDb.value = 0f

                            // Only transcribe if utterance contained meaningful speech (> 220ms)
                            if (capturedSpeechFrames >= 12 && capturedPcm.size > 3200) {
                                _partialTranscript.value = "Processing speech..."
                                val wavBytes = encodePcm16MonoToWav(capturedPcm, sampleRate)
                                val transcriber = cloudAudioTranscriber
                                val transcript = try {
                                    transcriber?.invoke(wavBytes, currentLanguageCode)?.trim()
                                } catch (_: Exception) {
                                    null
                                }
                                _partialTranscript.value = ""

                                if (!transcript.isNullOrBlank() && !isLikelyEcho(transcript, lastJarvisSpokenText)) {
                                    isComputing = true
                                    if (!isPersistentMicLocked) {
                                        _isListening.value = false
                                    }
                                    withContext(Dispatchers.Main) {
                                        onResultCallback?.invoke(transcript)
                                    }
                                    if (!isPersistentMicLocked) {
                                        break
                                    }
                                }
                            } else {
                                _partialTranscript.value = ""
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                releaseSilentRecorder()
            }
        }
        return true
    }

    private fun releaseSilentRecorder() {
        try {
            echoCanceler?.release()
        } catch (_: Exception) {}
        echoCanceler = null

        try {
            noiseSuppressor?.release()
        } catch (_: Exception) {}
        noiseSuppressor = null

        try {
            activeAudioRecord?.stop()
        } catch (_: Exception) {}
        try {
            activeAudioRecord?.release()
        } catch (_: Exception) {}
        activeAudioRecord = null
    }

    /**
     * Wraps raw 16-bit mono little-endian PCM bytes with a standard 44-byte RIFF/WAV header.
     */
    private fun encodePcm16MonoToWav(pcmData: ByteArray, sampleRate: Int): ByteArray {
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * 2 // 16-bit mono = 2 bytes per sample
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM chunk size
            putShort(1) // AudioFormat 1 = PCM
            putShort(1) // NumChannels 1 = Mono
            putInt(sampleRate)
            putInt(byteRate)
            putShort(2) // BlockAlign = 2
            putShort(16) // BitsPerSample = 16
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmData.size)
        }.array()

        return header + pcmData
    }

    /**
     * Suspends microphone input while speech synthesis outputs through the speaker,
     * preventing acoustic feedback loops where the microphone transcribes the device's own speaker.
     */
    fun pauseForSpeaking() {
        isJarvisSpeaking = true
        mainHandler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.stopListening()
        } catch (_: Exception) {}
        _isListening.value = false
        _audioRmsDb.value = 0f
        _partialTranscript.value = ""
    }

    fun armBargeInMonitorWhileSpeaking() {
        pauseForSpeaking()
    }

    private fun launchRecognizerInternal() {
        if (isComputing || isJarvisSpeaking) {
            return
        }

        // If silent continuous recorder is available, always use it to avoid OS SpeechRecognizer beeps
        if (canUseSilentContinuousRecorder() && cloudAudioTranscriber != null && hasRecordAudioPermission()) {
            if (continuousRecordJob?.isActive == true) {
                _isListening.value = true
                return
            }
            if (startSilentAudioRecordLoop()) {
                return
            }
        }

        val elapsedSinceSpeech = System.currentTimeMillis() - lastSpeakingEndTimeMs
        if (elapsedSinceSpeech < 650L) {
            val waitMs = (650L - elapsedSinceSpeech).coerceAtLeast(80L)
            mainHandler.postDelayed({
                if (!isComputing && !isJarvisSpeaking && isPersistentMicLocked) {
                    launchRecognizerInternal()
                }
            }, waitMs)
            return
        }

        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {
                                _isListening.value = true
                            }

                            override fun onBeginningOfSpeech() {}

                            override fun onRmsChanged(rmsdB: Float) {
                                if (isJarvisSpeaking) {
                                    _audioRmsDb.value = 0f
                                    return
                                }
                                _audioRmsDb.value = (rmsdB.coerceIn(0f, 12f) / 12f)
                            }

                            override fun onBufferReceived(buffer: ByteArray?) {}

                            override fun onEndOfSpeech() {
                                _audioRmsDb.value = 0f
                                if (!isPersistentMicLocked) {
                                    _isListening.value = false
                                }
                            }

                            override fun onError(error: Int) {
                                _audioRmsDb.value = 0f
                                _partialTranscript.value = ""

                                if (isJarvisSpeaking || isComputing) {
                                    _isListening.value = false
                                    return
                                }

                                val isTransient = error == SpeechRecognizer.ERROR_NO_MATCH ||
                                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                                        error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                                        error == SpeechRecognizer.ERROR_CLIENT ||
                                        error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY

                                if (isPersistentMicLocked) {
                                    _isListening.value = true
                                    mainHandler.removeCallbacksAndMessages(null)
                                    mainHandler.postDelayed({
                                        if (isPersistentMicLocked && !isComputing && !isJarvisSpeaking) {
                                            launchRecognizerInternal()
                                        }
                                    }, 800L)
                                } else {
                                    _isListening.value = false
                                    if (!isTransient && !isComputing) {
                                        val message = when (error) {
                                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                                            SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition"
                                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service is busy"
                                            SpeechRecognizer.ERROR_SERVER -> "Server error"
                                            else -> "Recognition error: code $error"
                                        }
                                        onErrorCallback?.invoke(message)
                                    }
                                }
                            }

                            override fun onPartialResults(partialResults: Bundle?) {
                                if (isJarvisSpeaking || (System.currentTimeMillis() - lastSpeakingEndTimeMs) < 650L) {
                                    return
                                }
                                val partialMatches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val partialText = partialMatches?.firstOrNull()?.trim() ?: ""
                                if (partialText.isBlank()) return
                                _partialTranscript.value = partialText
                            }

                            override fun onResults(results: Bundle?) {
                                _audioRmsDb.value = 0f
                                _partialTranscript.value = ""

                                if (isJarvisSpeaking || (System.currentTimeMillis() - lastSpeakingEndTimeMs) < 650L) {
                                    if (isPersistentMicLocked && !isComputing && !isJarvisSpeaking) {
                                        mainHandler.postDelayed({
                                            if (isPersistentMicLocked && !isComputing && !isJarvisSpeaking) {
                                                launchRecognizerInternal()
                                            }
                                        }, 700L)
                                    }
                                    return
                                }

                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val rawText = matches?.firstOrNull()?.trim() ?: ""

                                if (rawText.isNotBlank()) {
                                    if (!isLikelyEcho(rawText, lastJarvisSpokenText)) {
                                        pauseForComputing()
                                        onResultCallback?.invoke(rawText)
                                        return
                                    }
                                }

                                if (isPersistentMicLocked && !isComputing && !isJarvisSpeaking) {
                                    _isListening.value = true
                                    mainHandler.postDelayed({
                                        if (isPersistentMicLocked && !isComputing && !isJarvisSpeaking) {
                                            launchRecognizerInternal()
                                        }
                                    }, 700L)
                                } else {
                                    _isListening.value = false
                                }
                            }

                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        })
                    }
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguageCode)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, currentLanguageCode)
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf(currentLanguageCode))
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 700L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300L)
                }
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                if (!isPersistentMicLocked) {
                    _isListening.value = false
                }
                _audioRmsDb.value = 0f
                if (!isPersistentMicLocked && !isComputing) {
                    onErrorCallback?.invoke("Receptor initialization error: ${e.localizedMessage}")
                }
            }
        }
    }

    /**
     * Distinguishes genuine operator speech from acoustic speaker echo of JARVIS's own voice.
     */
    private fun isLikelyEcho(input: String, reference: String): Boolean {
        val cleanInput = input.lowercase(Locale.ROOT).trim()
        if (cleanInput.isBlank()) return true

        val inputWords = cleanInput.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (inputWords.any { it in explicitBargeInKeywords }) {
            return false
        }

        if (reference.isBlank()) return false
        val cleanRef = reference.lowercase(Locale.ROOT)

        if (cleanInput.length >= 6 && cleanRef.contains(cleanInput)) {
            return true
        }

        val sigInputTokens = inputWords.filter { it.length > 2 }.toSet()
        val sigRefTokens = cleanRef.split(Regex("\\s+")).filter { it.length > 2 }.toSet()
        if (sigInputTokens.isEmpty() || sigRefTokens.isEmpty()) return false

        val overlap = sigInputTokens.intersect(sigRefTokens).size.toFloat() / sigInputTokens.size.toFloat()
        return overlap >= 0.55f
    }

    fun setPersistentMicLock(locked: Boolean) {
        isPersistentMicLocked = locked
        if (!locked && !isJarvisSpeaking) {
            stopListening()
        }
    }

    fun isPersistentLock(): Boolean = isPersistentMicLocked

    fun pauseForComputing() {
        isComputing = true
        mainHandler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {}
        if (!isPersistentMicLocked) {
            _isListening.value = false
        }
        _audioRmsDb.value = 0f
        _partialTranscript.value = ""
    }

    fun resumeAfterSpeaking() {
        isComputing = false
        isJarvisSpeaking = false
        lastSpeakingEndTimeMs = System.currentTimeMillis()
        mainHandler.removeCallbacksAndMessages(null)
        if (isPersistentMicLocked) {
            // If silent AudioRecord loop is already active, it automatically un-gates after 650ms with ZERO beep!
            if (continuousRecordJob?.isActive == true) {
                mainHandler.postDelayed({
                    if (isPersistentMicLocked && !isComputing && !isJarvisSpeaking) {
                        _isListening.value = true
                    }
                }, 650L)
                return
            }

            mainHandler.postDelayed({
                if (isPersistentMicLocked && !isComputing && !isJarvisSpeaking) {
                    _isListening.value = true
                    launchRecognizerInternal()
                }
            }, 650L)
        }
    }

    fun restartIfLocked() {
        if (isPersistentMicLocked && !isComputing && !isJarvisSpeaking && !_isListening.value) {
            launchRecognizerInternal()
        }
    }

    fun stopListening() {
        isComputing = false
        isJarvisSpeaking = false
        isPersistentMicLocked = false
        mainHandler.removeCallbacksAndMessages(null)
        continuousRecordJob?.cancel()
        continuousRecordJob = null
        releaseSilentRecorder()
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        _isListening.value = false
        _audioRmsDb.value = 0f
        _partialTranscript.value = ""
    }
}
