package com.mocklocation.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mocklocation.app.ui.theme.Cockpit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════════
//  Glass primitives
// ═══════════════════════════════════════════════════════════════

/**
 * The single surface treatment used by every floating element: a dark glass
 * fill with a 1px top-lit hairline. Consistency here is what makes a stack of
 * unrelated controls read as one instrument cluster.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    color: Color = Cockpit.Glass,
    borderColor: Color = Cockpit.Hairline,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        border = BorderStroke(1.dp, borderColor),
        content = content
    )
}

/** Square glass button used in the right-hand map rail. */
@Composable
fun RailButton(
    glyph: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    size: Dp = 44.dp,
) {
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(14.dp),
        color = if (active) Cockpit.Live.copy(alpha = 0.18f) else Cockpit.Glass,
        border = BorderStroke(1.dp, if (active) Cockpit.Live.copy(alpha = 0.45f) else Cockpit.Hairline),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = glyph,
                fontSize = 17.sp,
                color = if (active) Cockpit.Live else Cockpit.Ink,
                maxLines = 1,
            )
        }
    }
}

/** Small uppercase caption used above every value in the telemetry grid. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier, color: Color = Cockpit.InkFaint) {
    Text(
        text = text.uppercase(Locale.ROOT),
        modifier = modifier,
        color = color,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.1.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * One telemetry cell: caption, monospaced value, optional unit.
 * Monospace matters — at 5 Hz, proportional digits make the whole panel jitter.
 */
@Composable
fun ReadoutCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    valueColor: Color = Cockpit.Ink,
    valueSize: Int = 15,
) {
    Column(modifier = modifier) {
        FieldLabel(label)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = valueColor,
                fontSize = valueSize.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.4).sp,
                maxLines = 1,
            )
            if (unit != null) {
                Text(
                    text = " $unit",
                    color = Cockpit.InkFaint,
                    fontSize = (valueSize - 5).coerceAtLeast(8).sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Pill-shaped status chip; the dot pulses when [live] is set. */
@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val alpha = if (live) pulse(1200) else 1f
    Surface(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.32f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha))
            )
            Text(
                text = text,
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp,
                maxLines = 1,
            )
        }
    }
}

/** Thin section heading with a trailing hairline. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FieldLabel(title, color = Cockpit.InkMuted)
        Box(
            Modifier
                .padding(horizontal = 10.dp)
                .weight(1f)
                .drawBehind {
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(Cockpit.HairlineStrong, Color.Transparent)
                        ),
                        size = size.copy(height = 1f),
                    )
                }
        )
        if (trailing != null) {
            Text(
                text = trailing,
                color = Cockpit.InkFaint,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

/** Hairline border helper matching [GlassSurface]. */
fun Modifier.hairline(shape: Shape, color: Color = Cockpit.Hairline): Modifier =
    border(1.dp, color, shape)

// ═══════════════════════════════════════════════════════════════
//  Animation helpers
// ═══════════════════════════════════════════════════════════════

/** 0.35..1 triangle wave, used for "this is live" affordances. */
@Composable
fun pulse(periodMs: Int = 1400, min: Float = 0.35f, max: Float = 1f): Float {
    val transition = rememberInfiniteTransition(label = "pulse")
    val value by transition.animateFloat(
        initialValue = min,
        targetValue = max,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseValue"
    )
    return value
}

// ═══════════════════════════════════════════════════════════════
//  Formatters
// ═══════════════════════════════════════════════════════════════

fun formatDistance(meters: Double): String = when {
    meters < 1000 -> "${meters.roundToInt()} m"
    meters < 100_000 -> String.format(Locale.US, "%.2f km", meters / 1000.0)
    else -> String.format(Locale.US, "%.0f km", meters / 1000.0)
}

fun formatDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.US, "%02d:%02d", m, sec)
}

/** Signed degrees-minutes-seconds, the notation a GPS nerd expects. */
fun formatDms(value: Double, isLatitude: Boolean): String {
    val hemisphere = when {
        isLatitude && value >= 0 -> "N"
        isLatitude -> "S"
        value >= 0 -> "E"
        else -> "W"
    }
    val abs = abs(value)
    val deg = abs.toInt()
    val minFull = (abs - deg) * 60.0
    val min = minFull.toInt()
    val sec = (minFull - min) * 60.0
    return String.format(Locale.US, "%d°%02d'%04.1f\"%s", deg, min, sec, hemisphere)
}

fun formatDecimal(value: Double, decimals: Int): String =
    String.format(Locale.US, "%.${decimals}f", value)

fun formatSigned(value: Float, decimals: Int = 2): String =
    String.format(Locale.US, "%+.${decimals}f", value)
