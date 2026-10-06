package cn.qztc.timetable.integration.qnu

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Parses only public column labels and row values from sanitized academic tables. */
object QnuAcademicParser {
    fun parseGrades(html: String): List<QnuGradeRecord> {
        val doc = Jsoup.parse(html)
        return doc.select("table").firstNotNullOfOrNull { table ->
            val rows = table.select("tr").filter { it.closest("table") === table }
            val headerRow = rows.indexOfFirst { row ->
                val labels = row.children().filter { it.tagName() in setOf("td", "th") }.map(::norm)
                labels.any { it in setOf("课程名", "课程名称") } &&
                    labels.any { it in setOf("总评", "成绩", "总评成绩", "最终成绩") }
            }
            if (headerRow < 0) return@firstNotNullOfOrNull null
            val header = rows[headerRow].children().map(::norm)
            val indexes = header.mapIndexedNotNull { i, value ->
                when {
                    value in setOf("课程号", "课程代码") -> "code" to i
                    value in setOf("课程名", "课程名称") -> "name" to i
                    value in setOf("总评", "成绩", "总评成绩", "最终成绩") -> "score" to i
                    value == "绩点" -> "gpa" to i
                    value == "学分" -> "credits" to i
                    value == "考核方式" -> "assessment" to i
                    value == "考试性质" -> "examType" to i
                    value in setOf("学年", "学年度") -> "year" to i
                    value == "学期" -> "term" to i
                    value == "课序号" -> "sequence" to i
                    value in setOf("选课属性", "课程性质") -> "selection" to i
                    value in setOf("状态", "是否通过", "及格标志") -> "status" to i
                    else -> null
                }
            }.toMap()
            if ("name" !in indexes || "score" !in indexes) return@firstNotNullOfOrNull null
            rows.drop(headerRow + 1).mapNotNull { row ->
                val elements = row.children().filter { it.tagName() in setOf("td", "th") }
                if (elements.size != header.size || elements.any { it.hasAttr("colspan") && it.attr("colspan") != "1" }) return@mapNotNull null
                val cells = elements.map { it.text().replace(Regex("\\s+"), " ").trim() }
                val name = cells.getOrNull(indexes.getValue("name")).orEmpty()
                if (name.isBlank()) null else QnuGradeRecord(
                    indexes.value("code", cells), name, indexes.value("score", cells),
                    indexes.value("gpa", cells), indexes.value("credits", cells),
                    indexes.value("assessment", cells), indexes.value("selection", cells),
                    indexes.value("status", cells),
                    indexes.value("year", cells), indexes.value("term", cells),
                    indexes.value("examType", cells), indexes.value("sequence", cells),
                    header.mapIndexed { i, label -> label to cells[i] }.filter { it.first.isNotBlank() }.toMap(),
                )
            }.filterNot { it.courseName in setOf("课程名", "课程名称") }
        }.orEmpty()
    }

    fun parseEvaluationTasks(html: String): List<QnuEvaluationTask> {
        val doc = Jsoup.parse(html)
        return doc.select("table").flatMap { table ->
            table.select("tr").drop(1).mapNotNull { row ->
                val cells = row.select("th,td").map(::norm)
                if (cells.size < 2) return@mapNotNull null
                val links = row.select("a[href]")
                val teacher = cells.firstOrNull { it.length in 2..40 && !it.matches(Regex("^\\d+$")) }.orEmpty()
                val course = cells.dropWhile { it != teacher }.drop(1).firstOrNull().orEmpty()
                if (teacher.isBlank() || course.isBlank()) null else QnuEvaluationTask(teacher, course, cells.lastOrNull(), links.firstOrNull()?.attr("href"))
            }
        }
    }

    private fun norm(value: Element): String = value.text().replace(Regex("\\s+"), "").trim()
    private fun Map<String, Int>.value(key: String, cells: List<String>): String? = get(key)?.let { cells.getOrNull(it) }?.takeIf(String::isNotBlank)
}
