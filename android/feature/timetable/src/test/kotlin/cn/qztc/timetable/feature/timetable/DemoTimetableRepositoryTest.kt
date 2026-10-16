package cn.qztc.timetable.feature.timetable

import cn.qztc.timetable.core.domain.model.Course
import cn.qztc.timetable.core.domain.model.CourseMeeting
import cn.qztc.timetable.core.domain.model.ExceptionType
import cn.qztc.timetable.core.domain.model.QnuPeriodDefaults
import cn.qztc.timetable.core.domain.model.Semester
import cn.qztc.timetable.core.domain.model.SourceType
import cn.qztc.timetable.core.domain.model.Term
import cn.qztc.timetable.core.domain.model.WeekRule
import cn.qztc.timetable.core.domain.calendar.AcademicDay
import cn.qztc.timetable.core.domain.calendar.AcademicDayKind
import cn.qztc.timetable.core.domain.model.ActivityKind
import cn.qztc.timetable.core.domain.model.ScheduleActivity
import cn.qztc.timetable.core.domain.model.ScheduleException
import cn.qztc.timetable.core.domain.model.SlotRef
import cn.qztc.timetable.core.domain.schedule.TimetableState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoTimetableRepositoryTest {
    @Test
    fun `week two friday has four course blocks before applying changes`() {
        assertEquals(4, SyntheticTimetableFixture.occurrences(2).count { it.weekday == 5 })
    }

    @Test
    fun `noon and afternoon classes on one day keep separate original rows`() {
        val friday = SyntheticTimetableFixture.occurrences(1).filter { it.weekday == 5 }
        assertTrue(friday.any { it.periodStart == 5 && it.periodEnd == 6 })
        assertTrue(friday.any { it.periodStart == 9 && it.periodEnd == 10 })
        assertEquals(2, friday.count { it.periodStart in 5..6 || it.periodStart in 7..10 })
    }

    @Test
    fun `week three includes a weekend sample to validate full seven column layout`() {
        val weekend = SyntheticTimetableFixture.occurrences(3).filter { it.weekday >= 6 }
        assertEquals(1, weekend.size)
        assertEquals("weekend-demo", weekend.single().course.id)
    }

    @Test
    fun `move change relocates project management to thursday`() {
        val course = SyntheticTimetableFixture.occurrences(2, setOf("ch1")).single { it.course.id == "pm" }
        assertEquals(4, course.weekday)
        assertEquals("朝阳305", course.room)
        assertTrue(course.moved)
    }

    @Test
    fun `cancel change only cancels current week occurrence`() {
        val current = SyntheticTimetableFixture.occurrences(2, setOf("ch2")).single { it.course.id == "career" }
        val later = SyntheticTimetableFixture.occurrences(3, setOf("ch2")).single { it.course.id == "career" }
        assertTrue(current.cancelled)
        assertTrue(!later.cancelled)
    }

    @Test
    fun `schedule state supports apply ignore and restore`() {
        val state = DemoScheduleState()
        state.pending += listOf("ch1", "ch2")
        state.apply(listOf("ch1"))
        assertEquals(listOf("ch2"), state.pending)
        assertEquals(listOf("ch1"), state.applied)

        state.ignore(listOf("ch2"))
        assertTrue(state.pending.isEmpty())
        assertEquals(listOf("ch2"), state.ignored)

        state.restorePending(listOf("ch1", "ch2"))
        assertEquals(listOf("ch1", "ch2"), state.pending)
        assertTrue(state.applied.isEmpty())
        assertTrue(state.ignored.isEmpty())
    }

    @Test
    fun `sync completion keeps pending changes and reports result`() {
        val state = DemoScheduleState()
        state.pending += listOf("ch1", "ch2")
        state.syncPhase = "对比本地快照…"
        state.finishSync(foundChanges = true)
        assertEquals("刚刚 · 发现 2 项待处理变化", state.syncResult)
        assertEquals(null, state.syncPhase)
        assertEquals(2, state.pending.size)
    }

    @Test
    fun `imported qnu periods keep real times and their original grid rows`() {
        val course = Course(
            id = "real-course",
            semesterId = "real-semester",
            sourceType = SourceType.QNU_EDU,
            sourceKey = "real-course",
            name = "真实课程",
        )
        val meeting = CourseMeeting(
            id = "real-meeting",
            courseId = course.id,
            weekday = 1,
            periodStart = 7,
            periodEnd = 8,
            weekRule = WeekRule.of(listOf(1)),
            teacher = "老师",
            room = "教室",
            sourceType = SourceType.QNU_EDU,
        )
        val state = DemoScheduleState()
        state.loadImported(
            ImportedSchedule(
                semester = Semester("real-semester", "真实学期", 2026, Term.AUTUMN, LocalDate.of(2026, 8, 17)),
                periods = QnuPeriodDefaults.periods(),
                state = TimetableState(listOf(course), listOf(meeting)),
            ),
        )

        assertEquals(LocalDate.now().dayOfWeek.value, state.selectedDay)
        val occurrence = state.occurrences(1).single()
        assertEquals(7, occurrence.periodStart)
        assertEquals(8, occurrence.periodEnd)
        assertEquals(7, occurrence.layoutPeriodStart)
        assertEquals(8, occurrence.layoutPeriodEnd)
        assertEquals(7, state.periodFor(7).no)
        assertEquals("12:20", state.actualPeriodFor(5).startTime.toString())
        assertEquals("18:10", state.periodFor(10).endTime.toString())
        assertEquals("19:00", state.periodFor(11).startTime.toString())
        assertEquals("第9节", state.displayPeriodLabel(11))
        assertEquals("第7-8节", displayPeriodRangeLabel(9, 10))
        assertEquals("午间第1-2节", displayPeriodRangeLabel(5, 6))
    }

    @Test
    fun `historical import opens a real semester date instead of the device date`() {
        val course = Course(
            id = "historic-course",
            semesterId = "historic-semester",
            sourceType = SourceType.QNU_EDU,
            sourceKey = "historic-course",
            name = "历史课程",
        )
        val meeting = CourseMeeting(
            id = "historic-meeting",
            courseId = course.id,
            weekday = 3,
            periodStart = 5,
            periodEnd = 6,
            weekRule = WeekRule.of(listOf(2)),
            sourceType = SourceType.QNU_EDU,
        )
        val state = DemoScheduleState()
        state.loadImported(
            ImportedSchedule(
                semester = Semester("historic-semester", "2024-2025-1", 2024, Term.AUTUMN, LocalDate.of(2024, 9, 2)),
                periods = QnuPeriodDefaults.periods(),
                state = TimetableState(listOf(course), listOf(meeting)),
            ),
        )

        assertEquals(2, state.currentWeek)
        assertEquals(3, state.referenceWeekday)
        assertEquals(LocalDate.of(2024, 9, 11), state.dateFor(state.currentWeek, state.referenceWeekday))
        assertTrue(!state.isCurrentDateInImportedSemester)
    }

    @Test
    fun `scoped cancellation marks only its week and original preview restores the class`() {
        val semester = Semester("changed-semester", "2025 秋", 2025, Term.AUTUMN, LocalDate.of(2025, 9, 1))
        val course = Course("changed-course", semester.id, SourceType.QNU_EDU, "changed-course", name = "单次停课")
        val meeting = CourseMeeting(
            "changed-meeting", course.id, 4, 3, 4, WeekRule.of((1..18).toList()),
            "教师", "教室", SourceType.QNU_EDU,
        )
        val cancellation = ScheduleException(
            "changed-cancel", course.id, ExceptionType.CANCEL, SlotRef(6, 4, 3, 4),
            sourceType = SourceType.QNU_EDU,
        )
        val state = DemoScheduleState().apply {
            loadImported(
                ImportedSchedule(
                    semester,
                    QnuPeriodDefaults.periods(),
                    TimetableState(listOf(course), listOf(meeting), listOf(cancellation)),
                ),
            )
        }

        assertFalse(state.hasScheduleChange(5))
        assertTrue(state.hasScheduleChange(6))
        assertTrue(state.occurrences(6).single().cancelled)
        assertFalse(state.occurrences(6, includeScheduleChanges = false).single().cancelled)
        assertFalse(state.occurrences(7).single().cancelled)
    }

    @Test
    fun `automatic imported colors remain distinct beyond built in palette`() {
        val tokens = (0 until 32).map(::automaticCourseColorToken)
        assertEquals(tokens.size, tokens.toSet().size)
    }

    @Test
    fun `custom imported colors use the same fill alpha as the preset palette`() {
        val occurrence = DemoOccurrence(
            course = Course(
                id = "custom-color",
                semesterId = "semester",
                sourceType = SourceType.MANUAL,
                sourceKey = "custom-color",
                name = "自定义颜色",
                color = 0xFF3B82F6.toInt(),
            ),
            paletteIndex = 0,
            weekday = 1,
            periodStart = 1,
            periodEnd = 1,
            room = "教室",
            teacher = "教师",
        )
        val preset = cn.qztc.timetable.core.ui.theme.CoursePalette.first()

        assertEquals(preset.lightBg.alpha, occurrence.visualColor(dark = false).lightBg.alpha, .001f)
        assertEquals(preset.darkBg.alpha, occurrence.visualColor(dark = true).darkBg.alpha, .001f)
    }

    @Test
    fun `activity view switch only appears in an affected week`() {
        val semester = Semester("calendar-semester", "2026 秋", 2026, Term.AUTUMN, LocalDate.of(2026, 8, 31))
        val base = ImportedSchedule(semester, QnuPeriodDefaults.periods(), TimetableState.EMPTY)
        val holiday = AcademicDay(
            id = "holiday",
            semesterId = semester.id,
            date = LocalDate.of(2026, 10, 1),
            kind = AcademicDayKind.HOLIDAY,
            name = "国庆节",
        )
        val activity = ScheduleActivity(
            id = "calendar-event:s:sports",
            semesterId = semester.id,
            name = "校运会",
            kind = ActivityKind.DAY,
            startDate = LocalDate.of(2026, 11, 5),
            endDate = LocalDate.of(2026, 11, 6),
        )

        assertFalse(hasWeekActivityView(base, 1))
        assertFalse(hasWeekActivityView(base.copy(academicDays = listOf(holiday)), 1))
        assertTrue(hasWeekActivityView(base.copy(academicDays = listOf(holiday)), 5))
        assertFalse(hasWeekActivityView(base.copy(activities = listOf(activity)), 5))
        assertTrue(hasWeekActivityView(base.copy(activities = listOf(activity)), 10))
    }
    @Test fun manualCalendarActivitiesDoNotEnableOrPopulateTimetableViews() {
        val semester = Semester("s", "test", 2026, Term.AUTUMN, LocalDate.of(2026, 8, 31))
        val manual = ScheduleActivity("manual", semester.id, "same title", kind = ActivityKind.DAY,
            startDate = semester.startDate, endDate = semester.startDate.plusDays(6))
        val base = ImportedSchedule(semester, QnuPeriodDefaults.periods(), TimetableState.EMPTY, activities = listOf(manual))
        val state = DemoScheduleState().apply { loadImported(base) }
        assertEquals(listOf(manual), state.activities())
        assertTrue(state.timetableActivities().isEmpty())
        assertTrue(state.activitiesOn(1, 1).isEmpty())
        assertTrue(state.activityBlocks(1, (1..7).toList()).isEmpty())
        assertFalse(hasWeekActivityView(base, 1))
        val school = manual.copy(id = "calendar-event:s:school")
        state.loadImported(base.copy(activities = listOf(manual, school)))
        assertEquals(listOf(school), state.activitiesOn(1, 1))
        assertTrue(state.activityBlocks(1, (1..7).toList()).isNotEmpty())
        assertTrue(hasWeekActivityView(state.importedSchedule, 1))
    }

}
