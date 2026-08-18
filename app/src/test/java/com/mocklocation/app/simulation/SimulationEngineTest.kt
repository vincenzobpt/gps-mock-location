package com.mocklocation.app.simulation

import com.mocklocation.app.location.FakeMockLocationPort
import com.mocklocation.app.location.MockLocationEngine
import com.mocklocation.app.model.FixRate
import com.mocklocation.app.model.LatLng
import com.mocklocation.app.model.Route
import com.mocklocation.app.model.SimulationStatus
import com.mocklocation.app.model.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Regression tests for the fixes to SimulationEngine's threading and its
 * once-stationary startup — see the commit history for the bugs these pin
 * down. All run against [FakeMockLocationPort]: no Android framework, no
 * device, no emulator.
 */
class SimulationEngineTest {

    /**
     * A straight ~2 km line, far enough north-south that curvature and
     * arrival braking never become the binding speed ceiling and the tests
     * stay about the engine's threading, not its cornering model.
     */
    private fun straightRoute(points: Int = 6): Route {
        val start = LatLng(45.0, 9.0)
        val step = 0.003 // ~333 m of latitude per point
        return Route(
            points = List(points) { i -> LatLng(start.latitude + step * i, start.longitude) },
            distanceMeters = 333.0 * (points - 1),
            durationSeconds = 120.0,
        )
    }

    private fun newEngine(fake: FakeMockLocationPort = FakeMockLocationPort()): SimulationEngine {
        val engine = SimulationEngine(fake)
        // 10 Hz: fast enough that these tests run in well under a second,
        // without needing a virtual/controllable clock.
        engine.updateConfig { it.copy(fixRate = FixRate.HZ_10, minSpeedKmh = 20f, maxSpeedKmh = 40f) }
        return engine
    }

