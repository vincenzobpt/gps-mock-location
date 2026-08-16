package com.mocklocation.app.data

import android.content.Context
import com.mocklocation.app.model.MapTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The handful of choices that must outlive the process.
 *
 * Deliberately not a full settings store: simulation tuning is per-session by
 * design, but a basemap preference the user has to re-pick on every launch is
 * just an annoyance.
 */
class Preferences(context: Context) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _mapTheme = MutableStateFlow(MapTheme.fromName(prefs.getString(KEY_MAP_THEME, null)))
    val mapTheme: StateFlow<MapTheme> = _mapTheme.asStateFlow()

    fun setMapTheme(theme: MapTheme) {
        _mapTheme.value = theme
        prefs.edit().putString(KEY_MAP_THEME, theme.name).apply()
    }

    private companion object {
        const val FILE = "mocklocation_prefs"
        const val KEY_MAP_THEME = "map_theme"
    }
}
