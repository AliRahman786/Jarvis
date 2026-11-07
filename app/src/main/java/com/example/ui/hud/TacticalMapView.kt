package com.example.ui.hud

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
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
import com.example.ui.theme.DarkHologramSurface
import com.example.ui.theme.HologramTextBright
import com.example.ui.theme.HologramTextDim
import com.example.ui.theme.SuccessEmerald
import com.example.ui.util.LocationPermissionRationaleDialog
import com.example.ui.util.rememberLocationPermissionHelper
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.maps.android.compose.GoogleMap as ComposeGoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import java.util.Collections
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.tan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Supported Google Maps Compose Styles: SATELLITE, HYBRID, TERRAIN, and NORMAL.
 */
enum class TacticalMapStyle(
    val label: String,
    val composeMapType: MapType,
    val googleMapTypeInt: Int,
    val tileLayerCode: String
) {
    SATELLITE("SATELLITE", MapType.SATELLITE, GoogleMap.MAP_TYPE_SATELLITE, "s"),
    HYBRID("HYBRID", MapType.HYBRID, GoogleMap.MAP_TYPE_HYBRID, "y"),
    TERRAIN("TERRAIN", MapType.TERRAIN, GoogleMap.MAP_TYPE_TERRAIN, "p"),
    NORMAL("NORMAL", MapType.NORMAL, GoogleMap.MAP_TYPE_NORMAL, "m");

    val isAerialStyle: Boolean
        get() = this == SATELLITE || this == HYBRID

    fun nextStyle(): TacticalMapStyle {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }
}

/**
 * Represents a dynamically grouped cluster of [PlaceLocation] markers at a given zoom level.
 */
data class MapMarkerCluster(
    val id: String,
    val centerLat: Double,
    val centerLon: Double,
    val items: List<PlaceLocation>
) {
    val count: Int get() = items.size
    val isCluster: Boolean get() = items.size > 1
    val primaryPlace: PlaceLocation get() = items.first()
}

/**
 * Groups nearby [PlaceLocation] search results into [MapMarkerCluster] instances based on
 * Mercator pixel distance at the active [zoom] level to prevent UI marker clutter.
 */
fun clusterSearchLocations(
    places: List<PlaceLocation>,
    zoom: Float,
    clusterRadiusTileFraction: Double = 0.24
): List<MapMarkerCluster> {
    if (places.isEmpty()) return emptyList()
    val intZoom = zoom.roundToInt().coerceIn(2, 20)
    // At very close street zoom (>= 17), show individual markers without clustering
    if (intZoom >= 17 || places.size == 1) {
        return places.mapIndexed { idx, p ->
            MapMarkerCluster(
                id = "single_${idx}_${p.name}",
                centerLat = p.latitude,
                centerLon = p.longitude,
                items = listOf(p)
            )
        }
    }

    val assigned = BooleanArray(places.size)
    val clusters = mutableListOf<MapMarkerCluster>()

    for (i in places.indices) {
        if (assigned[i]) continue
        assigned[i] = true
        val seed = places[i]
        val seedTileX = lonToTileX(seed.longitude, intZoom)
        val seedTileY = latToTileY(seed.latitude, intZoom)

        val group = mutableListOf(seed)
        for (j in (i + 1) until places.size) {
            if (assigned[j]) continue
            val candidate = places[j]
            val candTileX = lonToTileX(candidate.longitude, intZoom)
            val candTileY = latToTileY(candidate.latitude, intZoom)
            val distInTiles = hypot(candTileX - seedTileX, candTileY - seedTileY)
            if (distInTiles <= clusterRadiusTileFraction) {
                assigned[j] = true
                group.add(candidate)
            }
        }

        val avgLat = group.map { it.latitude }.average()
        val avgLon = group.map { it.longitude }.average()
        clusters.add(
            MapMarkerCluster(
                id = "cluster_${i}_${group.size}",
                centerLat = avgLat,
                centerLon = avgLon,
                items = group
            )
        )
    }

    return clusters
}

/**
 * Creates a custom tactical bitmap icon for a marker cluster badge showing the item count.
 */
