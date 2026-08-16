package com.mocklocation.app.model

/**
 * A single result from Nominatim geocoding.
 *
 * Nominatim returns a long comma-separated hierarchy; the UI shows the head as
 * the title and the tail as a dimmed subtitle rather than truncating blindly.
 */
data class SearchSuggestion(
    val displayName: String,
    val latitude: Double,
    val longitude: Double
) {
    private val parts: List<String> get() = displayName.split(",").map { it.trim() }

    /** Leading component — the place itself. */
    val shortName: String get() = parts.firstOrNull().orEmpty().ifEmpty { displayName }

    /** Remaining components, condensed to at most three levels of context. */
    val context: String
        get() = parts.drop(1)
            .filter { it.isNotEmpty() }
            .let { if (it.size > 3) it.takeLast(3) else it }
            .joinToString(", ")
}
