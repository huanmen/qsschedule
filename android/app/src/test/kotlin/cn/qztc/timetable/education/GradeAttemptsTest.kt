package cn.qztc.timetable.education

import cn.qztc.timetable.integration.qnu.QnuGradeRecord
import org.junit.Test
import org.junit.Assert.*

class GradeAttemptsTest {
    private fun record(score: String, exam: String, fields: Map<String, String>) = QnuGradeRecord("C1", "合成课程", score, "2", "3", "考试", "必修", if (score.toInt() < 60) "不及格" else "及格", examType = exam, officialFields = fields)
    @Test fun usesRetakeTotalButKeepsNormalDetails() {
        val normal = record("52", "正常考试", mapOf("平时" to "80", "期末" to "40", "总评" to "52"))
        val retake = record("68", "补考", mapOf("总评" to "68"))
        val merged = mergeAttempts(listOf(normal, retake))
        assertEquals("68", merged.score)
        assertEquals("及格", merged.status)
        assertEquals("80", merged.officialFields["平时"])
        assertEquals("40", merged.officialFields["期末"])
        assertEquals("52", merged.officialFields["正常考试"])
    }
    @Test fun latestRetakeWins() {
        val result = mergeAttempts(listOf(record("30", "补考", emptyMap()), record("75", "补考", emptyMap())))
        assertEquals("75", result.score)
    }
    @Test fun makeupKeepsOriginalFailedGpaIncludingNonzeroValue() {
        val normal = record("52", "正常考试", mapOf("绩点" to "0.6")).copy(gpa = "0.6")
        val makeup = record("68", "补考", mapOf("绩点" to "0.0")).copy(gpa = "0.0")
        val merged = mergeAttempts(listOf(normal, makeup))
        assertEquals("0.6", merged.gpa)
        assertEquals("0.6", merged.officialFields["绩点"])
    }
    @Test fun repeatingCourseKeepsNewGpa() {
        val normal = record("52", "正常考试", emptyMap()).copy(gpa = "0.6")
        val repeat = record("83", "重修", emptyMap()).copy(gpa = "3.3")
        assertEquals("3.3", mergeAttempts(listOf(normal, repeat)).gpa)
    }
    @Test fun makeupInNewSemesterUsesEarlierOriginalButNotUnrelatedOrFutureCourse() {
        val original = record("52", "正常考试", emptyMap()).copy(year = "2025", term = "秋", gpa = "0.6")
        val unrelated = original.copy(courseCode = "C2", gpa = "0.8")
        val future = original.copy(year = "2027", gpa = "0.9")
        val makeup = record("68", "补考", emptyMap()).copy(year = "2026", term = "春", gpa = "0.0")
        assertEquals("0.6", inheritMakeupGpa(makeup, listOf(original, unrelated, future)).gpa)
    }
}
