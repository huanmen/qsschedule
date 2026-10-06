package cn.qztc.timetable.feature.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusPlaceResolverTest {

    @Test
    fun `normalizes qnu building shorthand and room`() {
        assertEquals("朝阳楼", CampusPlaceResolver.normalize("朝阳"))
        assertEquals("荣茂楼", CampusPlaceResolver.normalize("荣茂"))
        assertEquals("千墅楼", CampusPlaceResolver.normalize("千墅"))
        assertEquals("朝阳楼4楼404室", CampusPlaceResolver.normalize("朝阳404"))
        assertEquals("千墅楼2楼201室", CampusPlaceResolver.normalize("千墅201"))
    }

    @Test
    fun `map search is anchored to the campus building`() {
        assertEquals("朝阳楼", CampusPlaceResolver.buildingName("朝阳404"))
        assertEquals("朝阳楼", CampusPlaceResolver.buildingName("黄朝阳教学楼"))
        assertEquals("千墅楼", CampusPlaceResolver.buildingName("千墅201"))
        assertEquals("泉州师范学院 朝阳楼", CampusPlaceResolver.searchQuery("朝阳404"))
    }

    @Test
    fun `campus place reference resolves buildings`() {
        val to = CampusRouteGraph.resolve("朝阳404")!!
        assertEquals("朝阳楼", to.name)
        assertTrue(to.coordinate.latitude in 24.86..24.88)
        assertTrue(to.coordinate.longitude in 118.65..118.68)
    }
}
