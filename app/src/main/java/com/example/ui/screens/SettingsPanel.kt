package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.automation.JarvisAccessibilityService
import com.example.data.memory.MemoryManager
import com.example.data.prefs.JarvisPersona
import com.example.data.prefs.PreferencesManager
import com.example.service.JarvisBackgroundService
import com.example.ui.hud.HudBadge
import com.example.ui.hud.HudButton
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.GlassCardBackground
import com.example.ui.theme.HologramTextBright
import com.example.ui.theme.HologramTextDim
import com.example.ui.theme.HologramTextMuted
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.ThemeCyberYellow
import com.example.ui.theme.ThemeElectricBlue
import com.example.ui.theme.ThemeNeonGreen
import com.example.ui.theme.ThemeQuantumPurple
import com.example.ui.theme.ThemeReactorRed
import com.example.ui.theme.ThemeSolarOrange
import com.example.viewmodel.ApiValidationState
import com.example.viewmodel.JarvisViewModel

@Composable
fun SettingsPanel(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accentColor = viewModel.getAccentColor()
    val scrollState = rememberScrollState()

    val userName by viewModel.prefs.userNameFlow.collectAsState()
    val activePersona by viewModel.prefs.personaFlow.collectAsState()
    val currentLanguage by viewModel.prefs.languageFlow.collectAsState()
    val memories by viewModel.memoryManager.memoriesFlow.collectAsState()
    val currentAccentName by viewModel.prefs.accentColorFlow.collectAsState()
    val validationState by viewModel.apiValidationState.collectAsState()
    val currentNeuralVoice by viewModel.prefs.neuralVoiceFlow.collectAsState()
    val currentImageModel by viewModel.prefs.imageGenModelFlow.collectAsState()

    // Modals state
    var showOperatorDialog by remember { mutableStateOf(false) }
    var showBiometricsModal by remember { mutableStateOf(false) }
    var showLanguageModal by remember { mutableStateOf(false) }

    // Custom Image Generation Model state
    var imageModelInput by remember(currentImageModel) { mutableStateOf(currentImageModel) }

    // Active AI Provider State
    var activeProvider by remember { mutableStateOf(viewModel.prefs.getActiveAiProvider()) }

    // API Key State (KeyStore Hardware Encrypted)
    var geminiApiKeyInput by remember { mutableStateOf(viewModel.prefs.getApiKeyForProvider("GEMINI")) }
    var isGeminiKeyVisible by remember { mutableStateOf(false) }

    var openAiApiKeyInput by remember { mutableStateOf(viewModel.prefs.getApiKeyForProvider("OPENAI")) }
    var isOpenAiKeyVisible by remember { mutableStateOf(false) }

    var groqApiKeyInput by remember { mutableStateOf(viewModel.prefs.getApiKeyForProvider("GROQ")) }
    var isGroqKeyVisible by remember { mutableStateOf(false) }

    var mapsApiKeyInput by remember { mutableStateOf(viewModel.prefs.getMapsApiKey()) }
    var isMapsKeyVisible by remember { mutableStateOf(false) }

    var externalTtsKeyInput by remember { mutableStateOf(viewModel.prefs.getExternalTtsApiKey()) }
    var isExternalTtsKeyVisible by remember { mutableStateOf(false) }

    // Model Selector State
    var savedCustomGeminiModels by remember { mutableStateOf(viewModel.prefs.getSavedCustomGeminiModels()) }
    var selectedModel by remember(activeProvider) {
        mutableStateOf(
            when (activeProvider) {
                "OPENAI" -> viewModel.prefs.getOpenAiModel()
                "GROQ" -> viewModel.prefs.getGroqModel()
                else -> viewModel.prefs.getGeminiModel()
            }
        )
    }
    var isModelDropdownExpanded by remember { mutableStateOf(false) }
    var customModelInput by remember { mutableStateOf(viewModel.prefs.getCustomModel()) }
    val currentProviderModels = when (activeProvider) {
        "OPENAI" -> PreferencesManager.VALID_OPENAI_MODELS
        "GROQ" -> PreferencesManager.VALID_GROQ_MODELS
        else -> (PreferencesManager.VALID_GEMINI_MODELS + savedCustomGeminiModels).distinct()
    }
    var showCustomModelInput by remember { mutableStateOf(true) }
    var isLowLatencyMode by remember { mutableStateOf(viewModel.prefs.isLowLatencyModeEnabled()) }

    // Speech Fine Tuning
    var speechRate by remember { mutableFloatStateOf(viewModel.prefs.getSpeechRate()) }
    var speechPitch by remember { mutableFloatStateOf(viewModel.prefs.getSpeechPitch()) }

    // Background & Continuous Voice Toggles
    var isContinuousVoice by remember { mutableStateOf(viewModel.prefs.isContinuousVoiceEnabled()) }
    var isBgServiceEnabled by remember { mutableStateOf(viewModel.prefs.isBackgroundServiceEnabled()) }

    // Permissions Status
    var hasMicPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var hasContactsPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    var hasPhonePermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED)
    }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasMicPermission = results[Manifest.permission.RECORD_AUDIO] ?: hasMicPermission
        hasCameraPermission = results[Manifest.permission.CAMERA] ?: hasCameraPermission
        hasContactsPermission = results[Manifest.permission.READ_CONTACTS] ?: hasContactsPermission
        hasPhonePermission = results[Manifest.permission.CALL_PHONE] ?: hasPhonePermission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = results[Manifest.permission.POST_NOTIFICATIONS] ?: hasNotificationPermission
        }
    }

    // Automation & Accessibility checks
    val isAccessibilityEnabled = JarvisAccessibilityService.isServiceEnabled(context)
    val isOverlayEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
    val isBatteryOptimizedIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val pm = context.getSystemService(PowerManager::class.java)
        pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    } else true

    // New Memory Inputs
    var newMemoryKey by remember { mutableStateOf("") }
    var newMemoryValue by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_intelligence_panel")
            .verticalScroll(scrollState)
            .padding(14.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "SETTINGS & INTELLIGENCE",
                    color = accentColor,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "System preferences & neural configuration",
                    color = HologramTextMuted,
                    fontSize = 11.sp
                )
            }

            HudBadge(text = "MARK-LIV", color = accentColor)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // A. TOP OPERATOR PROFILE CARD
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(GlassCardBackground)
                .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.2f))
                                .border(1.5.dp, accentColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Operator Avatar",
                                tint = accentColor,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = userName,
                                    color = HologramTextBright,
                                    fontSize = 15.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = { showOperatorDialog = true },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Operator Name",
                                        tint = accentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = "JARVIS OPERATOR • PRIMARY USER",
                                color = HologramTextDim,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Active Status Badges Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HudBadge(text = "VOICE ENROLLED", color = SuccessEmerald)
                    HudBadge(text = activePersona.displayName, color = accentColor)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // A2. FIREBASE AUTHENTICATION & GOOGLE CREDENTIAL MANAGER CARD
        OperatorAuthCard(
            authManager = viewModel.authManager,
            accentColor = accentColor
        )

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION 1: VOICE & NEURAL INTELLIGENCE
        SectionHeader(title = "SECTION 1: VOICE & NEURAL INTELLIGENCE", accentColor = accentColor)

        // 1. Owner Voice Biometrics Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(GlassCardBackground)
                .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Voice Biometrics",
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "OWNER VOICE BIOMETRICS",
                            color = HologramTextBright,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HudBadge(text = "ENROLLED: $userName", color = SuccessEmerald)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Acoustic neural pattern locked to primary boss $userName.",
                    color = HologramTextMuted,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(10.dp))
                HudButton(
                    text = "CALIBRATE & RE-TRAIN BIOMETRICS",
                    onClick = { showBiometricsModal = true },
                    accentColor = accentColor
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Multi-Persona Voice Engine Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(GlassCardBackground)
                .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = "Personas",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MULTI-PERSONA VOICE ENGINE",
                        color = HologramTextBright,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Persona Cards List
                JarvisPersona.entries.forEach { persona ->
                    val isSelected = activePersona == persona
                    val cardBorder = if (isSelected) accentColor else accentColor.copy(alpha = 0.15f)
                    val cardBg = if (isSelected) accentColor.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.3f)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(cardBg)
                            .border(1.dp, cardBorder, RoundedCornerShape(8.dp))
                            .clickable { viewModel.setActivePersona(persona) }
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = persona.displayName,
                                        color = if (isSelected) accentColor else HologramTextBright,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Active",
                                            tint = accentColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = persona.description,
                                    color = HologramTextMuted,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                            }

                            // Preview audio button
                            IconButton(
                                onClick = { viewModel.previewPersonaVoice(persona) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Test Voice",
                                    tint = if (isSelected) accentColor else HologramTextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Pitch & Speech Rate Fine Tuning
                Text(
                    text = "SPEECH RATE: ${String.format("%.2f", speechRate)}x",
                    color = accentColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = speechRate,
                    onValueChange = {
                        speechRate = it
                        viewModel.setSpeechRate(it)
                    },
                    valueRange = 0.75f..1.50f,
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = accentColor.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "PITCH FREQUENCY: ${String.format("%.2f", speechPitch)}x",
                    color = accentColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = speechPitch,
                    onValueChange = {
                        speechPitch = it
                        viewModel.setSpeechPitch(it)
                    },
                    valueRange = 0.75f..1.40f,
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = accentColor.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // PART 2: DEDICATED NATURAL SPEECH SYNTHESIS ENGINE (INDEPENDENT)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(GlassCardBackground)
                .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Speech Synthesis Engine",
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DEDICATED NATURAL VOICE ENGINE",
                            color = HologramTextBright,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    HudBadge(text = "ZERO GEMINI VOICE", color = SuccessEmerald)
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "High-quality, natural voice synthesis running via independent on-device speech engine with zero Gemini robotic voice calls. AI models and speech synthesis are strictly decoupled.",
                    color = HologramTextMuted,
                    fontSize = 10.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                PreferencesManager.NEURAL_VOICES.forEach { voice ->
                    val isSelected = currentNeuralVoice == voice
                    val genderLabel = when (voice) {
                        PreferencesManager.VOICE_GOOGLE_HUMANOID_FEMALE,
                        PreferencesManager.VOICE_GOOGLE_HUMANOID_FEMALE_CRISP,
                        PreferencesManager.VOICE_GOOGLE_AOEDE,
                        PreferencesManager.VOICE_GOOGLE_KORE,
                        PreferencesManager.VOICE_GOOGLE_LEDA,
                        PreferencesManager.VOICE_GOOGLE_ZEPHYR,
                        PreferencesManager.VOICE_NATURAL_FEMALE_WARM,
                        PreferencesManager.VOICE_NATURAL_FEMALE_CRISP -> "FEMALE"
                        PreferencesManager.VOICE_SYSTEM_DEFAULT -> "SYSTEM"
                        else -> "MALE"
                    }
                    val voiceDesc = when (voice) {
                        PreferencesManager.VOICE_GOOGLE_HUMANOID_MALE -> "Authentic British butler JARVIS male • Real Google Humanoid TTS"
                        PreferencesManager.VOICE_GOOGLE_HUMANOID_FEMALE -> "Warm, affectionate, caring Pari female • Real Google Humanoid TTS"
                        PreferencesManager.VOICE_GOOGLE_HUMANOID_FEMALE_CRISP -> "Sharp, crisp, tactical Friday female • Real Google Humanoid TTS"
                        PreferencesManager.VOICE_GOOGLE_HUMANOID_MALE_US -> "Deep, conversational US resonant male • Real Google Humanoid TTS"
                        PreferencesManager.VOICE_GOOGLE_FENRIR -> "Commanding tactical male baritone • In-built Google TTS"
                        PreferencesManager.VOICE_GOOGLE_AOEDE -> "Breezy, articulate, bright female tone • In-built Google TTS"
                        PreferencesManager.VOICE_GOOGLE_CHARON -> "Deep resonant baritone / bass male • In-built Google TTS"
                        PreferencesManager.VOICE_GOOGLE_PUCK -> "Playful, energetic, spirited dynamic male • In-built Google TTS"
                        PreferencesManager.VOICE_GOOGLE_KORE -> "Warm, soothing, empathic female delivery • In-built Google TTS"
                        PreferencesManager.VOICE_GOOGLE_LEDA -> "Executive, authoritative, crisp female • In-built Google TTS"
                        PreferencesManager.VOICE_GOOGLE_ORPHEUS -> "Classical orator, majestic male resonance • In-built Google TTS"
                        PreferencesManager.VOICE_GOOGLE_ZEPHYR -> "Gentle, balanced, natural conversational tone • In-built Google TTS"
                        PreferencesManager.VOICE_NATURAL_MALE_TACTICAL -> "Articulate, high-clarity tactical officer synthesis"
                        PreferencesManager.VOICE_NATURAL_FEMALE_WARM -> "Warm, expressive, high-empathy companion neural tone"
                        PreferencesManager.VOICE_NATURAL_FEMALE_CRISP -> "Sharp, crystal-clear, executive female vocal profile"
                        PreferencesManager.VOICE_NATURAL_MALE_RESONANT -> "Warm, protective, loyal male resonance"
                        PreferencesManager.VOICE_NATURAL_MALE_DEEP -> "Deep, commanding, low-frequency synthetic authority"
                        PreferencesManager.VOICE_NATURAL_MALE_SWIFT -> "Lively, high-tempo, modern cadence intelligence"
                        PreferencesManager.VOICE_SYSTEM_DEFAULT -> "Native installed Android system TTS voice"
                        else -> "High-fidelity natural vocal profile"
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) accentColor.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.3f))
                            .border(1.dp, if (isSelected) accentColor else accentColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .clickable { viewModel.setNeuralVoice(voice) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = voice,
                                        color = if (isSelected) accentColor else HologramTextBright,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    HudBadge(
                                        text = genderLabel,
                                        color = if (genderLabel == "FEMALE") ThemeQuantumPurple else accentColor
                                    )
                                }
                                Text(
                                    text = voiceDesc,
                                    color = HologramTextMuted,
                                    fontSize = 10.sp
                                )
                            }

                            IconButton(
                                onClick = { viewModel.previewSelectedVoice(voice) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Preview $voice",
                                    tint = if (isSelected) accentColor else HologramTextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Optional External Neural TTS API Key Field
                Text(
                    text = "OPTIONAL EXTERNAL NEURAL TTS API KEY",
                    color = HologramTextDim,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "By default, voices run offline/on-device with zero fees. Enter an external key only if integrating an optional cloud TTS endpoint.",
                    color = HologramTextMuted,
                    fontSize = 9.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = externalTtsKeyInput,
                    onValueChange = {
                        externalTtsKeyInput = it
                        viewModel.prefs.setExternalTtsApiKey(it)
                    },
                    label = { Text("External Cloud TTS API Key (Optional)", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                    visualTransformation = if (isExternalTtsKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { isExternalTtsKeyVisible = !isExternalTtsKeyVisible }) {
                            Icon(
                                imageVector = if (isExternalTtsKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle Visibility",
                                tint = accentColor
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = accentColor.copy(alpha = 0.4f),
                        focusedTextColor = HologramTextBright,
                        unfocusedTextColor = HologramTextBright,
                        cursorColor = accentColor
                    )
                )
            }
        }


        Spacer(modifier = Modifier.height(12.dp))

        // PART 4: Terminal Image Generation Engine Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(GlassCardBackground)
                .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "Image Model",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "TERMINAL IMAGE GENERATION ENGINE",
                        color = HologramTextBright,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Custom model routed when generating images in Terminal (e.g. Nano Banana 2):",
                    color = HologramTextMuted,
                    fontSize = 10.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PreferencesManager.POPULAR_IMAGE_MODELS.take(3).forEach { modelName ->
                        val isSel = currentImageModel == modelName
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) accentColor.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.4f))
                                .border(1.dp, if (isSel) accentColor else accentColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                .clickable {
                                    imageModelInput = modelName
                                    viewModel.setImageGenModel(modelName)
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = modelName,
                                color = if (isSel) accentColor else HologramTextBright,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = imageModelInput,
                    onValueChange = {
                        imageModelInput = it
                        viewModel.setImageGenModel(it)
                    },
                    label = { Text("Custom Image Model ID (e.g. Nano Banana 2)", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = accentColor.copy(alpha = 0.4f),
                        focusedTextColor = HologramTextBright,
                        unfocusedTextColor = HologramTextBright,
                        cursorColor = accentColor
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Language Selector Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(GlassCardBackground)
                .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val langObj = PreferencesManager.SUPPORTED_LANGUAGES.find { it.code == currentLanguage }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Language",
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "CONVERSATION LANGUAGE",
                            color = HologramTextBright,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${langObj?.flag ?: "🌐"} ${langObj?.displayName ?: currentLanguage}",
                            color = accentColor,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                HudButton(
                    text = "CHANGE",
                    onClick = { showLanguageModal = true },
                    accentColor = accentColor
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. AI NEURAL CORE & KEYSTORE CREDENTIAL SECURITY
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(GlassCardBackground)
                .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "API Security",
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI NEURAL PROVIDER & CREDENTIAL VAULT",
                            color = HologramTextBright,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    HudBadge(text = "AES-256-GCM KEYSTORE", color = SuccessEmerald)
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Select your AI provider and enter your personal API key. Credentials are encrypted in hardware KeyStore and never stored in plain text or shared.",
                    color = HologramTextMuted,
                    fontSize = 10.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Provider Selector Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("GEMINI" to "Google Gemini", "OPENAI" to "OpenAI", "GROQ" to "Groq").forEach { (providerId, label) ->
                        val isSel = activeProvider == providerId
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) accentColor.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.4f))
                                .border(1.dp, if (isSel) accentColor else accentColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .clickable {
                                    activeProvider = providerId
                                    viewModel.prefs.setActiveAiProvider(providerId)
                                    selectedModel = when (providerId) {
                                        "OPENAI" -> viewModel.prefs.getOpenAiModel()
                                        "GROQ" -> viewModel.prefs.getGroqModel()
                                        else -> viewModel.prefs.getGeminiModel()
                                    }
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSel) accentColor else HologramTextBright,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Current Provider API Key Input
                val currentKey = when (activeProvider) {
                    "OPENAI" -> openAiApiKeyInput
                    "GROQ" -> groqApiKeyInput
                    else -> geminiApiKeyInput
                }
                val isCurrentKeyVisible = when (activeProvider) {
                    "OPENAI" -> isOpenAiKeyVisible
                    "GROQ" -> isGroqKeyVisible
                    else -> isGeminiKeyVisible
                }
                val providerLabel = when (activeProvider) {
                    "OPENAI" -> "OpenAI API Key (sk-...)"
                    "GROQ" -> "Groq API Key (gsk_...)"
                    else -> "Google Gemini API Key (AIza...)"
                }

                OutlinedTextField(
                    value = currentKey,
                    onValueChange = { newVal ->
                        when (activeProvider) {
                            "OPENAI" -> {
                                openAiApiKeyInput = newVal
                                viewModel.prefs.setApiKeyForProvider("OPENAI", newVal)
                            }
                            "GROQ" -> {
                                groqApiKeyInput = newVal
                                viewModel.prefs.setApiKeyForProvider("GROQ", newVal)
                            }
                            else -> {
                                geminiApiKeyInput = newVal
                                viewModel.prefs.setApiKeyForProvider("GEMINI", newVal)
                            }
                        }
                    },
                    label = { Text(providerLabel, fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                    visualTransformation = if (isCurrentKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (currentKey.isNotBlank()) {
                                IconButton(onClick = {
                                    when (activeProvider) {
                                        "OPENAI" -> {
                                            openAiApiKeyInput = ""
                                            viewModel.prefs.deleteApiKeyForProvider("OPENAI")
                                        }
                                        "GROQ" -> {
                                            groqApiKeyInput = ""
                                            viewModel.prefs.deleteApiKeyForProvider("GROQ")
                                        }
                                        else -> {
                                            geminiApiKeyInput = ""
                                            viewModel.prefs.deleteApiKeyForProvider("GEMINI")
                                        }
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Clear Key",
                                        tint = AlertCrimson,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            IconButton(onClick = {
                                when (activeProvider) {
                                    "OPENAI" -> isOpenAiKeyVisible = !isOpenAiKeyVisible
                                    "GROQ" -> isGroqKeyVisible = !isGroqKeyVisible
                                    else -> isGeminiKeyVisible = !isGeminiKeyVisible
                                }
                            }) {
                                Icon(
                                    imageVector = if (isCurrentKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Visibility",
                                    tint = accentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = accentColor.copy(alpha = 0.4f),
                        focusedTextColor = HologramTextBright,
                        unfocusedTextColor = HologramTextBright,
                        cursorColor = accentColor
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Model Selector for Current Provider
                Text(
                    text = "ACTIVE ${activeProvider} MODEL",
                    color = accentColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.4f))
                            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .clickable { isModelDropdownExpanded = true }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedModel,
                                color = HologramTextBright,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select",
                                tint = accentColor
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = isModelDropdownExpanded,
                        onDismissRequest = { isModelDropdownExpanded = false },
                        modifier = Modifier
                            .background(GlassCardBackground)
                            .border(1.dp, accentColor.copy(alpha = 0.5f))
                    ) {
                        currentProviderModels.forEach { modelName ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = modelName,
                                        color = if (selectedModel == modelName) accentColor else HologramTextBright,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        fontWeight = if (selectedModel == modelName) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    selectedModel = modelName
                                    when (activeProvider) {
                                        "OPENAI" -> viewModel.prefs.setOpenAiModel(modelName)
                                        "GROQ" -> viewModel.prefs.setGroqModel(modelName)
                                        else -> viewModel.prefs.setGeminiModel(modelName)
                                    }
                                    isModelDropdownExpanded = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text("+ Add / Use Custom Model ID...", color = accentColor, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            },
                            onClick = {
                                isModelDropdownExpanded = false
                                showCustomModelInput = true
                                if (customModelInput.isNotBlank()) {
                                    val clean = PreferencesManager.normalizeModelId(customModelInput)
                                    selectedModel = clean
                                    viewModel.prefs.setModel(activeProvider, clean)
                                }
                            }
                        )
                    }
                }

                if (showCustomModelInput) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ADD OR ACTIVATE ANY MODEL BY ID (e.g. gemini-3.5-flash, gemini-2.5-pro):",
                        color = HologramTextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customModelInput,
                            onValueChange = { rawInput ->
                                customModelInput = rawInput
                                val clean = PreferencesManager.normalizeModelId(rawInput)
                                if (clean.isNotBlank()) {
                                    selectedModel = clean
                                    viewModel.prefs.setCustomModel(clean)
                                    when (activeProvider) {
                                        "OPENAI" -> viewModel.prefs.setOpenAiModel(clean)
                                        "GROQ" -> viewModel.prefs.setGroqModel(clean)
                                        else -> {
                                            viewModel.prefs.setGeminiModel(clean)
                                            savedCustomGeminiModels = viewModel.prefs.getSavedCustomGeminiModels()
                                        }
                                    }
                                }
                            },
                            label = { Text("Custom Model ID (any Gemini model)", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                            placeholder = { Text("gemini-3.5-flash-lite", color = HologramTextDim, fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("custom_model_id_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = accentColor.copy(alpha = 0.4f),
                                focusedTextColor = HologramTextBright,
                                unfocusedTextColor = HologramTextBright,
                                cursorColor = accentColor
                            )
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(accentColor.copy(alpha = 0.22f))
                                .border(1.dp, accentColor, RoundedCornerShape(8.dp))
                                .clickable {
                                    val clean = PreferencesManager.normalizeModelId(customModelInput)
                                    if (clean.isNotBlank()) {
                                        selectedModel = clean
                                        viewModel.prefs.setCustomModel(clean)
                                        when (activeProvider) {
                                            "OPENAI" -> viewModel.prefs.setOpenAiModel(clean)
                                            "GROQ" -> viewModel.prefs.setGroqModel(clean)
                                            else -> {
                                                viewModel.prefs.setGeminiModel(clean)
                                                savedCustomGeminiModels = viewModel.prefs.getSavedCustomGeminiModels()
                                            }
                                        }
                                        viewModel.testActiveApiKey(activeProvider, currentKey, clean)
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 14.dp)
                                .testTag("activate_custom_model_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "USE ID",
                                color = accentColor,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Ultra-Low Latency Turbo Mode Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "⚡ ULTRA-LOW LATENCY TURBO MODE",
                            color = SuccessEmerald,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Direct connection pooling, zero-404 sticky routing & fast token generation for custom/lite models.",
                            color = HologramTextMuted,
                            fontSize = 9.sp
                        )
                    }
                    Switch(
                        checked = isLowLatencyMode,
                        onCheckedChange = { enabled ->
                            isLowLatencyMode = enabled
                            viewModel.prefs.setLowLatencyModeEnabled(enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SuccessEmerald,
                            checkedTrackColor = SuccessEmerald.copy(alpha = 0.3f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Test Connection Button
                HudButton(
                    text = "TEST CONNECTION",
                    onClick = {
                        val modelToTest = if (customModelInput.isNotBlank()) {
                            PreferencesManager.normalizeModelId(customModelInput)
                        } else {
                            selectedModel
                        }
                        if (customModelInput.isNotBlank()) {
                            selectedModel = modelToTest
                            viewModel.prefs.setCustomModel(modelToTest)
                            when (activeProvider) {
                                "OPENAI" -> viewModel.prefs.setOpenAiModel(modelToTest)
                                "GROQ" -> viewModel.prefs.setGroqModel(modelToTest)
                                else -> {
                                    viewModel.prefs.setGeminiModel(modelToTest)
                                    savedCustomGeminiModels = viewModel.prefs.getSavedCustomGeminiModels()
                                }
                            }
                        }
                        viewModel.testActiveApiKey(activeProvider, currentKey, modelToTest)
                    },
                    accentColor = accentColor
                )

                Spacer(modifier = Modifier.height(6.dp))

                when (val state = validationState) {
                    is ApiValidationState.Validating -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = accentColor, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Testing $activeProvider Connection...", color = accentColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                    is ApiValidationState.Success -> {
                        Text("[VERIFIED] ${state.message}", color = SuccessEmerald, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    is ApiValidationState.Error -> {
                        Text("[ERROR] ${state.message}", color = AlertCrimson, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    is ApiValidationState.Idle -> {}
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 5. GOOGLE MAPS SDK LIVE TELEMETRY CARD
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(GlassCardBackground)
                .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Google Maps SDK",
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GOOGLE MAPS SDK LIVE TELEMETRY",
                            color = HologramTextBright,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    HudBadge(text = if (mapsApiKeyInput.isNotBlank()) "CONFIGURED" else "KEY NEEDED", color = if (mapsApiKeyInput.isNotBlank()) SuccessEmerald else ThemeSolarOrange)
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Google Maps SDK live tile rendering, tactical waypoints, route polylines, and place search. Encrypted in hardware KeyStore.",
                    color = HologramTextMuted,
                    fontSize = 10.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = mapsApiKeyInput,
                    onValueChange = {
                        mapsApiKeyInput = it
                        viewModel.prefs.setMapsApiKey(it)
                    },
                    label = { Text("Google Maps SDK API Key (AIza...)", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                    visualTransformation = if (isMapsKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { isMapsKeyVisible = !isMapsKeyVisible }) {
                            Icon(
                                imageVector = if (isMapsKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle Visibility",
                                tint = accentColor
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = accentColor.copy(alpha = 0.4f),
                        focusedTextColor = HologramTextBright,
                        unfocusedTextColor = HologramTextBright,
                        cursorColor = accentColor
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Ensure 'Maps SDK for Android' is enabled in your Google Cloud Console project.",
                    color = HologramTextDim,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Certificate and Package authorization details for Google Cloud Console
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "GCP AUTHORIZATION CREDENTIALS",
                                color = accentColor,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                            var copiedSha by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(accentColor.copy(alpha = 0.2f))
                                    .clickable {
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString("1F:53:4A:A9:36:9A:A0:D1:50:FF:45:D7:40:72:BA:48:3E:3E:43:64"))
                                        copiedSha = true
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (copiedSha) "COPIED!" else "COPY SHA-1",
                                    color = accentColor,
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Package: com.example\nSHA-1: 1F:53:4A:A9:36:9A:A0:D1:50:FF:45:D7:40:72:BA:48:3E:3E:43:64",
                            color = HologramTextBright,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Fix 'Authorization failure': In Google Cloud Console, enable 'Maps SDK for Android'. If key has an Android restriction, add the Package and SHA-1 above, or set restriction to 'None'.",
                            color = HologramTextMuted,
                            fontSize = 8.sp
                        )
                    }
                }
            }
        }


        Spacer(modifier = Modifier.height(12.dp))

        // 5. Continuous Voice & Background Telemetry Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(GlassCardBackground)
                .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CONTINUOUS VOICE LOOP",
                            color = HologramTextBright,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Auto-listen after responses for conversational flow",
                            color = HologramTextMuted,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = isContinuousVoice,
                        onCheckedChange = {
                            isContinuousVoice = it
                            viewModel.prefs.setContinuousVoiceEnabled(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = accentColor,
                            uncheckedTrackColor = Color.Black.copy(alpha = 0.4f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PERSISTENT BACKGROUND SERVICE",
                            color = HologramTextBright,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Keep Jarvis active in foreground service across apps",
                            color = HologramTextMuted,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = isBgServiceEnabled,
                        onCheckedChange = {
                            isBgServiceEnabled = it
                            viewModel.prefs.setBackgroundServiceEnabled(it)
                            if (it) {
                                if (hasMicPermission) {
                                    JarvisBackgroundService.start(context)
                                }
                            } else {
                                JarvisBackgroundService.stop(context)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = accentColor,
                            uncheckedTrackColor = Color.Black.copy(alpha = 0.4f)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION 2: APPEARANCE & THEMES
        SectionHeader(title = "SECTION 2: APPEARANCE & THEMES", accentColor = accentColor)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(GlassCardBackground)
                .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "HOLOGRAM ACCENT COLOR PALETTE",
                    color = HologramTextBright,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Real-time visual wavelength shifting for HUD and particle core",
                    color = HologramTextMuted,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PreferencesManager.THEME_COLORS.forEach { colorName ->
                        val swatchColor = when (colorName) {
                            "BLUE" -> ThemeElectricBlue
                            "YELLOW" -> ThemeCyberYellow
                            "RED" -> ThemeReactorRed
                            "GREEN" -> ThemeNeonGreen
                            "PURPLE" -> ThemeQuantumPurple
                            "ORANGE" -> ThemeSolarOrange
                            else -> ThemeElectricBlue
                        }
                        val isSelected = currentAccentName == colorName

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { viewModel.setAccentColorTheme(colorName) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected Theme",
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION 3: SYSTEM PERMISSIONS & AUTOMATION
        SectionHeader(title = "SECTION 3: SYSTEM PERMISSIONS & AUTOMATION", accentColor = accentColor)

        // Permissions Grid
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(GlassCardBackground)
                .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "CORE SYSTEM PERMISSIONS",
                    color = HologramTextBright,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                PermissionRow(
                    name = "Microphone (RECORD_AUDIO)",
                    isGranted = hasMicPermission,
                    accentColor = accentColor,
                    onGrant = { permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) }
                )
                Spacer(modifier = Modifier.height(6.dp))

                PermissionRow(
                    name = "Camera (Live Camera Vision)",
                    isGranted = hasCameraPermission,
                    accentColor = accentColor,
                    onGrant = { permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA)) }
                )
                Spacer(modifier = Modifier.height(6.dp))

                PermissionRow(
                    name = "Contacts & Calling",
                    isGranted = hasContactsPermission && hasPhonePermission,
                    accentColor = accentColor,
                    onGrant = {
                        permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE))
                    }
                )
                Spacer(modifier = Modifier.height(6.dp))

                PermissionRow(
                    name = "Notifications (POST_NOTIFICATIONS)",
                    isGranted = hasNotificationPermission,
                    accentColor = accentColor,
                    onGrant = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Hands-Free & App Control
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(GlassCardBackground)
                .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HANDS-FREE & APP CONTROL",
                        color = HologramTextBright,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )

                    HudBadge(
                        text = if (isAccessibilityEnabled && isOverlayEnabled) "ENABLED" else "ACTION REQUIRED",
                        color = if (isAccessibilityEnabled && isOverlayEnabled) SuccessEmerald else AlertCrimson
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Allows autonomous clicking, opening WhatsApp/YouTube, and background screen analysis.",
                    color = HologramTextMuted,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Action Buttons for Settings
                HudButton(
                    text = if (isAccessibilityEnabled) "ACCESSIBILITY: ACTIVE" else "ENABLE ACCESSIBILITY SERVICE",
                    onClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    },
                    accentColor = if (isAccessibilityEnabled) SuccessEmerald else accentColor
                )

                Spacer(modifier = Modifier.height(6.dp))

                HudButton(
                    text = if (isOverlayEnabled) "DISPLAY OVER APPS: ACTIVE" else "GRANT DISPLAY OVER OTHER APPS",
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                            context.startActivity(intent)
                        }
                    },
                    accentColor = if (isOverlayEnabled) SuccessEmerald else accentColor
                )

                Spacer(modifier = Modifier.height(6.dp))

                HudButton(
                    text = if (isBatteryOptimizedIgnored) "BATTERY OPTIMIZATION: UNRESTRICTED" else "IGNORE BATTERY OPTIMIZATIONS",
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                            context.startActivity(intent)
                        }
                    },
                    accentColor = if (isBatteryOptimizedIgnored) SuccessEmerald else accentColor
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION 4: MEMORY & DATA CORE
        SectionHeader(title = "SECTION 4: MEMORY & DATA CORE", accentColor = accentColor)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(GlassCardBackground)
                .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PERSISTENT RECALL MEMORIES",
                        color = HologramTextBright,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${memories.size} Facts Stored",
                        color = accentColor,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // List of Memories
                memories.forEach { (key, value) ->
                    val isProtected = key == MemoryManager.KEY_CREATOR || key == MemoryManager.KEY_BOSS
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .border(1.dp, accentColor.copy(alpha = if (isProtected) 0.45f else 0.15f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = key,
                                    color = if (isProtected) accentColor else HologramTextBright,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = value,
                                    color = HologramTextMuted,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }

                            if (!isProtected) {
                                IconButton(
                                    onClick = { viewModel.deleteMemory(key) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Memory",
                                        tint = AlertCrimson,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else {
                                HudBadge(text = "CORE", color = accentColor)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Add New Memory Row
                OutlinedTextField(
                    value = newMemoryKey,
                    onValueChange = { newMemoryKey = it },
                    label = { Text("Topic / Key (e.g., Boss Favorite Drink)", fontFamily = FontFamily.Monospace) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = accentColor.copy(alpha = 0.4f),
                        focusedTextColor = HologramTextBright,
                        unfocusedTextColor = HologramTextBright
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = newMemoryValue,
                    onValueChange = { newMemoryValue = it },
                    label = { Text("Fact / Memory Detail", fontFamily = FontFamily.Monospace) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = accentColor.copy(alpha = 0.4f),
                        focusedTextColor = HologramTextBright,
                        unfocusedTextColor = HologramTextBright
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                HudButton(
                    text = "RECORD NEW MEMORY",
                    onClick = {
                        if (newMemoryKey.isNotBlank() && newMemoryValue.isNotBlank()) {
                            viewModel.saveMemory(newMemoryKey, newMemoryValue)
                            newMemoryKey = ""
                            newMemoryValue = ""
                        }
                    },
                    accentColor = accentColor
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Dialogs
    if (showOperatorDialog) {
        OperatorNameDialog(
            viewModel = viewModel,
            onDismiss = { showOperatorDialog = false }
        )
    }

    if (showBiometricsModal) {
        VoiceBiometricsModal(
            viewModel = viewModel,
            onDismiss = { showBiometricsModal = false }
        )
    }

    if (showLanguageModal) {
        LanguageSelectorModal(
            viewModel = viewModel,
            onDismiss = { showLanguageModal = false }
        )
    }
}

@Composable
private fun SectionHeader(title: String, accentColor: Color) {
    Text(
        text = title,
        color = accentColor,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun PermissionRow(
    name: String,
    isGranted: Boolean,
    accentColor: Color,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            color = HologramTextBright,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )

        if (isGranted) {
            HudBadge(text = "GRANTED", color = SuccessEmerald)
        } else {
            HudButton(
                text = "GRANT",
                onClick = onGrant,
                accentColor = AlertCrimson
            )
        }
    }
}
