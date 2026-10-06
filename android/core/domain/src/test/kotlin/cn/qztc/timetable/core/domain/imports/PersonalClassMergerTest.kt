package cn.qztc.timetable.core.domain.imports

import cn.qztc.timetable.core.domain.model.Course
import cn.qztc.timetable.core.domain.model.CourseMeeting
import cn.qztc.timetable.core.domain.model.SourceType
import cn.qztc.timetable.core.domain.model.WeekRule
import cn.qztc.timetable.core.domain.schedule.TimetableState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalClassMergerTest {

    @Test
    fun `keeps personal slot and reports class-only extras`() {
        val personal = state(
            course("p-ds", "数据结构"),
            meeting("p-ds", weekday = 1, start = 1, end = 2, teacher = "王老师", room = "A101"),
        )
        val classTable = state(
            course("c-ds", "数据结构"),
            meeting("c-ds", weekday = 1, start = 1, end = 2, teacher = "王老师,李老师", room = "A101"),
            course("c-lab", "实验课"),
            meeting("c-lab", weekday = 3, start = 3, end = 4, teacher = "周老师", room = "B201"),
        )

        val result = PersonalClassMerger.merge(personal, classTable)

        assertEquals(1, result.preferred.meetings.size)
        assertEquals("王老师", result.preferred.meetings.single().teacher)
        assertEquals(listOf("实验课"), result.extras.map { it.course.name })
        assertEquals(listOf("李老师"), result.teacherConflicts.single().extraTeachers)
        assertTrue(result.needsReview)
    }

    @Test
    fun `identical tables do not need review`() {
        val personal = state(
            course("p-ds", "数据结构"),
            meeting("p-ds", weekday = 1, start = 1, end = 2, teacher = "王老师", room = "A101"),
        )
        val result = PersonalClassMerger.merge(personal, personal)
        assertTrue(!result.needsReview)
    }

    @Test
    fun `applies selected extras onto the personal course when names match`() {
        val personal = state(
            course("p-ds", "数据结构"),
            meeting("p-ds", weekday = 1, start = 1, end = 2, teacher = "王老师", room = "A101"),
        )
        val extraCourse = course("c-lab", "实验课")
        val extraMeeting = meeting("c-lab", weekday = 3, start = 3, end = 4, teacher = "周老师", room = "B201")
        val merged = PersonalClassMerger.apply(
            personal,
            listOf(PersonalClassMerger.ExtraSlot(extraCourse, extraMeeting)),
            emptyMap(),
        )

        assertEquals(setOf("数据结构", "实验课"), merged.courses.map { it.name }.toSet())
        assertEquals(2, merged.meetings.size)
    }

    @Test
    fun `keeps the personal teacher unless the user chooses another name`() {
        val personal = state(
            course("p-ds", "数据结构"),
            meeting("p-ds", weekday = 1, start = 1, end = 2, teacher = "王老师", room = "A101"),
        )
        val meetingId = personal.meetings.single().id
        val merged = PersonalClassMerger.apply(
            personal,
            emptyList(),
            mapOf(meetingId to "王老师、李老师"),
        )

        assertEquals("王老师、李老师", merged.meetings.single().teacher)
    }

    @Test
    fun `reports different courses that overlap in the same weeks`() {
        val personalCourse = course("p-math", "高等数学")
        val classCourse = course("c-english", "大学英语")
        val personalMeeting = meeting(personalCourse.id, 2, 1, 2, "甲", "A101")
        val classMeeting = meeting(classCourse.id, 2, 2, 3, "乙", "B201")

        val result = PersonalClassMerger.merge(
            state(personalCourse, personalMeeting),
            state(classCourse, classMeeting),
        )

        assertEquals(1, result.overlapConflicts.size)
        assertTrue(result.needsReview)
        assertEquals(listOf(1, 2, 3), result.overlapConflicts.single().weeks)
    }

    @Test fun `same title and start period have separate choices and repeated class rows coalesce`() {
        val p = course("p", "合成语言课")
        val c = course("c", p.name)
        val pShort = meeting(p.id, 2, 1, 1, "甲", "A101")
        val pLong = meeting(p.id, 2, 1, 2, "乙", "A102")
        val cShort = meeting(c.id, 2, 1, 1, "丙", "A101")
        val cLong = meeting(c.id, 2, 1, 2, "丁", "A102")
        val personal = state(p, pShort, pLong)
        val result = PersonalClassMerger.merge(personal, state(c, cShort, cLong, cLong.copy(id = "repeat", teacher = "戊")))
        assertEquals(2, result.teacherConflicts.size)
        assertEquals(2, result.teacherConflicts.map { it.meetingId }.distinct().size)
        assertEquals(listOf("丁", "戊"), result.teacherConflicts.single { it.meetingId == pLong.id }.extraTeachers)
        val applied = PersonalClassMerger.apply(personal, emptyList(), mapOf(pLong.id to "己"))
        assertEquals("甲", applied.meetings.single { it.id == pShort.id }.teacher)
        assertEquals("己", applied.meetings.single { it.id == pLong.id }.teacher)
    }

    private fun course(id: String, name: String) = Course(
        id = id,
        semesterId = "sem",
        sourceType = SourceType.QNU_EDU,
        sourceKey = name,
        name = name,
    )

    private fun meeting(courseId: String, weekday: Int, start: Int, end: Int, teacher: String, room: String) =
        CourseMeeting(
            id = CourseMeeting.stableId(courseId, weekday, start, end),
            courseId = courseId,
            weekday = weekday,
            periodStart = start,
            periodEnd = end,
            weekRule = WeekRule.of(listOf(1, 2, 3), "1-3周"),
            teacher = teacher,
            room = room,
            sourceType = SourceType.QNU_EDU,
        )

    private fun state(vararg items: Any) = TimetableState(
        courses = items.filterIsInstance<Course>(),
        meetings = items.filterIsInstance<CourseMeeting>(),
    )
}
