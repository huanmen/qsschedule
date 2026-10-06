package cn.qztc.timetable.feature.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CampusMapDataTest {

    @Test
    fun `parses local map layers and attribution`() {
        val dataset = CampusMapRepository.parse(
            """
            {
              "bounds": {"minLatitude": 24.0, "minLongitude": 118.0, "maxLatitude": 25.0, "maxLongitude": 119.0},
              "campusBoundary": [{"latitude":24.1,"longitude":118.1},{"latitude":24.2,"longitude":118.1},{"latitude":24.2,"longitude":118.2}],
              "attribution": "OpenStreetMap contributors",
              "features": [
                {"id":"way-1","kind":"building","subtype":"yes","name":"教学楼","nodeIds":["1","2","3"],"points":[{"latitude":24.1,"longitude":118.1},{"latitude":24.1,"longitude":118.2},{"latitude":24.2,"longitude":118.2}]},
                {"id":"way-2","kind":"path","subtype":"footway","name":"","nodeIds":["1","4"],"points":[{"latitude":24.1,"longitude":118.1},{"latitude":24.15,"longitude":118.15}]}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, dataset.buildings.size)
        assertEquals(1, dataset.paths.size)
        assertEquals(3, dataset.campusBoundary.size)
        assertEquals("OpenStreetMap contributors", dataset.attribution)
    }

    @Test
    fun `parses connected local road nodes for map rendering`() {
        val dataset = CampusMapRepository.parse(
            """
            {
              "bounds": {"minLatitude": 24.0, "minLongitude": 118.0, "maxLatitude": 25.0, "maxLongitude": 119.0},
              "features": [
                {"id":"way-1","kind":"path","subtype":"footway","name":"","nodeIds":["1","2","3"],"points":[{"latitude":24.1,"longitude":118.1},{"latitude":24.1,"longitude":118.11},{"latitude":24.1,"longitude":118.12}]}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(listOf("1", "2", "3"), dataset.paths.single().nodeIds)
        assertEquals(3, dataset.paths.single().points.size)
    }

    @Test
    fun `ships a routable campus asset`() {
        val asset = File("src/main/assets/campus/campus-map.json")
        assertTrue(asset.isFile)
        val dataset = CampusMapRepository.parse(asset.readText())

        assertTrue(dataset.buildings.size >= 80)
        assertTrue(dataset.roads.isNotEmpty())
        assertTrue(dataset.paths.isNotEmpty())
        assertTrue(dataset.campusBoundary.size >= 3)

        val places = CampusPlaceRepository.fromJson(File("src/main/assets/campus/places.json").readText())
        assertTrue(places.places.size >= 60)
        assertTrue(places.places.count { it.name.endsWith("号楼") } >= 30)
        assertEquals("朝阳楼", places.groupedNavigablePlaces().flatMap { it.second }.first { it.id == "qnu-chaoyang" }.name)
        assertTrue(places.groupedNavigablePlaces().any { it.first == "楼馆" && it.second.any { place -> place.id == "qnu-chaoyang" } })
        assertTrue(places.groupedNavigablePlaces().any { it.first == "宿舍A区" && it.second.any { place -> place.name == "A区2号楼" } })
        assertTrue(places.groupedNavigablePlaces().any { it.first == "宿舍D区" && it.second.any { place -> place.name.contains("D区") } })
        assertEquals("D区学生生活楼", places.resolve("D区学生生活楼")?.name)
        assertEquals("C区15号楼", places.resolve("C15")?.name)
        assertTrue(places.groupedNavigablePlaces().any { it.first == "出入口" && it.second.any { place -> place.name == "东海校区大门" } })
        assertEquals(24.8670560, places.point("qnu-main-entrance")?.coordinate?.latitude ?: 0.0, 0.0000001)
        assertEquals("朝阳楼", places.resolve("朝阳404")?.name)
        assertEquals("千墅楼", places.resolve("千墅201")?.name)
        assertEquals("A区2号楼", places.resolve("A2")?.name)
        assertEquals("C区13号楼", places.resolve("学生公寓C13")?.name)
        val chaoyang = places.point("qnu-chaoyang")!!.coordinate
        val rongmao = places.point("qnu-rongmao")!!.coordinate
        val qianshu = places.point("qnu-qianshu")!!.coordinate
        assertTrue(chaoyang.latitude > rongmao.latitude)
        assertTrue(chaoyang.longitude < rongmao.longitude)
        assertTrue(qianshu.latitude < rongmao.latitude)
        assertTrue(qianshu.longitude > rongmao.longitude)

    }

    @Test
    fun `official campus points use the WGS84 map coordinate system`() {
        val dataset = CampusMapRepository.parse(
            File("src/main/assets/campus/campus-map.json").readText(),
        )
        val entrance = CampusRouteGraph.point("main-entrance").coordinate

        assertEquals(24.8670560, entrance.latitude, 0.0000001)
        assertEquals(118.6626915, entrance.longitude, 0.0000001)
        CampusRouteGraph.points.forEach { point ->
            assertTrue(point.coordinate.latitude in dataset.bounds.minLatitude..dataset.bounds.maxLatitude)
            assertTrue(point.coordinate.longitude in dataset.bounds.minLongitude..dataset.bounds.maxLongitude)
        }
    }

    @Test
    fun `does not treat emulator default GPS as a campus location`() {
        val dataset = CampusMapRepository.parse(File("src/main/assets/campus/campus-map.json").readText())
        val farAway = CampusCoordinate(37.4219983, -122.084)
        val entrance = CampusCoordinate(24.8670560, 118.6626915)

        assertTrue(!dataset.bounds.contains(farAway, paddingRatio = 0.15))
        assertTrue(dataset.bounds.contains(entrance, paddingRatio = 0.15))
        val clamped = dataset.bounds.clamp(farAway)
        assertEquals(dataset.bounds.maxLatitude, clamped.latitude, 0.0000001)
        assertEquals(dataset.bounds.minLongitude, clamped.longitude, 0.0000001)
    }
}
