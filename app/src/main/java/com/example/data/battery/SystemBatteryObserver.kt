package com.example.data.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import com.example.data.prefs.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tactical AI compute throttle levels governed by Arc Reactor power reserves.
 */
enum class AiThrottleLevel(val displayName: String, val badgeLabel: String) {
    UNCONSTRAINED("Unconstrained (Peak Throughput)", "AI: PEAK"),
    BALANCED("Balanced Power Optimization", "AI: BALANCED"),
    CRITICAL_THROTTLED("Emergency Energy Conservation", "AI: THROTTLED")
}

/**
 * Hardware electrical bus connection source.
 */
enum class PluggedSource(val label: String) {
    AC_MAINS("AC MAINS"),
    USB_BUS("USB BUS"),
    QI_WIRELESS("QI WIRELESS"),
    DOCK("DOCK CRADLE"),
    UNPLUGGED("DISCHARGING (INTERNAL CELL)")
}

/**
 * System battery cell health classification.
 */
enum class BatteryHealth(val label: String) {
    GOOD("OPTIMAL"),
    OVERHEAT("THERMAL OVERHEAT"),
    DEAD("CELL DEPLETED"),
    OVER_VOLTAGE("OVER-VOLTAGE SURGE"),
    UNSPECIFIED_FAILURE("CELL ANOMALY"),
    COLD("LOW TEMPERATURE"),
    UNKNOWN("TELEMETRY UNKNOWN")
}

/**
 * Categorization of AI computational tasks with varying power footprints.
 */
enum class AiTaskType(val label: String, val isResourceIntensive: Boolean) {
    LLM_INFERENCE("Cognitive Reasoning & Chat", false),
    HIGH_CONTEXT_REASONING("Deep Context Multi-Turn Analysis", true),
    IMAGE_GENERATION("Imagen 3 Neural Image Synthesis", true),
    VIDEO_GENERATION("Veo Cinematic Neural Video Synthesis", true),
    VIDEO_SYNTHESIS("Veo Cinematic Neural Video Synthesis", true),
    MULTIMODAL_ANALYSIS("Multimodal Vision & Audio Processing", true),
    BACKGROUND_SYNC("Background Database Hygiene & Sync", true)
}

/**
 * Snapshot of live power status and active AI throttling profile.
 */
data class BatteryPowerState(
    val level: Int = 100,
    val scale: Int = 100,
    val isCharging: Boolean = false,
    val isPlugged: Boolean = false,
    val pluggedSource: PluggedSource = PluggedSource.UNPLUGGED,
    val pluggedSourceLabel: String = "Internal Cell",
    val statusDescription: String = "NOMINAL",
    val health: BatteryHealth = BatteryHealth.GOOD,
    val temperatureCelsius: Float = 25f,
    val voltageMv: Int = 4000,
    val isPowerSaveMode: Boolean = false,
    val isBatteryLow: Boolean = false,
    val isCriticalBattery: Boolean = false,
    val throttleLevel: AiThrottleLevel = AiThrottleLevel.UNCONSTRAINED,
    val throttleReason: String = "Optimal power reserves.",
    val isSimulated: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    val batteryPercentageText: String get() = "$level%"
    val isOptimalForHeavyAi: Boolean get() = isCharging || level > 35
    val canExecuteHeavyAi: Boolean get() = throttleLevel != AiThrottleLevel.CRITICAL_THROTTLED
    val tacticalHudSummary: String get() = when (throttleLevel) {
        AiThrottleLevel.UNCONSTRAINED ->
            "POWER [${pluggedSource.label}]: $level% // AI UNCONSTRAINED // HEURISTICS MAXIMUM"
        AiThrottleLevel.BALANCED ->
            "POWER [${pluggedSource.label}]: $level% // AI BALANCED // REASONING REGULATED"
        AiThrottleLevel.CRITICAL_THROTTLED ->
            "ALERT [${pluggedSource.label}]: $level% // AI CRITICALLY THROTTLED // CONSERVATION PROTOCOL ENGAGED"
    }
}

