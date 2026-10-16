package cn.qztc.timetable.reminder

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseReminderDetailTest {
    @Test fun todayShowsOnlyClockAndUsefulContext() {
        val date=LocalDate.of(2026,10,16)
        assertEquals("08:20 · 教室",formatCourseReminderDetail(date,LocalTime.of(8,20),"教室",null,null,today=date))
    }
    @Test
    fun `notification detail includes a non blank course note`() {
        val detail = formatCourseReminderDetail(
            date = LocalDate.of(2026, 9, 29),
            startTime = LocalTime.of(8, 20),
            room = "荣茂楼 201",
            teacher = "陈老师",
            note = "带实验报告",
        )

        assertEquals("9月29日 08:20 · 荣茂楼 201 · 陈老师\n备注：带实验报告", detail)
    }

    @Test
    fun `notification detail omits an empty note`() {
        val detail = formatCourseReminderDetail(
            date = LocalDate.of(2026, 9, 29),
            startTime = LocalTime.of(8, 20),
            room = null,
            teacher = null,
            note = "  ",
        )

        assertFalse(detail.contains("备注"))
        assertTrue(detail.startsWith("9月29日 08:20"))
    }

    @Test
    fun `custom reminder message is shown before the course context`() {
        val detail = formatCourseReminderDetail(
            date = LocalDate.of(2026, 9, 29),
            startTime = LocalTime.of(8, 20),
            room = "荣茂楼 201",
            teacher = "陈老师",
            note = null,
            customMessage = "带实验报告",
        )

        assertTrue(detail.startsWith("提醒：带实验报告\n9月29日 08:20"))
    }
}
