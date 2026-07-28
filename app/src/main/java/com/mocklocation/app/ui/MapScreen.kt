package com.mocklocation.app.ui

import android.content.Intent
import android.graphics.Color as AwtColor
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import kotlinx.coroutines.launch
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
import kotlin.math.roundToInt
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker as OsmMarker
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Polyline

// ── Brand colours ─────────────────────────────────────────────
private val StartColor = Color(0xFF34A853)
private val EndColor = Color(0xFFEA4335)
private val ActiveColor = Color(0xFF1A73E8)
private val InactiveColor = Color(0xFF9AA0A6)
private val BrandDark = Color(0xFF0D2137)
private val SurfaceGlass = Color(0xFF1A1A2E).copy(alpha = 0.92f)
private val CardBg = Color(0xFF1E1E32)

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
    val searchSuggestions by viewModel.searchSuggestions.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val minSpeed by viewModel.minSpeedKmh.collectAsStateWithLifecycle()
    val maxSpeed by viewModel.maxSpeedKmh.collectAsStateWithLifecycle()
    val minAltitude by viewModel.minAltitude.collectAsStateWithLifecycle()
    val maxAltitude by viewModel.maxAltitude.collectAsStateWithLifecycle()
    val useAutoAltitude by viewModel.useAutoAltitude.collectAsStateWithLifecycle()
    val mapCenter: LatLng by viewModel.mapCenter.collectAsStateWithLifecycle()
    val mapZoom: Double by viewModel.mapZoom.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    var showAbout by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // ── Re-center map when search changes mapCenter ────────────
    LaunchedEffect(mapCenter) {
        mapViewRef.value?.controller?.apply {
            animateTo(GeoPoint(mapCenter.latitude, mapCenter.longitude), 15.0, 500L)
        }
    }

    // ── Update map zoom when zoom in/out is pressed ────────────
    LaunchedEffect(mapZoom) {
        mapViewRef.value?.controller?.setZoom(mapZoom)
    }

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
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── Dialogs ────────────────────────────────────────────────
    if (showAbout) AboutDialog(onDismiss = { showAbout = false })

    // ── Settings Bottom Sheet ───────────────────────────────────
    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = sheetState,
            containerColor = CardBg,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
        ) {
            SettingsSheet(
                minSpeed = minSpeed,
                maxSpeed = maxSpeed,
                minAltitude = minAltitude,
                maxAltitude = maxAltitude,
                useAutoAltitude = useAutoAltitude,
                route = route,
                onMinSpeedChange = viewModel::setMinSpeed,
                onMaxSpeedChange = viewModel::setMaxSpeed,
                onMinAltitudeChange = viewModel::setMinAltitude,
                onMaxAltitudeChange = viewModel::setMaxAltitude,
                onUseAutoAltitudeChange = viewModel::setUseAutoAltitude
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandDark)
    ) {
        // ── Map (fills everything) ──────────────────────────────
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
                            p?.let { viewModel.onMapLongPress(LatLng(it.latitude, it.longitude)) }
                            return true
                        }
                    })
                    overlays.add(0, eventsOverlay)
                }
            },
            update = { mapView ->
                mapView.overlays.removeAll { it !is MapEventsOverlay }

                startPoint?.let { pt ->
                    mapView.overlays.add(
                        OsmMarker(mapView).apply {
                            position = GeoPoint(pt.latitude, pt.longitude)
                            setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_BOTTOM)
                            title = "Start"
                            icon = pinIcon(context, StartColor, "S")
                        }
                    )
                }

                endPoint?.let { pt ->
                    mapView.overlays.add(
                        OsmMarker(mapView).apply {
                            position = GeoPoint(pt.latitude, pt.longitude)
                            setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_BOTTOM)
                            title = "End"
                            icon = pinIcon(context, EndColor, "E")
                        }
                    )
                }

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

        // ── Top glass bar: title + about + search ───────────────
        Column(modifier = Modifier.align(Alignment.TopCenter)) {
            // Glass header
            Surface(
                color = SurfaceGlass,
                shadowElevation = 4.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Title row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GPS Mock",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Spacer(Modifier.weight(1f))

                        // Mode chip
                        val modeLabel = when (markerMode) {
                            MarkerMode.START -> "Set START"
                            MarkerMode.END -> "Set END"
                        }
                        SuggestionChip(
                            onClick = viewModel::cycleMarkerMode,
                            label = { Text(modeLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            icon = {
                                Text(
                                    if (markerMode == MarkerMode.START) "\uD83D\uDCCD" else "\uD83C\uDFC1",
                                    fontSize = 12.sp
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = null,
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = (if (markerMode == MarkerMode.START) StartColor else EndColor).copy(alpha = 0.2f),
                                labelColor = Color.White
                            )
                        )

                        Spacer(Modifier.width(8.dp))

                        IconButton(onClick = { showAbout = true }, modifier = Modifier.size(32.dp)) {
                            Text("\u2139\uFE0F", fontSize = 16.sp)
                        }
                    }

                    // Search bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.1f),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .fillMaxSize()
                            ) {
                                Text("\uD83D\uDD0D", fontSize = 14.sp)
                                Spacer(Modifier.width(8.dp))
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(
                                        color = Color.White,
                                        fontSize = 13.sp
                                    ),
                                    cursorBrush = SolidColor(Color.White.copy(alpha = 0.7f)),
                                    decorationBox = { innerTextField ->
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                "Search location…",
                                                color = Color.White.copy(alpha = 0.4f),
                                                fontSize = 13.sp
                                            )
                                        }
                                        innerTextField()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.onSearchQueryChanged("") },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Text("\u2716", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.width(6.dp))

                        FilledTonalButton(
                            onClick = { viewModel.searchExplicit(searchQuery) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = ActiveColor.copy(alpha = 0.3f)
                            )
                        ) {
                            Text("Go", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            // Search suggestions
            if (searchSuggestions.isNotEmpty()) {
                Surface(
                    color = SurfaceGlass,
                    shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                    shadowElevation = 8.dp
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchSuggestions) { suggestion ->
                            SuggestionChip(
                                onClick = { viewModel.selectSuggestion(suggestion) },
                                label = {
                                    Text(
                                        suggestion.displayName.take(40),
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = null,
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = Color.White.copy(alpha = 0.1f),
                                    labelColor = Color.White.copy(alpha = 0.8f)
                                )
                            )
                        }
                    }
                }
            }
        }

        // ── Error banner ─────────────────────────────────────
        if (errorMessage != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp, start = 24.dp, end = 24.dp)
                    .clickable { viewModel.clearError() },
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 6.dp
            ) {
                Text(
                    text = errorMessage ?: "",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        // ── Simulation info overlay ──────────────────────────
        if (simulation.status == SimulationStatus.PLAYING ||
            simulation.status == SimulationStatus.PAUSED) {
            SimulationInfoOverlay(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 110.dp, end = 12.dp),
                simulation = simulation
            )
        }

        // ── Zoom buttons ────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 80.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SmallFloatingActionButton(
                onClick = viewModel::zoomIn,
                shape = RoundedCornerShape(12.dp),
                containerColor = SurfaceGlass,
                contentColor = Color.White,
                modifier = Modifier.size(40.dp)
            ) {
                Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            SmallFloatingActionButton(
                onClick = viewModel::zoomOut,
                shape = RoundedCornerShape(12.dp),
                containerColor = SurfaceGlass,
                contentColor = Color.White,
                modifier = Modifier.size(40.dp)
            ) {
                Text("\u2212", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ── Bottom glass bar: compact controls ───────────────────
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter),
            color = SurfaceGlass,
            shadowElevation = 8.dp
        ) {
            Column {
                // Route info chip (thin, when route exists)
                val currentRoute = route
                if (currentRoute != null && !showSettings) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${currentRoute.totalDistanceFormatted} \u00B7 ${currentRoute.durationFormatted}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp
                        )
                        Spacer(Modifier.weight(1f))

                        if (simulation.status != SimulationStatus.IDLE) {
                            Text(
                                text = "${simulation.simulatedSpeedKmh.toInt()} km/h",
                                fontSize = 10.sp,
                                color = ActiveColor
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${simulation.currentAltitude.toInt()} m",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // Main control row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(40.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Action buttons
                    FilledTonalButton(
                        onClick = viewModel::calculateRoute,
                        enabled = startPoint != null && endPoint != null && !isCalculating,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(36.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.White.copy(alpha = 0.1f)
                        )
                    ) {
                        Text(
                            if (isCalculating) "\u23F3" else "\uD83C\uDFF1",
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (route != null) "ReCalc" else "Calc",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    FilledTonalButton(
                        onClick = viewModel::clearAll,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(36.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.White.copy(alpha = 0.08f)
                        )
                    ) {
                        Text("\u2716", fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
                    }

                    // Settings gear (only when route exists)
                    if (route != null) {
                        Spacer(Modifier.width(4.dp))
                        FilledTonalButton(
                            onClick = { showSettings = !showSettings },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (showSettings) ActiveColor.copy(alpha = 0.3f)
                                    else Color.White.copy(alpha = 0.08f)
                            )
                        ) {
                            Text(
                                if (showSettings) "\u2716" else "\u2699\uFE0F",
                                fontSize = 15.sp,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    // Play / Pause / Stop
                    when (simulation.status) {
                        SimulationStatus.IDLE -> {
                            Button(
                                onClick = viewModel::playSimulation,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StartColor
                                ),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Text("\u25B6  Play", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                        SimulationStatus.PLAYING -> {
                            Button(
                                onClick = viewModel::pauseSimulation,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ActiveColor
                                ),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Text("\u23F8", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Spacer(Modifier.width(6.dp))
                            OutlinedButton(
                                onClick = viewModel::stopSimulation,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(40.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White.copy(alpha = 0.7f)
                                )
                            ) {
                                Text("\u23F9", fontSize = 14.sp)
                            }
                        }
                        SimulationStatus.PAUSED -> {
                            Button(
                                onClick = viewModel::playSimulation,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StartColor
                                ),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Text("\u25B6  Resume", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Spacer(Modifier.width(6.dp))
                            OutlinedButton(
                                onClick = viewModel::stopSimulation,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(40.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White.copy(alpha = 0.7f)
                                )
                            ) {
                                Text("\u23F9", fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  SETTINGS BOTTOM SHEET  —  Speed + Altitude sliders
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SettingsSheet(
    minSpeed: Float,
    maxSpeed: Float,
    minAltitude: Float,
    maxAltitude: Float,
    useAutoAltitude: Boolean,
    route: com.mocklocation.app.model.Route?,
    onMinSpeedChange: (Float) -> Unit,
    onMaxSpeedChange: (Float) -> Unit,
    onMinAltitudeChange: (Float) -> Unit,
    onMaxAltitudeChange: (Float) -> Unit,
    onUseAutoAltitudeChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = "Settings",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = Color.White
        )

        Spacer(Modifier.height(20.dp))

        // ── Speed ─────────────────────────────────────────
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = 0.06f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("\u26A1", fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Speed",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${minSpeed.toInt()} — ${maxSpeed.toInt()} km/h",
                        color = ActiveColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(Modifier.height(8.dp))

                // DA
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("DA", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.width(24.dp))
                    Slider(
                        value = minSpeed,
                        onValueChange = onMinSpeedChange,
                        valueRange = 0f..240f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = ActiveColor,
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        )
                    )
                    Text("${minSpeed.toInt()}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
                }
                // A
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("A", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.width(24.dp))
                    Slider(
                        value = maxSpeed,
                        onValueChange = onMaxSpeedChange,
                        valueRange = 0f..240f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = ActiveColor,
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        )
                    )
                    Text("${maxSpeed.toInt()}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Altitude ──────────────────────────────────────
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = 0.06f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("\uD83C\uDFD4\uFE0F", fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Altitude",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${minAltitude.toInt()} — ${maxAltitude.toInt()} m",
                        color = ActiveColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(Modifier.height(8.dp))

                // DA
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("DA", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.width(24.dp))
                    Slider(
                        value = minAltitude,
                        onValueChange = onMinAltitudeChange,
                        valueRange = 0f..5000f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = ActiveColor,
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        )
                    )
                    Text("${minAltitude.toInt()}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
                }
                // A
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("A", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.width(24.dp))
                    Slider(
                        value = maxAltitude,
                        onValueChange = onMaxAltitudeChange,
                        valueRange = 0f..5000f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = ActiveColor,
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        )
                    )
                    Text("${maxAltitude.toInt()}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Auto altitude from map data ──────────────────────────
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = 0.06f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUseAutoAltitudeChange(!useAutoAltitude) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = useAutoAltitude,
                    onCheckedChange = onUseAutoAltitudeChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = ActiveColor,
                        uncheckedColor = Color.White.copy(alpha = 0.3f)
                    )
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Auto altitude from map data",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = if (useAutoAltitude) "Altitude follows natural terrain profile"
                               else "Uses DA (min) / A (max) slider range",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                }
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
                .widthIn(max = 320.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(BrandDark),
                    contentAlignment = Alignment.Center
                ) {
                    Text("\uD83D\uDCCD", fontSize = 28.sp)
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "GPS Mock Location",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "Simulate GPS routes with realistic speed and altitude profiles.",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(alpha = 0.6f),
                    lineHeight = 17.sp
                )

                Spacer(Modifier.height(16.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("\uD83D\uDEE0\uFE0F", fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("GitHub", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                            Text("vincenzobpt/gps-mock-location", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ActiveColor)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ActiveColor)
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
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
    Surface(
        modifier = modifier,
        color = SurfaceGlass,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 6.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Status + progress
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (simulation.status == SimulationStatus.PLAYING) "\u25B6" else "\u23F8",
                    fontSize = 11.sp,
                    color = Color.White
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${(simulation.overallProgress * 100).toInt()}%",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(4.dp))

            // Speed
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("\u26A1", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${simulation.simulatedSpeedKmh.toInt()} km/h",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            // Altitude
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("\uD83C\uDFD4\uFE0F", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${simulation.currentAltitude.toInt()} m",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            Spacer(Modifier.height(8.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { simulation.overallProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = ActiveColor,
                trackColor = Color.White.copy(alpha = 0.15f)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  MAP ICONS
// ═══════════════════════════════════════════════════════════════

private fun pinIcon(
    context: android.content.Context,
    color: Color,
    label: String
): android.graphics.drawable.Drawable {
    val density = context.resources.displayMetrics.density
    val sizePx = (36 * density).toInt()
    val bitmap = android.graphics.Bitmap.createBitmap(
        sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    val cx = sizePx / 2f
    val cy = sizePx / 2f
    val r = sizePx / 2f - 2f

    paint.color = color.toArgb()
    paint.style = android.graphics.Paint.Style.FILL
    canvas.drawCircle(cx, cy, r, paint)

    paint.color = AwtColor.WHITE
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 2.5f
    canvas.drawCircle(cx, cy, r, paint)

    paint.color = AwtColor.WHITE
    paint.style = android.graphics.Paint.Style.FILL
    paint.textSize = sizePx * 0.48f
    paint.textAlign = android.graphics.Paint.Align.CENTER
    val yOff = (paint.descent() + paint.ascent()) / 2f
    canvas.drawText(label, cx, cy - yOff, paint)

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

    paint.color = color.toArgb()
    paint.style = android.graphics.Paint.Style.FILL
    canvas.drawCircle(diameter / 2f, diameter / 2f, rPx.toFloat(), paint)

    paint.color = AwtColor.WHITE
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 2.5f
    canvas.drawCircle(diameter / 2f, diameter / 2f, rPx.toFloat(), paint)

    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}
