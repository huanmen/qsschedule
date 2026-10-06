package cn.qztc.timetable.feature.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusLocationFixTest {
    private val bounds = CampusMapBounds(
        minLatitude = 24.8642,
        minLongitude = 118.659,
        maxLatitude = 24.8782,
        maxLongitude = 118.6765,
    )
    private val entrance = CampusCoordinate(24.8670560, 118.6626915)
    private val now = 1_700_000_000_000L

    @Test
    fun `gcj02 inverse stays near the original WGS campus point`() {
        val gcj = CampusAmapProjection.wgs84ToGcj02(entrance)
        val back = CampusAmapProjection.gcj02ToWgs84(gcj)

        assertTrue(CampusRouteGraph.distanceMeters(entrance, back) < 25.0)
        assertTrue(CampusRouteGraph.distanceMeters(entrance, gcj) > 200.0)
    }

    @Test
    fun `keeps on-campus GPS in WGS84`() {
        val mapped = CampusLocationFix.toMapCoordinate(
            CampusLocationSample(entrance.latitude, entrance.longitude, 12f, now, "gps"),
            bounds,
        )

        assertEquals(entrance.latitude, mapped.latitude, 0.0000001)
        assertEquals(entrance.longitude, mapped.longitude, 0.0000001)
    }

    @Test
    fun `converts network GCJ so the pin lands on campus`() {
        val gcj = CampusAmapProjection.wgs84ToGcj02(entrance)
        val mapped = CampusLocationFix.toMapCoordinate(
            CampusLocationSample(gcj.latitude, gcj.longitude, 30f, now, "network"),
            bounds,
        )

        assertTrue(bounds.contains(mapped, paddingRatio = 0.0))
        assertTrue(CampusRouteGraph.distanceMeters(mapped, entrance) < 40.0)
    }

    @Test
    fun `converts fused GCJ the same way as network`() {
        val gcj = CampusAmapProjection.wgs84ToGcj02(entrance)
        val mapped = CampusLocationFix.toMapCoordinate(
            CampusLocationSample(gcj.latitude, gcj.longitude, 18f, now, "fused"),
            bounds,
        )

        assertTrue(CampusRouteGraph.distanceMeters(mapped, entrance) < 40.0)
    }

    @Test
    fun `drops stale off-campus lastKnown in favor of a campus network fix`() {
        val mountainView = CampusLocationSample(37.4219983, -122.084, 8f, now - 60_000L, "gps")
        val campusNetwork = CampusLocationSample(entrance.latitude, entrance.longitude, 35f, now - 20_000L, "network")
        val picked = CampusLocationFix.pick(listOf(mountainView, campusNetwork), bounds, now)

        assertEquals("network", picked?.provider)
        assertFalse(CampusLocationFix.isNearCampus(CampusCoordinate(37.4219983, -122.084), bounds))
        assertTrue(CampusLocationFix.isNearCampus(entrance, bounds))
    }

    @Test
    fun `ignores lastKnown that is older than half an hour`() {
        val stale = CampusLocationSample(entrance.latitude, entrance.longitude, 10f, now - 40 * 60_000L, "gps")

        assertEquals(null, CampusLocationFix.pick(listOf(stale), bounds, now))
    }
}
