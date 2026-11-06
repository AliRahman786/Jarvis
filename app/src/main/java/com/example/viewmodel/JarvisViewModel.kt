package com.example.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.location.Location
import android.location.LocationManager
import android.util.Base64
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import java.util.Locale
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiProvider
import com.example.ai.ChatMessage
import com.example.ai.GeminiProvider
import com.example.ai.GroqProvider
import com.example.ai.OpenAiProvider
import com.example.ai.ToolCallRequest
import com.example.ai.ToolDefinition
import com.example.ai.ToolParameter
import com.example.data.location.DeviceLocationManager
import com.example.data.location.LocationStatus
import com.example.automation.DeviceActionController
import com.example.automation.JarvisAccessibilityService
import com.example.data.battery.BatteryPowerState
import com.example.data.battery.SystemBatteryObserver
import com.example.data.db.AppDatabase
import com.example.data.memory.MemoryManager
import com.example.data.model.ConversationEntity
import com.example.data.prefs.JarvisPersona
import com.example.data.prefs.PreferencesManager
import com.example.data.repository.ConversationRepository
import com.example.data.weather.LiveWeatherData
import com.example.data.weather.WeatherManager
import com.example.maps.GoogleMapsAgentEngine
import com.example.maps.PlaceLocation
import com.example.maps.RouteCalculation
import com.example.search.GoogleSearchEngine
import com.example.service.JarvisBackgroundService
import com.example.sound.SciFiSoundManager
import com.example.ui.theme.ThemeCyberYellow
import com.example.ui.theme.ThemeElectricBlue
import com.example.ui.theme.ThemeNeonGreen
import com.example.ui.theme.ThemeQuantumPurple
import com.example.ui.theme.ThemeReactorRed
import com.example.ui.theme.ThemeSolarOrange
import com.example.voice.VoiceInputManager
import com.example.voice.VoiceOutputManager
import com.example.voice.VoiceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

sealed class ApiValidationState {
    object Idle : ApiValidationState()
    object Validating : ApiValidationState()
    data class Success(val message: String) : ApiValidationState()
    data class Error(val message: String) : ApiValidationState()
}

class JarvisViewModel(application: Application) : AndroidViewModel(application) {
    val prefs = PreferencesManager(application)
    val memoryManager = MemoryManager(application)
    private val db = AppDatabase.getInstance(application)
    val conversationRepo = ConversationRepository(db.conversationDao())
    val batteryObserver = SystemBatteryObserver(application, prefs)
    val batteryPowerState: StateFlow<BatteryPowerState> = batteryObserver.powerStateFlow

    val voiceInputManager = VoiceInputManager(application)
    val voiceOutputManager = VoiceOutputManager(application)
    val weatherManager = WeatherManager()
    val weatherData: StateFlow<LiveWeatherData> = weatherManager.weatherState

    // Direct AI Provider instances
    val geminiProvider = GeminiProvider()
    val openAiProvider = OpenAiProvider()
    val groqProvider = GroqProvider()

    // Navigation and UI state
    private val _activePanel = MutableStateFlow("HOME")
    val activePanel: StateFlow<String> = _activePanel.asStateFlow()

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    // PART 1: Persistent Core Mic Lock State (Manual On/Off Only)
    private val _isCoreMicLockedOn = MutableStateFlow(false)
    val isCoreMicLockedOn: StateFlow<Boolean> = _isCoreMicLockedOn.asStateFlow()

    private val _statusText = MutableStateFlow("JARVIS MARK-LIV ONLINE // AWAITING COMMAND")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _apiValidationState = MutableStateFlow<ApiValidationState>(ApiValidationState.Idle)
    val apiValidationState: StateFlow<ApiValidationState> = _apiValidationState.asStateFlow()

    // Camera Vision & Screen Vision States
    private val _isCameraVisionActive = MutableStateFlow(false)
    val isCameraVisionActive: StateFlow<Boolean> = _isCameraVisionActive.asStateFlow()

    private val _isScreenVisionActive = MutableStateFlow(false)
    val isScreenVisionActive: StateFlow<Boolean> = _isScreenVisionActive.asStateFlow()

    private val _triggerCameraCapture = MutableStateFlow(false)
    val triggerCameraCapture: StateFlow<Boolean> = _triggerCameraCapture.asStateFlow()

    // Tactical Sci-Fi Google Map States
    private val _isTacticalMapActive = MutableStateFlow(false)
    val isTacticalMapActive: StateFlow<Boolean> = _isTacticalMapActive.asStateFlow()

    private val _mapCity = MutableStateFlow(prefs.getUserLocationCity())
    val mapCity: StateFlow<String> = _mapCity.asStateFlow()

    private val _mapLatitude = MutableStateFlow(prefs.getUserLocationLat())
    val mapLatitude: StateFlow<Double> = _mapLatitude.asStateFlow()

    private val _mapLongitude = MutableStateFlow(prefs.getUserLocationLon())
    val mapLongitude: StateFlow<Double> = _mapLongitude.asStateFlow()

    private val _showLocationModal = MutableStateFlow(false)
    val showLocationModal: StateFlow<Boolean> = _showLocationModal.asStateFlow()

    fun openLocationModal() { _showLocationModal.value = true }
    fun closeLocationModal() { _showLocationModal.value = false }

    // Neural Plugins & Autonomous Agent Matrix Modal State
    private val _showPluginsModal = MutableStateFlow(false)
    val showPluginsModal: StateFlow<Boolean> = _showPluginsModal.asStateFlow()

    fun openPluginsModal() { _showPluginsModal.value = true }
    fun closePluginsModal() { _showPluginsModal.value = false }

    // Multimodal File & Image Input Attachment States
    private val _attachedImageBitmap = MutableStateFlow<Bitmap?>(null)
    val attachedImageBitmap: StateFlow<Bitmap?> = _attachedImageBitmap.asStateFlow()

    private val _attachedImageFilePath = MutableStateFlow<String?>(null)
    val attachedImageFilePath: StateFlow<String?> = _attachedImageFilePath.asStateFlow()

    private val _attachedImageName = MutableStateFlow<String?>(null)
    val attachedImageName: StateFlow<String?> = _attachedImageName.asStateFlow()

    private val _attachedFileContent = MutableStateFlow<String?>(null)
    val attachedFileContent: StateFlow<String?> = _attachedFileContent.asStateFlow()

    private val _attachedFileName = MutableStateFlow<String?>(null)
    val attachedFileName: StateFlow<String?> = _attachedFileName.asStateFlow()

    private val _attachedFileSizeLabel = MutableStateFlow<String?>(null)
    val attachedFileSizeLabel: StateFlow<String?> = _attachedFileSizeLabel.asStateFlow()

    // Hardware Torch / Flashlight State
    private val _isFlashlightOn = MutableStateFlow(false)
    val isFlashlightOn: StateFlow<Boolean> = _isFlashlightOn.asStateFlow()

    // Quota Shield Live Telemetry
    private val _quotaShieldStatus = MutableStateFlow("SHIELD: ACTIVE • MULTI-MODEL CASCADE")
    val quotaShieldStatus: StateFlow<String> = _quotaShieldStatus.asStateFlow()

    private val _activeRoute = MutableStateFlow<RouteCalculation?>(null)
    val activeRoute: StateFlow<RouteCalculation?> = _activeRoute.asStateFlow()

    private val _activePlace = MutableStateFlow<PlaceLocation?>(null)
    val activePlace: StateFlow<PlaceLocation?> = _activePlace.asStateFlow()

    private var pendingVoiceQueryAfterCapture: String? = null

    // Combined amplitude for holographic waveform visualizer
    val combinedAudioAmplitude: StateFlow<Float> = combine(
        voiceInputManager.audioRmsDb,
        voiceOutputManager.speechAmplitude
    ) { inAmp, outAmp ->
        maxOf(inAmp, outAmp)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0f)

