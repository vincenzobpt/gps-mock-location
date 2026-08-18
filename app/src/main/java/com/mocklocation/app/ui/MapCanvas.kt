package com.mocklocation.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.mocklocation.app.model.LatLng
import com.mocklocation.app.model.Route
import com.mocklocation.app.model.SimulationState
import com.mocklocation.app.model.SimulationStatus
import com.mocklocation.app.ui.theme.Cockpit
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Overlay
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow

/**
 * The map layer.
 *
 * Route and vehicle are drawn by hand-rolled osmdroid overlays rather than by
 * stock markers: it is the only way to get the traveled/remaining split, the
 * glow underlay and the heading cone to sit in the right z-order and stay
 * pixel-correct at every zoom.
 */
@Composable
fun MapCanvas(
    route: Route?,
    simulation: SimulationState,
    startPoint: LatLng?,
    endPoint: LatLng?,
    waypoints: List<LatLng>,
    nightMode: Boolean,
    onLongPress: (LatLng) -> Unit,
    onUserPan: () -> Unit,
    onMapReady: (MapView) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vehicleOverlay = remember { VehicleOverlay() }
    val routeOverlay = remember { RouteOverlay() }
    val endpointOverlay = remember {
        EndpointOverlay(
            startPin = endpointBitmap(context, Cockpit.Start, PinKind.START),
            endPin = endpointBitmap(context, Cockpit.End, PinKind.END),
            stopPin = endpointBitmap(context, Cockpit.Warn, PinKind.STOP),
            density = context.resources.displayMetrics.density,
        )
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(darkTiles)
                setMultiTouchControls(true)
                isTilesScaledToDpi = true
                // The default zoom buttons duplicate our rail and fight the
                // bottom console for space.
                zoomController.setVisibility(
                    org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER
                )
                minZoomLevel = 2.5
                maxZoomLevel = 20.0
                controller.setZoom(4.5)
                controller.setCenter(GeoPoint(41.9028, 12.4964))

                overlays.add(
                    MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
                        override fun longPressHelper(p: GeoPoint?): Boolean {
                            p?.let { onLongPress(LatLng(it.latitude, it.longitude)) }
                            return true
                        }
                    })
                )
                overlays.add(routeOverlay)
                overlays.add(endpointOverlay)
                overlays.add(vehicleOverlay)

                // Any manual drag breaks camera-follow; the map keeps handling
                // the event itself, we only observe it.
                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        android.view.MotionEvent.ACTION_MOVE -> onUserPan()
                        android.view.MotionEvent.ACTION_UP -> view.performClick()
                    }
                    false
                }

                onMapReady(this)
            }
        },
        update = { map ->
            applyTileStyle(map, nightMode)

            val density = map.resources.displayMetrics.density
            routeOverlay.density = density
            vehicleOverlay.density = density
            routeOverlay.night = nightMode
            routeOverlay.update(route?.points, simulation.pointIndex, simulation.isActive)
            endpointOverlay.update(startPoint, endPoint, waypoints)
            vehicleOverlay.update(simulation)

            map.invalidate()
        },
        onRelease = { map -> map.onDetach() }
    )
}

// ═══════════════════════════════════════════════════════════════
//  TILE SOURCES
// ═══════════════════════════════════════════════════════════════

/**
 * A real dark basemap rather than a colour-matrix filter over Mapnik.
 *
 * Inverting Mapnik is the usual osmdroid trick and it does not work here:
 * Mapnik draws roads white, so inversion turns them black on a barely-lighter
 * ground and the whole map collapses into a flat dark rectangle. Dark Matter is
 * cartography that was designed dark, so roads stay legible and the accent
 * colours on top keep their contrast.
 */
private val darkTiles: OnlineTileSourceBase by lazy {
    XYTileSource(
        "CartoDarkMatter",
        0, 20, 256, ".png",
        arrayOf(
            "https://a.basemaps.cartocdn.com/dark_all/",
            "https://b.basemaps.cartocdn.com/dark_all/",
            "https://c.basemaps.cartocdn.com/dark_all/",
        ),
        "© OpenStreetMap contributors, © CARTO"
    )
}

/** Slight desaturation on the day map so the route accents stay dominant. */
private val dayFilter: ColorMatrixColorFilter by lazy {
    ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0.82f) })
}

private fun applyTileStyle(map: MapView, night: Boolean) {
    val wanted = if (night) darkTiles else TileSourceFactory.MAPNIK
    if (map.tileProvider.tileSource.name() != wanted.name()) {
        map.setTileSource(wanted)
    }
    map.overlayManager.tilesOverlay?.setColorFilter(if (night) null else dayFilter)
    map.setBackgroundColor(if (night) Cockpit.Void.toArgb() else AndroidColor.WHITE)
}

