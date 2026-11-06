package com.example.ui.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.DarkHologramSurface
import com.example.ui.theme.HologramTextBright
import com.example.ui.theme.HologramTextDim
import com.example.ui.theme.ReactorCyanGlow
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Accompanist-based Location Permission Helper State.
 * Encapsulates requesting fine and coarse location permissions and centering
 * the tactical map on the user's live GPS coordinates.
 */
@OptIn(ExperimentalPermissionsApi::class)
class LocationPermissionHelper(
    val permissionsState: MultiplePermissionsState,
    val hasFinePermission: Boolean,
    val hasCoarsePermission: Boolean,
    val hasAnyPermission: Boolean,
    val isLocating: Boolean,
    val shouldShowRationale: Boolean,
    val requestPermission: () -> Unit,
    val centerOnCurrentLocation: (suspend (LatLng) -> Unit) -> Unit
)

/**
 * Utility composable that creates and remembers a [LocationPermissionHelper] using Accompanist.
 * Handles fine location permission requests and provides a direct hook to center
 * Google Maps Camera on the user's current GPS coordinates.
 *
 * @param onLocationAcquired Optional callback triggered when fresh coordinates are acquired.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberLocationPermissionHelper(
    onLocationAcquired: ((LatLng) -> Unit)? = null
): LocationPermissionHelper {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLocating by remember { mutableStateOf(false) }

    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    val fineGranted = (permissionsState.permissions.find {
        it.permission == Manifest.permission.ACCESS_FINE_LOCATION
    }?.status?.isGranted == true) ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    val coarseGranted = (permissionsState.permissions.find {
        it.permission == Manifest.permission.ACCESS_COARSE_LOCATION
    }?.status?.isGranted == true) ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    val hasAnyPermission = fineGranted || coarseGranted

    val centerOnLocation: (suspend (LatLng) -> Unit) -> Unit = { centerAction ->
        if (hasAnyPermission) {
            scope.launch {
                isLocating = true
                val latLng = fetchCurrentLocationCoordinates(context)
                if (latLng != null) {
                    centerAction(latLng)
                    onLocationAcquired?.invoke(latLng)
                }
                isLocating = false
            }
        } else {
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    return remember(
        permissionsState,
        fineGranted,
        coarseGranted,
        hasAnyPermission,
        isLocating,
        permissionsState.shouldShowRationale
    ) {
        LocationPermissionHelper(
            permissionsState = permissionsState,
            hasFinePermission = fineGranted,
            hasCoarsePermission = coarseGranted,
            hasAnyPermission = hasAnyPermission,
            isLocating = isLocating,
            shouldShowRationale = permissionsState.shouldShowRationale,
            requestPermission = { permissionsState.launchMultiplePermissionRequest() },
            centerOnCurrentLocation = centerOnLocation
        )
    }
}

/**
 * Single-permission variant using Accompanist for pure Fine Location requests.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberFineLocationOnlyState(
    onPermissionResult: (Boolean) -> Unit = {}
): PermissionState {
    return rememberPermissionState(
        permission = Manifest.permission.ACCESS_FINE_LOCATION,
        onPermissionResult = onPermissionResult
    )
}

/**
 * Asynchronously acquires the most accurate device coordinates using DeviceLocationManager
 * (hardware GPS + Geo-IP fallback) without triggering Play Services Volley 503 errors on emulators.
 */
suspend fun fetchCurrentLocationCoordinates(context: Context): LatLng? = withContext(Dispatchers.IO) {
    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    if (!fine && !coarse) return@withContext null

    try {
        val locManager = com.example.data.location.DeviceLocationManager(context)
        when (val status = locManager.getCurrentLocation()) {
            is com.example.data.location.LocationStatus.Success -> {
                return@withContext LatLng(status.result.latitude, status.result.longitude)
            }
            else -> {}
        }
    } catch (_: Exception) {}

    // Fallback to user's calibrated coordinates in PreferencesManager
    return@withContext try {
        val prefs = com.example.data.prefs.PreferencesManager(context)
        LatLng(prefs.getUserLocationLat(), prefs.getUserLocationLon())
    } catch (_: Exception) {
        null
    }
}

/**
 * Sci-Fi HUD themed dialog explaining why Tactical GPS positioning requires fine location permissions.
 */
@Composable
fun LocationPermissionRationaleDialog(
    accentColor: Color = ReactorCyanGlow,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "GPS LOCK // PERMISSION REQUIRED",
                color = accentColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        },
        text = {
            Text(
                text = "JARVIS Tactical Grid requires Precise Location access to center the tactical vector map on your live coordinates and provide real-time navigational telemetry.",
                color = HologramTextDim,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = accentColor),
                modifier = Modifier.testTag("confirm_location_permission")
            ) {
                Text(
                    text = "GRANT ACCESS",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_location_permission")
            ) {
                Text(
                    text = "CANCEL",
                    color = HologramTextBright,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        containerColor = DarkHologramSurface,
        modifier = Modifier.testTag("location_permission_rationale_dialog")
    )
}