private fun createClusterMarkerBitmap(count: Int, accentColor: Color): Bitmap {
    val sizePx = 96
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    val cx = sizePx / 2f
    val cy = sizePx / 2f

    val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor.copy(alpha = 0.28f).toArgb()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 44f, outerPaint)

    val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(7, 14, 26)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 32f, corePaint)

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor.toArgb()
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    canvas.drawCircle(cx, cy, 32f, strokePaint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 28f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(count.toString(), cx, textY, textPaint)

    return bmp
}

/**
 * Creates a custom tactical GPS beacon bitmap for the user's real-time location on the Google Map.
 */
private fun createCustomUserGpsMarkerBitmap(accentColor: Color): Bitmap {
    val sizePx = 84
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    val cx = sizePx / 2f
    val cy = sizePx / 2f

    val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SuccessEmerald.copy(alpha = 0.30f).toArgb()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 38f, haloPaint)

    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor.toArgb()
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    canvas.drawCircle(cx, cy, 26f, ringPaint)

    val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SuccessEmerald.toArgb()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 14f, centerPaint)

    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 5.5f, dotPaint)

    return bmp
}

data class MapsApiKeyStatus(
    val isValid: Boolean,
    val consumedKeyMasked: String,
    val source: String,
    val diagnosticMessage: String
)

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
 * Google Maps Compose view with support for Satellite, Hybrid, Terrain, and Normal MapType styles,
 * custom user GPS marker, route polylines, and marker clustering.
 */
@Composable
fun ComposeGoogleMapRenderer(
    targetLatLng: LatLng,
    targetTitle: String,
    targetSnippet: String,
    userLocation: LatLng?,
    mapStyle: TacticalMapStyle,
    isMyLocationEnabled: Boolean,
    routeCalculation: RouteCalculation?,
    clusters: List<MapMarkerCluster>,
    zoomLevel: Float,
    accentColor: Color,
    onClusterClick: (MapMarkerCluster) -> Unit,
    onMapLoaded: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(targetLatLng, zoomLevel)
    }

    LaunchedEffect(targetLatLng, zoomLevel) {
        try {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(targetLatLng, zoomLevel)
            )
        } catch (_: Throwable) {
            try {
                cameraPositionState.move(
                    CameraUpdateFactory.newLatLngZoom(targetLatLng, zoomLevel)
                )
            } catch (_: Throwable) {}
        }
    }

    // Automatically follow user's real-time GPS coordinates when updated
    LaunchedEffect(userLocation) {
        if (userLocation != null) {
            try {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(userLocation, zoomLevel.coerceAtLeast(15f))
                )
            } catch (_: Throwable) {}
        }
    }

    val mapProperties = remember(mapStyle, isMyLocationEnabled) {
        MapProperties(
            mapType = mapStyle.composeMapType,
            isMyLocationEnabled = isMyLocationEnabled
        )
    }

    val uiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            compassEnabled = true,
            mapToolbarEnabled = false,
            zoomGesturesEnabled = true,
            scrollGesturesEnabled = true,
            rotationGesturesEnabled = true,
            tiltGesturesEnabled = true
        )
    }

    ComposeGoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = mapProperties,
        uiSettings = uiSettings,
        onMapLoaded = onMapLoaded
    ) {
        // 1. Target Sector Marker
        Marker(
            state = remember(targetLatLng) { MarkerState(position = targetLatLng) },
            title = targetTitle,
            snippet = targetSnippet
        )

        // 2. Custom User Real-Time GPS Marker
        if (userLocation != null) {
            val userMarkerBmp = remember(accentColor) { createCustomUserGpsMarkerBitmap(accentColor) }
            Marker(
                state = remember(userLocation) { MarkerState(position = userLocation) },
                title = "Operator GPS Telemetry Lock",
                snippet = "${String.format(Locale.US, "%.4f", userLocation.latitude)}°N, ${String.format(Locale.US, "%.4f", userLocation.longitude)}°E",
                icon = BitmapDescriptorFactory.fromBitmap(userMarkerBmp)
            )
        }

        // 3. Clustered Search Result Markers
        clusters.forEach { cluster ->
            val pos = LatLng(cluster.centerLat, cluster.centerLon)
            if (cluster.isCluster) {
                val clusterBmp = remember(cluster.count, accentColor) {
                    createClusterMarkerBitmap(cluster.count, accentColor)
                }
                Marker(
                    state = remember(pos) { MarkerState(position = pos) },
                    title = "${cluster.count} Locations Clustered",
                    snippet = cluster.items.take(3).joinToString(", ") { it.name },
                    icon = BitmapDescriptorFactory.fromBitmap(clusterBmp),
                    onClick = {
                        onClusterClick(cluster)
                        true
                    }
                )
            } else {
                val place = cluster.primaryPlace
                Marker(
                    state = remember(pos) { MarkerState(position = pos) },
                    title = place.name,
                    snippet = "${place.category} • ${place.rating}★"
                )
            }
        }

        // 4. Route Polyline & Endpoints
        if (routeCalculation != null && routeCalculation.polylineCoords.isNotEmpty()) {
            val polyPoints = remember(routeCalculation) {
                routeCalculation.polylineCoords.map { LatLng(it.first, it.second) }
            }
            Polyline(
                points = polyPoints,
                color = accentColor,
                width = 10f
            )
            Marker(
                state = remember(routeCalculation.startLat, routeCalculation.startLon) {
                    MarkerState(position = LatLng(routeCalculation.startLat, routeCalculation.startLon))
                },
                title = "Origin: ${routeCalculation.originName}"
            )
            Marker(
                state = remember(routeCalculation.endLat, routeCalculation.endLon) {
                    MarkerState(position = LatLng(routeCalculation.endLat, routeCalculation.endLon))
                },
                title = "Destination: ${routeCalculation.destName}"
            )
        }
    }
}

