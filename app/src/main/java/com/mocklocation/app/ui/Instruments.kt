package com.mocklocation.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.mocklocation.app.ui.theme.Cockpit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

// ═══════════════════════════════════════════════════════════════
//  SPEED GAUGE
// ═══════════════════════════════════════════════════════════════

private const val GAUGE_START = 138f
private const val GAUGE_SWEEP = 264f

/**
 * The centrepiece instrument.
 *
 * Three values share one dial so their relationship is readable at a glance:
 * the filled arc is the *actual* speed, the thin outer notch is the *target*
 * the engine is easing toward, and the amber wedge is the curvature ceiling
 * ahead. When the wedge closes in on the fill you can literally watch the
 * simulation brake for a corner.
 */
@Composable
fun SpeedGauge(
    speedKmh: Float,
    targetKmh: Float,
    limitKmh: Float,
    maxKmh: Float,
    modifier: Modifier = Modifier,
    live: Boolean = false,
) {
    val ceiling = max(maxKmh, 1f)
    val animatedSpeed by animateFloatAsState(
        targetValue = speedKmh.coerceIn(0f, ceiling),
        animationSpec = tween(260),
        label = "speed"
    )
    val glow = if (live) pulse(1600, 0.25f, 0.55f) else 0f

    Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.075f
            val inset = stroke / 2f + size.minDimension * 0.06f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = arcSize.minDimension / 2f

            // Ambient glow behind the dial while running.
            if (glow > 0f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Cockpit.Live.copy(alpha = glow * 0.30f), Color.Transparent),
                        center = center,
                        radius = radius * 1.25f
                    ),
                    radius = radius * 1.25f,
                    center = center
                )
            }

            // Track.
            drawArc(
                color = Cockpit.Hairline,
                startAngle = GAUGE_START,
                sweepAngle = GAUGE_SWEEP,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )

            // Curvature ceiling wedge — the "you must slow down" region.
            if (limitKmh < ceiling * 0.98f) {
                val limitFraction = (limitKmh / ceiling).coerceIn(0f, 1f)
                val startAngle = GAUGE_START + GAUGE_SWEEP * limitFraction
                drawArc(
                    color = Cockpit.Warn.copy(alpha = 0.20f),
                    startAngle = startAngle,
                    sweepAngle = GAUGE_SWEEP * (1f - limitFraction),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke * 0.42f)
                )
            }

            // Value arc, painted as short segments so the ramp follows the curve
            // exactly (a sweep gradient would need rotation and still band).
            val fraction = (animatedSpeed / ceiling).coerceIn(0f, 1f)
            if (fraction > 0.001f) {
                val segments = max(1, (GAUGE_SWEEP * fraction / 3f).roundToInt())
                val segSweep = GAUGE_SWEEP * fraction / segments
                for (i in 0 until segments) {
                    val t = if (segments == 1) 0f else i / (segments - 1f)
                    drawArc(
                        color = rampColor(t * fraction),
                        startAngle = GAUGE_START + segSweep * i,
                        // Overlap by a hair to avoid seams between segments.
                        sweepAngle = segSweep * 1.25f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(
                            width = stroke,
                            cap = if (i == 0 || i == segments - 1)
                                StrokeCap.Round
                            else StrokeCap.Butt
                        )
                    )
                }
            }

            // Minor ticks.
            val ticks = 24
            for (i in 0..ticks) {
                val t = i / ticks.toFloat()
                val major = i % 4 == 0
                val angle = Math.toRadians((GAUGE_START + GAUGE_SWEEP * t).toDouble())
                val rOuter = radius - stroke * 0.85f
                val rInner = rOuter - if (major) stroke * 0.55f else stroke * 0.28f
                drawLine(
                    color = if (major) Cockpit.InkFaint else Cockpit.Hairline,
                    start = center + Offset(
                        (cos(angle) * rInner).toFloat(),
                        (sin(angle) * rInner).toFloat()
                    ),
                    end = center + Offset(
                        (cos(angle) * rOuter).toFloat(),
                        (sin(angle) * rOuter).toFloat()
                    ),
                    strokeWidth = if (major) 2f else 1f,
                    cap = StrokeCap.Round
                )
            }

            // Target notch.
            val targetFraction = (targetKmh / ceiling).coerceIn(0f, 1f)
            if (targetFraction > 0.001f) {
                val angle = Math.toRadians((GAUGE_START + GAUGE_SWEEP * targetFraction).toDouble())
                val rOuter = radius + stroke * 0.15f
                val rInner = rOuter - stroke * 1.15f
                drawLine(
                    color = Cockpit.Ink.copy(alpha = 0.75f),
                    start = center + Offset(
                        (cos(angle) * rInner).toFloat(),
                        (sin(angle) * rInner).toFloat()
                    ),
                    end = center + Offset(
                        (cos(angle) * rOuter).toFloat(),
                        (sin(angle) * rOuter).toFloat()
                    ),
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round
                )
            }

            // Needle tip dot riding the value arc.
            if (fraction > 0.001f) {
                val angle = Math.toRadians((GAUGE_START + GAUGE_SWEEP * fraction).toDouble())
                val tip = center + Offset(
                    (cos(angle) * radius).toFloat(),
                    (sin(angle) * radius).toFloat()
                )
                drawCircle(rampColor(fraction).copy(alpha = 0.28f), stroke * 0.95f, tip)
                drawCircle(Color.White, stroke * 0.24f, tip)
            }
        }
    }
}

