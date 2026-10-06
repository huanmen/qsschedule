package cn.qztc.timetable.feature.timetable

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusWalkRouterTest {
    @Test
    fun `follows connected campus paths instead of a straight shortcut`() {
        val dataset = CampusMapDataset(
            bounds = CampusMapBounds(24.0, 118.0, 25.0, 119.0),
            features = listOf(
                CampusMapFeature(
                    id = "path-1",
                    kind = "path",
                    subtype = "footway",
                    name = "",
                    nodeIds = listOf("a", "b", "c"),
                    points = listOf(
                        CampusCoordinate(24.10, 118.10),
                        CampusCoordinate(24.10, 118.11),
                        CampusCoordinate(24.11, 118.11),
                    ),
                ),
            ),
        )

        val route = CampusWalkRouter.route(
            dataset,
            CampusCoordinate(24.1001, 118.1001),
            CampusCoordinate(24.1101, 118.1101),
        )

        assertTrue(route.points.size >= 3)
        assertTrue(route.distanceMeters > 100)
        assertEquals("沿校园道路步行前往", route.steps.single().instruction)
    }

    @Test
    fun `joins nearby disconnected paths instead of a straight line`() {
        val dataset = CampusMapDataset(
            bounds = CampusMapBounds(24.0, 118.0, 25.0, 119.0),
            features = listOf(
                CampusMapFeature(
                    id = "path-a",
                    kind = "path",
                    subtype = "footway",
                    name = "",
                    points = listOf(
                        CampusCoordinate(24.10, 118.10),
                        CampusCoordinate(24.10, 118.10035),
                    ),
                ),
                CampusMapFeature(
                    id = "path-b",
                    kind = "path",
                    subtype = "footway",
                    name = "",
                    points = listOf(
                        CampusCoordinate(24.10, 118.1007),
                        CampusCoordinate(24.10, 118.11),
                    ),
                ),
            ),
        )

        val route = CampusWalkRouter.route(
            dataset,
            CampusCoordinate(24.10002, 118.10002),
            CampusCoordinate(24.10002, 118.1099),
        )

        assertTrue(route.points.size >= 3)
        assertEquals("沿校园道路步行前往", route.steps.single().instruction)
    }

    @Test
    fun `routes between published campus places on the local map`() {
        val dataset = CampusMapRepository.parse(File("src/main/assets/campus/campus-map.json").readText())
        val places = CampusPlaceRepository.fromJson(File("src/main/assets/campus/places.json").readText())
        val origin = places.point("qnu-main-entrance")!!.coordinate
        val destination = places.point("qnu-chaoyang")!!.coordinate

        val route = CampusWalkRouter.route(dataset, origin, destination)

        assertTrue(route.points.size >= 3)
        assertTrue(route.distanceMeters in 80..2_000)
        assertEquals("沿校园道路步行前往", route.steps.single().instruction)
        assertTrue(places.groupedNavigablePlaces().sumOf { it.second.size } >= 60)
    }
}
