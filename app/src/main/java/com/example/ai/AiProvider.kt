package com.example.ai

interface AiProvider {
    val id: String
    val displayName: String
    val supportedModels: List<String>
    val defaultModel: String

    suspend fun testConnection(apiKey: String, model: String): Result<String>

    suspend fun generateResponse(
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        systemInstruction: String,
        tools: List<ToolDefinition>
    ): Result<AiResponse> = generateResponse(
        apiKey = apiKey,
        model = model,
        messages = messages,
        systemInstruction = systemInstruction,
        tools = tools,
        enableSearchGrounding = false,
        enableMapsGrounding = false,
        userLocation = null
    )

    suspend fun generateResponse(
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        systemInstruction: String,
        tools: List<ToolDefinition>,
        enableSearchGrounding: Boolean = false,
        enableMapsGrounding: Boolean = false,
        userLocation: Pair<Double, Double>? = null
    ): Result<AiResponse> = Result.failure(UnsupportedOperationException("generateResponse is not implemented"))

    suspend fun generateStream(
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        systemInstruction: String,
        tools: List<ToolDefinition>,
        onChunk: (String) -> Unit
    ): Result<AiResponse>
}
