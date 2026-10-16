package cn.qztc.timetable.reminder

import cn.qztc.timetable.core.domain.model.*
import cn.qztc.timetable.core.domain.schedule.TimetableState
import cn.qztc.timetable.feature.timetable.ImportedSchedule
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class CourseReminderFingerprintTest {
    private val semester = Semester("reminder-test", "合成学期", 2026, Term.AUTUMN, LocalDate.of(2026, 9, 28), 20)
    private val course = Course("reminder-course", semester.id, SourceType.QNU_EDU, "synthetic", name = "合成提醒课程")
    private val meeting = CourseMeeting("reminder-meeting", course.id, 1, 1, 2, WeekRule.of(listOf(1)), "测试教师", "测试教室", SourceType.QNU_EDU)
    private val schedule = ImportedSchedule(semester, PeriodSchemeFactory.forSemester(semester.id).periods, TimetableState(listOf(course), listOf(meeting)))
    private val now = LocalDateTime.of(2026, 9, 28, 6, 0)

    private fun fingerprint(mode: ReminderDisplayMode = ReminderDisplayMode.SCREEN_POPUP, messages: Map<String, String> = emptyMap(), overrides: Map<String, Int> = emptyMap()) =
        CourseReminderScheduler.fingerprint(schedule, true, 15, mode, overrides, messages, now)

    @Test fun switchingDisplayModeInvalidatesScheduledJobs() {
        assertNotEquals(fingerprint(), fingerprint(ReminderDisplayMode.NOTIFICATION_BAR))
    }

    @Test fun changingCustomTextInvalidatesScheduledJobs() {
        assertNotEquals(fingerprint(), fingerprint(messages = mapOf(course.id to "带实验报告")))
        assertTrue(fingerprint(messages = mapOf(course.id to "带实验报告")).contains("测试教室"))
    }

    @Test fun disablingOneCourseRemovesItsJob() {
        val disabled = fingerprint(overrides = mapOf(course.id to 0))
        assertFalse(disabled.contains(course.name))
        assertTrue(fingerprint().contains(course.name))
    }

    @Test fun disabledAndMissingScheduleAreSafe() {
        assertEquals("disabled", CourseReminderScheduler.fingerprint(schedule, false, 15, ReminderDisplayMode.SCREEN_POPUP, emptyMap(), emptyMap(), now))
        assertEquals("no-schedule", CourseReminderScheduler.fingerprint(null, true, 15, ReminderDisplayMode.SCREEN_POPUP, emptyMap(), emptyMap(), now))
    }
    @Test fun holidayOptInRestoresOriginalCourseButKeepsMakeupTeaching() {
        val holiday=cn.qztc.timetable.core.domain.calendar.AcademicDay("holiday",semester.id,now.toLocalDate(),
            cn.qztc.timetable.core.domain.calendar.AcademicDayKind.HOLIDAY,"放假")
        val marked=schedule.copy(academicDays=listOf(holiday))
        fun value(on:Boolean)=CourseReminderScheduler.fingerprint(marked,true,15,ReminderDisplayMode.SCREEN_POPUP,emptyMap(),emptyMap(),now,on)
        assertFalse(value(false).contains(course.name))
        assertTrue(value(true).contains(course.name))
        val makeup=holiday.copy(kind=cn.qztc.timetable.core.domain.calendar.AcademicDayKind.MAKEUP,sourceWeek=1,sourceWeekday=1)
        val adjusted=schedule.copy(academicDays=listOf(makeup))
        assertTrue(CourseReminderScheduler.fingerprint(adjusted,true,15,ReminderDisplayMode.SCREEN_POPUP,emptyMap(),emptyMap(),now,false).contains(course.name))
    }
}