/** Samples the cockpit speed ramp at [t] in 0..1. */
private fun rampColor(t: Float): Color {
    val ramp = Cockpit.SpeedRamp
    val clamped = t.coerceIn(0f, 1f) * (ramp.size - 1)
    val i = clamped.toInt().coerceAtMost(ramp.size - 2)
    return lerp(ramp[i], ramp[i + 1], clamped - i)
}

// ═══════════════════════════════════════════════════════════════
//  ELEVATION PROFILE
// ═══════════════════════════════════════════════════════════════

/**
 * Route elevation as a filled area chart with the current position tracked by a
 * hairline. Falls back to a flat "no terrain data" baseline rather than
 * disappearing, so the console never reflows mid-run.
 */
@Composable
fun ElevationProfile(
    samples: List<Double>?,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val data = samples?.takeIf { it.size >= 2 }
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val baseline = h - 1f

            if (data == null) {
                drawLine(
                    color = Cockpit.Hairline,
                    start = Offset(0f, h * 0.72f),
                    end = Offset(w, h * 0.72f),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
                )
                return@Canvas
            }

            val lo = data.min()
            val hi = data.max()
            val span = (hi - lo).takeIf { it > 1.0 } ?: 1.0
            fun yAt(v: Double) = (baseline - ((v - lo) / span * (h * 0.82f)) - h * 0.08f).toFloat()

            val line = Path()
            val area = Path()
            area.moveTo(0f, baseline)
            data.forEachIndexed { i, v ->
                val x = w * i / (data.size - 1f)
                val y = yAt(v)
                if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
                area.lineTo(x, y)
            }
            area.lineTo(w, baseline)
            area.close()

            drawPath(
                path = area,
                brush = Brush.verticalGradient(
                    listOf(Cockpit.Violet.copy(alpha = 0.34f), Color.Transparent),
                    startY = 0f,
                    endY = h
                )
            )
            drawPath(
                path = line,
                color = Cockpit.Violet,
                style = Stroke(width = 1.8f, cap = StrokeCap.Round)
            )

            // Position tracker.
            val px = w * progress.coerceIn(0f, 1f)
            val idx = ((data.size - 1) * progress.coerceIn(0f, 1f)).roundToInt()
            val py = yAt(data[idx.coerceIn(data.indices)])
            drawLine(
                color = Cockpit.Live.copy(alpha = 0.55f),
                start = Offset(px, 0f),
                end = Offset(px, baseline),
                strokeWidth = 1.2f
            )
            drawCircle(Cockpit.Live.copy(alpha = 0.25f), 7f, Offset(px, py))
            drawCircle(Cockpit.Live, 3f, Offset(px, py))
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  PROGRESS RAIL  (scrubbable)
// ═══════════════════════════════════════════════════════════════

/**
 * Route progress with start/end nodes. Dragging seeks — useful for jumping to
 * the interesting part of a long route without waiting for it.
 */
@Composable
fun ProgressRail(
    progress: Float,
    modifier: Modifier = Modifier,
    stopFractions: List<Float> = emptyList(),
    enabled: Boolean = true,
    onSeek: (Float) -> Unit = {},
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(180),
        label = "progress"
    )
    Box(
        modifier = modifier
            .height(28.dp)
            .then(
                if (!enabled) Modifier else Modifier.pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onSeek((offset.x / size.width).coerceIn(0f, 1f))
                    }
                }
            )
            .then(
                if (!enabled) Modifier else Modifier.pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        onSeek((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                }
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cy = size.height / 2f
            val track = 4f
            val r = 5f

            drawLine(
                color = Cockpit.Hairline,
                start = Offset(r, cy),
                end = Offset(size.width - r, cy),
                strokeWidth = track,
                cap = StrokeCap.Round
            )

            val x = r + (size.width - r * 2) * animated
            if (animated > 0.001f) {
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Cockpit.Start, Cockpit.Live),
                        startX = r,
                        endX = max(x, r + 1f)
                    ),
                    start = Offset(r, cy),
                    end = Offset(x, cy),
                    strokeWidth = track,
                    cap = StrokeCap.Round
                )
            }

            // Intermediate stops, so the rail shows *where* the run will pause.
            stopFractions.forEach { fraction ->
                val sx = r + (size.width - r * 2) * fraction.coerceIn(0f, 1f)
                val passed = fraction <= animated
                drawCircle(Cockpit.Void, r * 0.9f, Offset(sx, cy))
                drawCircle(
                    color = if (passed) Cockpit.Start else Cockpit.Warn,
                    radius = r * 0.62f,
                    center = Offset(sx, cy)
                )
            }

            drawCircle(Cockpit.Start, r, Offset(r, cy))
            drawCircle(Cockpit.End, r, Offset(size.width - r, cy))

            drawCircle(Cockpit.Live.copy(alpha = 0.22f), 12f, Offset(x, cy))
            drawCircle(Color.White, 5.5f, Offset(x, cy))
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  HEADING DIAL
// ═══════════════════════════════════════════════════════════════

