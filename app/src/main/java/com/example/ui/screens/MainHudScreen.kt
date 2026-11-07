package com.example.ui.screens

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.battery.BatteryPowerState
import com.example.data.prefs.JarvisPersona
import com.example.data.prefs.PreferencesManager
import com.example.ui.hud.JarvisCoreView
import com.example.ui.hud.TacticalMapView
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.ArcAmberBright
import com.example.ui.theme.DeepSpaceBlack
import com.example.ui.theme.HologramTextBright
import com.example.ui.theme.HologramTextDim
import com.example.ui.theme.HologramTextMuted
import com.example.ui.theme.SuccessEmerald
import com.example.viewmodel.JarvisViewModel
import com.example.vision.CameraVisionView
import com.example.voice.VoiceState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val StarkCyan = Color(0xFF00E5FF)
private val StarkElectricBlue = Color(0xFF0088FF)
private val StarkGold = Color(0xFFFFB300)
private val StarkGoldBright = Color(0xFFFFD54F)
private val StarkPanelBg = Color(0xD9050F1E)
private val StarkPanelDark = Color(0xEE030914)

/**
 * Creates a chamfered (cut-corner) sci-fi polygon shape for HUD cards.
 */
private fun chamferedShape(cutPx: Float = 22f) = GenericShape { size, _ ->
    val c = cutPx.coerceAtMost(size.minDimension * 0.28f)
    moveTo(c, 0f)
    lineTo(size.width - c, 0f)
    lineTo(size.width, c)
    lineTo(size.width, size.height - c)
    lineTo(size.width - c, size.height)
    lineTo(c, size.height)
    lineTo(0f, size.height - c)
    lineTo(0f, c)
    close()
}

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
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF020712),
                        Color(0xFF051329),
                        Color(0xFF030A17),
                        DeepSpaceBlack
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("main_hud_screen")
    ) {
        // Subtle futuristic circuit grid & radial glow backdrop
        HudBackgroundBackdrop()

        Column(modifier = Modifier.fillMaxSize()) {
            // 1. TOP FUTURISTIC STARK HEADER
            TopFuturisticHeader(
                userName = currentAuthUser?.displayName?.takeIf { it.isNotBlank() } ?: userName,
                userCity = userCity,
                isSignedIn = currentAuthUser != null,
                accentColor = accentColor,
                onMenuClick = { viewModel.openPluginsModal() },
                onAuthClick = { showAuthModal = true },
                onSettingsClick = { viewModel.setActivePanel("SETTINGS") },
                onLocationClick = { viewModel.openLocationModal() }
            )

            // 2. MAIN SUBSYSTEM CONTENT AREA
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
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

            // 3. 5-ITEM FUTURISTIC CHAMFERED BOTTOM NAVIGATION BAR WITH AVENGERS 'A' CORE EMBLEM
            val isMicLockedOn by viewModel.isCoreMicLockedOn.collectAsState()
            FuturisticBottomNavBar(
                activePanel = activePanel,
                isMicLockedOn = isMicLockedOn,
                voiceState = voiceState,
                accentColor = accentColor,
                onSelectPanel = { panel ->
                    if (panel == "TOOLS") {
                        viewModel.openPluginsModal()
                    } else {
                        viewModel.setActivePanel(panel)
                    }
                },
                onCenterEmblemClick = {
                    if (activePanel != "HOME" && activePanel != "CORE") {
                        viewModel.setActivePanel("HOME")
                    } else {
                        viewModel.toggleCoreMicLock()
                    }
                }
            )
        }
    }

    // Quick-Access Modals (All preserved)
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

@Composable
private fun HudBackgroundBackdrop() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Ambient cyan-blue reactor glow in upper center
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    StarkElectricBlue.copy(alpha = 0.22f),
                    StarkCyan.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = Offset(w * 0.5f, h * 0.26f),
                radius = w * 0.65f
            ),
            radius = w * 0.65f,
            center = Offset(w * 0.5f, h * 0.26f)
        )

        // Subtle vertical tech lines in background
        val cols = 8
        for (i in 1 until cols) {
            val x = w * (i.toFloat() / cols)
            drawLine(
                color = StarkCyan.copy(alpha = 0.035f),
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 1f
            )
        }
    }
}

/**
 * 1. Top Futuristic Header matching the reference HUD:
 * Left: Dual-ring gold/cyan circular Menu button
 * Center: J A R V I S metallic title + GOOD MORNING, ALI RAHMAN + Location selector pill with chevron
 * Right: Glowing Profile/Auth button + Dual-ring gold/cyan Settings gear button
 */
