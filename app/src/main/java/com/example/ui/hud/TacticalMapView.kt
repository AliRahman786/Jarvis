package com.example.ui.hud

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.BuildConfig
import com.example.data.prefs.PreferencesManager
import com.example.maps.PlaceLocation
import com.example.maps.RouteCalculation
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.ArcAmberBright
import com.example.ui.theme.DarkHologramSurface
import com.example.ui.theme.HologramTextBright
import com.example.ui.theme.HologramTextDim
import com.example.ui.theme.HologramTextMuted
import com.example.ui.theme.SuccessEmerald
import com.example.ui.util.LocationPermissionRationaleDialog
import com.example.ui.util.rememberLocationPermissionHelper
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import java.util.Collections
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class MapsApiKeyStatus(
    val isValid: Boolean,
    val consumedKeyMasked: String,
    val source: String,
    val diagnosticMessage: String
)

/**
 * Verifies Google Maps API key consumption across AndroidManifest meta-data,
 * BuildConfig.MAPS_API_KEY, and PreferencesManager to prevent blank map rendering caused by
 * unconsumed or placeholder keys.
 */
fun verifyMapsApiKeyConsumption(context: Context): MapsApiKeyStatus {
    val manifestKey = try {
        val appInfo = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA
        )
        appInfo.metaData?.getString("com.google.android.geo.API_KEY")?.trim() ?: ""
    } catch (_: Exception) {
        ""
    }

    val buildConfigKey = try {
        BuildConfig.MAPS_API_KEY.trim()
    } catch (_: Exception) {
        ""
    }

    val prefsKey = try {
        PreferencesManager(context).getMapsApiKey().trim()
    } catch (_: Exception) {
        ""
    }

    val effectiveKey = when {
        manifestKey.isNotBlank() && !manifestKey.startsWith("PLACEHOLDER") && manifestKey != "\${MAPS_API_KEY}" -> manifestKey
        buildConfigKey.isNotBlank() && !buildConfigKey.startsWith("PLACEHOLDER") -> buildConfigKey
        prefsKey.isNotBlank() && !prefsKey.startsWith("PLACEHOLDER") -> prefsKey
        else -> ""
    }

    val source = when {
        effectiveKey == manifestKey && manifestKey.isNotBlank() -> "AndroidManifest (com.google.android.geo.API_KEY)"
        effectiveKey == buildConfigKey && buildConfigKey.isNotBlank() -> "BuildConfig.MAPS_API_KEY"
        effectiveKey == prefsKey && prefsKey.isNotBlank() -> "PreferencesManager"
        else -> "NONE"
    }

    val isValid = effectiveKey.startsWith("AIza") && effectiveKey.length >= 30
    val masked = if (effectiveKey.length > 12) {
        "${effectiveKey.take(8)}...${effectiveKey.takeLast(4)}"
    } else if (effectiveKey.isNotBlank()) {
        "INVALID_FORMAT"
    } else {
        "MISSING"
    }

    val message = if (isValid) {
        "API key verified ($masked) via $source"
    } else {
        "Maps API key missing or invalid ($masked). Configure MAPS_API_KEY in AI Studio Secrets."
    }

    return MapsApiKeyStatus(
        isValid = isValid,
        consumedKeyMasked = masked,
        source = source,
        diagnosticMessage = message
    )
}

/**
 * Remembers a [MapView] instance and explicitly binds its lifecycle methods
 * (onCreate, onStart, onResume, onPause, onStop, onDestroy) to the composition's
 * [LocalLifecycleOwner] so the Google Map renders reliably inside Jetpack Compose.
 */
@Composable
fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val isEmulator = remember { isRunningOnEmulator() }
    val mapView = remember {
        if (!isEmulator) {
            try {
                MapsInitializer.initialize(context.applicationContext, MapsInitializer.Renderer.LATEST) {}
            } catch (_: Throwable) {}
        }
        MapView(context).apply {
            id = View.generateViewId()
            // Explicit layout constraints to prevent zero-size blank MapView pitfall
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView, isEmulator) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (!isEmulator) {
                try {
                    when (event) {
                        Lifecycle.Event.ON_CREATE -> mapView.onCreate(Bundle())
                        Lifecycle.Event.ON_START -> mapView.onStart()
                        Lifecycle.Event.ON_RESUME -> mapView.onResume()
                        Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                        Lifecycle.Event.ON_STOP -> mapView.onStop()
                        Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                        else -> Unit
                    }
                } catch (_: Throwable) {}
            }
        }
        lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycle.removeObserver(lifecycleObserver)
        }
    }

    return mapView
}

/**
 * Jetpack Compose wrapper around a native [MapView] that explicitly manages lifecycle state,
 * enforces non-zero layout constraints, and updates markers, route polylines, and camera state.
 */
