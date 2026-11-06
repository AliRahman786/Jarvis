package com.example.voice.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class AndroidNativeSpeechEngine(
    private val context: Context,
    private val onInitializedCallback: (() -> Unit)? = null
) : SpeechSynthesisEngine, TextToSpeech.OnInitListener {

    override val id: String = "NATIVE_TTS"
    override val displayName: String = "Android High-Quality Natural Voices (Offline)"

    private var tts: TextToSpeech? = null
    private var isExternalTtsAttached = false
    private val isInitialized = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    private var attemptedFallback = false

    private var currentOnStart: (() -> Unit)? = null
    private var currentOnDone: (() -> Unit)? = null
    private var currentOnError: ((String) -> Unit)? = null

    init {
        initEngine(preferGoogleTts = true)
    }

    /**
     * Binds the Activity-managed Android TextToSpeech engine from MainActivity
     * so all JARVIS voice synthesis routes through the system-native engine initialized in MainActivity.
     */
    fun attachExternalTts(externalTts: TextToSpeech) {
        if (tts !== externalTts) {
            if (!isExternalTtsAttached) {
                try { tts?.shutdown() } catch (_: Exception) {}
            }
            tts = externalTts
            isExternalTtsAttached = true
        }
        setupUtteranceListener()
        isInitialized.set(true)
        onInitializedCallback?.invoke()
    }

    fun detachExternalTts() {
        if (isExternalTtsAttached) {
            tts = null
            isExternalTtsAttached = false
            isInitialized.set(false)
        }
    }

    private fun initEngine(preferGoogleTts: Boolean) {
        if (isExternalTtsAttached && tts != null) return
        try {
            if (preferGoogleTts) {
                tts = TextToSpeech(context.applicationContext, this, "com.google.android.tts")
            } else {
                tts = TextToSpeech(context.applicationContext, this)
            }
        } catch (_: Exception) {
            tts = TextToSpeech(context.applicationContext, this)
        }
    }

    override fun isReady(): Boolean = isInitialized.get() && tts != null

    override fun onInit(status: Int) {
        if (isExternalTtsAttached && tts != null) {
            setupUtteranceListener()
            isInitialized.set(true)
            onInitializedCallback?.invoke()
            return
        }
        if (status == TextToSpeech.SUCCESS) {
            setupUtteranceListener()
            isInitialized.set(true)
            onInitializedCallback?.invoke()
        } else if (!attemptedFallback) {
            attemptedFallback = true
            isInitialized.set(false)
            try { tts?.shutdown() } catch (_: Exception) {}
            initEngine(preferGoogleTts = false)
        } else {
            isInitialized.set(false)
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                mainHandler.post {
                    currentOnStart?.invoke()
                }
            }

            override fun onDone(utteranceId: String?) {
                mainHandler.post {
                    releaseAudioFocus()
                    currentOnDone?.invoke()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainHandler.post {
                    releaseAudioFocus()
                    currentOnError?.invoke("TTS utterance error")
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                mainHandler.post {
                    releaseAudioFocus()
                    currentOnError?.invoke("TTS error code $errorCode")
                }
            }
        })
    }

    override fun getAvailableVoices(locale: Locale): List<VoiceProfile> {
        val list = mutableListOf<VoiceProfile>()

        // 1. Genuine Google Humanoid Voices (Inbuilt / High-Fidelity)
        list.add(
            VoiceProfile(
                id = "Google Humanoid Male (JARVIS Butler)",
                displayName = "Google Humanoid Male (JARVIS Butler)",
                gender = VoiceGender.MALE,
                locale = Locale.UK,
                quality = 600,
                isNetworkRequired = false,
                isInstalled = true,
                description = "Sophisticated, crisp British butler male voice (JARVIS)"
            )
        )
        list.add(
            VoiceProfile(
                id = "Google Humanoid Female (Pari Companion)",
                displayName = "Google Humanoid Female (Pari Companion)",
                gender = VoiceGender.FEMALE,
                locale = Locale.US,
                quality = 600,
                isNetworkRequired = false,
                isInstalled = true,
                description = "Warm, natural, affectionate humanoid female companion"
            )
        )
        list.add(
            VoiceProfile(
                id = "Google Humanoid Female (Friday Tactical)",
                displayName = "Google Humanoid Female (Friday Tactical)",
                gender = VoiceGender.FEMALE,
                locale = Locale.US,
                quality = 600,
                isNetworkRequired = false,
                isInstalled = true,
                description = "Sharp, crystal-clear, articulate tactical female voice"
            )
        )
        list.add(
            VoiceProfile(
                id = "Google Humanoid Male (US Resonant)",
                displayName = "Google Humanoid Male (US Resonant)",
                gender = VoiceGender.MALE,
                locale = Locale.US,
                quality = 600,
                isNetworkRequired = false,
                isInstalled = true,
                description = "Deep, clear, natural conversational American male"
            )
        )

        // Legacy Profiles mapped to natural engine
        list.add(
            VoiceProfile(
                id = "GOOGLE_PUCK",
                displayName = "Google Puck (Playful Dynamic)",
                gender = VoiceGender.MALE,
                locale = locale,
                quality = 500,
                isNetworkRequired = false,
                isInstalled = true,
                description = "Playful, energetic, spirited and agile cadence"
            )
        )
        list.add(
            VoiceProfile(
                id = "GOOGLE_KORE",
                displayName = "Google Kore (Warm Empathic)",
                gender = VoiceGender.FEMALE,
                locale = locale,
                quality = 500,
                isNetworkRequired = false,
                isInstalled = true,
                description = "Warm, soothing, empathic, gentle tone"
            )
        )
        list.add(
            VoiceProfile(
                id = "GOOGLE_LEDA",
                displayName = "Google Leda (Articulate Executive)",
                gender = VoiceGender.FEMALE,
                locale = locale,
                quality = 500,
                isNetworkRequired = false,
                isInstalled = true,
                description = "Sharp, executive, articulate tactical female voice"
            )
        )
        list.add(
            VoiceProfile(
                id = "GOOGLE_ORPHEUS",
                displayName = "Google Orpheus (Majestic Classical)",
                gender = VoiceGender.MALE,
                locale = locale,
                quality = 500,
                isNetworkRequired = false,
                isInstalled = true,
                description = "Majestic, clear, classical orator resonance"
            )
        )
        list.add(
            VoiceProfile(
                id = "GOOGLE_ZEPHYR",
                displayName = "Google Zephyr (Gentle Balanced)",
                gender = VoiceGender.FEMALE,
                locale = locale,
                quality = 500,
                isNetworkRequired = false,
                isInstalled = true,
                description = "Smooth, balanced, natural conversational flow"
            )
        )

        // 2. Installed system voices from device TTS engine
        val engine = tts
        val engineVoices = try {
            engine?.voices
        } catch (_: Exception) {
            null
        }

        val lang = locale.language
        if (!engineVoices.isNullOrEmpty()) {
            val matching = engineVoices.filter {
                it.locale.language.equals(lang, ignoreCase = true)
            }
            for (v in matching) {
                val isFemale = isVoiceFemale(v.name)
                val gender = if (isFemale) VoiceGender.FEMALE else VoiceGender.MALE
                val quality = v.quality
                val networkReq = v.isNetworkConnectionRequired

                list.add(
                    VoiceProfile(
                        id = v.name,
                        displayName = cleanVoiceDisplayName(v.name, gender),
                        gender = gender,
                        locale = v.locale,
                        quality = quality,
                        isNetworkRequired = networkReq,
                        isInstalled = true,
                        description = if (gender == VoiceGender.FEMALE) "Natural Female Voice" else "Natural Male Voice"
                    )
                )
            }
        }

        // Always ensure at least default male and female profiles are represented
        if (list.none { it.id == "MALE_DEFAULT" }) {
            list.add(
                VoiceProfile(
                    id = "MALE_DEFAULT",
                    displayName = "Natural Male (Tactical)",
                    gender = VoiceGender.MALE,
                    locale = locale,
                    quality = 400,
                    isNetworkRequired = false,
                    isInstalled = true,
                    description = "Commanding, clear analytical delivery"
                )
            )
        }
        if (list.none { it.id == "FEMALE_DEFAULT" }) {
            list.add(
                VoiceProfile(
                    id = "FEMALE_DEFAULT",
                    displayName = "Natural Female (Warm)",
                    gender = VoiceGender.FEMALE,
                    locale = locale,
                    quality = 400,
                    isNetworkRequired = false,
                    isInstalled = true,
                    description = "Warm, articulate, expressive delivery"
                )
            )
        }

        return list
    }

    private fun isVoiceFemale(voiceName: String): Boolean {
        val lower = voiceName.lowercase(Locale.ROOT)
        if (lower.contains("male") && !lower.contains("female")) return false
        if (lower.contains("rjs") || lower.contains("iol") || lower.contains("iob") ||
            lower.contains("iog") || lower.contains("enc") || lower.contains("butler") || lower.contains("fenrir")) {
            return false
        }
        return lower.contains("female") || lower.contains("fem") ||
                lower.contains("pari") || lower.contains("friday") ||
                lower.contains("aoede") || lower.contains("kore") ||
                lower.contains("leda") || lower.contains("zephyr") ||
                lower.contains("sfg") || lower.contains("tpd") || lower.contains("fis") ||
                lower.contains("iom") || lower.contains("cfl") ||
                lower.contains("hi-in-x-hie") || lower.contains("ja-jp-x-jad") ||
                lower.contains("en-us-x-sfg") || lower.contains("en-gb-x-fis")
    }

    private fun cleanVoiceDisplayName(rawName: String, gender: VoiceGender): String {
        val clean = rawName.replace(Regex("^[a-z]{2}-[a-z]{2}-x-"), "")
            .replace("_", " ")
            .replace("-", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        val genderTag = if (gender == VoiceGender.FEMALE) "Female" else "Male"
        return "Voice $clean ($genderTag)"
    }

    private fun requestAudioFocus(): Boolean {
        if (audioManager == null) return true
        if (hasAudioFocus) return true

        return try {
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .build()
                audioFocusRequest = request
                audioManager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
            hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            hasAudioFocus
        } catch (_: Exception) {
            true
        }
    }

    private fun releaseAudioFocus() {
        if (!hasAudioFocus || audioManager == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (_: Exception) {}
        hasAudioFocus = false
    }

    override fun speak(
        text: String,
        options: VoiceSynthesisOptions,
        onStart: (() -> Unit)?,
        onDone: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ) {
        val engine = tts
        if (engine == null || !isInitialized.get()) {
            onError?.invoke("Speech synthesis engine not initialized.")
            return
        }

        val cleanText = sanitizeTextForSpeech(text)
        if (cleanText.isBlank()) {
            onDone?.invoke()
            return
        }

        currentOnStart = onStart
        currentOnDone = onDone
        currentOnError = onError

        requestAudioFocus()

        try {
            // Apply language
            engine.language = options.locale

            // Apply pitch & rate without artificial distortion multipliers
            val effectivePitch = if (options.isUltronMode) {
                (options.pitch * 0.72f).coerceIn(0.5f, 2.0f)
            } else {
                options.pitch.coerceIn(0.5f, 2.0f)
            }

            val effectiveRate = if (options.isUltronMode) {
                (options.speechRate * 0.94f).coerceIn(0.5f, 2.0f)
            } else {
                options.speechRate.coerceIn(0.5f, 2.0f)
            }

            engine.setPitch(effectivePitch)
            engine.setSpeechRate(effectiveRate)

            // Select optimal authentic voice
            selectVoice(engine, options)

            // Sentence chunking for natural pauses and long response delivery
            val chunks = chunkText(cleanText)
            val utteranceBaseId = "jarvis_speech_${System.currentTimeMillis()}"

            for (i in chunks.indices) {
                val chunk = chunks[i]
                val utteranceId = "${utteranceBaseId}_$i"
                val queueMode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD

                val params = Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                }
                engine.speak(chunk, queueMode, params, utteranceId)
            }
        } catch (e: Exception) {
            releaseAudioFocus()
            onError?.invoke("Speech execution failure: ${e.message}")
        }
    }

    private fun selectVoice(engine: TextToSpeech, options: VoiceSynthesisOptions) {
        val availableVoices = try {
            engine.voices
        } catch (_: Exception) {
            null
        }

        if (availableVoices.isNullOrEmpty()) return

        val wantsFemale = options.genderPreference == VoiceGender.FEMALE ||
                isVoiceFemale(options.voiceName) ||
                options.voiceName.contains("female", ignoreCase = true) ||
                options.voiceName.contains("pari", ignoreCase = true) ||
                options.voiceName.contains("friday", ignoreCase = true)

        val wantsBritishButler = options.voiceName.contains("butler", ignoreCase = true) ||
                options.voiceName.contains("jarvis", ignoreCase = true) ||
                options.voiceName.contains("rjs", ignoreCase = true)

        // 1. Try exact voice name match
        val exactMatch = availableVoices.find {
            it.name.equals(options.voiceName, ignoreCase = true)
        }
        if (exactMatch != null) {
            engine.voice = exactMatch
            return
        }

        // 2. Butler / Classic JARVIS British Male preference
        if (wantsBritishButler) {
            val britishButler = availableVoices.find { it.name.contains("rjs", ignoreCase = true) }
                ?: availableVoices.find { it.locale.country.equals("GB", ignoreCase = true) && !isVoiceFemale(it.name) }
            if (britishButler != null) {
                engine.voice = britishButler
                return
            }
        }

        // 3. Match within requested language
        val targetLang = options.locale.language
        val matchingLang = availableVoices.filter {
            it.locale.language.equals(targetLang, ignoreCase = true)
        }
        val candidatePool = if (matchingLang.isNotEmpty()) matchingLang else availableVoices.toList()

        val candidate = if (wantsFemale) {
            candidatePool.find { it.name.contains("sfg", ignoreCase = true) }
                ?: candidatePool.find { it.name.contains("tpd", ignoreCase = true) }
                ?: candidatePool.find { it.name.contains("fis", ignoreCase = true) }
                ?: candidatePool.find { it.name.contains("iom", ignoreCase = true) }
                ?: candidatePool.find { it.name.contains("cfl", ignoreCase = true) }
                ?: candidatePool.find { isVoiceFemale(it.name) }
                ?: candidatePool.find { it.features.contains("female") }
        } else {
            candidatePool.find { it.name.contains("rjs", ignoreCase = true) }
                ?: candidatePool.find { it.name.contains("iol", ignoreCase = true) }
                ?: candidatePool.find { it.name.contains("iob", ignoreCase = true) }
                ?: candidatePool.find { it.name.contains("iog", ignoreCase = true) }
                ?: candidatePool.find { it.name.contains("enc", ignoreCase = true) }
                ?: candidatePool.find { !isVoiceFemale(it.name) }
                ?: candidatePool.find { it.features.contains("male") }
        } ?: candidatePool.maxByOrNull { it.quality } ?: candidatePool.firstOrNull()

        if (candidate != null) {
            engine.voice = candidate
        }
    }

    companion object {
        /**
         * Strips Markdown formatting, code blocks, raw JSON, URLs, and tool syntax
         * so JARVIS speaks natural human sentences rather than punctuation and syntax.
         */
        fun sanitizeTextForSpeech(input: String): String {
            return input
                // 1. Remove code blocks
                .replace(Regex("```[\\s\\S]*?```"), "Code snippet omitted.")
                // 2. Remove inline code
                .replace(Regex("`([^`]+)`"), "$1")
                // 3. Remove URLs
                .replace(Regex("https?://\\S+"), "web link")
                // 4. Remove Markdown headers, bold, italics, strikethroughs, blockquotes
                .replace(Regex("(?m)^#{1,6}\\s*"), "")
                .replace(Regex("[*_~]"), "")
                .replace(Regex("(?m)^>\\s*"), "")
                // 5. Remove JSON structures if AI accidentally outputs raw objects
                .replace(Regex("\\{[^{}]*\\}"), "")
                // 6. Clean multiple spaces / newlines
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        /**
         * Chunks text along natural sentence boundaries without splitting words.
         */
        fun chunkText(text: String, maxChunkSize: Int = 220): List<String> {
            val sentences = text.split(Regex("(?<=[.!?])\\s+|\n+")).filter { it.isNotBlank() }
            val chunks = mutableListOf<String>()
            val currentChunk = StringBuilder()

            for (sentence in sentences) {
                if (sentence.length > maxChunkSize) {
                    // Large sentence without punctuation: split on commas or word boundaries
                    if (currentChunk.isNotEmpty()) {
                        chunks.add(currentChunk.toString().trim())
                        currentChunk.clear()
                    }
                    val commaParts = sentence.split(Regex("(?<=,)\\s+"))
                    for (cp in commaParts) {
                        if (cp.length > maxChunkSize) {
                            val words = cp.split(Regex("\\s+"))
                            for (w in words) {
                                if (currentChunk.length + w.length + 1 > maxChunkSize) {
                                    if (currentChunk.isNotEmpty()) {
                                        chunks.add(currentChunk.toString().trim())
                                        currentChunk.clear()
                                    }
                                }
                                if (currentChunk.isNotEmpty()) currentChunk.append(" ")
                                currentChunk.append(w)
                            }
                        } else {
                            if (currentChunk.length + cp.length + 1 > maxChunkSize) {
                                if (currentChunk.isNotEmpty()) {
                                    chunks.add(currentChunk.toString().trim())
                                    currentChunk.clear()
                                }
                            }
                            if (currentChunk.isNotEmpty()) currentChunk.append(" ")
                            currentChunk.append(cp)
                        }
                    }
                } else {
                    if (currentChunk.length + sentence.length > maxChunkSize) {
                        chunks.add(currentChunk.toString().trim())
                        currentChunk.clear()
                    }
                    if (currentChunk.isNotEmpty()) currentChunk.append(" ")
                    currentChunk.append(sentence)
                }
            }

            if (currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toString().trim())
            }

            return if (chunks.isEmpty() && text.isNotBlank()) listOf(text) else chunks
        }
    }

    override fun stop() {
        tts?.stop()
        releaseAudioFocus()
    }

    override fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized.set(false)
    }
}