@Composable
private fun TopFuturisticHeader(
    userName: String,
    userCity: String,
    isSignedIn: Boolean,
    accentColor: Color,
    onMenuClick: () -> Unit,
    onAuthClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLocationClick: () -> Unit
) {
    val calendar = Calendar.getInstance()
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val greetingTime = when (hour) {
        in 5..11 -> "GOOD MORNING"
        in 12..16 -> "GOOD AFTERNOON"
        else -> "GOOD EVENING"
    }
    val greeting = "$greetingTime, ${userName.uppercase()}"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // Decorative sci-fi top frame lines
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp)
        ) {
            val w = size.width
            val h = size.height

            // Top angled cyber wing lines flanking JARVIS
            val topPath = Path().apply {
                moveTo(0f, 10f)
                lineTo(w * 0.24f, 10f)
                lineTo(w * 0.30f, 2f)
                lineTo(w * 0.70f, 2f)
                lineTo(w * 0.76f, 10f)
                lineTo(w, 10f)
            }
            drawPath(
                path = topPath,
                color = StarkCyan.copy(alpha = 0.65f),
                style = Stroke(width = 2.0f)
            )

            // Bottom subtle divider bracket under greeting
            val bottomPath = Path().apply {
                moveTo(w * 0.14f, h * 0.70f)
                lineTo(w * 0.34f, h * 0.70f)
                moveTo(w * 0.66f, h * 0.70f)
                lineTo(w * 0.86f, h * 0.70f)
            }
            drawPath(
                path = bottomPath,
                color = StarkCyan.copy(alpha = 0.45f),
                style = Stroke(width = 1.4f)
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Dual-Ring Gold & Cyan Menu Button
                GlowingDualRingIconButton(
                    icon = Icons.Default.Menu,
                    contentDescription = "Quick Menu",
                    outerColor = StarkGold,
                    innerColor = StarkCyan,
                    size = 44.dp,
                    onClick = onMenuClick
                )

                // Center: J A R V I S + Dynamic Greeting
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "J A R V I S",
                        color = Color(0xFFE8F6FF),
                        fontSize = 21.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 5.sp
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = greeting,
                        color = HologramTextBright,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Right: Auth Profile Button + Dual-Ring Settings Gear Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        (if (isSignedIn) SuccessEmerald else StarkCyan).copy(alpha = 0.28f),
                                        StarkPanelDark
                                    )
                                )
                            )
                            .border(
                                1.5.dp,
                                if (isSignedIn) SuccessEmerald else StarkCyan.copy(alpha = 0.85f),
                                CircleShape
                            )
                            .clickable(onClick = onAuthClick)
                            .testTag("header_auth_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSignedIn) Icons.Default.AccountCircle else Icons.Default.Person,
                            contentDescription = "User Authentication",
                            tint = if (isSignedIn) SuccessEmerald else StarkCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    GlowingDualRingIconButton(
                        icon = Icons.Default.Settings,
                        contentDescription = "Settings",
                        outerColor = StarkGold,
                        innerColor = StarkCyan,
                        size = 40.dp,
                        onClick = onSettingsClick
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Pill Location Selector: 📍 BHADOHI, INDIA ⌄
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xDD041226))
                    .border(1.dp, StarkCyan.copy(alpha = 0.75f), RoundedCornerShape(50))
                    .clickable(onClick = onLocationClick)
                    .padding(horizontal = 12.dp, vertical = 3.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Tactical Location",
                    tint = StarkCyan,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${userCity.uppercase()}, INDIA".takeIf { !userCity.contains(",") } ?: userCity.uppercase(),
                    color = HologramTextBright,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.width(3.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Select Location",
                    tint = StarkCyan,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
private fun GlowingDualRingIconButton(
    icon: ImageVector,
    contentDescription: String,
    outerColor: Color,
    innerColor: Color,
    size: Dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            val c = Offset(this.size.width / 2f, this.size.height / 2f)

            // Dark core fill
            drawCircle(
                color = StarkPanelDark,
                radius = r * 0.88f,
                center = c
            )

            // Outer segmented gold ring
            for (i in 0 until 8) {
                drawArc(
                    color = outerColor.copy(alpha = if (i % 2 == 0) 0.9f else 0.45f),
                    startAngle = i * 45f + 4f,
                    sweepAngle = 36f,
                    useCenter = false,
                    style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                )
            }

            // Inner cyan ring
            drawCircle(
                color = innerColor.copy(alpha = 0.85f),
                radius = r * 0.72f,
                center = c,
                style = Stroke(width = 2.0f)
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = innerColor,
            modifier = Modifier.size(size * 0.46f)
        )
    }
}

/**
 * Main Home Screen Content matching the exact Stark Mark-LIV Sci-Fi HUD layout:
 * - Upper Hero Row: System Status + Mini Weather | 3D Arc Reactor Core on Pedestal | Core Mode (Assistant/Companion/Focus)
 * - Middle Row: 3D Holographic Globe | Location & Weather Card | Quick Controls (Camera, Torch, Settings, Files)
 * - Voice Command Banner: Waveform + Central Glowing Mic + "Tap or say... 'Jarvis, open YouTube'"
 * - 5 Hex Feature Tiles: Memories | Language | Plugins | Inbuilt Maps | Screen
 * - 4 Action Pill Chips: Briefing | Torch | HW Audit | Files / IMG
 * - Lower Deck: Neural Core (Brain) | J.A.R.V.I.S. More Than An AI Pedestal | Activity Log
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
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val isTacticalMapActive by viewModel.isTacticalMapActive.collectAsState()
    val mapCity by viewModel.mapCity.collectAsState()
    val mapLatitude by viewModel.mapLatitude.collectAsState()
    val mapLongitude by viewModel.mapLongitude.collectAsState()
    val isMicLockedOn by viewModel.isCoreMicLockedOn.collectAsState()
    val liveWeather by viewModel.weatherData.collectAsState()
    val activePersona by viewModel.prefs.personaFlow.collectAsState()
    val enabledPlugins by viewModel.prefs.enabledPluginsFlow.collectAsState()
    val isFlashlightOn by viewModel.isFlashlightOn.collectAsState()

    // Compute real device RAM usage percentage
    val ramUsagePercent = remember(powerState.level) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(memInfo)
            if (memInfo.totalMem > 0L) {
                val used = memInfo.totalMem - memInfo.availMem
                ((used.toDouble() / memInfo.totalMem.toDouble()) * 100.0).toInt().coerceIn(24, 92)
            } else 48
        } catch (_: Exception) {
            48
        }
    }

    val cpuLoadPercent = remember(voiceState) {
        when (voiceState) {
            VoiceState.THINKING, VoiceState.PROCESSING -> 38
            VoiceState.SPEAKING -> 24
            VoiceState.LISTENING -> 18
            else -> 12
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // If Camera Vision or Inbuilt Live Map is engaged, show a return bar + active viewport
        if (isCameraVisionActive || isTacticalMapActive) {
            Row(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(StarkPanelDark)
                    .border(1.dp, StarkCyan.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (!isTacticalMapActive && !isCameraVisionActive) StarkCyan.copy(alpha = 0.25f) else Color.Transparent)
                        .clickable {
                            viewModel.setTacticalMapActive(false)
                            viewModel.setCameraVisionActive(false)
                        }
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "◀ NEURAL CORE HUD",
                        color = StarkCyan,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isTacticalMapActive) StarkGold.copy(alpha = 0.25f) else Color.Transparent)
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
                            tint = if (isTacticalMapActive) StarkGoldBright else HologramTextDim,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "INBUILT LIVE MAP",
                            color = if (isTacticalMapActive) StarkGoldBright else HologramTextDim,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
                    accentColor = StarkCyan,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.findRouteAndDirections("New York", "Boston") }
                )
                TacticalQuickChip(
                    label = "DELHI ➔ AGRA",
                    accentColor = StarkCyan,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.findRouteAndDirections("New Delhi", "Agra") }
                )
                TacticalQuickChip(
                    label = "☕ CAFES",
                    accentColor = StarkCyan,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.searchNearbyPlaces("New Delhi", "coffee") }
                )
                TacticalQuickChip(
                    label = "🗼 EIFFEL",
                    accentColor = StarkCyan,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.findPlaceInformation("Eiffel Tower") }
                )
            }
        }

        if (isCameraVisionActive) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(270.dp)
                    .padding(vertical = 4.dp)
            ) {
                CameraVisionView(
                    accentColor = StarkCyan,
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
                accentColor = StarkCyan,
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
            // =========================================================================
            // ROW 1: UPPER HERO DECK (SYSTEM STATUS + 3D ARC REACTOR CORE + CORE MODE)
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(222.dp)
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Left Column: SYSTEM STATUS + Mini Weather Pod
                SystemStatusAndMiniWeatherColumn(
                    cpuPercent = cpuLoadPercent,
                    ramPercent = ramUsagePercent,
                    batteryPercent = powerState.level,
                    weatherTemp = liveWeather.temperature,
                    weatherCondition = liveWeather.condition,
                    onWeatherClick = { viewModel.refreshWeather() },
                    modifier = Modifier
                        .weight(0.32f)
                        .fillMaxHeight()
                )

                // Center Column: 3D Arc Reactor Triangular Mind-Core on Holographic Pedestal
                Box(
                    modifier = Modifier
                        .weight(0.36f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    JarvisCoreView(
                        voiceState = voiceState,
                        audioAmplitude = audioAmplitude,
                        accentColor = StarkCyan,
                        onCoreTap = { viewModel.toggleCoreMicLock() }
                    )
                }

                // Right Column: CORE MODE (Assistant / Companion / Focus)
                CoreModeSelectorCard(
                    activePersona = activePersona,
                    onSelectPersona = { viewModel.setActivePersona(it) },
                    modifier = Modifier
                        .weight(0.32f)
                        .fillMaxHeight()
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // =========================================================================
        // ROW 2: 3D HOLOGRAPHIC GLOBE + LOCATION/WEATHER CARD + QUICK CONTROLS CARD
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(126.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Left: 3D Rotating Holographic Earth Globe on Pedestal
            HolographicEarthGlobe(
                onClick = { viewModel.toggleTacticalMap() },
                modifier = Modifier
                    .weight(0.28f)
                    .fillMaxHeight()
            )

            // Center: LOCATION & WEATHER Telemetry Card
            LocationAndWeatherCenterCard(
                city = liveWeather.city.ifBlank { mapCity },
                latitude = mapLatitude,
                longitude = mapLongitude,
                temperature = liveWeather.temperature,
                condition = liveWeather.condition,
                high = liveWeather.high,
                low = liveWeather.low,
                onClick = { viewModel.openLocationModal() },
                modifier = Modifier
                    .weight(0.30f)
                    .fillMaxHeight()
            )

            // Right: QUICK CONTROLS (Camera, Torch, Settings, Files)
            QuickControlsHudCard(
                isCameraActive = isCameraVisionActive,
                isTorchActive = isFlashlightOn,
                onCameraClick = { viewModel.toggleCameraVision() },
                onTorchClick = { viewModel.toggleFlashlight() },
                onSettingsClick = { viewModel.setActivePanel("SETTINGS") },
                onFilesClick = { viewModel.setActivePanel("CHAT") },
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight()
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // =========================================================================
        // ROW 3: BEVELLED VOICE COMMAND BAR WITH CENTRAL GLOWING MICROPHONE ORB
        // =========================================================================
        BevelledVoiceCommandBar(
            isMicLockedOn = isMicLockedOn,
            voiceState = voiceState,
            audioAmplitude = audioAmplitude,
            statusText = statusText,
            onClick = { viewModel.toggleCoreMicLock() },
            onLongClick = { viewModel.cancelAction() }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // =========================================================================
        // ROW 4: 5 HEXAGONAL / CHAMFERED FEATURE TILES
        // (Memories | Language | Plugins | Inbuilt Maps | Screen)
        // =========================================================================
        val langObj = PreferencesManager.SUPPORTED_LANGUAGES.find { it.code == currentLanguage }
        val langLabel = langObj?.displayName?.substringBefore(" ") ?: "English"

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            HexFeatureTile(
                icon = Icons.Default.Psychology,
                title = "Memories",
                subtitle = "Recall Core",
                subtitleColor = HologramTextMuted,
                isGoldHighlighted = false,
                modifier = Modifier.weight(1f),
                onClick = onOpenMemories
            )

            HexFeatureTile(
                icon = Icons.Default.Language,
                title = "Language",
                subtitle = langLabel,
                subtitleColor = HologramTextMuted,
                isGoldHighlighted = false,
                modifier = Modifier.weight(1f),
                onClick = onOpenLanguage
            )

            HexFeatureTile(
                icon = Icons.Default.Extension,
                title = "Plugins",
                subtitle = "${enabledPlugins.size}/8 Active",
                subtitleColor = SuccessEmerald,
                isGoldHighlighted = true,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.openPluginsModal() }
            )

            HexFeatureTile(
                icon = Icons.Default.LocationOn,
                title = "Inbuilt Maps",
                subtitle = if (isTacticalMapActive) "Active" else "Navigation",
                subtitleColor = if (isTacticalMapActive) SuccessEmerald else HologramTextMuted,
                isGoldHighlighted = isTacticalMapActive,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.toggleTacticalMap() }
            )

            HexFeatureTile(
                icon = Icons.Default.DesktopWindows,
                title = "Screen",
                subtitle = if (isScreenVisionActive) "Monitoring" else "Live View",
                subtitleColor = if (isScreenVisionActive) SuccessEmerald else HologramTextMuted,
                isGoldHighlighted = isScreenVisionActive,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.toggleScreenVision() }
            )
        }

        Spacer(modifier = Modifier.height(7.dp))

        // =========================================================================
        // ROW 5: 4 ACTION PILL CHIPS (Briefing | Torch | HW Audit | Files / IMG)
        // =========================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            ActionPillChip(
                icon = Icons.Default.Bolt,
                label = "Briefing",
                iconTint = StarkGoldBright,
                isActive = false,
                modifier = Modifier.weight(1f),
                onClick = {
                    viewModel.executeAutonomousMission("MORNING_BRIEFING")
                    viewModel.setActivePanel("CHAT")
                }
            )

            ActionPillChip(
                icon = Icons.Default.FlashlightOn,
                label = if (isFlashlightOn) "Torch: ON" else "Torch",
                iconTint = if (isFlashlightOn) SuccessEmerald else StarkGoldBright,
                isActive = isFlashlightOn,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.toggleFlashlight() }
            )

            ActionPillChip(
                icon = Icons.Default.Shield,
                label = "HW Audit",
                iconTint = StarkGoldBright,
                isActive = false,
                modifier = Modifier.weight(1f),
                onClick = {
                    viewModel.executeAutonomousMission("HARDWARE_AUDIT")
                    viewModel.setActivePanel("CHAT")
                }
            )

            ActionPillChip(
                icon = Icons.Default.Folder,
                label = "Files / IMG",
                iconTint = StarkGoldBright,
                isActive = false,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.setActivePanel("CHAT") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // =========================================================================
        // ROW 6: LOWER TELEMETRY DECK
        // (NEURAL CORE Brain Card | J.A.R.V.I.S. Hologram Pedestal | ACTIVITY LOG Card)
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(114.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Left: NEURAL CORE Card (Tapping opens Voice Biometrics Modal)
            NeuralCoreBrainCard(
                onClick = onOpenBiometrics,
                modifier = Modifier
                    .weight(0.39f)
                    .fillMaxHeight()
            )

            // Center: Holographic Emitter Ring + "J.A.R.V.I.S. MORE THAN AN AI"
            CenterJarvisEmitterPedestal(
                modifier = Modifier
                    .weight(0.24f)
                    .fillMaxHeight()
            )

            // Right: ACTIVITY LOG Card
            ActivityLogHudCard(
                modifier = Modifier
                    .weight(0.37f)
                    .fillMaxHeight()
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}

/**
 * Left Column of Upper Hero Row:
 * - SYSTEM STATUS card (CPU, RAM, Battery, Network)
 * - Mini Weather Pod (29°C Clear Sky)
 */
@Composable
private fun SystemStatusAndMiniWeatherColumn(
    cpuPercent: Int,
    ramPercent: Int,
    batteryPercent: Int,
    weatherTemp: Int,
    weatherCondition: String,
    onWeatherClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = remember { chamferedShape(16f) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // 1. SYSTEM STATUS Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.73f)
                .clip(shape)
                .background(StarkPanelBg)
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(StarkGold, StarkCyan.copy(alpha = 0.7f), StarkCyan.copy(alpha = 0.35f))
                    ),
                    shape = shape
                )
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header: SYSTEM STATUS + ECG pulse line
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "SYSTEM STATUS",
                        color = StarkCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                    EcgWaveformIcon(modifier = Modifier.size(width = 22.dp, height = 12.dp))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.8.dp)
                        .background(StarkCyan.copy(alpha = 0.25f))
                )

                SystemTelemetryRow(
                    icon = Icons.Default.Memory,
                    label = "CPU",
                    value = "$cpuPercent%",
                    valueColor = HologramTextBright
                )
                SystemTelemetryRow(
                    icon = Icons.Default.Storage,
                    label = "RAM",
                    value = "$ramPercent%",
                    valueColor = HologramTextBright
                )
                SystemTelemetryRow(
                    icon = Icons.Default.BatteryChargingFull,
                    label = "Battery",
                    value = "$batteryPercent%",
                    valueColor = HologramTextBright
                )
                SystemTelemetryRow(
                    icon = Icons.Default.Wifi,
                    label = "Network",
                    value = "Excellent",
                    valueColor = SuccessEmerald
                )
            }
        }

        // 2. Mini Weather Pod below System Status
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.27f)
                .clip(shape)
                .background(StarkPanelBg)
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(StarkGold.copy(alpha = 0.7f), StarkCyan.copy(alpha = 0.6f))
                    ),
                    shape = shape
                )
                .clickable(onClick = onWeatherClick)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Icon(
                        imageVector = Icons.Default.WbSunny,
                        contentDescription = "Sun",
                        tint = StarkGoldBright,
                        modifier = Modifier.size(22.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.CloudQueue,
                        contentDescription = "Cloud",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Column {
                    Text(
                        text = "$weatherTemp°C",
                        color = HologramTextBright,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = weatherCondition,
                        color = HologramTextMuted,
                        fontSize = 8.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun EcgWaveformIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val midY = h * 0.55f
        val path = Path().apply {
            moveTo(0f, midY)
            lineTo(w * 0.25f, midY)
            lineTo(w * 0.38f, h * 0.15f)
            lineTo(w * 0.52f, h * 0.90f)
            lineTo(w * 0.66f, h * 0.30f)
            lineTo(w * 0.78f, midY)
            lineTo(w, midY)
        }
        drawPath(
            path = path,
            color = StarkCyan,
            style = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
private fun SystemTelemetryRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = StarkCyan,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                color = HologramTextDim,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = value,
            color = valueColor,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Right Column of Upper Hero Row:
 * CORE MODE Selector Card (Assistant / Companion / Focus) with subtle holographic armor accent.
 */
@Composable
private fun CoreModeSelectorCard(
    activePersona: JarvisPersona,
    onSelectPersona: (JarvisPersona) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = remember { chamferedShape(16f) }

    val selectedModeIndex = when (activePersona) {
        JarvisPersona.GIRLFRIEND, JarvisPersona.BOYFRIEND -> 1
        JarvisPersona.FRIDAY -> 2
        else -> 0
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(StarkPanelBg)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(StarkCyan.copy(alpha = 0.8f), StarkGold.copy(alpha = 0.6f), StarkCyan.copy(alpha = 0.4f))
                ),
                shape = shape
            )
    ) {
        // Subtle glowing cyan Iron Man helmet/chest reactor wireframe silhouette on right edge
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Chest arc reactor glow on right silhouette
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        StarkCyan.copy(alpha = 0.32f),
                        StarkElectricBlue.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.86f, h * 0.68f),
                    radius = w * 0.26f
                ),
                radius = w * 0.26f,
                center = Offset(w * 0.86f, h * 0.68f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 7.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "CORE MODE",
                color = StarkCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(start = 2.dp)
            )

            // 1. Assistant Mode
            CoreModeItemRow(
                title = "Assistant",
                subtitle = "Always here",
                isSelected = selectedModeIndex == 0,
                isWaveformIcon = true,
                icon = Icons.Default.Bolt,
                onClick = { onSelectPersona(JarvisPersona.JARVIS_PRO) }
            )

            // 2. Companion Mode
            CoreModeItemRow(
                title = "Companion",
                subtitle = "With you always",
                isSelected = selectedModeIndex == 1,
                isWaveformIcon = false,
                icon = Icons.Default.FavoriteBorder,
                onClick = { onSelectPersona(JarvisPersona.GIRLFRIEND) }
            )

            // 3. Focus Mode
            CoreModeItemRow(
                title = "Focus",
                subtitle = "Be productive",
                isSelected = selectedModeIndex == 2,
                isWaveformIcon = false,
                icon = Icons.Default.GpsFixed,
                onClick = { onSelectPersona(JarvisPersona.FRIDAY) }
            )
        }
    }
}

