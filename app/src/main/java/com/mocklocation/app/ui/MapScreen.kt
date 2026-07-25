package com.mocklocation.app.ui

import android.graphics.Color as AwtColor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mocklocation.app.model.LatLng
import com.mocklocation.app.model.MarkerMode
import com.mocklocation.app.model.Route
import com.mocklocation.app.model.SimulationState
import com.mocklocation.app.model.SimulationStatus
import com.mocklocation.app.viewmodel.MapViewModel
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker as OsmMarker
import org.osmdroid.views.overlay.Polyline

// Color constants
private val StartColor = Color(0xFF34A853)
private val EndColor = Color(0xFFEA4335)
private val ActiveColor = Color(0xFF1A73E8)
private val InactiveColor = Color(0xFF9AA0A6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(viewModel: MapViewModel) {
    val startPoint by viewModel.startPoint.collectAsStateWithLifecycle()
    val endPoint by viewModel.endPoint.collectAsStateWithLifecycle()
    val markerMode by viewModel.markerMode.collectAsStateWithLifecycle()
    val route by viewModel.route.collectAsStateWithLifecycle()
    val isCalculating by viewModel.isCalculatingRoute.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val simulation by viewModel.simulation.collectAsStateWithLifecycle()
    val mapCenter by viewModel.mapCenter.collectAsStateWithLifecycle()

    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MockLocation") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            BottomControls(
                markerMode = markerMode,
                route = route,
                simulation = simulation,
                isCalculating = isCalculating,
                hasStart = startPoint != null,
                hasEnd = endPoint != null,
                speedOptions = viewModel.speedOptions,
                onPlay = viewModel::playSimulation,
                onPause = viewModel::pauseSimulation,
                onStop = viewModel::stopSimulation,
                onSpeedChange = viewModel::setSpeedMultiplier,
                onClearAll = viewModel::clearAll,
                onCalculate = viewModel::calculateRoute,
                onCycleMarkerMode = viewModel::cycleMarkerMode
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── OSM Map ──────────────────────────────────────────
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        setBuiltInZoomControls(false)
                        isTilesScaledToDpi = true

                        controller.setZoom(12.0)
                        controller.setCenter(GeoPoint(mapCenter.latitude, mapCenter.longitude))

                        val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
                            override fun longPressHelper(p: GeoPoint?): Boolean {
                                p?.let {
                                    viewModel.onMapLongPress(LatLng(it.latitude, it.longitude))
                                }
                                return true
                            }
                        })
                        overlays.add(0, eventsOverlay)

                        minZoomLevel = 3.0
                        maxZoomLevel = 19.0
                    }
                },
                update = { mapView ->
                    // Remove dynamic overlays
                    val toRemove = mutableListOf<org.osmdroid.views.overlay.Overlay>()
                    for (overlay in mapView.overlays) {
                        if (overlay is OsmMarker || overlay is Polyline) {
                            toRemove.add(overlay)
                        }
                    }
                    mapView.overlays.removeAll(toRemove)

                    // Start marker
                    startPoint?.let { sp ->
                        val marker = OsmMarker(mapView).apply {
                            position = GeoPoint(sp.latitude, sp.longitude)
                            setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_BOTTOM)
                            title = "Start"
                            icon = markerIcon(context, StartColor, "S")
                        }
                        mapView.overlays.add(marker)
                    }

                    // End marker
                    endPoint?.let { ep ->
                        val marker = OsmMarker(mapView).apply {
                            position = GeoPoint(ep.latitude, ep.longitude)
                            setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_BOTTOM)
                            title = "End"
                            icon = markerIcon(context, EndColor, "E")
                        }
                        mapView.overlays.add(marker)
                    }

                    // Current simulation position
                    simulation.currentPosition?.let { pos ->
                        val color = if (simulation.status == SimulationStatus.PLAYING)
                            ActiveColor else InactiveColor
                        val marker = OsmMarker(mapView).apply {
                            position = GeoPoint(pos.latitude, pos.longitude)
                            setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_CENTER)
                            title = "Current"
                            icon = markerIcon(context, color, "●", sizeDp = 16)
                        }
                        mapView.overlays.add(marker)
                    }

                    // Route polyline
                    route?.points?.let { pts ->
                        if (pts.size >= 2) {
                            val line = Polyline().apply {
                                setPoints(pts.map { GeoPoint(it.latitude, it.longitude) })
                                outlinePaint.apply {
                                    color = AwtColor.rgb(26, 115, 232)
                                    strokeWidth = 6f
                                    isAntiAlias = true
                                }
                            }
                            mapView.overlays.add(line)
                        }
                    }

                    mapView.invalidate()
                }
            )

            // ── Error banner ─────────────────────
            if (errorMessage != null) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp, start = 24.dp, end = 24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = errorMessage ?: "",
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .clickable { viewModel.clearError() },
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp
                    )
                }
            }

            // ── Simulation info overlay ──────────────────────────
            if (simulation.status == SimulationStatus.PLAYING ||
                simulation.status == SimulationStatus.PAUSED) {
                SimulationInfoOverlay(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 12.dp),
                    simulation = simulation
                )
            }
        }
    }
}

