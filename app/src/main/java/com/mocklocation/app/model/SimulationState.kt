package com.mocklocation.app.model

import kotlin.math.abs
import kotlin.math.roundToInt

/** Lifecycle of the simulation loop. */
enum class SimulationStatus { IDLE, PLAYING, PAUSED }

/**
 * Everything the UI (and the notification) needs to render one instant of the
 * simulation. Produced by `SimulationEngine` at the GPS tick rate.
 *
 * The single source of truth for progress is [distanceTraveledMeters] — an
 * arc-length along the route, not a segment index — so pause/resume, speed
 * changes and variable tick rates all stay consistent.
 */
data class SimulationState(
    val status: SimulationStatus = SimulationStatus.IDLE,

    // ── Position fix ────────────────────────────────────────────
    val position: LatLng? = null,
    /** Position actually injected, including GPS jitter (may differ from [position]). */
    val injectedPosition: LatLng? = null,
    val bearingDegrees: Float = 0f,
    val altitudeMeters: Double = 0.0,
    val accuracyMeters: Float = 3f,

    // ── Kinematics ──────────────────────────────────────────────
    val speedKmh: Float = 0f,
    val targetSpeedKmh: Float = 0f,
    /** Speed cap imposed by the upcoming curve, in km/h. */
    val curveLimitKmh: Float = 0f,
    val accelerationMs2: Float = 0f,

    // ── Route progress ──────────────────────────────────────────
    val distanceTraveledMeters: Double = 0.0,
    val totalDistanceMeters: Double = 0.0,
    /** Index of the last route vertex passed — used to split the polyline. */
    val pointIndex: Int = 0,
    val elapsedMillis: Long = 0L,

    // ── Intermediate stops ──────────────────────────────────────
    val stopsTotal: Int = 0,
    /**
     * Indices into the caller's waypoint list whose stop has been served.
     *
     * A count would not do: the engine drops drive-through waypoints and orders
     * the rest by arc length, so its Nth stop is not the user's Nth waypoint.
     */
    val servedWaypoints: Set<Int> = emptySet(),
    /** Waypoint index currently being stood at, or null when moving. */
    val activeStopWaypoint: Int? = null,
    /** Seconds left standing at the current stop; 0 when moving. */
    val dwellRemainingSeconds: Long = 0L,

    // ── Fix / receiver telemetry ("nerd info") ──────────────────
    val fixCount: Long = 0L,
    val updateIntervalMs: Long = 200L,
    val satellitesInView: Int = 0,
    val satellitesUsed: Int = 0,
    val hdop: Float = 0f,
    /** Providers currently receiving injected fixes (gps, network, fused). */
    val activeProviders: List<String> = emptyList(),
    val lastFixWallClockMs: Long = 0L,
    /** Non-null when the platform refused the last injection. */
    val injectionError: String? = null,
) {
    val isActive: Boolean get() = status != SimulationStatus.IDLE

    /** Standing at an intermediate stop rather than driving. */
    val isDwelling: Boolean get() = dwellRemainingSeconds > 0L

    val progress: Float
        get() = if (totalDistanceMeters > 0.0)
            (distanceTraveledMeters / totalDistanceMeters).toFloat().coerceIn(0f, 1f)
        else 0f

    val remainingMeters: Double
        get() = (totalDistanceMeters - distanceTraveledMeters).coerceAtLeast(0.0)

    val speedMs: Float get() = speedKmh / 3.6f

    val elapsedSeconds: Long get() = elapsedMillis / 1000L

    /** Seconds to destination at the current speed; null while stopped. */
    val etaSeconds: Long?
        get() {
            val v = speedMs
            return if (v > 0.3f) (remainingMeters / v).roundToInt().toLong() else null
        }

    val updateRateHz: Float
        get() = if (updateIntervalMs > 0) 1000f / updateIntervalMs else 0f

    /** 16-point compass abbreviation for [bearingDegrees]. */
    val cardinal: String
        get() {
            val dirs = arrayOf(
                "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
            )
            val idx = (((bearingDegrees % 360f) + 360f) % 360f / 22.5f).roundToInt() % 16
            return dirs[idx]
        }

    /** Rough vertical rate in m/s is not tracked; expose slope sign instead. */
    val isAccelerating: Boolean get() = accelerationMs2 > 0.05f
    val isBraking: Boolean get() = accelerationMs2 < -0.05f
    val isCruising: Boolean get() = abs(accelerationMs2) <= 0.05f
}
