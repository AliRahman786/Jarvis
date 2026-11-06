package com.example.voice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Full-Duplex Neural Voice Receptor with Real-Time Barge-In Interruption.
 * Allows the operator to interrupt JARVIS mid-sentence at any moment:
 * as soon as non-echo operator speech is detected via partial recognition or high-decibel voice onset,
 * JARVIS immediately halts vocal transmission and listens to the operator's live words.
 */
class VoiceInputManager(private val context: Context) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _audioRmsDb = MutableStateFlow(0f)
    val audioRmsDb: StateFlow<Float> = _audioRmsDb.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private var onResultCallback: ((String) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    /**
     * Fired the instant the operator interrupts JARVIS mid-speech (barge-in),
     * allowing VoiceOutputManager to halt TTS immediately while the recognizer continues
     * capturing the rest of the operator's sentence.
     */
    var onBargeInInterruptCallback: ((String) -> Unit)? = null

    private var currentLanguageCode: String = "en-US"
    private var isPersistentMicLocked: Boolean = false

    // State guards for full-duplex barge-in & echo rejection
    var isComputing: Boolean = false
    var isJarvisSpeaking: Boolean = false
    var lastJarvisSpokenText: String = ""
    private var loudVoiceFramesWhileSpeaking: Int = 0

    private val explicitBargeInKeywords = setOf(
        "stop", "wait", "hold", "listen", "jarvis", "quiet", "pause",
        "cancel", "enough", "no", "actually", "instead", "change", "ok", "okay", "hey"
    )

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

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

        if (_isListening.value || isComputing) {
            return
        }

        launchRecognizerInternal()
    }

    @Volatile
    var lastSpeakingEndTimeMs: Long = 0L

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

    /**
     * Safely manages speech output state without self-listening acoustic feedback.
     */
    fun armBargeInMonitorWhileSpeaking() {
        pauseForSpeaking()
    }

    private var savedSystemVolume: Int = -1
    private var isMutedForBeep: Boolean = false

    private fun launchRecognizerInternal() {
        if (isComputing) {
            return
        }

        mainHandler.post {
            try {
                // Completely silence OS speech recognizer start beep/chime
                silenceSystemBeep(350L)

                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {
                                _isListening.value = true
                                loudVoiceFramesWhileSpeaking = 0
                                // Restore system audio stream as soon as listening starts
                                restoreSystemAudio()
                            }

                            override fun onBeginningOfSpeech() {
                                loudVoiceFramesWhileSpeaking = 0
                            }

                            override fun onRmsChanged(rmsdB: Float) {
                                _audioRmsDb.value = (rmsdB.coerceIn(0f, 12f) / 12f)
                                // Acoustic onset barge-in: if operator speaks strongly close to mic while JARVIS is talking
                                if (isJarvisSpeaking && rmsdB >= 8.8f) {
                                    loudVoiceFramesWhileSpeaking++
                                    if (loudVoiceFramesWhileSpeaking >= 3) {
                                        loudVoiceFramesWhileSpeaking = 0
                                        isJarvisSpeaking = false
                                        onBargeInInterruptCallback?.invoke("")
                                    }
                                } else if (rmsdB < 5.0f) {
                                    loudVoiceFramesWhileSpeaking = 0
                                }
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

                                val isTransient = error == SpeechRecognizer.ERROR_NO_MATCH ||
                                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                                        error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                                        error == SpeechRecognizer.ERROR_CLIENT ||
                                        error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY

                                if (isPersistentMicLocked) {
                                    // Persistent Mic Locked: The mic stays strictly ON until operator manually closes it.
                                    _isListening.value = true
                                    if (!isComputing) {
                                        silenceSystemBeep(300L)
                                        mainHandler.postDelayed({
                                            if (isPersistentMicLocked && !isComputing) {
                                                try {
                                                    speechRecognizer?.cancel()
                                                } catch (_: Exception) {}
                                                launchRecognizerInternal()
                                            }
                                        }, 40L)
                                    }
                                } else if (isJarvisSpeaking && !isComputing) {
                                    silenceSystemBeep(300L)
                                    mainHandler.postDelayed({
                                        if (isJarvisSpeaking && !isComputing && !_isListening.value) {
                                            launchRecognizerInternal()
                                        }
                                    }, 100L)
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
                                val partialMatches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val partialText = partialMatches?.firstOrNull()?.trim() ?: ""
                                if (partialText.isBlank()) return

                                _partialTranscript.value = partialText

                                // REAL-TIME AGI BARGE-IN: If JARVIS is currently speaking and the operator speaks non-echo words,
                                // immediately interrupt JARVIS's speech so it listens to the operator's words at that exact moment!
                                if (isJarvisSpeaking) {
                                    if (!isLikelyEcho(partialText, lastJarvisSpokenText)) {
                                        isJarvisSpeaking = false
                                        onBargeInInterruptCallback?.invoke(partialText)
                                    }
                                }
                            }

                            override fun onResults(results: Bundle?) {
                                _audioRmsDb.value = 0f
                                _partialTranscript.value = ""

                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val rawText = matches?.firstOrNull()?.trim() ?: ""

                                // Acoustic speaker output guard: drop any audio picked up during or immediately after TTS playback
                                if (isJarvisSpeaking || (System.currentTimeMillis() - lastSpeakingEndTimeMs) < 600L) {
                                    return
                                }

                                if (rawText.isNotBlank()) {
                                    if (!isLikelyEcho(rawText, lastJarvisSpokenText)) {
                                        pauseForComputing()
                                        onResultCallback?.invoke(rawText)
                                        return
                                    }
                                }

                                // If empty or self-echo, re-arm receptor smoothly and silently
                                if (isPersistentMicLocked && !isComputing) {
                                    _isListening.value = true
                                    silenceSystemBeep(300L)
                                    mainHandler.postDelayed({
                                        if (isPersistentMicLocked && !isComputing) {
                                            launchRecognizerInternal()
                                        }
                                    }, 50L)
                                } else if (isJarvisSpeaking && !isComputing) {
                                    silenceSystemBeep(300L)
                                    mainHandler.postDelayed({
                                        if (isJarvisSpeaking && !isComputing && !_isListening.value) {
                                            launchRecognizerInternal()
                                        }
                                    }, 100L)
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
                    // Enable partial results so barge-in interrupts JARVIS within ~150ms of the user speaking
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    // Ultra-low latency end-of-speech silence thresholds for rapid voice response
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 650L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 280L)
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
     * Completely silences the system start and stop chime/beep of Android's SpeechRecognizer.
     * Mutes STREAM_SYSTEM without triggering SecurityException.
     */
    private fun silenceSystemBeep(durationMs: Long = 350L) {
        val am = audioManager ?: return
        try {
            if (!isMutedForBeep) {
                if (savedSystemVolume == -1) {
                    savedSystemVolume = am.getStreamVolume(AudioManager.STREAM_SYSTEM)
                }
                isMutedForBeep = true
            }
            try {
                am.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0)
            } catch (_: Exception) {}
            try {
                am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
            } catch (_: Exception) {}

            mainHandler.removeCallbacks(restoreAudioRunnable)
            mainHandler.postDelayed(restoreAudioRunnable, durationMs)
        } catch (_: Exception) {}
    }

    private val restoreAudioRunnable = Runnable {
        restoreSystemAudio()
    }

    private fun restoreSystemAudio() {
        val am = audioManager ?: return
        try {
            if (isMutedForBeep) {
                try {
                    am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
                } catch (_: Exception) {}
                if (savedSystemVolume >= 0) {
                    try {
                        am.setStreamVolume(AudioManager.STREAM_SYSTEM, savedSystemVolume, 0)
                    } catch (_: Exception) {}
                    savedSystemVolume = -1
                }
                isMutedForBeep = false
            }
        } catch (_: Exception) {}
    }

    /**
     * Distinguishes genuine operator barge-in speech from acoustic speaker echo of JARVIS's own voice.
     */
    private fun isLikelyEcho(input: String, reference: String): Boolean {
        val cleanInput = input.lowercase(Locale.ROOT).trim()
        if (cleanInput.isBlank()) return true

        val inputWords = cleanInput.split(Regex("\\s+")).filter { it.isNotBlank() }
        // If user says an explicit interrupt keyword ("stop", "wait", "jarvis", "listen", etc.), NEVER treat as echo
        if (inputWords.any { it in explicitBargeInKeywords }) {
            return false
        }

        if (reference.isBlank()) return false
        val cleanRef = reference.lowercase(Locale.ROOT)

        // Direct phrase echo check
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
            // Anti-echo acoustic buffer: Wait 500ms after TTS finishes to let speaker sound dissipate completely!
            mainHandler.postDelayed({
                if (isPersistentMicLocked && !isComputing && !isJarvisSpeaking) {
                    _isListening.value = true
                    silenceSystemBeep(350L)
                    launchRecognizerInternal()
                }
            }, 500L)
        }
    }

    fun restartIfLocked() {
        if (isPersistentMicLocked && !isComputing && !_isListening.value) {
            launchRecognizerInternal()
        }
    }

    fun stopListening() {
        isComputing = false
        isJarvisSpeaking = false
        mainHandler.removeCallbacksAndMessages(null)
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
