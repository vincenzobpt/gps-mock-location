package com.mocklocation.app.model

/**
 * A geographic coordinate (latitude, longitude).
 */
data class LatLng(
    val latitude: Double,
    val longitude: Double
) {
    /** Calculate approximate distance in meters using Haversine formula. */
    fun distanceTo(other: LatLng): Double {
        val R = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLon = Math.toRadians(other.longitude - longitude)
        val a = Math.sin(dLat / 2).pow(2) +
                Math.cos(Math.toRadians(latitude)) *
                Math.cos(Math.toRadians(other.latitude)) *
                Math.sin(dLon / 2).pow(2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }

    /** Linear interpolation toward another point by fraction [t] (0..1). */
    fun interpolateTo(other: LatLng, t: Double): LatLng {
        return LatLng(
            latitude = latitude + (other.latitude - latitude) * t,
            longitude = longitude + (other.longitude - longitude) * t
        )
    }

    /** Bearing in degrees from this point to another. */
    fun bearingTo(other: LatLng): Float {
        val dLon = Math.toRadians(other.longitude - longitude)
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val y = Math.sin(dLon) * Math.cos(lat2)
        val x = Math.cos(lat1) * Math.sin(lat2) -
                Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
        return (Math.toDegrees(Math.atan2(y, x)) + 360).toFloat() % 360f
    }
}

private fun Double.pow(exp: Int): Double {
    var result = 1.0
    repeat(exp) { result *= this }
    return result
}