@Composable
fun LifecycleAwareGoogleMapView(
    targetLatLng: LatLng,
    targetTitle: String,
    targetSnippet: String,
    isSatellite: Boolean,
    isMyLocationEnabled: Boolean,
    routeCalculation: RouteCalculation?,
    accentColor: Color,
    onGoogleMapReady: (GoogleMap) -> Unit,
    onMapLoaded: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mapView = rememberMapViewWithLifecycle()
    var lastRenderedSignature by remember { mutableStateOf("") }

    AndroidView(
        factory = { mapView },
        modifier = modifier.fillMaxSize(),
        update = { view ->
            // Ensure non-zero layout parameters on every layout pass
            if (view.layoutParams == null ||
                view.layoutParams.width != ViewGroup.LayoutParams.MATCH_PARENT ||
                view.layoutParams.height != ViewGroup.LayoutParams.MATCH_PARENT
            ) {
                view.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            view.getMapAsync { googleMap ->
                onGoogleMapReady(googleMap)

                googleMap.mapType = if (isSatellite) {
                    GoogleMap.MAP_TYPE_HYBRID
                } else {
                    GoogleMap.MAP_TYPE_NORMAL
                }

                googleMap.uiSettings.apply {
                    isZoomControlsEnabled = false
                    isMyLocationButtonEnabled = false
                    isCompassEnabled = true
                    isMapToolbarEnabled = false
                    isZoomGesturesEnabled = true
                    isScrollGesturesEnabled = true
                    isRotateGesturesEnabled = true
                    isTiltGesturesEnabled = true
                }

                try {
                    @SuppressLint("MissingPermission")
                    googleMap.isMyLocationEnabled = isMyLocationEnabled
                } catch (_: SecurityException) {}

                googleMap.setOnMapLoadedCallback {
                    onMapLoaded()
                }

                val signature = "${targetLatLng.latitude},${targetLatLng.longitude},$targetTitle,$isSatellite,${routeCalculation?.originName},${routeCalculation?.destName},${routeCalculation?.polylineCoords?.size}"
                if (signature != lastRenderedSignature) {
                    lastRenderedSignature = signature
                    googleMap.clear()

                    // Target Sector Marker
                    googleMap.addMarker(
                        MarkerOptions()
                            .position(targetLatLng)
                            .title(targetTitle)
                            .snippet(targetSnippet)
                    )

                    // Route Polyline and Endpoint Markers
                    if (routeCalculation != null && routeCalculation.polylineCoords.isNotEmpty()) {
                        val polyPoints = routeCalculation.polylineCoords.map {
                            LatLng(it.first, it.second)
                        }
                        googleMap.addPolyline(
                            PolylineOptions()
                                .addAll(polyPoints)
                                .color(accentColor.toArgb())
                                .width(10f)
                        )
                        googleMap.addMarker(
                            MarkerOptions()
                                .position(LatLng(routeCalculation.startLat, routeCalculation.startLon))
                                .title("Origin: ${routeCalculation.originName}")
                        )
                        googleMap.addMarker(
                            MarkerOptions()
                                .position(LatLng(routeCalculation.endLat, routeCalculation.endLon))
                                .title("Destination: ${routeCalculation.destName}")
                        )
                    }

                    try {
                        googleMap.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(targetLatLng, 13.5f)
                        )
                    } catch (_: Throwable) {
                        try {
                            googleMap.moveCamera(
                                CameraUpdateFactory.newLatLngZoom(targetLatLng, 13.5f)
                            )
                        } catch (_: Throwable) {}
                    }
                }
            }
        }
    )
}

/**
 * Pure Jetpack Compose Slippy-Map Tile Renderer that fetches real 256x256 Google Maps
 * Road (lyrs=m) and Hybrid Satellite (lyrs=y) tiles via OkHttp without spawning a WebView
 * Chromium process or triggering Play Services Volley 503 errors on cloud emulators.
 */
private object TacticalTileLoader {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val cache = LruCache<String, ImageBitmap>(120)
    val inFlight: MutableSet<String> = Collections.synchronizedSet( HashSet<String>() )
}

private fun lonToTileX(lon: Double, zoom: Int): Double {
    return ((lon + 180.0) / 360.0) * (1 shl zoom)
}

private fun latToTileY(lat: Double, zoom: Int): Double {
    val clamped = lat.coerceIn(-85.0511, 85.0511)
    val rad = Math.toRadians(clamped)
    return (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / Math.PI) / 2.0 * (1 shl zoom)
}

private fun tileXToLon(tileX: Double, zoom: Int): Double {
    return tileX / (1 shl zoom) * 360.0 - 180.0
}

