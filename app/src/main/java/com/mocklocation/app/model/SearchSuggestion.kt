package com.mocklocation.app.model

/**
 * A single result from Nominatim geocoding.
 */
data class SearchSuggestion(
    val displayName: String,
    val latitude: Double,
    val longitude: Double
)
