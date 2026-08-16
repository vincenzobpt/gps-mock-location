package com.mocklocation.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * "Night cockpit" design tokens.
 *
 * One accent (electric cyan) carries every live/active meaning; emerald and rose
 * are reserved exclusively for start/end so the map reads instantly. Everything
 * else is a neutral on a near-black ground, which is what lets the map breathe.
 */
object Cockpit {
    // ── Ground ────────────────────────────────────────────────
    val Void = Color(0xFF04060B)
    val Deep = Color(0xFF090D16)
    val Panel = Color(0xFF0E1420)
    val Elevated = Color(0xFF141C2B)

    // ── Accents ───────────────────────────────────────────────
    val Live = Color(0xFF22D3EE)          // primary — motion, injection, "on"
    val LiveSoft = Color(0xFF0E7490)
    val Violet = Color(0xFF818CF8)        // secondary — altitude, terrain
    val Start = Color(0xFF34D399)
    val End = Color(0xFFFB7185)
    val Warn = Color(0xFFFBBF24)
    val Danger = Color(0xFFF43F5E)

    // ── Type ──────────────────────────────────────────────────
    val Ink = Color(0xFFE8F0FA)
    val InkMuted = Color(0xFF93A3B8)
    val InkFaint = Color(0xFF5C6B80)

    // ── Strokes / glass ───────────────────────────────────────
    val Hairline = Color(0x14FFFFFF)
    val HairlineStrong = Color(0x26FFFFFF)
    val Glass = Color(0xE60B1120)
    val GlassSoft = Color(0xB30B1120)

    /** Vertical scrim used behind the top bar and under the bottom console. */
    val TopScrim = Brush.verticalGradient(
        listOf(Color(0xCC04060B), Color(0x8804060B), Color(0x0004060B))
    )
    val BottomScrim = Brush.verticalGradient(
        listOf(Color(0x0004060B), Color(0x9904060B), Color(0xE604060B))
    )

    /** Sweep used by the speed gauge: cool at rest, hot at the ceiling. */
    val SpeedRamp = listOf(
        Color(0xFF22D3EE),
        Color(0xFF60A5FA),
        Color(0xFF818CF8),
        Color(0xFFF472B6),
        Color(0xFFFBBF24),
    )

    /** Tabular figures for every number that changes at the tick rate. */
    val Mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.2).sp,
    )
}

private val CockpitColors = darkColorScheme(
    primary = Cockpit.Live,
    onPrimary = Cockpit.Void,
    primaryContainer = Cockpit.LiveSoft,
    onPrimaryContainer = Cockpit.Ink,
    secondary = Cockpit.Violet,
    onSecondary = Cockpit.Void,
    tertiary = Cockpit.Start,
    onTertiary = Cockpit.Void,
    error = Cockpit.Danger,
    onError = Color.White,
    errorContainer = Color(0xFF4C0519),
    onErrorContainer = Color(0xFFFFE4E6),
    background = Cockpit.Void,
    onBackground = Cockpit.Ink,
    surface = Cockpit.Panel,
    onSurface = Cockpit.Ink,
    surfaceVariant = Cockpit.Elevated,
    onSurfaceVariant = Cockpit.InkMuted,
    outline = Cockpit.HairlineStrong,
    outlineVariant = Cockpit.Hairline,
    scrim = Color(0xCC000000),
)

private val CockpitTypography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        ),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp
        ),
    )
}

@Composable
fun MockLocationTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CockpitColors,
        typography = CockpitTypography,
        content = content
    )
}
