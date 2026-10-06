package cn.qztc.timetable.feature.timetable

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusPlaceOverrideTest {
    private val catalog = CampusPlaceCatalog.fromPoints(
        listOf(
            CampusPoint(
                id = "qnu-rongmao",
                name = "荣茂楼",
                coordinate = CampusCoordinate(24.8681, 118.6634),
                aliases = setOf("荣茂"),
            ),
        ),
    )

    @Test
    fun `rename and move a catalog place without duplicating it`() {
        val next = catalog.applyOverrides(
            CampusPlaceOverrides(
                names = mapOf("qnu-rongmao" to "荣茂综合楼"),
                coordinates = mapOf("qnu-rongmao" to CampusCoordinate(24.8682, 118.6635)),
            ),
        )

        assertEquals(1, next.places.size)
        assertEquals("荣茂综合楼", next.point("qnu-rongmao")?.name)
        assertEquals(24.8682, next.point("qnu-rongmao")?.coordinate?.latitude ?: 0.0, 0.0000001)
        assertEquals("荣茂综合楼", next.resolve("荣茂")?.name)
    }

    @Test
    fun `alias rename move and delete helpers persist on overrides`() {
        val added = CampusPlaceOverrides().copy(
            extras = listOf(
                CampusCustomPlace("custom:1", "临时点", 24.87, 118.66),
            ),
        )
        val renamed = added.renamePlace("qnu-rongmao", "荣茂综合楼").addAlias("qnu-rongmao", "RML")
        val moved = renamed.movePlace("custom:1", CampusCoordinate(24.871, 118.665))
        val removed = moved.removePlace("custom:1")

        assertEquals("荣茂综合楼", renamed.names["qnu-rongmao"])
        assertTrue(renamed.aliases["qnu-rongmao"].orEmpty().contains("RML"))
        assertEquals(24.871, moved.extras.single().latitude, 0.0000001)
        assertTrue(removed.extras.isEmpty())
        assertTrue("qnu-rongmao" !in removed.hiddenIds)
    }

    @Test
    fun `nearest marker ignores taps that are too far`() {
        val bounds = CampusMapBounds(24.0, 118.0, 25.0, 119.0)
        val canvas = Size(400f, 600f)
        val layout = campusMapLayout(canvas, bounds, paddingPx = 18f)
        val place = CampusPoint("p1", "朝阳楼", CampusCoordinate(24.5, 118.5))
        val screen = campusCoordinateToScreen(place.coordinate, layout, bounds, zoom = 1f, pan = Offset.Zero, rotation = 0f)

        assertEquals(
            "朝阳楼",
            nearestCampusPlace(screen, listOf(place), layout, bounds, 1f, Offset.Zero, 0f, 28f)?.name,
        )
        assertNull(
            nearestCampusPlace(Offset(0f, 0f), listOf(place), layout, bounds, 1f, Offset.Zero, 0f, 28f),
        )
    }
}
