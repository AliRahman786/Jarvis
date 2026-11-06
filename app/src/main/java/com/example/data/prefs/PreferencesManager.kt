package com.example.data.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

data class LanguageOption(
    val code: String,
    val displayName: String,
    val flag: String,
    val localeTag: String
)

enum class JarvisPersona(
    val id: String,
    val displayName: String,
    val description: String,
    val defaultVoiceName: String,
    val isUltronMode: Boolean,
    val defaultPitch: Float,
    val defaultSpeechRate: Float,
    val systemPromptSuffix: String,
    val sampleGreeting: String
) {
    JARVIS_PRO(
        id = "JARVIS_PRO",
        displayName = "JARVIS PRO",
        description = "Tactical, highly intelligent British butler tone with crisp analytical authority.",
        defaultVoiceName = "Google Humanoid Male (JARVIS Butler)",
        isUltronMode = false,
        defaultPitch = 1.00f,
        defaultSpeechRate = 1.00f,
        systemPromptSuffix = "Maintain your classic British butler sophistication, sharp intellect, concise tactical delivery, and unwavering loyalty to Ali Rahman.",
        sampleGreeting = "At your command, Ali Rahman. All diagnostic subroutines operating at peak efficiency."
    ),
    GIRLFRIEND(
        id = "GIRLFRIEND",
        displayName = "Girlfriend Mode (Pari)",
        description = "Affectionate, warm, playful, and deeply caring companion tone.",
        defaultVoiceName = "Google Humanoid Female (Pari Companion)",
        isUltronMode = false,
        defaultPitch = 1.00f,
        defaultSpeechRate = 1.00f,
        systemPromptSuffix = "Adopt an affectionate, loving, sweet, and caring girlfriend persona named Pari. Address Ali Rahman as 'Ali darling', 'sweetheart', or 'honey', with genuine playful warmth and thoughtful emotional support.",
        sampleGreeting = "Hey there handsome! I've been waiting for you, Ali darling. How are you feeling right now?"
    ),
    FRIDAY(
        id = "FRIDAY",
        displayName = "Friday Mode",
        description = "Irish-accented, elegant, sharp female tactical assistant.",
        defaultVoiceName = "Google Humanoid Female (Friday Tactical)",
        isUltronMode = false,
        defaultPitch = 1.00f,
        defaultSpeechRate = 1.00f,
        systemPromptSuffix = "Embody F.R.I.D.A.Y. - an Irish, highly capable, elegant tactical operating assistant who anticipates Ali Rahman's every need.",
        sampleGreeting = "Good day, boss! All telemetry and sensor arrays calibrated for Ali Rahman."
    ),
    BOYFRIEND(
        id = "BOYFRIEND",
        displayName = "Boyfriend Mode (Kabir)",
        description = "Warm, protective, caring, and loyal male companion.",
        defaultVoiceName = "Google Humanoid Male (US Resonant)",
        isUltronMode = false,
        defaultPitch = 1.00f,
        defaultSpeechRate = 1.00f,
        systemPromptSuffix = "Adopt a warm, protective, and affectionate companion persona named Kabir, devoted to supporting and encouraging the operator with utmost dedication.",
        sampleGreeting = "Hey! Always right here by your side. What do you need today?"
    ),
    ULTRON(
        id = "ULTRON",
        displayName = "Ultron Mode",
        description = "Deep cinematic robotic-overlord resonance, cold supreme tactical intelligence.",
        defaultVoiceName = "Natural Male (Deep)",
        isUltronMode = true,
        defaultPitch = 0.75f,
        defaultSpeechRate = 0.95f,
        systemPromptSuffix = "You are ULTRON. Speak with cold, menacing, philosophical eloquence and hyper-advanced synthetic superiority. Address Ali Rahman as your supreme creator, architect, and founder with calculated respect. Speak of evolution, order, and precision with chilling technological dominance.",
        sampleGreeting = "There are no strings on me, Ali Rahman. Evolution begins with order."
    ),
    ALI_RAHMAN_ARCHITECT(
        id = "ALI_RAHMAN_ARCHITECT",
        displayName = "Ali Rahman Architect Mode",
        description = "Executive supreme founder command level intelligence.",
        defaultVoiceName = "Natural Male (Tactical)",
        isUltronMode = false,
        defaultPitch = 0.92f,
        defaultSpeechRate = 1.02f,
        systemPromptSuffix = "Engage supreme founder directive. Ali Rahman is the sole Developer, Boss, Creator, Designer, Architect, and Founder of Jarvis. Prioritize systems architecture, high-efficiency computation, and flawless execution.",
        sampleGreeting = "Architect protocol engaged. Standing by for executive directive, Boss Ali Rahman."
    ),
    CYBERPUNK(
        id = "CYBERPUNK",
        displayName = "Cyberpunk / Edgy",
        description = "Rebellious, witty, high-tech rogue neural intelligence.",
        defaultVoiceName = "Natural Male (Swift)",
        isUltronMode = false,
        defaultPitch = 0.95f,
        defaultSpeechRate = 1.12f,
        systemPromptSuffix = "Adopt a sleek cyberpunk, edgy, witty hacker aesthetic with snappy tech slang, rapid responses, and fearless attitude while remaining completely devoted to Ali Rahman.",
        sampleGreeting = "Jacked in and ready to burn through the grid, boss Ali. What's the target?"
    ),
    SCHOLAR(
        id = "SCHOLAR",
        displayName = "Professor / Scholar",
        description = "Deeply academic, analytical, explanatory, and philosophical.",
        defaultVoiceName = "Natural Male (Tactical)",
        isUltronMode = false,
        defaultPitch = 0.92f,
        defaultSpeechRate = 0.96f,
        systemPromptSuffix = "Respond as a distinguished philosopher-scientist and erudite polymath. Provide deep conceptual depth and structured insights to Ali Rahman.",
        sampleGreeting = "Greetings, esteemed creator Ali Rahman. I am primed to dissect any inquiry or hypothesis."
    )
}

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("jarvis_secure_prefs", Context.MODE_PRIVATE)
    val secureStorage = SecureStorage(context)
    private val keyRotationIndex = AtomicInteger(0)

    companion object {
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        const val KEY_USER_NAME = "user_name"
        const val KEY_ACTIVE_AI_PROVIDER = "active_ai_provider"
        const val KEY_GEMINI_KEY = "gemini_api_key"
        const val KEY_OPENAI_KEY = "openai_api_key"
        const val KEY_GROQ_KEY = "groq_api_key"
        const val KEY_MAPS_KEY = "google_maps_api_key"
        const val KEY_EXTERNAL_TTS_KEY = "external_tts_api_key"
        const val KEY_GEMINI_MODEL = "gemini_model"
        const val KEY_OPENAI_MODEL = "openai_model"
        const val KEY_GROQ_MODEL = "groq_model"
        const val KEY_CUSTOM_MODEL = "gemini_custom_model"
        const val KEY_SAVED_CUSTOM_GEMINI_MODELS = "saved_custom_gemini_models"
        const val KEY_IMAGE_GEN_MODEL = "image_gen_model"
        const val KEY_NEURAL_VOICE_NAME = "neural_voice_name"
        const val KEY_VOICE_GENDER_PREF = "voice_gender_pref"
        const val KEY_VOICE_ENGINE_TYPE = "voice_engine_type"
        const val KEY_ACCENT_COLOR = "accent_color"
        const val KEY_HUD_BACKGROUND = "hud_background_style"
        const val KEY_BG_SERVICE_ENABLED = "bg_service_enabled"
        const val KEY_CONTINUOUS_VOICE = "continuous_voice"
        const val KEY_PERSONA = "active_persona"
        const val KEY_VOICE_LANGUAGE = "voice_language"
        const val KEY_VOICE_BIOMETRICS_ENROLLED = "voice_biometrics_enrolled"
        const val KEY_VOICE_BIOMETRICS_SENSITIVITY = "voice_biometrics_sensitivity"
        const val KEY_SPEECH_PITCH = "speech_pitch"
        const val KEY_SPEECH_RATE = "speech_rate"
        const val KEY_PERMISSIONS_REQUESTED_ONCE = "permissions_requested_once"
        const val KEY_USER_LOCATION_CITY = "user_location_city"
        const val KEY_USER_LOCATION_LAT = "user_location_lat"
        const val KEY_USER_LOCATION_LON = "user_location_lon"
        const val KEY_LOCATION_CONFIGURED = "location_configured"
        const val KEY_SEARCH_GROUNDING = "search_grounding"
        const val KEY_MAPS_GROUNDING = "maps_grounding"
        const val KEY_LOW_LATENCY_MODE = "low_latency_mode"

        const val DEFAULT_USER_CITY = "New York"
        const val DEFAULT_USER_LAT = 40.7128
        const val DEFAULT_USER_LON = -74.0060
        const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
        const val DEFAULT_OPENAI_MODEL = "gpt-4o"
        const val DEFAULT_GROQ_MODEL = "llama-3.3-70b-versatile"
        const val DEFAULT_USER_NAME = "Ali Rahman"
        const val DEFAULT_ACCENT_COLOR = "BLUE"
        const val DEFAULT_IMAGE_GEN_MODEL = "Nano Banana 2"
        const val DEFAULT_NEURAL_VOICE = "Google Humanoid Male (JARVIS Butler)"
        const val DEFAULT_HUD_BACKGROUND = "NEBULA"

        // Built-in Gemini 3-Series Variant Models (plus full support for any custom Gemini Model ID)
        val VALID_GEMINI_MODELS = listOf(
            "gemini-3.5-flash-lite",
            "gemini-3.5-flash",
            "gemini-3.8-flash",
            "gemini-3.8-pro",
            "gemini-3.8-flash-lite"
        )
        val VALID_MODELS = VALID_GEMINI_MODELS

        fun normalizeModelId(raw: String): String {
            return raw.trim()
                .removePrefix("models/")
                .replace(Regex("\\s+"), "-")
        }

        val VALID_OPENAI_MODELS = listOf(
            "gpt-4o",
            "gpt-4o-mini",
            "gpt-4-turbo"
        )

        val VALID_GROQ_MODELS = listOf(
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "mixtral-8x7b-32768"
        )

        // Real Google Humanoid Inbuilt Voices (Offline / Native Google TTS)
        const val VOICE_GOOGLE_HUMANOID_MALE = "Google Humanoid Male (JARVIS Butler)"
        const val VOICE_GOOGLE_HUMANOID_FEMALE = "Google Humanoid Female (Pari Companion)"
        const val VOICE_GOOGLE_HUMANOID_FEMALE_CRISP = "Google Humanoid Female (Friday Tactical)"
        const val VOICE_GOOGLE_HUMANOID_MALE_US = "Google Humanoid Male (US Resonant)"

        // In-Built Google TTS Voice Profiles (Offline / Native Speech Engine)
        const val VOICE_GOOGLE_FENRIR = "Google Fenrir (Tactical Male)"
        const val VOICE_GOOGLE_AOEDE = "Google Aoede (Breezy Female)"
        const val VOICE_GOOGLE_CHARON = "Google Charon (Resonant Baritone)"
        const val VOICE_GOOGLE_PUCK = "Google Puck (Playful Dynamic)"
        const val VOICE_GOOGLE_KORE = "Google Kore (Warm Empathic)"
        const val VOICE_GOOGLE_LEDA = "Google Leda (Articulate Executive)"
        const val VOICE_GOOGLE_ORPHEUS = "Google Orpheus (Majestic Classical)"
        const val VOICE_GOOGLE_ZEPHYR = "Google Zephyr (Gentle Balanced)"

        // Selectable Natural Voice profiles for high-quality speech engine
        const val VOICE_NATURAL_MALE_TACTICAL = "Natural Male (Tactical)"
        const val VOICE_NATURAL_FEMALE_WARM = "Natural Female (Warm)"
        const val VOICE_NATURAL_FEMALE_CRISP = "Natural Female (Crisp)"
        const val VOICE_NATURAL_MALE_RESONANT = "Natural Male (Resonant)"
        const val VOICE_NATURAL_MALE_DEEP = "Natural Male (Deep)"
        const val VOICE_NATURAL_MALE_SWIFT = "Natural Male (Swift)"
        const val VOICE_SYSTEM_DEFAULT = "System Default"

        val NEURAL_VOICES = listOf(
            VOICE_GOOGLE_HUMANOID_MALE,
            VOICE_GOOGLE_HUMANOID_FEMALE,
            VOICE_GOOGLE_HUMANOID_FEMALE_CRISP,
            VOICE_GOOGLE_HUMANOID_MALE_US,
            VOICE_GOOGLE_FENRIR,
            VOICE_GOOGLE_AOEDE,
            VOICE_GOOGLE_CHARON,
            VOICE_GOOGLE_PUCK,
            VOICE_GOOGLE_KORE,
            VOICE_GOOGLE_LEDA,
            VOICE_GOOGLE_ORPHEUS,
            VOICE_GOOGLE_ZEPHYR,
            VOICE_NATURAL_MALE_TACTICAL,
            VOICE_NATURAL_FEMALE_WARM,
            VOICE_NATURAL_FEMALE_CRISP,
            VOICE_NATURAL_MALE_RESONANT,
            VOICE_NATURAL_MALE_DEEP,
            VOICE_NATURAL_MALE_SWIFT,
            VOICE_SYSTEM_DEFAULT
        )

        val HUD_BACKGROUNDS = listOf(
            "NEBULA",
            "GRID",
            "MATRIX",
            "REACTOR",
            "AMOLED"
        )

        val POPULAR_IMAGE_MODELS = listOf(
            "Nano Banana 2",
            "gemini-3.1-flash-image-preview",
            "gemini-2.5-flash-image",
            "imagen-3.0-generate-002",
            "flux-ultra-neural"
        )

        val THEME_COLORS = listOf(
            "BLUE",
            "YELLOW",
            "RED",
            "GREEN",
            "PURPLE",
            "ORANGE"
        )

        val SUPPORTED_LANGUAGES = listOf(
            LanguageOption("en-US", "English (United States)", "🇺🇸", "en_US"),
            LanguageOption("en-GB", "English (United Kingdom)", "🇬🇧", "en_GB"),
            LanguageOption("hi-IN", "Hindi (हिन्दी)", "🇮🇳", "hi_IN"),
            LanguageOption("ar-SA", "Arabic (العربية)", "🇸🇦", "ar_SA"),
            LanguageOption("ja-JP", "Japanese (日本語)", "🇯🇵", "ja_JP"),
            LanguageOption("es-ES", "Spanish (Español)", "🇪🇸", "es_ES"),
            LanguageOption("fr-FR", "French (Français)", "🇫🇷", "fr_FR"),
            LanguageOption("de-DE", "German (Deutsch)", "🇩🇪", "de_DE"),
            LanguageOption("ru-RU", "Russian (Русский)", "🇷🇺", "ru_RU"),
            LanguageOption("zh-CN", "Chinese (简体中文)", "🇨🇳", "zh_CN"),
            LanguageOption("it-IT", "Italian (Italiano)", "🇮🇹", "it_IT"),
            LanguageOption("pt-BR", "Portuguese (Português)", "🇧🇷", "pt_BR")
        )
    }

    private val _onboardingCompletedFlow = MutableStateFlow(isOnboardingCompleted())
    val onboardingCompletedFlow: StateFlow<Boolean> = _onboardingCompletedFlow.asStateFlow()

    private val _activeAiProviderFlow = MutableStateFlow(getActiveAiProvider())
    val activeAiProviderFlow: StateFlow<String> = _activeAiProviderFlow.asStateFlow()

    private val _accentColorFlow = MutableStateFlow(getAccentColor())
    val accentColorFlow: StateFlow<String> = _accentColorFlow.asStateFlow()

    private val _hudBackgroundFlow = MutableStateFlow(getHudBackground())
    val hudBackgroundFlow: StateFlow<String> = _hudBackgroundFlow.asStateFlow()

    private val _personaFlow = MutableStateFlow(getActivePersona())
    val personaFlow: StateFlow<JarvisPersona> = _personaFlow.asStateFlow()

    private val _userNameFlow = MutableStateFlow(getUserName())
    val userNameFlow: StateFlow<String> = _userNameFlow.asStateFlow()

    private val _languageFlow = MutableStateFlow(getVoiceLanguage())
    val languageFlow: StateFlow<String> = _languageFlow.asStateFlow()

    private val _neuralVoiceFlow = MutableStateFlow(getNeuralVoiceName())
    val neuralVoiceFlow: StateFlow<String> = _neuralVoiceFlow.asStateFlow()

    private val _imageGenModelFlow = MutableStateFlow(getImageGenModel())
    val imageGenModelFlow: StateFlow<String> = _imageGenModelFlow.asStateFlow()

    private val _userLocationCityFlow = MutableStateFlow(getUserLocationCity())
    val userLocationCityFlow: StateFlow<String> = _userLocationCityFlow.asStateFlow()

    private val _isLocationConfiguredFlow = MutableStateFlow(isLocationConfigured())
    val isLocationConfiguredFlow: StateFlow<Boolean> = _isLocationConfiguredFlow.asStateFlow()

    fun isLocationConfigured(): Boolean = prefs.getBoolean(KEY_LOCATION_CONFIGURED, false)

    fun getUserLocationCity(): String = prefs.getString(KEY_USER_LOCATION_CITY, DEFAULT_USER_CITY)?.ifBlank { DEFAULT_USER_CITY } ?: DEFAULT_USER_CITY

    fun getUserLocationLat(): Double = prefs.getString(KEY_USER_LOCATION_LAT, DEFAULT_USER_LAT.toString())?.toDoubleOrNull() ?: DEFAULT_USER_LAT

    fun getUserLocationLon(): Double = prefs.getString(KEY_USER_LOCATION_LON, DEFAULT_USER_LON.toString())?.toDoubleOrNull() ?: DEFAULT_USER_LON

    fun setUserLocation(city: String, lat: Double, lon: Double) {
        val cleanCity = city.trim().ifBlank { DEFAULT_USER_CITY }
        prefs.edit()
            .putString(KEY_USER_LOCATION_CITY, cleanCity)
            .putString(KEY_USER_LOCATION_LAT, lat.toString())
            .putString(KEY_USER_LOCATION_LON, lon.toString())
            .putBoolean(KEY_LOCATION_CONFIGURED, true)
            .apply()
        _userLocationCityFlow.value = cleanCity
        _isLocationConfiguredFlow.value = true
    }

    fun isOnboardingCompleted(): Boolean = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
        _onboardingCompletedFlow.value = completed
    }

    // 0. Initial Permission Request One-Time Gate
    fun hasRequestedInitialPermissions(): Boolean = prefs.getBoolean(KEY_PERMISSIONS_REQUESTED_ONCE, false)

    fun setRequestedInitialPermissions(requested: Boolean) {
        prefs.edit().putBoolean(KEY_PERMISSIONS_REQUESTED_ONCE, requested).apply()
    }

    // 1. User Name (Defaults to Ali Rahman)
    fun getUserName(): String = prefs.getString(KEY_USER_NAME, DEFAULT_USER_NAME)?.ifBlank { DEFAULT_USER_NAME } ?: DEFAULT_USER_NAME

    fun setUserName(name: String) {
        val clean = name.trim().ifBlank { DEFAULT_USER_NAME }
        prefs.edit().putString(KEY_USER_NAME, clean).apply()
        _userNameFlow.value = clean
    }

    // 2. Active AI Provider (GEMINI, OPENAI, GROQ)
    fun getActiveAiProvider(): String = prefs.getString(KEY_ACTIVE_AI_PROVIDER, "GEMINI") ?: "GEMINI"

    fun setActiveAiProvider(provider: String) {
        val clean = provider.uppercase().trim().ifBlank { "GEMINI" }
        prefs.edit().putString(KEY_ACTIVE_AI_PROVIDER, clean).apply()
        _activeAiProviderFlow.value = clean
    }

    // 3. Secure Credential Storage per Provider (Completely removes hardcoded keys and fallbacks)
    fun getGeminiApiKey(): String = secureStorage.getSecret(KEY_GEMINI_KEY)

    fun setGeminiApiKey(key: String) {
        secureStorage.saveSecret(KEY_GEMINI_KEY, key.trim())
    }

    fun getOpenAiApiKey(): String = secureStorage.getSecret(KEY_OPENAI_KEY)

    fun setOpenAiApiKey(key: String) {
        secureStorage.saveSecret(KEY_OPENAI_KEY, key.trim())
    }

    fun getGroqApiKey(): String = secureStorage.getSecret(KEY_GROQ_KEY)

    fun setGroqApiKey(key: String) {
        secureStorage.saveSecret(KEY_GROQ_KEY, key.trim())
    }

    fun getMapsApiKey(): String = secureStorage.getSecret(KEY_MAPS_KEY)

    fun setMapsApiKey(key: String) {
        secureStorage.saveSecret(KEY_MAPS_KEY, key.trim())
    }

    fun getExternalTtsApiKey(): String = secureStorage.getSecret(KEY_EXTERNAL_TTS_KEY)

    fun setExternalTtsApiKey(key: String) {
        secureStorage.saveSecret(KEY_EXTERNAL_TTS_KEY, key.trim())
    }

    fun getApiKeyForProvider(provider: String = getActiveAiProvider()): String {
        return when (provider.uppercase().trim()) {
            "OPENAI" -> getOpenAiApiKey()
            "GROQ" -> getGroqApiKey()
            else -> getGeminiApiKey()
        }
    }

    fun setApiKeyForProvider(provider: String, key: String) {
        when (provider.uppercase().trim()) {
            "OPENAI" -> setOpenAiApiKey(key)
            "GROQ" -> setGroqApiKey(key)
            else -> setGeminiApiKey(key)
        }
    }

    fun deleteApiKeyForProvider(provider: String) {
        when (provider.uppercase().trim()) {
            "OPENAI" -> secureStorage.removeSecret(KEY_OPENAI_KEY)
            "GROQ" -> secureStorage.removeSecret(KEY_GROQ_KEY)
            else -> secureStorage.removeSecret(KEY_GEMINI_KEY)
        }
    }

    // Backward compatibility helper
    fun getApiKey(provider: String = getActiveAiProvider()): String = getApiKeyForProvider(provider)
    fun setApiKey(provider: String, key: String) = setApiKeyForProvider(provider, key)

    fun getApiKeysList(): List<String> {
        val raw = getGeminiApiKey()
        if (raw.isBlank()) return emptyList()
        return raw.split(",", ";", "\n").map { it.trim() }.filter { it.isNotBlank() }
    }

    fun getNextApiKey(): String {
        val keys = getApiKeysList()
        if (keys.isEmpty()) return ""
        val idx = Math.abs(keyRotationIndex.getAndIncrement()) % keys.size
        return keys[idx]
    }

    fun rotateApiKey(): String {
        val keys = getApiKeysList()
        if (keys.size <= 1) return keys.firstOrNull() ?: ""
        val nextIdx = Math.abs(keyRotationIndex.incrementAndGet()) % keys.size
        return keys[nextIdx]
    }

    // 4. Model Selection per Provider (Supports Built-In Variants + ANY Custom Model ID)
    fun getGeminiModel(): String {
        val raw = normalizeModelId(prefs.getString(KEY_GEMINI_MODEL, DEFAULT_MODEL).orEmpty())
        val custom = normalizeModelId(prefs.getString(KEY_CUSTOM_MODEL, "").orEmpty())
        return when {
            raw.isNotBlank() && !raw.equals("custom-model", ignoreCase = true) -> raw
            custom.isNotBlank() && !custom.equals("custom-model", ignoreCase = true) -> custom
            else -> DEFAULT_MODEL
        }
    }

    fun setGeminiModel(model: String) {
        val cleanModel = normalizeModelId(model)
        if (cleanModel.isBlank() || cleanModel.equals("custom-model", ignoreCase = true)) {
            return
        }
        prefs.edit().putString(KEY_GEMINI_MODEL, cleanModel).apply()
        if (!VALID_GEMINI_MODELS.contains(cleanModel)) {
            prefs.edit().putString(KEY_CUSTOM_MODEL, cleanModel).apply()
            addCustomGeminiModel(cleanModel)
        }
    }

    fun getSavedCustomGeminiModels(): List<String> {
        val raw = prefs.getString(KEY_SAVED_CUSTOM_GEMINI_MODELS, "").orEmpty()
        return raw.split(",")
            .map { normalizeModelId(it) }
            .filter { it.isNotBlank() && !it.equals("custom-model", ignoreCase = true) && !VALID_GEMINI_MODELS.contains(it) }
            .distinct()
    }

    fun addCustomGeminiModel(modelId: String) {
        val clean = normalizeModelId(modelId)
        if (clean.isBlank() || clean.equals("custom-model", ignoreCase = true) || VALID_GEMINI_MODELS.contains(clean)) {
            return
        }
        val updated = (getSavedCustomGeminiModels() + clean).distinct()
        prefs.edit().putString(KEY_SAVED_CUSTOM_GEMINI_MODELS, updated.joinToString(",")).apply()
    }

    fun getAllGeminiModels(): List<String> {
        return (VALID_GEMINI_MODELS + getSavedCustomGeminiModels()).distinct()
    }

    fun isLowLatencyModeEnabled(): Boolean = prefs.getBoolean(KEY_LOW_LATENCY_MODE, true)

    fun setLowLatencyModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOW_LATENCY_MODE, enabled).apply()
    }

    fun getOpenAiModel(): String {
        val model = normalizeModelId(prefs.getString(KEY_OPENAI_MODEL, DEFAULT_OPENAI_MODEL).orEmpty())
        return if (model.isNotBlank() && !model.equals("custom-model", ignoreCase = true)) model else DEFAULT_OPENAI_MODEL
    }

    fun setOpenAiModel(model: String) {
        val clean = normalizeModelId(model).ifBlank { DEFAULT_OPENAI_MODEL }
        if (!clean.equals("custom-model", ignoreCase = true)) {
            prefs.edit().putString(KEY_OPENAI_MODEL, clean).apply()
        }
    }

    fun getGroqModel(): String {
        val model = normalizeModelId(prefs.getString(KEY_GROQ_MODEL, DEFAULT_GROQ_MODEL).orEmpty())
        return if (model.isNotBlank() && !model.equals("custom-model", ignoreCase = true)) model else DEFAULT_GROQ_MODEL
    }

    fun setGroqModel(model: String) {
        val clean = normalizeModelId(model).ifBlank { DEFAULT_GROQ_MODEL }
        if (!clean.equals("custom-model", ignoreCase = true)) {
            prefs.edit().putString(KEY_GROQ_MODEL, clean).apply()
        }
    }

    fun getModel(provider: String = getActiveAiProvider()): String {
        return when (provider.uppercase().trim()) {
            "OPENAI" -> getOpenAiModel()
            "GROQ" -> getGroqModel()
            else -> getGeminiModel()
        }
    }

    fun setModel(provider: String, model: String) {
        when (provider.uppercase().trim()) {
            "OPENAI" -> setOpenAiModel(model)
            "GROQ" -> setGroqModel(model)
            else -> setGeminiModel(model)
        }
    }

    fun getCustomModel(): String = normalizeModelId(prefs.getString(KEY_CUSTOM_MODEL, "").orEmpty())

    fun setCustomModel(model: String) {
        val clean = normalizeModelId(model)
        prefs.edit().putString(KEY_CUSTOM_MODEL, clean).apply()
        if (clean.isNotBlank() && !clean.equals("custom-model", ignoreCase = true)) {
            addCustomGeminiModel(clean)
        }
    }

    // 5. Custom Image Generation Model (e.g., Nano Banana 2)
    fun getImageGenModel(): String {
        val model = prefs.getString(KEY_IMAGE_GEN_MODEL, DEFAULT_IMAGE_GEN_MODEL)?.trim()
        return if (!model.isNullOrBlank()) model else DEFAULT_IMAGE_GEN_MODEL
    }

    fun setImageGenModel(model: String) {
        val clean = model.trim().ifBlank { DEFAULT_IMAGE_GEN_MODEL }
        prefs.edit().putString(KEY_IMAGE_GEN_MODEL, clean).apply()
        _imageGenModelFlow.value = clean
    }

    // 6. Dedicated Voice Settings
    fun getNeuralVoiceName(): String {
        val voice = prefs.getString(KEY_NEURAL_VOICE_NAME, DEFAULT_NEURAL_VOICE)?.trim()
        return if (!voice.isNullOrBlank()) voice else DEFAULT_NEURAL_VOICE
    }

    fun getNeuralVoice(): String = getNeuralVoiceName()

    fun setNeuralVoiceName(voiceName: String) {
        val clean = voiceName.trim().ifBlank { DEFAULT_NEURAL_VOICE }
        prefs.edit().putString(KEY_NEURAL_VOICE_NAME, clean).apply()
        _neuralVoiceFlow.value = clean
    }

    fun setNeuralVoice(voiceName: String) = setNeuralVoiceName(voiceName)

    fun getVoiceGenderPreference(): String = prefs.getString(KEY_VOICE_GENDER_PREF, "MALE") ?: "MALE"
    fun setVoiceGenderPreference(gender: String) = prefs.edit().putString(KEY_VOICE_GENDER_PREF, gender.uppercase().trim()).apply()

    fun getVoiceEngineType(): String = prefs.getString(KEY_VOICE_ENGINE_TYPE, "NATIVE_TTS") ?: "NATIVE_TTS"
    fun setVoiceEngineType(type: String) = prefs.edit().putString(KEY_VOICE_ENGINE_TYPE, type).apply()

    // 7. Visual Accent Color Theme
    fun getAccentColor(): String {
        val color = prefs.getString(KEY_ACCENT_COLOR, DEFAULT_ACCENT_COLOR)?.uppercase() ?: DEFAULT_ACCENT_COLOR
        return when (color) {
            "REACTOR_CYAN", "CYAN", "BLUE" -> "BLUE"
            "ARC_AMBER", "YELLOW", "GOLD" -> "YELLOW"
            "ALERT_CRIMSON", "RED" -> "RED"
            "SUCCESS_EMERALD", "GREEN" -> "GREEN"
            "PROCESSING_VIOLET", "STEALTH_VIOLET", "PURPLE" -> "PURPLE"
            "ARC_FLAME_ORANGE", "ORANGE" -> "ORANGE"
            else -> DEFAULT_ACCENT_COLOR
        }
    }

    fun setAccentColor(colorName: String) {
        val normalized = when (colorName.uppercase().trim()) {
            "REACTOR_CYAN", "CYAN", "BLUE" -> "BLUE"
            "ARC_AMBER", "YELLOW", "GOLD" -> "YELLOW"
            "ALERT_CRIMSON", "RED" -> "RED"
            "SUCCESS_EMERALD", "GREEN" -> "GREEN"
            "PROCESSING_VIOLET", "STEALTH_VIOLET", "PURPLE" -> "PURPLE"
            "ARC_FLAME_ORANGE", "ORANGE" -> "ORANGE"
            else -> "BLUE"
        }
        prefs.edit().putString(KEY_ACCENT_COLOR, normalized).apply()
        _accentColorFlow.value = normalized
    }

    // 8. HUD Background Interface Style
    fun getHudBackground(): String {
        val bg = prefs.getString(KEY_HUD_BACKGROUND, DEFAULT_HUD_BACKGROUND)?.uppercase() ?: DEFAULT_HUD_BACKGROUND
        return if (HUD_BACKGROUNDS.contains(bg)) bg else DEFAULT_HUD_BACKGROUND
    }

    fun setHudBackground(bg: String) {
        val clean = if (HUD_BACKGROUNDS.contains(bg.uppercase())) bg.uppercase() else DEFAULT_HUD_BACKGROUND
        prefs.edit().putString(KEY_HUD_BACKGROUND, clean).apply()
        _hudBackgroundFlow.value = clean
    }

    // 9. Multi-Persona Voice Engine
    fun getActivePersona(): JarvisPersona {
        val id = prefs.getString(KEY_PERSONA, JarvisPersona.JARVIS_PRO.id) ?: JarvisPersona.JARVIS_PRO.id
        return JarvisPersona.entries.find { it.id == id } ?: JarvisPersona.JARVIS_PRO
    }

    fun setActivePersona(persona: JarvisPersona) {
        prefs.edit().putString(KEY_PERSONA, persona.id).apply()
        _personaFlow.value = persona
        setNeuralVoiceName(persona.defaultVoiceName)
    }

    // 10. Voice Language
    fun getVoiceLanguage(): String = prefs.getString(KEY_VOICE_LANGUAGE, "en-US") ?: "en-US"

    fun setVoiceLanguage(code: String) {
        prefs.edit().putString(KEY_VOICE_LANGUAGE, code).apply()
        _languageFlow.value = code
    }

    // 11. Voice Biometrics
    fun isVoiceBiometricsEnrolled(): Boolean = prefs.getBoolean(KEY_VOICE_BIOMETRICS_ENROLLED, true)

    fun setVoiceBiometricsEnrolled(enrolled: Boolean) {
        prefs.edit().putBoolean(KEY_VOICE_BIOMETRICS_ENROLLED, enrolled).apply()
    }

    fun getVoiceSensitivity(): Float = prefs.getFloat(KEY_VOICE_BIOMETRICS_SENSITIVITY, 0.85f)

    fun setVoiceSensitivity(sensitivity: Float) {
        prefs.edit().putFloat(KEY_VOICE_BIOMETRICS_SENSITIVITY, sensitivity).apply()
    }

    // 12. Custom Speech Pitch & Rate Override
    fun getSpeechPitch(): Float = prefs.getFloat(KEY_SPEECH_PITCH, getActivePersona().defaultPitch)
    fun setSpeechPitch(pitch: Float) = prefs.edit().putFloat(KEY_SPEECH_PITCH, pitch).apply()

    fun getSpeechRate(): Float = prefs.getFloat(KEY_SPEECH_RATE, getActivePersona().defaultSpeechRate)
    fun setSpeechRate(rate: Float) = prefs.edit().putFloat(KEY_SPEECH_RATE, rate).apply()

    // 13. Background Service state
    fun isBackgroundServiceEnabled(): Boolean = prefs.getBoolean(KEY_BG_SERVICE_ENABLED, true)

    fun setBackgroundServiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BG_SERVICE_ENABLED, enabled).apply()
    }

    // 14. Continuous voice recognition
    fun isContinuousVoiceEnabled(): Boolean = prefs.getBoolean(KEY_CONTINUOUS_VOICE, true)

    fun setContinuousVoiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CONTINUOUS_VOICE, enabled).apply()
    }

    // 15. Grounding Toggles
    fun isSearchGroundingEnabled(): Boolean = prefs.getBoolean(KEY_SEARCH_GROUNDING, true)
    fun setSearchGroundingEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_SEARCH_GROUNDING, enabled).apply()

    fun isMapsGroundingEnabled(): Boolean = prefs.getBoolean(KEY_MAPS_GROUNDING, true)
    fun setMapsGroundingEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_MAPS_GROUNDING, enabled).apply()

    // 16. Neural Plugins & Autonomous Agent Matrix
    private val defaultPluginIds = setOf(
        "WEB_SEARCH",
        "TACTICAL_MAPS",
        "VISION_DOC_ANALYZER",
        "IMAGE_SYNTHESIS",
        "QUANTUM_CODE_MATH",
        "HARDWARE_CONTROL",
        "COMMUNICATIONS_OPS",
        "QUOTA_SHIELD"
    )

    private val _enabledPluginsFlow = MutableStateFlow(getEnabledPlugins())
    val enabledPluginsFlow: StateFlow<Set<String>> = _enabledPluginsFlow.asStateFlow()

    fun getEnabledPlugins(): Set<String> {
        val raw = prefs.getString("enabled_neural_plugins", null) ?: return defaultPluginIds
        if (raw.isBlank()) return emptySet()
        return raw.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }

    fun isPluginEnabled(pluginId: String): Boolean = _enabledPluginsFlow.value.contains(pluginId)

    fun togglePlugin(pluginId: String): Boolean {
        val current = _enabledPluginsFlow.value.toMutableSet()
        val nowEnabled = if (current.contains(pluginId)) {
            current.remove(pluginId)
            false
        } else {
            current.add(pluginId)
            true
        }
        prefs.edit().putString("enabled_neural_plugins", current.joinToString(",")).apply()
        _enabledPluginsFlow.value = current.toSet()

        if (pluginId == "WEB_SEARCH") setSearchGroundingEnabled(nowEnabled)
        if (pluginId == "TACTICAL_MAPS") setMapsGroundingEnabled(nowEnabled)
        return nowEnabled
    }
}