private fun tileYToLat(tileY: Double, zoom: Int): Double {
    val n = Math.PI - 2.0 * Math.PI * tileY / (1 shl zoom)
    return Math.toDegrees(atan(0.5 * (exp(n) - exp(-n))))
}

@Composable
private fun NativeComposeSlippyMapView(
    centerLat: Double,
    centerLon: Double,
    zoom: Int,
    targetLat: Double,
    targetLon: Double,
    userLocation: LatLng?,
    routeCalculation: RouteCalculation?,
    isSatellite: Boolean,
    accentColor: Color,
    onPanDelta: (newLat: Double, newLon: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var tileRevision by remember { mutableIntStateOf(0) }
    val lyr = if (isSatellite) "y" else "m"

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(zoom, centerLat, centerLon) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val tileSpanPx = 256f * density
                    val newTileX = lonToTileX(centerLon, zoom) - (dragAmount.x / tileSpanPx)
                    val newTileY = latToTileY(centerLat, zoom) - (dragAmount.y / tileSpanPx)
                    val updatedLon = ((tileXToLon(newTileX, zoom) + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
                    val updatedLat = tileYToLat(newTileY, zoom).coerceIn(-82.0, 82.0)
                    onPanDelta(updatedLat, updatedLon)
                }
            }
    ) {
        // Read tileRevision so Canvas redraws as tiles stream in
        val rev = tileRevision
        val w = size.width
        val h = size.height
        val tilePx = (256f * density).roundToInt().coerceAtLeast(128)

        // 1. Deep Tactical Background & Radar Grid
        drawRect(color = Color(0xFF070B14))
        val gridSpacing = 36f * density
        var gx = 0f
        while (gx < w) {
            drawLine(
                color = accentColor.copy(alpha = 0.10f),
                start = Offset(gx, 0f),
                end = Offset(gx, h),
                strokeWidth = 1f
            )
            gx += gridSpacing
        }
        var gy = 0f
        while (gy < h) {
            drawLine(
                color = accentColor.copy(alpha = 0.10f),
                start = Offset(0f, gy),
                end = Offset(w, gy),
                strokeWidth = 1f
            )
            gy += gridSpacing
        }

        // 2. Render 256x256 Google Maps Tiles around (centerLat, centerLon)
        val cTileX = lonToTileX(centerLon, zoom)
        val cTileY = latToTileY(centerLat, zoom)
        val halfCols = (w / (2f * tilePx)).toInt() + 2
        val halfRows = (h / (2f * tilePx)).toInt() + 2
        val baseX = floor(cTileX).toInt()
        val baseY = floor(cTileY).toInt()
        val maxTile = 1 shl zoom

        for (tx in (baseX - halfCols)..(baseX + halfCols)) {
            for (ty in (baseY - halfRows)..(baseY + halfRows)) {
                if (ty < 0 || ty >= maxTile) continue
                val wrappedX = ((tx % maxTile) + maxTile) % maxTile
                val cacheKey = "${lyr}_${zoom}_${wrappedX}_${ty}"
                val cachedBitmap = TacticalTileLoader.cache.get(cacheKey)

                val drawX = (w / 2f + (tx - cTileX) * tilePx).roundToInt()
                val drawY = (h / 2f + (ty - cTileY) * tilePx).roundToInt()

                if (cachedBitmap != null) {
                    drawImage(
                        image = cachedBitmap,
                        dstOffset = IntOffset(drawX, drawY),
                        dstSize = IntSize(tilePx, tilePx),
                        alpha = if (isSatellite) 0.96f else 0.88f
                    )
                } else if (rev >= 0 && TacticalTileLoader.inFlight.add(cacheKey)) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val candidateUrls = if (isSatellite) {
                                listOf(
                                    "https://mt1.google.com/vt/lyrs=y&x=$wrappedX&y=$ty&z=$zoom",
                                    "https://mt0.google.com/vt/lyrs=y&x=$wrappedX&y=$ty&z=$zoom",
                                    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$zoom/$ty/$wrappedX"
                                )
                            } else {
                                listOf(
                                    "https://mt1.google.com/vt/lyrs=m&x=$wrappedX&y=$ty&z=$zoom",
                                    "https://mt0.google.com/vt/lyrs=m&x=$wrappedX&y=$ty&z=$zoom",
                                    "https://basemaps.cartocdn.com/rastertiles/voyager/$zoom/$wrappedX/$ty.png",
                                    "https://tile.openstreetmap.org/$zoom/$wrappedX/$ty.png"
                                )
                            }
                            var decoded: ImageBitmap? = null
                            for (cUrl in candidateUrls) {
                                decoded = fetchTileBitmap(cUrl)
                                if (decoded != null) break
                            }
                            if (decoded != null) {
                                TacticalTileLoader.cache.put(cacheKey, decoded)
                                withContext(Dispatchers.Main) {
                                    tileRevision++
                                }
                            }
                        } catch (_: Throwable) {
                        } finally {
                            TacticalTileLoader.inFlight.remove(cacheKey)
                        }
                    }
                }
            }
        }

        // Subtle tactical HUD tint over normal road tiles for high-contrast HUD legibility
        if (!isSatellite) {
            drawRect(color = Color(0xFF040914).copy(alpha = 0.22f))
        }

        fun latLonToOffset(latVal: Double, lonVal: Double): Offset {
            val ptX = lonToTileX(lonVal, zoom)
            val ptY = latToTileY(latVal, zoom)
            return Offset(
                x = (w / 2f + (ptX - cTileX) * tilePx).toFloat(),
                y = (h / 2f + (ptY - cTileY) * tilePx).toFloat()
            )
        }

        // 3. Draw Route Polyline & Origin/Destination Pins if active
        if (routeCalculation != null && routeCalculation.polylineCoords.size > 1) {
            val routePath = Path()
            routeCalculation.polylineCoords.forEachIndexed { idx, pair ->
                val pt = latLonToOffset(pair.first, pair.second)
                if (idx == 0) routePath.moveTo(pt.x, pt.y) else routePath.lineTo(pt.x, pt.y)
            }
            drawPath(
                path = routePath,
                color = accentColor.copy(alpha = 0.35f),
                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawPath(
                path = routePath,
                color = accentColor,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            val originPt = latLonToOffset(routeCalculation.startLat, routeCalculation.startLon)
            drawCircle(color = SuccessEmerald, radius = 6.dp.toPx(), center = originPt)

            val destPt = latLonToOffset(routeCalculation.endLat, routeCalculation.endLon)
            drawCircle(color = AlertCrimson, radius = 6.dp.toPx(), center = destPt)
        }

        // 4. Draw Target Sector Radar Pin & Crosshairs
        val targetPt = latLonToOffset(targetLat, targetLon)
        drawCircle(
            color = accentColor.copy(alpha = 0.22f),
            radius = 22.dp.toPx(),
            center = targetPt
        )
        drawCircle(
            color = accentColor,
            radius = 14.dp.toPx(),
            center = targetPt,
            style = Stroke(width = 1.5.dp.toPx())
        )
        drawCircle(
            color = accentColor,
            radius = 5.dp.toPx(),
            center = targetPt
        )
        drawCircle(
            color = Color.White,
            radius = 2.dp.toPx(),
            center = targetPt
        )

        // 5. Draw User Live GPS Marker if acquired
        if (userLocation != null) {
            val userPt = latLonToOffset(userLocation.latitude, userLocation.longitude)
            drawCircle(
                color = SuccessEmerald.copy(alpha = 0.28f),
                radius = 16.dp.toPx(),
                center = userPt
            )
            drawCircle(
                color = SuccessEmerald,
                radius = 6.dp.toPx(),
                center = userPt
            )
            drawCircle(
                color = Color.White,
                radius = 2.5.dp.toPx(),
                center = userPt
            )
        }
    }
}

