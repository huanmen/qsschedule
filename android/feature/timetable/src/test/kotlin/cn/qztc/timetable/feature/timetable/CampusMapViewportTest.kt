package cn.qztc.timetable.feature.timetable

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CampusMapViewportTest {
    private val bounds = CampusMapBounds(
        minLatitude = 24.0,
        minLongitude = 118.0,
        maxLatitude = 25.0,
        maxLongitude = 119.0,
    )
    private val canvas = Size(400f, 600f)

    @Test
    fun `zero pan keeps the campus center in view`() {
        val layout = campusMapLayout(canvas, bounds, paddingPx = 18f)
        val center = campusScreenToCoordinate(
            tap = layout.mapCenter,
            layout = layout,
            bounds = bounds,
            zoom = 1f,
            pan = Offset.Zero,
            rotation = 0f,
        )
        assertTrue(bounds.contains(center))
        assertEquals(24.5, center.latitude, 0.02)
        assertEquals(118.5, center.longitude, 0.02)
    }

    @Test
    fun `dragging far outside campus is clamped back onto the bounds`() {
        val pan = Offset(80_000f, -80_000f)
        val clamped = clampCampusPan(
            pan = pan,
            zoom = 1.4f,
            rotation = 25f,
            canvasSize = canvas,
            bounds = bounds,
            paddingPx = 18f,
        )
        val layout = campusMapLayout(canvas, bounds, paddingPx = 18f)
        val center = campusScreenToCoordinate(
            tap = Offset(canvas.width / 2f, canvas.height / 2f),
            layout = layout,
            bounds = bounds,
            zoom = 1.4f,
            pan = clamped,
            rotation = 25f,
        )
        assertTrue(bounds.contains(center))
        assertFalse(abs(clamped.x) > 70_000f && abs(clamped.y) > 70_000f)
    }

    @Test
    fun `rotation round-trips a campus coordinate`() {
        val layout = campusMapLayout(canvas, bounds, paddingPx = 18f)
        val original = CampusCoordinate(24.4, 118.3)
        val screen = campusCoordinateToScreen(original, layout, bounds, zoom = 1.8f, pan = Offset(12f, -18f), rotation = 40f)
        val back = campusScreenToCoordinate(screen, layout, bounds, zoom = 1.8f, pan = Offset(12f, -18f), rotation = 40f)
        assertEquals(original.latitude, back.latitude, 0.0001)
        assertEquals(original.longitude, back.longitude, 0.0001)
    }
}