private object TacticalTileLoader {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val cache = LruCache<String, ImageBitmap>(140)
    val inFlight: MutableSet<String> = Collections.synchronizedSet(HashSet<String>())
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
    clusters: List<MapMarkerCluster>,
    mapStyle: TacticalMapStyle,
    accentColor: Color,
    onPanDelta: (newLat: Double, newLon: Double) -> Unit,
    onClusterTap: (MapMarkerCluster) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var tileRevision by remember { mutableIntStateOf(0) }
    val lyr = mapStyle.tileLayerCode

    val textPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 24f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }

    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 19f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(zoom, centerLat, centerLon, clusters) {
                detectTapGestures { tapOffset ->
                    val w = size.width
                    val h = size.height
                    val tilePx = (256f * density).roundToInt().coerceAtLeast(128)
                    val cTileX = lonToTileX(centerLon, zoom)
                    val cTileY = latToTileY(centerLat, zoom)

                    for (cluster in clusters) {
                        val ptX = lonToTileX(cluster.centerLon, zoom)
                        val ptY = latToTileY(cluster.centerLat, zoom)
                        val screenX = (w / 2f + (ptX - cTileX) * tilePx).toFloat()
                        val screenY = (h / 2f + (ptY - cTileY) * tilePx).toFloat()
                        if (hypot(tapOffset.x - screenX, tapOffset.y - screenY) <= 36f * density) {
                            onClusterTap(cluster)
                            break
                        }
                    }
                }
            }
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
        val rev = tileRevision
        val w = size.width
        val h = size.height
        val tilePx = (256f * density).roundToInt().coerceAtLeast(128)

        // 1. Tactical Radar Grid Background
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

        // 2. Render 256x256 Google Maps Tiles for selected style (s=Satellite, y=Hybrid, p=Terrain, m=Normal)
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
                        alpha = if (mapStyle.isAerialStyle) 0.96f else 0.90f
                    )
                } else if (rev >= 0 && TacticalTileLoader.inFlight.add(cacheKey)) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val candidateUrls = when (mapStyle) {
                                TacticalMapStyle.SATELLITE -> listOf(
                                    "https://mt1.google.com/vt/lyrs=s&x=$wrappedX&y=$ty&z=$zoom",
                                    "https://mt0.google.com/vt/lyrs=s&x=$wrappedX&y=$ty&z=$zoom",
                                    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$zoom/$ty/$wrappedX"
                                )
                                TacticalMapStyle.HYBRID -> listOf(
                                    "https://mt1.google.com/vt/lyrs=y&x=$wrappedX&y=$ty&z=$zoom",
                                    "https://mt0.google.com/vt/lyrs=y&x=$wrappedX&y=$ty&z=$zoom",
                                    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$zoom/$ty/$wrappedX"
                                )
                                TacticalMapStyle.TERRAIN -> listOf(
                                    "https://mt1.google.com/vt/lyrs=p&x=$wrappedX&y=$ty&z=$zoom",
                                    "https://mt0.google.com/vt/lyrs=p&x=$wrappedX&y=$ty&z=$zoom",
                                    "https://tile.openstreetmap.org/$zoom/$wrappedX/$ty.png"
                                )
                                TacticalMapStyle.NORMAL -> listOf(
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

        if (!mapStyle.isAerialStyle) {
            drawRect(color = Color(0xFF040914).copy(alpha = 0.18f))
        }

        fun latLonToOffset(latVal: Double, lonVal: Double): Offset {
            val ptX = lonToTileX(lonVal, zoom)
            val ptY = latToTileY(latVal, zoom)
            return Offset(
                x = (w / 2f + (ptX - cTileX) * tilePx).toFloat(),
                y = (h / 2f + (ptY - cTileY) * tilePx).toFloat()
            )
        }

        // 3. Route Polyline & Origin/Destination Pins
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

        // 4. Draw Target Sector Radar Pin
        val targetPt = latLonToOffset(targetLat, targetLon)
        drawCircle(
            color = accentColor.copy(alpha = 0.22f),
            radius = 20.dp.toPx(),
            center = targetPt
        )
        drawCircle(
            color = accentColor,
            radius = 12.dp.toPx(),
            center = targetPt,
            style = Stroke(width = 1.5.dp.toPx())
        )
        drawCircle(
            color = accentColor,
            radius = 4.5.dp.toPx(),
            center = targetPt
        )
        drawCircle(
            color = Color.White,
            radius = 2.dp.toPx(),
            center = targetPt
        )

        // 5. Draw Clustered Search Result Markers
        clusters.forEach { cluster ->
            val clusterPt = latLonToOffset(cluster.centerLat, cluster.centerLon)
            if (cluster.isCluster) {
                drawCircle(
                    color = accentColor.copy(alpha = 0.32f),
                    radius = 22.dp.toPx(),
                    center = clusterPt
                )
                drawCircle(
                    color = Color(0xFF070E1A),
                    radius = 15.dp.toPx(),
                    center = clusterPt
                )
                drawCircle(
                    color = accentColor,
                    radius = 15.dp.toPx(),
                    center = clusterPt,
                    style = Stroke(width = 2.dp.toPx())
                )
                drawContext.canvas.nativeCanvas.drawText(
                    cluster.count.toString(),
                    clusterPt.x,
                    clusterPt.y + 8f,
                    textPaint
                )
            } else {
                val place = cluster.primaryPlace
                drawCircle(
                    color = Color(0xFFFFB300).copy(alpha = 0.28f),
                    radius = 14.dp.toPx(),
                    center = clusterPt
                )
                drawCircle(
                    color = Color(0xFFFFB300),
                    radius = 6.dp.toPx(),
                    center = clusterPt
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.5.dp.toPx(),
                    center = clusterPt
                )
                drawContext.canvas.nativeCanvas.drawText(
                    place.name.take(14),
                    clusterPt.x,
                    clusterPt.y - 14.dp.toPx(),
                    labelPaint
                )
            }
        }

        // 6. Draw Custom User Real-Time GPS Marker Beacon
        if (userLocation != null) {
            val userPt = latLonToOffset(userLocation.latitude, userLocation.longitude)
            drawCircle(
                color = SuccessEmerald.copy(alpha = 0.26f),
                radius = 22.dp.toPx(),
                center = userPt
            )
            drawCircle(
                color = accentColor,
                radius = 14.dp.toPx(),
                center = userPt,
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = SuccessEmerald,
                radius = 7.dp.toPx(),
                center = userPt
            )
            drawCircle(
                color = Color.White,
                radius = 3.dp.toPx(),
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
 * Features:
 * 1. Marker clustering for map search results to prevent UI clutter.
 * 2. Real-time GPS tracking via LocationRepository (FusedLocationProviderClient + LocationRequest)
 *    with a custom user GPS marker and automatic camera position updates.
 * 3. Toggle UI component to switch between Satellite, Hybrid, Terrain, and Normal map styles.
 * 4. Custom Zoom-In and Zoom-Out Floating Action Buttons (FABs) for smooth navigation.
 */
@Composable
fun TacticalMapView(
    accentColor: Color,
    targetCity: String = "Global Defense Grid",
    latitude: Double = 28.6139,
    longitude: Double = 77.2090,
    routeCalculation: RouteCalculation? = null,
    placeLocation: PlaceLocation? = null,
    searchResults: List<PlaceLocation> = emptyList(),
    liveUserGpsLocation: LatLng? = null,
    onUserLocationUpdated: (LatLng) -> Unit = {},
    onClearRoute: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val apiKeyStatus = remember(context) { verifyMapsApiKeyConsumption(context) }

    var currentMapStyle by remember { mutableStateOf(TacticalMapStyle.HYBRID) }
    var showStepsDrawer by remember { mutableStateOf(false) }
    var showRationaleDialog by remember { mutableStateOf(false) }
    var showAuthDiagnosticDialog by remember { mutableStateOf(false) }
    var isComposeTileEngineActive by remember { mutableStateOf(true) }
    var isMapLoaded by remember { mutableStateOf(true) }
    var isAutoFollowGps by remember { mutableStateOf(true) }
    var userLocationLatLng by remember(liveUserGpsLocation) { mutableStateOf(liveUserGpsLocation) }

    val locationHelper = rememberLocationPermissionHelper(
        onLocationAcquired = { latLng ->
            userLocationLatLng = latLng
            onUserLocationUpdated(latLng)
        }
    )

    val activeLat = placeLocation?.latitude ?: latitude
    val activeLon = placeLocation?.longitude ?: longitude
    val targetLatLng = remember(activeLat, activeLon) { LatLng(activeLat, activeLon) }

    var slippyCenterLat by remember(activeLat) { mutableStateOf(activeLat) }
    var slippyCenterLon by remember(activeLon) { mutableStateOf(activeLon) }
    var slippyZoom by remember { mutableIntStateOf(14) }
    var nativeZoomTracker by remember { mutableFloatStateOf(14f) }

    // Compute dynamic marker clusters based on active zoom level
    val activeZoomFloat = if (isComposeTileEngineActive) slippyZoom.toFloat() else nativeZoomTracker
    val activeClusters = remember(searchResults, activeZoomFloat) {
        clusterSearchLocations(searchResults, activeZoomFloat)
    }

    // Automatically update camera position when real-time GPS LocationRequest emits new coordinates
    LaunchedEffect(liveUserGpsLocation, isAutoFollowGps) {
        if (liveUserGpsLocation != null) {
            userLocationLatLng = liveUserGpsLocation
            if (isAutoFollowGps && routeCalculation == null && placeLocation == null && searchResults.isEmpty()) {
                slippyCenterLat = liveUserGpsLocation.latitude
                slippyCenterLon = liveUserGpsLocation.longitude
            }
        }
    }

    LaunchedEffect(activeLat, activeLon) {
        slippyCenterLat = activeLat
        slippyCenterLon = activeLon
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
                                "2. Map Styles: SATELLITE, HYBRID, TERRAIN, NORMAL via Google Maps Compose.\n" +
                                "3. Clustering & Fused GPS: Active (${activeClusters.size} clusters).",
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
                                text = if (isComposeTileEngineActive) "MAPS COMPOSE" else "DIRECT TILES",
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
            .height(380.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DarkHologramSurface)
            .border(1.5.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .testTag("tactical_map_view")
    ) {
        // --- 1. MAP ENGINE (SLIPPY TILE ENGINE OR GOOGLE MAPS COMPOSE) ---
        if (isComposeTileEngineActive) {
            NativeComposeSlippyMapView(
                centerLat = slippyCenterLat,
                centerLon = slippyCenterLon,
                zoom = slippyZoom,
                targetLat = activeLat,
                targetLon = activeLon,
                userLocation = userLocationLatLng,
                routeCalculation = routeCalculation,
                clusters = activeClusters,
                mapStyle = currentMapStyle,
                accentColor = accentColor,
                onPanDelta = { newLat, newLon ->
                    isAutoFollowGps = false
                    slippyCenterLat = newLat
                    slippyCenterLon = newLon
                },
                onClusterTap = { cluster ->
                    slippyCenterLat = cluster.centerLat
                    slippyCenterLon = cluster.centerLon
                    if (cluster.isCluster) {
                        slippyZoom = (slippyZoom + 2).coerceIn(2, 19)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            ComposeGoogleMapRenderer(
                targetLatLng = LatLng(slippyCenterLat, slippyCenterLon),
                targetTitle = placeLocation?.name ?: targetCity,
                targetSnippet = "${String.format(Locale.US, "%.4f", activeLat)}°N, ${String.format(Locale.US, "%.4f", activeLon)}°E",
                userLocation = userLocationLatLng,
                mapStyle = currentMapStyle,
                isMyLocationEnabled = locationHelper.hasAnyPermission,
                routeCalculation = routeCalculation,
                clusters = activeClusters,
                zoomLevel = nativeZoomTracker,
                accentColor = accentColor,
                onClusterClick = { cluster ->
                    slippyCenterLat = cluster.centerLat
                    slippyCenterLon = cluster.centerLon
                    if (cluster.isCluster) {
                        nativeZoomTracker = (nativeZoomTracker + 2f).coerceIn(2f, 20f)
                    }
                },
                onMapLoaded = {
                    isMapLoaded = true
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // --- 1B. SCI-FI TACTICAL SCAN OVERLAY ---
        SciFiTacticalScanOverlay(
            accentColor = accentColor,
            isSatelliteMode = currentMapStyle.isAerialStyle,
            modifier = Modifier
                .fillMaxSize()
                .testTag("sci_fi_map_scanning_overlay")
        )

        // --- 2. COMPACT FLOATING COORDINATE & CLUSTER BADGE (Top-Start) ---
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
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
                            .background(if (userLocationLatLng != null || locationHelper.hasAnyPermission) SuccessEmerald else accentColor)
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
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }
            }

            // Active Marker Clustering Badge when multiple search results are displayed
            if (searchResults.size > 1) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.78f))
                        .border(1.dp, SuccessEmerald.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .clickable {
                            val firstCluster = activeClusters.firstOrNull { it.isCluster } ?: activeClusters.firstOrNull()
                            if (firstCluster != null) {
                                slippyCenterLat = firstCluster.centerLat
                                slippyCenterLon = firstCluster.centerLon
                                slippyZoom = (slippyZoom + 2).coerceIn(2, 19)
                                nativeZoomTracker = (nativeZoomTracker + 2f).coerceIn(2f, 20f)
                            }
                        }
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                        .testTag("map_cluster_status_badge")
                ) {
                    Text(
                        text = "CLUSTERED: ${searchResults.size} POIs ➔ ${activeClusters.size} CLUSTERS",
                        color = SuccessEmerald,
                        fontSize = 7.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // --- 2B. TOP-RIGHT ROUTE / EXTERNAL MAPS CONTROLS ---
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 8.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (routeCalculation != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (showStepsDrawer) accentColor.copy(alpha = 0.28f)
                            else Color.Black.copy(alpha = 0.72f)
                        )
                        .border(1.dp, accentColor, RoundedCornerShape(6.dp))
                        .clickable { showStepsDrawer = !showStepsDrawer }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Route,
                            contentDescription = "Steps",
                            tint = accentColor,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "STEPS",
                            color = accentColor,
                            fontSize = 7.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(SuccessEmerald.copy(alpha = 0.22f))
                        .border(1.dp, SuccessEmerald, RoundedCornerShape(6.dp))
                        .clickable { launchNativeGps(context, routeCalculation) }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = "Start Navigation",
                            tint = SuccessEmerald,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "GO",
                            color = SuccessEmerald,
                            fontSize = 7.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AlertCrimson.copy(alpha = 0.22f))
                        .border(1.dp, AlertCrimson, RoundedCornerShape(6.dp))
                        .clickable {
                            showStepsDrawer = false
                            onClearRoute()
                        }
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
                .padding(start = 8.dp, top = 40.dp, bottom = 36.dp)
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

        // --- 4. CUSTOM ZOOM-IN / ZOOM-OUT FLOATING ACTION BUTTONS & TACTICAL CONTROLS (Right Side) ---
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Custom Zoom-In Floating Action Button (+)
            SmallFloatingActionButton(
                onClick = {
                    if (isComposeTileEngineActive) {
                        slippyZoom = (slippyZoom + 1).coerceIn(2, 19)
                    } else {
                        nativeZoomTracker = (nativeZoomTracker + 1f).coerceIn(2f, 20f)
                    }
                },
                containerColor = Color(0xDD070B14),
                contentColor = accentColor,
                shape = RoundedCornerShape(10.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .size(40.dp)
                    .border(1.dp, accentColor.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                    .testTag("btn_map_zoom_in")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Zoom In",
                    modifier = Modifier.size(18.dp)
                )
            }

            // Custom Zoom-Out Floating Action Button (-)
            SmallFloatingActionButton(
                onClick = {
                    if (isComposeTileEngineActive) {
                        slippyZoom = (slippyZoom - 1).coerceIn(2, 19)
                    } else {
                        nativeZoomTracker = (nativeZoomTracker - 1f).coerceIn(2f, 20f)
                    }
                },
                containerColor = Color(0xDD070B14),
                contentColor = accentColor,
                shape = RoundedCornerShape(10.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .size(40.dp)
                    .border(1.dp, accentColor.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                    .testTag("btn_map_zoom_out")
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Zoom Out",
                    modifier = Modifier.size(18.dp)
                )
            }

            // Center on User Real-Time GPS Location & Enable Auto-Follow
            TacticalMapButton(
                icon = if (locationHelper.hasAnyPermission || userLocationLatLng != null) Icons.Default.MyLocation else Icons.Default.GpsNotFixed,
                accentColor = if (locationHelper.hasFinePermission || userLocationLatLng != null) SuccessEmerald else if (locationHelper.hasCoarsePermission) accentColor else AlertCrimson,
                contentDescription = "Center on My Location",
                testTag = "btn_center_user_location",
                onClick = {
                    isAutoFollowGps = true
                    if (!locationHelper.hasAnyPermission) {
                        if (locationHelper.shouldShowRationale) {
                            showRationaleDialog = true
                        } else {
                            locationHelper.requestPermission()
                        }
                    } else {
                        locationHelper.centerOnCurrentLocation { userLoc ->
                            userLocationLatLng = userLoc
                            onUserLocationUpdated(userLoc)
                            slippyCenterLat = userLoc.latitude
                            slippyCenterLon = userLoc.longitude
                            slippyZoom = 16
                            nativeZoomTracker = 16f
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
                    isAutoFollowGps = false
                    slippyCenterLat = activeLat
                    slippyCenterLon = activeLon
                    slippyZoom = 14
                    nativeZoomTracker = 14f
                }
            )

            // Cycle Map Style (Satellite / Hybrid / Terrain / Normal)
            TacticalMapButton(
                icon = Icons.Default.Layers,
                accentColor = if (currentMapStyle.isAerialStyle) SuccessEmerald else accentColor,
                contentDescription = "Cycle Map Style",
                testTag = "btn_map_toggle_layer",
                onClick = {
                    currentMapStyle = currentMapStyle.nextStyle()
                }
            )

            // Toggle Direct Tile Renderer vs Google Maps Compose
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

        // --- 5. MAP STYLE TOGGLE UI COMPONENT (SATELLITE | HYBRID | TERRAIN | NORMAL) ---
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.80f))
                .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                .padding(horizontal = 4.dp, vertical = 3.dp)
                .testTag("map_style_toggle_bar"),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TacticalMapStyle.entries.forEach { styleOption ->
                val isSelected = currentMapStyle == styleOption
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.28f) else Color.Transparent)
                        .border(
                            width = if (isSelected) 1.dp else 0.dp,
                            color = if (isSelected) accentColor else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { currentMapStyle = styleOption }
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                        .testTag("map_style_${styleOption.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = styleOption.label,
                        color = if (isSelected) accentColor else HologramTextDim,
                        fontSize = 7.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // --- 6. BOTTOM TELEMETRY BAR ---
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 5.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .border(0.8.dp, accentColor.copy(alpha = 0.30f), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (routeCalculation != null) {
                    "ROUTE: ACTIVE • ETA ${routeCalculation.durationText} • ${currentMapStyle.label}"
                } else {
                    "STYLE: ${currentMapStyle.label} // ${String.format(Locale.US, "%.4f", slippyCenterLat)}°N, ${String.format(Locale.US, "%.4f", slippyCenterLon)}°E • Z$slippyZoom"
                },
                color = HologramTextDim,
                fontSize = 7.sp,
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