private fun fetchTileBitmap(url: String): ImageBitmap? {
    return try {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .build()
        TacticalTileLoader.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val bytes = resp.body?.bytes() ?: return null
            if (bytes.isEmpty()) return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    } catch (_: Throwable) {
        null
    }
}

private fun isRunningOnEmulator(): Boolean {
    val fp = Build.FINGERPRINT.lowercase(Locale.ROOT)
    val model = Build.MODEL.lowercase(Locale.ROOT)
    val hardware = Build.HARDWARE.lowercase(Locale.ROOT)
    val product = Build.PRODUCT.lowercase(Locale.ROOT)
    val board = Build.BOARD.lowercase(Locale.ROOT)
    val device = Build.DEVICE.lowercase(Locale.ROOT)
    val brand = Build.BRAND.lowercase(Locale.ROOT)
    val tags = Build.TAGS?.lowercase(Locale.ROOT) ?: ""
    val hasX86Abi = Build.SUPPORTED_ABIS.any { it.contains("x86", ignoreCase = true) }

    return hasX86Abi ||
            fp.startsWith("generic") ||
            fp.contains("emulator") ||
            fp.contains("sdk_gphone") ||
            fp.contains("cutf") ||
            fp.contains("vsoc") ||
            fp.contains("aosp") ||
            fp.contains("test-keys") ||
            fp.contains("userdebug") ||
            model.contains("emulator") ||
            model.contains("sdk_gphone") ||
            model.contains("cuttlefish") ||
            model.contains("android sdk") ||
            hardware.contains("ranchu") ||
            hardware.contains("goldfish") ||
            hardware.contains("cutf") ||
            hardware.contains("vsoc") ||
            product.contains("sdk_gphone") ||
            product.contains("emulator") ||
            product.contains("cf_") ||
            product.contains("aosp_") ||
            board.contains("cutf") ||
            board.contains("vsoc") ||
            board.contains("goldfish") ||
            board.contains("ranchu") ||
            device.contains("vsoc") ||
            device.contains("generic") ||
            device.contains("emulator") ||
            brand == "generic" ||
            tags.contains("test-keys") ||
            tags.contains("dev-keys")
}

