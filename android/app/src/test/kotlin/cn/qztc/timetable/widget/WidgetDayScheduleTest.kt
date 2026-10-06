package cn.qztc.timetable.widget

import cn.qztc.timetable.core.domain.calendar.*
import cn.qztc.timetable.core.domain.model.*
import cn.qztc.timetable.core.domain.schedule.TimetableState
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class WidgetDayScheduleTest {
    private val monday = LocalDate.of(2026, 9, 28)
    private val semester = Semester("s", "合成学期", 2026, Term.AUTUMN, monday, 2)
    private val course = Course("c", "s", SourceType.MANUAL, "c", name = "合成课程")
    private val meeting = CourseMeeting("m", "c", 1, 1, 2, WeekRule.of(listOf(1, 2)),
        room = "原教室", sourceType = SourceType.MANUAL)
    private fun state(vararg exceptions: ScheduleException) =
        TimetableState(listOf(course), listOf(meeting), exceptions.toList())
    private fun day(state: TimetableState, date: LocalDate, calendar: AcademicCalendar = AcademicCalendar.EMPTY) =
        widgetDaySchedule(semester, state, calendar, date)

    @Test fun movedCourseOnlyAppearsAtReplacementDateAndRoom() {
        val state = state(ScheduleException("move", "c", ExceptionType.MOVE,
            original = SlotRef(1, 1, 1, 2), replacement = SlotRef(1, 2, 7, 8, room = "新教室"),
            sourceType = SourceType.MANUAL))
        assertTrue(day(state, monday).occurrences.isEmpty())
        val moved = day(state, monday.plusDays(1)).occurrences.single()
        assertEquals(OccurrenceState.MOVED_IN, moved.state)
        assertEquals(7, moved.periodStart)
        assertEquals("新教室", moved.room)
    }

    @Test fun cancellationIsHiddenWithoutDroppingTheFollowingWeek() {
        val state = state(ScheduleException("cancel", "c", ExceptionType.CANCEL,
            original = SlotRef(1, 1, 1, 2), sourceType = SourceType.MANUAL))
        assertTrue(day(state, monday).occurrences.isEmpty())
        assertEquals(1, day(state, monday.plusWeeks(1)).occurrences.size)
    }

    @Test fun makeupCourseCancelledAgainIsNotDisplayed() {
        val slot = SlotRef(1, 2, 7, 8)
        val state = state(
            ScheduleException("makeup", "c", ExceptionType.MAKEUP, replacement = slot, sourceType = SourceType.MANUAL),
            ScheduleException("cancel", "c", ExceptionType.CANCEL, original = slot, sourceType = SourceType.MANUAL))
        assertTrue(day(state, monday.plusDays(1)).occurrences.isEmpty())
    }

    @Test fun holidayShowsItsFullNameAndHasNoRegularClasses() {
        val calendar = AcademicCalendar(listOf(AcademicDay("h", "s", monday,
            AcademicDayKind.HOLIDAY, "学校国庆特别假期")))
        val result = day(state(), monday, calendar)
        assertEquals("学校国庆特别假期", result.holidayName)
        assertTrue(result.occurrences.isEmpty())
        assertNull(day(state(), monday.plusDays(1), calendar).holidayName)
    }

    @Test fun calendarMakeupShowsSourceCoursesButNotAHolidayBadge() {
        val date = monday.plusDays(5)
        val calendar = AcademicCalendar(listOf(AcademicDay("m", "s", date,
            AcademicDayKind.MAKEUP, "国庆调休", sourceWeek = 1, sourceWeekday = 1)))
        val result = day(state(), date, calendar)
        assertNull(result.holidayName)
        assertEquals(date, result.occurrences.single().date)
    }

    @Test fun nationalDayBadgeDoesNotRequireImportingAHolidayOrSemester() {
        assertEquals("国庆节", widgetHolidayName(AcademicCalendar.EMPTY, LocalDate.of(2026, 10, 2)))
        assertNull(widgetHolidayName(AcademicCalendar.EMPTY, LocalDate.of(2026, 10, 10)))
        assertNull(widgetHolidayName(AcademicCalendar.EMPTY, LocalDate.of(2026, 10, 20)))
    }

    @Test fun holidayBadgeAloneDoesNotCancelUserTimetable() {
        val holiday = LocalDate.of(2026, 10, 5)
        val result = day(state(), holiday)
        assertEquals("国庆节", result.holidayName)
        assertEquals(1, result.occurrences.size)
    }
}
