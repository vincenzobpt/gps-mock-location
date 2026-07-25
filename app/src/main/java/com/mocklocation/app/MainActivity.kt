package com.mocklocation.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mocklocation.app.ui.MapScreen
import com.mocklocation.app.ui.theme.MockLocationTheme
import com.mocklocation.app.viewmodel.MapViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MockLocationTheme {
                val viewModel: MapViewModel = viewModel()
                MapScreen(viewModel = viewModel)
            }
        }
    }
}
