package com.mocklocation.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mocklocation.app.ui.MapScreen
import com.mocklocation.app.ui.theme.Cockpit
import com.mocklocation.app.ui.theme.MockLocationTheme
import com.mocklocation.app.viewmodel.MapViewModel
import androidx.compose.foundation.layout.Box

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Fully transparent system bars: the map runs edge to edge and every
        // overlay applies its own inset padding, so nothing sits under a bar.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            MockLocationTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Cockpit.Void)
                ) {
                    val viewModel: MapViewModel = viewModel()
                    MapScreen(viewModel = viewModel)
                }
            }
        }
    }
}
