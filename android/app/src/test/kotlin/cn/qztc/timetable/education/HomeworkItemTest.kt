package cn.qztc.timetable.education

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class HomeworkItemTest {
    private fun item(status: String = "pending", deadline: String = "2026-10-19 00:00") = HomeworkItem(
        "28881:37374", "28881", "37374", "C#程序设计", "Lesson 05", "教师", status,
        deadline, "2026-10-11 09:00", "", "", "100", "作业内容", emptyList(), "全体学生", "教师批阅",
        "", "", false, "https://eol.qztc.edu.cn/meol/common/homework.html#/homeworkInfo?courseId=28881&hwTaskId=37374", true)

    @Test fun submittedStatusIsPreservedAfterDeadlineAndUnsubmittedBecomesOverdue() {
        val now = LocalDateTime.of(2026, 10, 20, 8, 0)
        assertEquals("已提交", item("submitted").label(now))
        assertEquals("已截止 · 未提交", item().label(now))
        assertEquals("待提交", item().label(now.minusDays(3)))
        assertEquals("状态待确认", item("unknown").label(now))
        assertEquals("已退回 · 请修订", item("returned").label(now))
    }

    @Test fun partialRefreshUpdatesStatusWhileKeepingPreviouslyReadDetails() {
        val previous = item()
        val partial = item("submitted").copy(content = "", fullMark = "", targets = "平台未提供", detailComplete = false)
        val merged = previous.refreshedBy(partial)
        assertEquals("submitted", merged.status)
        assertEquals("作业内容", merged.content)
        assertEquals("100", merged.fullMark)
        assertTrue(merged.detailComplete)
        val complete = item("submitted").copy(content = "更新内容")
        assertEquals("更新内容", previous.refreshedBy(complete).content)
    }

    @Test fun schoolMinutePrecisionDatesAndMissingDateNeverBecomeEpochOrFakeDeadline() {
        assertEquals(LocalDateTime.of(2026, 10, 19, 0, 0), item().deadlineDate)
        assertNull(item(deadline = "另行通知").deadlineDate)
        assertEquals("未开始", item().label(LocalDateTime.of(2026, 10, 10, 8, 0)))
    }

    @Test fun linksCannotLeaveSchoolHostOrUseEmbeddedCredentials() {
        assertTrue(HomeworkPayload.trustedUrl("https://eol.qztc.edu.cn/meol/common/homework.html"))
        listOf("javascript:alert(1)", "http://eol.qztc.edu.cn/", "https://eol.qztc.edu.cn.evil.test/", "https://user@eol.qztc.edu.cn/", "https://eol.qztc.edu.cn:9000/").forEach {
            assertFalse(it, HomeworkPayload.trustedUrl(it))
        }
    }
}
