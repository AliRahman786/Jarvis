package com.example.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OpenAiProvider : AiProvider {
    override val id: String = "OPENAI"
    override val displayName: String = "OpenAI"
    override val supportedModels: List<String> = listOf(
        "gpt-4o",
        "gpt-4o-mini",
        "gpt-4-turbo"
    )
    override val defaultModel: String = "gpt-4o"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun testConnection(apiKey: String, model: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("OpenAI API key is required."))
        }
        try {
            val url = "https://api.openai.com/v1/chat/completions"
            val json = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "Ping test. Respond READY.")
                    })
                })
                put("max_tokens", 10)
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errorMsg = AiNetworkUtils.parseErrorMessage("OpenAI", response.code, body)
                return@withContext Result.failure(Exception(errorMsg))
            }
            Result.success("OpenAI $model verified successfully.")
        } catch (e: Exception) {
            Result.failure(Exception(AiNetworkUtils.parseException("OpenAI", e)))
        }
    }

    override suspend fun generateResponse(
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        systemInstruction: String,
        tools: List<ToolDefinition>,
        enableSearchGrounding: Boolean,
        enableMapsGrounding: Boolean,
        userLocation: Pair<Double, Double>?
    ): Result<AiResponse> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("OpenAI API key is required."))
        }
        try {
            val url = "https://api.openai.com/v1/chat/completions"
            val root = JSONObject()
            root.put("model", model)

            val msgsArray = JSONArray()
            if (systemInstruction.isNotBlank()) {
                msgsArray.put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemInstruction)
                })
            }
            for (m in messages) {
                val obj = JSONObject()
                obj.put("role", if (m.role == "assistant") "assistant" else "user")
                if (m.imageUrlBase64 != null) {
                    val contentArr = JSONArray()
                    contentArr.put(JSONObject().apply {
                        put("type", "text")
                        put("text", m.content)
                    })
                    contentArr.put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().apply {
                            put("url", "data:image/jpeg;base64,${m.imageUrlBase64}")
                        })
                    })
                    obj.put("content", contentArr)
                } else {
                    obj.put("content", m.content)
                }
                msgsArray.put(obj)
            }
            root.put("messages", msgsArray)

            if (tools.isNotEmpty()) {
                val toolsArray = JSONArray()
                for (tool in tools) {
                    val toolObj = JSONObject().apply {
                        put("type", "function")
                        put("function", JSONObject().apply {
                            put("name", tool.name)
                            put("description", tool.description)
                            val paramsObj = JSONObject().apply {
                                put("type", "object")
                                val props = JSONObject()
                                val req = JSONArray()
                                for (p in tool.parameters) {
                                    props.put(p.name, JSONObject().apply {
                                        put("type", p.type.lowercase())
                                        put("description", p.description)
                                    })
                                    if (p.required) req.put(p.name)
                                }
                                put("properties", props)
                                if (req.length() > 0) put("required", req)
                            }
                            put("parameters", paramsObj)
                        })
                    }
                    toolsArray.put(toolObj)
                }
                root.put("tools", toolsArray)
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(root.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errorMsg = AiNetworkUtils.parseErrorMessage("OpenAI", response.code, body)
                return@withContext Result.failure(Exception(errorMsg))
            }

            val respJson = JSONObject(body)
            val choices = respJson.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return@withContext Result.success(AiResponse("Diagnostic: Empty response from OpenAI."))
            }

            val choice = choices.getJSONObject(0)
            val messageObj = choice.getJSONObject("message")
            val text = messageObj.optString("content", "")
            val toolCalls = mutableListOf<ToolCallRequest>()

            val callsArray = messageObj.optJSONArray("tool_calls")
            if (callsArray != null) {
                for (i in 0 until callsArray.length()) {
                    val callObj = callsArray.getJSONObject(i)
                    val callId = callObj.optString("id", "call_$i")
                    val fn = callObj.optJSONObject("function")
                    if (fn != null) {
                        val name = fn.optString("name")
                        val args = fn.optString("arguments", "{}")
                        toolCalls.add(ToolCallRequest(id = callId, name = name, argumentsJson = args))
                    }
                }
            }

            Result.success(AiResponse(text = text.trim(), toolCalls = toolCalls))
        } catch (e: Exception) {
            Result.failure(Exception(AiNetworkUtils.parseException("OpenAI", e)))
        }
    }

    override suspend fun generateStream(
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        systemInstruction: String,
        tools: List<ToolDefinition>,
        onChunk: (String) -> Unit
    ): Result<AiResponse> {
        val res = generateResponse(apiKey, model, messages, systemInstruction, tools)
        if (res.isSuccess) {
            onChunk(res.getOrNull()?.text ?: "")
        }
        return res
    }
}
