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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.DarkHologramSurface
import com.example.ui.theme.DeepSpaceBlack
import com.example.ui.theme.HologramTextBright
import com.example.ui.theme.HologramTextDim
import com.example.ui.theme.HologramTextMuted
import com.example.ui.theme.HudCardSurface
import com.example.viewmodel.JarvisViewModel

@Composable
fun MemoryPanel(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    val memories by viewModel.memoryManager.memoriesFlow.collectAsState()
    val accentColor = viewModel.getAccentColor()

    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var newKey by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }

    val filteredMemories = remember(memories, searchQuery) {
        if (searchQuery.isBlank()) {
            memories
        } else {
            memories.filter {
                it.first.contains(searchQuery, ignoreCase = true) ||
                        it.second.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkHologramSurface.copy(alpha = 0.9f))
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(12.dp)
            .testTag("memory_panel")
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = "Neural Memory",
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "NEURAL MEMORY MATRIX",
                        color = accentColor,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${memories.size} PERSISTENT RECORDS ACTIVE",
                        color = HologramTextDim,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Add Memory Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(accentColor.copy(alpha = 0.2f))
                    .border(1.dp, accentColor, RoundedCornerShape(6.dp))
                    .clickable { showAddDialog = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("add_memory_button"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Memory",
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "RECORD FACT",
                        color = accentColor,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Filter
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = "SEARCH STORED MEMORIES...",
                    color = HologramTextDim,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = HologramTextBright,
                unfocusedTextColor = HologramTextMuted,
                focusedBorderColor = accentColor,
                unfocusedBorderColor = accentColor.copy(alpha = 0.35f),
                focusedContainerColor = HudCardSurface,
                unfocusedContainerColor = DeepSpaceBlack
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("memory_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Memory List or Empty State
        if (filteredMemories.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (memories.isEmpty()) "NO USER MEMORIES STORED YET" else "NO MATCHING RECORDS FOUND",
                        color = HologramTextMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Say: \"Jarvis, remember my father's number is +91...\"\nor tap 'RECORD FACT' above.",
                        color = HologramTextDim,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredMemories, key = { it.first }) { (key, value) ->
                    MemoryItemCard(
                        memoryKey = key,
                        memoryValue = value,
                        accentColor = accentColor,
                        onDelete = { viewModel.deleteMemory(key) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Footer telemetry hint
        Text(
            text = "AI INJECTION: ALL SAVED MEMORIES ARE DYNAMICALLY SYNCED WITH GEMINI",
            color = HologramTextDim,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace
        )
    }

    // Add Memory Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(
                    text = "RECORD USER FACT / RELATIONSHIP",
                    color = accentColor,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newKey,
                        onValueChange = { newKey = it },
                        label = { Text("Topic / Key (e.g. father, car_model)", color = HologramTextDim, fontSize = 10.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = HologramTextBright,
                            unfocusedTextColor = HologramTextMuted,
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = accentColor.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("add_memory_key_input")
                    )

                    OutlinedTextField(
                        value = newValue,
                        onValueChange = { newValue = it },
                        label = { Text("Details / Value (e.g. +919876543210)", color = HologramTextDim, fontSize = 10.sp) },
                        singleLine = false,
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = HologramTextBright,
                            unfocusedTextColor = HologramTextMuted,
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = accentColor.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("add_memory_value_input")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newKey.isNotBlank() && newValue.isNotBlank()) {
                            viewModel.saveMemory(newKey.trim(), newValue.trim())
                            newKey = ""
                            newValue = ""
                            showAddDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_add_memory_button")
                ) {
                    Text("SAVE TO CORE", color = accentColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("CANCEL", color = HologramTextDim)
                }
            },
            containerColor = DarkHologramSurface,
            tonalElevation = 6.dp
        )
    }
}

@Composable
private fun MemoryItemCard(
    memoryKey: String,
    memoryValue: String,
    accentColor: Color,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(HudCardSurface)
            .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = memoryKey.uppercase(),
                color = accentColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = memoryValue,
                color = HologramTextBright,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(AlertCrimson.copy(alpha = 0.15f))
                .border(1.dp, AlertCrimson.copy(alpha = 0.4f), CircleShape)
                .clickable { onDelete() }
                .testTag("delete_memory_$memoryKey"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete Memory",
                tint = AlertCrimson,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}