@Composable
private fun CoreModeItemRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    isWaveformIcon: Boolean,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val bgBrush = if (isSelected) {
        Brush.horizontalGradient(
            colors = listOf(
                StarkGold.copy(alpha = 0.26f),
                StarkGold.copy(alpha = 0.08f)
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0x66041022),
                Color(0x33041022)
            )
        )
    }
    val borderColor = if (isSelected) StarkGoldBright else StarkCyan.copy(alpha = 0.25f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(bgBrush)
            .border(1.dp, borderColor, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isWaveformIcon) {
            EcgWaveformIcon(
                modifier = Modifier.size(width = 16.dp, height = 14.dp)
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) StarkGoldBright else StarkCyan,
                modifier = Modifier.size(15.dp)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = if (isSelected) StarkGoldBright else HologramTextBright,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = subtitle,
                    color = if (isSelected) StarkGoldBright.copy(alpha = 0.85f) else HologramTextMuted,
                    fontSize = 7.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = StarkGoldBright,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
    }
}

/**
 * 3D Rotating Holographic Earth Globe on glowing emitter pedestal (Middle Row Left).
 */
@Composable
private fun HolographicEarthGlobe(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "globe_rotation")
    val globeSpin by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "globe_spin"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val globeCenter = Offset(w * 0.5f, h * 0.45f)
            val globeRadius = (minOf(w, h * 0.82f) * 0.42f)

            // 1. Pedestal base under globe
            val baseY = h * 0.89f
            for (i in 0..3) {
                val factor = 1f - i * 0.2f
                val bw = globeRadius * 2.0f * factor
                val bh = globeRadius * 0.36f * factor
                drawOval(
                    color = StarkCyan.copy(alpha = 0.35f + i * 0.15f),
                    topLeft = Offset(globeCenter.x - bw / 2f, baseY - bh / 2f),
                    size = Size(bw, bh),
                    style = Stroke(width = if (i == 1) 2.2f else 1.2f)
                )
            }

            // Glow from pedestal into bottom of globe
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.7f),
                        StarkCyan.copy(alpha = 0.45f),
                        Color.Transparent
                    ),
                    center = Offset(globeCenter.x, baseY - 2f),
                    radius = globeRadius * 0.65f
                ),
                radius = globeRadius * 0.65f,
                center = Offset(globeCenter.x, baseY - 2f)
            )

            // 2. Outer atmospheric aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        StarkElectricBlue.copy(alpha = 0.35f),
                        StarkCyan.copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    center = globeCenter,
                    radius = globeRadius * 1.25f
                ),
                radius = globeRadius * 1.25f,
                center = globeCenter
            )

            // 3. Globe dark ocean sphere fill
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0A2A5E),
                        Color(0xFF04122C),
                        Color(0xFF020816)
                    ),
                    center = Offset(globeCenter.x - globeRadius * 0.25f, globeCenter.y - globeRadius * 0.25f),
                    radius = globeRadius * 1.2f
                ),
                radius = globeRadius,
                center = globeCenter
            )

            // 4. Latitude & Longitude wireframe grid lines
            val latAngles = listOf(-0.55f, -0.28f, 0f, 0.28f, 0.55f)
            for (lat in latAngles) {
                val yOff = globeRadius * sin(lat)
                val rx = globeRadius * cos(lat)
                val ry = rx * 0.22f
                drawOval(
                    color = StarkCyan.copy(alpha = 0.32f),
                    topLeft = Offset(globeCenter.x - rx, globeCenter.y + yOff - ry),
                    size = Size(rx * 2f, ry * 2f),
                    style = Stroke(width = 1.0f)
                )
            }

            for (lonIdx in 0 until 6) {
                val angle = globeSpin + lonIdx * (PI.toFloat() / 6f)
                val rx = (globeRadius * abs(cos(angle))).coerceAtLeast(2f)
                drawOval(
                    color = StarkCyan.copy(alpha = 0.28f),
                    topLeft = Offset(globeCenter.x - rx, globeCenter.y - globeRadius),
                    size = Size(rx * 2f, globeRadius * 2f),
                    style = Stroke(width = 1.0f)
                )
            }

            // 5. Glowing Golden-Amber Continental Landmass Particle Cluster
            val continentSeeds = listOf(
                // Americas & Europe/Asia stylized landmass points (lat, lon)
                -0.35f to 0.2f, -0.25f to 0.1f, -0.15f to 0.0f, -0.45f to 0.3f,
                0.15f to 0.35f, 0.25f to 0.42f, 0.32f to 0.50f, 0.10f to 0.52f,
                0.22f to 1.2f, 0.35f to 1.35f, 0.15f to 1.45f, 0.42f to 1.6f,
                -0.10f to 1.3f, -0.25f to 1.4f, 0.05f to 1.75f, 0.28f to 1.9f,
                0.18f to 2.1f, 0.30f to 2.25f, -0.20f to 2.6f, 0.12f to 2.8f,
                -0.30f to 3.4f, -0.15f to 3.55f, 0.20f to 3.8f, 0.38f to 4.0f,
                0.08f to 4.3f, -0.12f to 4.6f, 0.25f to 5.0f, -0.22f to 5.4f
            )
            for ((lat, lonBase) in continentSeeds) {
                val lon = lonBase + globeSpin
                val z = cos(lat) * cos(lon)
                if (z > -0.1f) {
                    val x = globeCenter.x + globeRadius * 0.88f * cos(lat) * sin(lon)
                    val y = globeCenter.y - globeRadius * 0.88f * sin(lat)
                    drawCircle(
                        color = StarkGoldBright.copy(alpha = (0.45f + z * 0.55f).coerceIn(0.2f, 0.95f)),
                        radius = 2.4f + z * 1.6f,
                        center = Offset(x, y)
                    )
                }
            }

            // 6. Outer glowing cyan rim & tilted satellite orbital rings
            drawCircle(
                color = StarkCyan.copy(alpha = 0.9f),
                radius = globeRadius,
                center = globeCenter,
                style = Stroke(width = 2.0f)
            )

            // Tilted orbital ring 1
            drawOval(
                color = StarkCyan.copy(alpha = 0.55f),
                topLeft = Offset(globeCenter.x - globeRadius * 1.22f, globeCenter.y - globeRadius * 0.36f),
                size = Size(globeRadius * 2.44f, globeRadius * 0.72f),
                style = Stroke(width = 1.4f)
            )
        }
    }
}