/** Compact compass: fixed rose, rotating needle. */
@Composable
fun HeadingDial(bearing: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = bearing,
        animationSpec = tween(240),
        label = "bearing"
    )
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension / 2 - 2f

            drawCircle(Cockpit.Hairline, r, c, style = Stroke(1.2f))
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0 - 90.0)
                val outer = r - 2f
                val inner = outer - if (i % 2 == 0) 5f else 3f
                drawLine(
                    color = if (i == 0) Cockpit.End.copy(alpha = 0.8f) else Cockpit.InkFaint,
                    start = c + Offset((cos(a) * inner).toFloat(), (sin(a) * inner).toFloat()),
                    end = c + Offset((cos(a) * outer).toFloat(), (sin(a) * outer).toFloat()),
                    strokeWidth = if (i % 2 == 0) 1.8f else 1f
                )
            }

            val a = Math.toRadians(animated.toDouble() - 90.0)
            val tip = c + Offset((cos(a) * (r - 6)).toFloat(), (sin(a) * (r - 6)).toFloat())
            val leftA = a + PI * 0.72
            val rightA = a - PI * 0.72
            val tailR = r * 0.5f
            val needle = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(
                    c.x + (cos(leftA) * tailR).toFloat(),
                    c.y + (sin(leftA) * tailR).toFloat()
                )
                lineTo(c.x, c.y)
                lineTo(
                    c.x + (cos(rightA) * tailR).toFloat(),
                    c.y + (sin(rightA) * tailR).toFloat()
                )
                close()
            }
            drawPath(needle, Cockpit.Live)
            drawCircle(Cockpit.Void, 2.5f, c)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  SPARK BARS  (fix-rate activity)
// ═══════════════════════════════════════════════════════════════

/** A tiny equaliser that animates only while fixes are actually being pushed. */
@Composable
fun InjectionActivity(active: Boolean, modifier: Modifier = Modifier) {
    val phase = if (active) pulse(700, 0f, 1f) else 0f
    Canvas(modifier) {
        val bars = 5
        val gap = size.width / (bars * 2f - 1f)
        for (i in 0 until bars) {
            val t = (phase + i * 0.18f) % 1f
            val amp = if (active) 0.35f + 0.65f * sin(t * PI).toFloat() else 0.18f
            val h = size.height * amp
            drawRoundRect(
                color = if (active) Cockpit.Live.copy(alpha = 0.55f + 0.45f * amp)
                else Cockpit.InkFaint.copy(alpha = 0.4f),
                topLeft = Offset(i * gap * 2, size.height - h),
                size = Size(gap, h),
                cornerRadius = CornerRadius(gap / 2)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  DUAL-HANDLE RANGE FIELD
// ═══════════════════════════════════════════════════════════════

/** Label + live value pair used above every range slider in the console. */
@Composable
fun RangeHeader(
    icon: String,
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = Cockpit.Live,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(icon, fontSize = 13.sp)
        Text(
            text = title,
            color = Cockpit.Ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Box(Modifier.weight(1f))
        Text(
            text = value,
            color = accent,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
    }
}
