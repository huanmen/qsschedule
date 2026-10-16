package cn.qztc.timetable.education

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class HomeworkReminderTest {
    private val now = LocalDateTime.of(2026, 10, 12, 9, 0)
    private val item = HomeworkItem("1:2", "1", "2", "课程", "作业", "教师", "pending", "2026-10-19 00:00", "", "", "", "", "", emptyList(), "", "", "", "", null,
        "https://eol.qztc.edu.cn/meol/common/homework.html", true)
    @Test fun threeModesCoexistAndDailyMovesToTomorrowAfterItsTime() {
        val options = HomeworkReminderOptions(once = now.plusHours(2), daily = LocalTime.of(8, 0), beforeMinutes = 90)
        val rows = homeworkReminderTimes(options, item, now).toMap()
        assertEquals(now.plusHours(2), rows["once"])
        assertEquals(LocalDateTime.of(2026, 10, 13, 8, 0), rows["daily"])
        assertEquals(LocalDateTime.of(2026, 10, 18, 22, 30), rows["before"])
    }
    @Test fun submittedArchivedUnknownOverdueOrDisabledNeverNotify() {
        val options = HomeworkReminderOptions(daily = LocalTime.NOON)
        assertTrue(homeworkReminderTimes(options, item.copy(status = "submitted"), now).isEmpty())
        assertTrue(homeworkReminderTimes(options, item.copy(status = "unknown"), now).isEmpty())
        assertTrue(homeworkReminderTimes(options, item.copy(archiveTerm = "1"), now).isEmpty())
        assertTrue(homeworkReminderTimes(options, item, now.plusDays(10)).isEmpty())
        assertTrue(homeworkReminderTimes(options.copy(enabled = false), item, now).isEmpty())
        assertFalse(homeworkReminderTimes(options, item.copy(status = "returned"), now).isEmpty())
    }
    @Test fun remindersAfterDeadlineAndElapsedOneOffsAreExcluded() {
        val late = item.deadlineDate!!.plusHours(2)
        assertTrue(homeworkReminderTimes(HomeworkReminderOptions(once = late, daily = late.toLocalTime(), beforeMinutes = null), item, item.deadlineDate!!.minusMinutes(1)).isEmpty())
        assertTrue(homeworkReminderTimes(HomeworkReminderOptions(once = now.minusMinutes(1), beforeMinutes = null), item, now).isEmpty())
        assertTrue(homeworkReminderTimes(HomeworkReminderOptions(), item.copy(deadline = "待公布"), now).isEmpty())
    }
}
