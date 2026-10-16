package cn.qztc.timetable.core.domain.schedule

import cn.qztc.timetable.core.domain.model.Course
import cn.qztc.timetable.core.domain.model.SourceType
import org.junit.Assert.*
import org.junit.Test

class ScheduleCourseNamesTest {
    private val course = Course("qnu:qnu-2026-autumn:数据挖掘#1", "qnu-2026-autumn", SourceType.QNU_EDU,
        "qnu:qnu-2026-autumn:数据挖掘#1", code = "0701417", sequence = "1", name = "数据挖掘")

    @Test fun legacyNamedIdResolvesNoticeCodeAndNormalizedSequence() {
        assertEquals(course, ScheduleCourseNames.findCourse("qnu:qnu-2026-autumn:0701417#1", listOf(course)))
        assertEquals(course, ScheduleCourseNames.findCourse("qnu:temporary:0701417#01", listOf(course)))
        assertNull(ScheduleCourseNames.findCourse("qnu:temporary:0701417#2", listOf(course)))
    }
    @Test fun ambiguousCodeCannotBorrowAnotherCourseName() {
        assertNull(ScheduleCourseNames.findCourse("qnu:temporary:0701417#1", listOf(course, course.copy(id = "other"))))
        assertEquals("大学生职业生涯发展与规划", ScheduleCourseNames.fullest("大学生职业…", "大学生职业生涯发展与规划"))
    }
}
