package com.mocklocation.app.simulation

import android.content.Context
import com.mocklocation.app.location.MockLocationEngine
import com.mocklocation.app.model.AltitudeMode
import com.mocklocation.app.model.LatLng
import com.mocklocation.app.model.Route
import com.mocklocation.app.model.SimulationConfig
import com.mocklocation.app.model.SimulationState
import com.mocklocation.app.model.SimulationStatus
import com.mocklocation.app.model.Waypoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The simulation clock. Lives on the Application, not on a ViewModel, so the
 * run survives configuration changes and — paired with `MockLocationService` —
 * keeps ticking while the user is in the app they actually want to fool.
 *
 * Motion is a proper time integration: every tick advances `distanceMeters` by
 * `v * dt` and derives the fix from the route's arc-length parameterisation.
 */
class SimulationEngine(
    private val appContext: Context,
    val mockLocation: MockLocationEngine = MockLocationEngine(appContext),
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _state = MutableStateFlow(SimulationState())
    val state: StateFlow<SimulationState> = _state.asStateFlow()

    private val _config = MutableStateFlow(SimulationConfig())
    val config: StateFlow<SimulationConfig> = _config.asStateFlow()

    private val _providerStatus = MutableStateFlow<MockLocationEngine.Status>(MockLocationEngine.Status.Idle)
    val providerStatus: StateFlow<MockLocationEngine.Status> = _providerStatus.asStateFlow()

    /** Emitted when the engine wants the host to start/stop the foreground service. */
    var onRunningChanged: ((Boolean) -> Unit)? = null

    private var geometry: RouteGeometry? = null
    private val random = Random(System.nanoTime())

    // Integrator state — survives pause so resume continues mid-route.
    private var distanceMeters = 0.0
    private var speedMs = 0.0
    private var targetSpeedMs = 0.0

    /** Countdown to the next cruising-target re-roll; 0 means "pick one now". */
    private var msUntilTargetPick = 0L
    private var jitterLat = 0.0
    private var jitterLon = 0.0
    private var satellites = 11
    private var elapsedMs = 0L
    private var fixCount = 0L

    /** Intermediate stops, resolved to arc length along the route. */
    private var stops: List<Stop> = emptyList()
    private var nextStopIndex = 0
    private var dwellRemainingMs = 0L

    private data class Stop(
        /** Index into the caller's waypoint list, so rows and pins can be matched. */
        val waypointIndex: Int,
        val distanceMeters: Double,
        val dwellSeconds: Long,
    )

    // ── Configuration ───────────────────────────────────────────

    fun updateConfig(transform: (SimulationConfig) -> SimulationConfig) {
        _config.value = transform(_config.value).sanitised()
    }

    /**
     * Drops test providers left registered by a previous process.
     *
     * A crash or a force-stop gives the app no chance to tear down, and the
     * device then keeps serving the last injected fix as if it were real. This
     * runs once at process start, when nothing can be simulating yet.
     */
    fun clearStaleProviders() {
        if (_state.value.status == SimulationStatus.IDLE) mockLocation.stop()
    }

    /** Installs a new route and its intermediate stops. Resets progress. */
    fun setRoute(route: Route?, waypoints: List<Waypoint> = emptyList()) {
        stop()
        val geo = route?.let(::RouteGeometry)?.takeIf { it.isUsable }
        geometry = geo

        stops = if (geo == null) emptyList() else buildStops(geo, waypoints)

        _state.value = SimulationState(
            totalDistanceMeters = geo?.totalMeters ?: 0.0,
            updateIntervalMs = _config.value.intervalMs,
        )
    }

    /**
     * Snaps each waypoint onto the geometry OSRM returned and orders the result
     * by arc length: the simulation can only meet stops in the order it drives
     * past them. Drive-through waypoints (dwell 0) are dropped here, which is
     * why each [Stop] carries the index it came from — without it the UI would
     * be matching row 0 against a stop that belongs to a different waypoint.
     */
    private fun buildStops(geo: RouteGeometry, waypoints: List<Waypoint>): List<Stop> =
        waypoints
            .mapIndexed { index, wp ->
                Stop(index, geo.arcLengthNearest(wp.latLng), wp.stayDurationSeconds)
            }
            .filter { it.dwellSeconds > 0 }
            .sortedBy { it.distanceMeters }

    /** Waypoint indices whose stop has already been served. */
    private fun servedWaypoints(): Set<Int> =
        stops.take(nextStopIndex).mapTo(mutableSetOf()) { it.waypointIndex }

    /**
     * Applies new dwell durations in place.
     *
     * Changing how long the simulation waits somewhere does not move the route,
     * so this must not go through [setRoute] — that begins with [stop], which
     * would tear down the providers and rewind a run already in progress.
     */
    fun updateStopDwells(waypoints: List<Waypoint>) {
        val geo = geometry ?: return
        stops = buildStops(geo, waypoints)
        // Whatever lies behind us stays served, whatever its new duration.
        nextStopIndex = stops.count { it.distanceMeters <= distanceMeters }
        _state.value = _state.value.copy(
            stopsTotal = stops.size,
            servedWaypoints = servedWaypoints(),
        )
    }

    /** Arc-length positions of the stops, as fractions of the route. */
    val stopFractions: List<Float>
        get() {
            val total = geometry?.totalMeters ?: return emptyList()
            if (total <= 0.0) return emptyList()
            return stops.map { (it.distanceMeters / total).toFloat().coerceIn(0f, 1f) }
        }

    val elevationProfile: List<Double>?
        get() = geometry?.elevationProfile(PROFILE_SAMPLES)

    val hasRoute: Boolean get() = geometry != null

    // ── Transport controls ──────────────────────────────────────

    /**
     * Starts or resumes. Returns the provider status so the caller can show the
     * exact reason when injection is not possible.
     */
    fun play(): MockLocationEngine.Status {
        // Not a provider problem, so it must not land in providerStatus — that
        // drives the "mock providers lost" banner.
        val geo = geometry ?: return MockLocationEngine.Status.Failed("No route to simulate")

        val providerStatus = mockLocation.start()
        _providerStatus.value = providerStatus
        if (providerStatus !is MockLocationEngine.Status.Ready) return providerStatus

        // Finished run → rewind instead of dead-ending on the last fix.
        if (distanceMeters >= geo.totalMeters - 0.5) reset()

        job?.cancel()
        _state.value = _state.value.copy(
            status = SimulationStatus.PLAYING,
            totalDistanceMeters = geo.totalMeters,
            activeProviders = providerStatus.providers,
            injectionError = null,
        )
        onRunningChanged?.invoke(true)
        job = scope.launch { runLoop(geo) }
        return providerStatus
    }

    fun pause() {
        job?.cancel()
        job = null
        _state.value = _state.value.copy(status = SimulationStatus.PAUSED, speedKmh = 0f)
        onRunningChanged?.invoke(false)
    }

    fun stop() {
        job?.cancel()
        job = null
        mockLocation.stop()
        _providerStatus.value = MockLocationEngine.Status.Idle
        reset()
        _state.value = SimulationState(
            totalDistanceMeters = geometry?.totalMeters ?: 0.0,
            updateIntervalMs = _config.value.intervalMs,
        )
        onRunningChanged?.invoke(false)
    }

    fun toggle(): MockLocationEngine.Status = when (_state.value.status) {
        SimulationStatus.PLAYING -> {
            pause()
            _providerStatus.value
        }
        else -> play()
    }

    /** Jump to a fraction of the route without changing the transport state. */
    fun seekTo(fraction: Float) {
        val geo = geometry ?: return
        distanceMeters = (geo.totalMeters * fraction.coerceIn(0f, 1f))
        speedMs = 0.0
        dwellRemainingMs = 0L
        // Stops behind the new position are already served.
        nextStopIndex = stops.count { it.distanceMeters <= distanceMeters }
        publish(geo, accelerationMs2 = 0f)
    }

    private fun reset() {
        distanceMeters = 0.0
        speedMs = 0.0
        targetSpeedMs = 0.0
        msUntilTargetPick = 0L
        elapsedMs = 0L
        fixCount = 0L
        jitterLat = 0.0
        jitterLon = 0.0
        satellites = 11
        nextStopIndex = 0
        dwellRemainingMs = 0L
    }

    // ── The loop ────────────────────────────────────────────────

    private suspend fun runLoop(geo: RouteGeometry) {
        while (coroutineContext.isActive && _state.value.status == SimulationStatus.PLAYING) {
            val cfg = _config.value
            val dtMs = cfg.intervalMs
            val dt = dtMs / 1000.0

            val previousSpeed = speedMs

            if (dwellRemainingMs > 0L) {
                // Standing at a stop. Fixes keep flowing — a receiver that goes
                // silent while parked is exactly what a real one does not do.
                dwellRemainingMs -= dtMs
                speedMs = 0.0
                elapsedMs += dtMs
                publish(geo, accelerationMs2 = 0f)
                inject()
                delay(dtMs)
                continue
            }

            step(geo, cfg, dt, dtMs)
            val accel = ((speedMs - previousSpeed) / dt).toFloat()

            // Reached the next stop?
            stops.getOrNull(nextStopIndex)?.let { stop ->
                if (distanceMeters >= stop.distanceMeters) {
                    distanceMeters = stop.distanceMeters
                    speedMs = 0.0
                    dwellRemainingMs = stop.dwellSeconds * 1000L
                    nextStopIndex++
                }
            }

            publish(geo, accel)
            inject()

            if (distanceMeters >= geo.totalMeters - 0.01) {
                if (cfg.loopRoute) {
                    distanceMeters = 0.0
                    nextStopIndex = 0
                } else {
                    finish(geo)
                    return
                }
            }
            delay(dtMs)
        }
    }

    /** One integration step: pick a target, ease toward it, advance. */
    private fun step(geo: RouteGeometry, cfg: SimulationConfig, dt: Double, dtMs: Long) {
        val minMs = cfg.minSpeedKmh / 3.6
        val maxMs = max(cfg.maxSpeedKmh / 3.6, minMs)

        // Curvature ceiling, looking far enough ahead to brake in time.
        val lookAhead = max(40.0, speedMs * 4.0)
        val curveMs = if (cfg.curveBraking) {
            val limitKmh = geo.speedLimitAhead(distanceMeters, lookAhead)
            if (limitKmh == Float.MAX_VALUE) maxMs else (limitKmh / 3.6).toDouble()
        } else maxMs

        // Arrival ceiling: v = sqrt(2 * a * remaining) so we coast to a halt
        // exactly on the next thing we must stop at — an intermediate stop if
        // there is one ahead, otherwise the destination.
        val nextHalt = stops.getOrNull(nextStopIndex)?.distanceMeters ?: geo.totalMeters
        val remaining = (nextHalt - distanceMeters).coerceAtLeast(0.0)
        val arrivalMs = if (cfg.loopRoute && nextStopIndex >= stops.size) maxMs
        else sqrt(2.0 * BRAKE_MS2 * remaining)

        val ceiling = min(min(maxMs, curveMs), arrivalMs)

        // Re-roll the cruising target every few seconds for organic variation.
        // The countdown starts at zero so a fresh run picks a target on its very
        // first tick: waiting for the first hold period to elapse left the
        // vehicle parked at 0 km/h for 3.5-8 s after every DRIVE. Counting down
        // to a threshold drawn once also gives the intended hold distribution,
        // where re-drawing it on each comparison did not.
        msUntilTargetPick -= dtMs
        if (msUntilTargetPick <= 0L) {
            msUntilTargetPick = (TARGET_HOLD_MS_MIN + random.nextInt(TARGET_HOLD_MS_SPAN)).toLong()
            val lo = min(minMs, ceiling)
            targetSpeedMs = lo + random.nextDouble() * (max(ceiling, lo) - lo)
        }
        val target = targetSpeedMs.coerceIn(0.0, ceiling)

        // Ease toward the target with asymmetric accel/brake rates.
        speedMs = when {
            speedMs < target -> min(speedMs + ACCEL_MS2 * dt, target)
            speedMs > target -> max(speedMs - BRAKE_MS2 * dt, target)
            else -> speedMs
        }.coerceAtLeast(0.0)

        distanceMeters = (distanceMeters + speedMs * dt).coerceAtMost(geo.totalMeters)
        elapsedMs += dtMs

        // Receiver-side telemetry drift: satellites wander, jitter random-walks.
        if (random.nextInt(20) == 0) {
            satellites = (satellites + random.nextInt(-1, 2)).coerceIn(7, 14)
        }
        if (cfg.gpsJitter) {
            val decay = 0.82
            val amplitude = cfg.accuracyMeters / 3.0 / 111_320.0
            jitterLat = jitterLat * decay + random.nextDouble(-1.0, 1.0) * amplitude * (1 - decay)
            jitterLon = jitterLon * decay + random.nextDouble(-1.0, 1.0) * amplitude * (1 - decay)
        } else {
            jitterLat = 0.0
            jitterLon = 0.0
        }
    }

    private fun publish(geo: RouteGeometry, accelerationMs2: Float) {
        val cfg = _config.value
        val truePos = geo.positionAt(distanceMeters)
        val injected = if (cfg.gpsJitter) {
            LatLng(
                truePos.latitude + jitterLat,
                truePos.longitude + jitterLon / cos(Math.toRadians(truePos.latitude)).coerceAtLeast(0.05)
            )
        } else truePos

        val hdop = (0.7f + (14 - satellites) * 0.11f).coerceIn(0.6f, 2.4f)

        _state.value = _state.value.copy(
            position = truePos,
            injectedPosition = injected,
            bearingDegrees = geo.headingAt(distanceMeters),
            altitudeMeters = altitudeAt(geo, cfg),
            accuracyMeters = cfg.accuracyMeters * (hdop / 1.2f),
            speedKmh = (speedMs * 3.6).toFloat(),
            targetSpeedKmh = (targetSpeedMs * 3.6).toFloat(),
            curveLimitKmh = geo.speedLimitAhead(distanceMeters, max(40.0, speedMs * 4.0))
                .let { if (it == Float.MAX_VALUE) cfg.maxSpeedKmh else it },
            accelerationMs2 = accelerationMs2,
            distanceTraveledMeters = distanceMeters,
            totalDistanceMeters = geo.totalMeters,
            pointIndex = geo.indexAt(distanceMeters),
            elapsedMillis = elapsedMs,
            stopsTotal = stops.size,
            servedWaypoints = servedWaypoints(),
            activeStopWaypoint = if (dwellRemainingMs > 0L) {
                stops.getOrNull(nextStopIndex - 1)?.waypointIndex
            } else null,
            dwellRemainingSeconds = (dwellRemainingMs + 999L) / 1000L,
            fixCount = fixCount,
            updateIntervalMs = cfg.intervalMs,
            satellitesInView = (satellites + 6).coerceAtMost(24),
            satellitesUsed = satellites,
            hdop = hdop,
            activeProviders = mockLocation.providers,
        )
    }

    private fun inject() {
        val s = _state.value
        val pos = s.injectedPosition ?: return
        val error = mockLocation.push(
            MockLocationEngine.Fix(
                latitude = pos.latitude,
                longitude = pos.longitude,
                altitudeMeters = s.altitudeMeters,
                bearingDegrees = s.bearingDegrees,
                speedMs = s.speedMs,
                accuracyMeters = s.accuracyMeters,
                satellitesUsed = s.satellitesUsed,
            )
        )
        fixCount++
        _state.value = _state.value.copy(
            fixCount = fixCount,
            lastFixWallClockMs = System.currentTimeMillis(),
            injectionError = error,
        )
        if (error != null && mockLocation.status !is MockLocationEngine.Status.Ready) {
            // The registration is gone, not merely a dropped fix. Continuing
            // would report a live run that reaches no consumer at all.
            _providerStatus.value = mockLocation.status
            pause()
        }
    }

    private fun finish(geo: RouteGeometry) {
        distanceMeters = geo.totalMeters
        speedMs = 0.0
        publish(geo, accelerationMs2 = 0f)
        // Tear the providers down on arrival: leaving them registered would keep
        // the device pinned to the last injected fix until the next reboot.
        mockLocation.stop()
        _providerStatus.value = MockLocationEngine.Status.Idle
        _state.value = _state.value.copy(
            status = SimulationStatus.IDLE,
            speedKmh = 0f,
            activeProviders = emptyList(),
        )
        onRunningChanged?.invoke(false)
    }

    private fun altitudeAt(geo: RouteGeometry, cfg: SimulationConfig): Double {
        val lo = cfg.minAltitudeMeters.toDouble()
        val hi = max(cfg.maxAltitudeMeters.toDouble(), lo)
        return when (cfg.altitudeMode) {
            AltitudeMode.FIXED -> lo
            AltitudeMode.TERRAIN -> geo.elevationAt(distanceMeters)
                ?: syntheticTerrain(geo, lo, hi)
            AltitudeMode.MANUAL -> syntheticTerrain(geo, lo, hi)
        }
    }

    /** Multi-octave sine so manual mode still feels like ground, not a metronome. */
    private fun syntheticTerrain(geo: RouteGeometry, lo: Double, hi: Double): Double {
        if (hi - lo < 0.5) return lo
        val t = if (geo.totalMeters > 0) distanceMeters / geo.totalMeters else 0.0
        val amp = (hi - lo) / 2.0
        val mid = (hi + lo) / 2.0
        return mid +
            amp * 0.62 * sin(t * 2.0 * Math.PI - Math.PI / 2) +
            amp * 0.26 * sin(t * 5.0 * Math.PI) +
            amp * 0.12 * sin(t * 11.0 * Math.PI + Math.PI / 3)
    }

    private fun SimulationConfig.sanitised(): SimulationConfig = copy(
        minSpeedKmh = minSpeedKmh.coerceIn(0f, MAX_SPEED_KMH),
        maxSpeedKmh = maxSpeedKmh.coerceIn(minSpeedKmh.coerceIn(0f, MAX_SPEED_KMH), MAX_SPEED_KMH),
        minAltitudeMeters = minAltitudeMeters.coerceIn(MIN_ALT_M, MAX_ALT_M),
        maxAltitudeMeters = maxAltitudeMeters.coerceIn(minAltitudeMeters, MAX_ALT_M),
        accuracyMeters = accuracyMeters.coerceIn(1f, 50f),
    )

    companion object {
        const val MAX_SPEED_KMH = 300f
        const val MIN_ALT_M = -400f
        const val MAX_ALT_M = 8000f

        private const val ACCEL_MS2 = 2.2
        private const val BRAKE_MS2 = 3.4
        private const val TARGET_HOLD_MS_MIN = 3500
        private const val TARGET_HOLD_MS_SPAN = 4500
        private const val PROFILE_SAMPLES = 96
    }
}
