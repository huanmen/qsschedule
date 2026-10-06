package cn.qztc.timetable.feature.timetable

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcCorners
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val CampusMinZoom = 1.35f
private const val CampusMaxZoom = 7.5f
private const val CampusDefaultZoom = 1.85f

@Composable
internal fun CampusMapCanvas(
    dataset: CampusMapDataset,
    route: List<CampusCoordinate>,
    current: CampusCoordinate,
    currentLabel: String,
    destination: CampusPoint?,
    landmarks: List<CampusPoint>,
    modifier: Modifier,
    onPlaceTap: ((CampusPoint) -> Unit)? = null,
    headingDegrees: Float? = null,
    followUser: Boolean = false,
    revealedIds: Set<String> = emptySet(),
    expandedZones: Set<String> = emptySet(),
    onZoneTap: ((String) -> Unit)? = null,
    onUserMove: (() -> Unit)? = null,
) {
    val colors = LocalQztcColors.current
    val density = LocalDensity.current
    val paddingPx = with(density) { 18.dp.toPx() }
    var zoom by remember(dataset) { mutableStateOf(CampusDefaultZoom) }
    var pan by remember(dataset) { mutableStateOf(Offset.Zero) }
    var centered by remember(dataset) { mutableStateOf(false) }
    var basemap by remember(dataset, colors.isDark) { mutableStateOf<ImageBitmap?>(null) }
    val onPlaceTapState = rememberUpdatedState(onPlaceTap)
    val onZoneTapState = rememberUpdatedState(onZoneTap)
    val onUserMoveState = rememberUpdatedState(onUserMove)
    val markers = remember(landmarks, destination, revealedIds, expandedZones) {
        CampusDormZones.visibleMarkers(landmarks, destination, revealedIds, expandedZones)
    }
    val markersState = rememberUpdatedState(markers)
    val zoomState = rememberUpdatedState(zoom)
    val panState = rememberUpdatedState(pan)
    LaunchedEffect(dataset, colors.isDark) {
        val image = withContext(Dispatchers.Default) {
            rasterizeCampusMap(dataset, sizePx = 2048, dark = colors.isDark).asImageBitmap()
        }
        basemap = image
    }
    BoxWithConstraints(modifier) {
        val canvasSize = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        LaunchedEffect(canvasSize, current, followUser, dataset) {
            if (canvasSize.width <= 0f || canvasSize.height <= 0f) return@LaunchedEffect
            if (!centered || followUser) {
                val layout = campusMapLayout(canvasSize, dataset.bounds, paddingPx)
                val screen = campusCoordinateToScreen(current, layout, dataset.bounds, zoom, Offset.Zero, 0f)
                pan = clampCampusPan(
                    pan = Offset(canvasSize.width / 2f, canvasSize.height / 2f) - screen,
                    zoom = zoom,
                    rotation = 0f,
                    canvasSize = canvasSize,
                    bounds = dataset.bounds,
                    paddingPx = paddingPx,
                )
                centered = true
            }
        }
        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            if (kotlin.math.abs(panChange.x) > 3f || kotlin.math.abs(panChange.y) > 3f || zoomChange < 0.98f || zoomChange > 1.02f) {
                onUserMoveState.value?.invoke()
            }
            val nextZoom = (zoom * zoomChange).coerceIn(CampusMinZoom, CampusMaxZoom)
            val nextPan = pan + panChange
            zoom = nextZoom
            pan = if (canvasSize.width > 0f && canvasSize.height > 0f) {
                clampCampusPan(nextPan, nextZoom, 0f, canvasSize, dataset.bounds, paddingPx)
            } else {
                nextPan
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(dataset) {
                    detectTapGestures(
                        onTap = { offset ->
                            val layout = campusMapLayout(
                                canvasSize = Size(size.width.toFloat(), size.height.toFloat()),
                                bounds = dataset.bounds,
                                paddingPx = paddingPx,
                            )
                            val tapped = nearestCampusMarker(
                                tap = offset,
                                markers = markersState.value,
                                layout = layout,
                                bounds = dataset.bounds,
                                zoom = zoomState.value,
                                pan = panState.value,
                                rotation = 0f,
                                maxDistancePx = with(density) { 32.dp.toPx() },
                            )
                            when {
                                tapped?.place != null -> onPlaceTapState.value?.invoke(tapped.place)
                                tapped?.zone != null -> onZoneTapState.value?.invoke(tapped.zone)
                            }
                        },
                    )
                },
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(QztcCorners.content))
                    .transformable(transformState, lockRotationOnZoomPan = true),
            ) {
                val layout = campusMapLayout(size, dataset.bounds, 18.dp.toPx())
                fun mapOffset(coordinate: CampusCoordinate): Offset = campusCoordinateToScreen(
                    coordinate = coordinate,
                    layout = layout,
                    bounds = dataset.bounds,
                    zoom = zoom,
                    pan = pan,
                    rotation = 0f,
                )
                drawRect(if (colors.isDark) Color(0xFF1D2527) else Color(0xFFF2F1ED))
                val image = basemap
                if (image != null) {
                    withTransform({
                        translate(pan.x, pan.y)
                        scale(zoom, zoom, layout.mapCenter)
                    }) {
                        drawImage(
                            image = image,
                            dstOffset = IntOffset(layout.mapLeft.roundToInt(), layout.mapTop.roundToInt()),
                            dstSize = IntSize(layout.mapWidth.roundToInt(), layout.mapHeight.roundToInt()),
                        )
                    }
                }
                if (route.size >= 2) {
                    val routeLine = Path().apply {
                        route.forEachIndexed { index, point ->
                            val offset = mapOffset(point)
                            if (index == 0) moveTo(offset.x, offset.y) else lineTo(offset.x, offset.y)
                        }
                    }
                    drawPath(routeLine, Color.White.copy(alpha = .98f), style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                    drawPath(routeLine, Color(0xFF3D8FE6), style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
                val paint = android.graphics.Paint().apply {
                    color = colors.ink.toArgb()
                    textSize = 10.dp.toPx()
                    isAntiAlias = true
                }
                val labelBackgroundPaint = android.graphics.Paint().apply {
                    color = colors.solid.copy(alpha = .94f).toArgb()
                    isAntiAlias = true
                }
                val usedLabels = mutableListOf<android.graphics.RectF>()
                fun drawName(center: Offset, label: String, force: Boolean) {
                    val labelWidth = paint.measureText(label) + 12.dp.toPx()
                    val rect = android.graphics.RectF(
                        center.x + 10.dp.toPx(),
                        center.y - 9.dp.toPx(),
                        center.x + 10.dp.toPx() + labelWidth,
                        center.y + 9.dp.toPx(),
                    )
                    if (!force && usedLabels.any { android.graphics.RectF.intersects(it, rect) }) return
                    usedLabels += rect
                    drawContext.canvas.nativeCanvas.drawRoundRect(rect, 7.dp.toPx(), 7.dp.toPx(), labelBackgroundPaint)
                    val baseline = rect.centerY() - (paint.ascent() + paint.descent()) / 2f
                    drawContext.canvas.nativeCanvas.drawText(label, rect.left + 6.dp.toPx(), baseline, paint)
                }
                val labeledPlaces = markers
                    .filter { it.id != destination?.id }
                    .sortedBy { marker ->
                        when {
                            marker.zone != null && marker.place == null -> 0
                            marker.id in campusPriorityPlaceIds -> 1
                            marker.place?.category == "building" -> 2
                            marker.place?.category == "entrance" -> 3
                            else -> 4
                        }
                    }
                labeledPlaces.forEach { landmark ->
                    val center = mapOffset(landmark.coordinate)
                    drawCircle(Color.White.copy(alpha = .94f), 6.dp.toPx(), center)
                    drawCircle(Color(0xFF33826F), 3.5.dp.toPx(), center)
                    drawName(center, landmark.name, force = landmark.zone != null && landmark.place == null)
                }
                destination?.let { place ->
                    val center = mapOffset(place.coordinate)
                    drawPin(center, colors.danger)
                    drawName(center, place.name, force = true)
                }
                val currentCenter = mapOffset(current)
                if (headingDegrees != null) {
                    drawHeadingArrow(currentCenter, headingDegrees, colors.accent)
                } else {
                    drawPin(currentCenter, colors.accent)
                }
                drawName(currentCenter, currentLabel, force = true)
                val scalePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color(0xFF69757C).toArgb()
                    textSize = 9.dp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                val scaleStart = Offset(18.dp.toPx(), size.height - 22.dp.toPx())
                val scaleEnd = Offset(scaleStart.x + 64.dp.toPx(), scaleStart.y)
                drawLine(Color(0xFF69757C), scaleStart, scaleEnd, strokeWidth = 2.dp.toPx())
                drawLine(Color(0xFF69757C), Offset(scaleStart.x, scaleStart.y - 4.dp.toPx()), Offset(scaleStart.x, scaleStart.y + 4.dp.toPx()), strokeWidth = 2.dp.toPx())
                drawLine(Color(0xFF69757C), Offset(scaleEnd.x, scaleEnd.y - 4.dp.toPx()), Offset(scaleEnd.x, scaleEnd.y + 4.dp.toPx()), strokeWidth = 2.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText("100 m", (scaleStart.x + scaleEnd.x) / 2f, scaleStart.y - 8.dp.toPx(), scalePaint)
                val compassCenter = Offset(size.width - 30.dp.toPx(), 28.dp.toPx())
                drawCircle(Color.White.copy(alpha = .86f), 17.dp.toPx(), compassCenter)
                drawContext.canvas.nativeCanvas.drawText("N", compassCenter.x, compassCenter.y - 21.dp.toPx(), scalePaint)
                val north = Path().apply {
                    moveTo(compassCenter.x, compassCenter.y - 12.dp.toPx())
                    lineTo(compassCenter.x - 4.dp.toPx(), compassCenter.y + 4.dp.toPx())
                    lineTo(compassCenter.x, compassCenter.y + 1.dp.toPx())
                    lineTo(compassCenter.x + 4.dp.toPx(), compassCenter.y + 4.dp.toPx())
                    close()
                }
                drawPath(north, Color(0xFFC95E59))
            }
        }
    }
}

private fun DrawScope.drawPin(center: Offset, color: Color) {
    val outerPin = Path().apply {
        moveTo(center.x, center.y + 12.dp.toPx())
        cubicTo(center.x - 12.dp.toPx(), center.y - 3.dp.toPx(), center.x - 9.dp.toPx(), center.y - 14.dp.toPx(), center.x, center.y - 14.dp.toPx())
        cubicTo(center.x + 9.dp.toPx(), center.y - 14.dp.toPx(), center.x + 12.dp.toPx(), center.y - 3.dp.toPx(), center.x, center.y + 12.dp.toPx())
        close()
    }
    drawPath(outerPin, Color.White.copy(alpha = .98f))
    val innerPin = Path().apply {
        moveTo(center.x, center.y + 9.dp.toPx())
        cubicTo(center.x - 9.dp.toPx(), center.y - 3.dp.toPx(), center.x - 7.dp.toPx(), center.y - 11.dp.toPx(), center.x, center.y - 11.dp.toPx())
        cubicTo(center.x + 7.dp.toPx(), center.y - 11.dp.toPx(), center.x + 9.dp.toPx(), center.y - 3.dp.toPx(), center.x, center.y + 9.dp.toPx())
        close()
    }
    drawPath(innerPin, color)
    drawCircle(Color.White.copy(alpha = .96f), 4.dp.toPx(), Offset(center.x, center.y - 2.dp.toPx()))
}

private fun DrawScope.drawHeadingArrow(center: Offset, headingDegrees: Float, color: Color) {
    val radians = Math.toRadians(headingDegrees.toDouble())
    val cos = cos(radians).toFloat()
    val sin = sin(radians).toFloat()
    fun rotate(point: Offset): Offset {
        val dx = point.x - center.x
        val dy = point.y - center.y
        return Offset(center.x + dx * cos - dy * sin, center.y + dx * sin + dy * cos)
    }
    val tip = rotate(Offset(center.x, center.y - 16.dp.toPx()))
    val left = rotate(Offset(center.x - 8.dp.toPx(), center.y + 10.dp.toPx()))
    val right = rotate(Offset(center.x + 8.dp.toPx(), center.y + 10.dp.toPx()))
    val notch = rotate(Offset(center.x, center.y + 4.dp.toPx()))
    val arrow = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(left.x, left.y)
        lineTo(notch.x, notch.y)
        lineTo(right.x, right.y)
        close()
    }
    drawCircle(Color.White.copy(alpha = .9f), 13.dp.toPx(), center)
    drawPath(arrow, color)
    drawPath(arrow, Color.White.copy(alpha = .92f), style = Stroke(width = 1.6.dp.toPx()))
}

private val campusPriorityPlaceIds = setOf(
    "qnu-chaoyang",
    "qnu-rongmao",
    "qnu-qianshu",
    "qnu-wenke",
    "qnu-main-entrance",
    "qnu-education",
    "qnu-ligong",
    "qnu-nanyi",
    "qnu-chenghui",
    "qnu-sunyatsen",
    "qnu-belltower",
)
