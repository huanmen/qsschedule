package cn.qztc.timetable.feature.timetable

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot

private val closedFeatureKinds = setOf("building", "green", "water", "sport", "parking", "poi-area")
private val terrainKinds = setOf("water", "green", "sport", "parking")
private val majorRoadSubtypes = setOf("primary", "secondary", "tertiary")
private val localRoadSubtypes = setOf("service", "residential")

internal fun rasterizeCampusMap(
    dataset: CampusMapDataset,
    sizePx: Int,
    dark: Boolean,
): Bitmap {
    val width = sizePx.coerceIn(512, 4096)
    val height = sizePx.coerceIn(512, 4096)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    val canvas = Canvas(bitmap)
    val minLat = dataset.bounds.minLatitude
    val maxLat = dataset.bounds.maxLatitude
    val minLon = dataset.bounds.minLongitude
    val maxLon = dataset.bounds.maxLongitude
    val latSpan = (maxLat - minLat).coerceAtLeast(.0001)
    val lonSpan = (maxLon - minLon).coerceAtLeast(.0001)

    fun point(coordinate: CampusCoordinate): Offset {
        val x = ((coordinate.longitude - minLon) / lonSpan).toFloat() * width
        val y = (1f - ((coordinate.latitude - minLat) / latSpan).toFloat()) * height
        return Offset(x, y)
    }

    fun featurePath(feature: CampusMapFeature): Path {
        val path = Path()
        feature.points.forEachIndexed { index, coordinate ->
            val offset = point(coordinate)
            if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
        }
        if (feature.kind in closedFeatureKinds) path.close()
        return path
    }

    canvas.drawColor(if (dark) 0xFF1D2527.toInt() else 0xFFF2F1ED.toInt())
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }

    dataset.features.filter { it.kind in terrainKinds }.forEach { feature ->
        fill.color = when (feature.kind) {
            "water" -> if (dark) 0x3DC4E3EE else 0xC2C4E3EE.toInt()
            "sport" -> if (dark) 0x3DD5E8C8 else 0xC2D5E8C8.toInt()
            "parking" -> if (dark) 0x3DE1E0DB else 0xC2E1E0DB.toInt()
            else -> if (dark) 0x3DD4E9CE else 0xC2D4E9CE.toInt()
        }
        canvas.drawPath(featurePath(feature), fill)
    }
    if (dataset.campusBoundary.size >= 3) {
        val boundary = Path()
        dataset.campusBoundary.forEachIndexed { index, coordinate ->
            val offset = point(coordinate)
            if (index == 0) boundary.moveTo(offset.x, offset.y) else boundary.lineTo(offset.x, offset.y)
        }
        boundary.close()
        fill.color = if (dark) 0x1EE8F4E2 else 0x6BE8F4E2
        canvas.drawPath(boundary, fill)
        stroke.color = 0xCCB4D0A8.toInt()
        stroke.strokeWidth = 2.2f
        canvas.drawPath(boundary, stroke)
    }
    dataset.features.filter { it.kind == "building" }.forEach { feature ->
        if (feature.points.size < 3) return@forEach
        fill.color = if (dark) 0xFF2C3436.toInt() else 0xFFF0F1EE.toInt()
        canvas.drawPath(featurePath(feature), fill)
        stroke.color = if (dark) 0xFF4A5456.toInt() else 0xFFC4CDC7.toInt()
        stroke.strokeWidth = 1.15f
        canvas.drawPath(featurePath(feature), stroke)
    }
    dataset.features.filter { it.kind == "road" || it.kind == "path" }.forEach { feature ->
        if (feature.points.size < 2) return@forEach
        val path = Path()
        feature.points.forEachIndexed { index, coordinate ->
            val offset = point(coordinate)
            if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
        }
        val widthPx = when {
            feature.kind == "path" -> 2.2f
            feature.subtype in majorRoadSubtypes -> 7.4f
            feature.subtype in localRoadSubtypes -> 4.8f
            else -> 3.8f
        }
        if (feature.kind == "road") {
            stroke.color = 0xB8D7AD75.toInt()
            stroke.strokeWidth = widthPx + 3f
            canvas.drawPath(path, stroke)
        }
        stroke.color = when {
            feature.kind == "path" -> 0xFF9DB9A8.toInt()
            feature.subtype in majorRoadSubtypes -> 0xFFF4D49B.toInt()
            else -> 0xFFFFFFFF.toInt()
        }
        stroke.strokeWidth = widthPx
        canvas.drawPath(path, stroke)
    }
    return bitmap
}

internal fun nearestCampusPlace(
    tap: Offset,
    places: List<CampusPoint>,
    layout: CampusMapLayout,
    bounds: CampusMapBounds,
    zoom: Float,
    pan: Offset,
    rotation: Float,
    maxDistancePx: Float,
): CampusPoint? = nearestCampusMarker(
    tap = tap,
    markers = places.map { CampusMapMarker(id = it.id, name = it.name, coordinate = it.coordinate, place = it) },
    layout = layout,
    bounds = bounds,
    zoom = zoom,
    pan = pan,
    rotation = rotation,
    maxDistancePx = maxDistancePx,
)?.place

internal fun nearestCampusMarker(
    tap: Offset,
    markers: List<CampusMapMarker>,
    layout: CampusMapLayout,
    bounds: CampusMapBounds,
    zoom: Float,
    pan: Offset,
    rotation: Float,
    maxDistancePx: Float,
): CampusMapMarker? {
    var best: CampusMapMarker? = null
    var bestDistance = maxDistancePx
    markers.forEach { marker ->
        val screen = campusCoordinateToScreen(marker.coordinate, layout, bounds, zoom, pan, rotation)
        val distance = hypot((screen.x - tap.x).toDouble(), (screen.y - tap.y).toDouble()).toFloat()
        if (distance <= bestDistance) {
            bestDistance = distance
            best = marker
        }
    }
    return best
}
