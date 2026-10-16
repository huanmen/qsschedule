package cn.qztc.timetable.core.domain.schedule

import cn.qztc.timetable.core.domain.calendar.*
import cn.qztc.timetable.core.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CalendarActivityDisplayTest {
    private val date = LocalDate.of(2026, 10, 13)
    private fun activity(id: String) = ScheduleActivity(id, "s", "sports", kind = ActivityKind.DAY,
        startDate = date, endDate = date.plusDays(2))
    private fun alpha(day: LocalDate = date, days: List<AcademicDay> = emptyList(), activities: List<ScheduleActivity> = emptyList()) =
        CalendarActivityDisplay.courseAlpha(day, 1, day.dayOfWeek.value, days, activities)

    @Test fun onlyVisibleSchoolImportsEnterTimetable() {
        assertFalse(CalendarActivityDisplay.inTimetable(activity("manual")))
        assertFalse(CalendarActivityDisplay.inTimetable(activity("academic:s:exam")))
        assertTrue(CalendarActivityDisplay.inTimetable(activity("calendar-event:s:sports")))
        assertFalse(CalendarActivityDisplay.inTimetable(activity("calendar-event:s:sports").copy(hiddenFromSchedule = true)))
        assertFalse(CalendarActivityDisplay.inTimetable(activity("calendar-event:s:report").copy(calendarTimetable = false)))
        assertEquals(1f, alpha(activities = listOf(activity("calendar-event:s:report").copy(calendarTimetable = false))), .001f)
    }
    @Test fun schoolEventFadesAllItsDatesButManualEventDoesNot() {
        val school = listOf(activity("calendar-event:s:sports"))
        (0L..2L).forEach { assertEquals(.42f, alpha(date.plusDays(it), activities = school), .001f) }
        assertEquals(1f, alpha(date.plusDays(3), activities = school), .001f)
        assertEquals(1f, alpha(activities = listOf(activity("manual"))), .001f)
        assertEquals(1f, alpha(activities = listOf(school.single().copy(hiddenFromSchedule = true))), .001f)
    }
    @Test fun holidayFadesButMakeupHasPriorityEvenWhenAnEventOverlaps() {
        val holiday = AcademicDay("holiday", "s", date, AcademicDayKind.HOLIDAY, "holiday")
        val makeup = holiday.copy(id = "makeup", kind = AcademicDayKind.MAKEUP)
        assertEquals(.42f, alpha(days = listOf(holiday)), .001f)
        assertEquals(1f, alpha(days = listOf(makeup), activities = listOf(activity("calendar-event:s:sports"))), .001f)
        assertEquals(1f, alpha(days = listOf(holiday, makeup)), .001f)
    }
}