/**
 * Center Card of Middle Row:
 * LOCATION (Bhadohi, India + Lat/Lon) & WEATHER (29°C Clear Sky H:31° L:19°)
 */
@Composable
private fun LocationAndWeatherCenterCard(
    city: String,
    latitude: Double,
    longitude: Double,
    temperature: Int,
    condition: String,
    high: Int,
    low: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = remember { chamferedShape(14f) }
    val latDir = if (latitude >= 0) "N" else "S"
    val lonDir = if (longitude >= 0) "E" else "W"
    val latFormatted = String.format(Locale.US, "%.4f° %s", abs(latitude), latDir)
    val lonFormatted = String.format(Locale.US, "%.4f° %s", abs(longitude), lonDir)
    val displayCity = if (city.contains(",")) city else "$city, India"

    Box(
        modifier = modifier
            .clip(shape)
            .background(StarkPanelBg)
            .border(1.dp, StarkCyan.copy(alpha = 0.65f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 5.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top section: LOCATION
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Location",
                    tint = StarkCyan,
                    modifier = Modifier
                        .size(14.dp)
                        .padding(top = 1.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Column {
                    Text(
                        text = "LOCATION",
                        color = StarkCyan,
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = displayCity,
                        color = HologramTextBright,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = latFormatted,
                        color = HologramTextDim,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = lonFormatted,
                        color = HologramTextDim,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Bottom section: WEATHER
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.WbSunny,
                    contentDescription = "Weather",
                    tint = StarkGoldBright,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Column {
                    Text(
                        text = "WEATHER",
                        color = StarkCyan,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "$temperature°C",
                        color = HologramTextBright,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "$condition  H:$high° L:$low°",
                        color = HologramTextDim,
                        fontSize = 7.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Right Card of Middle Row:
 * QUICK CONTROLS (Camera, Torch, Settings, Files) in circular glowing buttons.
 */
@Composable
private fun QuickControlsHudCard(
    isCameraActive: Boolean,
    isTorchActive: Boolean,
    onCameraClick: () -> Unit,
    onTorchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onFilesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = remember { chamferedShape(16f) }

    Box(
        modifier = modifier
            .clip(shape)
            .background(StarkPanelBg)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(StarkGold, StarkCyan.copy(alpha = 0.7f), StarkGold)
                ),
                shape = shape
            )
            .padding(horizontal = 8.dp, vertical = 7.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "QUICK CONTROLS",
                color = StarkCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickControlCircleButton(
                    icon = Icons.Default.CameraAlt,
                    label = "Camera",
                    isActive = isCameraActive,
                    onClick = onCameraClick
                )
                QuickControlCircleButton(
                    icon = Icons.Default.FlashlightOn,
                    label = "Torch",
                    isActive = isTorchActive,
                    onClick = onTorchClick
                )
                QuickControlCircleButton(
                    icon = Icons.Default.Settings,
                    label = "Settings",
                    isActive = false,
                    onClick = onSettingsClick
                )
                QuickControlCircleButton(
                    icon = Icons.Default.Folder,
                    label = "Files",
                    isActive = false,
                    onClick = onFilesClick
                )
            }
        }
    }
}

@Composable
private fun QuickControlCircleButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val ringColor = if (isActive) StarkGoldBright else StarkCyan
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ringColor.copy(alpha = 0.28f),
                            StarkPanelDark
                        )
                    )
                )
                .border(1.5.dp, ringColor.copy(alpha = 0.85f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = ringColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = HologramTextBright,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * ROW 3: Full-Width Bevelled Voice Command Banner with Central Glowing Microphone Button
 * Left: Animated cyan vertical soundwave bars
 * Center: Glowing circular Mic button
 * Right: "Tap or say..." / "\"Jarvis, open YouTube\""
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BevelledVoiceCommandBar(
    isMicLockedOn: Boolean,
    voiceState: VoiceState,
    audioAmplitude: Float,
    statusText: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isActive = isMicLockedOn || voiceState == VoiceState.LISTENING || voiceState == VoiceState.SPEAKING || voiceState == VoiceState.THINKING
    val infiniteTransition = rememberInfiniteTransition(label = "voice_bar_wave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val titlePrompt = when {
        voiceState == VoiceState.SPEAKING -> "JARVIS Speaking..."
        voiceState == VoiceState.THINKING -> "Neural Computing..."
        isMicLockedOn || voiceState == VoiceState.LISTENING -> "Listening..."
        else -> "Tap or say..."
    }
    val subtitlePrompt = when {
        isActive -> statusText
        else -> "\"Jarvis, open YouTube\""
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        contentAlignment = Alignment.Center
    ) {
        // Horizontal chamfered wing banner behind the central mic orb
        val bannerShape = remember { chamferedShape(20f) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(bannerShape)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xEE041022),
                            Color(0xF5071E3D),
                            Color(0xEE041022)
                        )
                    )
                )
                .border(
                    width = 1.2.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            StarkGold,
                            StarkCyan.copy(alpha = 0.85f),
                            StarkCyan,
                            StarkCyan.copy(alpha = 0.85f),
                            StarkGold
                        )
                    ),
                    shape = bannerShape
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left side: Animated cyan soundwave equalizer bars
                Canvas(
                    modifier = Modifier
                        .weight(0.38f)
                        .height(28.dp)
                ) {
                    val barCount = 13
                    val spacing = size.width / (barCount + 2)
                    val midY = size.height / 2f
                    val baseHeights = floatArrayOf(0.22f, 0.35f, 0.55f, 0.40f, 0.78f, 0.95f, 0.65f, 0.88f, 0.48f, 0.68f, 0.36f, 0.25f, 0.18f)
                    for (i in 0 until barCount) {
                        val x = spacing * (i + 1)
                        val animFactor = if (isActive) {
                            0.55f + 0.45f * abs(sin(wavePhase + i * 0.55f)) + audioAmplitude * 0.4f
                        } else {
                            0.75f + 0.25f * abs(sin(wavePhase * 0.5f + i * 0.4f))
                        }
                        val barH = (size.height * baseHeights[i] * animFactor).coerceIn(4f, size.height)
                        drawLine(
                            color = if (isActive && isMicLockedOn) SuccessEmerald else StarkCyan,
                            start = Offset(x, midY - barH / 2f),
                            end = Offset(x, midY + barH / 2f),
                            strokeWidth = 3.2f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Center spacer for Mic Orb
                Spacer(modifier = Modifier.width(68.dp))

                // Right side: "Tap or say..." + "\"Jarvis, open YouTube\""
                Column(
                    modifier = Modifier.weight(0.42f),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = titlePrompt,
                        color = if (isMicLockedOn) SuccessEmerald else HologramTextBright,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitlePrompt,
                        color = HologramTextDim,
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Central Floating Multi-Ring Glowing Microphone Button
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                val ringCol = if (isMicLockedOn) SuccessEmerald else StarkCyan

                // Outer radial halo
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ringCol.copy(alpha = 0.45f),
                            StarkPanelDark.copy(alpha = 0.95f)
                        ),
                        center = c,
                        radius = r
                    ),
                    radius = r,
                    center = c
                )

                // Outer glowing cyan ring
                drawCircle(
                    color = ringCol,
                    radius = r * 0.94f,
                    center = c,
                    style = Stroke(width = 2.4f)
                )

                // Inner ring
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = r * 0.74f,
                    center = c,
                    style = Stroke(width = 1.6f)
                )
            }

            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Toggle Voice Microphone",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/**
 * ROW 4: Hexagonal / Chamfered Feature Tile (Memories, Language, Plugins, Inbuilt Maps, Screen)
 */
@Composable
private fun HexFeatureTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    subtitleColor: Color,
    isGoldHighlighted: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = remember { chamferedShape(14f) }
    val borderBrush = if (isGoldHighlighted) {
        Brush.verticalGradient(colors = listOf(StarkGoldBright, StarkGold, StarkGoldBright))
    } else {
        Brush.verticalGradient(colors = listOf(StarkCyan.copy(alpha = 0.85f), StarkElectricBlue.copy(alpha = 0.45f)))
    }
    val bgBrush = if (isGoldHighlighted) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xEE261905),
                Color(0xEE120C03)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                StarkPanelBg,
                StarkPanelDark
            )
        )
    }

    Box(
        modifier = modifier
            .height(68.dp)
            .clip(shape)
            .background(bgBrush)
            .border(width = if (isGoldHighlighted) 1.5.dp else 1.dp, brush = borderBrush, shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = StarkCyan,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = title,
                color = HologramTextBright,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                color = subtitleColor,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * ROW 5: Chamfered Action Pill Chip (Briefing, Torch, HW Audit, Files / IMG)
 */
@Composable
private fun ActionPillChip(
    icon: ImageVector,
    label: String,
    iconTint: Color,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = remember { chamferedShape(10f) }
    val borderColor = if (isActive) SuccessEmerald else StarkCyan.copy(alpha = 0.65f)

    Box(
        modifier = modifier
            .height(32.dp)
            .clip(shape)
            .background(StarkPanelBg)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = HologramTextBright,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * ROW 6 Left: NEURAL CORE Card with Glowing Wireframe Brain Illustration & 4 Status Dots
 */
@Composable
private fun NeuralCoreBrainCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = remember { chamferedShape(16f) }

    Box(
        modifier = modifier
            .clip(shape)
            .background(StarkPanelBg)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(StarkGold, StarkCyan.copy(alpha = 0.8f), StarkGold.copy(alpha = 0.5f))
                ),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "NEURAL CORE",
                color = StarkCyan,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.6.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Glowing Cyan Wireframe Brain Canvas
                HolographicBrainCanvas(
                    modifier = Modifier
                        .weight(0.44f)
                        .fillMaxHeight()
                )

                Spacer(modifier = Modifier.width(4.dp))

                // 4 Green Status Indicators
                Column(
                    modifier = Modifier.weight(0.56f),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    NeuralStatusBullet(text = "Low Latency", textColor = SuccessEmerald)
                    NeuralStatusBullet(text = "High Precision", textColor = HologramTextBright)
                    NeuralStatusBullet(text = "Adaptive Learning", textColor = HologramTextBright)
                    NeuralStatusBullet(text = "Ready", textColor = SuccessEmerald)
                }
            }
        }
    }
}

