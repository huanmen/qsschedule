package cn.qztc.timetable.integration.qnu

import org.junit.Assert.*
import org.junit.Test

class QnuGradeFieldsTest {
    @Test fun retainsEveryOfficialColumnIncludingBlankAndAdditionalColumns() {
        val headers = listOf("学年", "学期", "开课院系", "课程号", "课程名", "课序号", "课组", "平时", "期中", "期末", "实验", "口语", "总评", "绩点", "学分", "学时", "考核方式", "选课属性", "备注", "考试性质", "是否缓考", "及格标志", "过程性成绩", "新增字段")
        val values = headers.map { when (it) { "课程名" -> "合成测试课程"; "总评" -> "85"; "备注" -> ""; else -> "合成值" } }
        val html = "<table><tr>" + headers.joinToString("") { "<th>$it</th>" } + "</tr><tr>" + values.joinToString("") { "<td>$it</td>" } + "</tr></table>"
        val record = QnuAcademicParser.parseGrades(html).single()
        assertEquals(headers.zip(values).toMap(), record.officialFields)
        assertEquals("", record.officialFields["备注"])
        assertEquals("85", record.score)
    }
}
