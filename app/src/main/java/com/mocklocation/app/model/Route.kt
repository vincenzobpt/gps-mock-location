package com.mocklocation.app.model

/** A parsed OSRM route consisting of a polyline path. */
data class Route(
    val points: List<LatLng>,
    val distanceMeters: Double,
    val durationSeconds: Double
) {
    val totalDistanceKm: Double get() = distanceMeters / 1000.0
    val totalDistanceFormatted: String
        get() = if (distanceMeters < 1000) "%.0f m".format(distanceMeters)
                else "%.2f km".format(totalDistanceKm)
    val durationFormatted: String
        get() {
            val h = (durationSeconds / 3600).toInt()
            val m = ((durationSeconds % 3600) / 60).toInt()
            return if (h > 0) "${h}h ${m}m" else "${m}m"
        }
}
