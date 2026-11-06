package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.hud.HudBadge
import com.example.ui.hud.HudButton
import com.example.ui.theme.GlassCardBackground
import com.example.ui.theme.HologramTextBright
import com.example.ui.theme.HologramTextDim
import com.example.ui.theme.HologramTextMuted
import com.example.ui.theme.SuccessEmerald
import com.example.viewmodel.JarvisViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun UpdatesScreen(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    val accentColor = viewModel.getAccentColor()
    val scope = rememberCoroutineScope()

    var isCheckingUpdates by remember { mutableStateOf(false) }
    var showUpToDateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("updates_screen")
            .padding(14.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f))
                    .border(1.dp, accentColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = "Updates",
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "SYSTEM FIRMWARE & NEURAL UPDATES",
                    color = accentColor,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Mark-LIV Neural Kernel // Device OS Compatibility",
                    color = HologramTextMuted,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Check for updates button
        HudButton(
            text = if (isCheckingUpdates) "CONNECTING TO SECURE OTA REPOSITORY..." else "CHECK FOR FIRMWARE UPDATES",
            onClick = {
                if (!isCheckingUpdates) {
                    isCheckingUpdates = true
                    scope.launch {
                        delay(1400)
                        isCheckingUpdates = false
                        showUpToDateDialog = true
                    }
                }
            },
            accentColor = accentColor,
            icon = if (isCheckingUpdates) null else Icons.Default.Refresh
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Changelog List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Version 3.5.0
            item {
                ReleaseCard(
                    version = "v3.5.0 - PRODUCTION RELEASE",
                    date = "October 2026",
                    badge = "LATEST • INSTALLED",
                    badgeColor = SuccessEmerald,
                    accentColor = accentColor,
                    features = listOf(
                        "Dark futuristic glassmorphism UI with 3D interactive particle visualizer.",
                        "Multi-Persona Voice Engine: JARVIS Pro, Girlfriend Mode, Cyberpunk, Friday, Scholar.",
                        "Owner Voice Biometrics with dedicated operator recognition for Ali Rahman.",
                        "Android 14-16 Foreground Service resilience with dynamic FGS permissions.",
                        "Real-time Gemini Live camera vision and cross-app background screen vision.",
                        "Full hands-free autonomous device automation (YouTube, WhatsApp, Calling, UI actions)."
                    )
                )
            }

            // Version 3.2.0
            item {
                ReleaseCard(
                    version = "v3.2.0 - LIVE VISION & ACCESSIBILITY MATRIX",
                    date = "September 2026",
                    badge = "STABLE",
                    badgeColor = accentColor,
                    accentColor = accentColor,
                    features = listOf(
                        "Real-time CameraX frame pipeline with inline JPEG compression.",
                        "AccessibilityService screenshot capture engine for background screen vision.",
                        "Dynamic FGS type adaptation (microphone, camera, dataSync)."
                    )
                )
            }

            // Version 3.0.0
            item {
                ReleaseCard(
                    version = "v3.0.0 - NEURAL CORE INITIALIZATION",
                    date = "August 2026",
                    badge = "LEGACY",
                    badgeColor = HologramTextDim,
                    accentColor = accentColor,
                    features = listOf(
                        "Multi-model Gemini provider integration with function calling tools.",
                        "Low-latency continuous voice recognition loop and TTS audio stream.",
                        "Persistent long-term memory engine with Room SQLite database."
                    )
                )
            }
        }
    }

    if (showUpToDateDialog) {
        AlertDialog(
            onDismissRequest = { showUpToDateDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = "Up to date",
                        tint = SuccessEmerald,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "FIRMWARE UP TO DATE",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = HologramTextBright
                    )
                }
            },
            text = {
                Text(
                    text = "You are currently running the latest JARVIS Mark-LIV firmware (v3.5.0-PROD). All neural models, telemetry services, and accessibility layers are fully synchronized.",
                    color = HologramTextMuted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showUpToDateDialog = false }) {
                    Text("DISMISS", color = accentColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = GlassCardBackground,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun ReleaseCard(
    version: String,
    date: String,
    badge: String,
    badgeColor: Color,
    accentColor: Color,
    features: List<String>
) {
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
                Column {
                    Text(
                        text = version,
                        color = HologramTextBright,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = date,
                        color = HologramTextDim,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                HudBadge(
                    text = badge,
                    color = badgeColor
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            features.forEach { feat ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "•",
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = feat,
                        color = HologramTextMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
