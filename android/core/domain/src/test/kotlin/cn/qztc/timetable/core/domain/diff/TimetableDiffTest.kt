package cn.qztc.timetable.core.domain.diff

import cn.qztc.timetable.core.domain.model.Course
import cn.qztc.timetable.core.domain.model.CourseMeeting
import cn.qztc.timetable.core.domain.model.ExceptionType
import cn.qztc.timetable.core.domain.model.MeetingField
import cn.qztc.timetable.core.domain.model.ScheduleException
import cn.qztc.timetable.core.domain.model.SlotRef
import cn.qztc.timetable.core.domain.model.SourceType
import cn.qztc.timetable.core.domain.model.WeekRule
import cn.qztc.timetable.core.domain.schedule.TimetableState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableDiffTest {

    private fun course(id: String, source: SourceType = SourceType.QNU_EDU) =
        Course(id = id, semesterId = "s1", sourceType = source, sourceKey = id, name = "课程$id")

    private fun meeting(
        courseId: String,
        weekday: Int = 1,
        start: Int = 1,
        end: Int = 2,
        teacher: String? = "老师A",
        room: String? = "教室A",
        weeks: List<Int> = (1..16).toList(),
        overrides: Set<MeetingField> = emptySet(),
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
        overrideMask = overrides,
    )

    private fun makeup(courseId: String, week: Int): ScheduleException {
        val slot = SlotRef(week = week, weekday = 5, periodStart = 11, periodEnd = 12)
        return ScheduleException(
            id = ScheduleException.stableId(ExceptionType.MAKEUP, courseId, slot),
            courseId = courseId,
            type = ExceptionType.MAKEUP,
            replacement = slot,
            sourceType = SourceType.QNU_EDU,
        )
    }

    @Test
    fun `identical states produce empty diff`() {
        val state = TimetableState(listOf(course("c1")), listOf(meeting("c1")), listOf(makeup("c1", 9)))
        assertTrue(TimetableDiffer.diff(state, state).isEmpty)
    }

    @Test
    fun `added and removed detected by id`() {
        val current = TimetableState(listOf(course("c1")), listOf(meeting("c1")), emptyList())
        val candidate = TimetableState(
            listOf(course("c1"), course("c2")),
            listOf(meeting("c1"), meeting("c2", weekday = 3)),
            listOf(makeup("c1", 9)),
        )
        val diff = TimetableDiffer.diff(current, candidate)
        assertEquals(listOf("c2"), diff.addedCourses.map { it.id })
        assertEquals(1, diff.addedMeetings.size)
        assertEquals(1, diff.addedExceptions.size)
        assertTrue(diff.removedCourses.isEmpty())

        val reverse = TimetableDiffer.diff(candidate, current)
        assertEquals(listOf("c2"), reverse.removedCourses.map { it.id })
        assertEquals(1, reverse.removedMeetings.size)
        assertEquals(1, reverse.removedExceptions.size)
    }

    @Test
    fun `field changes detected`() {
        val current = TimetableState(listOf(course("c1")), listOf(meeting("c1")), emptyList())
        val candidate = TimetableState(
            listOf(course("c1")),
            listOf(meeting("c1", teacher = "老师B", room = "教室B", weeks = (1..8).toList())),
            emptyList(),
        )
        val diff = TimetableDiffer.diff(current, candidate)
        val change = diff.changedMeetings.single()
        assertEquals(
            setOf(MeetingField.TEACHER, MeetingField.ROOM, MeetingField.WEEKS),
            change.fields.map { it.field }.toSet(),
        )
        assertTrue(change.fields.none { it.skippedByOverride })
    }

    @Test
    fun `override mask marks field as skipped`() {
        val current = TimetableState(
            listOf(course("c1")),
            listOf(meeting("c1", room = "用户改的教室", overrides = setOf(MeetingField.ROOM))),
            emptyList(),
        )
        val candidate = TimetableState(
            listOf(course("c1")),
            listOf(meeting("c1", room = "教务教室", teacher = "老师B")),
            emptyList(),
        )
        val diff = TimetableDiffer.diff(current, candidate)
        val fields = diff.changedMeetings.single().fields.associateBy { it.field }
        assertTrue(fields.getValue(MeetingField.ROOM).skippedByOverride)
        assertTrue(!fields.getValue(MeetingField.TEACHER).skippedByOverride)
    }

    @Test
    fun `apply upserts removes and respects override`() {
        val manualCourse = course("m1", SourceType.MANUAL)
        val current = TimetableState(
            listOf(course("c1"), course("c2"), manualCourse),
            listOf(meeting("c1", room = "用户改的教室", overrides = setOf(MeetingField.ROOM)), meeting("c2", weekday = 3)),
            emptyList(),
        )
        val candidate = TimetableState(
            listOf(course("c1"), course("c3")),
            listOf(meeting("c1", room = "教务教室", teacher = "老师B"), meeting("c3", weekday = 5)),
            listOf(makeup("c1", 9)),
        )
        val diff = TimetableDiffer.diff(
            current.filterBySource(SourceType.QNU_EDU),
            candidate,
        )
        val applied = TimetableDiffApplier.apply(current, diff)

        assertEquals(setOf("c1", "c3", "m1"), applied.courses.map { it.id }.toSet())
        val c1 = applied.meetings.single { it.courseId == "c1" }
        assertEquals("用户改的教室", c1.room)
        assertEquals("老师B", c1.teacher)
        assertEquals(setOf(MeetingField.ROOM), c1.overrideMask)
        assertEquals(1, applied.exceptions.size)
        assertTrue(applied.meetings.none { it.courseId == "c2" })
    }

    @Test
    fun `apply then reverse diff restores original state`() {
        val current = TimetableState(listOf(course("c1")), listOf(meeting("c1")), emptyList())
        val candidate = TimetableState(
            listOf(course("c2")),
            listOf(meeting("c2", weekday = 2)),
            listOf(makeup("c2", 5)),
        )
        val forward = TimetableDiffer.diff(current, candidate)
        val applied = TimetableDiffApplier.apply(current, forward)
        val backward = TimetableDiffer.diff(applied, current)
        val restored = TimetableDiffApplier.apply(applied, backward)
        assertEquals(current.courses.toSet(), restored.courses.toSet())
        assertEquals(current.meetings.toSet(), restored.meetings.toSet())
        assertEquals(current.exceptions.toSet(), restored.exceptions.toSet())
    }

    @Test
    fun `course metadata changes are detected and applied`() {
        val currentCourse = course("c1").copy(nature = "通识", credits = "2.0")
        val nextCourse = currentCourse.copy(
            nature = "专业课",
            credits = "3.0",
            selectionAttribute = "专业必修",
            assessmentMethod = "考试",
        )
        val current = TimetableState(listOf(currentCourse), listOf(meeting("c1")), emptyList())
        val candidate = TimetableState(listOf(nextCourse), listOf(meeting("c1")), emptyList())
        val diff = TimetableDiffer.diff(current, candidate)
        assertEquals(1, diff.changedCourses.size)
        val applied = TimetableDiffApplier.apply(current, diff)
        assertEquals(nextCourse, applied.courses.single())
    }
}
