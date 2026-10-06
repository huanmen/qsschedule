package cn.qztc.timetable.core.domain.schedule

import cn.qztc.timetable.core.domain.model.ActivityKind
import cn.qztc.timetable.core.domain.model.Course
import cn.qztc.timetable.core.domain.model.CourseMeeting
import cn.qztc.timetable.core.domain.model.ScheduleActivity
import cn.qztc.timetable.core.domain.model.SourceType
import cn.qztc.timetable.core.domain.model.WeekRule
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityConflictTest {

    private val monday = LocalDate.of(2026, 9, 7)

    @Test
    fun `period activity conflicts with overlapping meeting on the same weekday and weeks`() {
        val activity = ScheduleActivity(
            id = "a1",
            semesterId = "sem",
            name = "讲座",
            kind = ActivityKind.PERIOD,
            weekday = 1,
            periodStart = 3,
            periodEnd = 4,
            weeks = listOf(1, 2),
        )
        val hits = ActivityConflict.overlappingMeetings(activity, stateWith(meeting(weekday = 1, start = 4, end = 5, weeks = listOf(2, 3))), ::dateOf)
        assertEquals(listOf("线性代数"), hits.map { it.courseName })
    }

    @Test
    fun `period activity ignores different weekday or non overlapping weeks`() {
        val activity = ScheduleActivity(
            id = "a1",
            semesterId = "sem",
            name = "讲座",
            kind = ActivityKind.PERIOD,
            weekday = 1,
            periodStart = 1,
            periodEnd = 2,
            weeks = listOf(1),
        )
        val otherDay = stateWith(meeting(weekday = 2, start = 1, end = 2, weeks = listOf(1)))
        val otherWeek = stateWith(meeting(weekday = 1, start = 1, end = 2, weeks = listOf(2)))
        assertTrue(ActivityConflict.overlappingMeetings(activity, otherDay, ::dateOf).isEmpty())
        assertTrue(ActivityConflict.overlappingMeetings(activity, otherWeek, ::dateOf).isEmpty())
    }

    @Test
    fun `day activity conflicts with any meeting on those dates`() {
        val activity = ScheduleActivity(
            id = "sports",
            semesterId = "sem",
            name = "运动会",
            kind = ActivityKind.DAY,
            startDate = LocalDate.of(2026, 9, 8),
            endDate = LocalDate.of(2026, 9, 9),
        )
        val tuesdayEvening = stateWith(meeting(weekday = 2, start = 11, end = 13, weeks = listOf(1)))
        val thursday = stateWith(meeting(weekday = 4, start = 1, end = 2, weeks = listOf(1)))
        assertEquals(1, ActivityConflict.overlappingMeetings(activity, tuesdayEvening, ::dateOf).size)
        assertTrue(ActivityConflict.overlappingMeetings(activity, thursday, ::dateOf).isEmpty())
    }

    private fun dateOf(week: Int, weekday: Int): LocalDate =
        monday.plusDays(((week - 1) * 7 + (weekday - 1)).toLong())

    private fun stateWith(meeting: CourseMeeting) = TimetableState(
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
