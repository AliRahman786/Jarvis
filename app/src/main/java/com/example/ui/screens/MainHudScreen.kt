package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WbSunny
import com.example.ui.hud.TacticalMapView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.battery.BatteryPowerState
import com.example.data.prefs.PreferencesManager
import com.example.ui.hud.HudBadge
import com.example.ui.hud.JarvisCoreView
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.DarkHologramSurface
import com.example.ui.theme.DeepSpaceBlack
import com.example.ui.theme.GlassCardBackground
import com.example.ui.theme.HologramTextBright
import com.example.ui.theme.HologramTextDim
import com.example.ui.theme.HologramTextMuted
import com.example.ui.theme.SuccessEmerald
import com.example.viewmodel.JarvisViewModel
import com.example.vision.CameraVisionView
import com.example.voice.VoiceState
import java.util.Calendar

@Composable
fun MainHudScreen(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    val activePanel by viewModel.activePanel.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val statusText by viewModel.statusText.collectAsState()
    val audioAmplitude by viewModel.combinedAudioAmplitude.collectAsState()
    val powerState by viewModel.batteryPowerState.collectAsState()
    val accentColor = viewModel.getAccentColor()
    val userName by viewModel.prefs.userNameFlow.collectAsState()
    val currentLanguage by viewModel.prefs.languageFlow.collectAsState()

    val isCameraVisionActive by viewModel.isCameraVisionActive.collectAsState()
    val isScreenVisionActive by viewModel.isScreenVisionActive.collectAsState()
    val triggerCameraCapture by viewModel.triggerCameraCapture.collectAsState()

    // Dialogs state for Quick-Access cards
    var showBiometricsModal by remember { mutableStateOf(false) }
    var showMemoryModal by remember { mutableStateOf(false) }
    var showLanguageModal by remember { mutableStateOf(false) }
    var showAuthModal by remember { mutableStateOf(false) }
    val showLocationModal by viewModel.showLocationModal.collectAsState()
    val showPluginsModal by viewModel.showPluginsModal.collectAsState()
    val userCity by viewModel.prefs.userLocationCityFlow.collectAsState()
    val currentAuthUser by viewModel.authManager.currentUser.collectAsState()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(DeepSpaceBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("main_hud_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. TOP HEADER (Chat Button | J A R V I S Dynamic Greeting | Location Badge | Auth & Settings)
            TopFuturisticHeader(
                userName = currentAuthUser?.displayName?.takeIf { it.isNotBlank() } ?: userName,
                userCity = userCity,
                isSignedIn = currentAuthUser != null,
                accentColor = accentColor,
                onQuickChatClick = { viewModel.setActivePanel("CHAT") },
                onAuthClick = { showAuthModal = true },
                onSettingsClick = { viewModel.setActivePanel("SETTINGS") },
                onLocationClick = { viewModel.openLocationModal() }
            )

            // 2. MAIN SUBSYSTEM CONTENT AREA
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            ) {
                when (activePanel) {
                    "HOME", "CORE" -> {
                        HomeScreenContent(
                            viewModel = viewModel,
                            voiceState = voiceState,
                            audioAmplitude = audioAmplitude,
                            statusText = statusText,
                            powerState = powerState,
                            accentColor = accentColor,
                            currentLanguage = currentLanguage,
                            isCameraVisionActive = isCameraVisionActive,
                            isScreenVisionActive = isScreenVisionActive,
                            triggerCameraCapture = triggerCameraCapture,
                            onOpenBiometrics = { showBiometricsModal = true },
                            onOpenMemories = { showMemoryModal = true },
                            onOpenLanguage = { showLanguageModal = true },
                            onCloseCamera = { viewModel.setCameraVisionActive(false) },
                            onCaptureFrame = { bitmap: Bitmap? -> viewModel.onCameraFrameCaptured(bitmap) }
                        )
                    }
                    "CHAT", "TERMINAL" -> ConversationPanel(viewModel = viewModel)
                    "SETTINGS" -> SettingsPanel(viewModel = viewModel)
                    "UPDATES" -> UpdatesScreen(viewModel = viewModel)
                    "MEMORY" -> MemoryPanel(viewModel = viewModel)
                    else -> {
                        HomeScreenContent(
                            viewModel = viewModel,
                            voiceState = voiceState,
                            audioAmplitude = audioAmplitude,
                            statusText = statusText,
                            powerState = powerState,
                            accentColor = accentColor,
                            currentLanguage = currentLanguage,
                            isCameraVisionActive = isCameraVisionActive,
                            isScreenVisionActive = isScreenVisionActive,
                            triggerCameraCapture = triggerCameraCapture,
                            onOpenBiometrics = { showBiometricsModal = true },
                            onOpenMemories = { showMemoryModal = true },
                            onOpenLanguage = { showLanguageModal = true },
                            onCloseCamera = { viewModel.setCameraVisionActive(false) },
                            onCaptureFrame = { bitmap: Bitmap? -> viewModel.onCameraFrameCaptured(bitmap) }
                        )
                    }
                }
            }

            // 3. 5-ITEM FUTURISTIC BOTTOM NAVIGATION BAR WITH GLOWING FLOATING MIC
            val isMicLockedOn by viewModel.isCoreMicLockedOn.collectAsState()
            FuturisticBottomNavBar(
                activePanel = activePanel,
                isMicLockedOn = isMicLockedOn,
                voiceState = voiceState,
                audioAmplitude = audioAmplitude,
                accentColor = accentColor,
                onSelectPanel = { viewModel.setActivePanel(it) },
                onMicClick = { viewModel.toggleCoreMicLock() }
            )
        }
    }

    // Quick-Access Modals
    if (showBiometricsModal) {
        VoiceBiometricsModal(
            viewModel = viewModel,
            onDismiss = { showBiometricsModal = false }
        )
    }

    if (showMemoryModal) {
        MemoryCoreModal(
            viewModel = viewModel,
            onDismiss = { showMemoryModal = false }
        )
    }

    if (showLanguageModal) {
        LanguageSelectorModal(
            viewModel = viewModel,
            onDismiss = { showLanguageModal = false }
        )
    }

    if (showLocationModal) {
        LocationSyncModal(
            viewModel = viewModel,
            accentColor = accentColor,
            onDismiss = { viewModel.closeLocationModal() }
        )
    }

    if (showPluginsModal) {
        NeuralPluginsModal(
            viewModel = viewModel,
            accentColor = accentColor,
            onDismiss = { viewModel.closePluginsModal() },
            onNavigateToChat = { viewModel.setActivePanel("CHAT") }
        )
    }

    if (showAuthModal) {
        AuthLoginModal(
            authManager = viewModel.authManager,
            accentColor = accentColor,
            onDismiss = { showAuthModal = false }
        )
    }
}

