package cn.qztc.timetable.integration.qnu

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class QnuGradeHistoryTest {
    private fun grade(year: String, term: String, name: String = "合成课程") =
        QnuGradeRecord("TEST01", name, "85", "3.5", "2", "考试", "必修", "通过", year, term)

    @Test fun preservesAttemptsAndIndependentOfficialColumns() {
        val rows = QnuAcademicParser.parseGrades("""
            <table><tr><td>课程名</td><td>学分</td></tr><tr><td>仅课表</td><td>2</td></tr></table>
            <table><tr><td>布局</td><td><table>
              <tr><td colspan="12">合成成绩</td></tr>
              <tr><th>学年</th><th>学期</th><th>课程号</th><th>课程名</th><th>总评</th><th>绩点</th><th>学分绩点</th><th>学分</th><th>考核方式</th><th>考试性质</th><th>及格标志</th><th>课序号</th></tr>
              <tr><td>2025</td><td>秋</td><td>TEST01</td><td>Academic English</td><td>55</td><td>0</td><td>0</td><td>2</td><td>考试</td><td>正常考试</td><td>不及格</td><td>01</td></tr>
              <tr><td>2025</td><td>秋</td><td>TEST01</td><td>Academic English</td><td>及格</td><td>1</td><td>2</td><td>2</td><td>考试</td><td>补考</td><td>及格</td><td>01</td></tr>
              <tr><td colspan="12">总计</td></tr>
            </table></td></tr></table>
        """)
        assertEquals(2, rows.size)
        assertEquals("Academic English", rows.first().courseName)
        assertEquals("2025", rows.first().year)
        assertEquals("秋", rows.first().term)
        assertEquals("补考", rows.last().examType)
        assertEquals("1", rows.last().gpa)
        assertEquals("及格", rows.last().status)
    }

    @Test fun missingScoreIsNotZeroAndTimetableIsNotGrades() {
        assertTrue(QnuAcademicParser.parseGrades("<table><tr><th>课程名</th><th>学分</th></tr><tr><td>合成课程</td><td>3</td></tr></table>").isEmpty())
        val row = QnuAcademicParser.parseGrades("<table><tr><th>课程名</th><th>总评</th></tr><tr><td>合成课程</td><td></td></tr></table>").single()
        assertNull(row.score)
    }

    @Test fun currentGradesDoNotExpandInsteadOfLastAttendedSemester() {
        val groups = QnuGradeSemesters.group(listOf(grade("2026", "秋"), grade("2025", "秋"), grade("2026", "春")))
        assertEquals(listOf("2026/秋", "2026/春", "2025/秋"), groups.map { it.id })
        assertEquals("2026/春", QnuGradeSemesters.defaultExpanded(groups, LocalDate.of(2026, 10, 6)))
    }

    @Test fun summerIsUsedOnlyWhenActuallyAttended() {
        val groups = QnuGradeSemesters.group(listOf(grade("2026", "夏"), grade("2026", "春")))
        assertEquals("2026/夏", QnuGradeSemesters.defaultExpanded(groups, LocalDate.of(2026, 10, 6)))
    }

    @Test fun januaryAndSpringChooseTheLastEarlierSemester() {
        val groups = QnuGradeSemesters.group(listOf(grade("2025", "秋"), grade("2025", "春"), grade("2026", "春")))
        assertEquals("2025/春", QnuGradeSemesters.defaultExpanded(groups, LocalDate.of(2026, 1, 10)))
        assertEquals("2025/秋", QnuGradeSemesters.defaultExpanded(groups, LocalDate.of(2026, 3, 10)))
    }

    @Test fun firstSemesterFallsBackAndEmptyHistoryStaysEmpty() {
        assertNull(QnuGradeSemesters.defaultExpanded(emptyList()))
        assertEquals("2026/秋", QnuGradeSemesters.defaultExpanded(QnuGradeSemesters.group(listOf(grade("2026", "秋"))), LocalDate.of(2026, 10, 6)))
        assertNull(QnuGradeSemesters.order("2025–2026", "1"))
    }
}
