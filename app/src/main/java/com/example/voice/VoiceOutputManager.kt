package com.example.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import com.example.data.prefs.JarvisPersona
import com.example.voice.engine.AndroidNativeSpeechEngine
import com.example.voice.engine.SpeechSynthesisEngine
import com.example.voice.engine.VoiceGender
import com.example.voice.engine.VoiceProfile
import com.example.voice.engine.VoiceSynthesisOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Dedicated Voice Synthesis and Audio Management Engine.
 * Decoupled completely from Gemini - uses independent native high-quality speech synthesis
 * with male, female, rate, pitch, sentence chunking, audio focus, and voice preview capabilities.
 */
class VoiceOutputManager(
    private val context: Context,
    private val speechEngine: SpeechSynthesisEngine = AndroidNativeSpeechEngine(context)
) : TextToSpeech.OnInitListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _speechAmplitude = MutableStateFlow(0f)
    val speechAmplitude: StateFlow<Float> = _speechAmplitude.asStateFlow()

    private var amplitudeJob: Job? = null

    private var currentPitch: Float = 0.95f
    private var currentRate: Float = 1.05f
    private var currentLocale: Locale = Locale.US
    private var currentVoiceName: String = "Natural Male (Tactical)"
    private var currentGenderPreference: VoiceGender = VoiceGender.MALE
    private var isCurrentUltronMode: Boolean = false

    var onStartSpeakingCallback: (() -> Unit)? = null
    var onEndSpeakingCallback: (() -> Unit)? = null

    override fun onInit(status: Int) {
        // Maintained for backward compatibility interface contracts
    }

    /**
     * Binds the system-native Android TextToSpeech instance created in MainActivity.
     */
    fun bindActivityTextToSpeech(activityTts: TextToSpeech) {
        (speechEngine as? AndroidNativeSpeechEngine)?.attachExternalTts(activityTts)
    }

    fun unbindActivityTextToSpeech() {
        (speechEngine as? AndroidNativeSpeechEngine)?.detachExternalTts()
    }

    fun isReady(): Boolean = speechEngine.isReady()

    fun getAvailableVoices(): List<VoiceProfile> = speechEngine.getAvailableVoices(currentLocale)

    fun applyPersona(
        persona: JarvisPersona,
        languageCode: String = "en-US",
        customPitch: Float? = null,
        customRate: Float? = null,
        neuralVoice: String = persona.defaultVoiceName
    ) {
        currentVoiceName = neuralVoice
        isCurrentUltronMode = persona.isUltronMode
        currentLocale = parseLocale(languageCode)

        // Classify gender preference from persona or voice name
        currentGenderPreference = when {
            persona == JarvisPersona.GIRLFRIEND || persona == JarvisPersona.FRIDAY -> VoiceGender.FEMALE
            neuralVoice.contains("female", ignoreCase = true) ||
                    neuralVoice.contains("pari", ignoreCase = true) ||
                    neuralVoice.contains("friday", ignoreCase = true) ||
                    neuralVoice.contains("aoede", ignoreCase = true) ||
                    neuralVoice.contains("kore", ignoreCase = true) ||
                    neuralVoice.contains("leda", ignoreCase = true) ||
                    neuralVoice.contains("zephyr", ignoreCase = true) ||
                    neuralVoice.contains("sfg", ignoreCase = true) ||
                    neuralVoice.contains("tpd", ignoreCase = true) ||
                    neuralVoice.contains("fis", ignoreCase = true) ||
                    neuralVoice.contains("warm", ignoreCase = true) ||
                    neuralVoice.contains("crisp", ignoreCase = true) -> VoiceGender.FEMALE
            else -> VoiceGender.MALE
        }

        currentPitch = customPitch ?: persona.defaultPitch
        currentRate = customRate ?: persona.defaultSpeechRate
    }

    private fun parseLocale(languageCode: String): Locale {
        return try {
            Locale.forLanguageTag(languageCode)
        } catch (_: Exception) {
            Locale.US
        }
    }

    /**
     * Primary speech invocation.
     * Uses the independent speech engine. Never relies on or communicates with Gemini voice API.
     */
    fun speak(
        text: String,
        apiKey: String = "", // Preserved for API signature compatibility, unused
        overrideVoice: String? = null,
        isUltron: Boolean = isCurrentUltronMode,
        onComplete: (() -> Unit)? = null
    ) {
        val voiceToUse = overrideVoice ?: currentVoiceName
        val gender = when {
            voiceToUse.contains("female", ignoreCase = true) ||
                    voiceToUse.contains("pari", ignoreCase = true) ||
                    voiceToUse.contains("friday", ignoreCase = true) ||
                    voiceToUse.contains("aoede", ignoreCase = true) ||
                    voiceToUse.contains("kore", ignoreCase = true) ||
                    voiceToUse.contains("leda", ignoreCase = true) ||
                    voiceToUse.contains("zephyr", ignoreCase = true) ||
                    voiceToUse.contains("sfg", ignoreCase = true) ||
                    voiceToUse.contains("tpd", ignoreCase = true) ||
                    voiceToUse.contains("fis", ignoreCase = true) ||
                    voiceToUse.contains("warm", ignoreCase = true) ||
                    voiceToUse.contains("crisp", ignoreCase = true) -> VoiceGender.FEMALE
            voiceToUse.contains("male", ignoreCase = true) ||
                    voiceToUse.contains("butler", ignoreCase = true) ||
                    voiceToUse.contains("jarvis", ignoreCase = true) ||
                    voiceToUse.contains("rjs", ignoreCase = true) ||
                    voiceToUse.contains("iol", ignoreCase = true) -> VoiceGender.MALE
            else -> currentGenderPreference
        }

        stop()

        val options = VoiceSynthesisOptions(
            pitch = currentPitch,
            speechRate = currentRate,
            voiceName = voiceToUse,
            genderPreference = gender,
            locale = currentLocale,
            isUltronMode = isUltron
        )

        speechEngine.speak(
            text = text,
            options = options,
            onStart = {
                _isSpeaking.value = true
                onStartSpeakingCallback?.invoke()
                startAmplitudeSimulation()
            },
            onDone = {
                _isSpeaking.value = false
                stopAmplitudeSimulation()
                // 400ms anti-echo cooldown before listening starts
                mainHandler.postDelayed({
                    onEndSpeakingCallback?.invoke()
                    onComplete?.invoke()
                }, 400)
            },
            onError = { _ ->
                _isSpeaking.value = false
                stopAmplitudeSimulation()
                mainHandler.postDelayed({
                    onEndSpeakingCallback?.invoke()
                    onComplete?.invoke()
                }, 400)
            }
        )
    }

    /**
     * Preview sample voice playback for Settings voice selector.
     */
    fun previewVoice(
        voiceName: String,
        sampleText: String = "All systems online and calibrated. Ready for your directive, Commander.",
        onComplete: (() -> Unit)? = null
    ) {
        val gender = if (
            voiceName.contains("female", ignoreCase = true) ||
            voiceName.contains("pari", ignoreCase = true) ||
            voiceName.contains("friday", ignoreCase = true) ||
            voiceName.contains("aoede", ignoreCase = true) ||
            voiceName.contains("kore", ignoreCase = true) ||
            voiceName.contains("leda", ignoreCase = true) ||
            voiceName.contains("zephyr", ignoreCase = true) ||
            voiceName.contains("sfg", ignoreCase = true) ||
            voiceName.contains("tpd", ignoreCase = true) ||
            voiceName.contains("fis", ignoreCase = true) ||
            voiceName.contains("warm", ignoreCase = true) ||
            voiceName.contains("crisp", ignoreCase = true)
        ) {
            VoiceGender.FEMALE
        } else {
            VoiceGender.MALE
        }

        val options = VoiceSynthesisOptions(
            pitch = if (gender == VoiceGender.FEMALE) 1.04f else 0.96f,
            speechRate = 1.0f,
            voiceName = voiceName,
            genderPreference = gender,
            locale = currentLocale,
            isUltronMode = voiceName.contains("deep", ignoreCase = true)
        )

        stop()

        speechEngine.speak(
            text = sampleText,
            options = options,
            onStart = {
                _isSpeaking.value = true
                startAmplitudeSimulation()
            },
            onDone = {
                _isSpeaking.value = false
                stopAmplitudeSimulation()
                onComplete?.invoke()
            },
            onError = {
                _isSpeaking.value = false
                stopAmplitudeSimulation()
                onComplete?.invoke()
            }
        )
    }

    private fun startAmplitudeSimulation() {
        amplitudeJob?.cancel()
        amplitudeJob = scope.launch {
            while (isActive && _isSpeaking.value) {
                val time = System.currentTimeMillis()
                val amp = 0.25f + (Math.sin(time / 65.0).toFloat() * 0.35f +
                        Math.cos(time / 42.0).toFloat() * 0.25f).coerceIn(0f, 0.75f)
                _speechAmplitude.value = amp
                delay(40)
            }
            _speechAmplitude.value = 0f
        }
    }

    private fun stopAmplitudeSimulation() {
        amplitudeJob?.cancel()
        _speechAmplitude.value = 0f
    }

    fun stop() {
        speechEngine.stop()
        _isSpeaking.value = false
        stopAmplitudeSimulation()
    }

    fun shutdown() {
        stop()
        speechEngine.shutdown()
    }
}
