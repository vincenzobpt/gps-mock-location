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
import com.mocklocation.app.model.SearchSuggestion
import com.mocklocation.app.model.SimulationState
import com.mocklocation.app.model.SimulationStatus
import com.mocklocation.app.network.OsrmRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

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

    private val _mapCenter = MutableStateFlow(LatLng(41.9028, 12.4964))
    val mapCenter: StateFlow<LatLng> = _mapCenter.asStateFlow()

    private val _mapZoom = MutableStateFlow(3.0)
    val mapZoom: StateFlow<Double> = _mapZoom.asStateFlow()

    // ── Simulation state ───────────────────────────────────────
    private val _simulation = MutableStateFlow(SimulationState())
    val simulation: StateFlow<SimulationState> = _simulation.asStateFlow()

    private var simulationJob: Job? = null
    private val repository = OsrmRepository()

    // ── HTTP client for Nominatim geocoding ─────────────────────
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // ── Search state ────────────────────────────────────────────
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchSuggestions = MutableStateFlow<List<SearchSuggestion>>(emptyList())
    val searchSuggestions: StateFlow<List<SearchSuggestion>> = _searchSuggestions.asStateFlow()

    private var searchJob: Job? = null

    /** Called on every keystroke — debounced 300 ms, fetches auto-complete suggestions. */
    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchSuggestions.value = emptyList()
            searchJob?.cancel()
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300) // debounce
            _isSearching.value = true
            try {
                val results = geocode(query.trim(), limit = 5)
                _searchSuggestions.value = results
            } catch (_: Exception) {
                // silent for suggestions — don't spam error banner
            }
            _isSearching.value = false
        }
    }

    /** Called when user presses Go / keyboard Search. Centers map on first result. */
    fun searchExplicit(query: String) {
        if (query.isBlank()) return
        searchJob?.cancel()
        viewModelScope.launch {
            _isSearching.value = true
            _errorMessage.value = null
            try {
                val results = geocode(query.trim(), limit = 1)
                if (results.isNotEmpty()) {
                    val hit = results.first()
                    _mapCenter.value = LatLng(hit.latitude, hit.longitude)
                    _mapZoom.value = 15.0
                    _searchQuery.value = hit.displayName
                    _searchSuggestions.value = emptyList()
                } else {
                    _errorMessage.value = "Location not found"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Search error: ${e.localizedMessage ?: "Unknown"}"
            }
            _isSearching.value = false
        }
    }

    /** Called when user taps a suggestion. Centers map and closes list. */
    fun selectSuggestion(suggestion: SearchSuggestion) {
        _mapCenter.value = LatLng(suggestion.latitude, suggestion.longitude)
        _mapZoom.value = 15.0
        _searchQuery.value = suggestion.displayName
        _searchSuggestions.value = emptyList()
    }

    /** Clear suggestions (e.g. when map is tapped). */
    fun clearSuggestions() {
        _searchSuggestions.value = emptyList()
    }

    private suspend fun geocode(query: String, limit: Int): List<SearchSuggestion> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url("https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=$limit")
                .header("User-Agent", "MockLocation/1.0 (Android)")
                .build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val arr = JSONArray(body)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                SearchSuggestion(
                    displayName = obj.getString("display_name"),
                    latitude = obj.getDouble("lat"),
                    longitude = obj.getDouble("lon")
                )
            }
        }

    // ── Speed bounds (km/h) ────────────────────────────────────
    private val _minSpeedKmh = MutableStateFlow(0f)
    val minSpeedKmh: StateFlow<Float> = _minSpeedKmh.asStateFlow()

    private val _maxSpeedKmh = MutableStateFlow(240f)
    val maxSpeedKmh: StateFlow<Float> = _maxSpeedKmh.asStateFlow()

    fun setMinSpeed(v: Float) {
        _minSpeedKmh.value = v.coerceIn(0f, _maxSpeedKmh.value)
    }
    fun setMaxSpeed(v: Float) {
        _maxSpeedKmh.value = v.coerceIn(_minSpeedKmh.value, 240f)
    }

    // ── Altitude bounds (metres) ──────────────────────────────────
    private val _minAltitude = MutableStateFlow(0f)
    val minAltitude: StateFlow<Float> = _minAltitude.asStateFlow()

    private val _maxAltitude = MutableStateFlow(1000f)
    val maxAltitude: StateFlow<Float> = _maxAltitude.asStateFlow()

    fun setMinAltitude(v: Float) {
        _minAltitude.value = v.coerceIn(0f, _maxAltitude.value)
    }
    fun setMaxAltitude(v: Float) {
        _maxAltitude.value = v.coerceIn(_minAltitude.value, 5000f)
    }

    // ── Auto altitude from map data toggle ─────────────────────
    private val _useAutoAltitude = MutableStateFlow(false)
    val useAutoAltitude: StateFlow<Boolean> = _useAutoAltitude.asStateFlow()

    fun setUseAutoAltitude(enabled: Boolean) {
        _useAutoAltitude.value = enabled
    }

    // ── Public API ────────────────────────────────────────────

    fun setMapCenter(latLng: LatLng) {
        _mapCenter.value = latLng
    }

    fun zoomIn() {
        _mapZoom.value = (_mapZoom.value + 1.0).coerceAtMost(19.0)
    }

    fun zoomOut() {
        _mapZoom.value = (_mapZoom.value - 1.0).coerceAtLeast(2.0)
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

        // Reset simulation state completely before starting,
        // otherwise if the route was previously completed (segmentIndex == totalSegments)
        // the while loop in runSimulation() exits immediately.
        _simulation.value = SimulationState(
            status = SimulationStatus.PLAYING,
            totalDistanceMeters = route.distanceMeters
        )
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
     * Core simulation loop: steps through route points, injecting mock locations
     * with natural speed variation between the selected min/max bounds.
     * Speed starts from DA (min), accelerates, and slows down on curves.
     * Speed is updated on every sub-step for smooth acceleration.
     */
    private suspend fun runSimulation(route: Route) {
        val points = route.points
        val totalSegments = points.size - 1
        val rng = java.util.Random()
        val curveLimits = calculateCurveSpeedLimits(points)

        var state = _simulation.value.copy(
            totalDistanceMeters = route.distanceMeters
        )

        val intervalMs = state.updateIntervalMs
        val intervalSec = intervalMs / 1000.0
        val accelPerStep = 8f * intervalSec.toFloat()     // km/h gained per sub-step
        val brakePerStep = 12f * intervalSec.toFloat()    // km/h lost per sub-step

        var currentSpeedKmh = _minSpeedKmh.value                // start from DA
        var targetSpeedKmh = currentSpeedKmh
        var msSinceTargetChange = 0L

        while (state.segmentIndex < totalSegments && state.status == SimulationStatus.PLAYING) {
            val from = points[state.segmentIndex]
            val to = points[state.segmentIndex + 1]
            val segDist = from.distanceTo(to)

            val speedMs = (currentSpeedKmh / 3.6).toDouble()

            // Number of sub-steps for this segment at current speed
            val stepsInSegment = if (speedMs > 0) {
                maxOf(1L, (segDist / (speedMs * intervalSec)).toLong())
            } else {
                1L
            }

            for (step in 0 until stepsInSegment) {
                if (state.status != SimulationStatus.PLAYING) break

                // ── Time-based target change (every 4-8 seconds) ──────
                msSinceTargetChange += intervalMs
                if (msSinceTargetChange >= 4000 + rng.nextInt(4001)) {
                    msSinceTargetChange = 0L
                    val boundMin = _minSpeedKmh.value
                    val boundMax = _maxSpeedKmh.value
                    val limitAhead = if (state.segmentIndex + 1 < curveLimits.size)
                        curveLimits[state.segmentIndex + 1] else Float.MAX_VALUE
                    val safeMax = minOf(boundMax, limitAhead)
                    targetSpeedKmh = if (safeMax > boundMin)
                        boundMin + rng.nextFloat() * (safeMax - boundMin)
                    else boundMin
                }

                // ── Smooth acceleration / braking on every sub-step ──
                val delta = if (currentSpeedKmh < targetSpeedKmh) accelPerStep else brakePerStep
                currentSpeedKmh = when {
                    currentSpeedKmh < targetSpeedKmh ->
                        minOf(currentSpeedKmh + delta, targetSpeedKmh)
                    currentSpeedKmh > targetSpeedKmh ->
                        maxOf(currentSpeedKmh - delta, targetSpeedKmh)
                    else -> currentSpeedKmh
                }
                // Never drop below the lower bound
                currentSpeedKmh = maxOf(currentSpeedKmh, _minSpeedKmh.value)

                // ── Position interpolation ────────────────────────────
                val t = if (stepsInSegment > 1) (step + 1).toDouble() / stepsInSegment else 1.0
                val position = from.interpolateTo(to, t)
                val bearing = from.bearingTo(to)

                // ── Altitude from sinusoidal profile or map data ────
                val overallT = (state.segmentIndex + t) / totalSegments
                val altitude = computeAltitude(overallT.toFloat(), route)

                // ── Update state ─────────────────────────────────────
                state = state.copy(
                    segmentIndex = state.segmentIndex,
                    segmentProgress = t.toFloat(),
                    overallProgress = overallT.toFloat(),
                    currentPosition = position,
                    currentBearing = bearing,
                    simulatedSpeedKmh = currentSpeedKmh,
                    currentAltitude = altitude,
                    elapsedSeconds = state.elapsedSeconds + (intervalMs / 1000L)
                )
                _simulation.value = state

                injectMockLocation(position, bearing, currentSpeedKmh, altitude)
                delay(intervalMs)
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
            val finalAlt = computeAltitude(1f, route)
            injectMockLocation(lastPos, finalBearing, 0f, finalAlt)
            state = state.copy(
                status = SimulationStatus.IDLE,
                currentPosition = lastPos,
                overallProgress = 1f,
                segmentIndex = totalSegments,
                segmentProgress = 1f,
                simulatedSpeedKmh = 0f,
                currentAltitude = finalAlt
            )
            _simulation.value = state
        }
    }

    /** Inject a mock location into Android's LocationManager. */
    private fun injectMockLocation(latLng: LatLng, bearing: Float, speedKmh: Float, altitude: Double) {
        val context = getApplication<Application>()
        try {
            val location = Location(LocationManager.GPS_PROVIDER).apply {
                latitude = latLng.latitude
                longitude = latLng.longitude
                this.altitude = altitude
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

    /** Compute altitude at given progress point.
     *  When auto-altitude is ON, it generates a natural terrain profile
     *  combining multiple sine waves for realistic feel.
     *  If real OSRM elevation data is available, it interpolates that instead.
     *  Otherwise it falls back to a sinusoidal oscillation between min/max. */
    private fun computeAltitude(overallProgress: Float, route: Route): Double {
        // Auto mode: use real OSRM elevation data if available
        if (_useAutoAltitude.value && route.hasElevation) {
            val idx = (overallProgress * (route.elevations.size - 1)).toInt()
                .coerceIn(0, route.elevations.size - 1)
            val frac = (overallProgress * (route.elevations.size - 1)) - idx
            val nextIdx = (idx + 1).coerceAtMost(route.elevations.size - 1)
            return route.elevations[idx] * (1.0 - frac) + route.elevations[nextIdx] * frac
        }
        val minAlt = _minAltitude.value.toDouble()
        val maxAlt = _maxAltitude.value.toDouble()
        if (minAlt >= maxAlt) return minAlt
        val amplitude = (maxAlt - minAlt) / 2.0
        val midpoint = (minAlt + maxAlt) / 2.0
        return if (_useAutoAltitude.value) {
            // Natural terrain: multi-frequency oscillation for realistic feel
            midpoint
                + amplitude * 0.6 * kotlin.math.sin(overallProgress * 2.0 * Math.PI + Math.PI / 2)
                + amplitude * 0.3 * kotlin.math.sin(overallProgress * 5.0 * Math.PI)
                + amplitude * 0.1 * kotlin.math.sin(overallProgress * 11.0 * Math.PI + Math.PI / 3)
        } else {
            // Manual: clean 2-cycle sine between DA/A
            midpoint + amplitude * kotlin.math.sin(overallProgress * 4.0 * Math.PI + Math.PI / 2)
        }
    }

    /**
     * Pre-compute speed limit for each route point based on corner sharpness.
     * Uses a running average of bearing changes over a 5-point window to
     * avoid noise from closely-spaced OSRM points.
     */
    private fun calculateCurveSpeedLimits(points: List<LatLng>): List<Float> {
        if (points.size < 5) return points.map { Float.MAX_VALUE }
        val limits = MutableList(points.size) { Float.MAX_VALUE }
        // Window = look 3 points ahead & behind to compute a smoothed bearing
        for (i in 3 until points.size - 3) {
            val before = points[i - 3].bearingTo(points[i])
            val after = points[i].bearingTo(points[i + 3])
            var diff = (after - before + 540f) % 360f - 180f
            val absAngle = kotlin.math.abs(diff)
            limits[i] = when {
                absAngle < 8f  -> Float.MAX_VALUE // straight
                absAngle < 15f -> 140f
                absAngle < 25f -> 100f
                absAngle < 35f -> 70f
                absAngle < 50f -> 45f
                absAngle < 70f -> 25f
                else           -> 15f
            }
        }
        return limits
    }
}
