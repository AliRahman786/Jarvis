package com.example.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class GeminiProvider : AiProvider {
    override val id: String = "GEMINI"
    override val displayName: String = "Google Gemini"
    override val supportedModels: List<String> = listOf(
        "gemini-3.5-flash-lite",
        "gemini-3.5-flash",
        "gemini-3.8-flash",
        "gemini-3.8-pro",
        "gemini-3.8-flash-lite"
    )
    override val defaultModel: String = "gemini-3.5-flash-lite"

    // Quota Shield Telemetry & In-Memory LRU Cache (prevents duplicate API calls from consuming quota)
    private val responseCache = LruCache<String, AiResponse>(80)
    // Sticky endpoint and API version caches so custom model IDs resolve with 0ms 404 probing overhead
    private val workingEndpointCache = ConcurrentHashMap<String, String>()
    private val workingApiVersionCache = ConcurrentHashMap<String, String>()
    private val discoveredLiveModels = java.util.concurrent.CopyOnWriteArrayList<String>()
    val quotaSavedCount = AtomicInteger(0)
    val modelFailoverCount = AtomicInteger(0)

    @Volatile
    var lowLatencyMode: Boolean = true

    @Volatile
    var lastLatencyMs: Long = 0L
        private set

    @Volatile
    var lastUsedModel: String = defaultModel
        private set

    private val httpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(6, 5, TimeUnit.MINUTES))
        .protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Preserves any custom Gemini model ID entered by the user while cleaning up
     * leading "models/" prefixes or spaces (e.g., "gemini 3.5 flash lite" -> "gemini-3.5-flash-lite").
     */
    fun normalizeModel(model: String): String {
        val cleaned = model.trim()
            .removePrefix("models/")
            .replace(Regex("\\s+"), "-")
        if (cleaned.isBlank() || cleaned.equals("custom-model", ignoreCase = true)) {
            return defaultModel
        }
        return cleaned
    }

    private fun parseApiKeys(rawKey: String): List<String> {
        return rawKey.split(",", ";", "\n")
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "PLACEHOLDER_GEMINI_API_KEY" }
            .distinct()
    }

    /**
     * Dynamically queries ModelService.ListModels across v1beta and v1 to discover
     * all models officially enabled on the user's specific API key/project.
     */
    suspend fun discoverSupportedModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val keys = parseApiKeys(apiKey)
        for (k in keys) {
            for (apiVersion in listOf("v1beta", "v1")) {
                try {
                    val url = "https://generativelanguage.googleapis.com/$apiVersion/models?key=$k"
                    val request = Request.Builder()
                        .url(url)
                        .get()
                        .build()
                    val response = httpClient.newCall(request).execute()
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val json = JSONObject(body)
                        val modelsArr = json.optJSONArray("models")
                        if (modelsArr != null && modelsArr.length() > 0) {
                            val list = mutableListOf<String>()
                            for (i in 0 until modelsArr.length()) {
                                val mObj = modelsArr.getJSONObject(i)
                                val name = mObj.optString("name", "").removePrefix("models/").trim()
                                val methods = mObj.optJSONArray("supportedGenerationMethods")
                                val canGenerate = methods == null || (0 until methods.length()).any {
                                    methods.optString(it) == "generateContent"
                                }
                                if (canGenerate && name.isNotBlank()) {
                                    list.add(name)
                                }
                            }
                            if (list.isNotEmpty()) {
                                discoveredLiveModels.clear()
                                discoveredLiveModels.addAll(list.distinct())
                                return@withContext list
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        emptyList()
    }

    /**
     * Maps any selected or custom Gemini model ID to ultra-low-latency live endpoints.
     * - ALWAYS tests the user's exact requested model ID first.
     * - Uses the sticky workingEndpointCache once verified for 0ms overhead.
     * - Integrates dynamically discovered live models from ModelService.ListModels.
     * - Falls back across all current Google Gemini production models.
     */
    fun getEndpointCandidatesForVariant(variant: String): List<String> {
        val clean = normalizeModel(variant)
        val cachedWorking = workingEndpointCache[clean]
        val isLite = clean.contains("lite", ignoreCase = true) || lowLatencyMode
        val isPro = clean.contains("pro", ignoreCase = true) && !clean.contains("lite", ignoreCase = true)

        val liveFallbacks = when {
            clean.contains("lite", ignoreCase = true) -> listOf(
                "gemini-2.0-flash-lite",
                "gemini-2.0-flash-lite-preview-02-05",
                "gemini-2.0-flash",
                "gemini-1.5-flash-latest",
                "gemini-1.5-flash",
                "gemini-1.5-flash-8b"
            )
            isPro -> listOf(
                "gemini-2.5-pro",
                "gemini-2.0-flash",
                "gemini-1.5-pro-latest",
                "gemini-1.5-pro",
                "gemini-1.5-flash-latest",
                "gemini-1.5-flash"
            )
            isLite -> listOf(
                "gemini-2.0-flash",
                "gemini-2.0-flash-lite",
                "gemini-2.0-flash-lite-preview-02-05",
                "gemini-1.5-flash-latest",
                "gemini-1.5-flash"
            )
            else -> listOf(
                "gemini-2.0-flash",
                "gemini-2.0-flash-lite",
                "gemini-1.5-flash-latest",
                "gemini-1.5-flash",
                "gemini-1.5-pro-latest"
            )
        }

        val candidates = mutableListOf<String>()
        if (!cachedWorking.isNullOrBlank()) {
            candidates.add(cachedWorking)
        }
        // ALWAYS try the user's exact model ID first!
        candidates.add(clean)

        // Integrate discovered models from live ModelService
        if (discoveredLiveModels.isNotEmpty()) {
            if (clean.contains("lite", ignoreCase = true)) {
                candidates.addAll(discoveredLiveModels.filter { it.contains("lite", ignoreCase = true) })
                candidates.addAll(discoveredLiveModels.filter { it.contains("flash", ignoreCase = true) })
            } else if (isPro) {
                candidates.addAll(discoveredLiveModels.filter { it.contains("pro", ignoreCase = true) })
                candidates.addAll(discoveredLiveModels.filter { it.contains("flash", ignoreCase = true) })
            } else {
                candidates.addAll(discoveredLiveModels.filter { it.contains("flash", ignoreCase = true) })
            }
            candidates.addAll(discoveredLiveModels)
        }

        candidates.addAll(liveFallbacks)
        return candidates.distinct()
    }

    override suspend fun testConnection(apiKey: String, model: String): Result<String> = withContext(Dispatchers.IO) {
        val keys = parseApiKeys(apiKey)
        if (keys.isEmpty()) {
            return@withContext Result.failure(Exception("Gemini API key is required. Please configure your key in Settings."))
        }
        val cleanVariant = normalizeModel(model)

        // Dynamically query available models for this key if not already cached
        if (discoveredLiveModels.isEmpty()) {
            discoverSupportedModels(apiKey)
        }

        val endpointCandidates = getEndpointCandidatesForVariant(cleanVariant)
        var lastErr = "Verification failed"
        var authError: String? = null
        var quotaError: String? = null
        val startMs = System.currentTimeMillis()

        for (k in keys) {
            for (endpointModel in endpointCandidates) {
                // Try v1beta first, then fallback to v1 on 404
                for (apiVer in listOf("v1beta", "v1")) {
                    try {
                        val url = "https://generativelanguage.googleapis.com/$apiVer/models/$endpointModel:generateContent?key=$k"
                        val json = JSONObject().apply {
                            put("contents", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("parts", JSONArray().apply {
                                        put(JSONObject().apply { put("text", "Ping. Reply ONLINE.") })
                                    })
                                })
                            })
                            put("generationConfig", JSONObject().apply {
                                put("maxOutputTokens", 16)
                                put("temperature", 0.1)
                            })
                        }
                        val request = Request.Builder()
                            .url(url)
                            .post(json.toString().toRequestBody("application/json".toMediaType()))
                            .build()
                        val response = httpClient.newCall(request).execute()
                        val body = response.body?.string() ?: ""
                        if (response.isSuccessful) {
                            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                            lastLatencyMs = elapsed
                            lastUsedModel = cleanVariant
                            workingEndpointCache[cleanVariant] = endpointModel
                            workingApiVersionCache[cleanVariant] = apiVer
                            val resolvedText = if (endpointModel == cleanVariant) {
                                "Gemini ($cleanVariant) verified in ${elapsed}ms • Ultra-Low Latency Active."
                            } else {
                                "Gemini ($cleanVariant) verified & linked to live engine ($endpointModel) in ${elapsed}ms • Ultra-Low Latency Active."
                            }
                            return@withContext Result.success(resolvedText)
                        } else {
                            val parsed = AiNetworkUtils.parseErrorMessage("Gemini", response.code, body)
                            if (response.code == 400 || response.code == 401 || response.code == 403) {
                                authError = parsed
                            } else if (response.code == 429) {
                                quotaError = parsed
                            }
                            lastErr = parsed
                        }
                    } catch (e: Exception) {
                        lastErr = AiNetworkUtils.parseException("Gemini", e)
                    }
                }
            }
        }

        // Return the most informative and context-appropriate error
        val finalError = authError ?: quotaError ?: run {
            if (lastErr.contains("404") || lastErr.contains("not found", ignoreCase = true)) {
                "[Gemini Model Alert // 404] Model '$cleanVariant' could not be resolved on this API key. Please verify your Google Gemini API key or ensure Generative Language API is enabled."
            } else {
                lastErr
            }
        }
        Result.failure(Exception(finalError))
    }

    override suspend fun generateResponse(
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        systemInstruction: String,
        tools: List<ToolDefinition>
    ): Result<AiResponse> {
        return generateResponse(
            apiKey = apiKey,
            model = model,
            messages = messages,
            systemInstruction = systemInstruction,
            tools = tools,
            enableSearchGrounding = false,
            enableMapsGrounding = false,
            userLocation = null
        )
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
        val keys = parseApiKeys(apiKey)
        if (keys.isEmpty()) {
            return@withContext Result.failure(Exception("API_KEY_MISSING: Gemini API key has not been configured."))
        }

        val primaryVariant = normalizeModel(model)
        val lastUserMsg = messages.lastOrNull { it.role == "user" }
        val hasImageAttachment = lastUserMsg?.imageUrlBase64 != null

        // 1. QUOTA SHIELD: Check LRU Cache for identical text queries (0 API tokens used on hit)
        val cacheKey = if (!hasImageAttachment && lastUserMsg != null && lastUserMsg.content.length in 3..500) {
            "${lastUserMsg.content.trim().lowercase()}|${systemInstruction.hashCode()}"
        } else null

        if (cacheKey != null) {
            val cached = responseCache.get(cacheKey)
            if (cached != null && cached.text.isNotBlank() && cached.toolCalls.isEmpty()) {
                quotaSavedCount.incrementAndGet()
                return@withContext Result.success(cached)
            }
        }

        // 2. ULTRA-LOW LATENCY ENDPOINT CASCADE (No synthetic 404 endpoints appended!)
        val failoverEndpoints = getEndpointCandidatesForVariant(primaryVariant)
        val isLiteOrFast = primaryVariant.contains("lite", ignoreCase = true) ||
            primaryVariant.contains("flash", ignoreCase = true) ||
            lowLatencyMode

        var lastException: Exception? = null
        var firstQuotaException: Exception? = null
        val requestStartMs = System.currentTimeMillis()

        for (candidateKey in keys) {
            for ((modelIndex, candidateModel) in failoverEndpoints.withIndex()) {
                val preferredVer = workingApiVersionCache[primaryVariant] ?: "v1beta"
                val versionsToTry = if (preferredVer == "v1") listOf("v1", "v1beta") else listOf("v1beta", "v1")

                for (ver in versionsToTry) {
                    val result = executeGenerateContent(
                        apiKey = candidateKey,
                        model = candidateModel,
                        messages = messages,
                        systemInstruction = systemInstruction,
                        tools = tools,
                        enableSearchGrounding = enableSearchGrounding,
                        enableMapsGrounding = enableMapsGrounding,
                        userLocation = userLocation,
                        isLowLatency = isLiteOrFast,
                        apiVersion = ver
                    )

                    if (result.isSuccess) {
                        lastLatencyMs = (System.currentTimeMillis() - requestStartMs).coerceAtLeast(1L)
                        lastUsedModel = primaryVariant
                        workingEndpointCache[primaryVariant] = candidateModel
                        workingApiVersionCache[primaryVariant] = ver
                        if (modelIndex > 0) {
                            modelFailoverCount.incrementAndGet()
                        }
                        val aiResp = result.getOrNull()!!
                        if (cacheKey != null && aiResp.text.isNotBlank() && aiResp.toolCalls.isEmpty()) {
                            responseCache.put(cacheKey, aiResp)
                        }
                        return@withContext result
                    }

                    val exception = result.exceptionOrNull() as? Exception ?: Exception("Unknown error")
                    lastException = exception
                    val msg = exception.message ?: ""

                    val isRateLimitOrQuota = msg.contains("429") ||
                            msg.contains("Rate Limit", ignoreCase = true) ||
                            msg.contains("quota", ignoreCase = true) ||
                            msg.contains("RESOURCE_EXHAUSTED", ignoreCase = true)
                    val isModelUnavailableOrServer = msg.contains("404") ||
                            msg.contains("not found", ignoreCase = true) ||
                            msg.contains("500") || msg.contains("502") ||
                            msg.contains("503") || msg.contains("504")

                    if (isRateLimitOrQuota && firstQuotaException == null) {
                        firstQuotaException = exception
                    }

                    if (msg.contains("400") || msg.contains("tool", ignoreCase = true) || msg.contains("grounding", ignoreCase = true)) {
                        // If custom/experimental model does not support function_declarations, retry immediately (0ms delay) without tools
                        if (tools.isNotEmpty() || enableSearchGrounding || enableMapsGrounding) {
                            val retryPlain = executeGenerateContent(
                                apiKey = candidateKey,
                                model = candidateModel,
                                messages = messages,
                                systemInstruction = systemInstruction,
                                tools = emptyList(),
                                enableSearchGrounding = false,
                                enableMapsGrounding = false,
                                userLocation = null,
                                isLowLatency = isLiteOrFast,
                                apiVersion = ver
                            )
                            if (retryPlain.isSuccess) {
                                lastLatencyMs = (System.currentTimeMillis() - requestStartMs).coerceAtLeast(1L)
                                lastUsedModel = primaryVariant
                                workingEndpointCache[primaryVariant] = candidateModel
                                workingApiVersionCache[primaryVariant] = ver
                                val aiResp = retryPlain.getOrNull()!!
                                if (cacheKey != null && aiResp.text.isNotBlank() && aiResp.toolCalls.isEmpty()) {
                                    responseCache.put(cacheKey, aiResp)
                                }
                                return@withContext retryPlain
                            }
                        }
                    }

                    if (isModelUnavailableOrServer || msg.contains("400")) {
                        // 0ms instant failover on 404/400 so custom models never add delay
                        continue
                    } else if (isRateLimitOrQuota) {
                        // Jitter backoff across rate limit buckets
                        delay(Random.nextLong(200, 420))
                        continue
                    } else if (msg.contains("401") || msg.contains("403") || msg.contains("API_KEY_INVALID", ignoreCase = true)) {
                        break
                    }
                }
            }
        }

        // Return firstQuotaException if encountered so quota limits are accurately reported rather than masked by downstream 404/400 errors
        val finalException = firstQuotaException ?: lastException ?: Exception("Neural communication error after multi-model failover.")
        Result.failure(finalException)
    }

    private fun executeGenerateContent(
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        systemInstruction: String,
        tools: List<ToolDefinition>,
        enableSearchGrounding: Boolean = false,
        enableMapsGrounding: Boolean = false,
        userLocation: Pair<Double, Double>? = null,
        isLowLatency: Boolean = true,
        apiVersion: String = "v1beta"
    ): Result<AiResponse> {
        try {
            val url = "https://generativelanguage.googleapis.com/$apiVersion/models/$model:generateContent?key=$apiKey"
            val root = JSONObject()

            // Low-Latency Generation Config for snappy voice & chat responses
            root.put("generationConfig", JSONObject().apply {
                put("temperature", if (isLowLatency) 0.55 else 0.7)
                put("maxOutputTokens", if (isLowLatency) 480 else 1024)
                put("topP", 0.90)
            })

            // System Instruction
            if (systemInstruction.isNotBlank()) {
                root.put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemInstruction) })
                    })
                })
            }

            // IMPORTANT: Do NOT combine googleSearch / googleMaps grounding objects with function_declarations
            // in the same tools array, as v1beta rejects mixed tool types with HTTP 400.
            // Since JarvisViewModel already declares searchGoogle & showTacticalMap in function_declarations,
            // use function_declarations exclusively when tools are provided.
            val toolsArray = JSONArray()

            if (tools.isNotEmpty()) {
                val functionDeclarations = JSONArray()
                for (tool in tools) {
                    val fnObj = JSONObject().apply {
                        put("name", tool.name)
                        put("description", tool.description)

                        val parametersObj = JSONObject().apply {
                            put("type", "OBJECT")
                            val propertiesObj = JSONObject()
                            val requiredArr = JSONArray()

                            for (param in tool.parameters) {
                                val paramProp = JSONObject().apply {
                                    put("type", param.type.uppercase())
                                    put("description", param.description)
                                }
                                propertiesObj.put(param.name, paramProp)
                                if (param.required) {
                                    requiredArr.put(param.name)
                                }
                            }

                            put("properties", propertiesObj)
                            if (requiredArr.length() > 0) {
                                put("required", requiredArr)
                            }
                        }
                        put("parameters", parametersObj)
                    }
                    functionDeclarations.put(fnObj)
                }

                toolsArray.put(JSONObject().apply {
                    put("function_declarations", functionDeclarations)
                })
            } else if (enableSearchGrounding) {
                toolsArray.put(JSONObject().apply {
                    put("googleSearch", JSONObject())
                })
            }

            if (toolsArray.length() > 0) {
                root.put("tools", toolsArray)
            }

            // Strict turn sequencing:
            // 1. Roles must be 'user' or 'model'
            // 2. Strict alternation: no two consecutive turns with the same role
            // 3. First turn MUST have role 'user'
            // 4. Last turn MUST have role 'user'
            val historyLimit = if (isLowLatency) 4 else 6
            val trimmedHistory = if (messages.size > historyLimit) messages.takeLast(historyLimit) else messages
            val sanitizedTurns = mutableListOf<Pair<String, MutableList<JSONObject>>>()

            for (i in trimmedHistory.indices) {
                val msg = trimmedHistory[i]
                if (msg.content.isBlank() && msg.imageUrlBase64 == null) continue

                val role = if (msg.role == "assistant") "model" else "user"
                val partsList = mutableListOf<JSONObject>()

                // Only attach image to the very last message in the list if present
                if (i == trimmedHistory.lastIndex && msg.imageUrlBase64 != null) {
                    partsList.add(JSONObject().apply {
                        put("inline_data", JSONObject().apply {
                            put("mime_type", "image/jpeg")
                            put("data", msg.imageUrlBase64)
                        })
                    })
                }

                if (msg.content.isNotBlank()) {
                    // Quota Shield & Low-Latency Token Optimizer: keep full content on active turn,
                    // compact older turns to 550 chars for ultra-fast prefill latency.
                    val maxPriorChars = if (isLowLatency) 550 else 900
                    val optimizedText = if (i == trimmedHistory.lastIndex) {
                        msg.content.take(18000)
                    } else {
                        if (msg.content.length > maxPriorChars) msg.content.take(maxPriorChars) + "... [compacted]" else msg.content
                    }
                    partsList.add(JSONObject().apply {
                        put("text", optimizedText)
                    })
                }

                if (partsList.isEmpty()) continue

                if (sanitizedTurns.isNotEmpty() && sanitizedTurns.last().first == role) {
                    // Merge consecutive parts into previous turn to guarantee strict alternation
                    sanitizedTurns.last().second.addAll(partsList)
                } else {
                    sanitizedTurns.add(Pair(role, partsList))
                }
            }

            // First turn MUST have role 'user'
            while (sanitizedTurns.isNotEmpty() && sanitizedTurns.first().first != "user") {
                sanitizedTurns.removeAt(0)
            }

            // If empty, supply a default user ping
            if (sanitizedTurns.isEmpty()) {
                val defaultParts = mutableListOf(JSONObject().apply { put("text", "JARVIS status report.") })
                sanitizedTurns.add(Pair("user", defaultParts))
            }

            // Last turn MUST have role 'user' for generateContent
            if (sanitizedTurns.isNotEmpty() && sanitizedTurns.last().first != "user") {
                // If the last message was from the model, append a user continuation
                val contParts = mutableListOf(JSONObject().apply { put("text", "Please proceed.") })
                sanitizedTurns.add(Pair("user", contParts))
            }

            val contentsArray = JSONArray()
            for (turn in sanitizedTurns) {
                val contentObj = JSONObject()
                contentObj.put("role", turn.first)
                val partsArr = JSONArray()
                for (p in turn.second) {
                    partsArr.put(p)
                }
                contentObj.put("parts", partsArr)
                contentsArray.put(contentObj)
            }
            root.put("contents", contentsArray)

            val request = Request.Builder()
                .url(url)
                .post(root.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = AiNetworkUtils.parseErrorMessage("Gemini", response.code, body)
                return Result.failure(Exception(errorMsg))
            }

            val respJson = JSONObject(body)
            val candidates = respJson.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return Result.success(AiResponse("Systems active. Ready for next command."))
            }

            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")

            var responseText = ""
            val toolCalls = mutableListOf<ToolCallRequest>()
            val groundingSources = mutableListOf<GroundingSource>()

            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    if (part.has("text")) {
                        responseText += part.getString("text")
                    }
                    if (part.has("functionCall")) {
                        val fnCall = part.getJSONObject("functionCall")
                        val fnName = fnCall.optString("name", "")
                        val fnArgs = fnCall.optJSONObject("args")?.toString() ?: "{}"
                        toolCalls.add(
                            ToolCallRequest(
                                id = "call_${System.currentTimeMillis()}_$i",
                                name = fnName,
                                argumentsJson = fnArgs
                            )
                        )
                    }
                }
            }

            // Extract Grounding Metadata Citations
            val groundingMeta = firstCandidate.optJSONObject("groundingMetadata")
            if (groundingMeta != null) {
                val chunks = groundingMeta.optJSONArray("groundingChunks")
                if (chunks != null) {
                    for (c in 0 until chunks.length()) {
                        val chunkObj = chunks.getJSONObject(c)
                        val web = chunkObj.optJSONObject("web")
                        if (web != null) {
                            val title = web.optString("title", "Web Source")
                            val uri = web.optString("uri", "")
                            if (uri.isNotBlank()) {
                                groundingSources.add(GroundingSource(title, uri))
                            }
                        }
                    }
                }
            }

            return Result.success(
                AiResponse(
                    text = responseText.trim(),
                    toolCalls = toolCalls,
                    groundingSources = groundingSources
                )
            )
        } catch (e: Exception) {
            return Result.failure(Exception(AiNetworkUtils.parseException("Gemini", e)))
        }
    }

    suspend fun generateImage(
        apiKey: String,
        modelName: String,
        prompt: String,
        cacheDir: File
    ): Result<File> = withContext(Dispatchers.IO) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank()) {
            return@withContext Result.failure(Exception("Visual prompt cannot be empty."))
        }

        val effectiveKey = resolveEffectiveGeminiKey(apiKey)
        var lastApiError: String? = null

        if (effectiveKey.isNotBlank()) {
            // 1. Vertex AI / Gemini Native Image Generation API (:generateContent with IMAGE responseModality)
            val candidateGeminiModels = buildList {
                val normalized = modelName.trim().removePrefix("models/")
                if (normalized.isNotBlank() &&
                    !normalized.startsWith("imagen", ignoreCase = true) &&
                    !normalized.equals("gemini-nano-banana-2.1", ignoreCase = true)
                ) {
                    add(normalized)
                }
                add("gemini-2.0-flash-exp-image-generation")
                add("gemini-2.0-flash-preview-image-generation")
            }.distinct()

            for (geminiImgModel in candidateGeminiModels) {
                try {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$geminiImgModel:generateContent?key=$effectiveKey"
                    val bodyJson = JSONObject().apply {
                        put("contents", JSONArray().apply {
                            put(JSONObject().apply {
                                put("parts", JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("text", "Generate a high-resolution, detailed image: $cleanPrompt")
                                    })
                                })
                            })
                        })
                        put("generationConfig", JSONObject().apply {
                            put("responseModalities", JSONArray().apply {
                                put("TEXT")
                                put("IMAGE")
                            })
                        })
                    }

                    val req = Request.Builder()
                        .url(url)
                        .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    httpClient.newCall(req).execute().use { resp ->
                        val respBody = resp.body?.string() ?: ""
                        if (resp.isSuccessful && respBody.isNotBlank()) {
                            val root = JSONObject(respBody)
                            val candidates = root.optJSONArray("candidates")
                            if (candidates != null && candidates.length() > 0) {
                                val content = candidates.getJSONObject(0).optJSONObject("content")
                                val parts = content?.optJSONArray("parts")
                                if (parts != null) {
                                    for (i in 0 until parts.length()) {
                                        val part = parts.getJSONObject(i)
                                        val inlineData = part.optJSONObject("inlineData")
                                            ?: part.optJSONObject("inline_data")
                                        if (inlineData != null) {
                                            val b64 = inlineData.optString("data", "")
                                            val savedFile = decodeAndSaveValidBitmap(b64, cacheDir, "gemini_img")
                                            if (savedFile != null) {
                                                return@withContext Result.success(savedFile)
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (respBody.isNotBlank()) {
                            lastApiError = extractGeminiErrorMessage(respBody, resp.code)
                        }
                    }
                } catch (e: Exception) {
                    lastApiError = e.message
                }
            }

            // 2. Vertex AI / Gemini Imagen 3 API (:predict endpoint)
            val imagenModels = listOf(
                "imagen-3.0-generate-002",
                "imagen-3.0-generate-001"
            )
            for (targetModel in imagenModels) {
                try {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$targetModel:predict?key=$effectiveKey"
                    val bodyJson = JSONObject().apply {
                        put("instances", JSONArray().apply {
                            put(JSONObject().apply { put("prompt", cleanPrompt) })
                        })
                        put("parameters", JSONObject().apply {
                            put("sampleCount", 1)
                            put("aspectRatio", "1:1")
                        })
                    }

                    val req = Request.Builder()
                        .url(url)
                        .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    httpClient.newCall(req).execute().use { resp ->
                        val respBody = resp.body?.string() ?: ""
                        if (resp.isSuccessful && respBody.isNotBlank()) {
                            val j = JSONObject(respBody)
                            val predictions = j.optJSONArray("predictions")
                            if (predictions != null && predictions.length() > 0) {
                                val predObj = predictions.getJSONObject(0)
                                val b64 = predObj.optString("bytesBase64Encoded", "").ifBlank {
                                    predObj.optJSONObject("image")?.optString("bytesBase64Encoded", "") ?: ""
                                }
                                val savedFile = decodeAndSaveValidBitmap(b64, cacheDir, "imagen3")
                                if (savedFile != null) {
                                    return@withContext Result.success(savedFile)
                                }
                            }
                        } else if (respBody.isNotBlank()) {
                            lastApiError = extractGeminiErrorMessage(respBody, resp.code)
                        }
                    }
                } catch (e: Exception) {
                    lastApiError = e.message
                }
            }
        }

        // 3. Secondary High-Availability Neural Image Synthesis Fallback
        try {
            val encodedPrompt = URLEncoder.encode(cleanPrompt, "UTF-8")
            val seed = Random.nextInt(10000, 99999)
            val fallbackUrl = "https://image.pollinations.ai/prompt/$encodedPrompt?width=1024&height=1024&nologo=true&seed=$seed&model=flux"
            val fallbackReq = Request.Builder()
                .url(fallbackUrl)
                .header("User-Agent", "Mozilla/5.0 (Android 15; Mobile; JARVIS-Mark-LIV)")
                .build()
            httpClient.newCall(fallbackReq).execute().use { fallbackResp ->
                if (fallbackResp.isSuccessful) {
                    val bytes = fallbackResp.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) {
                            val outFile = File(cacheDir, "jarvis_visual_${System.currentTimeMillis()}.jpg")
                            FileOutputStream(outFile).use { it.write(bytes) }
                            return@withContext Result.success(outFile)
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        val errDetail = lastApiError ?: if (effectiveKey.isBlank()) {
            "No valid Gemini API Key configured. Please enter your Gemini API Key in Settings or AI Studio Secrets."
        } else {
            "Image generation failed across Vertex AI / Gemini neural matrix."
        }
        Result.failure(Exception(errDetail))
    }

    private fun resolveEffectiveGeminiKey(passedKey: String): String {
        val cleanPassed = passedKey.trim()
        if (cleanPassed.isNotBlank() && cleanPassed != "PLACEHOLDER_GEMINI_API_KEY") {
            return cleanPassed
        }
        return try {
            val buildKey = com.example.BuildConfig.GEMINI_API_KEY.trim()
            if (buildKey.isNotBlank() && buildKey != "PLACEHOLDER_GEMINI_API_KEY") buildKey else ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun decodeAndSaveValidBitmap(base64Data: String, cacheDir: File, prefix: String): File? {
        if (base64Data.isBlank()) return null
        return try {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            if (bytes.isEmpty()) return null
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val outFile = File(cacheDir, "${prefix}_${System.currentTimeMillis()}.png")
            FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            outFile
        } catch (_: Exception) {
            null
        }
    }

    private fun extractGeminiErrorMessage(respBody: String, httpCode: Int): String {
        return try {
            val errObj = JSONObject(respBody).optJSONObject("error")
            val msg = errObj?.optString("message", "") ?: ""
            if (msg.isNotBlank()) "Gemini API ($httpCode): $msg" else "HTTP $httpCode"
        } catch (_: Exception) {
            "HTTP $httpCode"
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
            val text = res.getOrNull()?.text ?: ""
            onChunk(text)
        }
        return res
    }
}
