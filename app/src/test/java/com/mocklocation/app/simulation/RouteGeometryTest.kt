package com.mocklocation.app.simulation

import com.mocklocation.app.model.LatLng
import com.mocklocation.app.model.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-math coverage for the arc-length parameterisation the engine drives from. */
class RouteGeometryTest {

    private val straight = listOf(
        LatLng(45.000, 9.000),
        LatLng(45.003, 9.000),
        LatLng(45.006, 9.000),
        LatLng(45.009, 9.000),
    )

    @Test
    fun `isUsable is false for a degenerate route`() {
        assertFalse(RouteGeometry(Route(listOf(LatLng(45.0, 9.0)), 0.0, 0.0)).isUsable)
        assertFalse(RouteGeometry(Route(emptyList(), 0.0, 0.0)).isUsable)
        // Two coincident points: distance is ~0, below the usability floor.
        val samePoint = List(2) { LatLng(45.0, 9.0) }
        assertFalse(RouteGeometry(Route(samePoint, 0.0, 0.0)).isUsable)
    }

    @Test
    fun `totalMeters matches the summed segment lengths`() {
        val geo = RouteGeometry(Route(straight, 0.0, 0.0))
        var expected = 0.0
        for (i in 1 until straight.size) expected += straight[i - 1].distanceTo(straight[i])
        assertEquals(expected, geo.totalMeters, 0.5)
        assertTrue(geo.isUsable)
    }

    @Test
    fun `positionAt the endpoints returns the endpoints`() {
        val geo = RouteGeometry(Route(straight, 0.0, 0.0))
        val atStart = geo.positionAt(0.0)
        val atEnd = geo.positionAt(geo.totalMeters)
        assertEquals(straight.first().latitude, atStart.latitude, 1e-9)
        assertEquals(straight.last().latitude, atEnd.latitude, 1e-6)
        // Clamping: asking beyond either end must not extrapolate off the route.
        assertEquals(atStart, geo.positionAt(-500.0))
        assertEquals(atEnd, geo.positionAt(geo.totalMeters + 500.0))
    }

    @Test
    fun `positionAt the midpoint lies between the endpoints`() {
        val geo = RouteGeometry(Route(straight, 0.0, 0.0))
        val mid = geo.positionAt(geo.totalMeters / 2.0)
        assertTrue(mid.latitude > straight.first().latitude)
        assertTrue(mid.latitude < straight.last().latitude)
    }

    @Test
    fun `indexAt is monotonic and never exceeds the last vertex`() {
        val geo = RouteGeometry(Route(straight, 0.0, 0.0))
        var previous = -1
        var d = 0.0
        while (d <= geo.totalMeters) {
            val i = geo.indexAt(d)
            assertTrue("indexAt must not go backwards", i >= previous)
            assertTrue(i <= straight.lastIndex)
            previous = i
            d += 17.0
        }
        assertEquals(straight.lastIndex, geo.indexAt(geo.totalMeters))
    }

    @Test
    fun `headingAt a north-south line points due north`() {
        val geo = RouteGeometry(Route(straight, 0.0, 0.0))
        val bearing = geo.headingAt(geo.totalMeters / 2.0)
        assertTrue("expected ~0 deg (north), was $bearing", bearing < 1f || bearing > 359f)
    }

    @Test
    fun `arcLengthNearest snaps a point beside the line onto it`() {
        val geo = RouteGeometry(Route(straight, 0.0, 0.0))
        // A point ~50 m east of the second vertex: nearest point on the route
        // is that vertex, so its arc length should equal the cumulative
        // distance to it — not to any other vertex.
        val offRoute = LatLng(straight[1].latitude, straight[1].longitude + 0.0006)
        val arc = geo.arcLengthNearest(offRoute)
        val distanceToVertex1 = straight[0].distanceTo(straight[1])
        assertEquals(distanceToVertex1, arc, 5.0)
    }

    @Test
    fun `arcLengthNearest never returns outside the route`() {
        val geo = RouteGeometry(Route(straight, 0.0, 0.0))
        val farAway = LatLng(-10.0, 100.0)
        val arc = geo.arcLengthNearest(farAway)
        assertTrue(arc in 0.0..geo.totalMeters)
    }

    @Test
    fun `elevation is absent without data and present with it`() {
        val flat = RouteGeometry(Route(straight, 0.0, 0.0))
        assertFalse(flat.hasElevation)
        assertNull(flat.elevationAt(0.0))
        assertNull(flat.elevationProfile(10))

        val withElevation = RouteGeometry(
            Route(straight, 0.0, 0.0, elevations = listOf(100.0, 150.0, 120.0, 200.0))
        )
        assertTrue(withElevation.hasElevation)
        assertEquals(100.0, withElevation.elevationAt(0.0)!!, 0.5)
        assertEquals(200.0, withElevation.elevationAt(withElevation.totalMeters)!!, 0.5)

        val profile = withElevation.elevationProfile(20)
        assertEquals(20, profile!!.size)
    }

    @Test
    fun `speedLimitAhead is unrestricted on a straight line`() {
        val geo = RouteGeometry(Route(straight, 0.0, 0.0))
        val limit = geo.speedLimitAhead(0.0, geo.totalMeters)
        assertTrue("a straight line should impose no curvature limit", limit > 200f)
    }
}
