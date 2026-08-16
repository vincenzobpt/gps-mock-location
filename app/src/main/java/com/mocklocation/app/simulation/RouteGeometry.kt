package com.mocklocation.app.simulation

import com.mocklocation.app.model.LatLng
import com.mocklocation.app.model.Route
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pre-computed arc-length parameterisation of a route.
 *
 * The simulation advances a single scalar — metres travelled — and asks this
 * class for the position, heading and speed ceiling at that distance. That is
 * what makes the motion physically consistent: the old segment-index loop
 * derived step counts from the speed at segment entry, so a speed change
 * mid-segment silently changed how far the marker actually moved.
 */
class RouteGeometry(route: Route) {

    val points: List<LatLng> = route.points

    /** cumulative[i] = metres from the start of the route to points[i]. */
    private val cumulative: DoubleArray = DoubleArray(points.size)

    /** Heading of the segment leaving points[i]. */
    private val headings: FloatArray = FloatArray(maxOf(points.size - 1, 1))

    /** Speed ceiling in km/h imposed by curvature at points[i]. */
    private val curveLimits: FloatArray = FloatArray(points.size) { Float.MAX_VALUE }

    private val elevations: List<Double> = route.elevations

    val totalMeters: Double

    init {
        var acc = 0.0
        for (i in 1 until points.size) {
            acc += points[i - 1].distanceTo(points[i])
            cumulative[i] = acc
        }
        totalMeters = acc

        for (i in 0 until points.size - 1) {
            headings[i] = points[i].bearingTo(points[i + 1])
        }

        computeCurveLimits()
    }

    val hasElevation: Boolean get() = elevations.size == points.size && elevations.isNotEmpty()

    val isUsable: Boolean get() = points.size >= 2 && totalMeters > 1.0

    // ── Queries ─────────────────────────────────────────────────

