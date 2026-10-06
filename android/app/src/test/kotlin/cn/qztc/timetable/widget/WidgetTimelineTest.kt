package cn.qztc.timetable.widget

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime

class WidgetTimelineTest {
    @Test fun dotUsesCourseAndBreakProgressWithoutJumpingToTheNextCourse() {
        val start = LocalTime.of(8, 0)
        val end = LocalTime.of(9, 40)
        val next = LocalTime.of(10, 0)
        assertEquals(.5f, widgetTimelinePosition(start, end, next, LocalTime.of(8, 50)).courseFraction!!, .001f)
        assertEquals(.5f, widgetTimelinePosition(start, end, next, LocalTime.of(9, 50)).breakFraction!!, .001f)
        assertNull(widgetTimelinePosition(start, end, next, next).breakFraction)
        assertNull(widgetTimelinePosition(start, end, next, LocalTime.of(7, 0)).courseFraction)
    }

    @Test fun scrollTargetFollowsCurrentOrNextCourseIncludingLastCourse() {
        val entries = (0..5).map { TodayWidgetEntry("Test", LocalTime.of(8 + it * 2, 0), LocalTime.of(9 + it * 2, 40), "Room", it, 0) }
        mapOf(7 to 0, 8 to 0, 10 to 0, 12 to 1, 16 to 3, 18 to 3, 22 to 3).forEach { (hour, expected) ->
            val now = LocalTime.of(hour, 30)
            assertEquals(expected, widgetFocusIndex(entries, now))
        }
        assertEquals(1, widgetFocusIndex(entries, LocalTime.of(11, 50), 3))
        assertEquals(2, widgetFocusIndex(entries, LocalTime.of(11, 50), 2))
        assertEquals(2, widgetFocusIndex(entries, LocalTime.of(12, 20), 2))
        assertEquals(4, widgetFocusIndex(entries, LocalTime.of(18, 20), 2))
        assertEquals(4, widgetFocusIndex(entries, LocalTime.of(23, 0), 2))
        assertEquals(0, widgetFocusIndex(emptyList(), LocalTime.NOON, 3))
        assertEquals(0, widgetFocusIndex(entries.take(1), LocalTime.NOON, 2))
    }
}