// ═══════════════════════════════════════════════════════════════
//  ROUTE OVERLAY
// ═══════════════════════════════════════════════════════════════

/**
 * Draws the route in three passes: a wide low-alpha glow, the remaining path as
 * a dashed neutral, and the traveled path as the live accent. Splitting on the
 * vertex index the engine already reports keeps the split exact.
 *
 * Every vertex is projected once per frame into [projected], and the three
 * passes are filled from that. Building each pass straight from the Projection
 * walked the polyline about twice a frame, and toPixels — which does the actual
 * Mercator work — was the expensive half. The Paths and the dash effect are
 * kept and reused for the same reason: this runs on the main thread at the fix
 * rate, over routes of a few thousand vertices.
 */
private class RouteOverlay : Overlay() {

    private var points: List<GeoPoint> = emptyList()
    /** Identity of the source list, so we only re-project when the route changes. */
    private var source: List<LatLng>? = null
    private var travelledIndex = 0
    private var active = false

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val remainingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val travelledPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val scratch = android.graphics.Point()

    /** Screen-space x,y pairs for every vertex, refilled once per frame. */
    private var projected = FloatArray(0)

    // Reused every frame: rewind() keeps the backing allocation, unlike Path().
    private val glowPath = Path()
    private val remainingPath = Path()
    private val travelledPath = Path()

    private var dashEffect: DashPathEffect? = null
    private var dashDensity = 0f

    /** Screen density, so stroke widths are dp-consistent across devices. */
    var density: Float = 3f

    /** Drives the palette — the same colours are invisible on the other basemap. */
    var night: Boolean = true

    fun update(routePoints: List<LatLng>?, index: Int, isActive: Boolean) {
        // Re-projecting thousands of vertices at the fix rate would burn the
        // main thread; the route only changes when a new one is calculated.
        if (source !== routePoints) {
            source = routePoints
            points = routePoints?.map { GeoPoint(it.latitude, it.longitude) } ?: emptyList()
        }
        travelledIndex = index.coerceIn(0, (points.size - 1).coerceAtLeast(0))
        active = isActive
    }

    override fun draw(canvas: AndroidCanvas, projection: Projection) {
        if (points.size < 2) return

        // The first pass is a casing, not decoration. On the dark basemap it is
        // an accent glow; on the light one it has to be a dark halo, because a
        // pale line over pale cartography reads as no line at all — which is
        // exactly how the route "disappeared" in light mode.
        glowPaint.color = if (night) {
            Cockpit.Live.copy(alpha = if (active) 0.20f else 0.10f).toArgb()
        } else {
            Color(0xFF0F172A).copy(alpha = 0.34f).toArgb()
        }
        glowPaint.strokeWidth = if (night) 7f * density else 5.4f * density

        remainingPaint.color = if (night) {
            Cockpit.InkMuted.copy(alpha = 0.55f).toArgb()
        } else {
            Color(0xFF1E293B).copy(alpha = 0.85f).toArgb()
        }
        remainingPaint.strokeWidth = if (night) 1.8f * density else 2.2f * density
        remainingPaint.pathEffect = dashEffect()

        travelledPaint.color = if (night) Cockpit.Live.toArgb() else Color(0xFF0369A1).toArgb()
        travelledPaint.strokeWidth = 2.8f * density

        projectVertices(projection)

        canvas.drawPath(fill(glowPath, 0, points.lastIndex), glowPaint)

        if (travelledIndex < points.lastIndex) {
            canvas.drawPath(fill(remainingPath, travelledIndex, points.lastIndex), remainingPaint)
        }
        if (travelledIndex > 0) {
            canvas.drawPath(fill(travelledPath, 0, travelledIndex), travelledPaint)
        }
    }

    /** Projects every vertex once, into a buffer that survives across frames. */
    private fun projectVertices(projection: Projection) {
        if (projected.size < points.size * 2) projected = FloatArray(points.size * 2)
        for (i in points.indices) {
            projection.toPixels(points[i], scratch)
            projected[i * 2] = scratch.x.toFloat()
            projected[i * 2 + 1] = scratch.y.toFloat()
        }
    }

    /** Rewinds [path] and refills it from the cached projection. */
    private fun fill(path: Path, from: Int, to: Int): Path {
        path.rewind()
        path.moveTo(projected[from * 2], projected[from * 2 + 1])
        for (i in from + 1..to) {
            path.lineTo(projected[i * 2], projected[i * 2 + 1])
        }
        return path
    }

