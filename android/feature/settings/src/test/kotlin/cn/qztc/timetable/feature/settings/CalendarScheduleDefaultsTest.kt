package cn.qztc.timetable.feature.settings

import cn.qztc.timetable.core.domain.calendar.*
import cn.qztc.timetable.core.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CalendarScheduleDefaultsTest {
    private val semester = Semester("s", "test", 2026, Term.AUTUMN, LocalDate.of(2026, 8, 31))
    private val holiday = AcademicDay("public-holiday", "public", LocalDate.of(2026, 10, 1), AcademicDayKind.HOLIDAY, "National Day")
    private val makeup = holiday.copy(id = "public-makeup", date = LocalDate.of(2026, 10, 10), kind = AcademicDayKind.MAKEUP, useCurrentWeek = true)
    private val sports = SchoolCalendarEvent("sports", "2026-2027", "第一学期", SchoolCalendarEventType.SPORTS_MEETING,
        "sports", LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 17))
    private fun plan(savedDays: List<AcademicDay> = emptyList(), savedActivities: List<ScheduleActivity> = emptyList(),
                     selections: Map<String, Boolean> = emptyMap(), events: List<SchoolCalendarEvent> = listOf(sports)) =
        CalendarScheduleDefaults.plan(semester, savedDays, savedActivities, listOf(holiday, makeup), events, selections, null)

    @Test fun holidaysMakeupAndSportsAreIncludedWithoutOpeningManager() {
        val additions = plan()
        assertEquals(setOf(AcademicDayKind.HOLIDAY, AcademicDayKind.MAKEUP), additions.days.map { it.kind }.toSet())
        assertTrue(additions.days.all { it.semesterId == semester.id })
        assertTrue(additions.days.single { it.kind == AcademicDayKind.MAKEUP }.useCurrentWeek)
        assertEquals("calendar-event:s:sports", additions.activities.single().id)
        assertEquals(sports.endDate, additions.activities.single().endDate)
    }
    @Test fun officialImportsRestoreOldOptOutAndEditedNodes() {
        val excluded = plan(selections = mapOf("event:sports" to false, "calendar-day:${holiday.date}:HOLIDAY" to false))
        assertEquals(1, excluded.activities.size)
        assertEquals(listOf(holiday.date, makeup.date), excluded.days.map { it.date })
        val first = plan()
        val edited = first.activities.single().copy(name = "customized", endDate = sports.startDate)
        val next = plan(savedDays = first.days, savedActivities = listOf(edited))
        assertEquals(sports.name, next.activities.single().name)
        assertEquals(sports.endDate, next.activities.single().endDate)
        assertTrue(next.days.isEmpty())
        assertFalse(plan(savedActivities = listOf(edited.copy(hiddenFromSchedule = true))).activities.single().hiddenFromSchedule)
    }
    @Test fun oldHolidayOptOutIsIgnoredAndOtherSemesterEventsAreNotImported() {
        val key = holidaySelectionKey(buildCalendarHolidaySchedules(listOf(holiday, makeup)).first())
        assertEquals(2, plan(selections = mapOf(key to false)).days.size)
        assertTrue(plan(events = listOf(sports.copy(schoolYear = "2025-2026"), sports.copy(semesterLabel = "第二学期"))).activities.isEmpty())
    }

    @Test fun breaksImportOnlyBoundariesAndOtherEventsAreCalendarOnly() {
        val vacation = sports.copy(id = "vacation", type = SchoolCalendarEventType.BREAK, name = "winter break",
            startDate = LocalDate.of(2027, 1, 15), endDate = LocalDate.of(2027, 2, 28))
        val report = sports.copy(id = "report", type = SchoolCalendarEventType.REPORTING)
        val additions = plan(events = listOf(vacation, report))
        val boundaries = additions.activities.filter { it.id.contains("vacation") }
        assertEquals(2, boundaries.size)
        assertEquals(setOf(vacation.startDate, vacation.endDate), boundaries.map { it.startDate }.toSet())
        assertTrue(boundaries.all { it.startDate == it.endDate && it.calendarTimetable })
        assertFalse(additions.activities.single { it.id.endsWith(":report") }.calendarTimetable)
        assertTrue(boundaries.none { it.appliesOn(22, 2, LocalDate.of(2027, 2, 2)) })
    }
}
