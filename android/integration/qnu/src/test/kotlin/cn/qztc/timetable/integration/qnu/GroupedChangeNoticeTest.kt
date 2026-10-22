package cn.qztc.timetable.integration.qnu

import cn.qztc.timetable.core.domain.model.*
import cn.qztc.timetable.core.domain.schedule.OccurrenceExpander
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/** Synthetic equivalent of the school's grouped stop/makeup headings. */
internal fun groupedChangeNotice(withSubstitute: Boolean = true, continuation: Boolean = false): String {
    val metadata = listOf("类型", "课程号", "课程名", "课序号", "教师姓名") +
        (if (withSubstitute) listOf("代课人") else emptyList()) + "学时"
    val values = listOf("停<br>课", "TEST001", "测试课程", "1", "测试教师") +
        (if (withSubstitute) listOf("") else emptyList()) + "2.0"
    val original = listOf("10-08", "第 6 周", "周<br>四", "3 - 4节", "测试教室")
    return """<div id="title">2026 秋 学生课表</div>
        <table id="timetable"><tr><th>节次</th><th>周四</th></tr>
        <tr><th>第3节</th><td id="4-3">&lt;&lt;测试课程&gt;&gt;;1<br>测试教室<br>测试教师<br>1-18周<br>讲课学时<br>停课</td></tr>
        <tr><th>第4节</th><td id="4-4">&lt;&lt;测试课程&gt;&gt;;1<br>测试教室<br>测试教师<br>1-18周<br>讲课学时<br>停课</td></tr></table>
        <table><tr>${metadata.joinToString("") { "<td rowspan='2'>$it</td>" }}
        <td colspan="5">停/代课时间地点</td><td colspan="5">补课时间地点</td></tr>
        <tr>${(listOf("日期", "周", "星期", "节次", "教室") * 2).joinToString("") { "<td>$it</td>" }}</tr>
        <tr>${values.joinToString("") { "<td rowspan='${if (continuation) 2 else 1}'>$it</td>" }}
        ${(original + List(5) { "" }).joinToString("") { "<td>$it</td>" }}</tr>
        ${if (continuation) "<tr>" + (listOf("10-15", "7", "周四", "3-4", "测试教室") + List(5) { "" }).joinToString("") { "<td>$it</td>" } + "</tr>" else ""}
        </table>""".trimIndent()
}

private operator fun <T> List<T>.times(count: Int) = List(count) { this }.flatten()

class GroupedChangeNoticeTest {
    @Test fun `a move scopes the original cancellation badge instead of cancelling the whole term`() {
        val page = QnuTimetableParser.parseHtml(groupedChangeNotice())
        val stop = page.changeDetails.single()
        val moved = stop.copy(typeText = "调课", makeup = stop.original!!.copy(week = 7, weekday = 5))
        val result = QnuTimetableImporter.map(page.copy(changeDetails = listOf(moved)), "test")
        assertFalse(result.hasErrors)
        assertFalse(result.diagnostics.any { it.code == "CANCEL_SCOPE_UNCONFIRMED" })
        assertEquals(ExceptionType.MOVE, result.state.exceptions.single().type)
        assertEquals(6, result.state.exceptions.single().original?.week)
        assertEquals(7, result.state.exceptions.single().replacement?.week)
        assertEquals(18, result.state.meetings.single().weekRule.weeks.size)
    }
    @Test fun `grouped td headings and wrapped numbers retain the explicit week`() {
        val detail = QnuTimetableParser.parseHtml(groupedChangeNotice()).changeDetails.single()
        assertEquals("停课", detail.typeText)
        assertEquals(6, detail.original?.week)
        assertEquals(4, detail.original?.weekday)
        assertEquals(3, detail.original?.periodStart)
        assertEquals(4, detail.original?.periodEnd)
        assertNull(detail.makeup)
    }

    @Test fun `omitted substitute column and row spans retain alignment`() {
        val details = QnuTimetableParser.parseHtml(groupedChangeNotice(false, true)).changeDetails
        assertEquals(listOf(6, 7), details.map { it.original?.week })
        assertEquals(listOf(false, true), details.map { it.isContinuation })
        assertTrue(details.all { it.courseCode == "TEST001" && it.hoursText == "2.0" && it.substituteTeacher == null })
    }

    @Test fun `explicit cancellation changes week six only and retains other weeks`() {
        val result = QnuTimetableImporter.importHtml(groupedChangeNotice(), "test")
        assertFalse(result.hasErrors)
        assertFalse(result.diagnostics.any { it.code == "CANCEL_SCOPE_UNCONFIRMED" })
        assertEquals(6, result.state.exceptions.single().original?.week)
        val semester = Semester("test", "测试学期", 2026, Term.AUTUMN, LocalDate.of(2026, 8, 31), 18)
        assertEquals(OccurrenceState.NORMAL, OccurrenceExpander.expand(semester, result.state, 5).single().state)
        assertEquals(OccurrenceState.CANCELLED, OccurrenceExpander.expand(semester, result.state, 6).single().state)
        assertEquals(OccurrenceState.NORMAL, OccurrenceExpander.expand(semester, result.state, 7).single().state)
    }
}
