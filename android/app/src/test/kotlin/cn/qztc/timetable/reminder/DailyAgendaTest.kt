package cn.qztc.timetable.reminder

import cn.qztc.timetable.core.domain.model.*
import cn.qztc.timetable.core.domain.schedule.TimetableState
import cn.qztc.timetable.education.AcademicCalendarEntry
import cn.qztc.timetable.feature.timetable.ImportedSchedule
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class DailyAgendaTest {
    @Test fun reminderBodyContainsOnlyTitleAndContentWithoutInventingDatesOrClock() {
        val date=LocalDate.of(2026,10,15)
        val activity=ScheduleActivity("a","calendar","只写标题",kind=ActivityKind.DAY,memoTime=ActivityMemoTime(date,540))
        val items=buildDailyAgenda(date,DailyAgendaSettings(enabled=true),emptyList(),emptyList(),listOf(activity))
        assertEquals("只写标题",dailyAgendaDetail(items,10))
        assertFalse(dailyAgendaDetail(items,10).contains("2026"))
    }
    private val day = LocalDate.of(2026, 10, 12)
    private val options = DailyAgendaSettings(enabled = true)
    private val semester = Semester("s", "秋", 2026, Term.AUTUMN, day, 2)
    private fun entry(id: String, kind: String, date: LocalDate = day) =
        AcademicCalendarEntry(id, id, date, null, "报名截止", false, kind)

    @Test fun standaloneMemosWorkWithoutSemesterAndUntimedMemosNeverInventDate() {
        val point = ScheduleActivity("memo", "calendar", "memo", kind = ActivityKind.DAY, memoTime = ActivityMemoTime(day, 0))
        val undated = point.copy(id = "note", memoTime = ActivityMemoTime())
        val result = buildDailyAgenda(day, options, emptyList(), emptyList(), listOf(point, undated))
        assertEquals(1, result.size)
        assertEquals(LocalTime.MIDNIGHT, result.single().time)
        assertTrue(buildDailyAgenda(day, options.copy(includeActivities = false), emptyList(), emptyList(), listOf(point)).isEmpty())
    }

    @Test fun nextRunHonorsTimeWeekdaysAndDisabledState() {
        assertEquals(day.atTime(7, 30), nextDailyAgendaRun(options, day.atStartOfDay()))
        assertEquals(day.plusDays(1).atTime(7, 30), nextDailyAgendaRun(options, day.atTime(7, 30)))
        assertEquals(day.plusDays(7).atTime(7, 30), nextDailyAgendaRun(options.copy(weekdays = setOf(1)), day.atTime(8, 0)))
        assertNull(nextDailyAgendaRun(options.copy(enabled = false), day.atStartOfDay()))
        assertNull(nextDailyAgendaRun(options.copy(weekdays = emptySet()), day.atStartOfDay()))
        assertEquals(day.atStartOfDay(), nextDailyAgendaRun(options.copy(hour = 0, minute = 0), day.minusDays(1).atTime(23, 59)))
    }

    @Test fun onlyTodaysSelectedTypesAppearAndDeletingEntryRemovesIt() {
        val entries = listOf(entry("event", "activity"), entry("ctf", "competition"), entry("test", "exam"), entry("later", "exam", day.plusDays(1)))
        assertEquals(setOf("event", "ctf", "test"), buildDailyAgenda(day, options, emptyList(), entries).map { it.title }.toSet())
        assertEquals(listOf("test"), buildDailyAgenda(day, options.copy(includeActivities = false, includeCompetitions = false), emptyList(), entries).map { it.title })
        assertTrue(buildDailyAgenda(day, options, emptyList(), emptyList()).isEmpty())
    }

    @Test fun calendarAndTimetableCopiesDoNotDoubleNotifyAndHiddenItemsAreExcluded() {
        val calendar = entry("ctf", "competition")
        val activity = ScheduleActivity("academic:s:ctf", "s", "ctf", kind = ActivityKind.DAY, startDate = day)
        val hidden = activity.copy(id = "hidden", name = "hidden", hiddenFromSchedule = true)
        val schedule = ImportedSchedule(semester, emptyList(), TimetableState.EMPTY, listOf(activity, hidden))
        val result = buildDailyAgenda(day, options, listOf(schedule), listOf(calendar))
        assertEquals(1, result.size)
        assertEquals("competition", result.single().kind)
    }

    @Test fun datedActivityOutsideSemesterStillAppears() {
        val holiday = day.plusMonths(4)
        val activity = ScheduleActivity("local", "s", "寒假实践", kind = ActivityKind.DAY, startDate = holiday)
        val schedule = ImportedSchedule(semester, emptyList(), TimetableState.EMPTY, listOf(activity))
        assertEquals("寒假实践", buildDailyAgenda(holiday, options, listOf(schedule), emptyList()).single().title)
    }

    @Test fun timedItemsSortAfterAllDayAndSummaryRespectsLimit() {
        val allDay = entry("全天项目", "activity")
        val late = allDay.copy(id = "late", title = "晚间", time = LocalTime.of(20, 0))
        val early = allDay.copy(id = "early", title = "早间", time = LocalTime.of(8, 0))
        val items = buildDailyAgenda(day, options, emptyList(), listOf(late, allDay, early))
        assertEquals(listOf("全天项目", "早间", "晚间"), items.map { it.title })
        val detail = dailyAgendaDetail(items, 1)
        assertTrue(detail.contains("全天项目"))
        assertTrue(detail.contains("另有 2 项"))
        assertFalse(detail.contains("晚间"))
    }

    @Test fun cancelledCoursesAreExcludedAndCoursesRequireOptIn() {
        val course = Course("c", "s", SourceType.QNU_EDU, "c", name = "测试课程")
        val meeting = CourseMeeting("m", "c", 1, 1, 2, WeekRule.of(listOf(1)), sourceType = SourceType.QNU_EDU)
        val state = TimetableState(courses = listOf(course), meetings = listOf(meeting))
        val schedule = ImportedSchedule(semester, emptyList(), state)
        assertTrue(buildDailyAgenda(day, options, listOf(schedule), emptyList()).isEmpty())
        assertEquals(1, buildDailyAgenda(day, options.copy(includeCourses = true), listOf(schedule), emptyList()).size)
        val cancel = ScheduleException("cancel", "c", ExceptionType.CANCEL, SlotRef(1, 1, 1, 2), sourceType = SourceType.QNU_EDU)
        assertTrue(buildDailyAgenda(day, options.copy(includeCourses = true), listOf(schedule.copy(state = state.copy(exceptions = listOf(cancel)))), emptyList()).isEmpty())
    }
}
