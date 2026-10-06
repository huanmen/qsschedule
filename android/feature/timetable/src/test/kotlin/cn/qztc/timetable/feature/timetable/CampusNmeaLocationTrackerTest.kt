package cn.qztc.timetable.feature.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CampusNmeaLocationTrackerTest {
    @Test fun `parses a valid gga hardware fix`() {
        val fix = parseNmeaFix("\$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47")!!
        assertEquals(48.1173, fix.latitude, 0.000001)
        assertEquals(11.5166667, fix.longitude, 0.000001)
        assertEquals(4.5f, fix.accuracyMeters, 0.001f)
        assertEquals(545.4, fix.altitudeMeters!!, 0.001)
    }

    @Test fun `rejects no-fix and corrupted sentences`() {
        assertNull(parseNmeaFix("\$GPGGA,123519,4807.038,N,01131.000,E,0,00,9.9,0.0,M,0.0,M,,*48"))
        assertNull(parseNmeaFix("\$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*00"))
    }
}
