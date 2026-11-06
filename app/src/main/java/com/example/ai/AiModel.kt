package com.example.ai

data class ChatMessage(
    val role: String, // "user", "assistant", "system", "tool"
    val content: String,
    val imageUrlBase64: String? = null,
    val toolCallId: String? = null,
    val toolName: String? = null
)

data class ToolParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = false
)

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: List<ToolParameter> = emptyList()
)

data class ToolCallRequest(
    val id: String,
    val name: String,
    val argumentsJson: String
)

data class GroundingSource(
    val title: String,
    val url: String
)

data class AiResponse(
    val text: String,
    val toolCalls: List<ToolCallRequest> = emptyList(),
    val groundingSources: List<GroundingSource> = emptyList(),
    val isStreamingFinished: Boolean = true
)
