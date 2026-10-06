package cn.qztc.timetable.feature.timetable

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusLiveFixPolicyTest {
    @Test fun `a precise mock fix cannot be displayed or replace real location`() {
        val now = 1_000_000L
        val real = CampusLiveFix(24.87, 118.67, 30f, now - 2_000)
        val mock = CampusLiveFix(30.0, 120.0, 1f, now, isMock = true)
        assertFalse(CampusLiveFixPolicy.canDisplay(mock, now))
        assertFalse(CampusLiveFixPolicy.canNavigate(mock, now, precisePermission = true))
        assertFalse(CampusLiveFixPolicy.shouldReplace(real, mock, now))
        assertFalse(CampusLiveFixPolicy.shouldReplace(null, mock, now))
        assertTrue(CampusLiveFixPolicy.shouldReplace(mock, real, now))
    }

    @Test fun `a recent coarse fix can be displayed but cannot start navigation`() {
        val now = 1_000_000L
        val fix = CampusLiveFix(24.87, 118.67, 120f, now - 2_000)
        assertTrue(CampusLiveFixPolicy.canDisplay(fix, now))
        assertFalse(CampusLiveFixPolicy.canNavigate(fix, now, precisePermission = false))
        assertFalse(CampusLiveFixPolicy.canNavigate(fix, now, precisePermission = true))
    }

    @Test fun `a fresh precise fix can navigate`() {
        val now = 1_000_000L
        val fix = CampusLiveFix(24.87, 118.67, 18f, now - 1_000)
        assertTrue(CampusLiveFixPolicy.canNavigate(fix, now, precisePermission = true))
    }

    @Test fun `expired fixes are rejected`() {
        val now = 1_000_000L
        val fix = CampusLiveFix(24.87, 118.67, 10f, now - CampusLiveFixPolicy.DISPLAY_MAX_AGE_MS - 1)
        assertFalse(CampusLiveFixPolicy.canDisplay(fix, now))
    }

    @Test fun `location without elapsed realtime uses wall clock timestamp`() {
        val elapsed = locationElapsedMillis(
            elapsedRealtimeNanos = 0L,
            wallTimeMillis = 998_000L,
            nowElapsedMillis = 1_000_000L,
            nowWallMillis = 1_000_000L,
        )
        assertTrue(CampusLiveFixPolicy.canDisplay(CampusLiveFix(24.87, 118.67, 30f, elapsed), 1_000_000L))
    }

    @Test fun `a fresh accurate fix is not replaced by a later broad estimate`() {
        val now = 1_000_000L
        val previous = CampusLiveFix(24.8700, 118.6700, 8f, now - 12_000)
        val network = CampusLiveFix(24.8720, 118.6720, 180f, now - 1_000)
        assertFalse(CampusLiveFixPolicy.shouldReplace(previous, network, now))
    }

    @Test fun `an expired fix can be replaced by a new provider`() {
        val now = 1_000_000L
        val previous = CampusLiveFix(24.8700, 118.6700, 8f, now - CampusLiveFixPolicy.DISPLAY_MAX_AGE_MS - 1)
        val network = CampusLiveFix(24.8720, 118.6720, 180f, now - 1_000)
        assertTrue(CampusLiveFixPolicy.shouldReplace(previous, network, now))
    }
}
