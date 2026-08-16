package com.mocklocation.app.model

/**
 * A saved route snapshot that can fully restore a simulation setup:
 * start/end points, waypoints, speed/altitude ranges, and mode toggles.
 */
data class RouteFavorite(
    val name: String,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
    /** JSON array string of waypoints [{"lat":..,"lng":..,"stay":..}]. */
    val waypointsJson: String? = null,
    val speedMin: Float = 0f,
    val speedMax: Float = 240f,
    val altMin: Float = 0f,
    val altMax: Float = 1000f,
    val useAutoAltitude: Boolean = false,
    val straightLine: Boolean = false,
    val closedRoute: Boolean = false,
    val gpsFluctuations: Boolean = true
)