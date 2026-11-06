package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ConversationEntity
import com.example.ui.hud.HudBadge
import com.example.ui.hud.HudPanel
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.GlassCardBackground
import com.example.ui.theme.HologramTextBright
import com.example.ui.theme.HologramTextDim
import com.example.ui.theme.HologramTextMuted
import com.example.ui.theme.HudCardSurface
import com.example.ui.theme.ReactorCyanGlow
import com.example.ui.theme.SuccessEmerald
import com.example.viewmodel.JarvisViewModel
import com.example.voice.VoiceState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConversationPanel(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val messages by viewModel.conversationMessages.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val accentColor = viewModel.getAccentColor()
    val listState = rememberLazyListState()
    val currentImageModel by viewModel.prefs.imageGenModelFlow.collectAsState()
    val enabledPlugins by viewModel.prefs.enabledPluginsFlow.collectAsState()
    val quotaShieldStatus by viewModel.quotaShieldStatus.collectAsState()

    // Multimodal File & Image Attachments
    val attachedImageBitmap by viewModel.attachedImageBitmap.collectAsState()
    val attachedImageName by viewModel.attachedImageName.collectAsState()
    val attachedFileContent by viewModel.attachedFileContent.collectAsState()
    val attachedFileName by viewModel.attachedFileName.collectAsState()
    val attachedFileSizeLabel by viewModel.attachedFileSizeLabel.collectAsState()

    // Zero-Permission Android Photo Picker (Google Play Policy Compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.attachImageFromUri(context, uri)
        }
    }

    // Zero-Permission Storage Access Framework Document/File Picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.attachDocumentFromUri(context, uri)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    HudPanel(
        title = "TACTICAL COMMUNICATIONS & NEURAL TERMINAL",
        accentColor = accentColor,
        trailingBadge = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.16f))
                        .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .clickable { viewModel.openPluginsModal() }
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                        .testTag("terminal_plugins_badge")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = "Neural Plugins",
                            tint = accentColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${enabledPlugins.size} PLUGINS",
                            color = accentColor,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                HudBadge(text = "${messages.size} LOGS", color = accentColor)
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = { viewModel.clearConversationHistory() },
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("clear_history_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear History",
                        tint = HologramTextDim,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Quota Shield & Multimodal Sub-Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .border(1.dp, accentColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Quota Shield",
                        tint = SuccessEmerald,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = quotaShieldStatus,
                        color = SuccessEmerald,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "MULTIMODAL: FILES • IMAGES • VOICE",
                    color = HologramTextMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Message log
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "MARK-LIV NEURAL TERMINAL ONLINE",
                                    color = accentColor,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Attach files, photos, code, or launch autonomous missions below.",
                                    color = HologramTextDim,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
                items(messages, key = { it.id }) { msg ->
                    ConversationBubble(
                        message = msg,
                        accentColor = accentColor,
                        operatorName = viewModel.prefs.getUserName().uppercase(),
                        imageModelName = currentImageModel,
                        onSpeak = { viewModel.speakVoice(msg.content) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Quick Action & Multimodal Toolbar Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TerminalActionPill(
                    icon = Icons.Default.AddPhotoAlternate,
                    label = "Attach Image",
                    accentColor = accentColor,
                    testTag = "attach_image_button",
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
                TerminalActionPill(
                    icon = Icons.Default.AttachFile,
                    label = "Attach File",
                    accentColor = accentColor,
                    testTag = "attach_file_button",
                    onClick = {
                        filePickerLauncher.launch(arrayOf("*/*"))
                    }
                )
                TerminalActionPill(
                    icon = Icons.Default.Extension,
                    label = "Plugins (${enabledPlugins.size})",
                    accentColor = accentColor,
                    testTag = "open_plugins_button",
                    onClick = { viewModel.openPluginsModal() }
                )
                TerminalActionPill(
                    icon = Icons.Default.Bolt,
                    label = "Exec Briefing",
                    accentColor = accentColor,
                    testTag = "mission_briefing_button",
                    onClick = { viewModel.executeAutonomousMission("MORNING_BRIEFING") }
                )
                TerminalActionPill(
                    icon = Icons.Default.Memory,
                    label = "System Audit",
                    accentColor = accentColor,
                    testTag = "mission_audit_button",
                    onClick = { viewModel.executeAutonomousMission("HARDWARE_AUDIT") }
                )
            }

            // Active Multimodal Attachment Preview Strip (if image or file is attached)
            if (attachedImageBitmap != null || attachedFileContent != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(GlassCardBackground)
                        .border(1.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Attached Image Chip
                    attachedImageBitmap?.let { bmp ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accentColor.copy(alpha = 0.15f))
                                .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Attached Image Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = attachedImageName ?: "Photo",
                                    color = HologramTextBright,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${bmp.width}x${bmp.height} • VISION READY",
                                    color = SuccessEmerald,
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove Attached Image",
                                tint = AlertCrimson,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { viewModel.clearAttachedImage() }
                            )
                        }
                    }

                    // Attached Document/File Chip
                    if (attachedFileContent != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accentColor.copy(alpha = 0.15f))
                                .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "Attached File",
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = attachedFileName ?: "Document",
                                    color = HologramTextBright,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${attachedFileSizeLabel ?: "FILE"} • PARSED",
                                    color = SuccessEmerald,
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove Attached File",
                                tint = AlertCrimson,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { viewModel.clearAttachedFile() }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Input Row
            val canSend = inputText.isNotBlank() || attachedImageBitmap != null || attachedFileContent != null
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { viewModel.setInputText(it) },
                    placeholder = {
                        Text(
                            text = when {
                                voiceState == VoiceState.LISTENING -> "Listening..."
                                attachedImageBitmap != null || attachedFileContent != null -> "Ask about attached file/image or press Send..."
                                else -> "Command, /image, or attach file/photo..."
                            },
                            color = HologramTextDim,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = accentColor.copy(alpha = 0.4f),
                        focusedTextColor = HologramTextBright,
                        unfocusedTextColor = HologramTextBright
                    ),
                    maxLines = 3
                )
                Spacer(modifier = Modifier.width(6.dp))

                // Terminal-based Image Generation Trigger Button
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .border(1.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .clickable {
                            if (inputText.isNotBlank()) {
                                viewModel.generateTerminalImage(inputText)
                                viewModel.setInputText("")
                            } else {
                                viewModel.setInputText("Generate an image of ")
                            }
                        }
                        .testTag("image_gen_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Generate Image via $currentImageModel",
                        tint = accentColor,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Send Button (works for text, attached image, attached file, or all together)
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (canSend) accentColor.copy(alpha = 0.25f) else accentColor.copy(alpha = 0.1f))
                        .border(1.dp, if (canSend) accentColor else accentColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                        .clickable(enabled = canSend) {
                            viewModel.sendUserMessage(inputText)
                        }
                        .testTag("send_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Command",
                        tint = if (canSend) accentColor else HologramTextDim,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Voice Mic Button
                val isListening = voiceState == VoiceState.LISTENING
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isListening) AlertCrimson.copy(alpha = 0.3f) else ReactorCyanGlow.copy(alpha = 0.15f))
                        .border(1.2.dp, if (isListening) AlertCrimson else ReactorCyanGlow, RoundedCornerShape(6.dp))
                        .clickable { viewModel.toggleCoreMicLock() }
                        .testTag("mic_toggle_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = "Toggle Voice Receptor",
                        tint = if (isListening) AlertCrimson else ReactorCyanGlow,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalActionPill(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(GlassCardBackground)
            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accentColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                color = HologramTextBright,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun ConversationBubble(
    message: ConversationEntity,
    accentColor: Color,
    operatorName: String,
    imageModelName: String,
    onSpeak: () -> Unit
) {
    val isUser = message.sender == "USER"
    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(message.timestamp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Sender header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (isUser) "OPERATOR // $operatorName" else "JARVIS // MARK-LIV",
                color = if (isUser) ReactorCyanGlow else accentColor,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = timeStr,
                color = HologramTextDim,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
            if (!isUser) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Replay Vocalization",
                    tint = accentColor.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { onSpeak() }
                )
            }
        }

        // Bubble Box
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isUser) ReactorCyanGlow.copy(alpha = 0.12f) else HudCardSurface)
                .border(
                    width = 1.dp,
                    color = if (isUser) ReactorCyanGlow.copy(alpha = 0.35f) else accentColor.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(10.dp)
        ) {
            Column {
                // If message has tool result attached
                if (message.toolName != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(accentColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = when (message.toolName) {
                                "IMAGE_GENERATOR" -> Icons.Default.Image
                                "GOOGLE_SEARCH" -> Icons.Default.Search
                                "GOOGLE_MAPS_ROUTE" -> Icons.Default.Directions
                                "GOOGLE_MAPS_PLACE" -> Icons.Default.Place
                                "AUTONOMOUS_BRIEFING" -> Icons.Default.Bolt
                                "HARDWARE_DIAGNOSTICS" -> Icons.Default.Memory
                                else -> Icons.Default.Build
                            },
                            contentDescription = "Tool Executed",
                            tint = accentColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (message.toolName) {
                                "IMAGE_GENERATOR" -> "NEURAL VISUAL MODEL // $imageModelName"
                                "GOOGLE_SEARCH" -> "LIVE GOOGLE SEARCH TELEMETRY"
                                "GOOGLE_MAPS_ROUTE" -> "REAL-TIME GOOGLE MAPS ROUTE TELEMETRY"
                                "GOOGLE_MAPS_PLACE" -> "REAL-TIME GOOGLE MAPS PLACE RADAR"
                                "AUTONOMOUS_BRIEFING" -> "AUTONOMOUS EXECUTIVE BRIEFING"
                                "HARDWARE_DIAGNOSTICS" -> "HARDWARE & QUOTA SHIELD DIAGNOSTICS"
                                else -> "SUBSYSTEM TOOL: ${message.toolName.uppercase()}"
                            },
                            color = accentColor,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Inline Generated or Attached Image Preview
                if (!message.imageUrl.isNullOrBlank() && message.imageUrl != "IMAGE_DATA") {
                    val file = File(message.imageUrl)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        AsyncImage(
                            model = file,
                            contentDescription = "Synthesized or Attached Visual",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Text(
                    text = message.content,
                    color = HologramTextBright,
                    fontSize = 13.sp,
                    fontFamily = if (isUser) FontFamily.SansSerif else FontFamily.Monospace,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
