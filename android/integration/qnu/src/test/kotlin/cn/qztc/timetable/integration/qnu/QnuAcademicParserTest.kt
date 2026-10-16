package cn.qztc.timetable.integration.qnu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QnuAcademicParserTest {
    @Test fun parsesGradeRowsByOfficialHeaders() {
        val rows = QnuAcademicParser.parseGrades("""
            <table><tr><th>课程号</th><th>课程名</th><th>总评</th><th>绩点</th><th>学分</th><th>考核方式</th><th>选课属性</th></tr>
            <tr><td>CS01</td><td>数据结构</td><td>92</td><td>4.0</td><td>3</td><td>考试</td><td>必修</td></tr></table>
        """)
        assertEquals(1, rows.size); assertEquals("数据结构", rows.single().courseName); assertEquals("3", rows.single().credits)
    }

    @Test fun parsesEvaluationTeacherAndCourseWithoutExposingValuesToLogs() {
        val tasks = QnuAcademicParser.parseEvaluationTasks("""
            <table><tr><th>教师</th><th>课程(课程号)</th><th>评估状态</th><th>操作</th></tr>
            <tr><td>教师甲</td><td>课程乙</td><td>待评价</td><td><a href="/academic/eva/form.jsdo">评价</a></td></tr></table>
        """)
        assertEquals(1, tasks.size); assertEquals("教师甲", tasks.single().teacher); assertTrue(tasks.single().actionPath!!.contains("/academic/eva/"))
    }
}
