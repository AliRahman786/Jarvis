package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.service.JarvisBackgroundService
import com.example.ui.screens.MainHudScreen
import com.example.ui.screens.SetupWizardScreen
import com.example.ui.theme.DeepSpaceBlack
import com.example.ui.theme.JarvisTheme
import com.example.viewmodel.JarvisViewModel
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var textToSpeech: TextToSpeech? = null
    private var attemptedDefaultTtsFallback = false
    private var isTtsReady by mutableStateOf(false)
    private var boundViewModel: JarvisViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initializeSystemTextToSpeech(preferGoogleEngine = true)

        setContent {
            val viewModel: JarvisViewModel = viewModel()
            val isOnboardingCompleted by viewModel.prefs.onboardingCompletedFlow.collectAsState()
            val activePanel by viewModel.activePanel.collectAsState()
            val activePersona by viewModel.prefs.personaFlow.collectAsState()
            val selectedVoiceName by viewModel.prefs.neuralVoiceFlow.collectAsState()
            val selectedLangCode by viewModel.prefs.languageFlow.collectAsState()
            val accentColor = viewModel.getAccentColor()

            // Bind MainActivity's system-native TextToSpeech engine to JarvisViewModel
            // and keep humanoid male/female voice selection synced with active persona & settings.
            LaunchedEffect(viewModel, isTtsReady, activePersona, selectedVoiceName, selectedLangCode) {
                boundViewModel = viewModel
                val engine = textToSpeech
                if (isTtsReady && engine != null) {
                    val isFemale = activePersona.name == "GIRLFRIEND" ||
                            activePersona.name == "FRIDAY" ||
                            isFemaleVoiceIdentifier(selectedVoiceName)
                    val targetLocale = try {
                        Locale.forLanguageTag(selectedLangCode)
                    } catch (_: Exception) {
                        Locale.US
                    }
                    configureHumanoidVoice(
                        tts = engine,
                        isFemale = isFemale,
                        locale = targetLocale,
                        preferredVoiceName = selectedVoiceName,
                        pitch = activePersona.defaultPitch,
                        speechRate = activePersona.defaultSpeechRate
                    )
                    viewModel.bindActivityTextToSpeech(engine)
                }
            }

            // Request runtime permissions on launch without blocking dialog race conditions
            val permissionsLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { results ->
                val micGranted = results[Manifest.permission.RECORD_AUDIO] == true ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (micGranted && viewModel.prefs.isBackgroundServiceEnabled()) {
                    try {
                        JarvisBackgroundService.start(this)
                    } catch (_: Exception) {
                    }
                }
            }

            LaunchedEffect(Unit) {
                if (!viewModel.prefs.hasRequestedInitialPermissions()) {
                    viewModel.prefs.setRequestedInitialPermissions(true)
                    val permissions = mutableListOf(
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.CAMERA,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    permissionsLauncher.launch(permissions.toTypedArray())
                }
            }

            // Predictive back handler for secondary panels -> return to HOME
            BackHandler(enabled = isOnboardingCompleted && activePanel != "HOME" && activePanel != "CORE") {
                viewModel.setActivePanel("HOME")
            }

            JarvisTheme(accentColor = accentColor) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DeepSpaceBlack)
                ) {
                    if (!isOnboardingCompleted) {
                        SetupWizardScreen(viewModel = viewModel)
                    } else {
                        MainHudScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }

    /**
     * Initializes the Android system-native TextToSpeech engine without external API dependencies.
     * Prefers the inbuilt Google Speech Services ("com.google.android.tts") for humanoid voices,
     * falling back gracefully to the system default engine if unavailable.
     */
    private fun initializeSystemTextToSpeech(preferGoogleEngine: Boolean) {
        try {
            textToSpeech = if (preferGoogleEngine) {
                TextToSpeech(this, this, "com.google.android.tts")
            } else {
                TextToSpeech(this, this)
            }
        } catch (_: Exception) {
            textToSpeech = TextToSpeech(this, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val engine = textToSpeech ?: return
            isTtsReady = true
            val vm = boundViewModel
            val activePersona = vm?.prefs?.getActivePersona()
            val voiceName = vm?.prefs?.getNeuralVoiceName() ?: "Google Humanoid Male (JARVIS Butler)"
            val langCode = vm?.prefs?.getVoiceLanguage() ?: "en-US"
            val locale = try {
                Locale.forLanguageTag(langCode)
            } catch (_: Exception) {
                Locale.US
            }
            val isFemale = activePersona?.name == "GIRLFRIEND" ||
                    activePersona?.name == "FRIDAY" ||
                    isFemaleVoiceIdentifier(voiceName)

            configureHumanoidVoice(
                tts = engine,
                isFemale = isFemale,
                locale = locale,
                preferredVoiceName = voiceName,
                pitch = activePersona?.defaultPitch ?: if (isFemale) 1.04f else 0.96f,
                speechRate = activePersona?.defaultSpeechRate ?: 1.0f
            )
            vm?.bindActivityTextToSpeech(engine)
        } else if (!attemptedDefaultTtsFallback) {
            attemptedDefaultTtsFallback = true
            isTtsReady = false
            try { textToSpeech?.shutdown() } catch (_: Exception) {}
            initializeSystemTextToSpeech(preferGoogleEngine = false)
        } else {
            isTtsReady = false
        }
    }

    /**
     * Selects the highest-fidelity system-native humanoid Male or Female Voice from the
     * Android TextToSpeech engine without requiring any external API keys.
     */
    fun configureHumanoidVoice(
        tts: TextToSpeech,
        isFemale: Boolean,
        locale: Locale = Locale.US,
        preferredVoiceName: String = "",
        pitch: Float = if (isFemale) 1.04f else 0.96f,
        speechRate: Float = 1.0f
    ): Voice? {
        try {
            val langResult = tts.setLanguage(locale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.US)
            }
            tts.setPitch(pitch.coerceIn(0.5f, 2.0f))
            tts.setSpeechRate(speechRate.coerceIn(0.5f, 2.0f))

            val installedVoices = try {
                tts.voices
            } catch (_: Exception) {
                null
            }
            if (installedVoices.isNullOrEmpty()) return null

            // 1. Check exact match if user selected a specific system voice ID
            val exactVoice = installedVoices.find {
                it.name.equals(preferredVoiceName, ignoreCase = true)
            }
            if (exactVoice != null) {
                tts.voice = exactVoice
                return exactVoice
            }

            // 2. Filter voices matching target language
            val langMatches = installedVoices.filter {
                it.locale.language.equals(locale.language, ignoreCase = true)
            }
            val pool = if (langMatches.isNotEmpty()) langMatches else installedVoices.toList()

            // 3. Select natural humanoid Male or Female voice from candidate pool
            val selectedVoice = if (isFemale) {
                pool.find { it.name.contains("sfg", ignoreCase = true) }
                    ?: pool.find { it.name.contains("tpd", ignoreCase = true) }
                    ?: pool.find { it.name.contains("fis", ignoreCase = true) }
                    ?: pool.find { it.name.contains("iom", ignoreCase = true) }
                    ?: pool.find { it.name.contains("cfl", ignoreCase = true) }
                    ?: pool.filter { isFemaleVoiceIdentifier(it.name) || it.features.contains("female") }
                        .maxByOrNull { it.quality }
            } else {
                val wantsBritishButler = preferredVoiceName.contains("butler", ignoreCase = true) ||
                        preferredVoiceName.contains("jarvis", ignoreCase = true) ||
                        preferredVoiceName.contains("rjs", ignoreCase = true)
                val britishMale = if (wantsBritishButler) {
                    installedVoices.find { it.name.contains("rjs", ignoreCase = true) }
                        ?: installedVoices.find {
                            it.locale.country.equals("GB", ignoreCase = true) && !isFemaleVoiceIdentifier(it.name)
                        }
                } else null

                britishMale
                    ?: pool.find { it.name.contains("rjs", ignoreCase = true) }
                    ?: pool.find { it.name.contains("iol", ignoreCase = true) }
                    ?: pool.find { it.name.contains("iob", ignoreCase = true) }
                    ?: pool.find { it.name.contains("iog", ignoreCase = true) }
                    ?: pool.find { it.name.contains("enc", ignoreCase = true) }
                    ?: pool.filter { !isFemaleVoiceIdentifier(it.name) || it.features.contains("male") }
                        .maxByOrNull { it.quality }
            } ?: pool.maxByOrNull { it.quality }

            if (selectedVoice != null) {
                tts.voice = selectedVoice
            }
            return selectedVoice
        } catch (_: Exception) {
            return null
        }
    }

    private fun isFemaleVoiceIdentifier(voiceName: String): Boolean {
        val lower = voiceName.lowercase(Locale.ROOT)
        if (lower.contains("male") && !lower.contains("female")) return false
        if (lower.contains("rjs") || lower.contains("iol") || lower.contains("iob") ||
            lower.contains("iog") || lower.contains("enc") || lower.contains("butler") || lower.contains("fenrir")
        ) {
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

    override fun onDestroy() {
        boundViewModel?.unbindActivityTextToSpeech()
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (_: Exception) {}
        textToSpeech = null
        isTtsReady = false
        super.onDestroy()
    }
}
