package cn.qztc.timetable.qnu

import cn.qztc.timetable.integration.qnu.QnuTimetableImporter
import cn.qztc.timetable.core.domain.model.*
import cn.qztc.timetable.core.domain.schedule.OccurrenceExpander
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class QnuNoticeMergeTest {
    @Test fun movedClassNoticeResolvesPersonalCancellationWarningWithoutChangingRegularWeeks() {
        val personal = QnuTimetableImporter.importHtml("""<div id="title">2026 春 学生课表</div>
            <table id="timetable"><tr><th>节次</th><th>周四</th></tr>
            <tr><th>第3节</th><td id="4-3">&lt;&lt;测试课程&gt;&gt;;01<br>教室<br>教师<br>1-16周<br>讲课学时<br>停课</td></tr></table>""", "test")
        val target = personal.state.courses.single()
        val slot = SlotRef(8, 4, 3, 4)
        val classTable = personal.copy(state = personal.state.copy(courses = listOf(target.copy(sequence = "1")),
            exceptions = listOf(ScheduleException("move", target.id, ExceptionType.MOVE, slot, slot.copy(week = 9), sourceType = SourceType.QNU_EDU))))
        val result = mergeClassNotices(personal, classTable)
        assertFalse(result.diagnostics.any { it.code == "CANCEL_SCOPE_UNCONFIRMED" })
        assertEquals(ExceptionType.MOVE, result.state.exceptions.single().type)
        assertEquals(personal.state.meetings, result.state.meetings)
    }
    @Test fun classOnlyNoticeMatchesPersonalCourseAndDoesNotImportOtherStudentsCourses() {
        val personal = QnuTimetableImporter.importHtml("""<div id="title">2026 秋 学生课表</div>
            <table id="timetable"><tr><th>节次</th><th>周四</th></tr>
            <tr><th>第3节</th><td id="4-3">&lt;&lt;测试课程&gt;&gt;;01<br>教室<br>教师<br>1-18周<br>讲课学时<br>停课</td></tr></table>""", "test")
        fun notice(code: String, name: String) = "<tr>" + listOf("停课", code, name, "1", "教师", "", "2.0", "10-08", "6", "周四", "3-4", "教室", "", "", "", "", "").joinToString("") { "<td>$it</td>" } + "</tr>"
        val classTable = QnuTimetableImporter.importChangesHtml("""<div id="title">2026 秋 学生课表</div><table id="timetable"></table>
            <table>${notice("TEST01", "测试课程")}${notice("OTHER01", "其他同学课程")}</table>""", "test")
        val result = mergeClassNotices(personal, classTable)
        assertEquals(personal.state.courses, result.state.courses)
        assertEquals(personal.state.meetings, result.state.meetings)
        assertEquals(1, result.state.exceptions.size)
        assertEquals(personal.state.courses.single().id, result.state.exceptions.single().courseId)
        assertFalse(result.diagnostics.any { it.code == "CANCEL_SCOPE_UNCONFIRMED" })
        val semester = Semester("test", "测试学期", 2026, Term.AUTUMN, LocalDate.of(2026, 8, 31), 18)
        assertEquals(OccurrenceState.CANCELLED, OccurrenceExpander.expand(semester, result.state, 6).single().state)
        assertEquals(OccurrenceState.NORMAL, OccurrenceExpander.expand(semester, result.state, 7).single().state)
    }
}
