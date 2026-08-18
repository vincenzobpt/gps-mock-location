package com.mocklocation.app.location

/**
 * What [com.mocklocation.app.simulation.SimulationEngine] needs from a
 * mock-location backend.
 *
 * [MockLocationEngine] is the only production implementation and talks to the
 * real platform providers, which exist only on a device. Extracting this
 * seam is what lets the engine's integrator and its threading be driven by a
 * fake in a plain JVM unit test instead.
 */
interface MockLocationPort {
    val status: MockLocationEngine.Status
    val providers: List<String>
    fun start(): MockLocationEngine.Status
    fun stop()
    fun push(fix: MockLocationEngine.Fix): String?
}