@Composable
private fun HolographicBrainCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val cy = h * 0.48f
        val rx = w * 0.44f
        val ry = h * 0.36f

        // Background brain aura
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    StarkElectricBlue.copy(alpha = 0.45f),
                    StarkCyan.copy(alpha = 0.15f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = maxOf(rx, ry) * 1.25f
            ),
            radius = maxOf(rx, ry) * 1.25f,
            center = Offset(cx, cy)
        )

        // Brain cerebral cortex outline path
        val brainPath = Path().apply {
            moveTo(cx - rx * 0.85f, cy + ry * 0.15f)
            cubicTo(
                cx - rx * 1.05f, cy - ry * 0.55f,
                cx - rx * 0.45f, cy - ry * 1.05f,
                cx + rx * 0.05f, cy - ry * 0.95f
            )
            cubicTo(
                cx + rx * 0.65f, cy - ry * 1.0f,
                cx + rx * 1.05f, cy - ry * 0.35f,
                cx + rx * 0.90f, cy + ry * 0.25f
            )
            cubicTo(
                cx + rx * 0.75f, cy + ry * 0.75f,
                cx + rx * 0.25f, cy + ry * 0.80f,
                cx, cy + ry * 0.55f
            )
            cubicTo(
                cx - rx * 0.35f, cy + ry * 0.65f,
                cx - rx * 0.75f, cy + ry * 0.50f,
                cx - rx * 0.85f, cy + ry * 0.15f
            )
            close()
        }

        drawPath(
            path = brainPath,
            color = StarkElectricBlue.copy(alpha = 0.35f)
        )
        drawPath(
            path = brainPath,
            color = StarkCyan,
            style = Stroke(width = 2.0f)
        )

        // Internal synaptic gyri folds & glowing nodes
        val nodes = listOf(
            Offset(cx - rx * 0.45f, cy - ry * 0.35f),
            Offset(cx - rx * 0.10f, cy - ry * 0.55f),
            Offset(cx + rx * 0.35f, cy - ry * 0.40f),
            Offset(cx + rx * 0.55f, cy + ry * 0.05f),
            Offset(cx + rx * 0.15f, cy + ry * 0.15f),
            Offset(cx - rx * 0.35f, cy + ry * 0.18f),
            Offset(cx, cy - ry * 0.10f)
        )
        for (i in 0 until nodes.size - 1) {
            drawLine(
                color = StarkCyan.copy(alpha = 0.75f),
                start = nodes[i],
                end = nodes[i + 1],
                strokeWidth = 1.4f
            )
            drawLine(
                color = StarkCyan.copy(alpha = 0.5f),
                start = nodes[i],
                end = nodes.last(),
                strokeWidth = 1.1f
            )
        }
        nodes.forEach { pt ->
            drawCircle(color = Color.White, radius = 2.2f, center = pt)
        }

        // Brainstem projection lines
        drawLine(
            color = StarkCyan.copy(alpha = 0.8f),
            start = Offset(cx, cy + ry * 0.55f),
            end = Offset(cx - rx * 0.12f, h * 0.94f),
            strokeWidth = 2.2f
        )
        drawLine(
            color = StarkCyan.copy(alpha = 0.6f),
            start = Offset(cx + rx * 0.08f, cy + ry * 0.55f),
            end = Offset(cx + rx * 0.04f, h * 0.94f),
            strokeWidth = 1.5f
        )
    }
}

