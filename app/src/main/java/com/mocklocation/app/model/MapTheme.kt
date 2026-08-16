package com.mocklocation.app.model

/** Basemap appearance. Applies to the map tiles only; the cockpit UI is always dark. */
enum class MapTheme {
    LIGHT,
    DARK,

    /** Track the device's light/dark setting. */
    SYSTEM;

    val label: String
        get() = when (this) {
            LIGHT -> "Light"
            DARK -> "Dark"
            SYSTEM -> "System"
        }

    /** Resolves to the concrete style, given whether the system is currently dark. */
    fun isDark(systemInDarkMode: Boolean): Boolean = when (this) {
        LIGHT -> false
        DARK -> true
        SYSTEM -> systemInDarkMode
    }

    companion object {
        fun fromName(name: String?): MapTheme =
            entries.firstOrNull { it.name == name } ?: DARK
    }
}
