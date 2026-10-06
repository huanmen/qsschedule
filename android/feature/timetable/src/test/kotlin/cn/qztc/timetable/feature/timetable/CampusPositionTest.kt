package cn.qztc.timetable.feature.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusPositionTest {
    @Test fun `amap positions must not be offset a second time`() {
        val native = CampusCoordinate(24.870, 118.670)
        val amap = native.forCampusMap(CampusCoordinateSystem.WGS84)
        assertTrue(kotlin.math.abs(amap.longitude - native.longitude) > 0.001)
        assertEquals(amap, amap.forCampusMap(CampusCoordinateSystem.GCJ02))
    }

    @Test fun `external navigation converts amap origin back to wgs84`() {
        val native = CampusCoordinate(24.870, 118.670)
        val amap = native.forCampusMap(CampusCoordinateSystem.WGS84)
        val restored = amap.forExternalNavigation(CampusCoordinateSystem.GCJ02)
        assertEquals(native.latitude, restored.latitude, 0.00002)
        assertEquals(native.longitude, restored.longitude, 0.00002)
        assertEquals(native, native.forExternalNavigation(CampusCoordinateSystem.WGS84))
    }
}