/**
 * 1. Top Futuristic Header
 * Left: Quick Chat bubble
 * Center: J A R V I S title + dynamic greeting (Good morning/afternoon/evening, Ali Rahman)
 * Right: Settings gear
 */
@Composable
private fun TopFuturisticHeader(
    userName: String,
    userCity: String,
    isSignedIn: Boolean,
    accentColor: Color,
    onQuickChatClick: () -> Unit,
    onAuthClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLocationClick: () -> Unit
) {
    val calendar = Calendar.getInstance()
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val greetingTime = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    val greeting = "$greetingTime, $userName"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(GlassCardBackground)
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Quick Chat Button
            IconButton(
                onClick = onQuickChatClick,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.12f))
                    .border(1.dp, accentColor.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.ChatBubbleOutline,
                    contentDescription = "Quick Chat",
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Center: Futuristic J A R V I S + Dynamic Greeting + Tactical Location Badge
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "J  A  R  V  I  S",
                    color = accentColor,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 3.sp
                )
                Text(
                    text = greeting,
                    color = HologramTextBright,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .border(0.8.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .clickable(onClick = onLocationClick)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Tactical Location",
                        tint = accentColor,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = userCity.uppercase(),
                        color = accentColor,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Right Auth Account + Settings Gear Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onAuthClick,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (isSignedIn) SuccessEmerald.copy(alpha = 0.16f) else accentColor.copy(alpha = 0.12f))
                        .border(1.dp, if (isSignedIn) SuccessEmerald else accentColor.copy(alpha = 0.4f), CircleShape)
                        .testTag("header_auth_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "User Authentication",
                        tint = if (isSignedIn) SuccessEmerald else accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.12f))
                        .border(1.dp, accentColor.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Main Home Screen Content:
 * - 3D Particle Orb Visualizer
 * - Status Pill Button below orb
 * - Live Weather Bar
 * - 3 Quick-Access Status Cards: Voice (Biometrics), Memories (Recall Core), Language (Language Modal)
 * - Vision Action Row (Camera Vision, Screen Vision)
 */
@Composable
private fun HomeScreenContent(
    viewModel: JarvisViewModel,
    voiceState: VoiceState,
    audioAmplitude: Float,
    statusText: String,
    powerState: BatteryPowerState,
    accentColor: Color,
    currentLanguage: String,
    isCameraVisionActive: Boolean,
    isScreenVisionActive: Boolean,
    triggerCameraCapture: Boolean,
    onOpenBiometrics: () -> Unit,
    onOpenMemories: () -> Unit,
    onOpenLanguage: () -> Unit,
    onCloseCamera: () -> Unit,
    onCaptureFrame: (Bitmap?) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val isTacticalMapActive by viewModel.isTacticalMapActive.collectAsState()
        val mapCity by viewModel.mapCity.collectAsState()
        val mapLatitude by viewModel.mapLatitude.collectAsState()
        val mapLongitude by viewModel.mapLongitude.collectAsState()

        // Visualizer Mode Selector Tab (Neural Core vs Tactical Google Map)
        Row(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (!isTacticalMapActive && !isCameraVisionActive) accentColor.copy(alpha = 0.25f) else Color.Transparent)
                    .clickable {
                        viewModel.setTacticalMapActive(false)
                        viewModel.setCameraVisionActive(false)
                    }
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "NEURAL CORE",
                    color = if (!isTacticalMapActive && !isCameraVisionActive) accentColor else HologramTextDim,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isTacticalMapActive) accentColor.copy(alpha = 0.25f) else Color.Transparent)
                    .clickable {
                        viewModel.setTacticalMapActive(true)
                        viewModel.setCameraVisionActive(false)
                    }
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = "Map",
                        tint = if (isTacticalMapActive) accentColor else HologramTextDim,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "INBUILT LIVE MAP",
                        color = if (isTacticalMapActive) accentColor else HologramTextDim,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (isTacticalMapActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TacticalQuickChip(
                    label = "NY ➔ BOS",
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.findRouteAndDirections("New York", "Boston") }
                )
                TacticalQuickChip(
                    label = "DELHI ➔ AGRA",
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.findRouteAndDirections("New Delhi", "Agra") }
                )
                TacticalQuickChip(
                    label = "☕ CAFES",
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.searchNearbyPlaces("New Delhi", "coffee") }
                )
                TacticalQuickChip(
                    label = "🗼 EIFFEL",
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.findPlaceInformation("Eiffel Tower") }
                )
            }
        }

        // 2. CENTER DISPLAY (Camera Vision / Tactical Google Map / 3D Particle Orb)
        if (isCameraVisionActive) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .padding(vertical = 4.dp)
            ) {
                CameraVisionView(
                    accentColor = accentColor,
                    onCloseCamera = onCloseCamera,
                    onCaptureFrameReady = onCaptureFrame,
                    triggerCapture = triggerCameraCapture
                )
            }
        } else if (isTacticalMapActive) {
            val activeRoute by viewModel.activeRoute.collectAsState()
            val activePlace by viewModel.activePlace.collectAsState()
            val searchResultPlaces by viewModel.searchResultPlaces.collectAsState()
            val liveUserGpsLocation by viewModel.liveUserGpsLocation.collectAsState()
            TacticalMapView(
                accentColor = accentColor,
                targetCity = mapCity,
                latitude = mapLatitude,
                longitude = mapLongitude,
                routeCalculation = activeRoute,
                placeLocation = activePlace,
                searchResults = searchResultPlaces,
                liveUserGpsLocation = liveUserGpsLocation,
                onClearRoute = { viewModel.clearTacticalRoute() },
                onUserLocationUpdated = { viewModel.onUserGpsLocationUpdated(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        } else {
            val isMicLockedOn by viewModel.isCoreMicLockedOn.collectAsState()
            val liveWeather by viewModel.weatherData.collectAsState()

            Box(
                modifier = Modifier
                    .size(260.dp)
                    .padding(top = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                JarvisCoreView(
                    voiceState = voiceState,
                    audioAmplitude = audioAmplitude,
                    accentColor = accentColor,
                    onCoreTap = { viewModel.toggleCoreMicLock() }
                )
            }
        }

        // Status Pill Button directly below the orb:
        // Idle: JARVIS OFF • TAP TO START (Subtitle: "Tap mic or orb to power on")
        // Active: MIC ON • READY with green indicator dot (Subtitle: "Tap to mute • Hold to power off")
        val isMicLockedOn by viewModel.isCoreMicLockedOn.collectAsState()
        val liveWeather by viewModel.weatherData.collectAsState()

        StatusPillButton(
            isMicLockedOn = isMicLockedOn,
            voiceState = voiceState,
            accentColor = accentColor,
            onClick = { viewModel.toggleCoreMicLock() },
            onLongClick = { viewModel.cancelAction() }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 3. LIVE WEATHER BAR
        LiveWeatherBar(
            weather = liveWeather,
            accentColor = accentColor,
            onRefresh = { viewModel.refreshWeather() },
            onOpenLocationDialog = { viewModel.openLocationModal() }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 4. 4 QUICK-ACCESS STATUS CARDS: Voice | Memories | Language | Plugins
        val langObj = PreferencesManager.SUPPORTED_LANGUAGES.find { it.code == currentLanguage }
        val langLabel = langObj?.displayName?.substringBefore(" ") ?: "English"
        val enabledPlugins by viewModel.prefs.enabledPluginsFlow.collectAsState()
        val isFlashlightOn by viewModel.isFlashlightOn.collectAsState()
        val quotaShieldStatus by viewModel.quotaShieldStatus.collectAsState()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            QuickAccessCard(
                icon = Icons.Default.Fingerprint,
                title = "Voice",
                subtitle = "Enrolled",
                subtitleColor = SuccessEmerald,
                accentColor = accentColor,
                modifier = Modifier.weight(1f),
                onClick = onOpenBiometrics
            )

            QuickAccessCard(
                icon = Icons.Default.Psychology,
                title = "Memories",
                subtitle = "Recall Core",
                subtitleColor = accentColor,
                accentColor = accentColor,
                modifier = Modifier.weight(1f),
                onClick = onOpenMemories
            )

            QuickAccessCard(
                icon = Icons.Default.Language,
                title = "Language",
                subtitle = langLabel,
                subtitleColor = HologramTextBright,
                accentColor = accentColor,
                modifier = Modifier.weight(1f),
                onClick = onOpenLanguage
            )

            QuickAccessCard(
                icon = Icons.Default.Extension,
                title = "Plugins",
                subtitle = "${enabledPlugins.size}/8 Active",
                subtitleColor = SuccessEmerald,
                accentColor = accentColor,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.openPluginsModal() }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 5. QUICK CONTROLS ROW (Camera Vision, Tactical Map & Screen Vision)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            VisionControlButton(
                text = if (isCameraVisionActive) "CAMERA: ON" else "CAMERA",
                icon = Icons.Default.CameraAlt,
                isActive = isCameraVisionActive,
                accentColor = accentColor,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.toggleCameraVision() }
            )

            VisionControlButton(
                text = if (isTacticalMapActive) "INBUILT MAP: ON" else "INBUILT MAP",
                icon = Icons.Default.Map,
                isActive = isTacticalMapActive,
                accentColor = accentColor,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.toggleTacticalMap() }
            )

            VisionControlButton(
                text = if (isScreenVisionActive) "SCREEN: ON" else "SCREEN",
                icon = Icons.Default.Screenshot,
                isActive = isScreenVisionActive,
                accentColor = accentColor,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.toggleScreenVision() }
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 6. AUTONOMOUS AGENT & MULTIMODAL QUICK DECK (Briefing | Torch | System Audit | Files & Img)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TacticalQuickChip(
                label = "⚡ BRIEFING",
                accentColor = accentColor,
                modifier = Modifier.weight(1f),
                onClick = {
                    viewModel.executeAutonomousMission("MORNING_BRIEFING")
                    viewModel.setActivePanel("CHAT")
                }
            )
            TacticalQuickChip(
                label = if (isFlashlightOn) "🔦 TORCH: ON" else "🔦 TORCH",
                accentColor = if (isFlashlightOn) SuccessEmerald else accentColor,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.toggleFlashlight() }
            )
            TacticalQuickChip(
                label = "🛡️ HW AUDIT",
                accentColor = accentColor,
                modifier = Modifier.weight(1f),
                onClick = {
                    viewModel.executeAutonomousMission("HARDWARE_AUDIT")
                    viewModel.setActivePanel("CHAT")
                }
            )
            TacticalQuickChip(
                label = "📎 FILES / IMG",
                accentColor = accentColor,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.setActivePanel("CHAT") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // System Telemetry & Quota Shield Ticker
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.35f))
                .border(1.dp, accentColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = statusText.uppercase(),
                        color = accentColor,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    Text(
                        text = "${powerState.level}% ${if (powerState.isCharging) "⚡" else ""}",
                        color = if (powerState.level > 20) SuccessEmerald else AlertCrimson,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "API Quota Shield",
                            tint = SuccessEmerald,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = quotaShieldStatus,
                            color = SuccessEmerald,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = "PLUGINS: ${enabledPlugins.size}/8 ONLINE",
                        color = HologramTextMuted,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

/**
 * Status Pill Button directly below the orb:
 * - When idle: JARVIS OFF • TAP TO START (Subtitle: "Tap mic or orb to power on")
 * - When active: MIC ON • READY with green indicator dot (Subtitle: "Tap to mute • Hold to power off")
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StatusPillButton(
    isMicLockedOn: Boolean,
    voiceState: VoiceState,
    accentColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isActive = isMicLockedOn || voiceState == VoiceState.LISTENING || voiceState == VoiceState.SPEAKING || voiceState == VoiceState.THINKING
    val titleText = when {
        voiceState == VoiceState.SPEAKING -> "JARVIS SPEAKING • BARGE-IN ARMED"
        voiceState == VoiceState.THINKING -> "NEURAL CORE • COMPUTING"
        !isMicLockedOn -> "JARVIS OFF • TAP TO START"
        else -> "MIC ON • READY"
    }
    val subtitleText = when {
        voiceState == VoiceState.SPEAKING -> "Speak or tap to interrupt JARVIS instantly"
        isMicLockedOn -> "Tap to mute • Hold to power off"
        else -> "Tap mic or orb to power on"
    }
    val borderColor = if (isActive) SuccessEmerald else accentColor.copy(alpha = 0.4f)
    val pillBg = if (isActive) SuccessEmerald.copy(alpha = 0.15f) else GlassCardBackground

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(pillBg)
            .border(1.5.dp, borderColor, RoundedCornerShape(24.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Indicator Dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isMicLockedOn) SuccessEmerald else HologramTextDim)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = titleText,
                    color = if (isMicLockedOn) SuccessEmerald else HologramTextBright,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            Text(
                text = subtitleText,
                color = HologramTextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * 3. Live Weather Bar
 * Full-width rounded card displaying:
 * Current temperature (29°C), condition & city, "Feels like • High / Low • Humidity%", and weather icon.
 */
@Composable
private fun LiveWeatherBar(
    weather: com.example.data.weather.LiveWeatherData,
    accentColor: Color,
    onRefresh: () -> Unit,
    onOpenLocationDialog: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GlassCardBackground)
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenLocationDialog)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f))
                        .border(1.dp, accentColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (weather.weatherCode in 51..99) Icons.Default.CloudQueue else Icons.Default.WbSunny,
                        contentDescription = "Weather",
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${weather.temperature}°C",
                            color = HologramTextBright,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(accentColor.copy(alpha = 0.12f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Location",
                                tint = accentColor,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${weather.condition} • ${weather.city}",
                                color = accentColor,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Text(
                        text = "Feels like ${weather.feelsLike}°C • High ${weather.high}° / Low ${weather.low}° • Humidity ${weather.humidity}%",
                        color = HologramTextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onOpenLocationDialog,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Set Location",
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Weather",
                        tint = accentColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Quick-Access Status Card: Icon + Title + Subtitle
 */
@Composable
private fun QuickAccessCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    subtitleColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(GlassCardBackground)
            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                color = HologramTextBright,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = subtitleColor,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

/**
 * Vision Control Button (Camera / Screen Vision)
 */
@Composable
private fun VisionControlButton(
    text: String,
    icon: ImageVector,
    isActive: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (isActive) accentColor.copy(alpha = 0.2f) else GlassCardBackground
    val borderCol = if (isActive) accentColor else accentColor.copy(alpha = 0.25f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, borderCol, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = if (isActive) accentColor else HologramTextMuted,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                color = if (isActive) accentColor else HologramTextBright,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 5. Futuristic Bottom Navigation Bar
 * 5 items: Home, Chat, Central Floating Glowing Microphone Button, Settings, Updates
 */
@Composable
private fun FuturisticBottomNavBar(
    activePanel: String,
    isMicLockedOn: Boolean,
    voiceState: VoiceState,
    audioAmplitude: Float,
    accentColor: Color,
    onSelectPanel: (String) -> Unit,
    onMicClick: () -> Unit
) {
    val isMicActive = isMicLockedOn || voiceState == VoiceState.LISTENING || voiceState == VoiceState.SPEAKING
    val infiniteTransition = rememberInfiniteTransition(label = "halo_anim")
    val haloScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(GlassCardBackground)
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            // 1. HOME
            BottomNavItem(
                icon = Icons.Default.Home,
                label = "Home",
                isSelected = activePanel == "HOME" || activePanel == "CORE",
                accentColor = accentColor,
                onClick = { onSelectPanel("HOME") }
            )

            // 2. CHAT
            BottomNavItem(
                icon = Icons.AutoMirrored.Filled.Chat,
                label = "Chat",
                isSelected = activePanel == "CHAT" || activePanel == "TERMINAL",
                accentColor = accentColor,
                onClick = { onSelectPanel("CHAT") }
            )

            // 3. CENTRAL FLOATING GLOWING MICROPHONE BUTTON
            Box(
                modifier = Modifier
                    .size(60.dp),
                contentAlignment = Alignment.Center
            ) {
                // Pulsing outer halo when active
                if (isMicActive) {
                    Box(
                        modifier = Modifier
                            .size((56 * haloScale).dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.25f))
                    )
                }

                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentColor,
                                    accentColor.copy(alpha = 0.75f),
                                    Color.Black
                                )
                            )
                        )
                        .border(1.5.dp, if (isMicActive) Color.White else accentColor, CircleShape)
                        .clickable(onClick = onMicClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isMicActive) Icons.Default.Mic else Icons.Default.Mic,
                        contentDescription = "Voice Action",
                        tint = Color.Black,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // 4. SETTINGS
            BottomNavItem(
                icon = Icons.Default.Settings,
                label = "Settings",
                isSelected = activePanel == "SETTINGS",
                accentColor = accentColor,
                onClick = { onSelectPanel("SETTINGS") }
            )

            // 5. UPDATES
            BottomNavItem(
                icon = Icons.Default.SystemUpdate,
                label = "Updates",
                isSelected = activePanel == "UPDATES",
                accentColor = accentColor,
                onClick = { onSelectPanel("UPDATES") }
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) accentColor else HologramTextDim,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = if (isSelected) accentColor else HologramTextDim,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun TacticalQuickChip(
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = accentColor,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
