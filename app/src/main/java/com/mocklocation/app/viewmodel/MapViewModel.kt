package com.mocklocation.app.viewmodel

import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mocklocation.app.model.LatLng
import com.mocklocation.app.model.MarkerMode
import com.mocklocation.app.model.Route
import com.mocklocation.app.model.SimulationState
import com.mocklocation.app.model.SimulationStatus
import com.mocklocation.app.network.OsrmRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MapViewModel(application: Application) : AndroidViewModel(application) {

    // ── Map state ──────────────────────────────────────────────
    private val _startPoint = MutableStateFlow<LatLng?>(null)
    val startPoint: StateFlow<LatLng?> = _startPoint.asStateFlow()

    private val _endPoint = MutableStateFlow<LatLng?>(null)
    val endPoint: StateFlow<LatLng?> = _endPoint.asStateFlow()

    private val _markerMode = MutableStateFlow(MarkerMode.START)
    val markerMode: StateFlow<MarkerMode> = _markerMode.asStateFlow()

    private val _route = MutableStateFlow<Route?>(null)
    val route: StateFlow<Route?> = _route.asStateFlow()

    private val _isCalculatingRoute = MutableStateFlow(false)
    val isCalculatingRoute: StateFlow<Boolean> = _isCalculatingRoute.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // ── Simulation state ───────────────────────────────────────
    private val _simulation = MutableStateFlow(SimulationState())
    val simulation: StateFlow<SimulationState> = _simulation.asStateFlow()

    private var simulationJob: Job? = null
    private val repository = OsrmRepository()

    // ── Map center (default: Rome) ──────────────────────────────
    private val _mapCenter = MutableStateFlow(LatLng(41.9028, 12.4964))
    val mapCenter: StateFlow<LatLng> = _mapCenter.asStateFlow()

    // ── Speed options ──────────────────────────────────────────
    val speedOptions = listOf(0.5f, 1f, 2f, 5f, 10f, 50f)

    // ── Public API ────────────────────────────────────────────

    fun setMapCenter(latLng: LatLng) {
        _mapCenter.value = latLng
    }

    /** Toggle between placing START or END marker. */
    fun cycleMarkerMode() {
        _markerMode.value = when (_markerMode.value) {
            MarkerMode.START -> MarkerMode.END
            MarkerMode.END -> MarkerMode.START
        }
    }

    /** Called when user long-presses the map at [latLng]. */
    fun onMapLongPress(latLng: LatLng) {
        when (_markerMode.value) {
            MarkerMode.START -> {
                _startPoint.value = latLng
                _markerMode.value = MarkerMode.END
                _errorMessage.value = null
            }
            MarkerMode.END -> {
                _endPoint.value = latLng
                _markerMode.value = MarkerMode.START
                _errorMessage.value = null
            }
        }
        // Auto-fetch route if both points are set
        val start = _startPoint.value ?: return
        val end = _endPoint.value ?: return
        fetchRoute(start, end)
    }

    /** Calculate route from the current start/end points. */
    fun calculateRoute() {
        val start = _startPoint.value ?: run {
            _errorMessage.value = "Set start point first"
            return
        }
        val end = _endPoint.value ?: run {
            _errorMessage.value = "Set end point first"
            return
        }
        fetchRoute(start, end)
    }

    /** Clear all route data and stop simulation. */
    fun clearAll() {
        stopSimulation()
        _startPoint.value = null
        _endPoint.value = null
        _route.value = null
        _errorMessage.value = null
        _markerMode.value = MarkerMode.START
    }

    // ── Simulation controls ────────────────────────────────────

    fun playSimulation() {
        val route = _route.value ?: run {
            _errorMessage.value = "Calculate a route first"
            return
        }
        if (route.points.size < 2) {
            _errorMessage.value = "Route has no path points"
            return
        }

        _simulation.value = _simulation.value.copy(status = SimulationStatus.PLAYING)
        simulationJob?.cancel()
        simulationJob = viewModelScope.launch {
            runSimulation(route)
        }
    }

    fun pauseSimulation() {
        _simulation.value = _simulation.value.copy(status = SimulationStatus.PAUSED)
        simulationJob?.cancel()
    }

    fun stopSimulation() {
        simulationJob?.cancel()
        _simulation.value = SimulationState()
    }

    fun setSpeedMultiplier(multiplier: Float) {
        _simulation.value = _simulation.value.copy(speedMultiplier = multiplier)
    }

    fun clearError() {
        _errorMessage.value = null
    }

    // ── Internal ──────────────────────────────────────────────

    private fun fetchRoute(start: LatLng, end: LatLng) {
        viewModelScope.launch {
            _isCalculatingRoute.value = true
            _errorMessage.value = null
            repository.getRoute(start, end).fold(
                onSuccess = { route ->
                    _route.value = route
                    _simulation.value = _simulation.value.copy(
                        totalDistanceMeters = route.distanceMeters
                    )
                },
                onFailure = { e ->
                    _errorMessage.value = "Route error: ${e.localizedMessage ?: "Unknown"}"
                }
            )
            _isCalculatingRoute.value = false
        }
    }

    /**
     * Core simulation loop: steps through route points, injecting mock locations.
     */
    private suspend fun runSimulation(route: Route) {
        val points = route.points
        val totalSegments = points.size - 1

        var state = _simulation.value.copy(
            totalDistanceMeters = route.distanceMeters
        )

        while (state.segmentIndex < totalSegments && state.status == SimulationStatus.PLAYING) {
            val from = points[state.segmentIndex]
            val to = points[state.segmentIndex + 1]
            val segDist = from.distanceTo(to)

            // Determine realistic speed for this segment
            val segDuration = route.durationSeconds * (segDist / route.distanceMeters)
            val baseSpeedMs = if (segDuration > 0) segDist / segDuration else 14.0 // ~50 km/h fallback
            val speedMs = baseSpeedMs * state.speedMultiplier
            val speedKmh = (speedMs * 3.6).toFloat()

            // Sub-steps for smooth movement
            val intervalSec = state.updateIntervalMs / 1000.0
            val stepsInSegment = if (speedMs > 0) {
                maxOf(1L, (segDist / (speedMs * intervalSec)).toLong())
            } else {
                1L
            }

            for (step in 0 until stepsInSegment) {
                if (state.status != SimulationStatus.PLAYING) break

                val t = if (stepsInSegment > 1) step.toDouble() / (stepsInSegment - 1) else 0.0
                val position = from.interpolateTo(to, t)
                val bearing = from.bearingTo(to)

                state = state.copy(
                    segmentIndex = state.segmentIndex,
                    segmentProgress = t.toFloat(),
                    overallProgress = ((state.segmentIndex + t) / totalSegments).toFloat(),
                    currentPosition = position,
                    currentBearing = bearing,
                    simulatedSpeedKmh = speedKmh,
                    elapsedSeconds = state.elapsedSeconds + (intervalSec.toLong()).toLong()
                )
                _simulation.value = state

                injectMockLocation(position, bearing, speedKmh)

                // Wait — speed up by reducing delay proportionally
                val actualDelay = (state.updateIntervalMs / state.speedMultiplier).toLong().coerceAtLeast(50L)
                delay(actualDelay)
            }

            // Advance to next segment
            if (state.status == SimulationStatus.PLAYING && state.segmentIndex < totalSegments) {
                state = state.copy(segmentIndex = state.segmentIndex + 1, segmentProgress = 0f)
                _simulation.value = state
            }
        }

        // Ensure we end at destination
        if (state.status == SimulationStatus.PLAYING) {
            val lastPos = points.last()
            val finalBearing = if (points.size >= 2) points[points.size - 2].bearingTo(lastPos) else 0f
            injectMockLocation(lastPos, finalBearing, 0f)
            state = state.copy(
                status = SimulationStatus.IDLE,
                currentPosition = lastPos,
                overallProgress = 1f,
                segmentIndex = totalSegments,
                segmentProgress = 1f,
                simulatedSpeedKmh = 0f
            )
            _simulation.value = state
        }
    }

    /** Inject a mock location into Android's LocationManager. */
    private fun injectMockLocation(latLng: LatLng, bearing: Float, speedKmh: Float) {
        val context = getApplication<Application>()
        try {
            val location = Location(LocationManager.GPS_PROVIDER).apply {
                latitude = latLng.latitude
                longitude = latLng.longitude
                accuracy = 3f
                this.bearing = bearing
                speed = speedKmh / 3.6f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
            try {
                lm.addTestProvider(
                    LocationManager.GPS_PROVIDER,
                    false, false, false, false, true, true, true,
                    android.location.Criteria.POWER_LOW,
                    android.location.Criteria.ACCURACY_FINE
                )
                lm.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            } catch (_: IllegalArgumentException) {
                // Provider already exists
            }
            lm.setTestProviderLocation(LocationManager.GPS_PROVIDER, location)
        } catch (_: SecurityException) {
            _errorMessage.value = "Mock location not granted. Go to: Developer Options \u2192 Select mock location app \u2192 choose MockLocation"
        } catch (_: Exception) {
            // Ignore transient errors
        }
    }
}