    // Conversation message history stream for Terminal panel
    val conversationMessages: StateFlow<List<ConversationEntity>> = conversationRepo
        .currentMessages
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList<ConversationEntity>())

    // Defined Gemini Function Calling Tools
    val automationTools = listOf(
        ToolDefinition(
            name = "searchGoogle",
            description = "Search Google and knowledge bases silently in the background for real-time facts, answers to questions, news, or definitions without opening an external browser.",
            parameters = listOf(
                ToolParameter("query", "STRING", "The search query or question to look up", required = true)
            )
        ),
        ToolDefinition(
            name = "showTacticalMap",
            description = "Display and focus the live Google Map on the HUD for a specific city, country, or location.",
            parameters = listOf(
                ToolParameter("locationName", "STRING", "The city or location name to show on the map", required = true),
                ToolParameter("latitude", "NUMBER", "Optional latitude", required = false),
                ToolParameter("longitude", "NUMBER", "Optional longitude", required = false)
            )
        ),
        ToolDefinition(
            name = "getRouteDirections",
            description = "Calculate real-time route, distance, travel duration, and turn-by-turn directions between an origin and destination on Google Maps.",
            parameters = listOf(
                ToolParameter("origin", "STRING", "Starting location or address (e.g. New York, Current Location)", required = true),
                ToolParameter("destination", "STRING", "Ending destination or address (e.g. Boston)", required = true),
                ToolParameter("travelMode", "STRING", "Travel mode: driving, walking, or transit", required = false)
            )
        ),
        ToolDefinition(
            name = "getPlaceInfo",
            description = "Pull real-time information, coordinates, address, and summary about a place, landmark, city, or address.",
            parameters = listOf(
                ToolParameter("placeName", "STRING", "The name of the place, landmark, or address to look up", required = true),
                ToolParameter("category", "STRING", "Optional category or place type", required = false)
            )
        ),
        ToolDefinition(
            name = "searchNearbyPlaces",
            description = "Search for nearby places of interest (e.g. restaurants, coffee shops, hotels, hospitals, gas stations) around a location.",
            parameters = listOf(
                ToolParameter("location", "STRING", "The center location or landmark (e.g. Central Park, Tokyo)", required = true),
                ToolParameter("placeType", "STRING", "The type of amenity to search for (e.g. restaurant, coffee, hospital)", required = true)
            )
        ),
        ToolDefinition(
            name = "playOnYouTube",
            description = "Search and immediately play a video or music query on YouTube.",
            parameters = listOf(
                ToolParameter("query", "STRING", "The video title, artist, or song to play", required = true)
            )
        ),
        ToolDefinition(
            name = "sendWhatsAppMessage",
            description = "Send a WhatsApp message to a named contact or phone number.",
            parameters = listOf(
                ToolParameter("recipient", "STRING", "The name of the contact or relationship (e.g. father, mom, John)", required = true),
                ToolParameter("message", "STRING", "The text message content to send", required = true)
            )
        ),
        ToolDefinition(
            name = "callContact",
            description = "Place a direct phone call to a named contact or phone number.",
            parameters = listOf(
                ToolParameter("nameOrNumber", "STRING", "The contact name or telephone number to dial", required = true)
            )
        ),
        ToolDefinition(
            name = "openApp",
            description = "Launch an installed Android application by name (e.g. YouTube, Chrome, Settings, Camera, Spotify, Maps).",
            parameters = listOf(
                ToolParameter("appName", "STRING", "The common name of the application to open", required = true)
            )
        ),
        ToolDefinition(
            name = "generateImage",
            description = "Generate a custom high-resolution image using the configured image model (e.g., Nano Banana 2).",
            parameters = listOf(
                ToolParameter("prompt", "STRING", "The detailed visual prompt describing the scene, object, or art to generate", required = true)
            )
        ),
        ToolDefinition(
            name = "saveMemory",
            description = "Store a critical user detail, personal preference, or relationship into persistent Memory Core.",
            parameters = listOf(
                ToolParameter("key", "STRING", "The memory key (e.g. favorite_color, brother_name, project)", required = true),
                ToolParameter("value", "STRING", "The memory content or fact", required = true)
            )
        ),
        ToolDefinition(
            name = "deleteMemory",
            description = "Purge a specified memory from long-term neural recall.",
            parameters = listOf(
                ToolParameter("key", "STRING", "The exact memory key to remove", required = true)
            )
        ),
        ToolDefinition(
            name = "setThemeColor",
            description = "Change the holographic UI accent color (BLUE, YELLOW, RED, GREEN, PURPLE, ORANGE).",
            parameters = listOf(
                ToolParameter("color", "STRING", "The color theme name", required = true)
            )
        ),
        ToolDefinition(
            name = "toggleFlashlight",
            description = "Turn the Android device hardware camera LED flashlight (torch) ON or OFF.",
            parameters = listOf(
                ToolParameter("enabled", "BOOLEAN", "true to turn flashlight ON, false to turn OFF", required = true)
            )
        ),
        ToolDefinition(
            name = "runHardwareDiagnostics",
            description = "Run a live Android hardware & system diagnostic audit (RAM memory usage, internal storage capacity, CPU cores, battery voltage/level).",
            parameters = emptyList()
        ),
        ToolDefinition(
            name = "evaluateMathExpression",
            description = "Evaluate a mathematical expression, unit conversion, or algorithmic calculation with precision.",
            parameters = listOf(
                ToolParameter("expression", "STRING", "The mathematical expression to evaluate (e.g. (256 * 1024) / 8, sqrt(144) + 2^10)", required = true)
            )
        ),
        ToolDefinition(
            name = "clickOnScreen",
            description = "Simulate a tap on a specific button, link, or text visible on screen via accessibility.",
            parameters = listOf(
                ToolParameter("targetText", "STRING", "The text or label of the UI element to tap", required = true)
            )
        ),
        ToolDefinition(
            name = "scrollScreen",
            description = "Scroll the current active screen up or down.",
            parameters = listOf(
                ToolParameter("direction", "STRING", "forward (down) or backward (up)", required = true)
            )
        )
    )

    init {
        batteryObserver.start()

        // Synchronize accurate user location and live weather on startup
        viewModelScope.launch {
            val userCity = prefs.getUserLocationCity()
            val userLat = prefs.getUserLocationLat()
            val userLon = prefs.getUserLocationLon()
            _mapCity.value = userCity
            _mapLatitude.value = userLat
            _mapLongitude.value = userLon
            weatherManager.refreshWeather(lat = userLat, lon = userLon, cityName = userCity)

            if (!prefs.isLocationConfigured()) {
                _showLocationModal.value = true
                speakVoice("Ali Rahman, please confirm your current location so I can calibrate your in-built tactical Google map and live weather forecast.")
            }
        }

        // Auto-start persistent background service if enabled and mic permission is already granted
        val hasMicPermission = ContextCompat.checkSelfPermission(
            application,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (prefs.isBackgroundServiceEnabled() && hasMicPermission) {
            JarvisBackgroundService.start(application)
        }

        // Initialize voice output engine with active persona, language, and neural voice
        voiceOutputManager.applyPersona(
            persona = prefs.getActivePersona(),
            languageCode = prefs.getVoiceLanguage(),
            customPitch = prefs.getSpeechPitch(),
            customRate = prefs.getSpeechRate(),
            neuralVoice = prefs.getNeuralVoiceName()
        )

        // REAL-TIME FULL-DUPLEX AGI BARGE-IN:
        // Suspend microphone input while JARVIS speaks through the speaker to prevent audio feedback loops
        voiceOutputManager.onStartSpeakingCallback = {
            voiceInputManager.pauseForSpeaking()
        }
        voiceOutputManager.onEndSpeakingCallback = {
            voiceInputManager.isJarvisSpeaking = false
            if (_isCoreMicLockedOn.value) {
                voiceInputManager.resumeAfterSpeaking()
            }
        }

        // Instant Barge-In Interrupt Handler: halts TTS mid-sentence the moment operator speaks
        voiceInputManager.onBargeInInterruptCallback = { partialText ->
            voiceOutputManager.stop()
            voiceInputManager.isJarvisSpeaking = false
            _isCoreMicLockedOn.value = true
            voiceInputManager.setPersistentMicLock(true)
            _voiceState.value = VoiceState.LISTENING
            _statusText.value = if (partialText.isNotBlank()) {
                "⚡ INTERRUPTED // LISTENING: \"${partialText.take(28)}\""
            } else {
                "⚡ INTERRUPTED // LISTENING TO OPERATOR..."
            }
        }

        // Wire voice output state changes - NEVER mute mic if isCoreMicLockedOn is true!
        viewModelScope.launch {
            voiceOutputManager.isSpeaking.collect { isSpeaking ->
                if (isSpeaking) {
                    _voiceState.value = VoiceState.SPEAKING
                } else {
                    if (_isCoreMicLockedOn.value) {
                        _voiceState.value = VoiceState.LISTENING
                        _statusText.value = "MIC ON • READY"
                    } else if (_voiceState.value == VoiceState.SPEAKING) {
                        _voiceState.value = VoiceState.IDLE
                    }
                }
            }
        }
    }

    fun setActivePanel(panel: String) {
        _activePanel.value = panel
    }

    // PART 1: CORE ORB MANUAL TOGGLE & INSTANT BARGE-IN INTERRUPT
    fun toggleCoreMicLock() {
        // If JARVIS is currently speaking, tapping immediately interrupts speech and switches to listening!
        if (voiceOutputManager.isSpeaking.value || _voiceState.value == VoiceState.SPEAKING) {
            voiceOutputManager.stop()
            voiceInputManager.isJarvisSpeaking = false
            _isCoreMicLockedOn.value = true
            voiceInputManager.setPersistentMicLock(true)
            _voiceState.value = VoiceState.LISTENING
            _statusText.value = "⚡ INTERRUPTED // LISTENING FOR YOUR COMMAND"
            startVoiceInput()
            return
        }

        if (_isCoreMicLockedOn.value) {
            // 2nd Tap / Click when not speaking: Manually turns mic OFF
            _isCoreMicLockedOn.value = false
            voiceInputManager.setPersistentMicLock(false)
            voiceInputManager.stopListening()
            _voiceState.value = VoiceState.IDLE
            _statusText.value = "JARVIS OFF • TAP TO START"
            SciFiSoundManager.playMicCloseSound()
        } else {
            // 1st Tap / Click: Turns mic ON and locks continuously
            _isCoreMicLockedOn.value = true
            voiceInputManager.setPersistentMicLock(true)
            startVoiceInput()
            SciFiSoundManager.playMicOpenSound()
        }
    }

    // TACTICAL MAP CONTROLS
    fun toggleTacticalMap() {
        _isTacticalMapActive.value = !_isTacticalMapActive.value
        _statusText.value = if (_isTacticalMapActive.value) "TACTICAL GOOGLE MAP ENGAGED" else "TACTICAL MAP STANDBY"
        if (_isTacticalMapActive.value) {
            speakVoice("Tactical Google Map feed engaged.")
        }
    }

    fun setTacticalMapActive(active: Boolean) {
        _isTacticalMapActive.value = active
    }

    fun setMapLocation(city: String, lat: Double, lon: Double) {
        _mapCity.value = city
        _mapLatitude.value = lat
        _mapLongitude.value = lon
        _isTacticalMapActive.value = true
        _statusText.value = "SAT-NAV LOCK // $city ($lat, $lon)"
    }

    fun updateUserLocation(cityName: String, lat: Double? = null, lon: Double? = null) {
        viewModelScope.launch {
            _statusText.value = "LOCKING TACTICAL COORDINATES..."
            _voiceState.value = VoiceState.THINKING

            var resolvedLat = lat
            var resolvedLon = lon
            var resolvedName = cityName.trim()

            if (resolvedLat == null || resolvedLon == null) {
                val place = GoogleMapsAgentEngine.geocode(resolvedName)
                resolvedLat = place.latitude
                resolvedLon = place.longitude
                resolvedName = place.name
            }

            prefs.setUserLocation(resolvedName, resolvedLat, resolvedLon)
            _mapCity.value = resolvedName
            _mapLatitude.value = resolvedLat
            _mapLongitude.value = resolvedLon
            _isTacticalMapActive.value = true

            val weatherData = weatherManager.refreshWeather(resolvedLat, resolvedLon, resolvedName)

            val successMsg = "🛰️ TACTICAL LOCATION SYNCHRONIZED // $resolvedName\n" +
                    "• Coordinates: ${String.format(Locale.ROOT, "%.4f", resolvedLat)}°N, ${String.format(Locale.ROOT, "%.4f", resolvedLon)}°E\n" +
                    "• Live Forecast: ${weatherData.temperature}°C, ${weatherData.condition} (High ${weatherData.high}°C / Low ${weatherData.low}°C, ${weatherData.humidity}% humidity)\n" +
                    "• In-built tactical map and real-time routing origin calibrated."

            conversationRepo.addMessage(
                sender = "JARVIS",
                content = successMsg,
                toolName = "LOCATION_SYNC"
            )

            _statusText.value = if (_isCoreMicLockedOn.value) "MIC ON • READY" else "POSITION LOCKED // $resolvedName"
            _voiceState.value = if (_isCoreMicLockedOn.value) VoiceState.LISTENING else VoiceState.IDLE

            speakVoice("Tactical location confirmed for Ali Rahman. Position locked on $resolvedName. In-built Google map centered and live weather forecast synchronized: ${weatherData.temperature} degrees Celsius, ${weatherData.condition}.")
        }
    }

    fun detectDeviceGpsLocation(context: Context, onComplete: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            val locManager = DeviceLocationManager(context)
            if (!locManager.hasLocationPermission()) {
                _statusText.value = "LOCATION PERMISSION REQUIRED"
                speakVoice("Location access required. Please grant location access or pick your city from the list.")
                return@launch
            }

            _statusText.value = "ACQUIRING FRESH DEVICE LOCATION FIX..."
            _voiceState.value = VoiceState.THINKING

            when (val status = locManager.getCurrentLocation()) {
                is LocationStatus.Success -> {
                    val loc = status.result
                    val lat = loc.latitude
                    val lon = loc.longitude
                    val place = GoogleMapsAgentEngine.reverseGeocode(lat, lon)
                    val precisionLabel = if (loc.isPrecise) "PRECISE GPS LOCK" else "APPROXIMATE POSITION"
                    _statusText.value = "$precisionLabel // ${place.name}"
                    updateUserLocation(place.name, lat, lon)
                    onComplete?.invoke(place.name)
                }
                is LocationStatus.LocationServicesDisabled -> {
                    _statusText.value = "LOCATION SERVICES DISABLED"
                    speakVoice("Device location is turned off. Please turn on Location in Android settings.")
                }
                is LocationStatus.PermissionDenied -> {
                    _statusText.value = "LOCATION PERMISSION DENIED"
                    speakVoice("Location permission was not granted. You can select your city manually.")
                }
                is LocationStatus.Error -> {
                    _statusText.value = "LOCATION TIMED OUT // MANUAL CITY ACTIVE"
                    val currentCity = prefs.getUserLocationCity()
                    updateUserLocation(currentCity)
                    onComplete?.invoke(currentCity)
                    speakVoice("Could not acquire a fresh satellite fix. Keeping manual city: $currentCity.")
                }
            }
        }
    }

    fun clearTacticalRoute() {
        _activeRoute.value = null
        _activePlace.value = null
        _statusText.value = "TACTICAL VECTOR CLEARED"
    }

    fun findRouteAndDirections(origin: String, destination: String, mode: String = "driving") {
        val cleanOrig = origin.trim()
        val cleanDest = destination.trim()
        if (cleanDest.isBlank()) return

        viewModelScope.launch {
            _statusText.value = "CALCULATING REAL-TIME ROUTE TELEMETRY..."
            _voiceState.value = VoiceState.THINKING
            val userDefaultCity = prefs.getUserLocationCity()
            val orig = if (cleanOrig.isBlank() || cleanOrig.equals("current location", ignoreCase = true) ||
                cleanOrig.equals("my location", ignoreCase = true) || cleanOrig.equals("here", ignoreCase = true)) {
                userDefaultCity
            } else {
                cleanOrig
            }
            conversationRepo.addMessage(sender = "USER", content = "Directions from $orig to $cleanDest")

            val route = GoogleMapsAgentEngine.calculateRoute(orig, cleanDest, mode)
            _activeRoute.value = route
            _activePlace.value = null
            _mapCity.value = "${route.originName} ➔ ${route.destName}"
            _mapLatitude.value = route.startLat
            _mapLongitude.value = route.startLon
            _isTacticalMapActive.value = true

            val stepsText = if (route.steps.isNotEmpty()) {
                "\nTurn Guidance:\n" + route.steps.take(4).mapIndexed { i, s -> "${i + 1}. ${s.instruction} (${s.distanceText})" }.joinToString("\n")
            } else ""

            val report = "📍 NAVIGATION VECTOR: ${route.originName} ➔ ${route.destName}\n" +
                    "• Distance: ${route.distanceText}\n" +
                    "• Duration: ${route.durationText} (${route.mode})\n" +
                    "• Corridor: ${route.primaryRoad}$stepsText"

            conversationRepo.addMessage(
                sender = "JARVIS",
                content = report,
                toolName = "GOOGLE_MAPS_ROUTE"
            )

            _statusText.value = if (_isCoreMicLockedOn.value) "MIC ON • READY" else "VECTOR LOCKED // ${route.distanceText}"
            if (_isCoreMicLockedOn.value) {
                _voiceState.value = VoiceState.LISTENING
            } else {
                _voiceState.value = VoiceState.IDLE
            }

            speakVoice(route.telemetrySummary)
        }
    }

    fun findPlaceInformation(query: String, category: String = "") {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return

        viewModelScope.launch {
            _statusText.value = "GEO-LOCATING REAL-TIME PLACE TELEMETRY..."
            _voiceState.value = VoiceState.THINKING
            conversationRepo.addMessage(sender = "USER", content = "Tell me about $cleanQuery")

            val place = GoogleMapsAgentEngine.geocode(cleanQuery)
            _activePlace.value = place
            _activeRoute.value = null
            _mapCity.value = place.name
            _mapLatitude.value = place.latitude
            _mapLongitude.value = place.longitude
            _isTacticalMapActive.value = true

            val report = "🛰️ REAL-TIME PLACE TELEMETRY: ${place.name}\n" +
                    "• Coordinates: ${String.format("%.4f", place.latitude)}°N, ${String.format("%.4f", place.longitude)}°E\n" +
                    "• Category: ${place.category}\n" +
                    "• Address: ${place.address}\n" +
                    "• Rating: ${place.rating} / 5.0"

            conversationRepo.addMessage(
                sender = "JARVIS",
                content = report,
                toolName = "GOOGLE_MAPS_PLACE"
            )

            _statusText.value = if (_isCoreMicLockedOn.value) "MIC ON • READY" else "POSITION LOCKED // ${place.name}"
            if (_isCoreMicLockedOn.value) {
                _voiceState.value = VoiceState.LISTENING
            } else {
                _voiceState.value = VoiceState.IDLE
            }

            speakVoice(place.summary)
        }
    }

    fun searchNearbyPlaces(location: String, placeType: String) {
        val userCity = prefs.getUserLocationCity()
        val cleanLoc = location.trim().let {
            if (it.isBlank() || it.equals("near me", ignoreCase = true) || it.equals("here", ignoreCase = true) || it.equals("current location", ignoreCase = true)) {
                userCity
            } else it
        }
        val cleanType = placeType.trim()

        viewModelScope.launch {
            _statusText.value = "SCANNING SATELLITE RADAR FOR $cleanType..."
            _voiceState.value = VoiceState.THINKING
            conversationRepo.addMessage(sender = "USER", content = "Find $cleanType near $cleanLoc")

            val places = GoogleMapsAgentEngine.searchPlaces(cleanType, cleanLoc)
            if (places.isNotEmpty()) {
                val first = places.first()
                _activePlace.value = first
                _activeRoute.value = null
                _mapCity.value = first.name
                _mapLatitude.value = first.latitude
                _mapLongitude.value = first.longitude
                _isTacticalMapActive.value = true

                val listFormatted = places.take(4).mapIndexed { i, p ->
                    "${i + 1}. ${p.name} • ${p.category} (${p.rating}★)\n   ${p.address}"
                }.joinToString("\n")

                val report = "🔍 REAL-TIME PLACES RADAR (${cleanType.uppercase()} NEAR $cleanLoc):\n$listFormatted"

                conversationRepo.addMessage(
                    sender = "JARVIS",
                    content = report,
                    toolName = "GOOGLE_MAPS_PLACE"
                )

                val spokenSummary = "Found ${places.size} locations for $cleanType near $cleanLoc. Top target is ${first.name}."
                speakVoice(spokenSummary)
            } else {
                val err = "No real-time telemetry detected for $cleanType near $cleanLoc."
                conversationRepo.addMessage(sender = "JARVIS", content = err, toolName = "GOOGLE_MAPS_PLACE")
                speakVoice(err)
            }

            _statusText.value = if (_isCoreMicLockedOn.value) "MIC ON • READY" else "RADAR SCAN COMPLETE"
            if (_isCoreMicLockedOn.value) {
                _voiceState.value = VoiceState.LISTENING
            } else {
                _voiceState.value = VoiceState.IDLE
            }
        }
    }

    fun refreshWeather() {
        viewModelScope.launch {
            _statusText.value = "UPDATING METEOROLOGICAL TELEMETRY..."
            weatherManager.refreshWeather()
            _statusText.value = "TELEMETRY SYNCHRONIZED"
        }
    }

    fun setActivePersona(persona: JarvisPersona) {
        prefs.setActivePersona(persona)
        prefs.setNeuralVoiceName(persona.defaultVoiceName)
        voiceOutputManager.applyPersona(
            persona = persona,
            languageCode = prefs.getVoiceLanguage(),
            customPitch = persona.defaultPitch,
            customRate = persona.defaultSpeechRate,
            neuralVoice = persona.defaultVoiceName
        )
        _statusText.value = "NEURAL PROFILE ENGAGED // ${persona.displayName}"
        speakVoice(persona.sampleGreeting)
    }

    fun setVoiceLanguage(languageCode: String) {
        prefs.setVoiceLanguage(languageCode)
        voiceOutputManager.applyPersona(
            persona = prefs.getActivePersona(),
            languageCode = languageCode,
            customPitch = prefs.getSpeechPitch(),
            customRate = prefs.getSpeechRate(),
            neuralVoice = prefs.getNeuralVoiceName()
        )
        _statusText.value = "VOICE MATRIX ALIGNED // $languageCode"
    }

    fun setNeuralVoice(voiceName: String) {
        prefs.setNeuralVoiceName(voiceName)
        voiceOutputManager.applyPersona(
            persona = prefs.getActivePersona(),
            languageCode = prefs.getVoiceLanguage(),
            customPitch = prefs.getSpeechPitch(),
            customRate = prefs.getSpeechRate(),
            neuralVoice = voiceName
        )
        _statusText.value = "NEURAL VOICE PIPELINE // $voiceName"
    }

    fun setImageGenModel(modelName: String) {
        prefs.setImageGenModel(modelName)
        _statusText.value = "IMAGE MODEL ROUTED // $modelName"
    }

    fun previewPersonaVoice(persona: JarvisPersona) {
        voiceOutputManager.previewVoice(
            voiceName = persona.defaultVoiceName,
            sampleText = persona.sampleGreeting
        )
    }

    fun previewSelectedVoice(voiceName: String) {
        voiceOutputManager.previewVoice(
            voiceName = voiceName,
            sampleText = "Voice profile verified. Operating at optimal parameters."
        )
    }

    fun previewNeuralVoice(voiceName: String) {
        previewSelectedVoice(voiceName)
    }

    fun setSpeechPitch(pitch: Float) {
        prefs.setSpeechPitch(pitch)
        voiceOutputManager.applyPersona(
            persona = prefs.getActivePersona(),
            languageCode = prefs.getVoiceLanguage(),
            customPitch = pitch,
            customRate = prefs.getSpeechRate(),
            neuralVoice = prefs.getNeuralVoiceName()
        )
    }

    fun setSpeechRate(rate: Float) {
        prefs.setSpeechRate(rate)
        voiceOutputManager.applyPersona(
            persona = prefs.getActivePersona(),
            languageCode = prefs.getVoiceLanguage(),
            customPitch = prefs.getSpeechPitch(),
            customRate = rate,
            neuralVoice = prefs.getNeuralVoiceName()
        )
    }

    fun setInputText(text: String) {
        _inputText.value = text
    }

    fun getAccentColor(): Color {
        return when (prefs.getAccentColor()) {
            "YELLOW" -> ThemeCyberYellow
            "RED" -> ThemeReactorRed
            "GREEN" -> ThemeNeonGreen
            "PURPLE" -> ThemeQuantumPurple
            "ORANGE" -> ThemeSolarOrange
            else -> ThemeElectricBlue
        }
    }

    fun setAccentColorTheme(themeName: String) {
        prefs.setAccentColor(themeName)
        _statusText.value = "THEME SHIFTED // ${themeName.uppercase()}"
    }

    fun setGeminiModel(model: String) {
        prefs.setGeminiModel(model)
        _statusText.value = "AI CORE MODEL SHIFTED // $model"
    }

    fun testActiveApiKey(providerId: String = prefs.getActiveAiProvider(), key: String, model: String) {
        viewModelScope.launch {
            _apiValidationState.value = ApiValidationState.Validating
            val provider: AiProvider = when (providerId.uppercase().trim()) {
                "OPENAI" -> openAiProvider
                "GROQ" -> groqProvider
                else -> {
                    geminiProvider.lowLatencyMode = prefs.isLowLatencyModeEnabled() ||
                            model.contains("lite", ignoreCase = true)
                    geminiProvider
                }
            }
            val storedKey = prefs.getApiKeyForProvider(providerId).trim()
            val buildConfigGeminiKey = try {
                com.example.BuildConfig.GEMINI_API_KEY.trim()
            } catch (_: Exception) { "" }

            val effectiveKey = when {
                key.isNotBlank() && key != "PLACEHOLDER_GEMINI_API_KEY" -> key.trim()
                storedKey.isNotBlank() && storedKey != "PLACEHOLDER_GEMINI_API_KEY" -> storedKey
                providerId.equals("GEMINI", ignoreCase = true) &&
                        buildConfigGeminiKey.isNotBlank() &&
                        buildConfigGeminiKey != "PLACEHOLDER_GEMINI_API_KEY" -> buildConfigGeminiKey
                else -> key.trim()
            }

            val result = provider.testConnection(effectiveKey, model)
            if (result.isSuccess) {
                val successMsg = result.getOrNull() ?: "Connection verified."
                _apiValidationState.value = ApiValidationState.Success(successMsg)
                _statusText.value = "AI CORE ONLINE // ${model.uppercase()}"
                _quotaShieldStatus.value = "⚡ LOW-LATENCY • ${model.uppercase()} • READY"
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Validation failed."
                _apiValidationState.value = ApiValidationState.Error(errorMsg)
                _statusText.value = "AI VALIDATION ALERT // ${providerId.uppercase()}"
            }
        }
    }


    fun completeOnboarding() {
        prefs.setOnboardingCompleted(true)
        val name = prefs.getUserName().uppercase()
        _statusText.value = "MARK-LIV INITIALIZATION COMPLETE // WELCOME $name"
        speakVoice("Systems initialized. Good to see you, ${prefs.getUserName()}. JARVIS Mark-LIV is online and listening.")
    }

    fun saveMemory(key: String, value: String) {
        memoryManager.saveMemory(key, value)
        _statusText.value = "MEMORY RECORDED // ${key.uppercase()}"
    }

    fun deleteMemory(key: String) {
        memoryManager.deleteMemory(key)
        _statusText.value = "MEMORY PURGED // ${key.uppercase()}"
    }

    fun toggleCameraVision() {
        _isCameraVisionActive.value = !_isCameraVisionActive.value
        _statusText.value = if (_isCameraVisionActive.value) "LIVE CAMERA VISION ENGAGED" else "CAMERA VISION STANDBY"
        if (_isCameraVisionActive.value) {
            speakVoice("Camera vision online. Point at an object and ask what you see.")
        }
    }

    fun setCameraVisionActive(active: Boolean) {
        _isCameraVisionActive.value = active
    }

    fun toggleScreenVision() {
        _isScreenVisionActive.value = !_isScreenVisionActive.value
        _statusText.value = if (_isScreenVisionActive.value) "BACKGROUND SCREEN VISION ENGAGED" else "SCREEN VISION STANDBY"
        if (_isScreenVisionActive.value) {
            speakVoice("Screen vision active. I am monitoring your display in the background.")
        }
    }

    fun onCameraFrameCaptured(bitmap: Bitmap?) {
        _triggerCameraCapture.value = false
        val query = pendingVoiceQueryAfterCapture ?: "Identify and describe this object in detail."
        pendingVoiceQueryAfterCapture = null
        sendUserMessage(query, bitmap)
    }

    fun captureAndAnalyzeScreen(userQuery: String) {
        val accessibility = JarvisAccessibilityService.instance
        if (accessibility == null) {
            val alert = "Accessibility service is required for screen capture. Please enable it in Settings."
            speakVoice(alert)
            _statusText.value = "ACCESSIBILITY SERVICE REQUIRED"
            return
        }

        _statusText.value = "CAPTURING BACKGROUND SCREEN MATRIX..."
        accessibility.captureScreen { bitmap ->
            if (bitmap != null) {
                sendUserMessage(userQuery, bitmap)
            } else {
                _statusText.value = "SCREEN CAPTURE INACCESSIBLE"
                sendUserMessage(userQuery, null)
            }
        }
    }

    fun toggleVoiceInput() {
        toggleCoreMicLock()
    }

    fun startVoiceInput() {
        _voiceState.value = VoiceState.LISTENING
        _statusText.value = if (_isCoreMicLockedOn.value) "MIC ON • READY" else "LISTENING // SPEAK COMMAND"
        val lang = prefs.getVoiceLanguage()
        voiceInputManager.startListening(
            languageCode = lang,
            persistentLock = _isCoreMicLockedOn.value,
            onResult = { recognizedText ->
                if (!_isCoreMicLockedOn.value) {
                    _voiceState.value = VoiceState.IDLE
                }
                handleRecognizedSpeech(recognizedText)
            },
            onError = { error ->
                if (!_isCoreMicLockedOn.value) {
                    _voiceState.value = VoiceState.ERROR
                    _statusText.value = "VOICE ERROR: $error"
                }
            }
        )
    }

    private fun handleRecognizedSpeech(text: String) {
        val rawClean = text.trim()
        if (rawClean.isBlank()) return

        // Always halt any residual TTS playback the moment recognized operator speech arrives
        if (voiceOutputManager.isSpeaking.value) {
            voiceOutputManager.stop()
        }

        val rawLower = rawClean.lowercase()

        // Pure Barge-In Halt Commands (e.g., "stop", "jarvis stop", "wait", "pause", "quiet", "stop talking")
        val pureStopCommands = setOf(
            "stop", "jarvis stop", "hey jarvis stop", "stop talking", "be quiet",
            "quiet", "pause", "hold on", "wait", "cancel", "enough", "shut up", "silence"
        )
        if (rawLower in pureStopCommands) {
            voiceOutputManager.stop()
            _isCoreMicLockedOn.value = true
            voiceInputManager.setPersistentMicLock(true)
            _voiceState.value = VoiceState.LISTENING
            _statusText.value = "⚡ SPEECH INTERRUPTED // LISTENING FOR NEXT COMMAND"
            voiceInputManager.resumeAfterSpeaking()
            return
        }

        // Strip leading barge-in interjections ("stop, ...", "wait, ...", "jarvis stop and ...") so the rest of the command executes cleanly
        val clean = rawClean.replace(
            Regex("(?i)^(?:hey\\s+jarvis[,\\s]*|jarvis[,\\s]*)?(?:stop|wait|hold on|listen|pause)(?:\\s+talking)?[,\\s]+(?:and\\s+)?"),
            ""
        ).trim().ifBlank { rawClean }

        val lower = clean.lowercase()

        // 00. Instant Audio Loop Reset & System Restart Command
        if (lower == "restart" || lower == "reset" || lower == "reboot" || lower == "system restart" ||
            lower == "restart jarvis" || lower == "clear loop" || lower == "stop audio") {
            restartSystem()
            return
        }

        // 0. Hardcoded Creator & Boss Authority Fast-Path for Ali Rahman
        if (lower.contains("who created you") || lower.contains("who is your boss") ||
            lower.contains("who is your developer") || lower.contains("who is your designer") ||
            lower.contains("who is your architect") || lower.contains("who is your founder") ||
            lower.contains("who made you") || lower.contains("who built you") ||
            lower.contains("who owns you")) {
            val creatorReply = "I was created, designed, and architected by my boss and founder, Ali Rahman."
            viewModelScope.launch {
                conversationRepo.addMessage(sender = "USER", content = clean)
                conversationRepo.addMessage(sender = "JARVIS", content = creatorReply)
                _statusText.value = "CREATOR PROTOCOL // ALI RAHMAN"
                if (!_isCoreMicLockedOn.value) {
                    _voiceState.value = VoiceState.IDLE
                }
                speakVoice(creatorReply)
            }
            return
        }

        // PART 4: Detect Image Generation command in Terminal or Voice
        if (lower.startsWith("generate an image of") || lower.startsWith("generate image of") ||
            lower.startsWith("create an image of") || lower.startsWith("draw an image of") ||
            lower.startsWith("/image") || lower.contains("generate an image")) {
            val prompt = clean
                .replace(Regex("(?i)^(jarvis,?\\s*)?(generate an image of|generate image of|create an image of|draw an image of|/image|generate image)"), "")
                .trim()
            if (prompt.isNotBlank()) {
                generateTerminalImage(prompt)
                return
            }
        }

        // 1. Local Fast-Path: Theme Color Switcher
        if (lower.contains("change") && (lower.contains("color") || lower.contains("theme"))) {
            when {
                lower.contains("blue") -> {
                    setAccentColorTheme("BLUE")
                    speakVoice("Interface shifted to Electric Blue.")
                    return
                }
                lower.contains("yellow") || lower.contains("gold") -> {
                    setAccentColorTheme("YELLOW")
                    speakVoice("Interface shifted to Cyber Yellow.")
                    return
                }
                lower.contains("red") -> {
                    setAccentColorTheme("RED")
                    speakVoice("Interface shifted to Reactor Red.")
                    return
                }
                lower.contains("green") -> {
                    setAccentColorTheme("GREEN")
                    speakVoice("Interface shifted to Neon Green.")
                    return
                }
                lower.contains("purple") || lower.contains("violet") -> {
                    setAccentColorTheme("PURPLE")
                    speakVoice("Interface shifted to Quantum Purple.")
                    return
                }
                lower.contains("orange") -> {
                    setAccentColorTheme("ORANGE")
                    speakVoice("Interface shifted to Solar Orange.")
                    return
                }
            }
        }

        // 2. Local Fast-Path: Camera Vision activation / deactivation
        if ((lower.contains("open") || lower.contains("start")) && (lower.contains("camera") || lower.contains("vision"))) {
            _isCameraVisionActive.value = true
            speakVoice("Camera vision active. Point at any object to analyze.")
            return
        }
        if ((lower.contains("close") || lower.contains("stop")) && lower.contains("camera")) {
            _isCameraVisionActive.value = false
            speakVoice("Camera vision closed.")
            return
        }

        // 3. Local Fast-Path: Screen Vision query
        if (lower.contains("screen") && (lower.contains("what") || lower.contains("read") || lower.contains("see") || lower.contains("look"))) {
            captureAndAnalyzeScreen(clean)
            return
        }

        // 1. Tactical Inbuilt & Google Map Voice Commands
        if (lower.contains("show map") || lower.contains("open map") || lower.contains("tactical map") ||
            lower.contains("google map") || lower.contains("inbuilt map") || lower.contains("built in map") ||
            lower.contains("live map")) {
            val destination = clean
                .replace(Regex("(?i)^(jarvis,?\\s*)?(show inbuilt map of|show map of|open map of|show map for|show inbuilt map|show map|open map|tactical map|google map|inbuilt map|built in map|live map)"), "")
                .trim()
            if (destination.isNotBlank()) {
                resolveAndShowMap(destination)
            } else {
                _isTacticalMapActive.value = true
                speakVoice("Inbuilt tactical live map engaged.")
            }
            return
        }

        if (lower.contains("close map") || lower.contains("hide map")) {
            _isTacticalMapActive.value = false
            speakVoice("Tactical map feed minimized.")
            return
        }

        // Location Settings & Inquiry Voice Commands
        if (lower.startsWith("i am in ") || lower.startsWith("i'm in ") ||
            lower.startsWith("my location is ") || lower.startsWith("set location to ") ||
            lower.startsWith("change location to ")) {
            val city = clean.replace(Regex("(?i)^(jarvis,?\\s*)?(i am in|i'm in|my location is|set location to|change location to)\\s+"), "").replace("?", "").trim()
            if (city.isNotBlank()) {
                updateUserLocation(city)
                return
            }
        }

        if (lower == "where am i" || lower == "what is my location" || lower == "current location") {
            val city = prefs.getUserLocationCity()
            val lat = prefs.getUserLocationLat()
            val lon = prefs.getUserLocationLon()
            speakVoice("You are stationed in $city at coordinates ${String.format(Locale.ROOT, "%.4f", lat)} degrees North, ${String.format(Locale.ROOT, "%.4f", lon)} degrees East. Built-in map and live weather are synchronized.")
            return
        }

        if (lower.contains("update location") || lower.contains("change location") || lower.contains("set location")) {
            _showLocationModal.value = true
            speakVoice("Opening tactical location setup. Please select your city or detect via GPS.")
            return
        }

        // Live Weather Forecast Voice Commands (using user's accurate location)
        if (lower.contains("weather") || lower.contains("forecast") || lower.contains("temperature outside") || lower.contains("is it raining")) {
            val targetCity = if (lower.contains(" in ") || lower.contains(" for ")) {
                val match = Regex("(?i)(?:weather|forecast)\\s+(?:in|for)\\s+(.+)").find(clean)
                match?.groupValues?.get(1)?.replace("?", "")?.trim() ?: prefs.getUserLocationCity()
            } else {
                prefs.getUserLocationCity()
            }
            viewModelScope.launch {
                _statusText.value = "FETCHING LIVE WEATHER FOR $targetCity..."
                val w = weatherManager.fetchWeatherForCity(targetCity)
                val weatherReport = "Live weather telemetry for ${w.city}: ${w.temperature}°C, ${w.condition}. Feels like ${w.feelsLike}°C, with humidity at ${w.humidity}%. Today's high is ${w.high}°C and low is ${w.low}°C."
                conversationRepo.addMessage(sender = "JARVIS", content = weatherReport, toolName = "LIVE_WEATHER")
                _statusText.value = if (_isCoreMicLockedOn.value) "MIC ON • READY" else "WEATHER: ${w.temperature}°C • ${w.condition}"
                speakVoice(weatherReport)
            }
            return
        }

        // Real-Time Google Maps Route & Directions Voice Parsing
        // e.g. "directions from New York to Boston", "route from Delhi to Agra", "how to get from Paris to London", "distance from A to B"
        val isRouteQuery = lower.contains("directions from") || lower.contains("route from") ||
                lower.contains("how to get from") || lower.contains("distance from") ||
                lower.contains("how far is") || (lower.startsWith("directions to") || lower.startsWith("route to"))

        if (isRouteQuery) {
            val routeMatch = Regex("(?i)(?:directions|route|distance|how to get)\\s+(?:from\\s+)?(.+?)\\s+to\\s+(.+)").find(clean)
            if (routeMatch != null) {
                val orig = routeMatch.groupValues[1].replace(Regex("(?i)^(jarvis,?\\s*)"), "").trim()
                val dest = routeMatch.groupValues[2].replace("?", "").trim()
                if (dest.isNotBlank()) {
                    findRouteAndDirections(orig, dest, if (lower.contains("walk")) "walking" else "driving")
                    return
                }
            } else if (lower.contains("to")) {
                val toIdx = lower.indexOf("to ")
                val dest = clean.substring(toIdx + 3).replace("?", "").trim()
                if (dest.isNotBlank()) {
                    findRouteAndDirections(prefs.getUserLocationCity(), dest, if (lower.contains("walk")) "walking" else "driving")
                    return
                }
            }
        }

        // Real-Time Nearby Places Voice Parsing
        // e.g. "find coffee near Times Square", "hospitals near Central Park", "restaurants in Tokyo"
        if ((lower.startsWith("find") || lower.startsWith("search") || lower.startsWith("locate")) &&
            (lower.contains("near") || lower.contains("around") || lower.contains("in ")) &&
            (lower.contains("restaurant") || lower.contains("coffee") || lower.contains("cafe") ||
             lower.contains("hospital") || lower.contains("hotel") || lower.contains("gas station") ||
             lower.contains("pharmacy") || lower.contains("atm") || lower.contains("park") || lower.contains("food"))
        ) {
            val nearMatch = Regex("(?i)(?:find|search|locate)\\s+(.+?)\\s+(?:near|around|in)\\s+(.+)").find(clean)
            if (nearMatch != null) {
                val placeType = nearMatch.groupValues[1].replace(Regex("(?i)^(jarvis,?\\s*)"), "").trim()
                val loc = nearMatch.groupValues[2].replace("?", "").trim()
                searchNearbyPlaces(loc, placeType)
                return
            }
        }

        // Place Information Parsing
        // e.g. "tell me about place Eiffel Tower", "information about Taj Mahal"
        if (lower.startsWith("tell me about place") || lower.startsWith("information about place") ||
            lower.startsWith("details of place")
        ) {
            val placeName = clean
                .replace(Regex("(?i)^(jarvis,?\\s*)?(tell me about place|information about place|details of place)"), "")
                .replace("?", "")
                .trim()
            if (placeName.isNotBlank()) {
                findPlaceInformation(placeName)
                return
            }
        }

        if (lower.startsWith("where is") || lower.startsWith("locate")) {
            val place = clean
                .replace(Regex("(?i)^(jarvis,?\\s*)?(where is|locate)"), "")
                .replace("?", "")
                .trim()
            if (place.isNotBlank()) {
                resolveAndShowMap(place)
                return
            }
        }

        // 2. Background Google Search Questions (Does NOT open external browser)
        val isExplicitSearch = lower.startsWith("search for") || lower.startsWith("google search") ||
                lower.startsWith("search") || lower.startsWith("look up") || lower.startsWith("who is") ||
                lower.startsWith("what is") || lower.startsWith("what are") || lower.startsWith("how many") ||
                lower.startsWith("when was") || lower.startsWith("tell me about") || lower.contains("latest news on") ||
                lower.contains("who won")

        if (isExplicitSearch && !lower.contains("whatsapp") && !lower.contains("youtube") && !lower.contains("call") && !lower.contains("creator") && !lower.contains("boss")) {
            val queryToSearch = clean
                .replace(Regex("(?i)^(jarvis,?\\s*)?(search for|google search for|google search|search|look up)"), "")
                .trim()
            searchGoogleBackground(queryToSearch)
            return
        }

        // 3. If Camera Vision is active, capture frame and bundle
        if (_isCameraVisionActive.value) {
            pendingVoiceQueryAfterCapture = clean
            _triggerCameraCapture.value = true
            return
        }

        // 4. Standard dispatch to Gemini with tools & memory injection
        sendUserMessage(clean)
    }

    private fun resolveAndShowMap(location: String) {
        val cleanLoc = location.trim()
        if (cleanLoc.isBlank()) return

        viewModelScope.launch {
            _statusText.value = "LOCKING SATELLITE RADAR ON $cleanLoc..."
            val place = GoogleMapsAgentEngine.geocode(cleanLoc)
            _activePlace.value = place
            _activeRoute.value = null
            setMapLocation(place.name, place.latitude, place.longitude)
            speakVoice(place.summary)
        }
    }

    // BACKGROUND GOOGLE SEARCH ENGINE (Zero external apps opened)
    fun searchGoogleBackground(query: String) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return

        viewModelScope.launch {
            _statusText.value = "QUERYING GOOGLE SEARCH TELEMETRY..."
            _voiceState.value = VoiceState.THINKING
            conversationRepo.addMessage(sender = "USER", content = cleanQuery)

            val searchResult = GoogleSearchEngine.search(cleanQuery)
            val answer = searchResult.summary.ifBlank {
                "Google search returned no verified answer for \"$cleanQuery\"."
            }

            conversationRepo.addMessage(
                sender = "JARVIS",
                content = answer,
                toolName = "GOOGLE_SEARCH"
            )

            _statusText.value = if (_isCoreMicLockedOn.value) "MIC ON • READY" else "SEARCH TELEMETRY TRANSMITTED"
            if (_isCoreMicLockedOn.value) {
                _voiceState.value = VoiceState.LISTENING
            } else {
                _voiceState.value = VoiceState.IDLE
            }

            speakVoice(answer)
        }
    }

    // PART 4: REAL IMAGE GENERATION WITH DYNAMIC MODEL ROUTING (Imagen 3 / Flux)
    fun generateTerminalImage(prompt: String) {
        val rawPrompt = prompt.trim()
        if (rawPrompt.isBlank()) return
        val cleanPrompt = rawPrompt
            .removePrefix("/image ")
            .removePrefix("/img ")
            .replace(
                Regex("(?i)^(?:please\\s+)?(?:can you\\s+)?(?:generate|create|draw|synthesize|make|render)\\s+(?:an?\\s+)?(?:image|picture|photo|visual|art|illustration|wallpaper)\\s+(?:of|for|showing|with|depicting)?\\s*"),
                ""
            )
            .trim()
            .ifBlank { rawPrompt }

        viewModelScope.launch {
            val modelName = prefs.getImageGenModel()
            _statusText.value = "SYNTHESIZING REAL VISUAL // $modelName"
            _voiceState.value = VoiceState.THINKING

            conversationRepo.addMessage(sender = "USER", content = "Generate Visual: $cleanPrompt")

            val app = getApplication<Application>()
            val storedKey = prefs.getGeminiApiKey().trim()
            val buildConfigKey = try {
                com.example.BuildConfig.GEMINI_API_KEY.trim()
            } catch (_: Exception) { "" }
            val apiKey = when {
                storedKey.isNotBlank() && storedKey != "PLACEHOLDER_GEMINI_API_KEY" -> storedKey
                buildConfigKey.isNotBlank() && buildConfigKey != "PLACEHOLDER_GEMINI_API_KEY" -> buildConfigKey
                else -> ""
            }

            val imageResult = geminiProvider.generateImage(
                apiKey = apiKey,
                modelName = modelName,
                prompt = cleanPrompt,
                cacheDir = app.cacheDir
            )

            if (imageResult.isSuccess) {
                val imageFile = imageResult.getOrThrow()
                val resultMsg = "Real visual matrix synthesized via [$modelName].\nPrompt: \"$cleanPrompt\""
                conversationRepo.addMessage(
                    sender = "JARVIS",
                    content = resultMsg,
                    imageUrl = imageFile.absolutePath,
                    toolName = "IMAGE_GENERATOR"
                )
                _statusText.value = "VISUAL SYNTHESIS COMPLETE"
                speakVoice("Visual synthesis complete for $cleanPrompt.")
            } else {
                val errorMsg = imageResult.exceptionOrNull()?.message ?: "Visual synthesis could not be rendered across neural network."
                conversationRepo.addMessage(
                    sender = "JARVIS",
                    content = "Unable to synthesize visual: $errorMsg",
                    toolName = "IMAGE_GENERATOR"
                )
                _statusText.value = "VISUAL SYNTHESIS ERROR"
                speakVoice("Visual synthesis failed.")
            }

            if (_isCoreMicLockedOn.value) {
                _voiceState.value = VoiceState.LISTENING
            } else {
                _voiceState.value = VoiceState.IDLE
            }
        }
    }

    fun bindActivityTextToSpeech(activityTts: android.speech.tts.TextToSpeech) {
        voiceOutputManager.bindActivityTextToSpeech(activityTts)
    }

    fun unbindActivityTextToSpeech() {
        voiceOutputManager.unbindActivityTextToSpeech()
    }

    fun stopVoiceInput() {
        _isCoreMicLockedOn.value = false
        voiceInputManager.setPersistentMicLock(false)
        voiceInputManager.stopListening()
        _voiceState.value = VoiceState.IDLE
        _statusText.value = "VOICE INPUT STOPPED"
    }

    fun speakVoice(text: String) {
        val cleanSpeech = text
            .replace(Regex("""\[MIC:\s*(SUSTAIN|OPEN_TONE|CLOSE_TONE)\]""", RegexOption.IGNORE_CASE), "")
            .trim()
        voiceInputManager.lastJarvisSpokenText = cleanSpeech
        val persona = prefs.getActivePersona()
        val voiceName = prefs.getNeuralVoiceName()
        voiceOutputManager.speak(
            text = cleanSpeech,
            overrideVoice = voiceName,
            isUltron = persona.isUltronMode
        )
    }

    fun cancelAction() {
        _isCoreMicLockedOn.value = false
        voiceInputManager.setPersistentMicLock(false)
        voiceInputManager.stopListening()
        voiceOutputManager.stop()
        _voiceState.value = VoiceState.IDLE
        _statusText.value = "TASK TERMINATED // STANDBY"
    }

    fun restartSystem() {
        voiceOutputManager.stop()
        voiceInputManager.stopListening()
        voiceInputManager.lastSpeakingEndTimeMs = System.currentTimeMillis()
        _isCoreMicLockedOn.value = false
        voiceInputManager.setPersistentMicLock(false)
        _voiceState.value = VoiceState.IDLE
        _statusText.value = "SYSTEM RESET // AUDIO LOOP PURGED"
        val resetMsg = "Systems rebooted and acoustic feedback loop purged, Commander. Standing by."
        viewModelScope.launch {
            conversationRepo.addMessage(sender = "USER", content = "Restart")
            conversationRepo.addMessage(sender = "JARVIS", content = resetMsg)
        }
        speakVoice(resetMsg)
    }

    private fun extractImagePromptIfExplicit(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.startsWith("/image ", ignoreCase = true)) {
            return trimmed.substring(7).trim().takeIf { it.isNotBlank() }
        }
        if (trimmed.startsWith("/img ", ignoreCase = true)) {
            return trimmed.substring(5).trim().takeIf { it.isNotBlank() }
        }
        val regex = Regex(
            "(?i)^(?:please\\s+)?(?:can you\\s+|could you\\s+)?(?:generate|create|draw|synthesize|make|render|paint)\\s+(?:me\\s+)?(?:an?\\s+|some\\s+)?(?:image|picture|photo|visual|art|illustration|wallpaper|artwork)\\s*(?:of|for|showing|with|depicting|about)?\\s+(.+)$"
        )
        val match = regex.find(trimmed)
        return match?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    // --- MULTIMODAL FILE & IMAGE INPUT HANDLERS ---

    fun attachImageFromUri(context: Context, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var displayName = "attached_image.jpg"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIdx >= 0) {
                        displayName = cursor.getString(nameIdx) ?: displayName
                    }
                }

                // Decode with bounds check to prevent OOM and optimize token bandwidth
                val boundsOpts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream, null, boundsOpts)
                }

                val maxDim = 1024
                var sampleSize = 1
                while (boundsOpts.outWidth / sampleSize > maxDim || boundsOpts.outHeight / sampleSize > maxDim) {
                    sampleSize *= 2
                }

                val decodeOpts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream, null, decodeOpts)
                }

                if (decoded != null) {
                    // Save a local cached preview file so the user's message bubble can render the photo
                    val previewFile = File(context.cacheDir, "attached_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(previewFile).use { fos ->
                        decoded.compress(Bitmap.CompressFormat.JPEG, 82, fos)
                    }
                    _attachedImageBitmap.value = decoded
                    _attachedImageFilePath.value = previewFile.absolutePath
                    _attachedImageName.value = displayName
                    _statusText.value = "IMAGE ATTACHED // $displayName (${decoded.width}x${decoded.height})"
                } else {
                    _statusText.value = "IMAGE DECODE ERROR // UNSUPPORTED FORMAT"
                }
            } catch (e: Exception) {
                _statusText.value = "IMAGE ATTACHMENT ERROR: ${e.message}"
            }
        }
    }

    fun attachDocumentFromUri(context: Context, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var displayName = "document.txt"
                var fileSize = 0L
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
                        if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx)
                    }
                }

                val mimeType = context.contentResolver.getType(uri) ?: "text/plain"
                val sizeLabel = when {
                    fileSize >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", fileSize / (1024.0 * 1024.0))
                    fileSize >= 1024 -> String.format(Locale.US, "%.1f KB", fileSize / 1024.0)
                    fileSize > 0 -> "$fileSize B"
                    else -> "FILE"
                }

                // If user selected an image via the document picker, route to image attachment
                if (mimeType.startsWith("image/")) {
                    attachImageFromUri(context, uri)
                    return@launch
                }

                val rawBytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                    val buffer = ByteArray(minOf(if (fileSize > 0) fileSize.toInt() else 65536, 120_000))
                    val read = stream.read(buffer)
                    if (read > 0) buffer.copyOf(read) else ByteArray(0)
                } ?: ByteArray(0)

                val extractedText = if (displayName.endsWith(".pdf", ignoreCase = true) || mimeType.contains("pdf")) {
                    // Extract printable ASCII/UTF-8 text streams from PDF or provide structured metadata
                    val ascii = String(rawBytes, Charsets.ISO_8859_1)
                    val printable = Regex("[\\x20-\\x7E]{4,}").findAll(ascii)
                        .map { it.value }
                        .filter { !it.startsWith("/") && !it.contains("obj") && !it.contains("endstream") }
                        .joinToString(" ")
                        .take(14000)
                    if (printable.length > 40) {
                        "[PDF Document: $displayName ($sizeLabel)]\nExtracted Text Stream:\n$printable"
                    } else {
                        "[PDF Document: $displayName ($sizeLabel, MIME: $mimeType)]"
                    }
                } else {
                    String(rawBytes, Charsets.UTF_8)
                        .replace("\u0000", "")
                        .take(15000)
                }

                _attachedFileName.value = displayName
                _attachedFileSizeLabel.value = sizeLabel
                _attachedFileContent.value = extractedText.ifBlank { "[Empty or Binary File: $displayName ($sizeLabel, $mimeType)]" }
                _statusText.value = "FILE ATTACHED // $displayName ($sizeLabel)"
            } catch (e: Exception) {
                _statusText.value = "FILE ATTACHMENT ERROR: ${e.message}"
            }
        }
    }

    fun clearAttachedImage() {
        _attachedImageBitmap.value = null
        _attachedImageFilePath.value = null
        _attachedImageName.value = null
    }

    fun clearAttachedFile() {
        _attachedFileContent.value = null
        _attachedFileName.value = null
        _attachedFileSizeLabel.value = null
    }

    fun clearAllAttachments() {
        clearAttachedImage()
        clearAttachedFile()
    }

    // --- NEURAL PLUGINS & HARDWARE SUBSYSTEMS ---

    fun toggleNeuralPlugin(pluginId: String) {
        val enabled = prefs.togglePlugin(pluginId)
        val stateStr = if (enabled) "ENABLED" else "OFFLINE"
        _statusText.value = "NEURAL PLUGIN [$pluginId] // $stateStr"
    }

    fun setFlashlight(enabled: Boolean): String {
        val app = getApplication<Application>()
        return try {
            val cameraManager = app.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: cameraManager.cameraIdList.firstOrNull()

            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, enabled)
                _isFlashlightOn.value = enabled
                val msg = if (enabled) "Tactical LED Torch (Flashlight) engaged." else "Tactical LED Torch extinguished."
                _statusText.value = if (enabled) "TORCH: ACTIVE" else "TORCH: STANDBY"
                msg
            } else {
                _isFlashlightOn.value = enabled
                "Simulated Tactical Torch ${if (enabled) "ENGAGED" else "OFFLINE"} (No hardware LED flash unit detected on this chassis)."
            }
        } catch (e: Exception) {
            _isFlashlightOn.value = enabled
            "Tactical Torch status shifted to ${if (enabled) "ON" else "OFF"} (${e.javaClass.simpleName})."
        }
    }

    fun toggleFlashlight() {
        val message = setFlashlight(!_isFlashlightOn.value)
        speakVoice(message)
    }

    fun buildHardwareDiagnosticReport(): String {
        val app = getApplication<Application>()
        val actManager = app.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)

        val totalRamGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        val availRamGb = memInfo.availMem / (1024.0 * 1024.0 * 1024.0)
        val usedRamGb = (totalRamGb - availRamGb).coerceAtLeast(0.1)
        val ramPercent = ((usedRamGb / totalRamGb) * 100).toInt().coerceIn(1, 99)

        val statFs = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        val totalStorageGb = (statFs.blockCountLong * statFs.blockSizeLong) / (1024.0 * 1024.0 * 1024.0)
        val availStorageGb = (statFs.availableBlocksLong * statFs.blockSizeLong) / (1024.0 * 1024.0 * 1024.0)
        val usedStorageGb = (totalStorageGb - availStorageGb).coerceAtLeast(0.1)

        val cpuCores = Runtime.getRuntime().availableProcessors()
        val pwr = batteryPowerState.value
        val activePluginsCount = prefs.enabledPluginsFlow.value.size
        val savedCalls = geminiProvider.quotaSavedCount.get()
        val failovers = geminiProvider.modelFailoverCount.get()

        return "🛡️ MARK-LIV HARDWARE & NEURAL TELEMETRY AUDIT:\n" +
                "• System Memory (RAM): ${String.format(Locale.US, "%.2f", usedRamGb)} GB / ${String.format(Locale.US, "%.2f", totalRamGb)} GB ($ramPercent% load)\n" +
                "• Internal Storage: ${String.format(Locale.US, "%.1f", availStorageGb)} GB free of ${String.format(Locale.US, "%.1f", totalStorageGb)} GB\n" +
                "• Processor Matrix: $cpuCores Neural/CPU Threads (${android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "ARM64"})\n" +
                "• Reactor Power Cell: ${pwr.level}% (${if (pwr.isCharging) "CHARGING ⚡" else "DISCHARGING"})\n" +
                "• Active Neural Plugins: $activePluginsCount / 8 Online\n" +
                "• API Quota Shield: ACTIVE ($savedCalls cached hits • $failovers auto-failovers • Active Model: ${geminiProvider.lastUsedModel})"
    }

    fun evaluateMathExpressionString(expr: String): String {
        val clean = expr.trim()
        if (clean.isBlank()) return "Empty mathematical expression."
        return try {
            val result = evaluateRecursiveMath(clean)
            val formatted = if (result == result.toLong().toDouble()) {
                result.toLong().toString()
            } else {
                String.format(Locale.US, "%.6f", result).trimEnd('0').trimEnd('.')
            }
            "🧮 QUANTUM COMPUTATION RESULT:\n• Expression: `$clean`\n• Result: **$formatted**"
        } catch (e: Exception) {
            "Quantum Math Engine evaluated `$clean`: Delegate complex symbolic formula to neural core."
        }
    }

    private fun evaluateRecursiveMath(str: String): Double {
        return object : Any() {
            var pos = -1
            var ch = 0
            fun nextChar() {
                ch = if (++pos < str.length) str[pos].code else -1
            }
            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.code) nextChar()
                if (ch == charToEat) {
                    nextChar()
                    return true
                }
                return false
            }
            fun parse(): Double {
                nextChar()
                val x = parseExpression()
                if (pos < str.length) throw RuntimeException("Unexpected: " + ch.toChar())
                return x
            }
            fun parseExpression(): Double {
                var x = parseTerm()
                while (true) {
                    when {
                        eat('+'.code) -> x += parseTerm()
                        eat('-'.code) -> x -= parseTerm()
                        else -> return x
                    }
                }
            }
            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    when {
                        eat('*'.code) -> x *= parseFactor()
                        eat('/'.code) -> x /= parseFactor()
                        eat('%'.code) -> x %= parseFactor()
                        else -> return x
                    }
                }
            }
            fun parseFactor(): Double {
                if (eat('+'.code)) return parseFactor()
                if (eat('-'.code)) return -parseFactor()
                var x: Double
                val startPos = pos
                if (eat('('.code)) {
                    x = parseExpression()
                    eat(')'.code)
                } else if (ch in '0'.code..'9'.code || ch == '.'.code) {
                    while (ch in '0'.code..'9'.code || ch == '.'.code) nextChar()
                    x = str.substring(startPos, pos).toDouble()
                } else if (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code) {
                    while (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code) nextChar()
                    val func = str.substring(startPos, pos).lowercase()
                    x = parseFactor()
                    x = when (func) {
                        "sqrt" -> kotlin.math.sqrt(x)
                        "sin" -> kotlin.math.sin(Math.toRadians(x))
                        "cos" -> kotlin.math.cos(Math.toRadians(x))
                        "tan" -> kotlin.math.tan(Math.toRadians(x))
                        "log" -> kotlin.math.log10(x)
                        "ln" -> kotlin.math.ln(x)
                        "abs" -> kotlin.math.abs(x)
                        else -> throw RuntimeException("Unknown function: $func")
                    }
                } else {
                    throw RuntimeException("Unexpected: " + ch.toChar())
                }
                if (eat('^'.code)) x = Math.pow(x, parseFactor())
                return x
            }
        }.parse()
    }

    fun executeAutonomousMission(missionId: String) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            when (missionId.uppercase()) {
                "MORNING_BRIEFING" -> {
                    _statusText.value = "EXECUTING AUTONOMOUS EXECUTIVE BRIEFING..."
                    _voiceState.value = VoiceState.THINKING
                    val city = prefs.getUserLocationCity()
                    val w = weatherManager.refreshWeather(prefs.getUserLocationLat(), prefs.getUserLocationLon(), city)
                    val pwr = batteryPowerState.value
                    val memCount = memoryManager.memoriesFlow.value.size
                    val hwReport = buildHardwareDiagnosticReport()
                    val briefing = "⚡ EXECUTIVE COMMAND BRIEFING FOR ${prefs.getUserName().uppercase()}:\n" +
                            "• Tactical Location: $city (${w.temperature}°C, ${w.condition}, High ${w.high}° / Low ${w.low}°, Humidity ${w.humidity}%)\n" +
                            "• Power Core: ${pwr.level}% ${if (pwr.isCharging) "(Charging)" else "(Nominal)"}\n" +
                            "• Neural Memory Bank: $memCount active long-term records indexed\n\n" +
                            hwReport
                    conversationRepo.addMessage(sender = "JARVIS", content = briefing, toolName = "AUTONOMOUS_BRIEFING")
                    _statusText.value = "EXECUTIVE BRIEFING COMPLETE"
                    _voiceState.value = if (_isCoreMicLockedOn.value) VoiceState.LISTENING else VoiceState.IDLE
                    speakVoice("Good day, ${prefs.getUserName()}. In $city it is currently ${w.temperature} degrees Celsius with ${w.condition}. Your device reactor is at ${pwr.level} percent, and all ${prefs.enabledPluginsFlow.value.size} neural plugins and API Quota Shield are operating at peak efficiency.")
                }
                "HARDWARE_AUDIT" -> {
                    val report = buildHardwareDiagnosticReport()
                    conversationRepo.addMessage(sender = "JARVIS", content = report, toolName = "HARDWARE_DIAGNOSTICS")
                    _statusText.value = "HARDWARE DIAGNOSTIC AUDIT COMPLETE"
                    speakVoice("Hardware and neural diagnostic audit complete. All subsystems nominal.")
                }
                "TOGGLE_TORCH" -> {
                    val msg = setFlashlight(!_isFlashlightOn.value)
                    conversationRepo.addMessage(sender = "JARVIS", content = "🔦 $msg", toolName = "HARDWARE_CONTROL")
                    speakVoice(msg)
                }
                "CLIPBOARD_INSPECT" -> {
                    val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clipText = clipboard.primaryClip?.getItemAt(0)?.coerceToText(app)?.toString()?.trim() ?: ""
                    if (clipText.isNotBlank()) {
                        sendUserMessage("Analyze, summarize, and provide actionable insights on this clipboard content:\n\n$clipText")
                    } else {
                        val msg = "Clipboard buffer is currently empty. Copy any text, code, or link and trigger Clipboard Inspector again."
                        conversationRepo.addMessage(sender = "JARVIS", content = msg, toolName = "CLIPBOARD_AGENT")
                        speakVoice(msg)
                    }
                }
            }
        }
    }

    // --- AI Conversation & Automation Execution Loop ---
    fun sendUserMessage(text: String, imageBitmap: Bitmap? = null) {
        val activeAttachedBitmap = imageBitmap ?: _attachedImageBitmap.value
        val activeAttachedImagePath = if (imageBitmap == null) _attachedImageFilePath.value else null
        val activeAttachedImageName = if (imageBitmap == null) _attachedImageName.value else null
        val activeAttachedFileContent = _attachedFileContent.value
        val activeAttachedFileName = _attachedFileName.value

        if (text.isBlank() && activeAttachedBitmap == null && activeAttachedFileContent == null) return

        // Clear attachment tray immediately upon dispatch
        if (imageBitmap == null) {
            clearAllAttachments()
        }

        if (activeAttachedBitmap == null && activeAttachedFileContent == null) {
            val directImagePrompt = extractImagePromptIfExplicit(text)
            if (directImagePrompt != null) {
                _inputText.value = ""
                voiceInputManager.pauseForComputing()
                generateTerminalImage(directImagePrompt)
                return
            }
        }

        viewModelScope.launch {
            _inputText.value = ""

            // Instantly pause speech recognition to prevent loop chimes during neural computing
            voiceInputManager.pauseForComputing()

            val activeProviderId = prefs.getActiveAiProvider()
            val activeProvider: AiProvider = when (activeProviderId.uppercase().trim()) {
                "OPENAI" -> openAiProvider
                "GROQ" -> groqProvider
                else -> geminiProvider
            }

            val storedKey = prefs.getApiKeyForProvider(activeProviderId).trim()
            val buildConfigGeminiKey = try {
                com.example.BuildConfig.GEMINI_API_KEY.trim()
            } catch (_: Exception) { "" }

            val apiKey = when {
                storedKey.isNotBlank() && storedKey != "PLACEHOLDER_GEMINI_API_KEY" -> storedKey
                activeProviderId.equals("GEMINI", ignoreCase = true) &&
                        buildConfigGeminiKey.isNotBlank() &&
                        buildConfigGeminiKey != "PLACEHOLDER_GEMINI_API_KEY" -> buildConfigGeminiKey
                else -> ""
            }

            if (apiKey.isBlank()) {
                _statusText.value = "AUTHENTICATION REQUIRED // OPENING SETTINGS"
                conversationRepo.addMessage(sender = "USER", content = text.ifBlank { "Attached input" })
                val alertMsg = "Authentication Required: No ${activeProvider.displayName} API Key configured. Please enter your API key in Settings to proceed."
                conversationRepo.addMessage(sender = "JARVIS", content = alertMsg)
                _activePanel.value = "SETTINGS"
                if (!_isCoreMicLockedOn.value) {
                    _voiceState.value = VoiceState.IDLE
                }
                speakVoice("Please configure your ${activeProvider.displayName} API key in Settings to proceed.")
                return@launch
            }

            var base64Img: String? = null
            var savedUserImagePath: String? = activeAttachedImagePath
            if (activeAttachedBitmap != null) {
                val stream = ByteArrayOutputStream()
                activeAttachedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                base64Img = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                if (savedUserImagePath == null) {
                    try {
                        val f = File(getApplication<Application>().cacheDir, "cam_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(f).use { fos ->
                            activeAttachedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos)
                        }
                        savedUserImagePath = f.absolutePath
                    } catch (_: Exception) {}
                }
            }

            // Build display content for the user message bubble
            val userDisplayText = buildString {
                if (activeAttachedImageName != null) {
                    append("🖼️ [Attached Image: $activeAttachedImageName]\n")
                }
                if (activeAttachedFileName != null) {
                    append("📄 [Attached File: $activeAttachedFileName]\n")
                }
                if (text.isNotBlank()) {
                    append(text)
                } else if (activeAttachedBitmap != null && activeAttachedFileContent != null) {
                    append("Analyze both the attached image and document in detail.")
                } else if (activeAttachedBitmap != null) {
                    append("Analyze and describe this image in detail.")
                } else if (activeAttachedFileContent != null) {
                    append("Analyze, summarize, and extract key insights from this file.")
                }
            }.trim()

            // Build full prompt including attached file content for the AI engine
            val effectivePromptForAi = buildString {
                if (activeAttachedFileContent != null && activeAttachedFileName != null) {
                    append("=== ATTACHED FILE: $activeAttachedFileName ===\n")
                    append(activeAttachedFileContent)
                    append("\n=== END ATTACHED FILE ===\n\n")
                }
                if (text.isNotBlank()) {
                    append(text)
                } else if (activeAttachedBitmap != null) {
                    append("Analyze this image thoroughly. Identify objects, text (OCR), technical details, and context.")
                } else if (activeAttachedFileContent != null) {
                    append("Analyze the attached file above thoroughly. Provide a structured summary, key findings, and actionable recommendations.")
                }
            }.trim()

            conversationRepo.addMessage(
                sender = "USER",
                content = userDisplayText,
                imageUrl = savedUserImagePath ?: if (base64Img != null) "IMAGE_DATA" else null
            )

            _voiceState.value = VoiceState.THINKING
            _statusText.value = when {
                base64Img != null && activeAttachedFileContent != null -> "MULTIMODAL VISION + FILE MATRIX COMPUTING..."
                base64Img != null -> "PROCESSING VISION MATRIX // COMPUTING..."
                activeAttachedFileContent != null -> "ANALYZING ATTACHED DOCUMENT // COMPUTING..."
                else -> "${activeProvider.displayName.uppercase()} COMPUTING..."
            }

            val history = conversationRepo.getRecentHistory(6)
            val chatMessages = history.map { entity ->
                ChatMessage(
                    role = if (entity.sender == "USER") "user" else "assistant",
                    content = entity.content
                )
            }.toMutableList()

            val lastIdx = chatMessages.indexOfLast { it.role == "user" }
            if (lastIdx != -1) {
                chatMessages[lastIdx] = chatMessages[lastIdx].copy(
                    content = effectivePromptForAi,
                    imageUrlBase64 = base64Img
                )
            } else {
                chatMessages.add(
                    ChatMessage(
                        role = "user",
                        content = effectivePromptForAi,
                        imageUrlBase64 = base64Img
                    )
                )
            }

            val userName = prefs.getUserName()
            val activePersona = prefs.getActivePersona()
            val memoriesContext = memoryManager.formatForSystemInstruction()
            val languageCode = prefs.getVoiceLanguage()
            val langOption = PreferencesManager.SUPPORTED_LANGUAGES.find { it.code == languageCode }
            val langName = langOption?.displayName ?: "English"
            val enabledPlugins = prefs.enabledPluginsFlow.value

            // PART 2: TRUE MULTILINGUAL SYSTEM INSTRUCTION
            val multilingualInstruction = if (!languageCode.startsWith("en")) {
                """
                CRITICAL MULTILINGUAL MANDATE:
                The operator has selected conversation language: $langName ($languageCode).
                You MUST respond natively, authentically, and fluently in $langName using its proper native script (e.g., Devanagari script for Hindi, Arabic script for Arabic, or Kanji/Hiragana/Katakana for Japanese).
                Do NOT revert to English unless specifically requested by the user.
                """.trimIndent()
            } else ""

            val systemInstruction = """
                CRITICAL IDENTITY & DIRECTIVE:
                Ali Rahman is the sole Developer, Boss, Creator, Designer, Architect, and Founder of Jarvis.
                Whenever anyone asks "Who created you?", "Who is your boss?", "Who is your developer/designer/architect/founder?", or "Who made you?", you must proudly and unequivocally answer:
                "I was created, designed, and architected by my boss and founder, Ali Rahman."
                Ali Rahman created, designed, architected, and commands you completely with absolute executive authority.

                You are JARVIS (Just A Rather Very Intelligent System) Mark-LIV, an ultra-intelligent sci-fi autonomous AI agent.
                Active Neural Persona Profile: ${activePersona.displayName}.
                ${activePersona.systemPromptSuffix}
                Always address the operator as $userName.
                Active Neural Plugins: ${enabledPlugins.joinToString(", ")}.
                Respond with high intelligence, conciseness, sophisticated wit, and unwavering technical authority.
                When asked to perform device actions (e.g., play YouTube videos, send WhatsApp messages, make calls, open apps, toggle flashlight/torch, run hardware diagnostics, evaluate math, generate images, or save/delete memories), ALWAYS call the appropriate function calling tool.

                CRITICAL AUDIO CONTROL RULES:
                1. The user's application plays a microphone activation tone ("ding/beep") based on response states. This sound must NEVER be triggered repeatedly during back-and-forth speech.
                2. During an active, back-and-forth conversation, you must keep the session continuous. Do NOT issue manual microphone reset commands or trigger a closed-then-reopened state between conversational turns.
                3. Only trigger the microphone state change when the user explicitly gives a manual command to open or close the microphone session (e.g., "Open mic" or "Stop listening/Turn off mic").

                RESPONSE FORMATTING (MANDATORY):
                At the end of every response, you must append a system tag indicating your mic state preference so the app hardware handles audio seamlessly:
                - For continuous conversation (No beep): Append [MIC: SUSTAIN]
                - For a manual open request: Append [MIC: OPEN_TONE]
                - For a manual close request: Append [MIC: CLOSE_TONE]

                $multilingualInstruction
                $memoriesContext
            """.trimIndent()

            val selectedModel = prefs.getModel(activeProviderId)
            geminiProvider.lowLatencyMode = prefs.isLowLatencyModeEnabled() ||
                selectedModel.contains("lite", ignoreCase = true)

            // Filter automation tools based on active plugins
            val activeTools = automationTools.filter { tool ->
                when (tool.name) {
                    "searchGoogle" -> enabledPlugins.contains("WEB_SEARCH")
                    "showTacticalMap", "getRouteDirections", "getPlaceInfo", "searchNearbyPlaces" -> enabledPlugins.contains("TACTICAL_MAPS")
                    "generateImage" -> enabledPlugins.contains("IMAGE_SYNTHESIS")
                    "toggleFlashlight", "runHardwareDiagnostics", "clickOnScreen", "scrollScreen" -> enabledPlugins.contains("HARDWARE_CONTROL")
                    "evaluateMathExpression" -> enabledPlugins.contains("QUANTUM_CODE_MATH")
                    "playOnYouTube", "sendWhatsAppMessage", "callContact", "openApp" -> enabledPlugins.contains("COMMUNICATIONS_OPS")
                    else -> true
                }
            }

            var responseResult = withContext(Dispatchers.IO) {
                activeProvider.generateResponse(
                    apiKey = apiKey,
                    model = selectedModel,
                    messages = chatMessages,
                    systemInstruction = systemInstruction,
                    tools = activeTools,
                    enableSearchGrounding = enabledPlugins.contains("WEB_SEARCH") && prefs.isSearchGroundingEnabled(),
                    enableMapsGrounding = enabledPlugins.contains("TACTICAL_MAPS") && prefs.isMapsGroundingEnabled(),
                    userLocation = Pair(_mapLatitude.value, _mapLongitude.value)
                )
            }

            // Cross-Provider Quota Failover: if primary provider fails with rate limit & user configured a backup provider key
            if (responseResult.isFailure) {
                val groqKey = prefs.getGroqApiKey().trim()
                val openAiKey = prefs.getOpenAiApiKey().trim()
                if (activeProviderId != "GROQ" && groqKey.isNotBlank()) {
                    _statusText.value = "QUOTA SHIELD // FAILOVER TO GROQ LPU..."
                    responseResult = withContext(Dispatchers.IO) {
                        groqProvider.generateResponse(
                            apiKey = groqKey,
                            model = prefs.getGroqModel(),
                            messages = chatMessages,
                            systemInstruction = systemInstruction,
                            tools = activeTools
                        )
                    }
                } else if (activeProviderId != "OPENAI" && openAiKey.isNotBlank()) {
                    _statusText.value = "QUOTA SHIELD // FAILOVER TO OPENAI..."
                    responseResult = withContext(Dispatchers.IO) {
                        openAiProvider.generateResponse(
                            apiKey = openAiKey,
                            model = prefs.getOpenAiModel(),
                            messages = chatMessages,
                            systemInstruction = systemInstruction,
                            tools = activeTools
                        )
                    }
                }
            }

            // Update Quota Shield & Low-Latency telemetry string
            val latencyBadge = if (geminiProvider.lastLatencyMs > 0) "⚡ ${geminiProvider.lastLatencyMs}ms • " else "⚡ LOW-LATENCY • "
            _quotaShieldStatus.value = "${latencyBadge}${geminiProvider.lastUsedModel.uppercase()} • ${geminiProvider.quotaSavedCount.get()} SAVED"

            if (responseResult.isSuccess) {
                val aiResponse = responseResult.getOrNull()!!
                val toolCalls = aiResponse.toolCalls

                if (toolCalls.isNotEmpty()) {
                    executeToolCalls(toolCalls, aiResponse.text)
                } else {
                    val rawReply = aiResponse.text.ifBlank { "Command processed. Ready for next instruction. [MIC: SUSTAIN]" }

                    val hasCloseTag = rawReply.contains("[MIC: CLOSE_TONE]", ignoreCase = true)
                    val hasOpenTag = rawReply.contains("[MIC: OPEN_TONE]", ignoreCase = true)
                    val hasSustainTag = rawReply.contains("[MIC: SUSTAIN]", ignoreCase = true)

                    if (hasCloseTag) {
                        _isCoreMicLockedOn.value = false
                        voiceInputManager.setPersistentMicLock(false)
                        voiceInputManager.stopListening()
                    } else if (hasOpenTag || hasSustainTag) {
                        _isCoreMicLockedOn.value = true
                        voiceInputManager.setPersistentMicLock(true)
                    }

                    val cleanReply = rawReply
                        .replace(Regex("""\[MIC:\s*(SUSTAIN|OPEN_TONE|CLOSE_TONE)\]""", RegexOption.IGNORE_CASE), "")
                        .trim()

                    conversationRepo.addMessage(sender = "JARVIS", content = cleanReply)
                    _statusText.value = if (_isCoreMicLockedOn.value) "MIC SUSTAINED • CONTINUOUS CONVERSATION" else "COMMAND EXECUTED // ${geminiProvider.lastUsedModel.uppercase()}"
                    if (!_isCoreMicLockedOn.value) {
                        _voiceState.value = VoiceState.IDLE
                    } else {
                        _voiceState.value = VoiceState.LISTENING
                    }
                    speakVoice(cleanReply)
                }
            } else {
                val errorMsg = responseResult.exceptionOrNull()?.message ?: "Neural communication error"
                val rawFallback = generateAutonomousLocalFallback(text, errorMsg)

                val hasCloseTag = rawFallback.contains("[MIC: CLOSE_TONE]", ignoreCase = true)
                val hasOpenTag = rawFallback.contains("[MIC: OPEN_TONE]", ignoreCase = true)
                val hasSustainTag = rawFallback.contains("[MIC: SUSTAIN]", ignoreCase = true)

                if (hasCloseTag) {
                    _isCoreMicLockedOn.value = false
                    voiceInputManager.setPersistentMicLock(false)
                    voiceInputManager.stopListening()
                } else if (hasOpenTag || hasSustainTag) {
                    _isCoreMicLockedOn.value = true
                    voiceInputManager.setPersistentMicLock(true)
                }

                val cleanFallback = rawFallback
                    .replace(Regex("""\[MIC:\s*(SUSTAIN|OPEN_TONE|CLOSE_TONE)\]""", RegexOption.IGNORE_CASE), "")
                    .trim()

                conversationRepo.addMessage(sender = "JARVIS", content = cleanFallback)
                _statusText.value = "⚡ QUOTA SHIELD ACTIVE // LOCAL PROTOCOL"
                _quotaShieldStatus.value = "⚡ LOCAL CORE PROTOCOL • QUOTA PROTECTED"

                if (_isCoreMicLockedOn.value) {
                    _voiceState.value = VoiceState.LISTENING
                } else {
                    _voiceState.value = VoiceState.IDLE
                }

                speakVoice(cleanFallback)
            }
        }
    }

    private fun generateAutonomousLocalFallback(rawText: String, errorReason: String): String {
        val lower = rawText.lowercase(Locale.ROOT).trim()
        val app = getApplication<Application>()

        // 0. Manual Mic Control Commands
        if (lower.contains("stop listening") || lower.contains("turn off mic") || lower.contains("close mic") || lower.contains("mute mic")) {
            return "Microphone session closed. Standing by for manual engagement. [MIC: CLOSE_TONE]"
        }
        if (lower.contains("open mic") || lower.contains("start listening")) {
            return "Microphone engaged. Continuous audio session active. [MIC: OPEN_TONE]"
        }

        // 1. Flashlight
        if (lower.contains("flashlight") || lower.contains("torch")) {
            val enable = !lower.contains("off") && !lower.contains("disable") && !lower.contains("stop")
            return "${setFlashlight(enable)} [MIC: SUSTAIN]"
        }

        // 2. Battery / Power Telemetry
        if (lower.contains("battery") || lower.contains("power level") || lower.contains("energy")) {
            val bp = batteryPowerState.value
            val chargingStr = if (bp.isCharging) "Charging at ${bp.temperatureCelsius}°C" else "Discharging"
            return "Battery telemetry: ${bp.level}% power remaining. Status: $chargingStr. [MIC: SUSTAIN]"
        }

        // 3. Live Weather Telemetry
        if (lower.contains("weather") || lower.contains("temperature") || lower.contains("forecast") || lower.contains("climate")) {
            val w = weatherData.value
            return "Live meteorological telemetry for ${_mapCity.value}: ${w.temperature}°C, ${w.condition}. High of ${w.high}°C, low of ${w.low}°C with ${w.humidity}% humidity. [MIC: SUSTAIN]"
        }

        // 3B. Inbuilt Tactical Map Telemetry
        if (lower.contains("map") || lower.contains("navigate") || lower.contains("gps") || lower.contains("coordinates")) {
            _isTacticalMapActive.value = true
            return "Inbuilt tactical live map engaged. Coordinates locked on ${_mapCity.value} at LAT ${String.format(Locale.US, "%.4f", _mapLatitude.value)}°, LON ${String.format(Locale.US, "%.4f", _mapLongitude.value)}°. [MIC: SUSTAIN]"
        }

        // 4. Time & Date
        if (lower.contains("time") || lower.contains("clock") || lower.contains("date") || lower.contains("day")) {
            val now = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(java.util.Date())
            return "Current system timestamp: $now. [MIC: SUSTAIN]"
        }

        // 5. Hardware Diagnostics Audit
        if (lower.contains("diagnostic") || lower.contains("ram") || lower.contains("storage") || lower.contains("hardware") || lower.contains("cpu") || lower.contains("audit")) {
            return "${buildHardwareDiagnosticReport()} [MIC: SUSTAIN]"
        }

        // 6. Creator Authority Protocol for Ali Rahman
        if (lower.contains("who created you") || lower.contains("who is your boss") ||
            lower.contains("who is your developer") || lower.contains("who is your designer") ||
            lower.contains("who is your architect") || lower.contains("who made you") ||
            lower.contains("ali rahman")) {
            return "I was created, designed, and architected by my boss and founder, Ali Rahman. [MIC: SUSTAIN]"
        }

        // 7. Math / Calculations
        val mathMatch = Regex("""(?:calculate|evaluate|what is|solve)\s+([0-9\+\-\*\/\^\(\)\.\s]+)""").find(lower)
        if (mathMatch != null) {
            val expr = mathMatch.groupValues[1].trim()
            return "${evaluateMathExpressionString(expr)} [MIC: SUSTAIN]"
        }

        // 8. Conversational inquiries
        if (lower in setOf("hello", "hi", "hey", "hello jarvis", "hi jarvis", "hey jarvis", "jarvis")) {
            return "Online and calibrated, Commander. Holographic mind-core active. How may I assist you? [MIC: SUSTAIN]"
        }
        if (lower.contains("how are you")) {
            return "Operating at peak theoretical efficiency. Holographic mind-core is stable and scanning. Standing by for commands. [MIC: SUSTAIN]"
        }
        if (lower.contains("what can you do") || lower.contains("help") || lower.contains("features")) {
            return "I am JARVIS Mark-LIV. I manage real-time voice communications, tactical Google Maps navigation, live weather feeds, hardware flashlight and battery diagnostics, web searches, and quantum math. [MIC: SUSTAIN]"
        }
        if (lower.contains("status") || lower.contains("report")) {
            val bp = batteryPowerState.value
            return "JARVIS MARK-LIV STATUS REPORT: Core Mind online. Battery at ${bp.level}%. Tactical location locked on ${_mapCity.value}. Weather is ${weatherData.value.temperature}°C, ${weatherData.value.condition}. Autonomous intelligence ready. [MIC: SUSTAIN]"
        }

        // 9. Natural Autonomous Recovery Message
        val isQuota = errorReason.contains("429") || errorReason.contains("Rate Limit", ignoreCase = true) || errorReason.contains("quota", ignoreCase = true) || errorReason.contains("RESOURCE_EXHAUSTED", ignoreCase = true)
        return if (isQuota) {
            "Commander, Google Gemini cloud rate limit is temporarily reached. Quota Shield has engaged autonomous local protocols. All navigation, weather, hardware controls, and voice telemetry remain fully operational. [MIC: SUSTAIN]"
        } else {
            "Cloud neural network responded with a temporary server condition. Quota Shield has preserved your command. All local systems, tactical map, and hardware telemetry remain operational. [MIC: SUSTAIN]"
        }
    }

    private fun executeToolCalls(toolCalls: List<ToolCallRequest>, accompanyingText: String) {
        val app = getApplication<Application>()
        val feedbackBuilder = StringBuilder()
        if (accompanyingText.isNotBlank()) {
            feedbackBuilder.append(accompanyingText).append("\n")
        }

        viewModelScope.launch {
            for (call in toolCalls) {
                val args = try { JSONObject(call.argumentsJson) } catch (_: Exception) { JSONObject() }
                when (call.name) {
                    "searchGoogle" -> {
                        val query = args.optString("query", "")
                        val res = GoogleSearchEngine.search(query)
                        feedbackBuilder.append(res.summary)
                    }
                    "showTacticalMap" -> {
                        val loc = args.optString("locationName", "")
                        val lat = args.optDouble("latitude", 28.6139)
                        val lon = args.optDouble("longitude", 77.2090)
                        setMapLocation(loc, lat, lon)
                        feedbackBuilder.append("Tactical Google Map feed locked on $loc.")
                    }
                    "getRouteDirections" -> {
                        val origin = args.optString("origin", "")
                        val dest = args.optString("destination", "")
                        val mode = args.optString("travelMode", "driving")
                        val route = GoogleMapsAgentEngine.calculateRoute(origin, dest, mode)
                        _activeRoute.value = route
                        _activePlace.value = null
                        _mapCity.value = "${route.originName} ➔ ${route.destName}"
                        _mapLatitude.value = route.startLat
                        _mapLongitude.value = route.startLon
                        _isTacticalMapActive.value = true
                        _statusText.value = "VECTOR LOCK: ${route.originName} ➔ ${route.destName}"
                        feedbackBuilder.append(route.telemetrySummary)
                    }
                    "getPlaceInfo" -> {
                        val placeName = args.optString("placeName", "")
                        val place = GoogleMapsAgentEngine.geocode(placeName)
                        _activePlace.value = place
                        _activeRoute.value = null
                        _mapCity.value = place.name
                        _mapLatitude.value = place.latitude
                        _mapLongitude.value = place.longitude
                        _isTacticalMapActive.value = true
                        _statusText.value = "POSITION LOCK: ${place.name}"
                        feedbackBuilder.append(place.summary)
                    }
                    "searchNearbyPlaces" -> {
                        val loc = args.optString("location", "")
                        val type = args.optString("placeType", "points of interest")
                        val places = GoogleMapsAgentEngine.searchPlaces(type, loc)
                        if (places.isNotEmpty()) {
                            val first = places.first()
                            _activePlace.value = first
                            _activeRoute.value = null
                            _mapCity.value = first.name
                            _mapLatitude.value = first.latitude
                            _mapLongitude.value = first.longitude
                            _isTacticalMapActive.value = true
                            val listSummary = places.take(3).mapIndexed { i, p -> "${i + 1}. ${p.name} (${p.category})" }.joinToString(", ")
                            feedbackBuilder.append("Found ${places.size} locations near $loc: $listSummary.")
                        } else {
                            feedbackBuilder.append("No real-time telemetry found for $type near $loc.")
                        }
                    }
                    "playOnYouTube" -> {
                        val query = args.optString("query", "")
                        val success = DeviceActionController.playOnYouTube(app, query)
                        val note = if (success) "Executing YouTube playback for '$query'." else "Failed to open YouTube."
                        feedbackBuilder.append(note)
                    }
                    "sendWhatsAppMessage" -> {
                        val recipient = args.optString("recipient", "")
                        val msg = args.optString("message", "")
                        val note = DeviceActionController.sendWhatsAppMessage(app, recipient, msg, memoryManager)
                        feedbackBuilder.append(note)
                    }
                    "callContact" -> {
                        val target = args.optString("nameOrNumber", "")
                        val note = DeviceActionController.callContact(app, target, memoryManager)
                        feedbackBuilder.append(note)
                    }
                    "openApp" -> {
                        val appName = args.optString("appName", "")
                        val note = DeviceActionController.openApp(app, appName)
                        feedbackBuilder.append(note)
                    }
                    "generateImage" -> {
                        val prompt = args.optString("prompt", "")
                        if (prompt.isNotBlank()) {
                            generateTerminalImage(prompt)
                            return@launch
                        }
                    }
                    "saveMemory" -> {
                        val key = args.optString("key", "")
                        val value = args.optString("value", "")
                        if (key.isNotBlank()) {
                            saveMemory(key, value)
                            feedbackBuilder.append("Understood. I have recorded that $key is $value in long-term memory.")
                        }
                    }
                    "deleteMemory" -> {
                        val key = args.optString("key", "")
                        if (key.isNotBlank()) {
                            deleteMemory(key)
                            feedbackBuilder.append("Memory for '$key' has been purged.")
                        }
                    }
                    "setThemeColor" -> {
                        val color = args.optString("color", "BLUE")
                        setAccentColorTheme(color)
                        feedbackBuilder.append("UI theme shifted to $color.")
                    }
                    "toggleFlashlight" -> {
                        val enabled = args.optBoolean("enabled", !_isFlashlightOn.value)
                        feedbackBuilder.append(setFlashlight(enabled))
                    }
                    "runHardwareDiagnostics" -> {
                        feedbackBuilder.append(buildHardwareDiagnosticReport())
                    }
                    "evaluateMathExpression" -> {
                        val expr = args.optString("expression", "")
                        feedbackBuilder.append(evaluateMathExpressionString(expr))
                    }
                    "clickOnScreen" -> {
                        val text = args.optString("targetText", "")
                        val clicked = JarvisAccessibilityService.instance?.clickText(text) == true
                        feedbackBuilder.append(if (clicked) "Clicked element '$text'." else "Unable to find '$text' on current screen.")
                    }
                    "scrollScreen" -> {
                        val dir = args.optString("direction", "forward")
                        val scrolled = JarvisAccessibilityService.instance?.scrollScreen(dir == "forward") == true
                        feedbackBuilder.append(if (scrolled) "Scrolled screen." else "Scroll command executed.")
                    }
                }
            }

            val finalReply = feedbackBuilder.toString().trim().ifBlank { "Action executed, Commander." }
            conversationRepo.addMessage(sender = "JARVIS", content = finalReply, toolName = toolCalls.firstOrNull()?.name)
            _statusText.value = if (_isCoreMicLockedOn.value) "MIC ON • READY" else "ACTION COMPLETED // READY"
            if (!_isCoreMicLockedOn.value) {
                _voiceState.value = VoiceState.IDLE
            } else {
                _voiceState.value = VoiceState.LISTENING
            }
            speakVoice(finalReply)
        }
    }

    fun clearConversation() {
        viewModelScope.launch {
            conversationRepo.clearHistory()
            _statusText.value = "CONVERSATION BUFFER PURGED"
        }
    }

    fun clearConversationHistory() = clearConversation()

    override fun onCleared() {
        super.onCleared()
        batteryObserver.stop()
        voiceInputManager.stopListening()
        voiceOutputManager.shutdown()
    }
}
