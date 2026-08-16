package com.mocklocation.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mocklocation.app.MockLocationApp
import com.mocklocation.app.location.MockLocationEngine
import com.mocklocation.app.model.LatLng
import com.mocklocation.app.model.MarkerMode
import com.mocklocation.app.model.Route
import com.mocklocation.app.model.SearchSuggestion
import com.mocklocation.app.model.SimulationConfig
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

/**
 * Owns map/route/search state and forwards transport commands to the
 * process-wide `SimulationEngine`. The engine deliberately lives outside the
 * ViewModel so a run is not tied to this screen's lifetime.
 */
class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = MockLocationApp.engineOf(application)
    private val repository = OsrmRepository()

    // ── Simulation (delegated) ──────────────────────────────────
    val simulation = engine.state
    val config: StateFlow<SimulationConfig> = engine.config
    val providerStatus: StateFlow<MockLocationEngine.Status> = engine.providerStatus

    val elevationProfile: List<Double>? get() = engine.elevationProfile

    // ── Map / route state ───────────────────────────────────────
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

    // ── Camera ──────────────────────────────────────────────────

    /** One-shot camera instruction; [token] makes repeats of the same move fire. */
    sealed interface CameraCommand {
        val token: Long

        data class MoveTo(
            val point: LatLng,
            val zoom: Double?,
            override val token: Long
        ) : CameraCommand

        data class FitRoute(override val token: Long) : CameraCommand
    }

    private val _cameraCommand = MutableStateFlow<CameraCommand?>(null)
    val cameraCommand: StateFlow<CameraCommand?> = _cameraCommand.asStateFlow()

    private val _followVehicle = MutableStateFlow(true)
    val followVehicle: StateFlow<Boolean> = _followVehicle.asStateFlow()

    private val _nightMap = MutableStateFlow(true)
    val nightMap: StateFlow<Boolean> = _nightMap.asStateFlow()

    // ── Search ──────────────────────────────────────────────────
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchSuggestions = MutableStateFlow<List<SearchSuggestion>>(emptyList())
    val searchSuggestions: StateFlow<List<SearchSuggestion>> = _searchSuggestions.asStateFlow()

    private var searchJob: Job? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // ═══ Transport ═════════════════════════════════════════════

    /** Play/pause. Surfaces the precise reason when the platform refuses. */
    fun togglePlay() {
        if (_route.value == null) {
            _errorMessage.value = "Set two points and calculate a route first"
            return
        }
        when (val status = engine.toggle()) {
            is MockLocationEngine.Status.Ready,
            is MockLocationEngine.Status.Idle -> _errorMessage.value = null
            is MockLocationEngine.Status.MissingLocationPermission ->
                _errorMessage.value = "Location permission is required to inject fixes"
            is MockLocationEngine.Status.NotSelectedAsMockApp ->
                _errorMessage.value = "Not selected as mock location app"
            is MockLocationEngine.Status.Failed ->
                _errorMessage.value = "Mock provider failed: ${status.reason}"
        }
    }

    fun stopSimulation() = engine.stop()

    fun seekTo(fraction: Float) = engine.seekTo(fraction)

    fun updateConfig(transform: (SimulationConfig) -> SimulationConfig) =
        engine.updateConfig(transform)

    /** Re-arms the providers after the user fixes permissions in Settings. */
    fun refreshProviderStatus() {
        if (!simulation.value.isActive) return
        engine.play()
    }

    // ═══ Map interaction ═══════════════════════════════════════

    fun onMapLongPress(latLng: LatLng) {
        when (_markerMode.value) {
            MarkerMode.START -> {
                _startPoint.value = latLng
                _markerMode.value = MarkerMode.END
            }
            MarkerMode.END -> {
                _endPoint.value = latLng
                _markerMode.value = MarkerMode.START
            }
        }
        _errorMessage.value = null
        val start = _startPoint.value ?: return
        val end = _endPoint.value ?: return
        fetchRoute(start, end)
    }

    fun setMarkerMode(mode: MarkerMode) {
        _markerMode.value = mode
    }

    fun cycleMarkerMode() {
        _markerMode.value =
            if (_markerMode.value == MarkerMode.START) MarkerMode.END else MarkerMode.START
    }

    fun calculateRoute() {
        val start = _startPoint.value
        val end = _endPoint.value
        if (start == null || end == null) {
            _errorMessage.value = "Long-press the map to drop both points"
            return
        }
        fetchRoute(start, end)
    }

    fun swapEndpoints() {
        val start = _startPoint.value
        val end = _endPoint.value
        if (start == null || end == null) return
        _startPoint.value = end
        _endPoint.value = start
        fetchRoute(end, start)
    }

    fun clearAll() {
        engine.stop()
        engine.setRoute(null)
        _startPoint.value = null
        _endPoint.value = null
        _route.value = null
        _errorMessage.value = null
        _markerMode.value = MarkerMode.START
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun setFollowVehicle(enabled: Boolean) {
        _followVehicle.value = enabled
    }

    fun toggleNightMap() {
        _nightMap.value = !_nightMap.value
    }

    /** Frames the whole route; used by the "fit" control and after routing. */
    fun requestFitRoute() {
        if (_route.value?.points.isNullOrEmpty()) return
        _followVehicle.value = false
        _cameraCommand.value = CameraCommand.FitRoute(System.nanoTime())
    }

    fun recenterOnVehicle() {
        val pos = simulation.value.position ?: _startPoint.value ?: return
        _followVehicle.value = true
        moveCamera(pos, zoom = 17.0)
    }

    private fun moveCamera(point: LatLng, zoom: Double?) {
        _cameraCommand.value = CameraCommand.MoveTo(point, zoom, System.nanoTime())
    }

    // ═══ Routing ═══════════════════════════════════════════════

    private fun fetchRoute(start: LatLng, end: LatLng) {
        viewModelScope.launch {
            _isCalculatingRoute.value = true
            _errorMessage.value = null
            repository.getRoute(start, end).fold(
                onSuccess = { route ->
                    _route.value = route
                    engine.setRoute(route)
                    requestFitRoute()
                },
                onFailure = { e ->
                    _route.value = null
                    engine.setRoute(null)
                    _errorMessage.value = "Routing failed: ${e.localizedMessage ?: "unknown error"}"
                }
            )
            _isCalculatingRoute.value = false
        }
    }

    // ═══ Search ════════════════════════════════════════════════

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchSuggestions.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(320)
            _isSearching.value = true
            runCatching { geocode(query.trim(), limit = 6) }
                .onSuccess { _searchSuggestions.value = it }
            _isSearching.value = false
        }
    }

    fun searchExplicit(query: String) {
        if (query.isBlank()) return
        searchJob?.cancel()
        viewModelScope.launch {
            _isSearching.value = true
            _errorMessage.value = null
            runCatching { geocode(query.trim(), limit = 1) }
                .onSuccess { results ->
                    val hit = results.firstOrNull()
                    if (hit == null) {
                        _errorMessage.value = "No match for \"$query\""
                    } else {
                        selectSuggestion(hit)
                    }
                }
                .onFailure { _errorMessage.value = "Search failed: ${it.localizedMessage}" }
            _isSearching.value = false
        }
    }

    fun selectSuggestion(suggestion: SearchSuggestion) {
        _followVehicle.value = false
        moveCamera(LatLng(suggestion.latitude, suggestion.longitude), zoom = 15.5)
        _searchQuery.value = suggestion.shortName
        _searchSuggestions.value = emptyList()
    }

    fun clearSuggestions() {
        _searchSuggestions.value = emptyList()
    }

    private suspend fun geocode(query: String, limit: Int): List<SearchSuggestion> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url("https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=$limit")
                .header("User-Agent", "MockLocation/2.0 (Android)")
                .build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrBlank()) return@use emptyList()
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
        }
}
