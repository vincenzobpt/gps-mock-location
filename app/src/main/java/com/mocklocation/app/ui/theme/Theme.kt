package com.mocklocation.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Brand palette ─────────────────────────────────────────────
private val BrandDark = Color(0xFF0D2137)       // dark navy
private val BrandBlue = Color(0xFF1A73E8)        // primary blue
private val BrandBlueDark = Color(0xFF1557B0)    // darker variant
private val BrandGreen = Color(0xFF34A853)       // start / success
private val BrandRed = Color(0xFFEA4335)         // end / error
private val BrandAmber = Color(0xFFFBBC04)       // accent / warning

private val SurfaceLight = Color(0xFFF8F9FA)
private val SurfaceWhite = Color(0xFFFFFFFF)
private val OnSurfaceDark = Color(0xFF202124)
private val OnSurfaceMedium = Color(0xFF5F6368)
private val OutlineLight = Color(0xFFDADCE0)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = BrandBlueDark,
    secondary = BrandGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCEEAD6),
    tertiary = BrandAmber,
    error = BrandRed,
    onError = Color.White,
    errorContainer = Color(0xFFFCE8E6),
    surface = SurfaceWhite,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = OnSurfaceMedium,
    outline = OutlineLight,
    background = SurfaceLight,
    onBackground = OnSurfaceDark,
    inverseSurface = BrandDark,
    inverseOnSurface = Color(0xFFE8EAED),
)

@Composable
fun MockLocationTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}
