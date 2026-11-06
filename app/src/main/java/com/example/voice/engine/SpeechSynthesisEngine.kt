package com.example.voice.engine

import java.util.Locale

enum class VoiceGender {
    MALE,
    FEMALE,
    NEUTRAL
}

data class VoiceProfile(
    val id: String,
    val displayName: String,
    val gender: VoiceGender,
    val locale: Locale,
    val quality: Int = 300, // Android Voice.QUALITY_NORMAL / HIGH
    val isNetworkRequired: Boolean = false,
    val isInstalled: Boolean = true,
    val description: String = ""
)

data class VoiceSynthesisOptions(
    val pitch: Float = 1.0f,
    val speechRate: Float = 1.0f,
    val voiceName: String = "",
    val genderPreference: VoiceGender = VoiceGender.MALE,
    val locale: Locale = Locale.US,
    val isUltronMode: Boolean = false
)

interface SpeechSynthesisEngine {
    val id: String
    val displayName: String

    fun isReady(): Boolean

    fun getAvailableVoices(locale: Locale): List<VoiceProfile>

    fun speak(
        text: String,
        options: VoiceSynthesisOptions,
        onStart: (() -> Unit)? = null,
        onDone: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    )

    fun stop()

    fun shutdown()
}
