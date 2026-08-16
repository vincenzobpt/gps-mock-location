package com.mocklocation.app.model

/** How the altitude track is generated along the route. */
enum class AltitudeMode {
    /** Interpolate real elevation data from the routing engine, when present. */
    TERRAIN,
    /** Smooth oscillation between the manual min/max bounds. */
    MANUAL,
    /** Hold a single fixed altitude (min value). */
    FIXED
}

/** Tick rate presets for injected fixes. */
enum class FixRate(val intervalMs: Long, val label: String) {
    HZ_1(1000L, "1 Hz"),
    HZ_2(500L, "2 Hz"),
    HZ_5(200L, "5 Hz"),
    HZ_10(100L, "10 Hz");

    companion object {
        fun fromInterval(ms: Long): FixRate = entries.minByOrNull { kotlin.math.abs(it.intervalMs - ms) } ?: HZ_5
    }
}

/**
 * User-tunable simulation parameters. Immutable; the engine reads a fresh copy
 * on every tick so slider changes apply live without restarting the run.
 */
data class SimulationConfig(
    val minSpeedKmh: Float = 30f,
    val maxSpeedKmh: Float = 90f,
    val minAltitudeMeters: Float = 60f,
    val maxAltitudeMeters: Float = 320f,
    val altitudeMode: AltitudeMode = AltitudeMode.TERRAIN,
    val fixRate: FixRate = FixRate.HZ_5,
    /** Adds sub-metre random walk to each fix so the trace looks like a real receiver. */
    val gpsJitter: Boolean = true,
    /** Slow down for corners using pre-computed curvature limits. */
    val curveBraking: Boolean = true,
    /** Restart from the beginning when the destination is reached. */
    val loopRoute: Boolean = false,
    /** Reported horizontal accuracy in metres. */
    val accuracyMeters: Float = 3.5f,
) {
    val intervalMs: Long get() = fixRate.intervalMs

    /** Clamped, always-valid speed band. */
    val speedBand: ClosedFloatingPointRange<Float>
        get() = minSpeedKmh..maxOf(maxSpeedKmh, minSpeedKmh)

    val altitudeBand: ClosedFloatingPointRange<Float>
        get() = minAltitudeMeters..maxOf(maxAltitudeMeters, minAltitudeMeters)
}
