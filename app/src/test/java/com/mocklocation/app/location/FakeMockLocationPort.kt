package com.mocklocation.app.location

import java.util.concurrent.CopyOnWriteArrayList

/**
 * A [MockLocationPort] that never touches Android — tracks fixes as plain
 * data and lets a test script exactly the failures [MockLocationEngine]
 * would otherwise only produce on a device (another mock app taking over,
 * the app-op being revoked mid-run).
 *
 * Thread-safe: [SimulationEngine][com.mocklocation.app.simulation.SimulationEngine]
 * calls [push] from its tick loop and [start]/[stop] from transport
 * commands, and the concurrency tests deliberately hammer both from
 * multiple threads at once.
 */
class FakeMockLocationPort(
    private val readyProviders: List<String> = listOf("gps", "network", "fused"),
) : MockLocationPort {

    @Volatile
    override var status: MockLocationEngine.Status = MockLocationEngine.Status.Idle
        private set

    override val providers: List<String>
        get() = (status as? MockLocationEngine.Status.Ready)?.providers ?: emptyList()

    /** Every fix ever pushed, in order. */
    val pushedFixes = CopyOnWriteArrayList<MockLocationEngine.Fix>()

    /**
     * When positive, the next [push] decrements it, records nothing, and
     * fails with [failureStatus] — simulating a provider that vanished
     * underneath the engine.
     */
    @Volatile
    var failNextPushes = 0

    @Volatile
    var failureStatus: MockLocationEngine.Status =
        MockLocationEngine.Status.Failed("simulated provider loss")

    override fun start(): MockLocationEngine.Status {
        status = MockLocationEngine.Status.Ready(readyProviders)
        return status
    }

    override fun stop() {
        status = MockLocationEngine.Status.Idle
    }

    override fun push(fix: MockLocationEngine.Fix): String? {
        if (failNextPushes > 0) {
            failNextPushes--
            status = failureStatus
            return "simulated push failure"
        }
        pushedFixes += fix
        return null
    }
}
