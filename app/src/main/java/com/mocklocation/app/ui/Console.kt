package com.mocklocation.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mocklocation.app.model.AltitudeMode
import com.mocklocation.app.model.FixRate
import com.mocklocation.app.model.MapTheme
import com.mocklocation.app.model.Route
import com.mocklocation.app.model.SimulationConfig
import com.mocklocation.app.model.SimulationState
import com.mocklocation.app.model.SimulationStatus
import com.mocklocation.app.model.Waypoint
import com.mocklocation.app.simulation.SimulationEngine
import com.mocklocation.app.ui.theme.Cockpit
import kotlin.math.roundToInt

enum class ConsoleTab { ROUTE, TELEMETRY, TUNING }

/**
 * The bottom instrument console.
 *
 * Collapsed it is a transport bar and nothing else, so the map keeps the screen.
 * Expanded it becomes the cockpit: gauge, compass, full fix telemetry and the
 * tuning controls — all behind one gesture, never stacked as competing sheets.
 */
@Composable
fun Console(
    state: SimulationState,
    config: SimulationConfig,
    route: Route?,
    elevationProfile: List<Double>?,
    waypoints: List<Waypoint>,
    stopFractions: List<Float>,
    mapTheme: MapTheme,
    expanded: Boolean,
    tab: ConsoleTab,
    isCalculating: Boolean,
    canRoute: Boolean,
    onToggleExpanded: () -> Unit,
    onTabChange: (ConsoleTab) -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Float) -> Unit,
    onCalculate: () -> Unit,
    onClear: () -> Unit,
    onSwap: () -> Unit,
    onConfigChange: ((SimulationConfig) -> SimulationConfig) -> Unit,
    onRemoveWaypoint: (Int) -> Unit,
    onCycleDwell: (Int) -> Unit,
    onClearWaypoints: () -> Unit,
    onMapThemeChange: (MapTheme) -> Unit,
    onAddStopHint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        // Collapsed it floats over the map as glass; expanded it carries dense
        // numeric data, and glass at that density is just noise behind text.
        color = if (expanded) Cockpit.Panel else Cockpit.Glass,
        borderColor = Cockpit.Hairline,
    ) {
        Column(Modifier.fillMaxWidth()) {

            // ── Grab handle ───────────────────────────────────
            Box(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = 38.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(if (expanded) Cockpit.Live.copy(alpha = 0.5f) else Cockpit.HairlineStrong)
                )
            }

            // ── Route strip ───────────────────────────────────
            RouteStrip(
                state = state,
                route = route,
                stopFractions = stopFractions,
                onSeek = onSeek,
                modifier = Modifier.padding(horizontal = 18.dp)
            )

            Spacer(Modifier.height(10.dp))

            // ── Transport ─────────────────────────────────────
            TransportBar(
                state = state,
                canRoute = canRoute,
                hasRoute = route != null,
                isCalculating = isCalculating,
                onPlayPause = onPlayPause,
                onStop = onStop,
                onCalculate = onCalculate,
                onClear = onClear,
                onSwap = onSwap,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // ── Expandable body ───────────────────────────────
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(240)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(120)),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(14.dp))
                    TabStrip(tab, onTabChange, Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(12.dp))

                    // A scroll state per tab: sharing one would drop the user
                    // into the middle of the other pane on every switch.
                    key(tab) {
                        Column(
                            Modifier
                                .heightIn(max = 380.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 18.dp)
                        ) {
                            when (tab) {
                                ConsoleTab.ROUTE -> RoutePane(
                                    route = route,
                                    waypoints = waypoints,
                                    state = state,
                                    onRemoveWaypoint = onRemoveWaypoint,
                                    onCycleDwell = onCycleDwell,
                                    onClearWaypoints = onClearWaypoints,
                                    onAddStopHint = onAddStopHint,
                                )
                                ConsoleTab.TELEMETRY -> TelemetryPane(state, config, elevationProfile)
                                ConsoleTab.TUNING -> TuningPane(
                                    config = config,
                                    mapTheme = mapTheme,
                                    onChange = onConfigChange,
                                    onMapThemeChange = onMapThemeChange,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            // The console sits flush against the bottom edge; the gesture bar
            // inset lives inside the glass so nothing is ever clipped by it.
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  ROUTE STRIP
// ═══════════════════════════════════════════════════════════════

@Composable
private fun RouteStrip(
    state: SimulationState,
    route: Route?,
    stopFractions: List<Float>,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (route == null) "NO ROUTE" else formatDistance(state.distanceTraveledMeters),
                color = if (route == null) Cockpit.InkFaint else Cockpit.Ink,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${(state.progress * 100).roundToInt()}%",
                color = Cockpit.Live,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Box(Modifier.weight(1f))
            if (route != null) {
                Text(
                    text = "−${formatDistance(state.remainingMeters)}",
                    color = Cockpit.InkMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        ProgressRail(
            progress = state.progress,
            stopFractions = stopFractions,
            enabled = route != null,
            onSeek = onSeek,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  TRANSPORT
// ═══════════════════════════════════════════════════════════════

@Composable
private fun TransportBar(
    state: SimulationState,
    canRoute: Boolean,
    hasRoute: Boolean,
    isCalculating: Boolean,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onCalculate: () -> Unit,
    onClear: () -> Unit,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playing = state.status == SimulationStatus.PLAYING

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RailButton(
            glyph = if (isCalculating) "◌" else "⟲",
            onClick = onCalculate,
            active = canRoute && !hasRoute,
            size = 42.dp,
        )
        RailButton(glyph = "⇅", onClick = onSwap, size = 42.dp)
        RailButton(glyph = "✕", onClick = onClear, size = 42.dp)

        Box(Modifier.weight(1f))

        if (state.isActive) {
            RailButton(glyph = "◼", onClick = onStop, size = 42.dp)
        }

        PlayButton(playing = playing, enabled = hasRoute, onClick = onPlayPause)
    }
}

/** The one saturated element on the screen — it should be obvious what to press. */
@Composable
private fun PlayButton(playing: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val width by animateFloatAsState(
        targetValue = if (playing) 78f else 108f,
        animationSpec = tween(240),
        label = "playWidth"
    )
    val tint = when {
        !enabled -> Cockpit.InkFaint
        playing -> Cockpit.Live
        else -> Cockpit.Start
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        color = tint.copy(alpha = if (enabled) 0.16f else 0.06f),
        border = BorderStroke(1.dp, tint.copy(alpha = if (enabled) 0.55f else 0.16f)),
        modifier = Modifier
            .height(52.dp)
            .width(width.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (playing) "❚❚" else "▶",
                color = tint,
                fontSize = if (playing) 15.sp else 18.sp,
            )
            if (!playing) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "DRIVE",
                    color = tint,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  TABS
// ═══════════════════════════════════════════════════════════════

@Composable
private fun TabStrip(
    selected: ConsoleTab,
    onSelect: (ConsoleTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        ConsoleTab.entries.forEach { entry ->
            val active = entry == selected
            Surface(
                onClick = { onSelect(entry) },
                shape = RoundedCornerShape(11.dp),
                color = if (active) Cockpit.Live.copy(alpha = 0.15f) else Color.Transparent,
                modifier = Modifier.weight(1f),
            ) {
                Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = entry.name,
                        color = if (active) Cockpit.Live else Cockpit.InkMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  ROUTE PANE  —  intermediate stops
// ═══════════════════════════════════════════════════════════════

@Composable
private fun RoutePane(
    route: Route?,
    waypoints: List<Waypoint>,
    state: SimulationState,
    onRemoveWaypoint: (Int) -> Unit,
    onCycleDwell: (Int) -> Unit,
    onClearWaypoints: () -> Unit,
    onAddStopHint: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {

        SectionHeader(
            "Route",
            trailing = route?.let { "${it.totalDistanceFormatted} · ${it.durationFormatted}" }
        )
        Spacer(Modifier.height(10.dp))

        SectionHeader("Stops", trailing = if (waypoints.isEmpty()) null else "${waypoints.size}")
        Spacer(Modifier.height(8.dp))

        if (waypoints.isEmpty()) {
            // An empty list that only says "empty" teaches nothing; this one
            // arms the placement mode so the gesture is one tap away.
            Surface(
                onClick = onAddStopHint,
                shape = RoundedCornerShape(14.dp),
                color = Color.White.copy(alpha = 0.04f),
                border = BorderStroke(1.dp, Cockpit.Hairline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("＋", color = Cockpit.Warn, fontSize = 16.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Add an intermediate stop",
                            color = Cockpit.Ink,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Tap here, then long-press the map. The route is re-calculated through every stop in order.",
                            color = Cockpit.InkFaint,
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp,
                        )
                    }
                }
            }
        } else {
            waypoints.forEachIndexed { index, waypoint ->
                StopRow(
                    index = index,
                    waypoint = waypoint,
                    served = state.isActive && index in state.servedWaypoints,
                    onCycleDwell = { onCycleDwell(index) },
                    onRemove = { onRemoveWaypoint(index) },
                )
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = onAddStopHint,
                    shape = RoundedCornerShape(11.dp),
                    color = Cockpit.Warn.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, Cockpit.Warn.copy(alpha = 0.35f)),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(Modifier.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "＋ ADD STOP",
                            color = Cockpit.Warn,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Surface(
                    onClick = onClearWaypoints,
                    shape = RoundedCornerShape(11.dp),
                    color = Color.White.copy(alpha = 0.04f),
                    border = BorderStroke(1.dp, Cockpit.Hairline),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(Modifier.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "CLEAR ALL",
                            color = Cockpit.InkMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StopRow(
    index: Int,
    waypoint: Waypoint,
    served: Boolean,
    onCycleDwell: () -> Unit,
    onRemove: () -> Unit,
) {
    val accent = if (served) Cockpit.Start else Cockpit.Warn
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, Cockpit.Hairline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (served) "✓" else "${index + 1}",
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = formatDecimal(waypoint.latLng.latitude, 5) + ", " +
                        formatDecimal(waypoint.latLng.longitude, 5),
                    color = Cockpit.Ink,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
                Text(
                    text = if (waypoint.stayDurationSeconds <= 0) "drive through"
                    else "wait ${Waypoint.formatDwell(waypoint.stayDurationSeconds)}",
                    color = Cockpit.InkFaint,
                    fontSize = 10.sp,
                )
            }

            Spacer(Modifier.width(8.dp))

            Surface(
                onClick = onCycleDwell,
                shape = RoundedCornerShape(9.dp),
                color = accent.copy(alpha = 0.12f),
            ) {
                Text(
                    text = Waypoint.formatDwell(waypoint.stayDurationSeconds),
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                )
            }

            Spacer(Modifier.width(6.dp))

            Surface(
                onClick = onRemove,
                shape = RoundedCornerShape(9.dp),
                color = Color.White.copy(alpha = 0.05f),
            ) {
                Text(
                    text = "✕",
                    color = Cockpit.InkMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  TELEMETRY PANE
// ═══════════════════════════════════════════════════════════════

@Composable
private fun TelemetryPane(
    state: SimulationState,
    config: SimulationConfig,
    elevationProfile: List<Double>?,
) {
    Column(Modifier.fillMaxWidth()) {

        // ── Gauge cluster ─────────────────────────────────────
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(148.dp), contentAlignment = Alignment.Center) {
                SpeedGauge(
                    speedKmh = state.speedKmh,
                    targetKmh = state.targetSpeedKmh,
                    limitKmh = state.curveLimitKmh,
                    maxKmh = config.maxSpeedKmh,
                    live = state.status == SimulationStatus.PLAYING,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.speedKmh.roundToInt().toString(),
                        color = Cockpit.Ink,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = (-2).sp,
                    )
                    Text(
                        text = "km/h",
                        color = Cockpit.InkFaint,
                        fontSize = 10.sp,
                        letterSpacing = 1.4.sp,
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeadingDial(state.bearingDegrees, Modifier.size(44.dp))
                    Spacer(Modifier.width(10.dp))
                    ReadoutCell(
                        label = "Heading",
                        value = "${state.bearingDegrees.roundToInt()}°",
                        unit = state.cardinal,
                        valueSize = 17,
                    )
                }
                Row {
                    ReadoutCell(
                        label = "Altitude",
                        value = state.altitudeMeters.roundToInt().toString(),
                        unit = "m",
                        valueColor = Cockpit.Violet,
                        modifier = Modifier.weight(1f),
                    )
                    ReadoutCell(
                        label = "Accel",
                        value = formatSigned(state.accelerationMs2, 1),
                        unit = "m/s²",
                        valueColor = when {
                            state.isAccelerating -> Cockpit.Start
                            state.isBraking -> Cockpit.Warn
                            else -> Cockpit.InkMuted
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row {
                    ReadoutCell(
                        label = "Elapsed",
                        value = formatDuration(state.elapsedSeconds),
                        modifier = Modifier.weight(1f),
                    )
                    ReadoutCell(
                        label = "ETA",
                        value = state.etaSeconds?.let { formatDuration(it) } ?: "—:—",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Fix ───────────────────────────────────────────────
        SectionHeader("Fix", trailing = "#${state.fixCount}")
        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth()) {
            ReadoutCell(
                label = "Latitude",
                value = formatDecimal(state.position?.latitude ?: 0.0, 6),
                modifier = Modifier.weight(1f),
                valueSize = 14,
            )
            ReadoutCell(
                label = "Longitude",
                value = formatDecimal(state.position?.longitude ?: 0.0, 6),
                modifier = Modifier.weight(1f),
                valueSize = 14,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            ReadoutCell(
                label = "DMS lat",
                value = formatDms(state.position?.latitude ?: 0.0, true),
                modifier = Modifier.weight(1f),
                valueSize = 11,
                valueColor = Cockpit.InkMuted,
            )
            ReadoutCell(
                label = "DMS lon",
                value = formatDms(state.position?.longitude ?: 0.0, false),
                modifier = Modifier.weight(1f),
                valueSize = 11,
                valueColor = Cockpit.InkMuted,
            )
        }

        Spacer(Modifier.height(14.dp))

        // ── Receiver ──────────────────────────────────────────
        SectionHeader("Receiver")
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            ReadoutCell(
                label = "Accuracy",
                value = formatDecimal(state.accuracyMeters.toDouble(), 1),
                unit = "m",
                modifier = Modifier.weight(1f),
                valueSize = 14,
            )
            ReadoutCell(
                label = "HDOP",
                value = formatDecimal(state.hdop.toDouble(), 2),
                modifier = Modifier.weight(1f),
                valueSize = 14,
                valueColor = if (state.hdop < 1.2f) Cockpit.Start else Cockpit.Warn,
            )
            ReadoutCell(
                label = "Sats",
                value = "${state.satellitesUsed}/${state.satellitesInView}",
                modifier = Modifier.weight(1f),
                valueSize = 14,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ReadoutCell(
                label = "Rate",
                value = formatDecimal(state.updateRateHz.toDouble(), 1),
                unit = "Hz",
                modifier = Modifier.weight(1f),
                valueSize = 14,
            )
            ReadoutCell(
                label = "Speed",
                value = formatDecimal(state.speedMs.toDouble(), 1),
                unit = "m/s",
                modifier = Modifier.weight(1f),
                valueSize = 14,
            )
            Column(Modifier.weight(1f)) {
                FieldLabel("Injecting")
                Spacer(Modifier.height(4.dp))
                InjectionActivity(
                    active = state.status == SimulationStatus.PLAYING && state.injectionError == null,
                    modifier = Modifier.size(width = 42.dp, height = 14.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        ProviderRow(state)

        Spacer(Modifier.height(16.dp))

        // ── Terrain ───────────────────────────────────────────
        SectionHeader(
            "Terrain",
            trailing = if (elevationProfile != null) "OSRM" else "SYNTHETIC"
        )
        Spacer(Modifier.height(8.dp))
        ElevationProfile(
            samples = elevationProfile,
            progress = state.progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        )
    }
}

@Composable
private fun ProviderRow(state: SimulationState) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FieldLabel("Providers", Modifier.width(66.dp))
        if (state.activeProviders.isEmpty()) {
            Text(
                text = "none",
                color = Cockpit.InkFaint,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        } else {
            state.activeProviders.forEach { provider ->
                StatusPill(
                    text = provider.uppercase(),
                    color = Cockpit.Start,
                    live = state.status == SimulationStatus.PLAYING,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  TUNING PANE
// ═══════════════════════════════════════════════════════════════

@Composable
private fun TuningPane(
    config: SimulationConfig,
    mapTheme: MapTheme,
    onChange: ((SimulationConfig) -> SimulationConfig) -> Unit,
    onMapThemeChange: (MapTheme) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {

        PillGroup(
            label = "Basemap",
            options = MapTheme.entries.map { it to it.label },
            selected = mapTheme,
            onSelect = onMapThemeChange,
        )
        Text(
            text = when (mapTheme) {
                MapTheme.SYSTEM -> "Follows the device light/dark setting"
                MapTheme.DARK -> "Always dark — CARTO Dark Matter"
                MapTheme.LIGHT -> "Always light — OpenStreetMap standard"
            },
            color = Cockpit.InkFaint,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 6.dp),
        )

        Spacer(Modifier.height(16.dp))

        RangeHeader(
            icon = "⚡",
            title = "Speed band",
            value = "${config.minSpeedKmh.roundToInt()}–${config.maxSpeedKmh.roundToInt()} km/h",
        )
        RangeSlider(
            value = config.minSpeedKmh..maxOf(config.maxSpeedKmh, config.minSpeedKmh),
            onValueChange = { range ->
                onChange { it.copy(minSpeedKmh = range.start, maxSpeedKmh = range.endInclusive) }
            },
            valueRange = 0f..SimulationEngine.MAX_SPEED_KMH,
            colors = rangeColors(Cockpit.Live),
        )

        Spacer(Modifier.height(6.dp))

        RangeHeader(
            icon = "⛰",
            title = "Altitude band",
            value = "${config.minAltitudeMeters.roundToInt()}–${config.maxAltitudeMeters.roundToInt()} m",
            accent = Cockpit.Violet,
        )
        RangeSlider(
            value = config.minAltitudeMeters..maxOf(config.maxAltitudeMeters, config.minAltitudeMeters),
            onValueChange = { range ->
                onChange {
                    it.copy(minAltitudeMeters = range.start, maxAltitudeMeters = range.endInclusive)
                }
            },
            valueRange = 0f..4000f,
            colors = rangeColors(Cockpit.Violet),
            enabled = config.altitudeMode != AltitudeMode.TERRAIN,
        )

        Spacer(Modifier.height(4.dp))
        PillGroup(
            label = "Altitude source",
            options = AltitudeMode.entries.map { it to it.name.lowercase().replaceFirstChar(Char::titlecase) },
            selected = config.altitudeMode,
            onSelect = { mode -> onChange { it.copy(altitudeMode = mode) } },
        )

        Spacer(Modifier.height(14.dp))

        PillGroup(
            label = "Fix rate",
            options = FixRate.entries.map { it to it.label },
            selected = config.fixRate,
            onSelect = { rate -> onChange { it.copy(fixRate = rate) } },
        )

        Spacer(Modifier.height(14.dp))

        RangeHeader(
            icon = "◎",
            title = "Reported accuracy",
            value = "${formatDecimal(config.accuracyMeters.toDouble(), 1)} m",
        )
        Slider(
            value = config.accuracyMeters,
            onValueChange = { v -> onChange { it.copy(accuracyMeters = v) } },
            valueRange = 1f..25f,
            colors = SliderDefaults.colors(
                thumbColor = Cockpit.Ink,
                activeTrackColor = Cockpit.Live,
                inactiveTrackColor = Cockpit.Hairline,
            ),
        )

        Spacer(Modifier.height(6.dp))

        ToggleRow(
            title = "GPS jitter",
            subtitle = "Sub-metre random walk so the trace looks like a real receiver",
            checked = config.gpsJitter,
            onCheckedChange = { v -> onChange { it.copy(gpsJitter = v) } },
        )
        ToggleRow(
            title = "Corner braking",
            subtitle = "Slow down for curves using the route's own geometry",
            checked = config.curveBraking,
            onCheckedChange = { v -> onChange { it.copy(curveBraking = v) } },
        )
        ToggleRow(
            title = "Loop route",
            subtitle = "Restart from the origin on arrival instead of stopping",
            checked = config.loopRoute,
            onCheckedChange = { v -> onChange { it.copy(loopRoute = v) } },
        )
    }
}

@Composable
private fun <T> PillGroup(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        FieldLabel(label)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (value, title) ->
                val active = value == selected
                Surface(
                    onClick = { onSelect(value) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (active) Cockpit.Live.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f),
                    border = BorderStroke(
                        1.dp,
                        if (active) Cockpit.Live.copy(alpha = 0.5f) else Cockpit.Hairline
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = title,
                            color = if (active) Cockpit.Live else Cockpit.InkMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = Cockpit.Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                color = Cockpit.InkFaint,
                fontSize = 10.sp,
                lineHeight = 13.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Cockpit.Void,
                checkedTrackColor = Cockpit.Live,
                uncheckedThumbColor = Cockpit.InkMuted,
                uncheckedTrackColor = Color.Transparent,
                uncheckedBorderColor = Cockpit.HairlineStrong,
            ),
        )
    }
}

@Composable
private fun rangeColors(accent: Color) = SliderDefaults.colors(
    thumbColor = Cockpit.Ink,
    activeTrackColor = accent,
    inactiveTrackColor = Cockpit.Hairline,
    disabledThumbColor = Cockpit.InkFaint,
    disabledActiveTrackColor = Cockpit.InkFaint.copy(alpha = 0.4f),
    disabledInactiveTrackColor = Cockpit.Hairline,
)
