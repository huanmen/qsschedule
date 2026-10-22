package cn.qztc.timetable.core.domain.schedule

import cn.qztc.timetable.core.domain.calendar.AcademicCalendar
import cn.qztc.timetable.core.domain.calendar.AcademicDay
import cn.qztc.timetable.core.domain.calendar.AcademicDayKind
import cn.qztc.timetable.core.domain.model.Course
import cn.qztc.timetable.core.domain.model.CourseMeeting
import cn.qztc.timetable.core.domain.model.ExceptionType
import cn.qztc.timetable.core.domain.model.OccurrenceState
import cn.qztc.timetable.core.domain.model.ScheduleException
import cn.qztc.timetable.core.domain.model.Semester
import cn.qztc.timetable.core.domain.model.SlotRef
import cn.qztc.timetable.core.domain.model.SourceType
import cn.qztc.timetable.core.domain.model.Term
import cn.qztc.timetable.core.domain.model.WeekRule
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OccurrenceExpanderTest {
    @Test fun `cancelled period does not cancel neighboring periods or weeks`() {
        val state=TimetableState(listOf(course("c1")),listOf(meeting("c1",start=1,end=4)),listOf(
            ScheduleException("one-period","c1",ExceptionType.CANCEL,SlotRef(2,1,2,2),sourceType=SourceType.QNU_EDU)))
        assertTrue(OccurrenceExpander.expand(semester,state,1).none { it.state==OccurrenceState.CANCELLED })
        val second=OccurrenceExpander.expand(semester,state,2)
        assertEquals(listOf(1..1,2..2,3..4),second.map { it.periodStart..it.periodEnd })
        assertEquals(1,second.count { it.state==OccurrenceState.CANCELLED })
    }

    private val semester = Semester(
        id = "s1",
        name = "2024 秋",
        schoolYear = 2024,
        term = Term.AUTUMN,
        startDate = LocalDate.of(2024, 9, 2),
        totalWeeks = 20,
    )

    private fun course(id: String) = Course(
        id = id, semesterId = "s1", sourceType = SourceType.QNU_EDU, sourceKey = id, name = "课程$id",
    )

    private fun meeting(
        courseId: String,
        weekday: Int = 1,
        start: Int = 1,
        end: Int = 2,
        weeks: List<Int> = listOf(1, 2, 3),
        teacher: String? = "老师A",
        room: String? = "教室A",
    ) = CourseMeeting(
        id = CourseMeeting.stableId(courseId, weekday, start, end),
        courseId = courseId,
        weekday = weekday,
        periodStart = start,
        periodEnd = end,
        weekRule = WeekRule.of(weeks),
        teacher = teacher,
        room = room,
        sourceType = SourceType.QNU_EDU,
    )

    @Test
    fun `normal expansion produces one occurrence per week`() {
        val state = TimetableState(listOf(course("c1")), listOf(meeting("c1")), emptyList())
        val occurrences = OccurrenceExpander.expand(semester, state)
        assertEquals(3, occurrences.size)
        assertEquals(LocalDate.of(2024, 9, 2), occurrences[0].date)
        assertEquals(LocalDate.of(2024, 9, 16), occurrences[2].date)
        assertTrue(occurrences.all { it.state == OccurrenceState.NORMAL })
    }

    @Test
    fun `weeks beyond semester total are dropped`() {
        val state = TimetableState(
            listOf(course("c1")),
            listOf(meeting("c1", weeks = listOf(1, 25))),
            emptyList(),
        )
        assertEquals(1, OccurrenceExpander.expand(semester, state).size)
    }

    @Test
    fun `cancel marks matching occurrence cancelled`() {
        val cancel = ScheduleException(
            id = "e1", courseId = "c1", type = ExceptionType.CANCEL,
            original = SlotRef(week = 2, weekday = 1, periodStart = 1, periodEnd = 2),
            sourceType = SourceType.QNU_EDU,
        )
        val state = TimetableState(listOf(course("c1")), listOf(meeting("c1")), listOf(cancel))
        val occurrences = OccurrenceExpander.expand(semester, state)
        assertEquals(3, occurrences.size)
        val cancelled = occurrences.single { it.week == 2 }
        assertEquals(OccurrenceState.CANCELLED, cancelled.state)
        assertEquals("e1", cancelled.exceptionId)
    }

    @Test
    fun `move cancels original and adds moved-in occurrence`() {
        val move = ScheduleException(
            id = "e1", courseId = "c1", type = ExceptionType.MOVE,
            original = SlotRef(week = 2, weekday = 1, periodStart = 1, periodEnd = 2),
            replacement = SlotRef(week = 2, weekday = 4, periodStart = 7, periodEnd = 8, room = "教室B"),
            sourceType = SourceType.QNU_EDU,
        )
        val state = TimetableState(listOf(course("c1")), listOf(meeting("c1")), listOf(move))
        val occurrences = OccurrenceExpander.expand(semester, state)
        assertEquals(4, occurrences.size)
        assertEquals(
            OccurrenceState.CANCELLED,
            occurrences.single { it.week == 2 && it.weekday == 1 }.state,
        )
        val movedIn = occurrences.single { it.state == OccurrenceState.MOVED_IN }
        assertEquals(LocalDate.of(2024, 9, 12), movedIn.date)
        assertEquals("教室B", movedIn.room)
        assertEquals("老师A", movedIn.teacher)
    }

    @Test
    fun `move takes precedence over duplicate makeup at the same destination`() {
        val move = ScheduleException(
            id = "move", courseId = "c1", type = ExceptionType.MOVE,
            original = SlotRef(week = 2, weekday = 1, periodStart = 1, periodEnd = 2),
            replacement = SlotRef(week = 3, weekday = 4, periodStart = 7, periodEnd = 8, room = "新教室"),
            sourceType = SourceType.QNU_EDU,
        )
        val duplicateMakeup = ScheduleException(
            id = "makeup", courseId = "c1", type = ExceptionType.MAKEUP,
            replacement = SlotRef(week = 3, weekday = 4, periodStart = 7, periodEnd = 8),
            sourceType = SourceType.QNU_EDU,
        )
        val independentMakeup = ScheduleException(
            id = "independent", courseId = "c1", type = ExceptionType.MAKEUP,
            replacement = SlotRef(week = 5, weekday = 6, periodStart = 9, periodEnd = 10),
            sourceType = SourceType.QNU_EDU,
        )
        val state = TimetableState(
            listOf(course("c1")),
            listOf(meeting("c1", weeks = (1..8).toList())),
            listOf(move, duplicateMakeup, independentMakeup),
        )

        val occurrences = OccurrenceExpander.expand(semester, state)
        val destination = occurrences.filter { it.week == 3 && it.weekday == 4 && it.periodStart == 7 }
        assertEquals(1, destination.size)
        assertEquals(OccurrenceState.MOVED_IN, destination.single().state)
        assertEquals("新教室", destination.single().room)
        assertEquals(OccurrenceState.CANCELLED, occurrences.single { it.week == 2 }.state)
        assertEquals(1, occurrences.count { it.state == OccurrenceState.MAKEUP && it.week == 5 })
    }

    @Test
    fun `makeup adds occurrence`() {
        val makeup = ScheduleException(
            id = "e1", courseId = "c1", type = ExceptionType.MAKEUP,
            replacement = SlotRef(week = 9, weekday = 5, periodStart = 11, periodEnd = 12, room = "教室C"),
            sourceType = SourceType.QNU_EDU,
        )
        val state = TimetableState(listOf(course("c1")), listOf(meeting("c1")), listOf(makeup))
        val occurrences = OccurrenceExpander.expand(semester, state)
        val added = occurrences.single { it.state == OccurrenceState.MAKEUP }
        assertEquals(9, added.week)
        assertEquals("教室C", added.room)
    }

    @Test
    fun `makeup then cancelled is marked cancelled`() {
        val slot = SlotRef(week = 15, weekday = 6, periodStart = 1, periodEnd = 1)
        val exceptions = listOf(
            ScheduleException(
                id = "e1", courseId = "c1", type = ExceptionType.MAKEUP,
                replacement = slot, sourceType = SourceType.QNU_EDU,
            ),
            ScheduleException(
                id = "e2", courseId = "c1", type = ExceptionType.CANCEL,
                original = slot, sourceType = SourceType.QNU_EDU,
            ),
        )
        val state = TimetableState(listOf(course("c1")), emptyList(), exceptions)
        val occurrences = OccurrenceExpander.expand(semester, state)
        assertEquals(1, occurrences.size)
        assertEquals(OccurrenceState.CANCELLED, occurrences.single().state)
    }

    @Test
    fun `substitute replaces teacher`() {
        val substitute = ScheduleException(
            id = "e1", courseId = "c1", type = ExceptionType.SUBSTITUTE,
            original = SlotRef(week = 1, weekday = 1, periodStart = 1, periodEnd = 2),
            substituteTeacher = "代课老师",
            sourceType = SourceType.QNU_EDU,
        )
        val state = TimetableState(listOf(course("c1")), listOf(meeting("c1")), listOf(substitute))
        val substituted = OccurrenceExpander.expand(semester, state)
            .single { it.state == OccurrenceState.SUBSTITUTED }
        assertEquals(1, substituted.week)
        assertEquals("代课老师", substituted.teacher)
    }

    @Test
    fun `overlapping active occurrences are conflicts`() {
        val state = TimetableState(
            listOf(course("c1"), course("c2")),
            listOf(
                meeting("c1", weekday = 1, start = 1, end = 2, weeks = listOf(1)),
                meeting("c2", weekday = 1, start = 2, end = 3, weeks = listOf(1)),
            ),
            emptyList(),
        )
        val occurrences = OccurrenceExpander.expand(semester, state)
        val conflicts = OccurrenceExpander.findConflicts(occurrences)
        assertEquals(1, conflicts.size)
        assertEquals(LocalDate.of(2024, 9, 2), conflicts.single().date)
    }

    @Test
    fun `cancelled occurrence does not conflict`() {
        val cancel = ScheduleException(
            id = "e1", courseId = "c1", type = ExceptionType.CANCEL,
            original = SlotRef(week = 1, weekday = 1, periodStart = 1, periodEnd = 2),
            sourceType = SourceType.QNU_EDU,
        )
        val state = TimetableState(
            listOf(course("c1"), course("c2")),
            listOf(
                meeting("c1", weekday = 1, start = 1, end = 2, weeks = listOf(1)),
                meeting("c2", weekday = 1, start = 2, end = 3, weeks = listOf(1)),
            ),
            listOf(cancel),
        )
        val occurrences = OccurrenceExpander.expand(semester, state)
        assertTrue(OccurrenceExpander.findConflicts(occurrences).isEmpty())
    }

    @Test
    fun `week scoped expansion matches filtered full expansion`() {
        val exceptions = listOf(
            ScheduleException(
                id = "cancel", courseId = "c1", type = ExceptionType.CANCEL,
                original = SlotRef(week = 1, weekday = 1, periodStart = 1, periodEnd = 2),
                sourceType = SourceType.QNU_EDU,
            ),
            ScheduleException(
                id = "move", courseId = "c1", type = ExceptionType.MOVE,
                original = SlotRef(week = 2, weekday = 1, periodStart = 1, periodEnd = 2),
                replacement = SlotRef(week = 3, weekday = 4, periodStart = 7, periodEnd = 8, room = "教室B"),
                sourceType = SourceType.QNU_EDU,
            ),
            ScheduleException(
                id = "makeup", courseId = "c1", type = ExceptionType.MAKEUP,
                replacement = SlotRef(week = 9, weekday = 5, periodStart = 11, periodEnd = 12, room = "教室C"),
                sourceType = SourceType.QNU_EDU,
            ),
            ScheduleException(
                id = "sub", courseId = "c1", type = ExceptionType.SUBSTITUTE,
                original = SlotRef(week = 4, weekday = 1, periodStart = 1, periodEnd = 2),
                substituteTeacher = "代课老师",
                sourceType = SourceType.QNU_EDU,
            ),
        )
        val state = TimetableState(
            listOf(course("c1")),
            listOf(meeting("c1", weeks = (1..20).toList())),
            exceptions,
        )
        val all = OccurrenceExpander.expand(semester, state)
        (1..20).forEach { week ->
            assertEquals(all.filter { it.week == week }, OccurrenceExpander.expand(semester, state, week))
        }
    }

    @Test
    fun `week scoped move keeps original teacher on replacement week`() {
        val move = ScheduleException(
            id = "e1", courseId = "c1", type = ExceptionType.MOVE,
            original = SlotRef(week = 2, weekday = 1, periodStart = 1, periodEnd = 2),
            replacement = SlotRef(week = 3, weekday = 4, periodStart = 7, periodEnd = 8, room = "教室B"),
            sourceType = SourceType.QNU_EDU,
        )
        val state = TimetableState(listOf(course("c1")), listOf(meeting("c1")), listOf(move))
        val replacementWeek = OccurrenceExpander.expand(semester, state, week = 3)
        val movedIn = replacementWeek.single { it.state == OccurrenceState.MOVED_IN }
        assertEquals("老师A", movedIn.teacher)
        assertEquals("教室B", movedIn.room)
        val originalWeek = OccurrenceExpander.expand(semester, state, week = 2)
        assertEquals(OccurrenceState.CANCELLED, originalWeek.single { it.weekday == 1 }.state)
        assertTrue(originalWeek.none { it.state == OccurrenceState.MOVED_IN })
    }

    @Test
    fun `visible week hides holiday and remaps makeup`() {
        val autumn = Semester(
            id = "s1",
            name = "2026 秋",
            schoolYear = 2026,
            term = Term.AUTUMN,
            startDate = LocalDate.of(2026, 8, 31),
            totalWeeks = 20,
        )
        val monday = LocalDate.of(2026, 10, 5)
        val saturday = LocalDate.of(2026, 10, 10)
        val week = checkNotNull(cn.qztc.timetable.core.domain.calendar.SemesterCalendar.weekOf(autumn, saturday))
        val state = TimetableState(listOf(course("c1")), listOf(meeting("c1", weeks = listOf(week))), emptyList())
        val calendar = AcademicCalendar(
            listOf(
                AcademicDay(
                    id = "h",
                    semesterId = "s1",
                    date = monday,
                    kind = AcademicDayKind.HOLIDAY,
                    name = "国庆节",
                ),
                AcademicDay(
                    id = "m",
                    semesterId = "s1",
                    date = saturday,
                    kind = AcademicDayKind.MAKEUP,
                    name = "国庆调休",
                    sourceWeek = week,
                    sourceWeekday = 1,
                ),
            ),
        )
        val visible = OccurrenceExpander.expandVisibleWeek(autumn, state, week, calendar)
        assertTrue(visible.none { it.weekday == 1 && it.isActive })
        val remapped = visible.single { it.weekday == 6 }
        assertEquals(saturday, remapped.date)
        assertEquals(1, remapped.periodStart)
        assertEquals("c1", remapped.courseId)
    }
}