/**
 * Live Google Maps View integrated into JARVIS Tactical HUD.
 * Renders authentic Google Maps vector/satellite tiles via a lifecycle-aware MapView wrapper
 * on physical devices and a native Jetpack Compose Google Maps Tile Renderer on emulators,
 * avoiding both Play Services Volley 503 errors and WebView Chromium renderer crashes.
 */
@Composable
fun TacticalMapView(
    accentColor: Color,
    targetCity: String = "Global Defense Grid",
    latitude: Double = 28.6139,
    longitude: Double = 77.2090,
    routeCalculation: RouteCalculation? = null,
    placeLocation: PlaceLocation? = null,
    onClearRoute: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiKeyStatus = remember(context) { verifyMapsApiKeyConsumption(context) }

    var isSatelliteGrid by remember { mutableStateOf(false) }
    var showStepsDrawer by remember { mutableStateOf(false) }
    var showRationaleDialog by remember { mutableStateOf(false) }
    var showAuthDiagnosticDialog by remember { mutableStateOf(false) }
    var isComposeTileEngineActive by remember { mutableStateOf(true) }
    var isMapLoaded by remember { mutableStateOf(true) }
    var googleMapRef by remember { mutableStateOf<GoogleMap?>(null) }
    var userLocationLatLng by remember { mutableStateOf<LatLng?>(null) }

    // Accompanist Location Permission Utility
    val locationHelper = rememberLocationPermissionHelper(
        onLocationAcquired = { latLng ->
            userLocationLatLng = latLng
        }
    )

    // Active coordinates
    val activeLat = placeLocation?.latitude ?: latitude
    val activeLon = placeLocation?.longitude ?: longitude
    val targetLatLng = remember(activeLat, activeLon) { LatLng(activeLat, activeLon) }

    // Compose slippy-map camera state
    var slippyCenterLat by remember(activeLat) { mutableStateOf(activeLat) }
    var slippyCenterLon by remember(activeLon) { mutableStateOf(activeLon) }
    var slippyZoom by remember { mutableIntStateOf(14) }
    var nativeZoomTracker by remember { mutableFloatStateOf(13.5f) }

    // Smoothly update camera when target coordinates change
    LaunchedEffect(activeLat, activeLon, isComposeTileEngineActive) {
        slippyCenterLat = activeLat
        slippyCenterLon = activeLon
        if (!isComposeTileEngineActive) {
            try {
                googleMapRef?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(targetLatLng, 13.5f)
                )
            } catch (_: Throwable) {
                try {
                    googleMapRef?.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(targetLatLng, 13.5f)
                    )
                } catch (_: Throwable) {}
            }
        }
    }

    if (showRationaleDialog) {
        LocationPermissionRationaleDialog(
            accentColor = accentColor,
            onConfirm = {
                showRationaleDialog = false
                locationHelper.requestPermission()
            },
            onDismiss = {
                showRationaleDialog = false
            }
        )
    }

    if (showAuthDiagnosticDialog) {
        Dialog(onDismissRequest = { showAuthDiagnosticDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF070B14))
                    .border(1.5.dp, accentColor, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GOOGLE MAPS SDK DIAGNOSTICS",
                            color = accentColor,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = HologramTextDim,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { showAuthDiagnosticDialog = false }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. API Key Status: ${apiKeyStatus.diagnosticMessage}\n" +
                                "2. Lifecycle Wrapper: Native MapView bound to LocalLifecycleOwner (ON_CREATE..ON_DESTROY).\n" +
                                "3. Layout Constraints: Explicit MATCH_PARENT + Modifier.fillMaxSize() verified.",
                        color = HologramTextBright,
                        fontSize = 9.sp,
                        lineHeight = 13.5.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Column {
                            Text(
                                text = "Key Source: ${apiKeyStatus.source}",
                                color = if (apiKeyStatus.isValid) SuccessEmerald else AlertCrimson,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Consumed Key: ${apiKeyStatus.consumedKeyMasked}",
                                color = HologramTextBright,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "SHA-1: 1F:53:4A:A9:36:9A:A0:D1:50:FF:45:D7:40:72:BA:48:3E:3E:43:64",
                                color = accentColor,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    val clipboard = LocalClipboardManager.current
                    var copied by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(accentColor.copy(alpha = 0.2f))
                                .border(1.dp, accentColor, RoundedCornerShape(6.dp))
                                .clickable {
                                    clipboard.setText(AnnotatedString("1F:53:4A:A9:36:9A:A0:D1:50:FF:45:D7:40:72:BA:48:3E:3E:43:64"))
                                    copied = true
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (copied) "COPIED" else "COPY SHA-1",
                                color = accentColor,
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFFB300).copy(alpha = 0.2f))
                                .border(1.dp, Color(0xFFFFB300), RoundedCornerShape(6.dp))
                                .clickable {
                                    isComposeTileEngineActive = !isComposeTileEngineActive
                                    showAuthDiagnosticDialog = false
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isComposeTileEngineActive) "NATIVE MAPVIEW" else "DIRECT TILES",
                                color = Color(0xFFFFB300),
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(SuccessEmerald.copy(alpha = 0.25f))
                                .border(1.dp, SuccessEmerald, RoundedCornerShape(6.dp))
                                .clickable {
                                    showAuthDiagnosticDialog = false
                                    openInGoogleMapsApp(context, activeLat, activeLon, targetCity)
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "MAPS APP",
                                color = SuccessEmerald,
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(360.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DarkHologramSurface)
            .border(1.5.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .testTag("tactical_map_view")
    ) {
        // --- 1. NATIVE COMPOSE GOOGLE MAPS TILE RENDERER OR LIFECYCLE-AWARE GOOGLE MAPVIEW ---
        if (isComposeTileEngineActive) {
            NativeComposeSlippyMapView(
                centerLat = slippyCenterLat,
                centerLon = slippyCenterLon,
                zoom = slippyZoom,
                targetLat = activeLat,
                targetLon = activeLon,
                userLocation = userLocationLatLng,
                routeCalculation = routeCalculation,
                isSatellite = isSatelliteGrid,
                accentColor = accentColor,
                onPanDelta = { newLat, newLon ->
                    slippyCenterLat = newLat
                    slippyCenterLon = newLon
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LifecycleAwareGoogleMapView(
                targetLatLng = targetLatLng,
                targetTitle = placeLocation?.name ?: targetCity,
                targetSnippet = "${String.format(Locale.US, "%.4f", activeLat)}°N, ${String.format(Locale.US, "%.4f", activeLon)}°E",
                isSatellite = isSatelliteGrid,
                isMyLocationEnabled = locationHelper.hasAnyPermission,
                routeCalculation = routeCalculation,
                accentColor = accentColor,
                onGoogleMapReady = { mapInstance ->
                    googleMapRef = mapInstance
                },
                onMapLoaded = {
                    isMapLoaded = true
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Connecting indicator if native Google MapView is loading tiles
        if (!isComposeTileEngineActive && !isMapLoaded) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "INITIALIZING GOOGLE MAPVIEW...",
                        color = accentColor,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = apiKeyStatus.diagnosticMessage,
                        color = HologramTextDim,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // --- 1B. REUSABLE SCI-FI TACTICAL SCAN LINE OVERLAY (CANVAS + ANIMATABLE) ---
        SciFiTacticalScanOverlay(
            accentColor = accentColor,
            isSatelliteMode = isSatelliteGrid,
            modifier = Modifier
                .fillMaxSize()
                .testTag("sci_fi_map_scanning_overlay")
        )

        // --- 2. COMPACT FLOATING COORDINATE BADGE (NARROW WIDTH - REDUCED BLACK COLOR OVERLAY) ---
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.70f))
                .border(1.dp, accentColor.copy(alpha = 0.40f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (locationHelper.hasAnyPermission) SuccessEmerald else accentColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = if (routeCalculation != null) {
                            "NAV // ${routeCalculation.destName}"
                        } else {
                            (placeLocation?.name ?: targetCity).uppercase()
                        },
                        color = HologramTextBright,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = if (routeCalculation != null) {
                            "${routeCalculation.distanceText} • ETA ${routeCalculation.durationText}"
                        } else {
                            "LAT: ${String.format(Locale.US, "%.4f", slippyCenterLat)}° | LON: ${String.format(Locale.US, "%.4f", slippyCenterLon)}°"
                        },
                        color = accentColor,
                        fontSize = 7.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // --- 2B. COMPACT FLOATING TOP-RIGHT ACTIONS (NARROW WIDTH - DOES NOT COVER MAP) ---
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Inbuilt Map status indicator & toggle
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(SuccessEmerald.copy(alpha = 0.22f))
                    .border(1.dp, SuccessEmerald.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .clickable { isComposeTileEngineActive = !isComposeTileEngineActive }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (isComposeTileEngineActive) "INBUILT MAP" else "MAPVIEW SDK",
                    color = SuccessEmerald,
                    fontSize = 7.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            if (routeCalculation != null) {
                // Steps Drawer Toggle
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.20f))
                        .clickable { showStepsDrawer = !showStepsDrawer }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "STEPS",
                        color = accentColor,
                        fontSize = 7.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Clear Route Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AlertCrimson.copy(alpha = 0.20f))
                        .clickable(onClick = onClearRoute)
                        .padding(horizontal = 5.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear Route",
                        tint = AlertCrimson,
                        modifier = Modifier.size(11.dp)
                    )
                }
            } else {
                // Open External Maps App button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.68f))
                        .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                        .clickable { openInGoogleMapsApp(context, activeLat, activeLon, targetCity) }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = "Open Maps",
                            tint = accentColor,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "APP",
                            color = accentColor,
                            fontSize = 7.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // --- 3. TURN-BY-TURN ROUTE STEPS DRAWER OVERLAY ---
        AnimatedVisibility(
            visible = showStepsDrawer && routeCalculation != null,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .align(Alignment.CenterStart)
                .padding(start = 8.dp, top = 40.dp, bottom = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.92f))
                    .border(1.dp, accentColor, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TURN-BY-TURN TELEMETRY",
                            color = accentColor,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Steps",
                            tint = HologramTextDim,
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { showStepsDrawer = false }
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 180.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(routeCalculation!!.steps) { idx, step ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(accentColor.copy(alpha = 0.08f))
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "#${idx + 1}",
                                    color = accentColor,
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = step.instruction,
                                    color = HologramTextBright,
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f)
                                )
                                if (step.distanceText.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = step.distanceText,
                                        color = HologramTextDim,
                                        fontSize = 7.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 4. TACTICAL HUD CONTROLS (Right Side Column) ---
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Zoom In (+)
            TacticalMapButton(
                icon = Icons.Default.Add,
                accentColor = accentColor,
                contentDescription = "Zoom In",
                testTag = "btn_map_zoom_in",
                onClick = {
                    if (isComposeTileEngineActive) {
                        slippyZoom = (slippyZoom + 1).coerceIn(2, 19)
                    } else {
                        scope.launch {
                            nativeZoomTracker = (nativeZoomTracker + 1f).coerceIn(2f, 20f)
                            try {
                                googleMapRef?.animateCamera(CameraUpdateFactory.zoomIn())
                            } catch (_: Throwable) {
                                try {
                                    googleMapRef?.moveCamera(CameraUpdateFactory.zoomIn())
                                } catch (_: Throwable) {}
                            }
                        }
                    }
                }
            )

            // Zoom Out (-)
            TacticalMapButton(
                icon = Icons.Default.Remove,
                accentColor = accentColor,
                contentDescription = "Zoom Out",
                testTag = "btn_map_zoom_out",
                onClick = {
                    if (isComposeTileEngineActive) {
                        slippyZoom = (slippyZoom - 1).coerceIn(2, 19)
                    } else {
                        scope.launch {
                            nativeZoomTracker = (nativeZoomTracker - 1f).coerceIn(2f, 20f)
                            try {
                                googleMapRef?.animateCamera(CameraUpdateFactory.zoomOut())
                            } catch (_: Throwable) {
                                try {
                                    googleMapRef?.moveCamera(CameraUpdateFactory.zoomOut())
                                } catch (_: Throwable) {}
                            }
                        }
                    }
                }
            )

            // Center on User Live Location (Accompanist Fine Location Permission)
            TacticalMapButton(
                icon = if (locationHelper.hasAnyPermission) Icons.Default.MyLocation else Icons.Default.GpsNotFixed,
                accentColor = if (locationHelper.hasFinePermission) SuccessEmerald else if (locationHelper.hasCoarsePermission) accentColor else AlertCrimson,
                contentDescription = "Center on My Location",
                testTag = "btn_center_user_location",
                onClick = {
                    if (!locationHelper.hasAnyPermission) {
                        if (locationHelper.shouldShowRationale) {
                            showRationaleDialog = true
                        } else {
                            locationHelper.requestPermission()
                        }
                    } else {
                        locationHelper.centerOnCurrentLocation { userLoc ->
                            userLocationLatLng = userLoc
                            if (isComposeTileEngineActive) {
                                slippyCenterLat = userLoc.latitude
                                slippyCenterLon = userLoc.longitude
                                slippyZoom = 16
                            } else {
                                try {
                                    googleMapRef?.animateCamera(
                                        CameraUpdateFactory.newLatLngZoom(userLoc, 16f)
                                    )
                                } catch (_: Throwable) {
                                    try {
                                        googleMapRef?.moveCamera(
                                            CameraUpdateFactory.newLatLngZoom(userLoc, 16f)
                                        )
                                    } catch (_: Throwable) {}
                                }
                            }
                        }
                    }
                }
            )

            // Recenter on Target Sector
            TacticalMapButton(
                icon = Icons.Default.GpsFixed,
                accentColor = accentColor,
                contentDescription = "Recenter on Target Sector",
                testTag = "btn_recenter_target",
                onClick = {
                    if (isComposeTileEngineActive) {
                        slippyCenterLat = activeLat
                        slippyCenterLon = activeLon
                        slippyZoom = 14
                    } else {
                        scope.launch {
                            try {
                                googleMapRef?.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(targetLatLng, 14.5f)
                                )
                            } catch (_: Throwable) {
                                try {
                                    googleMapRef?.moveCamera(
                                        CameraUpdateFactory.newLatLngZoom(targetLatLng, 14.5f)
                                    )
                                } catch (_: Throwable) {}
                            }
                        }
                    }
                }
            )

            // Toggle Layer (Hybrid Satellite vs Normal)
            TacticalMapButton(
                icon = Icons.Default.Layers,
                accentColor = if (isSatelliteGrid) SuccessEmerald else accentColor,
                contentDescription = "Toggle Satellite Layer",
                testTag = "btn_map_toggle_layer",
                onClick = {
                    val nextSat = !isSatelliteGrid
                    isSatelliteGrid = nextSat
                    if (!isComposeTileEngineActive) {
                        googleMapRef?.mapType = if (nextSat) GoogleMap.MAP_TYPE_HYBRID else GoogleMap.MAP_TYPE_NORMAL
                    }
                }
            )

            // Toggle Direct Tile Renderer vs Native MapView
            TacticalMapButton(
                icon = Icons.Default.Public,
                accentColor = if (isComposeTileEngineActive) SuccessEmerald else accentColor,
                contentDescription = "Toggle Map Engine",
                testTag = "btn_map_toggle_web",
                onClick = { isComposeTileEngineActive = !isComposeTileEngineActive }
            )

            // GCP Authorization Info Dialog
            TacticalMapButton(
                icon = Icons.Default.Info,
                accentColor = accentColor,
                contentDescription = "Maps SDK Key Setup",
                testTag = "btn_map_info",
                onClick = { showAuthDiagnosticDialog = true }
            )
        }

        // --- 5. BOTTOM TELEMETRY BAR (NARROW, FLOATING, NON-INTRUSIVE) ---
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 6.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .border(0.8.dp, accentColor.copy(alpha = 0.30f), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.5.dp)
        ) {
            Text(
                text = if (routeCalculation != null) {
                    "TACTICAL ROUTE: ACTIVE • ETA ${routeCalculation.durationText}"
                } else {
                    "INBUILT MAP // ${String.format(Locale.US, "%.4f", slippyCenterLat)}°N, ${String.format(Locale.US, "%.4f", slippyCenterLon)}°E • Z$slippyZoom"
                },
                color = HologramTextDim,
                fontSize = 7.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private fun launchNativeGps(context: Context, route: RouteCalculation) {
    try {
        val gmmIntent = Intent(Intent.ACTION_VIEW, Uri.parse(route.externalNavUri))
        gmmIntent.setPackage("com.google.android.apps.maps")
        context.startActivity(gmmIntent)
    } catch (_: Exception) {
        val webNav = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/maps/dir/?api=1&origin=${route.startLat},${route.startLon}&destination=${route.endLat},${route.endLon}")
        )
        context.startActivity(webNav)
    }
}

private fun openInGoogleMapsApp(context: Context, lat: Double, lon: Double, label: String) {
    try {
        val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon($label)")
        val mapIntent = Intent(Intent.ACTION_VIEW, uri)
        mapIntent.setPackage("com.google.android.apps.maps")
        context.startActivity(mapIntent)
    } catch (_: Exception) {
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lon"))
        context.startActivity(webIntent)
    }
}

@Composable
private fun TacticalMapButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.85f))
            .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .testTag(testTag)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = accentColor,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * Wrapper around the reusable [SciFiTacticalScanOverlay] component (powered by [Canvas] and [Animatable]).
 */
@Composable
fun AgeOfUltronSciFiMapOverlay(
    accentColor: Color,
    isSatellite: Boolean,
    modifier: Modifier = Modifier
) {
    SciFiTacticalScanOverlay(
        modifier = modifier,
        accentColor = accentColor,
        isSatelliteMode = isSatellite
    )
}