// ── Marker icon helper (non-Composable) ─────────────────────────

private fun markerIcon(
    context: android.content.Context,
    color: Color,
    label: String,
    sizeDp: Int = 36
): android.graphics.drawable.Drawable {
    val density = context.resources.displayMetrics.density
    val sizePx = (sizeDp * density).toInt()
    val bitmap = android.graphics.Bitmap.createBitmap(
        sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        style = android.graphics.Paint.Style.FILL
    }
    val radius = sizePx / 2f
    canvas.drawCircle(radius, radius, radius - 2f, paint)

    // White border
    paint.color = AwtColor.WHITE
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 2f
    canvas.drawCircle(radius, radius, radius - 2f, paint)

    // Label text
    paint.color = AwtColor.WHITE
    paint.style = android.graphics.Paint.Style.FILL
    paint.textSize = sizePx * 0.5f
    paint.textAlign = android.graphics.Paint.Align.CENTER
    val yOff = (paint.descent() + paint.ascent()) / 2f
    canvas.drawText(label, radius, radius - yOff, paint)

    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}

// ── Bottom Controls ────────────────────────────────────────────

@Composable
private fun BottomControls(
    markerMode: MarkerMode,
    route: Route?,
    simulation: SimulationState,
    isCalculating: Boolean,
    hasStart: Boolean,
    hasEnd: Boolean,
    speedOptions: List<Float>,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onClearAll: () -> Unit,
    onCalculate: () -> Unit,
    onCycleMarkerMode: () -> Unit
) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            // Row 1: Mode indicator + route info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val modeLabel = when (markerMode) {
                    MarkerMode.START -> "Set START"
                    MarkerMode.END -> "Set END"
                }
                val modeColor = when (markerMode) {
                    MarkerMode.START -> MaterialTheme.colorScheme.secondary
                    MarkerMode.END -> MaterialTheme.colorScheme.error
                }
                SuggestionChip(
                    onClick = onCycleMarkerMode,
                    label = { Text(modeLabel, fontSize = 12.sp) },
                    icon = {
                        Text(
                            if (markerMode == MarkerMode.START) "📍" else "🏁",
                            fontSize = 12.sp
                        )
                    },
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(Modifier.width(12.dp))

                route?.let {
                    Text(
                        text = "${it.totalDistanceFormatted} · ${it.durationFormatted}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Row 2: Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasStart && hasEnd && route == null) {
                    Button(
                        onClick = onCalculate,
                        enabled = !isCalculating,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isCalculating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Calculate Route")
                    }
                }

                OutlinedButton(
                    onClick = onClearAll,
                    modifier = Modifier.weight(if (route != null) 0.5f else 1f)
                ) {
                    Text("Clear")
                }

                if (route != null) {
                    Spacer(Modifier.width(4.dp))

                    when (simulation.status) {
                        SimulationStatus.IDLE -> {
                            FilledTonalButton(onClick = onPlay) {
                                Text("▶ Play")
                            }
                        }
                        SimulationStatus.PLAYING -> {
                            FilledTonalButton(onClick = onPause) {
                                Text("⏸ Pause")
                            }
                            Spacer(Modifier.width(4.dp))
                            OutlinedButton(onClick = onStop) {
                                Text("⏹ Stop")
                            }
                        }
                        SimulationStatus.PAUSED -> {
                            FilledTonalButton(onClick = onPlay) {
                                Text("▶ Resume")
                            }
                            Spacer(Modifier.width(4.dp))
                            OutlinedButton(onClick = onStop) {
                                Text("⏹ Stop")
                            }
                        }
                    }
                }
            }

            // Row 3: Speed selector
            if (route != null) {
                Spacer(Modifier.height(6.dp))
                SpeedSelector(
                    currentSpeed = simulation.speedMultiplier,
                    options = speedOptions,
                    onSelect = onSpeedChange
                )
            }
        }
    }
}

// ── Speed Selector ─────────────────────────────────────────────

@Composable
private fun SpeedSelector(
    currentSpeed: Float,
    options: List<Float>,
    onSelect: (Float) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Speed:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(options) { speed ->
                val selected = currentSpeed == speed
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(speed) },
                    label = {
                        Text(
                            text = "${speed.toInt()}×",
                            fontSize = 12.sp
                        )
                    },
                    leadingIcon = if (selected) {
                        { Text("✓", fontSize = 11.sp) }
                    } else null
                )
            }
        }
    }
}

// ── Simulation Info Overlay ────────────────────────────────────

@Composable
private fun SimulationInfoOverlay(
    modifier: Modifier = Modifier,
    simulation: SimulationState
) {
    Card(
        modifier = modifier.widthIn(min = 140.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val statusText = when (simulation.status) {
                SimulationStatus.PLAYING -> "▶ Running"
                SimulationStatus.PAUSED -> "⏸ Paused"
                else -> ""
            }
            Text(statusText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${(simulation.overallProgress * 100).toInt()}%",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${simulation.simulatedSpeedKmh.toInt()} km/h",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { simulation.overallProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Seg ${simulation.segmentIndex + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
