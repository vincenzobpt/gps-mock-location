package com.mocklocation.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log

/**
 * Owns the platform test-location providers.
 *
 * Two rules drive this class, and breaking either one is why naive mock apps
 * silently do nothing:
 *
 *  1. **`addTestProvider` is registered once per run, never per fix.** In the
 *     framework, `addTestProvider` calls `LocationProviderManager.setMockProvider`,
 *     which *replaces* the provider: it clears the last known location and drops
 *     the provider back to "disabled". Calling it on every tick therefore tears
 *     down the provider several times a second and consumers never see a stable
 *     fix.
 *
 *  2. **Every provider a consumer might read is mocked**, not just `gps`.
 *     Play-Services apps read the *fused* provider; some read `network`. Mocking
 *     only `gps` leaves those apps on the real position.
 *
 * Failures are classified and surfaced instead of being swallowed.
 */
class MockLocationEngine(private val context: Context) : MockLocationPort {

    /** Outcome of [start], suitable for showing to the user verbatim. */
    sealed interface Status {
        /** At least one provider accepted the mock registration. */
        data class Ready(val providers: List<String>) : Status

        /** The app is not the selected "mock location app" in Developer options. */
        data object NotSelectedAsMockApp : Status

        /** ACCESS_FINE_LOCATION has not been granted at runtime. */
        data object MissingLocationPermission : Status

        /** Registration failed for an unexpected reason. */
        data class Failed(val reason: String) : Status

        data object Idle : Status
    }

    private val locationManager: LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val activeProviders = mutableListOf<String>()

    @Volatile
    override var status: Status = Status.Idle
        private set

    /** Providers currently accepting injected fixes. */
    override val providers: List<String> get() = activeProviders.toList()

    val isReady: Boolean get() = status is Status.Ready

    // ── Setup / teardown ────────────────────────────────────────

    /**
     * Registers the test providers. Idempotent: calling it while already running
     * returns the current status without churning the providers.
     */
    override fun start(): Status {
        if (status is Status.Ready) return status

        val lm = locationManager
            ?: return Status.Failed("LocationManager unavailable").also { status = it }

        if (!hasFineLocationPermission()) {
            return Status.MissingLocationPermission.also { status = it }
        }

        activeProviders.clear()
        var securityDenied = false
        var lastFailure: String? = null

        for (provider in candidateProviders()) {
            try {
                // A stale provider from a previous run (or a crashed process)
                // must go first — re-adding over it is what resets consumers.
                runCatching { lm.removeTestProvider(provider) }

                registerProvider(lm, provider)
                // Registration and enabling are separate concerns: a provider can
                // be added and still refuse to enable, and an unusable provider
                // must never be reported as a working one.
                lm.setTestProviderEnabled(provider, true)
                activeProviders += provider
            } catch (e: SecurityException) {
                securityDenied = true
                lastFailure = e.message
                Log.w(TAG, "SecurityException registering '$provider'", e)
            } catch (e: IllegalArgumentException) {
                lastFailure = e.message
                Log.w(TAG, "Provider '$provider' rejected by the platform", e)
            } catch (e: Exception) {
                lastFailure = e.message
                Log.w(TAG, "Unexpected failure registering '$provider'", e)
            }
        }

        status = when {
            activeProviders.isNotEmpty() -> Status.Ready(activeProviders.toList())
            securityDenied -> Status.NotSelectedAsMockApp
            else -> Status.Failed(lastFailure ?: "No provider accepted the mock registration")
        }
        return status
    }

    /**
     * Removes every test provider. **Must** run when the simulation ends,
     * otherwise the fake provider outlives the app and the device keeps
     * reporting the last injected position instead of the real GPS.
     */
    override fun stop() {
        val lm = locationManager
        if (lm != null) {
            for (provider in activeProviders.toList() + candidateProviders()) {
                runCatching { lm.setTestProviderEnabled(provider, false) }
                runCatching { lm.removeTestProvider(provider) }
            }
        }
        activeProviders.clear()
        status = Status.Idle
    }

    // ── Injection ───────────────────────────────────────────────

