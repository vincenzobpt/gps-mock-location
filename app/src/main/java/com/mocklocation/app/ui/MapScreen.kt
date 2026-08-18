package com.mocklocation.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mocklocation.app.location.MockLocationEngine
import com.mocklocation.app.model.MapTheme
import com.mocklocation.app.model.MarkerMode
import com.mocklocation.app.model.SimulationStatus
import com.mocklocation.app.ui.theme.Cockpit
import com.mocklocation.app.viewmodel.MapViewModel
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

@Composable
fun MapScreen(viewModel: MapViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current

    val route by viewModel.route.collectAsStateWithLifecycle()
    val simulation by viewModel.simulation.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val providerStatus by viewModel.providerStatus.collectAsStateWithLifecycle()
    val startPoint by viewModel.startPoint.collectAsStateWithLifecycle()
    val endPoint by viewModel.endPoint.collectAsStateWithLifecycle()
    val markerMode by viewModel.markerMode.collectAsStateWithLifecycle()
    val isCalculating by viewModel.isCalculatingRoute.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val suggestions by viewModel.searchSuggestions.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val cameraCommand by viewModel.cameraCommand.collectAsStateWithLifecycle()
    val followVehicle by viewModel.followVehicle.collectAsStateWithLifecycle()
    val waypoints by viewModel.waypoints.collectAsStateWithLifecycle()
    val mapTheme by viewModel.mapTheme.collectAsStateWithLifecycle()

    // SYSTEM resolves here, where Compose already tracks the configuration, so
    // the map follows a theme change without a restart.
    val systemInDark = isSystemInDarkTheme()
    val nightMap = mapTheme.isDark(systemInDark)

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var consoleExpanded by remember { mutableStateOf(false) }
    var consoleTab by remember { mutableStateOf(ConsoleTab.ROUTE) }
    var showAbout by remember { mutableStateOf(false) }
    var permissionsGranted by remember { mutableStateOf(hasLocationPermission(context)) }

    // ── Runtime permissions ─────────────────────────────────────
    // The previous build declared them in the manifest only, which since API 23
    // grants nothing — the very reason injection failed with "all permissions
    // given".
    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionsGranted = hasLocationPermission(context)
        // Re-arm the providers if the grant arrived mid-run.
        if (permissionsGranted) viewModel.refreshProviderStatus()
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) permissionLauncher.launch(requiredPermissions)
    }

    // ── osmdroid + permission lifecycle ─────────────────────────
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapView?.onResume()
                    permissionsGranted = hasLocationPermission(context)
                }
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── Camera commands ─────────────────────────────────────────
    LaunchedEffect(cameraCommand, mapView) {
        val map = mapView ?: return@LaunchedEffect
        when (val command = cameraCommand) {
            is MapViewModel.CameraCommand.MoveTo -> map.controller.animateTo(
                GeoPoint(command.point.latitude, command.point.longitude),
                command.zoom ?: map.zoomLevelDouble,
                600L
            )
            is MapViewModel.CameraCommand.FitRoute ->
                route?.points?.let { map.fitTo(it, paddingPx = (map.width * 0.14f).toInt()) }
            null -> Unit
        }
    }

    // ── Follow the vehicle ──────────────────────────────────────
    LaunchedEffect(simulation.position, followVehicle) {
        if (!followVehicle) return@LaunchedEffect
        val pos = simulation.position ?: return@LaunchedEffect
        // setCenter, not animateTo: at up to 10 Hz an animation queue would
        // fight itself and the map would visibly stutter.
        mapView?.controller?.setCenter(GeoPoint(pos.latitude, pos.longitude))
    }

    if (showAbout) AboutDialog(onDismiss = { showAbout = false })

    Box(
        Modifier
            .fillMaxSize()
            .background(Cockpit.Void)
    ) {
        MapCanvas(
            route = route,
            simulation = simulation,
            startPoint = startPoint,
            endPoint = endPoint,
            waypoints = waypoints.map { it.latLng },
            nightMode = nightMap,
            onLongPress = viewModel::onMapLongPress,
            onUserPan = { if (followVehicle) viewModel.setFollowVehicle(false) },
            onMapReady = { mapView = it },
            modifier = Modifier.fillMaxSize(),
        )

        // Top scrim so white tiles never fight the search bar.
        Box(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .background(Cockpit.TopScrim)
        )

        // ── Top: search + status ────────────────────────────────
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            SearchBar(
                query = searchQuery,
                isSearching = isSearching,
                onQueryChange = viewModel::onSearchQueryChanged,
                onSubmit = {
                    focusManager.clearFocus()
                    viewModel.searchExplicit(searchQuery)
                },
                onClear = { viewModel.onSearchQueryChanged("") },
                onAbout = { showAbout = true },
            )

            AnimatedVisibility(
                visible = suggestions.isNotEmpty(),
                enter = fadeIn() + slideInVertically { -it / 4 },
                exit = fadeOut() + slideOutVertically { -it / 4 },
            ) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    GlassSurface(shape = RoundedCornerShape(18.dp)) {
                        Column {
                            suggestions.take(6).forEach { suggestion ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            focusManager.clearFocus()
                                            viewModel.selectSuggestion(suggestion)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("◈", color = Cockpit.Live, fontSize = 11.sp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = suggestion.shortName,
                                            color = Cockpit.Ink,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (suggestion.context.isNotEmpty()) {
                                            Text(
                                                text = suggestion.context,
                                                color = Cockpit.InkFaint,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            StatusRow(
                markerMode = markerMode,
                hasStart = startPoint != null,
                hasEnd = endPoint != null,
                stopCount = waypoints.size,
                simulation = simulation,
                onModeClick = viewModel::cycleMarkerMode,
            )

            // ── Blocking conditions ─────────────────────────────
            val blocker = blockerFor(providerStatus, permissionsGranted)
            AnimatedVisibility(visible = blocker != null) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    if (blocker != null) {
                        AlertBanner(
                            title = blocker.title,
                            body = blocker.body,
                            action = blocker.action,
                            onAction = {
                                when (blocker.kind) {
                                    BlockerKind.PERMISSION -> permissionLauncher.launch(requiredPermissions)
                                    BlockerKind.MOCK_APP -> context.openDeveloperOptions()
                                }
                            },
                        )
                    }
                }
            }

            AnimatedVisibility(visible = errorMessage != null) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    GlassSurface(
                        shape = RoundedCornerShape(14.dp),
                        color = Cockpit.Danger.copy(alpha = 0.16f),
                        borderColor = Cockpit.Danger.copy(alpha = 0.4f),
                        modifier = Modifier.clickable { viewModel.clearError() },
                    ) {
                        Text(
                            text = errorMessage.orEmpty(),
                            color = Cockpit.Ink,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }

        // ── Right rail ──────────────────────────────────────────
        // Hidden while the console is open: the glass is translucent, so a rail
        // left underneath reads as a rendering glitch rather than a control.
        AnimatedVisibility(
            visible = !consoleExpanded,
            enter = fadeIn() + slideInHorizontally { it / 2 },
            exit = fadeOut() + slideOutHorizontally { it / 2 },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Column(
                Modifier.padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                RailButton(
                    glyph = if (nightMap) "☾" else "☀",
                    onClick = { viewModel.toggleMapTheme(systemInDark) },
                    active = nightMap,
                )
                RailButton("⤢", onClick = viewModel::requestFitRoute)
                RailButton(
                    glyph = "◎",
                    onClick = viewModel::recenterOnVehicle,
                    active = followVehicle,
                )
                Spacer(Modifier.height(4.dp))
                RailButton("+", onClick = { mapView?.controller?.zoomIn() })
                RailButton("−", onClick = { mapView?.controller?.zoomOut() })
            }
        }

        // ── Bottom console ──────────────────────────────────────
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(220.dp)
                .background(Cockpit.BottomScrim)
        )
        Console(
            state = simulation,
            config = config,
            route = route,
            // Resampling the profile is O(n log n); it only changes with the
            // route, never with the fix, so it must not run at the tick rate.
            elevationProfile = remember(route) { viewModel.elevationProfile },
            waypoints = waypoints,
            stopFractions = remember(route, waypoints) { viewModel.stopFractions },
            mapTheme = mapTheme,
            expanded = consoleExpanded,
            tab = consoleTab,
            isCalculating = isCalculating,
            canRoute = startPoint != null && endPoint != null,
            onToggleExpanded = { consoleExpanded = !consoleExpanded },
            onTabChange = { consoleTab = it },
            onPlayPause = viewModel::togglePlay,
            onStop = viewModel::stopSimulation,
            onSeek = viewModel::seekTo,
            onCalculate = viewModel::calculateRoute,
            onClear = viewModel::clearAll,
            onSwap = viewModel::swapEndpoints,
            onConfigChange = viewModel::updateConfig,
            onRemoveWaypoint = viewModel::removeWaypoint,
            onCycleDwell = viewModel::cycleWaypointDwell,
            onClearWaypoints = viewModel::clearWaypoints,
            onMapThemeChange = viewModel::setMapTheme,
            onAddStopHint = {
                // Arm the gesture and get out of the way so the map is reachable.
                viewModel.setMarkerMode(MarkerMode.STOP)
                consoleExpanded = false
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  SEARCH
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SearchBar(
    query: String,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onAbout: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GlassSurface(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f),
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isSearching) "◌" else "⌕",
                    color = if (isSearching) Cockpit.Live else Cockpit.InkMuted,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        color = Cockpit.Ink,
                        fontSize = 14.sp,
                    ),
                    cursorBrush = SolidColor(Cockpit.Live),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                text = "Search a place…",
                                color = Cockpit.InkFaint,
                                fontSize = 14.sp,
                            )
                        }
                        inner()
                    },
                    modifier = Modifier.weight(1f),
                )
                if (query.isNotEmpty()) {
                    Text(
                        text = "✕",
                        color = Cockpit.InkMuted,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable(onClick = onClear)
                            .padding(start = 8.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        RailButton("ℹ", onClick = onAbout, size = 46.dp)
    }
}

// ═══════════════════════════════════════════════════════════════
//  STATUS
// ═══════════════════════════════════════════════════════════════

@Composable
private fun StatusRow(
    markerMode: MarkerMode,
    hasStart: Boolean,
    hasEnd: Boolean,
    stopCount: Int,
    simulation: com.mocklocation.app.model.SimulationState,
    onModeClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val modeColor = when (markerMode) {
            MarkerMode.START -> Cockpit.Start
            MarkerMode.END -> Cockpit.End
            MarkerMode.STOP -> Cockpit.Warn
        }
        StatusPill(
            text = "HOLD → ${markerMode.label}",
            color = modeColor,
            onClick = onModeClick,
        )
        if (hasStart && hasEnd) {
            StatusPill(
                text = if (stopCount > 0) "A→B · $stopCount STOP" else "A→B SET",
                color = Cockpit.InkMuted,
            )
        }
        Box(Modifier.weight(1f))
        when {
            simulation.isDwelling -> StatusPill(
                text = "STOP ${(simulation.activeStopWaypoint ?: 0) + 1} · ${simulation.dwellRemainingSeconds}s",
                color = Cockpit.Warn,
                live = true,
            )
            simulation.status == SimulationStatus.PLAYING -> StatusPill(
                text = "LIVE · ${simulation.fixCount}",
                color = Cockpit.Live,
                live = true,
            )
            simulation.status == SimulationStatus.PAUSED ->
                StatusPill(text = "HOLD", color = Cockpit.Warn)
            else -> Unit
        }
    }
}

private enum class BlockerKind { PERMISSION, MOCK_APP }

private data class Blocker(
    val kind: BlockerKind,
    val title: String,
    val body: String,
    val action: String,
)

private fun blockerFor(
    status: MockLocationEngine.Status,
    permissionsGranted: Boolean,
): Blocker? = when {
    !permissionsGranted -> Blocker(
        BlockerKind.PERMISSION,
        "Location permission required",
        "Android will not accept injected fixes from an app that has not been granted precise location.",
        "GRANT",
    )
    status is MockLocationEngine.Status.NotSelectedAsMockApp -> Blocker(
        BlockerKind.MOCK_APP,
        "Not the selected mock location app",
        "Developer options → Select mock location app → GPS Mock Location.",
        "OPEN SETTINGS",
    )
    status is MockLocationEngine.Status.MissingLocationPermission -> Blocker(
        BlockerKind.PERMISSION,
        "Location permission required",
        "Grant precise location so the platform accepts the test provider.",
        "GRANT",
    )
    status is MockLocationEngine.Status.Failed -> Blocker(
        BlockerKind.MOCK_APP,
        "Mock providers were lost",
        "${status.reason}. Another app may have taken over mock location.",
        "OPEN SETTINGS",
    )
    else -> null
}

@Composable
private fun AlertBanner(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
) {
    GlassSurface(
        shape = RoundedCornerShape(16.dp),
        color = Cockpit.Warn.copy(alpha = 0.12f),
        borderColor = Cockpit.Warn.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Cockpit.Warn,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = body,
                    color = Cockpit.InkMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Surface(
                onClick = onAction,
                shape = RoundedCornerShape(10.dp),
                color = Cockpit.Warn.copy(alpha = 0.20f),
            ) {
                Text(
                    text = action,
                    color = Cockpit.Warn,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  HELPERS
// ═══════════════════════════════════════════════════════════════

private fun hasLocationPermission(context: android.content.Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun android.content.Context.openDeveloperOptions() {
    val intents = listOf(
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )
    for (intent in intents) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { startActivity(intent) }.isSuccess) return
    }
}
