package cn.qztc.timetable.feature.settings

import cn.qztc.timetable.core.domain.model.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CalendarDisplayPolicyTest {
    private val autumn = DetectedSchoolSemester("2026-2027", Term.AUTUMN, "第一学期", LocalDate.of(2026, 8, 31), LocalDate.of(2027, 1, 15))
    private val spring = DetectedSchoolSemester("2026-2027", Term.SPRING, "第二学期", LocalDate.of(2027, 2, 22), LocalDate.of(2027, 7, 2))
    private val semester = Semester("autumn", "合成秋季学期", 2026, Term.AUTUMN, autumn.startDate, 20)
    private val event = SchoolCalendarEvent("reporting", "2026-2027", "第一学期", SchoolCalendarEventType.REPORTING,
        "学生报到", autumn.startDate.minusDays(1))
    private val saved = ScheduleActivity("calendar-event:autumn:reporting", semester.id, event.name,
        kind = ActivityKind.DAY, startDate = event.startDate, endDate = event.startDate)

    @Test fun defaultSuggestionIsNotEvidenceOfSavedInclusion() {
        assertFalse(CalendarDefaultInclusion.includes(event, 2025, LocalDate.of(2026, 10, 4)))
        assertFalse(isCalendarEventIncluded(event, semester, emptyList()))
        assertTrue(isCalendarEventIncluded(event, semester, listOf(saved)))
        assertFalse(isCalendarEventIncluded(event, semester, listOf(saved.copy(hiddenFromSchedule = true))))
    }

    @Test fun anotherSemesterOrYearCannotAppearIncludedEvenWithMatchingEventId() {
        assertFalse(isCalendarEventIncluded(event.copy(semesterLabel = "第二学期"), semester, listOf(saved)))
        assertFalse(isCalendarEventIncluded(event.copy(schoolYear = "2025-2026"), semester, listOf(saved)))
        assertFalse(isCalendarEventIncluded(event, semester, listOf(saved.copy(semesterId = "another-semester"))))
    }

    @Test fun autumnRetiresAtSpringStartWithoutHidingFutureOrOngoingTerms() {
        val terms = listOf(autumn, spring)
        assertEquals(terms, visibleCalendarSemesters(terms, spring.startDate.minusDays(1)))
        assertEquals(listOf(spring), visibleCalendarSemesters(terms, spring.startDate))
        assertEquals(listOf(spring), visibleCalendarSemesters(listOf(autumn.copy(endDate = null), spring), spring.startDate))
        val futureAutumn = autumn.copy(startDate = LocalDate.of(2027, 8, 30), endDate = LocalDate.of(2028, 1, 14))
        assertEquals(listOf(spring, futureAutumn), visibleCalendarSemesters(terms + futureAutumn, spring.startDate))
        val overlapping = autumn.copy(endDate = spring.startDate.plusDays(1))
        assertEquals(listOf(overlapping, spring), visibleCalendarSemesters(listOf(overlapping, spring), spring.startDate))
    }
}
