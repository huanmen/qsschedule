package cn.qztc.timetable.core.domain.schedule

import cn.qztc.timetable.core.domain.model.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ActivityMemoTimeTest {
    private val date = LocalDate.of(2026, 10, 14)
    @Test fun undatedMemoHasNoCourseSessions() {
        val activity = ScheduleActivity("memo", "term", "买书", kind = ActivityKind.DAY, memoTime = ActivityMemoTime())
        assertTrue(activity.effectiveSessions().isEmpty())
        assertFalse(activity.appliesOn(1, 3, date))
    }
    @Test fun singlePointAndCrossDayIntervalHaveExactDates() {
        assertEquals(listOf(date), ActivityMemoTime(date, 600).dates())
        assertEquals(listOf(date, date.plusDays(1)), ActivityMemoTime(date, 1380, date.plusDays(1), 30).dates())
    }
    @Test fun midnightAndDateOnlyAreValid() {
        assertEquals("2026-10-14 00:00", ActivityMemoTime(date, 0).label())
        assertEquals("2026-10-14", ActivityMemoTime(date).label())
    }
    @Test fun reversedOrPartialIntervalRejected() {
        assertThrows(IllegalArgumentException::class.java) { ActivityMemoTime(date, 600, date, 590) }
        assertThrows(IllegalArgumentException::class.java) { ActivityMemoTime(date, 600, date.plusDays(1), null) }
        assertThrows(IllegalArgumentException::class.java) { ActivityMemoTime(null, 600) }
    }
}
