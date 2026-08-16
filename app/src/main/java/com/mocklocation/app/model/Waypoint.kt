package com.mocklocation.app.model

/** An intermediate stop along a route, with a configurable dwell time. */
data class Waypoint(
    val latLng: LatLng,
    /** How long the simulation stands still here, in seconds. */
    val stayDurationSeconds: Long = 10L,
) {
    companion object {
        /** Dwell presets offered in the console, in seconds. */
        val DWELL_STEPS = listOf(0L, 5L, 10L, 30L, 60L, 120L, 300L)

        fun formatDwell(seconds: Long): String = when {
            seconds <= 0 -> "pass"
            seconds < 60 -> "${seconds}s"
            seconds % 60 == 0L -> "${seconds / 60}m"
            else -> "${seconds / 60}m${seconds % 60}s"
        }
    }

    /** Next preset in the cycle, so one tap steps the dwell time. */
    fun cycleDwell(): Waypoint {
        val index = DWELL_STEPS.indexOf(stayDurationSeconds)
        val next = DWELL_STEPS[(if (index < 0) 0 else index + 1) % DWELL_STEPS.size]
        return copy(stayDurationSeconds = next)
    }
}
