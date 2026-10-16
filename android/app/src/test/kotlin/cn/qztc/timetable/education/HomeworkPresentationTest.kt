package cn.qztc.timetable.education

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class HomeworkPresentationTest {
    private val now = LocalDateTime.of(2026, 10, 13, 12, 0)
    @Test fun countdownOmitsUnitsBelowTheirThresholdAndNeverInventsDates() {
        assertEquals("2天3小时4分钟", homeworkRemaining(now.plusDays(2).plusHours(3).plusMinutes(4), now))
        assertEquals("3小时4分钟", homeworkRemaining(now.plusHours(3).plusMinutes(4), now))
        assertEquals("4分钟", homeworkRemaining(now.plusMinutes(4), now))
        assertEquals("1天", homeworkRemaining(now.plusDays(1), now))
        assertEquals("不足1分钟", homeworkRemaining(now.plusSeconds(30), now))
        assertEquals("已截止", homeworkRemaining(now, now))
        assertEquals("已截止", homeworkRemaining(now.minusSeconds(1), now))
        assertEquals("无", homeworkRemaining(null, now))
    }
    @Test fun teacherReviewDoesNotDisplayStudentReviewDates() {
        assertFalse(homeworkHasStudentReview("教师批阅"))
        assertFalse(homeworkHasStudentReview("无"))
        assertTrue(homeworkHasStudentReview("学生互评"))
        assertTrue(homeworkHasStudentReview("自评"))
    }
}
