package com.example.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * Real-time GPS LocationRepository powered by FusedLocationProviderClient and LocationRequest.
 * Continuously tracks the operator's live GPS coordinates while location permission is granted,
 * automatically updating the custom marker and camera position on the Google Map as well as
 * live weather telemetry.
 */
class LocationRepository(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val deviceLocationManager = DeviceLocationManager(context)

    private val _currentGpsLocation = MutableStateFlow<LatLng?>(null)
    val currentGpsLocation: StateFlow<LatLng?> = _currentGpsLocation.asStateFlow()

    private val _isRealtimeTrackingActive = MutableStateFlow(false)
    val isRealtimeTrackingActive: StateFlow<Boolean> = _isRealtimeTrackingActive.asStateFlow()

    private var trackingJob: Job? = null

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * Builds a high-accuracy LocationRequest for real-time GPS coordinate tracking.
     */
    fun buildLocationRequest(
        intervalMillis: Long = 4000L,
        minUpdateIntervalMillis: Long = 2000L,
        minUpdateDistanceMeters: Float = 2f
    ): LocationRequest {
        return LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(minUpdateIntervalMillis)
            .setMinUpdateDistanceMeters(minUpdateDistanceMeters)
            .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            .setWaitForAccurateLocation(false)
            .build()
    }

    /**
     * Streams real-time GPS updates using FusedLocationProviderClient + LocationRequest + LocationCallback.
     */
    @SuppressLint("MissingPermission")
    fun observeRealtimeLocation(
        intervalMillis: Long = 4000L,
        minUpdateDistanceMeters: Float = 2f
    ): Flow<LatLng> = callbackFlow {
        if (!hasLocationPermission()) {
            close()
            return@callbackFlow
        }

        val locationRequest = buildLocationRequest(
            intervalMillis = intervalMillis,
            minUpdateIntervalMillis = (intervalMillis / 2).coerceAtLeast(1000L),
            minUpdateDistanceMeters = minUpdateDistanceMeters
        )

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                val loc = result.lastLocation ?: result.locations.lastOrNull() ?: return
                val latLng = LatLng(loc.latitude, loc.longitude)
                _currentGpsLocation.value = latLng
                trySend(latLng)
            }
        }

        try {
            // 1. Seed immediately with FusedLocationProviderClient.lastLocation if available
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    val latLng = LatLng(loc.latitude, loc.longitude)
                    _currentGpsLocation.value = latLng
                    trySend(latLng)
                }
            }

            // 2. Register continuous LocationRequest updates
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            _isRealtimeTrackingActive.value = true
        } catch (_: SecurityException) {
            _isRealtimeTrackingActive.value = false
        } catch (_: Exception) {
            _isRealtimeTrackingActive.value = false
        }

        // 3. Also query DeviceLocationManager once so cloud emulators without Play Services location fixes still get an immediate coordinate lock
        launch {
            if (_currentGpsLocation.value == null) {
                val status = deviceLocationManager.getCurrentLocation()
                if (status is LocationStatus.Success) {
                    val latLng = LatLng(status.result.latitude, status.result.longitude)
                    _currentGpsLocation.value = latLng
                    trySend(latLng)
                }
            }
        }

        awaitClose {
            try {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            } catch (_: Exception) {}
            _isRealtimeTrackingActive.value = false
        }
    }

    /**
     * Starts continuous real-time GPS tracking in the provided [scope] and invokes [onLocationUpdate]
     * whenever the user's coordinates change.
     */
    fun startRealtimeTracking(
        scope: CoroutineScope,
        intervalMillis: Long = 4000L,
        onLocationUpdate: (LatLng) -> Unit = {}
    ) {
        if (!hasLocationPermission()) return
        trackingJob?.cancel()
        trackingJob = scope.launch {
            observeRealtimeLocation(intervalMillis = intervalMillis).collect { latLng ->
                _currentGpsLocation.value = latLng
                onLocationUpdate(latLng)
            }
        }
    }

    fun stopRealtimeTracking() {
        trackingJob?.cancel()
        trackingJob = null
        _isRealtimeTrackingActive.value = false
    }

    /**
     * Fetches a single fresh or cached GPS coordinate fix on demand.
     */
    @SuppressLint("MissingPermission")
    suspend fun fetchImmediateLocation(): LatLng? {
        if (!hasLocationPermission()) return _currentGpsLocation.value
        val status = deviceLocationManager.getCurrentLocation()
        if (status is LocationStatus.Success) {
            val latLng = LatLng(status.result.latitude, status.result.longitude)
            _currentGpsLocation.value = latLng
            return latLng
        }
        return _currentGpsLocation.value
    }
}
