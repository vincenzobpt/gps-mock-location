package com.mocklocation.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1A73E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFF34A853),
    error = Color(0xFFEA4335),
    background = Color(0xFFF8F9FA),
    surface = Color.White,
    onBackground = Color(0xFF202124),
    onSurface = Color(0xFF202124),
    outline = Color(0xFFDADCE0)
)

@Composable
fun MockLocationTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}
