package cn.qztc.timetable.feature.timetable

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineMapPolicyTest {
    @Test fun `allows only packaged offline resources`() {
        assertTrue(OfflineMapPolicy.allows(OfflineMapPolicy.START))
        assertTrue(OfflineMapPolicy.allows(OfflineMapPolicy.ROOT + "tiles/clean/18/217485/112367.webp"))
        assertFalse(OfflineMapPolicy.allows("https://example.com/"))
        assertFalse(OfflineMapPolicy.allows("file:///sdcard/test.html"))
        assertFalse(OfflineMapPolicy.allows(OfflineMapPolicy.ROOT + "../other.html"))
        assertFalse(OfflineMapPolicy.allows(OfflineMapPolicy.ROOT + "%2e%2e/other.html"))
        assertFalse(OfflineMapPolicy.allows("https://appassets.androidplatform.net:9000/assets/offline-campus/app.html"))
    }
}
