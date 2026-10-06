package cn.qztc.timetable.feature.timetable

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TodaySchedulePresentationTest {
    @Test
    fun `remaining course bar shrinks toward zero as class approaches its end`() {
        val start = LocalTime.of(8, 0)
        val end = LocalTime.of(9, 0)

        assertEquals(1f, courseRemainingFraction(LocalTime.of(7, 50), start, end), 0.001f)
        assertEquals(0.75f, courseRemainingFraction(LocalTime.of(8, 15), start, end), 0.001f)
        assertEquals(0.25f, courseRemainingFraction(LocalTime.of(8, 45), start, end), 0.001f)
        assertEquals(0f, courseRemainingFraction(LocalTime.of(9, 0), start, end), 0.001f)
    }
}
