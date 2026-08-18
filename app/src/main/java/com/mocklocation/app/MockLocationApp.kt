package com.mocklocation.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mocklocation.app.location.MockLocationEngine
import com.mocklocation.app.service.MockLocationService
import com.mocklocation.app.simulation.SimulationEngine
import org.osmdroid.config.Configuration

/**
 * Holds the process-wide [SimulationEngine]. Keeping it here (rather than in a
 * ViewModel) is what lets a run keep injecting fixes while the user switches to
 * the app they are testing.
 */
class MockLocationApp : Application() {

    lateinit var engine: SimulationEngine
        private set

    override fun onCreate() {
        super.onCreate()

        // load() is what binds osmdroid to a Context. Without it the tile
        // provider resolves its base path against a null context, throws
        // internally and never renders a single tile — which looks exactly like
        // "the map is broken".
        Configuration.getInstance().apply {
            load(this@MockLocationApp, getSharedPreferences("osmdroid", MODE_PRIVATE))
            userAgentValue = packageName
            osmdroidBasePath = filesDir.resolve("osmdroid")
            osmdroidTileCache = filesDir.resolve("osmdroid/tiles")
        }

        engine = SimulationEngine(MockLocationEngine(applicationContext)).apply {
            onRunningChanged = { running ->
                val intent = Intent(this@MockLocationApp, MockLocationService::class.java)
                runCatching {
                    if (running) {
                        intent.action = MockLocationService.ACTION_START
                        startForegroundService(intent)
                    } else {
                        // stopService is safe whether or not the service is up,
                        // and is not subject to background-start restrictions.
                        stopService(intent)
                    }
                }
            }
        }

        // A previous process may have died mid-run; do not inherit its
        // providers, or the device stays pinned to that last fix.
        engine.clearStaleProviders()
    }

    companion object {
        fun engineOf(context: Context): SimulationEngine =
            (context.applicationContext as MockLocationApp).engine
    }
}