@Composable
private fun NeuralStatusBullet(
    text: String,
    textColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(SuccessEmerald)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = text,
            color = textColor,
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * ROW 6 Center: Holographic Emitter Pedestal + "J.A.R.V.I.S. MORE THAN AN AI"
 */
@Composable
private fun CenterJarvisEmitterPedestal(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val centerY = h * 0.66f

                // Vertical light pillar rising from center emitter
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            StarkCyan.copy(alpha = 0.25f),
                            Color.White.copy(alpha = 0.65f),
                            StarkCyan.copy(alpha = 0.15f)
                        ),
                        startY = 0f,
                        endY = centerY
                    ),
                    topLeft = Offset(w * 0.38f, 0f),
                    size = Size(w * 0.24f, centerY)
                )

                // Concentric glowing cyan holographic emitter discs
                val rings = listOf(0.95f, 0.74f, 0.52f, 0.30f)
                rings.forEachIndexed { idx, factor ->
                    val rw = w * factor
                    val rh = h * 0.34f * factor
                    drawOval(
                        color = if (idx % 2 == 0) StarkCyan.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.75f),
                        topLeft = Offset((w - rw) / 2f, centerY - rh / 2f),
                        size = Size(rw, rh),
                        style = Stroke(width = if (idx == 0) 2.2f else 1.4f)
                    )
                }
            }
        }

        Text(
            text = "J. A. R. V. I. S.",
            color = HologramTextBright,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.2.sp,
            maxLines = 1
        )
        Text(
            text = "— MORE THAN AN AI —",
            color = HologramTextDim,
            fontSize = 6.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            maxLines = 1
        )
    }
}

