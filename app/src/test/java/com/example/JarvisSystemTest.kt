package com.example

import com.example.ai.AiNetworkUtils
import com.example.ai.AiResponse
import com.example.ai.ChatMessage
import com.example.ai.GeminiProvider
import com.example.data.battery.AiThrottleLevel
import com.example.data.battery.BatteryHealth
import com.example.data.battery.BatteryPowerState
import com.example.data.battery.PluggedSource
import com.example.data.model.ConversationEntity
import com.example.data.model.MemoryEntity
import com.example.data.prefs.PreferencesManager
import com.example.data.prefs.SecureStorage
import com.example.data.weather.LiveWeatherData
import com.example.voice.engine.AndroidNativeSpeechEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class JarvisSystemTest {

    @Test
    fun testMemoryEntityDefaults() {
        val memory = MemoryEntity(
            content = "User preference: Iron Man Armor Mark-LIV",
            category = MemoryEntity.CATEGORY_PREFERENCE,
            tags = "armor,suit,tactical",
            importance = 3
        )
        assertEquals("User preference: Iron Man Armor Mark-LIV", memory.content)
        assertEquals(MemoryEntity.CATEGORY_PREFERENCE, memory.category)
        assertEquals("armor,suit,tactical", memory.tags)
        assertEquals(3, memory.importance)
        assertTrue(memory.createdAt > 0L)
        assertTrue(memory.updatedAt > 0L)
    }

    @Test
    fun testConversationEntityCreation() {
        val message = ConversationEntity(
            sender = "USER",
            content = "Status report on power grid."
        )
        assertEquals("USER", message.sender)
        assertEquals("Status report on power grid.", message.content)
        assertNull(message.imageUrl)
    }

    @Test
    fun testAiProviderDefinitions() {
        val gemini = GeminiProvider()

        assertEquals("GEMINI", gemini.id)
        assertEquals("Google Gemini", gemini.displayName)
        assertEquals("gemini-3.5-flash-lite", gemini.defaultModel)
        assertEquals(5, gemini.supportedModels.size)

        assertTrue(gemini.supportedModels.contains("gemini-3.5-flash-lite"))
        assertTrue(gemini.supportedModels.contains("gemini-3.5-flash"))
        assertTrue(gemini.supportedModels.contains("gemini-3.8-flash"))
        assertTrue(gemini.supportedModels.contains("gemini-3.8-pro"))
        assertTrue(gemini.supportedModels.contains("gemini-3.8-flash-lite"))

        // Verify any custom model ID is preserved through normalizeModel
        assertEquals("gemini-3.5-flash-lite", gemini.normalizeModel("gemini 3.5 flash lite"))
        assertEquals("gemini-2.5-pro-preview-03-25", gemini.normalizeModel("models/gemini-2.5-pro-preview-03-25"))
        assertEquals("gemini-exp-1206", gemini.normalizeModel("gemini-exp-1206"))

        // Verify candidate endpoints include the target custom model and live endpoints
        val liteEndpoints = gemini.getEndpointCandidatesForVariant("gemini-3.5-flash-lite")
        assertTrue(liteEndpoints.isNotEmpty())
        assertTrue(liteEndpoints.contains("gemini-3.5-flash-lite"))
        assertTrue(liteEndpoints.contains("gemini-2.0-flash-lite"))
    }

    @Test
    fun testAiNetworkUtilsRateLimit() {
        val errorJson = "{\"error\":{\"message\":\"Rate limit exceeded. Please wait.\"}}"
        val msg = AiNetworkUtils.parseErrorMessage("Gemini", 429, errorJson)
        assertTrue(msg.contains("Rate Limit Exceeded // 429"))
        assertTrue(msg.contains("Rate limit exceeded"))
    }

    @Test
    fun testAiNetworkUtilsAuthenticationError() {
        val errorJson = "{\"error\":{\"message\":\"API key not valid.\"}}"
        val msg = AiNetworkUtils.parseErrorMessage("Gemini", 401, errorJson)
        assertTrue(msg.contains("Authentication Failed // 401"))

        val forbiddenMsg = AiNetworkUtils.parseErrorMessage("Gemini", 403, errorJson)
        assertTrue(forbiddenMsg.contains("Authentication Failed // 403"))
    }

    @Test
    fun testAiNetworkUtilsModelNotFound() {
        val errorJson = "{\"error\":{\"message\":\"models/gemini-invalid is not found for API version v1beta\"}}"
        val msg = AiNetworkUtils.parseErrorMessage("Gemini", 404, errorJson)
        assertTrue(msg.contains("Model Not Found // 404"))
        assertTrue(msg.contains("The selected model or endpoint was not found"))
    }

    @Test
    fun testAiNetworkUtilsBadRequest() {
        val errorJson = "{\"error\":{\"message\":\"Please ensure that multiturn talk alternates between user and model.\"}}"
        val msg = AiNetworkUtils.parseErrorMessage("Gemini", 400, errorJson)
        assertTrue(msg.contains("Bad Request // 400"))
    }

    @Test
    fun testAiNetworkUtilsHttpVersionError() {
        val msg = AiNetworkUtils.parseErrorMessage("Gemini", 505, "HTTP Version Not Supported")
        assertTrue(msg.contains("HTTP Version Error // 505"))
    }

    @Test
    fun testAiNetworkUtilsServerOutages() {
        val msg500 = AiNetworkUtils.parseErrorMessage("Gemini", 500, "{}")
        assertTrue(msg500.contains("Internal Server Error // 500"))

        val msg503 = AiNetworkUtils.parseErrorMessage("Gemini", 503, "{}")
        assertTrue(msg503.contains("Service Unavailable // 503"))
    }

    @Test
    fun testAiNetworkUtilsApiKeySanitization() {
        val sensitiveDetail = "Request failed for key=AIzaSyB_1234567890abcdef and Bearer sk-proj-supersecretkey"
        val sanitized = AiNetworkUtils.parseErrorMessage("Gemini", 400, sensitiveDetail)
        assertFalse(sanitized.contains("AIzaSyB_1234567890abcdef"))
        assertFalse(sanitized.contains("sk-proj-supersecretkey"))
        assertTrue(sanitized.contains("[REDACTED]"))
    }

    @Test
    fun testAiNetworkUtilsNetworkExceptions() {
        val timeoutEx = SocketTimeoutException("Read timed out")
        val timeoutMsg = AiNetworkUtils.parseException("Gemini", timeoutEx)
        assertTrue(timeoutMsg.contains("[Gemini Timeout]"))

        val offlineEx = UnknownHostException("Unable to resolve host")
        val offlineMsg = AiNetworkUtils.parseException("Gemini", offlineEx)
        assertTrue(offlineMsg.contains("[Gemini Offline]"))
    }

    @Test
    fun testPreferencesManagerConstants() {
        assertEquals("user_name", PreferencesManager.KEY_USER_NAME)
        assertEquals("gemini_api_key", PreferencesManager.KEY_GEMINI_KEY)
        assertEquals("gemini_model", PreferencesManager.KEY_GEMINI_MODEL)
        assertEquals("gemini-3.5-flash-lite", PreferencesManager.DEFAULT_MODEL)
        assertEquals("Ali Rahman", PreferencesManager.DEFAULT_USER_NAME)

        assertEquals(5, PreferencesManager.VALID_GEMINI_MODELS.size)
        assertTrue(PreferencesManager.VALID_GEMINI_MODELS.contains("gemini-3.5-flash-lite"))
        assertTrue(PreferencesManager.VALID_GEMINI_MODELS.contains("gemini-3.5-flash"))
        assertTrue(PreferencesManager.VALID_GEMINI_MODELS.contains("gemini-3.8-flash"))
        assertTrue(PreferencesManager.VALID_GEMINI_MODELS.contains("gemini-3.8-pro"))
        assertTrue(PreferencesManager.VALID_GEMINI_MODELS.contains("gemini-3.8-flash-lite"))
        assertTrue(PreferencesManager.VALID_OPENAI_MODELS.contains("gpt-4o"))
        assertTrue(PreferencesManager.VALID_GROQ_MODELS.contains("llama-3.3-70b-versatile"))

        assertEquals("gemini-2.5-flash", PreferencesManager.normalizeModelId("models/gemini-2.5-flash"))
        assertEquals("gemini-3.5-flash-lite", PreferencesManager.normalizeModelId("gemini 3.5 flash lite"))

        assertTrue(PreferencesManager.NEURAL_VOICES.contains("Natural Male (Tactical)"))
        assertTrue(PreferencesManager.NEURAL_VOICES.contains("Natural Female (Warm)"))
        assertTrue(PreferencesManager.NEURAL_VOICES.contains("System Default"))
    }

    @Test
    fun testSecureStorageMasking() {
        assertEquals("", SecureStorage.maskKey(""))
        assertEquals("••••••••", SecureStorage.maskKey("12345"))
        assertEquals("AIza••••••••cdef", SecureStorage.maskKey("AIzaSyD1234567890abcdef"))
        assertEquals("sk-p••••••••7890", SecureStorage.maskKey("sk-proj-1234567890"))
    }

    @Test
    fun testSpeechTextSanitization() {
        val markdownText = """
            # Tactical Briefing
            Here is the **status update** for `Mark-LIV`.
            ```kotlin
            val armor = "Mark-LIV"
            ```
            Check telemetry at https://defense.gov/telemetry for { "status": "active" }.
        """.trimIndent()

        val clean = AndroidNativeSpeechEngine.sanitizeTextForSpeech(markdownText)
        assertFalse(clean.contains("```"))
        assertFalse(clean.contains("val armor"))
        assertFalse(clean.contains("# Tactical"))
        assertFalse(clean.contains("**status update**"))
        assertFalse(clean.contains("https://defense.gov"))
        assertTrue(clean.contains("Tactical Briefing"))
        assertTrue(clean.contains("status update"))
        assertTrue(clean.contains("Code snippet omitted."))
        assertTrue(clean.contains("web link"))
    }

    @Test
    fun testSpeechTextChunking() {
        val longText = "First sentence of report. Second sentence provides situational analysis. Third sentence confirms radar grid is fully operational and telemetry locked."
        val chunks = AndroidNativeSpeechEngine.chunkText(longText, maxChunkSize = 60)

        assertTrue(chunks.isNotEmpty())
        for (chunk in chunks) {
            assertTrue("Chunk length should be <= 60 or single boundary", chunk.length <= 60)
            assertFalse(chunk.endsWith(" "))
            assertFalse(chunk.startsWith(" "))
        }
    }

    @Test
    fun testWeatherTelemetryTimestamps() {
        val now = System.currentTimeMillis()
        val weather = LiveWeatherData(
            city = "San Francisco",
            temperature = 20,
            condition = "Partly Cloudy",
            humidity = 65,
            isLive = true,
            isCached = false,
            lastUpdatedMillis = now
        )

        assertEquals("San Francisco", weather.city)
        assertEquals(20, weather.temperature)
        assertTrue(weather.isLive)
        assertFalse(weather.isCached)
        assertNull(weather.errorMessage)
        assertNotNull(weather.getFormattedLastUpdated())
        assertTrue(weather.getFormattedLastUpdated().isNotBlank())
    }

    @Test
    fun testBatteryPowerStateAndTelemetry() {
        val state = BatteryPowerState(
            level = 85,
            isCharging = true,
            isPlugged = true,
            pluggedSource = PluggedSource.AC_MAINS,
            pluggedSourceLabel = "AC MAINS",
            statusDescription = "CHARGING",
            health = BatteryHealth.GOOD,
            temperatureCelsius = 28.5f,
            voltageMv = 4150,
            isPowerSaveMode = false,
            isBatteryLow = false,
            isCriticalBattery = false,
            throttleLevel = AiThrottleLevel.UNCONSTRAINED,
            throttleReason = "Connected to AC MAINS. Unconstrained heuristics active."
        )
        assertEquals(85, state.level)
        assertTrue(state.isCharging)
        assertTrue(state.isPlugged)
        assertTrue(state.isOptimalForHeavyAi)
        assertEquals(AiThrottleLevel.UNCONSTRAINED, state.throttleLevel)
    }

    @Test
    fun testChatMessageDataClasses() {
        val userMsg = ChatMessage(role = "user", content = "Report status.")
        assertEquals("user", userMsg.role)
        assertEquals("Report status.", userMsg.content)

        val aiResp = AiResponse(text = "Systems nominal, Commander.")
        assertEquals("Systems nominal, Commander.", aiResp.text)
    }
}
