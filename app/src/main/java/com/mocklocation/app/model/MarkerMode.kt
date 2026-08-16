package com.mocklocation.app.model

/** What a long-press on the map will place. */
enum class MarkerMode {
    /** The origin of the route. */
    START,

    /** The destination. */
    END,

    /** An intermediate stop, appended in order between start and end. */
    STOP;

    val label: String
        get() = when (this) {
            START -> "START"
            END -> "END"
            STOP -> "STOP"
        }

    /**
     * Placing START moves on to END, and END moves on to STOP so a route can be
     * built in one uninterrupted sequence of long-presses.
     */
    fun next(): MarkerMode = when (this) {
        START -> END
        END -> STOP
        STOP -> STOP
    }
}