/**
 * ROW 6 Right: ACTIVITY LOG Card with live timestamps
 */
@Composable
private fun ActivityLogHudCard(modifier: Modifier = Modifier) {
    val shape = remember { chamferedShape(16f) }
    val baseTimeMillis = remember { System.currentTimeMillis() }
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val logs = remember(baseTimeMillis) {
        listOf(
            timeFormatter.format(Date(baseTimeMillis)) to "System Ready",
            timeFormatter.format(Date(baseTimeMillis - 60_000L)) to "Maps Loaded",
            timeFormatter.format(Date(baseTimeMillis - 120_000L)) to "Network Connected",
            timeFormatter.format(Date(baseTimeMillis - 180_000L)) to "Voice Engine Active"
        )
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(StarkPanelBg)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(StarkCyan.copy(alpha = 0.8f), StarkGold, StarkGoldBright)
                ),
                shape = shape
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "ACTIVITY LOG",
                color = StarkCyan,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.6.sp
            )

            logs.forEach { (timeStr, eventLabel) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(StarkCyan)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = timeStr,
                        color = HologramTextDim,
                        fontSize = 7.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = eventLabel,
                        color = HologramTextBright,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * 5. Futuristic Chamfered Bottom Navigation Bar with Central Metallic Avengers 'A' Emblem
 * Items: Home | Chat | [Avengers 'A' Core Emblem] | Tools | Updates
 */
@Composable
private fun FuturisticBottomNavBar(
    activePanel: String,
    isMicLockedOn: Boolean,
    voiceState: VoiceState,
    accentColor: Color,
    onSelectPanel: (String) -> Unit,
    onCenterEmblemClick: () -> Unit
) {
    val isHomeSelected = activePanel == "HOME" || activePanel == "CORE"
    val isChatSelected = activePanel == "CHAT" || activePanel == "TERMINAL"
    val isToolsSelected = activePanel == "SETTINGS"
    val isUpdatesSelected = activePanel == "UPDATES"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 1. Home
            ChamferedBottomNavButton(
                icon = Icons.Default.Home,
                label = "Home",
                isSelected = isHomeSelected,
                modifier = Modifier.weight(1f),
                onClick = { onSelectPanel("HOME") }
            )

            // 2. Chat
            ChamferedBottomNavButton(
                icon = Icons.AutoMirrored.Filled.Chat,
                label = "Chat",
                isSelected = isChatSelected,
                modifier = Modifier.weight(1f),
                onClick = { onSelectPanel("CHAT") }
            )

            // Center Spacer for Metallic Emblem
            Spacer(modifier = Modifier.width(64.dp))

            // 4. Tools
            ChamferedBottomNavButton(
                icon = Icons.Default.GridView,
                label = "Tools",
                isSelected = isToolsSelected,
                modifier = Modifier.weight(1f),
                onClick = { onSelectPanel("SETTINGS") }
            )

            // 5. Updates
            ChamferedBottomNavButton(
                icon = Icons.Default.SystemUpdateAlt,
                label = "Updates",
                isSelected = isUpdatesSelected,
                modifier = Modifier.weight(1f),
                onClick = { onSelectPanel("UPDATES") }
            )
        }

        // 3. Central Metallic Avengers 'A' / Jarvis Core Emblem Button
        AvengersCoreEmblemButton(
            isActive = isMicLockedOn || voiceState == VoiceState.LISTENING || voiceState == VoiceState.SPEAKING,
            onClick = onCenterEmblemClick
        )
    }
}