    /** The dash pattern only depends on density, so it is built once per device. */
    private fun dashEffect(): DashPathEffect {
        dashEffect?.takeIf { dashDensity == density }?.let { return it }
        return DashPathEffect(floatArrayOf(5f * density, 4f * density), 0f).also {
            dashEffect = it
            dashDensity = density
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  VEHICLE OVERLAY
// ═══════════════════════════════════════════════════════════════

/**
 * The moving fix: an accuracy disc sized in real metres, an expanding ping, a
 * heading cone and a solid core. Everything is projected per frame so the
 * accuracy disc stays geographically honest at every zoom level.
 */
private class VehicleOverlay : Overlay() {

    private var state: SimulationState? = null
    private val scratch = android.graphics.Point()

    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val conePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val coreRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    var density: Float = 3f

    fun update(newState: SimulationState) {
        state = newState.takeIf { it.position != null }
    }

    override fun draw(canvas: AndroidCanvas, projection: Projection) {
        val s = state ?: return
        val pos = s.position ?: return
        projection.toPixels(GeoPoint(pos.latitude, pos.longitude), scratch)
        val cx = scratch.x.toFloat()
        val cy = scratch.y.toFloat()

        val playing = s.status == SimulationStatus.PLAYING
        val accent = if (playing) Cockpit.Live else Cockpit.InkMuted

        // Accuracy disc, sized from real ground resolution.
        val metresPerPixel = groundResolution(pos.latitude, projection.zoomLevel)
        val accuracyPx = (s.accuracyMeters / metresPerPixel).toFloat()
            .coerceIn(11f * density, 90f * density)

        discPaint.shader = RadialGradient(
            cx, cy, accuracyPx,
            accent.copy(alpha = 0.22f).toArgb(),
            accent.copy(alpha = 0.02f).toArgb(),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, accuracyPx, discPaint)

        ringPaint.color = accent.copy(alpha = 0.35f).toArgb()
        ringPaint.strokeWidth = 0.7f * density
        canvas.drawCircle(cx, cy, accuracyPx, ringPaint)

        // Expanding ping — the only animated element, driven off the clock so it
        // stays smooth regardless of the fix rate.
        if (playing) {
            val phase = (SystemClock.uptimeMillis() % PING_PERIOD_MS) / PING_PERIOD_MS.toFloat()
            val pingRadius = accuracyPx * (0.35f + phase * 1.1f)
            pingPaint.color = accent.copy(alpha = 0.45f * (1f - phase)).toArgb()
            pingPaint.strokeWidth = density * (1f + 1.2f * (1f - phase))
            canvas.drawCircle(cx, cy, pingRadius, pingPaint)
        }

        // Heading cone.
        val coneRadius = 17f * density
        conePaint.shader = RadialGradient(
            cx, cy, coneRadius,
            accent.copy(alpha = 0.55f).toArgb(),
            accent.copy(alpha = 0f).toArgb(),
            Shader.TileMode.CLAMP
        )
        canvas.save()
        // Screen space is y-down and bearings are clockwise from north, so the
        // sweep starts 90° behind the heading.
        canvas.rotate(s.bearingDegrees - 90f, cx, cy)
        val cone = Path().apply {
            moveTo(cx, cy)
            arcTo(cx - coneRadius, cy - coneRadius, cx + coneRadius, cy + coneRadius, -26f, 52f, false)
            close()
        }
        canvas.drawPath(cone, conePaint)
        canvas.restore()

        // Core.
        val coreR = 3.4f * density
        corePaint.color = AndroidColor.WHITE
        coreRingPaint.color = accent.toArgb()
        coreRingPaint.strokeWidth = 1.5f * density
        canvas.drawCircle(cx, cy, coreR, corePaint)
        canvas.drawCircle(cx, cy, coreR, coreRingPaint)
    }

    /** Metres per pixel at [latitude] for a Web-Mercator tile pyramid. */
    private fun groundResolution(latitude: Double, zoom: Double): Double {
        val safeZoom = zoom.coerceIn(1.0, 22.0)
        return 156_543.03392 * cos(Math.toRadians(latitude)) / 2.0.pow(safeZoom)
    }

    private companion object {
        const val PING_PERIOD_MS = 1_800L
    }
}

// ═══════════════════════════════════════════════════════════════
//  ENDPOINT PINS
// ═══════════════════════════════════════════════════════════════

/**
 * Start/end pins as a single overlay. Stock [Marker]s would mean allocating and
 * re-adding two objects on every fix; here the bitmaps are rasterised once and
 * only blitted.
 */
private class EndpointOverlay(
    private val startPin: Bitmap,
    private val endPin: Bitmap,
    private val stopPin: Bitmap,
    private val density: Float,
) : Overlay() {

    private var start: LatLng? = null
    private var end: LatLng? = null
    private var stops: List<LatLng> = emptyList()

    private val scratch = android.graphics.Point()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Cockpit.Void.toArgb()
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    fun update(newStart: LatLng?, newEnd: LatLng?, newStops: List<LatLng>) {
        start = newStart
        end = newEnd
        stops = newStops
    }

    override fun draw(canvas: AndroidCanvas, projection: Projection) {
        // Stops sit below the endpoints so an overlapping start/end stays readable.
        stops.forEachIndexed { index, at ->
            blit(canvas, projection, at, stopPin)
            // The ordinal is drawn live rather than baked into a bitmap per
            // stop — the list is short and it survives reordering for free.
            labelPaint.textSize = stopPin.width * 0.34f
            val cx = scratch.x.toFloat()
            val headCy = scratch.y - stopPin.height + stopPin.width * 0.36f + 2f * density
            val yOffset = (labelPaint.descent() + labelPaint.ascent()) / 2f
            canvas.drawText("${index + 1}", cx, headCy - yOffset, labelPaint)
        }
        start?.let { blit(canvas, projection, it, startPin) }
        end?.let { blit(canvas, projection, it, endPin) }
    }

    private fun blit(canvas: AndroidCanvas, projection: Projection, at: LatLng, pin: Bitmap) {
        projection.toPixels(GeoPoint(at.latitude, at.longitude), scratch)
        canvas.drawBitmap(
            pin,
            scratch.x - pin.width / 2f,
            // The tip of the pin marks the point, so anchor at the bottom.
            scratch.y - pin.height.toFloat(),
            paint
        )
    }
}

private enum class PinKind { START, END, STOP }

/** Teardrop pin with a soft halo, rasterised once and reused. */
private fun endpointBitmap(context: Context, color: Color, kind: PinKind): Bitmap {
    val density = context.resources.displayMetrics.density
    val w = (34 * density).toInt()
    val h = (44 * density).toInt()
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val cx = w / 2f
    val headR = w * 0.36f
    val cy = headR + 2f * density

    // Halo.
    paint.shader = RadialGradient(
        cx, cy, headR * 2.1f,
        color.copy(alpha = 0.30f).toArgb(),
        color.copy(alpha = 0f).toArgb(),
        Shader.TileMode.CLAMP
    )
    canvas.drawCircle(cx, cy, headR * 2.1f, paint)
    paint.shader = null

    // Stem.
    paint.color = color.toArgb()
    paint.style = Paint.Style.FILL
    val stem = Path().apply {
        moveTo(cx - headR * 0.42f, cy + headR * 0.72f)
        lineTo(cx, h - 3f * density)
        lineTo(cx + headR * 0.42f, cy + headR * 0.72f)
        close()
    }
    canvas.drawPath(stem, paint)

    // Head.
    canvas.drawCircle(cx, cy, headR, paint)

    when (kind) {
        // START keeps a dark core with a filled dot, END a dark core with a
        // ring; STOP stays solid so the ordinal drawn on top of it reads.
        PinKind.START -> {
            paint.color = Cockpit.Void.toArgb()
            canvas.drawCircle(cx, cy, headR * 0.62f, paint)
            paint.color = color.toArgb()
            canvas.drawCircle(cx, cy, headR * 0.30f, paint)
        }
        PinKind.END -> {
            paint.color = Cockpit.Void.toArgb()
            canvas.drawCircle(cx, cy, headR * 0.62f, paint)
            paint.color = color.toArgb()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = headR * 0.20f
            canvas.drawCircle(cx, cy, headR * 0.30f, paint)
        }
        PinKind.STOP -> {
            paint.color = Cockpit.Void.toArgb()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = headR * 0.14f
            canvas.drawCircle(cx, cy, headR * 0.80f, paint)
        }
    }

    return bitmap
}

// ═══════════════════════════════════════════════════════════════
//  CAMERA HELPERS
// ═══════════════════════════════════════════════════════════════

/** Frames [points] with padding, deferred until the view has been laid out. */
fun MapView.fitTo(points: List<LatLng>, paddingPx: Int) {
    if (points.isEmpty()) return
    val box = BoundingBox.fromGeoPoints(points.map { GeoPoint(it.latitude, it.longitude) })
    post {
        runCatching {
            zoomToBoundingBox(box, true, paddingPx, min(17.0, maxZoomLevel), 600L)
        }
    }
}
