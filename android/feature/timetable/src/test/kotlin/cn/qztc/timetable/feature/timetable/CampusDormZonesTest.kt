package cn.qztc.timetable.feature.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusDormZonesTest {
    private val chaoyang = CampusPoint(
        id = "qnu-chaoyang",
        name = "朝阳楼",
        coordinate = CampusCoordinate(24.868475, 118.663016),
        category = "building",
    )
    private val a2 = dorm("qnu-a2", "A区2号楼", 24.8690, 118.6610)
    private val a3 = dorm("qnu-a3", "A区3号楼", 24.8692, 118.6612)
    private val b4 = dorm("qnu-b4", "B区4号楼", 24.8700, 118.6620)
    private val c13 = dorm("qnu-c13", "C区13号楼", 24.8710, 118.6640)
    private val places = listOf(chaoyang, a2, a3, b4, c13)

    @Test
    fun `collapses dorms into zone markers until a building is revealed`() {
        val markers = CampusDormZones.visibleMarkers(places)

        assertEquals(listOf("朝阳楼", "A区", "B区", "C区"), markers.map { it.name })
        assertTrue(markers.none { it.name == "D区" })
        assertTrue(markers.none { it.name.contains("号楼") })
    }

    @Test
    fun `search or destination reveals only that dorm building`() {
        val byDestination = CampusDormZones.visibleMarkers(places, destination = a2)
        val bySearch = CampusDormZones.visibleMarkers(places, revealedIds = setOf("qnu-a2"))

        assertTrue(byDestination.any { it.name == "A区2号楼" })
        assertTrue(byDestination.none { it.name == "A区3号楼" || it.name == "A区" })
        assertEquals(byDestination.map { it.name }, bySearch.map { it.name })
        assertTrue(byDestination.any { it.name == "B区" })
    }

    @Test
    fun `expanded zone lists every building and keeps the zone pin`() {
        val markers = CampusDormZones.visibleMarkers(places, expandedZones = setOf("A区"))

        assertTrue(markers.any { it.name == "A区2号楼" })
        assertTrue(markers.any { it.name == "A区3号楼" })
        assertTrue(markers.any { it.id == "zone-A区" })
        assertTrue(markers.none { it.name == "B区4号楼" })
    }

    @Test
    fun `zone query matches A区 and not a lone letter`() {
        assertEquals("A区", CampusDormZones.zoneFromQuery("A区"))
        assertEquals("B区", CampusDormZones.zoneFromQuery(" b区 "))
        assertNull(CampusDormZones.zoneFromQuery("A"))
        assertNull(CampusDormZones.zoneFromQuery("A区2号楼"))
        assertEquals("D区", CampusDormZones.zoneFromQuery("D区"))
    }

    @Test
    fun `D区 student living buildings collapse to a D zone pin`() {
        val living = dorm("qnu-d-life", "D区学生生活楼", 24.8746, 118.6686)
        val markers = CampusDormZones.visibleMarkers(places + living)

        assertEquals("D区", CampusDormZones.zoneOf(living))
    }

    @Test
    fun `second tap on the same destination clears navigation`() {
        assertEquals(a2, CampusDormZones.destinationAfterTap(null, a2))
        assertNull(CampusDormZones.destinationAfterTap(a2, a2))
        assertEquals(a3, CampusDormZones.destinationAfterTap(a2, a3))
    }

    private fun dorm(id: String, name: String, latitude: Double, longitude: Double) = CampusPoint(
        id = id,
        name = name,
        coordinate = CampusCoordinate(latitude, longitude),
        category = "building",
        isDormitory = true,
    )
}
