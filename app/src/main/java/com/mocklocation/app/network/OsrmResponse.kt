package com.mocklocation.app.network

import com.google.gson.annotations.SerializedName

/** OSRM API response wrapper. */
data class OsrmResponse(
    val code: String,
    val routes: List<OsrmRoute>?
)

data class OsrmRoute(
    val geometry: OsrmGeometry,
    val distance: Double,
    val duration: Double
)

data class OsrmGeometry(
    val coordinates: List<List<Double>> // [[lng, lat, ?elevation], …]
)
