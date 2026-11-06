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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.prefs.PreferencesManager
import com.example.ui.hud.HudBadge
import com.example.ui.hud.HudButton
import com.example.ui.hud.HudCard
import com.example.ui.hud.HudPanel
import com.example.ui.theme.DarkHologramSurface
import com.example.ui.theme.DeepSpaceBlack
import com.example.ui.theme.HologramTextBright
import com.example.ui.theme.HologramTextDim
import com.example.ui.theme.SuccessEmerald
import com.example.viewmodel.ApiValidationState
import com.example.viewmodel.JarvisViewModel

@Composable
fun SetupWizardScreen(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    val accentColor = viewModel.getAccentColor()
    val validationState by viewModel.apiValidationState.collectAsState()
    val scrollState = rememberScrollState()

    var userName by remember { mutableStateOf(viewModel.prefs.getUserName()) }
    var selectedProvider by remember { mutableStateOf(viewModel.prefs.getActiveAiProvider()) }

    var apiKeyInput by remember(selectedProvider) {
        mutableStateOf(viewModel.prefs.getApiKeyForProvider(selectedProvider))
    }
    var showApiKey by remember { mutableStateOf(false) }

    var savedCustomGeminiModels by remember { mutableStateOf(viewModel.prefs.getSavedCustomGeminiModels()) }
    val modelsForProvider = remember(selectedProvider, savedCustomGeminiModels) {
        when (selectedProvider) {
            "OPENAI" -> PreferencesManager.VALID_OPENAI_MODELS
            "GROQ" -> PreferencesManager.VALID_GROQ_MODELS
            else -> (PreferencesManager.VALID_GEMINI_MODELS + savedCustomGeminiModels).distinct()
        }
    }

    var selectedModel by remember(selectedProvider) {
        mutableStateOf(viewModel.prefs.getModel(selectedProvider))
    }
    var isModelDropdownExpanded by remember { mutableStateOf(false) }
    var customModelInput by remember { mutableStateOf(viewModel.prefs.getCustomModel()) }
    var showCustomModelInput by remember { mutableStateOf(true) }
    val isCustomModelSelected = !PreferencesManager.VALID_GEMINI_MODELS.contains(selectedModel) && selectedProvider == "GEMINI"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepSpaceBlack)
            .statusBarsPadding()
            .padding(16.dp)
            .testTag("setup_wizard_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Shield",
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "JARVIS MARK-LIV",
                        color = accentColor,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.2f))
                        .border(1.dp, accentColor, RoundedCornerShape(6.dp))
                        .clickable { viewModel.completeOnboarding() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("skip_to_hud_button")
                ) {
                    Text(
                        text = "ENTER HUD",
                        color = accentColor,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            HudPanel(title = "SYSTEM PROVISIONING", accentColor = accentColor) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Configure your operator identity and private AI credentials to bring JARVIS online. Keys are hardware-encrypted with Android KeyStore.",
                        color = HologramTextBright,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    // 1. Operator Designation
                    HudCard(borderColor = accentColor.copy(alpha = 0.4f)) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("1. OPERATOR NAME", color = accentColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Name used in vocal address and system directives:", color = HologramTextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = userName,
                                onValueChange = {
                                    userName = it
                                    viewModel.prefs.setUserName(it)
                                },
                                label = { Text("Operator Name", fontFamily = FontFamily.Monospace) },
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

                    // 2. AI Intelligence Provider
                    HudCard(borderColor = accentColor.copy(alpha = 0.4f)) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Psychology, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("2. SELECT AI PROVIDER", color = accentColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "GEMINI" to "Google Gemini",
                                    "OPENAI" to "OpenAI",
                                    "GROQ" to "Groq"
                                ).forEach { (pId, pName) ->
                                    val isSel = selectedProvider == pId
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSel) accentColor.copy(alpha = 0.25f) else DarkHologramSurface)
                                            .border(1.dp, if (isSel) accentColor else accentColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                            .clickable {
                                                selectedProvider = pId
                                                viewModel.prefs.setActiveAiProvider(pId)
                                                apiKeyInput = viewModel.prefs.getApiKeyForProvider(pId)
                                                selectedModel = viewModel.prefs.getModel(pId)
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = pName,
                                            color = if (isSel) accentColor else HologramTextDim,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. User API Key
                    HudCard(borderColor = accentColor.copy(alpha = 0.4f)) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Key, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "3. $selectedProvider API KEY",
                                    color = accentColor,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Enter your private $selectedProvider API key (Stored encrypted in KeyStore):",
                                color = HologramTextDim,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = apiKeyInput,
                                onValueChange = {
                                    apiKeyInput = it
                                    viewModel.prefs.setApiKeyForProvider(selectedProvider, it)
                                },
                                label = { Text("$selectedProvider API Key", fontFamily = FontFamily.Monospace) },
                                placeholder = {
                                    val hint = when (selectedProvider) {
                                        "OPENAI" -> "sk-proj-..."
                                        "GROQ" -> "gsk_..."
                                        else -> "AIzaSy..."
                                    }
                                    Text(hint, color = HologramTextDim)
                                },
                                singleLine = true,
                                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                trailingIcon = {
                                    IconButton(onClick = { showApiKey = !showApiKey }) {
                                        Icon(
                                            imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle visibility",
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

                    // 4. Model Selection
                    HudCard(borderColor = accentColor.copy(alpha = 0.4f)) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Psychology, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("4. AI MODEL", color = accentColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            Box(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(DarkHologramSurface)
                                        .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                        .clickable { isModelDropdownExpanded = true }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isCustomModelSelected) "Custom: $selectedModel" else selectedModel,
                                        color = HologramTextBright,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = accentColor)
                                }

                                DropdownMenu(
                                    expanded = isModelDropdownExpanded,
                                    onDismissRequest = { isModelDropdownExpanded = false },
                                    modifier = Modifier
                                        .background(DeepSpaceBlack)
                                        .border(1.dp, accentColor, RoundedCornerShape(6.dp))
                                ) {
                                    modelsForProvider.forEach { m ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = m,
                                                    color = if (selectedModel == m) accentColor else HologramTextBright,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = if (selectedModel == m) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            onClick = {
                                                selectedModel = m
                                                viewModel.prefs.setModel(selectedProvider, m)
                                                isModelDropdownExpanded = false
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = {
                                            Text("+ Custom Model ID...", color = accentColor, fontFamily = FontFamily.Monospace)
                                        },
                                        onClick = {
                                            isModelDropdownExpanded = false
                                            showCustomModelInput = true
                                            if (customModelInput.isNotBlank()) {
                                                val clean = PreferencesManager.normalizeModelId(customModelInput)
                                                selectedModel = clean
                                                viewModel.prefs.setModel(selectedProvider, clean)
                                            }
                                        }
                                    )
                                }
                            }

                            if (showCustomModelInput || isCustomModelSelected) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = customModelInput,
                                    onValueChange = { rawInput ->
                                        customModelInput = rawInput
                                        val clean = PreferencesManager.normalizeModelId(rawInput)
                                        if (clean.isNotBlank()) {
                                            selectedModel = clean
                                            viewModel.prefs.setCustomModel(clean)
                                            viewModel.prefs.setModel(selectedProvider, clean)
                                            savedCustomGeminiModels = viewModel.prefs.getSavedCustomGeminiModels()
                                        }
                                    },
                                    label = { Text("Custom Model ID (any Gemini model)", fontFamily = FontFamily.Monospace) },
                                    placeholder = { Text("e.g. gemini-3.5-flash", color = HologramTextDim, fontFamily = FontFamily.Monospace) },
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
                    }

                    // Test key button
                    HudButton(
                        text = "TEST $selectedProvider CONNECTION",
                        onClick = {
                            viewModel.testActiveApiKey(selectedProvider, apiKeyInput, selectedModel)
                        },
                        accentColor = accentColor.copy(alpha = 0.8f)
                    )

                    when (val state = validationState) {
                        is ApiValidationState.Validating -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = accentColor, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Connecting to $selectedProvider...", color = accentColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                        is ApiValidationState.Success -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessEmerald, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("[VERIFIED] ${state.message}", color = SuccessEmerald, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                        is ApiValidationState.Error -> {
                            Text("[FAILED] ${state.message}", color = Color(0xFFFF5252), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                        is ApiValidationState.Idle -> {}
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Final initialization button
                    HudButton(
                        text = "INITIALIZE JARVIS // BRING ONLINE",
                        onClick = {
                            viewModel.prefs.setUserName(userName)
                            viewModel.prefs.setActiveAiProvider(selectedProvider)
                            viewModel.prefs.setApiKeyForProvider(selectedProvider, apiKeyInput)
                            viewModel.prefs.setModel(selectedProvider, selectedModel)
                            viewModel.completeOnboarding()
                        },
                        accentColor = accentColor
                    )
                }
            }
        }
    }
}
