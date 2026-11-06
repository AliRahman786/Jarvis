package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.GlassCardBackground
import com.example.ui.theme.HologramTextBright
import com.example.ui.theme.HologramTextDim
import com.example.ui.theme.HologramTextMuted
import com.example.ui.theme.HudCardSurface
import com.example.ui.theme.SuccessEmerald
import com.example.viewmodel.JarvisViewModel

data class NeuralPluginDescriptor(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val icon: ImageVector
)

private val NEURAL_PLUGIN_CATALOG = listOf(
    NeuralPluginDescriptor(
        id = "QUOTA_SHIELD",
        title = "API Quota Shield & 3-Variant Cascade",
        subtitle = "Zero-cost LRU response cache + automatic failover across Gemini 3.8 Flash, 3.8 Pro, and 3.8 Flash-Lite & backup providers.",
        badge = "CORE SHIELD",
        icon = Icons.Default.Shield
    ),
    NeuralPluginDescriptor(
        id = "VISION_DOC_ANALYZER",
        title = "Multimodal File, Code & Vision Analyzer",
        subtitle = "Attach images, screenshots, PDFs, JSON, CSV, and source code files for deep OCR, debugging, and synthesis.",
        badge = "MULTIMODAL",
        icon = Icons.Default.Description
    ),
    NeuralPluginDescriptor(
        id = "WEB_SEARCH",
        title = "Deep Web Grounding & Fact Engine",
        subtitle = "Live Google Search telemetry and real-time multi-source verification without leaving the HUD.",
        badge = "LIVE WEB",
        icon = Icons.Default.Public
    ),
    NeuralPluginDescriptor(
        id = "TACTICAL_MAPS",
        title = "Sat-Nav Radar & Route Telemetry",
        subtitle = "In-built slippy map renderer, geocoding, nearby POI radar, and turn-by-turn corridor vectors.",
        badge = "SAT-NAV",
        icon = Icons.Default.Map
    ),
    NeuralPluginDescriptor(
        id = "IMAGE_SYNTHESIS",
        title = "Vertex / Gemini Visual Synthesizer",
        subtitle = "High-resolution neural image generation via Gemini Multimodal & Imagen 3 pipelines.",
        badge = "IMAGEN",
        icon = Icons.Default.AutoAwesome
    ),
    NeuralPluginDescriptor(
        id = "QUANTUM_CODE_MATH",
        title = "Quantum Math, Logic & Code Architect",
        subtitle = "High-precision scientific math expression evaluator, unit converter, and syntax code synthesis.",
        badge = "COMPUTE",
        icon = Icons.Default.Calculate
    ),
    NeuralPluginDescriptor(
        id = "HARDWARE_CONTROL",
        title = "Android Hardware, Torch & Diagnostics",
        subtitle = "Direct hardware camera LED torch control, live RAM/Storage/CPU telemetry audit, and screen accessibility.",
        badge = "HARDWARE",
        icon = Icons.Default.Memory
    ),
    NeuralPluginDescriptor(
        id = "COMMUNICATIONS_OPS",
        title = "Autonomous Comms & App Dispatcher",
        subtitle = "Direct YouTube media playback, WhatsApp message dispatch, voice dialer, and Android app launcher.",
        badge = "AUTOMATION",
        icon = Icons.Default.PhoneAndroid
    )
)

@Composable
fun NeuralPluginsModal(
    viewModel: JarvisViewModel,
    accentColor: Color,
    onDismiss: () -> Unit,
    onNavigateToChat: () -> Unit
) {
    val enabledPlugins by viewModel.prefs.enabledPluginsFlow.collectAsState()
    val isFlashlightOn by viewModel.isFlashlightOn.collectAsState()
    val quotaStatus by viewModel.quotaShieldStatus.collectAsState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f))
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(HudCardSurface)
                    .border(1.5.dp, accentColor.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.18f))
                                .border(1.dp, accentColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Extension,
                                contentDescription = "Neural Plugins",
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "NEURAL PLUGINS & AGENT MATRIX",
                                color = HologramTextBright,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "${enabledPlugins.size} / ${NEURAL_PLUGIN_CATALOG.size} SUBSYSTEMS ACTIVE • $quotaStatus",
                                color = SuccessEmerald,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_plugins_modal")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = HologramTextBright
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Autonomous Mission Deck (1-Tap Smart Agent Actions)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(GlassCardBackground)
                        .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = "Autonomous Missions",
                                    tint = accentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "AUTONOMOUS AGENT MISSIONS (1-TAP EXECUTE)",
                                    color = accentColor,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MissionActionChip(
                                icon = Icons.Default.WbSunny,
                                label = "EXEC BRIEFING",
                                accentColor = accentColor,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewModel.executeAutonomousMission("MORNING_BRIEFING")
                                    onDismiss()
                                    onNavigateToChat()
                                }
                            )
                            MissionActionChip(
                                icon = Icons.Default.Memory,
                                label = "SYSTEM AUDIT",
                                accentColor = accentColor,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewModel.executeAutonomousMission("HARDWARE_AUDIT")
                                    onDismiss()
                                    onNavigateToChat()
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MissionActionChip(
                                icon = Icons.Default.FlashlightOn,
                                label = if (isFlashlightOn) "TORCH: ON" else "TORCH: OFF",
                                accentColor = if (isFlashlightOn) SuccessEmerald else accentColor,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewModel.executeAutonomousMission("TOGGLE_TORCH")
                                }
                            )
                            MissionActionChip(
                                icon = Icons.Default.ContentPaste,
                                label = "SCAN CLIPBOARD",
                                accentColor = accentColor,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewModel.executeAutonomousMission("CLIPBOARD_INSPECT")
                                    onDismiss()
                                    onNavigateToChat()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Plugins List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(NEURAL_PLUGIN_CATALOG, key = { it.id }) { plugin ->
                        val isEnabled = enabledPlugins.contains(plugin.id)
                        PluginRowCard(
                            plugin = plugin,
                            isEnabled = isEnabled,
                            accentColor = accentColor,
                            onToggle = { viewModel.toggleNeuralPlugin(plugin.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MissionActionChip(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accentColor.copy(alpha = 0.14f))
            .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accentColor,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = HologramTextBright,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PluginRowCard(
    plugin: NeuralPluginDescriptor,
    isEnabled: Boolean,
    accentColor: Color,
    onToggle: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isEnabled) GlassCardBackground else Color.Black.copy(alpha = 0.35f))
            .border(
                width = 1.dp,
                color = if (isEnabled) accentColor.copy(alpha = 0.45f) else HologramTextDim.copy(alpha = 0.2f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onToggle)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isEnabled) accentColor.copy(alpha = 0.18f) else Color.DarkGray.copy(alpha = 0.25f))
                        .border(1.dp, if (isEnabled) accentColor else HologramTextDim, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = plugin.icon,
                        contentDescription = plugin.title,
                        tint = if (isEnabled) accentColor else HologramTextDim,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = plugin.title,
                            color = if (isEnabled) HologramTextBright else HologramTextMuted,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isEnabled) SuccessEmerald.copy(alpha = 0.18f) else Color.DarkGray.copy(alpha = 0.3f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = plugin.badge,
                                color = if (isEnabled) SuccessEmerald else HologramTextDim,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = plugin.subtitle,
                        color = HologramTextMuted,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = isEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = accentColor,
                    uncheckedThumbColor = HologramTextDim,
                    uncheckedTrackColor = Color.Black
                )
            )
        }
    }
}
