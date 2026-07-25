package com.mocklocation.app

import android.app.Application
import org.osmdroid.config.Configuration

class MockLocationApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Configure osmdroid user-agent (required)
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidTileCache = cacheDir.resolve("tiles")
        }
    }
}