@Composable
private fun ChamferedBottomNavButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = remember { chamferedShape(14f) }
    val borderBrush = if (isSelected) {
        Brush.verticalGradient(colors = listOf(StarkGoldBright, StarkGold, StarkGoldBright))
    } else {
        Brush.verticalGradient(colors = listOf(StarkCyan.copy(alpha = 0.55f), StarkCyan.copy(alpha = 0.25f)))
    }
    val bgBrush = if (isSelected) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xEE2B1B04),
                Color(0xEE120B02)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                StarkPanelBg,
                StarkPanelDark
            )
        )
    }

    Box(
        modifier = modifier
            .height(52.dp)
            .minimumInteractiveComponentSize()
            .clip(shape)
            .background(bgBrush)
            .border(width = if (isSelected) 1.5.dp else 1.dp, brush = borderBrush, shape = shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) StarkGoldBright else StarkCyan.copy(alpha = 0.85f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = if (isSelected) StarkGoldBright else HologramTextBright,
                fontSize = 9.5.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun AvengersCoreEmblemButton(
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)

            // Dark metallic core background
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1A283B),
                        Color(0xFF09101D),
                        Color(0xFF040811)
                    ),
                    center = c,
                    radius = r
                ),
                radius = r * 0.96f,
                center = c
            )

            // Outer metallic gold-bronze segmented bezel
            drawCircle(
                color = if (isActive) SuccessEmerald else StarkGold,
                radius = r * 0.94f,
                center = c,
                style = Stroke(width = 3.0f)
            )

            // Inner silver-cyan ring
            drawCircle(
                color = Color(0xFFB0D8F0),
                radius = r * 0.78f,
                center = c,
                style = Stroke(width = 2.2f)
            )

            // Stylized metallic 'A' insignia inside the ring
            val aPath = Path().apply {
                // Left leg of A
                moveTo(c.x + r * 0.06f, c.y - r * 0.52f)
                lineTo(c.x - r * 0.34f, c.y + r * 0.46f)
                lineTo(c.x - r * 0.16f, c.y + r * 0.46f)
                lineTo(c.x + r * 0.06f, c.y - r * 0.12f)
                // Right vertical leg of A
                lineTo(c.x + r * 0.06f, c.y + r * 0.44f)
                lineTo(c.x + r * 0.24f, c.y + r * 0.44f)
                lineTo(c.x + r * 0.24f, c.y - r * 0.52f)
                close()
            }
            drawPath(
                path = aPath,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White,
                        Color(0xFFA8C6DF),
                        Color(0xFF6B8BA8)
                    )
                )
            )

            // Crossbar + Arrowhead of the 'A'
            val arrowBar = Path().apply {
                moveTo(c.x - r * 0.22f, c.y + r * 0.12f)
                lineTo(c.x + r * 0.18f, c.y - r * 0.02f)
                lineTo(c.x + r * 0.18f, c.y - r * 0.14f)
                lineTo(c.x + r * 0.36f, c.y + r * 0.02f)
                lineTo(c.x + r * 0.18f, c.y + r * 0.20f)
                lineTo(c.x + r * 0.18f, c.y + r * 0.09f)
                lineTo(c.x - r * 0.26f, c.y + r * 0.24f)
                close()
            }
            drawPath(
                path = arrowBar,
                color = Color.White
            )
        }
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
