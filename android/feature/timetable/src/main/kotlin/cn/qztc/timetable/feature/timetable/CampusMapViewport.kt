package cn.qztc.timetable.feature.timetable

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.cos
import kotlin.math.sin

internal data class CampusMapLayout(
    val mapLeft: Float,
    val mapTop: Float,
    val mapWidth: Float,
    val mapHeight: Float,
    val mapCenter: Offset,
)

internal fun campusMapLayout(
    canvasSize: Size,
    bounds: CampusMapBounds,
    paddingPx: Float,
): CampusMapLayout {
    val minLat = bounds.minLatitude
    val maxLat = bounds.maxLatitude
    val minLon = bounds.minLongitude
    val maxLon = bounds.maxLongitude
    val latSpan = (maxLat - minLat).coerceAtLeast(.0001)
    val lonSpan = (maxLon - minLon).coerceAtLeast(.0001)
    val mapAspect = (lonSpan * kotlin.math.cos(Math.toRadians((minLat + maxLat) / 2)) / latSpan).toFloat()
    val canvasAspect = canvasSize.width / canvasSize.height.coerceAtLeast(1f)
    val mapWidth = if (mapAspect >= canvasAspect) canvasSize.width - 2 * paddingPx else (canvasSize.height - 2 * paddingPx) * mapAspect
    val mapHeight = if (mapAspect >= canvasAspect) (canvasSize.width - 2 * paddingPx) / mapAspect else canvasSize.height - 2 * paddingPx
    return CampusMapLayout(
        mapLeft = (canvasSize.width - mapWidth) / 2f,
        mapTop = (canvasSize.height - mapHeight) / 2f,
        mapWidth = mapWidth,
        mapHeight = mapHeight,
        mapCenter = Offset(canvasSize.width / 2f, canvasSize.height / 2f),
    )
}

internal fun rotateAround(point: Offset, center: Offset, degrees: Float): Offset {
    if (degrees == 0f) return point
    val rad = Math.toRadians(degrees.toDouble())
    val cos = cos(rad).toFloat()
    val sin = sin(rad).toFloat()
    val dx = point.x - center.x
    val dy = point.y - center.y
    return Offset(center.x + dx * cos - dy * sin, center.y + dx * sin + dy * cos)
}

internal fun campusCoordinateToScreen(
    coordinate: CampusCoordinate,
    layout: CampusMapLayout,
    bounds: CampusMapBounds,
    zoom: Float,
    pan: Offset,
    rotation: Float,
): Offset {
    val latSpan = (bounds.maxLatitude - bounds.minLatitude).coerceAtLeast(.0001)
    val lonSpan = (bounds.maxLongitude - bounds.minLongitude).coerceAtLeast(.0001)
    val x = ((coordinate.longitude - bounds.minLongitude) / lonSpan).toFloat()
    val y = (1f - (coordinate.latitude - bounds.minLatitude) / latSpan).toFloat()
    val base = Offset(layout.mapLeft + x * layout.mapWidth, layout.mapTop + y * layout.mapHeight)
    val zoomed = layout.mapCenter + (base - layout.mapCenter) * zoom
    return rotateAround(zoomed, layout.mapCenter, rotation) + pan
}

internal fun campusScreenToCoordinate(
    tap: Offset,
    layout: CampusMapLayout,
    bounds: CampusMapBounds,
    zoom: Float,
    pan: Offset,
    rotation: Float,
): CampusCoordinate {
    val unrotated = rotateAround(tap - pan, layout.mapCenter, -rotation)
    val base = layout.mapCenter + (unrotated - layout.mapCenter) / zoom
    val x = ((base.x - layout.mapLeft) / layout.mapWidth).toDouble()
    val y = ((base.y - layout.mapTop) / layout.mapHeight).toDouble()
    val latSpan = (bounds.maxLatitude - bounds.minLatitude).coerceAtLeast(.0001)
    val lonSpan = (bounds.maxLongitude - bounds.minLongitude).coerceAtLeast(.0001)
    return CampusCoordinate(
        latitude = bounds.minLatitude + (1.0 - y) * latSpan,
        longitude = bounds.minLongitude + x * lonSpan,
    )
}

/** Keep the viewport center inside campus bounds so the map cannot be dragged off school. */
internal fun clampCampusPan(
    pan: Offset,
    zoom: Float,
    rotation: Float,
    canvasSize: Size,
    bounds: CampusMapBounds,
    paddingPx: Float,
): Offset {
    val layout = campusMapLayout(canvasSize, bounds, paddingPx)
    val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
    val coord = campusScreenToCoordinate(center, layout, bounds, zoom, pan, rotation)
    if (bounds.contains(coord)) return pan
    val clamped = bounds.clamp(coord)
    val screenWithoutPan = campusCoordinateToScreen(clamped, layout, bounds, zoom, Offset.Zero, rotation)
    return center - screenWithoutPan
}