    private fun waitUntil(timeoutMs: Long = 2000L, predicate: () -> Boolean) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            if (predicate()) return
            Thread.sleep(5)
        }
        if (!predicate()) fail("condition not met within ${timeoutMs}ms")
    }

    // ─────────────────────────────────────────────────────────────
    //  Motion begins on the first tick
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `speed and distance advance well before the first target-hold period would elapse`() {
        val engine = newEngine()
        engine.setRoute(straightRoute())

        val status = engine.play()
        assertTrue("play() should reach Ready against a fake that always accepts", status is MockLocationEngine.Status.Ready)

        // The bug this pins down parked the vehicle at 0 km/h for
        // 3.5-8 seconds after every DRIVE. Three ticks at 10 Hz is 300 ms —
        // an order of magnitude inside that window.
        waitUntil(timeoutMs = 1000) { engine.state.value.fixCount >= 3 }

        val state = engine.state.value
        assertTrue("speed should be > 0 by the third fix, was ${state.speedKmh}", state.speedKmh > 0f)
        assertTrue(
            "distance should have advanced by the third fix, was ${state.distanceTraveledMeters}",
            state.distanceTraveledMeters > 0.0
        )

        engine.stop()
    }

    // ─────────────────────────────────────────────────────────────
    //  Editing a stop's dwell must not tear the run down
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `updateStopDwells does not stop a run in progress`() {
        val engine = newEngine()
        val route = straightRoute()
        // A stop near the far end so the run is still short of it while we edit.
        val stop = Waypoint(route.points.last(), stayDurationSeconds = 5L)
        engine.setRoute(route, listOf(stop))
        engine.play()

        waitUntil { engine.state.value.fixCount >= 2 }
        val fixesBeforeEdit = engine.state.value.fixCount
        assertEquals(SimulationStatus.PLAYING, engine.state.value.status)

        engine.updateStopDwells(listOf(stop.copy(stayDurationSeconds = 30L)))

        // Still the same run: status never dropped, and fixes kept counting
        // up rather than resetting to 0 the way stop()-via-setRoute would.
        assertEquals(SimulationStatus.PLAYING, engine.state.value.status)
        waitUntil { engine.state.value.fixCount > fixesBeforeEdit }

        engine.stop()
    }

    // ─────────────────────────────────────────────────────────────
    //  A provider that vanishes mid-run must pause, not report success
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `a push failure pauses the run and surfaces the non-Ready status`() {
        val fake = FakeMockLocationPort()
        val engine = newEngine(fake)
        engine.setRoute(straightRoute())
        engine.play()

        waitUntil { engine.state.value.fixCount >= 1 }
        fake.failNextPushes = 1

        waitUntil { engine.state.value.status == SimulationStatus.PAUSED }
        assertFalse(
            "providerStatus must not still claim Ready once the push failed",
            engine.providerStatus.value is MockLocationEngine.Status.Ready
        )

        engine.stop()
    }

    // ─────────────────────────────────────────────────────────────
    //  seekTo is synchronous and exact
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `seekTo lands exactly on the requested fraction`() {
        val engine = newEngine()
        val route = straightRoute()
        engine.setRoute(route)
        engine.play()
        waitUntil { engine.state.value.fixCount >= 1 }
        engine.pause() // freeze the integrator so only seekTo moves it

        // The route's own reported distance is OSRM's estimate; totalDistanceMeters
        // is the engine's actual arc length from RouteGeometry, which is what
        // seekTo's fraction is taken against — the two differ by a few metres
        // even on a synthetic route, so the expectation has to use the latter.
        val totalMeters = engine.state.value.totalDistanceMeters
        engine.seekTo(0.5f)

        val state = engine.state.value
        val expected = totalMeters * 0.5
        assertTrue(
            "expected ~$expected m, was ${state.distanceTraveledMeters}",
            kotlin.math.abs(state.distanceTraveledMeters - expected) < 1.0
        )
        assertEquals(0f, state.speedKmh, 0.01f)

        engine.stop()
    }

    // ─────────────────────────────────────────────────────────────
    //  Thread confinement: rapid transport commands never desync
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `rapid play-pause cycling from one caller thread ends coherent`() {
        val engine = newEngine()
        engine.setRoute(straightRoute())

        // Mirrors tapping DRIVE/PAUSE rapidly while the tick loop is running
        // concurrently on Dispatchers.Default — the exact shape of the race
        // that used to let a stopped run "resurrect" mid-tick.
        repeat(30) {
            engine.play()
            Thread.sleep(3)
            engine.pause()
            Thread.sleep(3)
        }
        engine.stop()

        val state = engine.state.value
        assertEquals(SimulationStatus.IDLE, state.status)
        assertEquals(0.0, state.distanceTraveledMeters, 0.0)
        assertEquals(0L, state.fixCount)
        assertTrue(state.activeProviders.isEmpty())
    }

    @Test
    fun `concurrent transport commands from multiple threads never throw and always end coherent`() {
        val engine = newEngine()
        engine.setRoute(straightRoute())

        val errors = CopyOnWriteArrayList<Throwable>()
        val threadCount = 4
        val startLine = CountDownLatch(1)
        val done = CountDownLatch(threadCount)

        repeat(threadCount) { i ->
            Thread {
                try {
                    startLine.await()
                    repeat(40) { n ->
                        when ((i + n) % 3) {
                            0 -> engine.play()
                            1 -> engine.pause()
                            else -> engine.seekTo((n % 10) / 10f)
                        }
                    }
                } catch (t: Throwable) {
                    errors += t
                } finally {
                    done.countDown()
                }
            }.start()
        }

        startLine.countDown()
        assertTrue("threads did not finish in time", done.await(5, TimeUnit.SECONDS))
        assertTrue("concurrent transport commands threw: $errors", errors.isEmpty())

        // The burst is inherently unordered across threads; what must hold
        // regardless is that the *next* command — issued after every thread
        // has finished — deterministically wins and leaves a clean state.
        engine.stop()
        val state = engine.state.value
        assertEquals(SimulationStatus.IDLE, state.status)
        assertEquals(0L, state.fixCount)
        assertTrue(state.activeProviders.isEmpty())
    }
}