    /** Index of the last vertex at or before [meters]. */
    fun indexAt(meters: Double): Int {
        if (points.size < 2) return 0
        val d = meters.coerceIn(0.0, totalMeters)
        var lo = 0
        var hi = points.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (cumulative[mid] <= d) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** Interpolated position at [meters] along the route. */
    fun positionAt(meters: Double): LatLng {
        if (points.isEmpty()) return LatLng(0.0, 0.0)
        if (points.size == 1) return points[0]
        val d = meters.coerceIn(0.0, totalMeters)
        val i = indexAt(d)
        if (i >= points.lastIndex) return points.last()
        val segLen = cumulative[i + 1] - cumulative[i]
        val t = if (segLen > 0.0) (d - cumulative[i]) / segLen else 0.0
        return points[i].interpolateTo(points[i + 1], t)
    }

    /** Heading in degrees at [meters], smoothed across the segment boundary. */
    fun headingAt(meters: Double): Float {
        if (headings.isEmpty()) return 0f
        val i = indexAt(meters).coerceAtMost(headings.lastIndex)
        return headings[i]
    }

    /**
     * Altitude from real elevation data, interpolated by arc length.
     * Returns null when the route carries no elevation profile.
     */
    fun elevationAt(meters: Double): Double? {
        if (!hasElevation) return null
        val d = meters.coerceIn(0.0, totalMeters)
        val i = indexAt(d)
        if (i >= points.lastIndex) return elevations.last()
        val segLen = cumulative[i + 1] - cumulative[i]
        val t = if (segLen > 0.0) (d - cumulative[i]) / segLen else 0.0
        return elevations[i] * (1.0 - t) + elevations[i + 1] * t
    }

    /**
     * Arc length of the point on the route closest to [target].
     *
     * Used to place intermediate stops: the coordinate the user long-pressed is
     * snapped onto the road geometry OSRM actually returned, so a stop triggers
     * where the route passes it rather than where the finger landed.
     */
    fun arcLengthNearest(target: LatLng): Double {
        if (points.size < 2) return 0.0
        // Local equirectangular projection — over a single segment the error is
        // far below the metre, and it keeps the projection a plain dot product.
        val latScale = 111_320.0
        val lonScale = latScale * kotlin.math.cos(Math.toRadians(target.latitude))
            .coerceAtLeast(0.05)

        var bestDistanceSq = Double.MAX_VALUE
        var bestArc = 0.0

        for (i in 0 until points.lastIndex) {
            val ax = points[i].longitude * lonScale
            val ay = points[i].latitude * latScale
            val bx = points[i + 1].longitude * lonScale
            val by = points[i + 1].latitude * latScale
            val px = target.longitude * lonScale
            val py = target.latitude * latScale

            val dx = bx - ax
            val dy = by - ay
            val lenSq = dx * dx + dy * dy
            val t = if (lenSq > 0.0) (((px - ax) * dx + (py - ay) * dy) / lenSq).coerceIn(0.0, 1.0) else 0.0

            val cx = ax + dx * t
            val cy = ay + dy * t
            val distSq = (px - cx) * (px - cx) + (py - cy) * (py - cy)

            if (distSq < bestDistanceSq) {
                bestDistanceSq = distSq
                bestArc = cumulative[i] + (cumulative[i + 1] - cumulative[i]) * t
            }
        }
        return bestArc.coerceIn(0.0, totalMeters)
    }

    /** Lowest curvature ceiling within [lookAheadMeters] ahead of [meters]. */
    fun speedLimitAhead(meters: Double, lookAheadMeters: Double): Float {
        if (points.size < 2) return Float.MAX_VALUE
        val from = indexAt(meters)
        val to = indexAt(meters + lookAheadMeters).coerceAtMost(points.lastIndex)
        var limit = Float.MAX_VALUE
        for (i in from..to) limit = min(limit, curveLimits[i])
        return limit
    }

    /** Elevation profile resampled to [samples] points, for the UI chart. */
    fun elevationProfile(samples: Int): List<Double>? {
        if (!hasElevation || samples < 2) return null
        return List(samples) { i ->
            elevationAt(totalMeters * i / (samples - 1)) ?: 0.0
        }
    }

    // ── Curvature ───────────────────────────────────────────────

    /**
     * Turn radius from three consecutive samples taken at a fixed spacing, then
     * converted into a comfortable cornering speed with v = sqrt(a_lat * r).
     *
     * Sampling by *distance* rather than by vertex index is what keeps this
     * stable: OSRM emits vertices with wildly uneven spacing, so an index window
     * measures noise on dense stretches and misses real corners on sparse ones.
     */
    private fun computeCurveLimits() {
        if (points.size < 3 || totalMeters < SAMPLE_SPACING_M * 2) return
        val lateralAccel = 3.2 // m/s^2 — brisk but not aggressive

        for (i in points.indices) {
            val d = cumulative[i]
            val a = positionAt(d - SAMPLE_SPACING_M)
            val b = points[i]
            val c = positionAt(d + SAMPLE_SPACING_M)

            val ab = a.distanceTo(b)
            val bc = b.distanceTo(c)
            val ac = a.distanceTo(c)
            if (ab < 1.0 || bc < 1.0 || ac < 1.0) continue

            // Menger curvature: area of the triangle gives the circumradius.
            val area = abs(
                (b.longitude - a.longitude) * (c.latitude - a.latitude) -
                    (c.longitude - a.longitude) * (b.latitude - a.latitude)
            ) / 2.0
            if (area < 1e-12) continue

            // Convert the degree-space area to square metres (rough but the
            // ratio is all that matters, and it is locally accurate).
            val metersPerDegLat = 111_320.0
            val metersPerDegLon = metersPerDegLat *
                kotlin.math.cos(Math.toRadians(b.latitude)).coerceAtLeast(0.05)
            val areaM2 = area * metersPerDegLat * metersPerDegLon
            if (areaM2 < 1e-6) continue

            val radius = (ab * bc * ac) / (4.0 * areaM2)
            if (!radius.isFinite() || radius <= 0.0) continue

            val vMs = sqrt(lateralAccel * radius)
            curveLimits[i] = (vMs * 3.6).toFloat().coerceIn(8f, Float.MAX_VALUE)
        }
    }

    private companion object {
        const val SAMPLE_SPACING_M = 18.0
    }
}
