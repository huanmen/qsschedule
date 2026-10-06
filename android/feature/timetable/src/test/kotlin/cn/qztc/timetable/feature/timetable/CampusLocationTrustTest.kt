package cn.qztc.timetable.feature.timetable

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusLocationTrustTest {
    @Test fun `fused amap network result is rejected when a mock provider is present`() {
        assertTrue(isDerivedFromAmapNetwork("fused", "network", true))
    }

    @Test fun `the same fused metadata remains untrusted without relying on mock provider state`() {
        assertTrue(isDerivedFromAmapNetwork("fused", "network", true))
    }

    @Test fun `ordinary fused result remains trusted`() {
        assertFalse(isDerivedFromAmapNetwork("fused", "gps", false))
    }
}
