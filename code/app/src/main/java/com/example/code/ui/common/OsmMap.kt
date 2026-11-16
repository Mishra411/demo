/*
 * OsmMap.kt
 * OpenStreetMap view used to show a campsite's location (geolocation
 * feature) and for the map view and reservation map WOW features. Uses the
 * osmdroid library, which needs no API key. Also asks for the device's
 * location for the "near me" radius search.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.ui.common

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.MaterialTheme
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.example.code.model.MapPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.io.File

/**
 * Map with one pin per point.
 *
 * @param points pins to show.
 * @param center centre of the radius circle (the device's location), or null for none.
 * @param radiusKm radius of the circle drawn around [center], or null for none.
 * @param onPointClick called with the pin the user tapped.
 */
@Composable
fun OsmMap(
    points: List<MapPoint>,
    modifier: Modifier = Modifier,
    center: Pair<Double, Double>? = null,
    radiusKm: Double? = null,
    onPointClick: ((MapPoint) -> Unit)? = null,
) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    val map = remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.filesDir, "osmdroid")
            osmdroidTileCache = File(context.cacheDir, "osmdroid-tiles")
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            // Let the map pan inside a scrolling screen.
            setOnTouchListener { view, _ ->
                view.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
    }
    DisposableEffect(map) {
        map.onResume()
        onDispose {
            map.onPause()
            map.onDetach()
        }
    }
    AndroidView(
        factory = { map },
        modifier = modifier,
        update = { view ->
            view.overlays.clear()
            if (center != null && radiusKm != null) {
                view.overlays.add(
                    Polygon(view).apply {
                        this.points = Polygon.pointsAsCircle(GeoPoint(center.first, center.second), radiusKm * 1000)
                        fillPaint.color = (accent and 0x00FFFFFF) or 0x22000000
                        outlinePaint.color = accent
                        outlinePaint.strokeWidth = 3f
                        setOnClickListener { _, _, _ -> false }
                    },
                )
            }
            points.forEach { point ->
                view.overlays.add(
                    Marker(view).apply {
                        position = GeoPoint(point.latitude, point.longitude)
                        title = point.title
                        snippet = point.subtitle
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        alpha = if (point.highlight || points.none { it.highlight }) 1f else 0.55f
                        setOnMarkerClickListener { marker, _ ->
                            marker.showInfoWindow()
                            onPointClick?.invoke(point)
                            true
                        }
                    },
                )
            }
            val shown = points.map { GeoPoint(it.latitude, it.longitude) } +
                listOfNotNull(center?.let { GeoPoint(it.first, it.second) })
            view.post {
                when {
                    shown.isEmpty() -> {
                        // Alberta, where the campsites are.
                        view.controller.setZoom(5.0)
                        view.controller.setCenter(GeoPoint(53.5, -115.0))
                    }
                    shown.size == 1 -> {
                        view.controller.setZoom(11.0)
                        view.controller.setCenter(shown.first())
                    }
                    else -> view.zoomToBoundingBox(BoundingBox.fromGeoPoints(shown).increaseByScale(1.4f), false)
                }
                view.invalidate()
            }
        },
    )
}

/**
 * Returns a function that asks for location permission if needed and then
 * reports the device's location, or null if it is denied or unavailable.
 *
 * @param onResult called with (latitude, longitude), or null.
 */
@Composable
fun rememberLocationRequester(onResult: (Pair<Double, Double>?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) readLocation(context, onResult) else onResult(null)
    }
    return {
        val allowed = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (allowed) {
            readLocation(context, onResult)
        } else {
            permission.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        }
    }
}

/** Reads the last known location, or asks the device for a fresh one. */
@SuppressLint("MissingPermission")
private fun readLocation(context: Context, onResult: (Pair<Double, Double>?) -> Unit) {
    try {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = manager.getProviders(true)
        val last = providers.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }
        if (last != null) {
            onResult(last.latitude to last.longitude)
            return
        }
        val provider = providers.firstOrNull { it == LocationManager.NETWORK_PROVIDER }
            ?: providers.firstOrNull { it == LocationManager.GPS_PROVIDER }
            ?: providers.firstOrNull()
        if (provider == null) {
            onResult(null)
            return
        }
        LocationManagerCompat.getCurrentLocation(
            manager,
            provider,
            null as android.os.CancellationSignal?,
            ContextCompat.getMainExecutor(context),
            androidx.core.util.Consumer<Location?> { location ->
                onResult(location?.let { it.latitude to it.longitude })
            },
        )
    } catch (e: Exception) {
        onResult(null)
    }
}