/**
 * Tactical decision returned when evaluating an AI task against live power telemetry.
 */
data class ThrottleDecision(
    val isThrottled: Boolean,
    val throttleLevel: AiThrottleLevel,
    val allowExecution: Boolean = true,
    val requiresUserOverride: Boolean = false,
    val reason: String = "",
    val tacticalDirective: String? = null,
    val maxTokensLimit: Int = 4096,
    val recommendedTemperature: Float = 0.7f,
    val suggestedModel: String? = null
)

/**
 * SystemBatteryObserver provides live power status telemetry and dynamically calculates
 * AI computational throttling based on battery percentage, charging state changes,
 * Android power-save mode, and temperature metrics.
 */
class SystemBatteryObserver(
    private val context: Context,
    private val prefs: PreferencesManager? = null
) {
    private val listeners = CopyOnWriteArrayList<(BatteryPowerState) -> Unit>()
    private var isReceiverRegistered = false

    // Developer / Tactical simulation state (useful on emulators or demo environments)
    private var simulatedLevel: Int? = null
    private var simulatedCharging: Boolean? = null
    private var simulatedPlugged: PluggedSource? = null

    private val _powerStateFlow = MutableStateFlow(readCurrentHardwareState())
    val powerStateFlow: StateFlow<BatteryPowerState> = _powerStateFlow.asStateFlow()
    val currentPowerStatus: BatteryPowerState
        get() = _powerStateFlow.value

    var onChargingStateChanged: ((isCharging: Boolean, state: BatteryPowerState) -> Unit)? = null
    var onThrottleLevelChanged: ((level: AiThrottleLevel, state: BatteryPowerState) -> Unit)? = null
    var onPowerLowAlert: ((state: BatteryPowerState) -> Unit)? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            intent ?: return
            val action = intent.action ?: return
            if (action == Intent.ACTION_BATTERY_CHANGED ||
                action == Intent.ACTION_POWER_CONNECTED ||
                action == Intent.ACTION_POWER_DISCONNECTED ||
                action == Intent.ACTION_BATTERY_LOW ||
                action == Intent.ACTION_BATTERY_OKAY ||
                action == PowerManager.ACTION_POWER_SAVE_MODE_CHANGED
            ) {
                updatePowerState(intent)
            }
        }
    }

    init {
        start()
    }

    /**
     * Registers the broadcast receiver for real-time power updates.
     */
    @Synchronized
    fun start() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(Intent.ACTION_BATTERY_LOW)
                addAction(Intent.ACTION_BATTERY_OKAY)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            }
            try {
                val stickyIntent = context.registerReceiver(batteryReceiver, filter)
                isReceiverRegistered = true
                if (stickyIntent != null) {
                    updatePowerState(stickyIntent)
                } else {
                    _powerStateFlow.value = readCurrentHardwareState()
                }
            } catch (_: Exception) {
                _powerStateFlow.value = readCurrentHardwareState()
            }
        }
    }

    /**
     * Unregisters broadcast receiver to avoid leaking context.
     */
    @Synchronized
    fun stop() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(batteryReceiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
    }

    fun addPowerStateListener(listener: (BatteryPowerState) -> Unit) {
        listeners.add(listener)
        listener(_powerStateFlow.value)
    }

    fun removePowerStateListener(listener: (BatteryPowerState) -> Unit) {
        listeners.remove(listener)
    }

    /**
     * Synchronously parses an Intent or reads current sticky battery broadcast.
     */
    fun refreshPowerStatus(): BatteryPowerState {
        val intent = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) {
            null
        }
        return updatePowerState(intent)
    }

    private fun updatePowerState(intent: Intent?): BatteryPowerState {
        val previous = _powerStateFlow.value
        val newState = parseBatteryIntent(intent)
        _powerStateFlow.value = newState

        // Detect charging state change
        if (previous.isCharging != newState.isCharging) {
            onChargingStateChanged?.invoke(newState.isCharging, newState)
        }

        // Detect throttle profile shift
        if (previous.throttleLevel != newState.throttleLevel) {
            onThrottleLevelChanged?.invoke(newState.throttleLevel, newState)
        }

        // Detect drop into low battery
        if (!previous.isBatteryLow && newState.isBatteryLow) {
            onPowerLowAlert?.invoke(newState)
        }

        for (listener in listeners) {
            try {
                listener(newState)
            } catch (_: Exception) {}
        }

        return newState
    }

    private fun readCurrentHardwareState(): BatteryPowerState {
        val stickyIntent = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) {
            null
        }
        return parseBatteryIntent(stickyIntent)
    }

    private fun parseBatteryIntent(intent: Intent?): BatteryPowerState {
        // Read raw battery values
        val rawLevel = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val rawScale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val rawStatus = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val rawPlugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val rawHealth = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
        val rawVoltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 4000) ?: 4000
        val rawTempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 250) ?: 250

        val powerManager = try {
            context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        } catch (_: Exception) {
            null
        }
        val isSystemPowerSave = powerManager?.isPowerSaveMode == true

        var calculatedPct = if (rawLevel >= 0 && rawScale > 0) {
            (rawLevel * 100) / rawScale
        } else {
            100
        }
        var isPlugged = rawPlugged > 0
        var isCharging = rawStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
                rawStatus == BatteryManager.BATTERY_STATUS_FULL ||
                isPlugged

        var pluggedSource = when (rawPlugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> PluggedSource.AC_MAINS
            BatteryManager.BATTERY_PLUGGED_USB -> PluggedSource.USB_BUS
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> PluggedSource.QI_WIRELESS
            4 -> PluggedSource.DOCK
            else -> if (isCharging) PluggedSource.AC_MAINS else PluggedSource.UNPLUGGED
        }

        var isSimulated = false

        // Apply developer/tactical simulation override if set
        simulatedLevel?.let {
            calculatedPct = it.coerceIn(0, 100)
            isSimulated = true
        }
        simulatedCharging?.let {
            isCharging = it
            isPlugged = it
            if (!it) {
                pluggedSource = PluggedSource.UNPLUGGED
            } else if (pluggedSource == PluggedSource.UNPLUGGED) {
                pluggedSource = PluggedSource.AC_MAINS
            }
            isSimulated = true
        }
        simulatedPlugged?.let {
            pluggedSource = it
            isPlugged = it != PluggedSource.UNPLUGGED
            isCharging = isPlugged
            isSimulated = true
        }

        val statusDescription = when (rawStatus) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "DISCHARGING"
            BatteryManager.BATTERY_STATUS_FULL -> "FULL CAPACITY"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NOT CHARGING"
            else -> if (isCharging) "CHARGING" else "DISCHARGING"
        }

        val health = when (rawHealth) {
            BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealth.GOOD
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealth.OVERHEAT
            BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealth.DEAD
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealth.OVER_VOLTAGE
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> BatteryHealth.UNSPECIFIED_FAILURE
            BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealth.COLD
            else -> BatteryHealth.GOOD
        }

        val tempCelsius = rawTempTenths / 10f
        val lowThreshold = 20
        val criticalThreshold = 10
        val throttlingEnabled = true

        val isBatteryLow = calculatedPct <= lowThreshold
        val isCriticalBattery = calculatedPct <= criticalThreshold

        // Determine AI Throttling Profile
        val (throttleLevel, reason) = when {
            !throttlingEnabled -> {
                AiThrottleLevel.UNCONSTRAINED to "USER_OVERRIDE // Throttling globally disengaged by user"
            }
            isCharging -> {
                AiThrottleLevel.UNCONSTRAINED to "EXTERNAL_POWER // Connected to ${pluggedSource.label}. Full quantum compute allocated."
            }
            isCriticalBattery || (isSystemPowerSave && calculatedPct <= 15) -> {
                AiThrottleLevel.CRITICAL_THROTTLED to "POWER_CRITICAL // Arc Reactor reserves at $calculatedPct%. Heavy AI synthesis restricted."
            }
            isBatteryLow || isSystemPowerSave -> {
                AiThrottleLevel.BALANCED to "POWER_LOW // Reserves at $calculatedPct%. Balanced token limits and compute regulation active."
            }
            calculatedPct <= 35 -> {
                AiThrottleLevel.BALANCED to "POWER_MODERATE // Reserves at $calculatedPct%. Regulating high-context compute."
            }
            else -> {
                AiThrottleLevel.UNCONSTRAINED to "POWER_NOMINAL // Reserves at $calculatedPct%. Standard computational execution."
            }
        }

        return BatteryPowerState(
            level = calculatedPct,
            scale = rawScale,
            isCharging = isCharging,
            isPlugged = isPlugged,
            pluggedSource = pluggedSource,
            pluggedSourceLabel = pluggedSource.label,
            statusDescription = statusDescription,
            health = health,
            temperatureCelsius = tempCelsius,
            voltageMv = rawVoltage,
            isPowerSaveMode = isSystemPowerSave,
            isBatteryLow = isBatteryLow,
            isCriticalBattery = isCriticalBattery,
            throttleLevel = throttleLevel,
            throttleReason = reason,
            isSimulated = isSimulated
        )
    }

    /**
     * Evaluates whether an intensive AI task should be throttled or modified based on
     * the current power status.
     */
    fun evaluateAiThrottle(taskType: AiTaskType): ThrottleDecision {
        val state = _powerStateFlow.value
        val throttlingEnabled = true
        if (!throttlingEnabled) {
            return ThrottleDecision(
                isThrottled = false,
                throttleLevel = AiThrottleLevel.UNCONSTRAINED,
                allowExecution = true,
                requiresUserOverride = false,
                reason = "AI Power Throttling policy is currently bypassed in system preferences.",
                tacticalDirective = null,
                maxTokensLimit = 4096,
                recommendedTemperature = 0.7f
            )
        }

        return when (state.throttleLevel) {
            AiThrottleLevel.UNCONSTRAINED -> {
                ThrottleDecision(
                    isThrottled = false,
                    throttleLevel = AiThrottleLevel.UNCONSTRAINED,
                    allowExecution = true,
                    requiresUserOverride = false,
                    reason = if (state.isCharging) {
                        "Connected to ${state.pluggedSource.label}. Full neural acceleration unconstrained."
                    } else {
                        "Battery reserves healthy (${state.level}%). Standard operational throughput."
                    },
                    tacticalDirective = null,
                    maxTokensLimit = 4096,
                    recommendedTemperature = 0.7f
                )
            }
            AiThrottleLevel.BALANCED -> {
                when (taskType) {
                    AiTaskType.VIDEO_GENERATION, AiTaskType.VIDEO_SYNTHESIS -> {
                        ThrottleDecision(
                            isThrottled = true,
                            throttleLevel = AiThrottleLevel.BALANCED,
                            allowExecution = true,
                            requiresUserOverride = true,
                            reason = "Battery reserves at ${state.level}%. Cinematic video generation will rapidly deplete internal cells.",
                            tacticalDirective = null,
                            maxTokensLimit = 2048,
                            recommendedTemperature = 0.5f
                        )
                    }
                    AiTaskType.IMAGE_GENERATION -> {
                        ThrottleDecision(
                            isThrottled = true,
                            throttleLevel = AiThrottleLevel.BALANCED,
                            allowExecution = true,
                            requiresUserOverride = false,
                            reason = "Battery reserves at ${state.level}%. Image generation allowed with single-pass optimization.",
                            tacticalDirective = null,
                            maxTokensLimit = 2048,
                            recommendedTemperature = 0.5f
                        )
                    }
                    AiTaskType.BACKGROUND_SYNC -> {
                        ThrottleDecision(
                            isThrottled = true,
                            throttleLevel = AiThrottleLevel.BALANCED,
                            allowExecution = false,
                            requiresUserOverride = false,
                            reason = "Background synchronization deferred until external power is attached.",
                            tacticalDirective = null,
                            maxTokensLimit = 1024,
                            recommendedTemperature = 0.4f
                        )
                    }
                    else -> {
                        ThrottleDecision(
                            isThrottled = true,
                            throttleLevel = AiThrottleLevel.BALANCED,
                            allowExecution = true,
                            requiresUserOverride = false,
                            reason = "Battery reserves moderate (${state.level}%). Low latency model engaged.",
                            tacticalDirective = "[TACTICAL POWER CONSERVATION DIRECTIVE]: System power is at ${state.level}% (discharging). Formulate response concisely, eliminating unnecessary narrative preamble.",
                            maxTokensLimit = 1500,
                            recommendedTemperature = 0.5f,
                            suggestedModel = "gemini-3.8-flash-lite"
                        )
                    }
                }
            }
            AiThrottleLevel.CRITICAL_THROTTLED -> {
                when (taskType) {
                    AiTaskType.VIDEO_GENERATION, AiTaskType.VIDEO_SYNTHESIS, AiTaskType.IMAGE_GENERATION -> {
                        ThrottleDecision(
                            isThrottled = true,
                            throttleLevel = AiThrottleLevel.CRITICAL_THROTTLED,
                            allowExecution = false,
                            requiresUserOverride = true,
                            reason = "CRITICAL POWER ALERT (${state.level}%): Neural media synthesis is locked down to protect device from sudden shutdown. Connect charger or confirm manual override.",
                            tacticalDirective = null,
                            maxTokensLimit = 512,
                            recommendedTemperature = 0.2f
                        )
                    }
                    AiTaskType.BACKGROUND_SYNC -> {
                        ThrottleDecision(
                            isThrottled = true,
                            throttleLevel = AiThrottleLevel.CRITICAL_THROTTLED,
                            allowExecution = false,
                            requiresUserOverride = false,
                            reason = "CRITICAL POWER (${state.level}%): Background operations completely halted.",
                            tacticalDirective = null,
                            maxTokensLimit = 256,
                            recommendedTemperature = 0.2f
                        )
                    }
                    else -> {
                        ThrottleDecision(
                            isThrottled = true,
                            throttleLevel = AiThrottleLevel.CRITICAL_THROTTLED,
                            allowExecution = true,
                            requiresUserOverride = false,
                            reason = "CRITICAL POWER (${state.level}%): Cognitive reasoning throttled to minimum emergency protocol.",
                            tacticalDirective = "[EMERGENCY SUB-CORE POWER CONSERVATION]: Arc Reactor reserves critical (${state.level}%). Provide an extremely short, bulleted or direct response. Conserve maximum CPU/network cycles.",
                            maxTokensLimit = 512,
                            recommendedTemperature = 0.2f,
                            suggestedModel = "gemini-3.8-flash-lite"
                        )
                    }
                }
            }
        }
    }

    /**
     * Simulate a specific battery percentage and charging state for UI / debugging verification.
     */
    fun simulatePowerState(level: Int? = null, isCharging: Boolean? = null, pluggedSource: PluggedSource? = null) {
        simulatedLevel = level
        simulatedCharging = isCharging
        simulatedPlugged = pluggedSource
        refreshPowerStatus()
    }

    /**
     * Resets simulated power state and restores true hardware sensor monitoring.
     */
    fun resetSimulation() {
        simulatedLevel = null
        simulatedCharging = null
        simulatedPlugged = null
        refreshPowerStatus()
    }

    val isSimulating: Boolean
        get() = simulatedLevel != null || simulatedCharging != null || simulatedPlugged != null
}
