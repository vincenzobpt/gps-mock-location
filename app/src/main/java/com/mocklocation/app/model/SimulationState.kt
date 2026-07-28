package com.mocklocation.app.model

/** Current simulation state. */
enum class SimulationStatus { IDLE, PLAYING, PAUSED }

data class SimulationState(
    val status: SimulationStatus = SimulationStatus.IDLE,
    /** Index into the route points currently being approached. */
    val segmentIndex: Int = 0,
    /** Sub-segment progress 0..1 within the current segment. */
    val segmentProgress: Float = 0f,
    /** Overall progress 0..1 along the full route. */
    val overallProgress: Float = 0f,
    /** Current simulated position (may be null before first tick). */
    val currentPosition: LatLng? = null,
    /** Current bearing in degrees. */
    val currentBearing: Float = 0f,
    /** Target simulation speed in km/h (chosen via range slider). */
    val targetSpeedKmh: Float = 40f,
    /** Current speed in km/h being simulated. */
    val simulatedSpeedKmh: Float = 0f,
    /** Current altitude in meters (simulated from min/max range). */
    val currentAltitude: Double = 0.0,
    /** GPS update interval in milliseconds. */
    val updateIntervalMs: Long = 200L,
    /** Elapsed simulation time in seconds. */
    val elapsedSeconds: Long = 0L,
    /** Total route distance in meters. */
    val totalDistanceMeters: Double = 0.0
)
