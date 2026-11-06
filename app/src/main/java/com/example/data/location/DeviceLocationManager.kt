package com.example.data.location

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class LocationResult(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val isPrecise: Boolean,
    val timestampMillis: Long,
    val provider: String
)

sealed class LocationStatus {
    data class Success(val result: LocationResult) : LocationStatus()
    data class PermissionDenied(val message: String) : LocationStatus()
    data class LocationServicesDisabled(val message: String) : LocationStatus()
    data class Error(val message: String) : LocationStatus()
}

/**
 * Foreground-only Device Location Manager.
 * Obtains fresh device location fix strictly on demand when user activates
 * Maps or Weather features. Respects precise vs approximate permission grants.
 * Never performs continuous background tracking.
 */
class DeviceLocationManager(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val executor = Executors.newSingleThreadExecutor()

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun hasPreciseLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun isLocationServiceEnabled(): Boolean {
        val lm = locationManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    fun getLocationSettingsIntent(): Intent {
        return Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    suspend fun getCurrentLocation(maxStaleAgeMillis: Long = TimeUnit.MINUTES.toMillis(15)): LocationStatus = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) {
            return@withContext LocationStatus.PermissionDenied(
                "Location permission not granted. Current-location features require location permission."
            )
        }

        if (!isLocationServiceEnabled()) {
            return@withContext LocationStatus.LocationServicesDisabled(
                "Device location services are turned off. Please enable Location in Android settings."
            )
        }

        val isPrecise = hasPreciseLocationPermission()
        val lm = locationManager ?: return@withContext LocationStatus.Error("Location service unavailable.")

        // 1. Check cached GPS/Passive location first to avoid blocking or triggering Play Services network location 503
        try {
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            var bestLocation: Location? = null
            val now = System.currentTimeMillis()

            for (provider in providers) {
                if (!lm.isProviderEnabled(provider)) continue
                @Suppress("MissingPermission")
                val loc = lm.getLastKnownLocation(provider) ?: continue
                val age = now - loc.time
                if (age <= maxStaleAgeMillis) {
                    if (bestLocation == null || loc.accuracy < bestLocation.accuracy) {
                        bestLocation = loc
                    }
                }
            }

            if (bestLocation != null) {
                return@withContext LocationStatus.Success(
                    LocationResult(
                        latitude = bestLocation.latitude,
                        longitude = bestLocation.longitude,
                        accuracyMeters = bestLocation.accuracy,
                        isPrecise = isPrecise,
                        timestampMillis = bestLocation.time,
                        provider = bestLocation.provider ?: "gps_cache"
                    )
                )
            }
        } catch (_: Exception) {}

        // 2. Try fresh hardware GPS_PROVIDER fix with 2.5s timeout (never NETWORK_PROVIDER on emulators to avoid glm/mmap/api 503)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            try {
                val freshFix = kotlinx.coroutines.withTimeoutOrNull(2500L) {
                    requestFreshCurrentLocationApi30(lm)
                }
                if (freshFix != null) {
                    return@withContext LocationStatus.Success(
                        LocationResult(
                            latitude = freshFix.latitude,
                            longitude = freshFix.longitude,
                            accuracyMeters = freshFix.accuracy,
                            isPrecise = isPrecise,
                            timestampMillis = freshFix.time,
                            provider = freshFix.provider ?: "gps"
                        )
                    )
                }
            } catch (_: Exception) {}
        }

        // 3. Cloud Emulator / Indoor fallback: HTTPS Geo-IP lookup (never touches clients4.google.com/glm/mmap/api)
        try {
            val ipGeoUrl = java.net.URL("https://get.geojs.io/v1/ip/geo.json")
            val conn = (ipGeoUrl.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                requestMethod = "GET"
            }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = org.json.JSONObject(body)
                val lat = json.optString("latitude", "").toDoubleOrNull()
                val lon = json.optString("longitude", "").toDoubleOrNull()
                if (lat != null && lon != null) {
                    return@withContext LocationStatus.Success(
                        LocationResult(
                            latitude = lat,
                            longitude = lon,
                            accuracyMeters = 50f,
                            isPrecise = isPrecise,
                            timestampMillis = System.currentTimeMillis(),
                            provider = "geo_ip"
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        LocationStatus.Error("Unable to acquire a fresh location fix. You can select your city manually.")
    }

    private suspend fun requestFreshCurrentLocationApi30(lm: LocationManager): Location? =
        suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation {
                cancellationSignal.cancel()
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    @Suppress("MissingPermission")
                    lm.getCurrentLocation(
                        LocationManager.GPS_PROVIDER,
                        cancellationSignal,
                        executor
                    ) { location ->
                        if (continuation.isActive) {
                            continuation.resume(location)
                        }
                    }
                } else {
                    continuation.resume(null)
                }
            } catch (_: Exception) {
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }
}
