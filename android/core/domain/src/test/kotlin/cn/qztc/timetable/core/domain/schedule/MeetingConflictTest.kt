package cn.qztc.timetable.core.domain.schedule

import cn.qztc.timetable.core.domain.model.Course
import cn.qztc.timetable.core.domain.model.CourseMeeting
import cn.qztc.timetable.core.domain.model.ExceptionType
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

class MeetingConflictTest {

    private val semester = Semester(
        id = "s1",
        name = "2024 秋",
        schoolYear = 2024,
        term = Term.AUTUMN,
        startDate = LocalDate.of(2024, 9, 2),
        totalWeeks = 20,
    )

    @Test
    fun `same weekday overlapping periods and weeks are conflicts`() {
        val hits = MeetingConflict.overlapping(
            weekday = 1,
            periodStart = 3,
            periodEnd = 4,
            weeks = listOf(1, 2),
            state = stateWith(meeting(weekday = 1, start = 4, end = 5, weeks = listOf(2, 3))),
            semester = semester,
        )
        assertEquals(listOf("线性代数"), hits.map { it.courseName })
        assertEquals(listOf(2), hits.single().weeks)
    }

    @Test
    fun `different weekday or weeks are not conflicts`() {
        val otherDay = stateWith(meeting(weekday = 2, start = 1, end = 2, weeks = listOf(1)))
        val otherWeek = stateWith(meeting(weekday = 1, start = 1, end = 2, weeks = listOf(2)))
        assertTrue(
            MeetingConflict.overlapping(1, 1, 2, listOf(1), otherDay, semester).isEmpty(),
        )
        assertTrue(
            MeetingConflict.overlapping(1, 1, 2, listOf(1), otherWeek, semester).isEmpty(),
        )
    }

    @Test
    fun `the meeting being edited is ignored`() {
        val hits = MeetingConflict.overlapping(
            weekday = 1,
            periodStart = 1,
            periodEnd = 2,
            weeks = listOf(1),
            state = stateWith(meeting(weekday = 1, start = 1, end = 2, weeks = listOf(1))),
            semester = semester,
            ignoreMeetingId = "m1",
        )
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `cancelled weeks do not occupy the slot`() {
        val cancel = ScheduleException(
            id = "e1",
            courseId = "c1",
            type = ExceptionType.CANCEL,
            original = SlotRef(week = 1, weekday = 1, periodStart = 1, periodEnd = 2),
            sourceType = SourceType.MANUAL,
        )
        val state = stateWith(meeting(weekday = 1, start = 1, end = 2, weeks = listOf(1, 2)), cancel)
        assertTrue(
            MeetingConflict.overlapping(1, 1, 2, listOf(1), state, semester).isEmpty(),
        )
        assertEquals(
            listOf(2),
            MeetingConflict.overlapping(1, 1, 2, listOf(1, 2), state, semester).single().weeks,
        )
    }

    @Test
    fun `moved-out original slot can be overwritten`() {
        val move = ScheduleException(
            id = "e1",
            courseId = "c1",
            type = ExceptionType.MOVE,
            original = SlotRef(week = 2, weekday = 1, periodStart = 1, periodEnd = 2),
            replacement = SlotRef(week = 2, weekday = 4, periodStart = 7, periodEnd = 8),
            sourceType = SourceType.MANUAL,
        )
        val state = stateWith(meeting(weekday = 1, start = 1, end = 2, weeks = listOf(1, 2, 3)), move)
        assertTrue(
            MeetingConflict.overlapping(1, 1, 2, listOf(2), state, semester).isEmpty(),
        )
        val movedIn = MeetingConflict.overlapping(4, 7, 8, listOf(2), state, semester)
        assertEquals(listOf("线性代数"), hitsNames(movedIn))
    }

    private fun hitsNames(hits: List<MeetingConflictHit>) = hits.map { it.courseName }

    @Test fun holidayPlanAndRemappedMakeupBothParticipateInConflictReview() {
        val state = stateWith(meeting(1, 1, 2, listOf(1, 3)))
        val holiday = cn.qztc.timetable.core.domain.calendar.AcademicDay(
            "holiday", semester.id, semester.startDate, cn.qztc.timetable.core.domain.calendar.AcademicDayKind.HOLIDAY, "假期")
        val makeup = holiday.copy(id = "makeup", date = semester.startDate.plusDays(5),
            kind = cn.qztc.timetable.core.domain.calendar.AcademicDayKind.MAKEUP, sourceWeek = 1, sourceWeekday = 1)
        val calendar = cn.qztc.timetable.core.domain.calendar.AcademicCalendar(listOf(holiday, makeup))
        val hits = MeetingConflict.overlapping(1, 1, 2, listOf(1), state, semester, calendar = calendar)
        assertEquals(setOf(1, 6), hits.map { it.weekday }.toSet())
        assertTrue(hits.all { it.weeks == listOf(1) })
        assertTrue(MeetingConflict.overlapping(1, 1, 2, listOf(2), state, semester, calendar = calendar).isEmpty())
    }

    private fun stateWith(meeting: CourseMeeting, vararg exceptions: ScheduleException) = TimetableState(
        courses = listOf(
            Course(
                id = meeting.courseId,
                semesterId = "sem",
                sourceType = SourceType.MANUAL,
                sourceKey = meeting.courseId,
                name = "线性代数",
            ),
        ),
        meetings = listOf(meeting),
        exceptions = exceptions.toList(),
    )

    private fun meeting(
        weekday: Int,
        start: Int,
        end: Int,
        weeks: List<Int>,
    ) = CourseMeeting(
        id = "m1",
        courseId = "c1",
        weekday = weekday,
        periodStart = start,
        periodEnd = end,
        weekRule = WeekRule.of(weeks),
        sourceType = SourceType.MANUAL,
    )
}
