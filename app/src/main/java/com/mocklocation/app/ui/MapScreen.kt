package com.mocklocation.app.ui

import android.content.Intent
import android.graphics.Color as AwtColor
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mocklocation.app.BuildConfig
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

// ── Brand colours used across the UI ───────────────────────────
private val StartColor = Color(0xFF34A853)
private val EndColor = Color(0xFFEA4335)
private val ActiveColor = Color(0xFF1A73E8)
private val InactiveColor = Color(0xFF9AA0A6)
private val BrandDark = Color(0xFF0D2137)

private const val GITHUB_URL = "https://github.com/vincenzobpt/gps-mock-location"

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
    var showAbout by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }

    // ── osmdroid lifecycle ─────────────────────────────────────
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewRef.value?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewRef.value?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            mapViewRef.value?.onDetach()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // ── About dialog ───────────────────────────────────────────
    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GPS Mock Location") },
                actions = {
                    IconButton(onClick = { showAbout = true }) {
                        Text("\u2139\uFE0F", fontSize = 20.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandDark,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
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
                        mapViewRef.value = this
                        setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
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
                    // Remove only dynamic overlays (never the events overlay)
                    val toRemove = mutableListOf<org.osmdroid.views.overlay.Overlay>()
                    for (overlay in mapView.overlays) {
                        if (overlay is OsmMarker || overlay is Polyline) {
                            toRemove.add(overlay)
                        }
                    }
                    mapView.overlays.removeAll(toRemove)

                    // ── Start marker ──────────────────────────────
                    startPoint?.let { sp ->
                        mapView.overlays.add(
                            OsmMarker(mapView).apply {
                                position = GeoPoint(sp.latitude, sp.longitude)
                                setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_BOTTOM)
                                title = "Start"
                                icon = markerIcon(context, StartColor, "S")
                            }
                        )
                    }

                    // ── End marker ────────────────────────────────
                    endPoint?.let { ep ->
                        mapView.overlays.add(
                            OsmMarker(mapView).apply {
                                position = GeoPoint(ep.latitude, ep.longitude)
                                setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_BOTTOM)
                                title = "End"
                                icon = markerIcon(context, EndColor, "E")
                            }
                        )
                    }

                    // ── Simulation cursor ─────────────────────────
                    simulation.currentPosition?.let { pos ->
                        val color = if (simulation.status == SimulationStatus.PLAYING)
                            ActiveColor else InactiveColor
                        mapView.overlays.add(
                            OsmMarker(mapView).apply {
                                position = GeoPoint(pos.latitude, pos.longitude)
                                setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_CENTER)
                                title = "Current"
                                icon = circleIcon(context, color, 14)
                            }
                        )
                    }

                    // ── Route polyline ────────────────────────────
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

            // ── Error banner ─────────────────────────────────────
            if (errorMessage != null) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp, start = 24.dp, end = 24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Text(
                        text = errorMessage ?: "",
                        modifier = Modifier
                            .padding(horizontal = 18.dp, vertical = 12.dp)
                            .clickable { viewModel.clearError() },
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
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

// ═══════════════════════════════════════════════════════════════
//  ABOUT DIALOG
// ═══════════════════════════════════════════════════════════════

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // App icon placeholder
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(BrandDark),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "GPS",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "GPS Mock Location",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Simulate GPS movement along any route.\n" +
                            "Set start and end points on the map, then play to watch " +
                            "your device follow the path with configurable speed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(16.dp))

                // GitHub link
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL))
                                context.startActivity(intent)
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "\uD83D\uDEE0\uFE0F",
                            fontSize = 18.sp
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "GitHub",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "vincenzobpt/gps-mock-location",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // License row
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "\u00A9\uFE0F",
                            fontSize = 18.sp
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "License",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "MIT \u2014 VINCENZO BUONOMANO",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                TextButton(onClick = onDismiss) {
                    Text("Close", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  MARKER ICONS
// ═══════════════════════════════════════════════════════════════

/** Drop-pin style marker with a coloured circle and white label. */
private fun markerIcon(
    context: android.content.Context,
    color: Color,
    label: String,
    sizeDp: Int = 38
): android.graphics.drawable.Drawable {
    val density = context.resources.displayMetrics.density
    val sizePx = (sizeDp * density).toInt()
    val bitmap = android.graphics.Bitmap.createBitmap(
        sizePx, sizePx + (sizePx / 4),
        android.graphics.Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    // Drop pin body
    val cx = sizePx / 2f
    val cy = sizePx / 2f
    val r = sizePx / 2f - 2f

    paint.color = color.toArgb()
    paint.style = android.graphics.Paint.Style.FILL
    canvas.drawCircle(cx, cy, r, paint)

    // White border
    paint.color = AwtColor.WHITE
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 2.5f
    canvas.drawCircle(cx, cy, r, paint)

    // Label
    paint.color = AwtColor.WHITE
    paint.style = android.graphics.Paint.Style.FILL
    paint.textSize = sizePx * 0.48f
    paint.textAlign = android.graphics.Paint.Align.CENTER
    val yOff = (paint.descent() + paint.ascent()) / 2f
    canvas.drawText(label, cx, cy - yOff, paint)

    // Small pointed tail at bottom
    paint.color = color.toArgb()
    paint.style = android.graphics.Paint.Style.FILL
    paint.strokeWidth = 1f
    val tailPath = android.graphics.Path().apply {
        moveTo(cx - r * 0.3f, cy + r * 0.7f)
        lineTo(cx, cy + r * 1.3f)
        lineTo(cx + r * 0.3f, cy + r * 0.7f)
        close()
    }
    canvas.drawPath(tailPath, paint)

    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}

/** Simple filled circle (used for the simulation cursor). */
private fun circleIcon(
    context: android.content.Context,
    color: Color,
    radiusDp: Int
): android.graphics.drawable.Drawable {
    val density = context.resources.displayMetrics.density
    val rPx = (radiusDp * density).toInt()
    val diameter = rPx * 2 + 8
    val bitmap = android.graphics.Bitmap.createBitmap(
        diameter, diameter, android.graphics.Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    // Outer glow (semi-transparent)
    paint.color = color.copy(alpha = 0.25f).toArgb()
    paint.style = android.graphics.Paint.Style.FILL
    canvas.drawCircle(diameter / 2f, diameter / 2f, rPx + 4f, paint)

    // Solid dot
    paint.color = color.toArgb()
    canvas.drawCircle(diameter / 2f, diameter / 2f, rPx.toFloat(), paint)

    // White highlight
    paint.color = AwtColor.WHITE
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 2f
    canvas.drawCircle(diameter / 2f, diameter / 2f, rPx.toFloat(), paint)

    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}

// ═══════════════════════════════════════════════════════════════
//  BOTTOM CONTROLS
// ═══════════════════════════════════════════════════════════════

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
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 10.dp)) {

            // ── Row 1: mode chip + route summary ─────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Animated chip that shows the current mode
                val modeLabel = when (markerMode) {
                    MarkerMode.START -> "Set START"
                    MarkerMode.END -> "Set END"
                }
                val modeIcon = when (markerMode) {
                    MarkerMode.START -> "\uD83D\uDCCD"
                    MarkerMode.END -> "\uD83C\uDFC1"
                }
                val chipColor = when (markerMode) {
                    MarkerMode.START -> MaterialTheme.colorScheme.secondary
                    MarkerMode.END -> MaterialTheme.colorScheme.error
                }

                SuggestionChip(
                    onClick = onCycleMarkerMode,
                    label = { Text(modeLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                    icon = { Text(modeIcon, fontSize = 13.sp) },
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(Modifier.width(12.dp))

                // Route summary badge
                route?.let {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${it.totalDistanceFormatted} \u00B7 ${it.durationFormatted}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Row 2: action buttons ────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Calculate route button (only shown when both points exist but no route yet)
                if (hasStart && hasEnd && route == null) {
                    Button(
                        onClick = onCalculate,
                        enabled = !isCalculating,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isCalculating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Calculating\u2026")
                        } else {
                            Text("\uD83D\uDEEB\uFE0F Calculate Route", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Clear button — always visible
                OutlinedButton(
                    onClick = onClearAll,
                    modifier = Modifier.weight(if (route != null) 0.35f else 1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("\uD83D\uDDD1\uFE0F Clear")
                }

                // Playback buttons (only when a route is loaded)
                if (route != null) {
                    Spacer(Modifier.width(4.dp))

                    when (simulation.status) {
                        SimulationStatus.IDLE -> {
                            Button(
                                onClick = onPlay,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Text("\u25B6 Play", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        SimulationStatus.PLAYING -> {
                            Button(
                                onClick = onPause,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary
                                )
                            ) {
                                Text("\u23F8 Pause", fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.width(6.dp))
                            OutlinedButton(
                                onClick = onStop,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("\u23F9 Stop")
                            }
                        }
                        SimulationStatus.PAUSED -> {
                            Button(
                                onClick = onPlay,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Text("\u25B6 Resume", fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.width(6.dp))
                            OutlinedButton(
                                onClick = onStop,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("\u23F9 Stop")
                            }
                        }
                    }
                }
            }

            // ── Row 3: speed selector ────────────────────────────
            if (route != null) {
                Spacer(Modifier.height(10.dp))
                SpeedSelector(
                    currentSpeed = simulation.speedMultiplier,
                    options = speedOptions,
                    onSelect = onSpeedChange
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  SPEED SELECTOR
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SpeedSelector(
    currentSpeed: Float,
    options: List<Float>,
    onSelect: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = "\u26A1 Speed",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.width(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(options) { speed ->
                val selected = currentSpeed == speed
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(speed) },
                    label = {
                        Text(
                            text = "${speed.toInt()}\u00D7",
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  SIMULATION INFO OVERLAY
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SimulationInfoOverlay(
    modifier: Modifier = Modifier,
    simulation: SimulationState
) {
    Card(
        modifier = modifier.widthIn(min = 145.dp),
        colors = CardDefaults.cardColors(
            containerColor = BrandDark.copy(alpha = 0.88f)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Status line
            val (statusIcon, statusLabel) = when (simulation.status) {
                SimulationStatus.PLAYING -> "\u25B6" to "Running"
                SimulationStatus.PAUSED -> "\u23F8" to "Paused"
                else -> "" to ""
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(statusIcon, fontSize = 12.sp, color = Color.White)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = statusLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(6.dp))

            // Progress %
            Text(
                text = "${(simulation.overallProgress * 100).toInt()}%",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 28.sp
            )

            Spacer(Modifier.height(2.dp))

            // Speed
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${simulation.simulatedSpeedKmh.toInt()} km/h",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${simulation.speedMultiplier.toInt()}\u00D7",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            Spacer(Modifier.height(8.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { simulation.overallProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.2f)
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Segment ${simulation.segmentIndex + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}
