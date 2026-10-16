package cn.qztc.timetable.core.ui.theme

import org.junit.Assert.*
import org.junit.Test

class ClockWheelBoundsTest {
    @Test fun wheelClampsBothEndsAndPreservesValidMinute() {
        assertEquals(541,closestAllowedClockMinute(480) {it in 541..600})
        assertEquals(600,closestAllowedClockMinute(660) {it in 541..600})
        assertEquals(572,closestAllowedClockMinute(572) {it in 541..600})
    }
    @Test fun impossibleWindowOffersNoSelection() {
        assertNull(closestAllowedClockMinute(540) {false})
    }
}