    /**
     * Pushes one fix to every active provider.
     *
     * @return null on success, or a human-readable reason for the failure.
     */
    override fun push(fix: Fix): String? {
        val lm = locationManager ?: return "LocationManager unavailable"
        if (activeProviders.isEmpty()) return "No active mock provider"

        var lastError: String? = null
        var delivered = 0
        val stale = mutableListOf<String>()

        for (provider in activeProviders) {
            try {
                lm.setTestProviderLocation(provider, fix.toLocation(provider))
                delivered++
            } catch (e: SecurityException) {
                lastError = "Mock location permission revoked"
                status = Status.NotSelectedAsMockApp
            } catch (e: IllegalArgumentException) {
                // The provider was torn down underneath us — another mock app
                // took over, or the platform reclaimed it.
                lastError = e.message ?: "Provider '$provider' is no longer registered"
                stale += provider
            } catch (e: Exception) {
                lastError = e.message ?: e::class.java.simpleName
            }
        }

        if (stale.isNotEmpty()) {
            // Actually drop the dead handles and the ready flag. Leaving them in
            // place let a run keep "succeeding" against providers that no longer
            // existed, and made start() early-return on a stale Ready so pause
            // then play could never re-register.
            activeProviders.removeAll(stale)
            if (status is Status.Ready) {
                status = if (activeProviders.isEmpty()) {
                    Status.Failed(lastError ?: "Test providers are no longer registered")
                } else {
                    Status.Ready(activeProviders.toList())
                }
            }
        }

        return if (delivered > 0) null else lastError ?: "Fix rejected"
    }

    // ── Helpers ─────────────────────────────────────────────────

    /**
     * Android 12 replaced the ten-argument flag soup with [ProviderProperties].
     * The legacy overload is still the only option below API 31, and its
     * `powerRequirement`/`accuracy` parameters carry the newer annotation even
     * though they predate it — the integer values are identical, hence the
     * suppression.
     */
    @SuppressLint("WrongConstant")
    private fun registerProvider(lm: LocationManager, provider: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            lm.addTestProvider(
                provider,
                ProviderProperties.Builder()
                    .setHasNetworkRequirement(false)
                    .setHasSatelliteRequirement(provider == LocationManager.GPS_PROVIDER)
                    .setHasCellRequirement(false)
                    .setHasMonetaryCost(false)
                    .setHasAltitudeSupport(true)
                    .setHasSpeedSupport(true)
                    .setHasBearingSupport(true)
                    .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                    .setAccuracy(ProviderProperties.ACCURACY_FINE)
                    .build()
            )
        } else {
            @Suppress("DEPRECATION")
            lm.addTestProvider(
                provider,
                /* requiresNetwork   = */ false,
                /* requiresSatellite = */ provider == LocationManager.GPS_PROVIDER,
                /* requiresCell      = */ false,
                /* hasMonetaryCost   = */ false,
                /* supportsAltitude  = */ true,
                /* supportsSpeed     = */ true,
                /* supportsBearing   = */ true,
                Criteria.POWER_LOW,
                Criteria.ACCURACY_FINE
            )
        }
    }

    fun hasFineLocationPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun candidateProviders(): List<String> = buildList {
        add(LocationManager.GPS_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        // "fused" only exists as a platform provider from Android 12.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(FUSED_PROVIDER)
    }

    /** One simulated GPS fix, provider-agnostic. */
    data class Fix(
        val latitude: Double,
        val longitude: Double,
        val altitudeMeters: Double,
        val bearingDegrees: Float,
        val speedMs: Float,
        val accuracyMeters: Float,
        val satellitesUsed: Int,
    ) {
        fun toLocation(provider: String): Location = Location(provider).apply {
            latitude = this@Fix.latitude
            longitude = this@Fix.longitude
            altitude = altitudeMeters
            bearing = bearingDegrees
            speed = speedMs
            accuracy = accuracyMeters
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()

            // Vertical/speed/bearing accuracies exist from API 26 and make the
            // fix look like it came from a real chipset to strict consumers.
            verticalAccuracyMeters = accuracyMeters * 1.6f
            speedAccuracyMetersPerSecond = 0.4f
            bearingAccuracyDegrees = 2.5f

            extras = Bundle().apply { putInt("satellites", satellitesUsed) }
        }
    }

    private companion object {
        const val TAG = "MockLocationEngine"
        /** `LocationManager.FUSED_PROVIDER`, inlined to stay compilable below API 31. */
        const val FUSED_PROVIDER = "fused"
    }
}
