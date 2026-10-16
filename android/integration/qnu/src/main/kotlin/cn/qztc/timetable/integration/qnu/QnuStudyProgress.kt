package cn.qztc.timetable.integration.qnu

import org.jsoup.Jsoup

data class QnuCreditCourse(val code: String?, val name: String, val credits: Double?, val status: String?)

data class QnuCreditGroup(val name: String, val required: Double?, val earned: Double?,
    val requiredCount: Int?, val passedCount: Int?, val qualified: Boolean?,
    val courses: List<QnuCreditCourse> = emptyList(),
    val plan: String = "",
    val id: String = "") {
    val progressRatio: Float? get() = when {
        required != null && required > 0 && earned != null -> (earned / required).toFloat().coerceIn(0f, 1f)
        requiredCount != null && requiredCount > 0 && passedCount != null -> (passedCount.toFloat() / requiredCount).coerceIn(0f, 1f)
        qualified == true -> 1f
        else -> null
    }
}

data class QnuStudyProgress(val groups: List<QnuCreditGroup>, val total: QnuCreditGroup?)

object QnuStudyProgressParser {
    fun parse(html: String): QnuStudyProgress {
        val doc = Jsoup.parse(html)
        fun norm(s: String) = s.replace(Regex("\\s+"), "")
        fun rowsOf(t: org.jsoup.nodes.Element) = t.select("tr").filter { it.parents().firstOrNull { p -> p.tagName() == "table" } == t }
        val table = doc.select("table").firstOrNull { t -> rowsOf(t).any { r ->
            val h = r.children().map { norm(it.text()).replace("课组名称", "课组") }; h.containsAll(listOf("课组", "学分要求", "已获学分"))
        } } ?: error("修读进程表头不完整")
        val rows = rowsOf(table)
        val index = rows.indexOfFirst { it.children().map { c -> norm(c.text()).replace("课组名称", "课组") }.containsAll(listOf("课组", "学分要求", "已获学分")) }
        val headers = rows[index].children().map { norm(it.text()).replace("课组名称", "课组") }
        val spans = mutableMapOf<Int, Pair<String, Int>>()
        val planSummaries = mutableSetOf<String>()
        val planTotals = mutableListOf<QnuCreditGroup>()
        val occurrences = mutableMapOf<String, Int>()
        val groups = rows.drop(index + 1).mapNotNull { r ->
            val cells = r.children()
            if (cells.any { it.attr("colspan").toIntOrNull()?.let { n -> n > 1 } == true }) return@mapNotNull null
            val expanded = mutableListOf<String>()
            var cellIndex = 0
            for (column in headers.indices) {
                val span = spans[column]
                if (span != null) {
                    expanded.add(span.first)
                    if (span.second <= 1) spans.remove(column) else spans[column] = span.first to span.second - 1
                } else {
                    val cell = cells.getOrNull(cellIndex++) ?: return@mapNotNull null
                    expanded.add(cell.text().trim())
                    val height = cell.attr("rowspan").toIntOrNull() ?: 1
                    if (height > 1) spans[column] = cell.text().trim() to height - 1
                }
            }
            if (cellIndex != cells.size) return@mapNotNull null
            val values = headers.zip(expanded).toMap()
            values["教学计划"]?.let(planSummaries::add)
            if (values["课组"].isNullOrBlank() && !values["教学计划"].isNullOrBlank()) {
                val required = values["学分要求"]?.toDoubleOrNull()
                val earned = values["已获学分"]?.toDoubleOrNull()
                if (required != null && earned != null) planTotals.add(QnuCreditGroup("总计", required, earned, null, null, null))
                return@mapNotNull null
            }
            val name = values["课组"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val plan = values["教学计划"].orEmpty()
            val identity = "$plan|$name"
            val occurrence = occurrences.getOrDefault(identity, 0)
            occurrences[identity] = occurrence + 1
            val qualified = when (norm(values["是否合格"].orEmpty())) { "是", "合格", "已达标", "通过" -> true; "否", "不合格", "未达标", "未通过" -> false; else -> null }
            QnuCreditGroup(name, values["学分要求"]?.toDoubleOrNull(), values["已获学分"]?.toDoubleOrNull(),
                values["门数要求"]?.toDoubleOrNull()?.toInt(), values["已通过门数"]?.toDoubleOrNull()?.toInt(), qualified,
                plan = plan, id = "$identity|$occurrence")
        }
        require(groups.isNotEmpty()) { "修读进程暂无课组数据" }
        val total = groups.firstOrNull { norm(it.name) in setOf("总计", "合计", "总学分", "总学分要求") }
            ?: planTotals.singleOrNull()
            ?: planSummaries.mapNotNull { text ->
                fun number(label: String) = Regex("$label\\s*[:：]?\\s*(\\d+(?:\\.\\d+)?)").find(text)?.groupValues?.get(1)?.toDoubleOrNull()
                val required = number("学分要求") ?: return@mapNotNull null
                val earned = number("已获学分") ?: return@mapNotNull null
                QnuCreditGroup("总计", required, earned, null, null, null)
            }.singleOrNull()
            ?: readExplicitTotal(doc)
        val courseGroups = readPlanCourses(doc, groups)
        return QnuStudyProgress(groups.filter { it !== total }.map {
            it.copy(courses = courseGroups[it.id].orEmpty())
        }, total)
    }

    private fun readExplicitTotal(doc: org.jsoup.nodes.Document): QnuCreditGroup? {
        val fields = mutableMapOf<String, Double>()
        val labels = mapOf("要求总学分" to "required", "总学分要求" to "required", "毕业要求学分" to "required",
            "已获总学分" to "earned", "已获得总学分" to "earned", "已修总学分" to "earned")
        doc.select("td,th,span,p,div").filter { it.children().isEmpty() || it.tagName() in setOf("td", "th") }.forEach { e ->
            val text = e.text().replace(Regex("\\s+"), "")
            labels.forEach { (label, key) ->
                val inline = Regex("^$label[:：]?(\\d+(?:\\.\\d+)?)(?:学分)?$").matchEntire(text)?.groupValues?.get(1)?.toDoubleOrNull()
                val adjacent = if (text.trimEnd(':', '：') == label) e.nextElementSibling()?.text()?.trim()?.toDoubleOrNull() else null
                (inline ?: adjacent)?.let { fields[key] = it }
            }
        }
        if (fields.isEmpty()) return null
        return QnuCreditGroup("总计", fields["required"], fields["earned"], null, null, null)
    }

    private fun readPlanCourses(doc: org.jsoup.nodes.Document, groups: List<QnuCreditGroup>): Map<String, List<QnuCreditCourse>> {
        fun normalize(s: String) = s.replace(Regex("\\s+|[（(](?:必修|限选|任选)[）)]"), "")
        fun group(s: String) = s.takeIf { value -> groups.any { normalize(it.name) == normalize(value) } }
        fun owner(name: String, plan: String?): QnuCreditGroup? {
            val exact = groups.filter { it.name.replace(Regex("\\s+"), "") == name.replace(Regex("\\s+"), "") }
            val candidates = exact.ifEmpty { groups.filter { normalize(it.name) == normalize(name) } }
            return candidates.filter { plan.isNullOrBlank() || normalize(it.plan) == normalize(plan) }.singleOrNull()
        }
        val result = mutableMapOf<String, MutableList<QnuCreditCourse>>()
        val states = setOf("及格", "不及格", "通过", "未通过", "未选课", "再次选课", "已选课成绩未知", "已选课", "未修读", "修读中", "免修",
            "最高成绩已及格", "最高成绩未及格", "最高成绩不及格再次选课", "最高成绩已及格再次选课")
        fun styleKey(e: org.jsoup.nodes.Element) = e.attr("style").replace(Regex("\\s+"), "").lowercase().takeIf { it.isNotEmpty() }
        val legend = doc.select("[style]").filter { it.children().isEmpty() && it.text().trim() in states }
            .mapNotNull { e -> styleKey(e)?.let { it to e.text().trim() } }.toMap()
        doc.select("[data-qnu-plan] table").forEach { table ->
            val rows = table.select("tr").filter { it.closest("table") == table }
            val index = rows.indexOfFirst { r -> val h = r.children().map { normalize(it.text()) }; "课程号" in h && h.any { it in setOf("课程名", "课程名称") } && "学分" in h }
            if (index < 0) return@forEach
            val headers = rows[index].children().map { normalize(it.text()) }
            var currentGroup = table.selectFirst("caption")?.text()?.let(::group)
                ?: table.previousElementSibling()?.text()?.let(::group)
            var currentPlan = table.closest("[data-qnu-plan]")?.attr("data-qnu-plan-name")?.takeIf { it.isNotBlank() }
            val explicitId = table.closest("[data-qnu-plan]")?.attr("data-qnu-group-id")?.takeIf { it.isNotBlank() }
            val spans = mutableMapOf<Int, Pair<String, Int>>()
            rows.drop(index + 1).forEach row@ { row ->
                val cells = row.children()
                if (cells.any { (it.attr("colspan").toIntOrNull() ?: 1) > 1 }) {
                    group(row.text())?.let { currentGroup = it }; return@row
                }
                val values = mutableListOf<String>()
                var ci = 0
                headers.indices.forEach { col ->
                    val span = spans[col]
                    if (span != null) {
                        values.add(span.first)
                        if (span.second <= 1) spans.remove(col) else spans[col] = span.first to span.second - 1
                    } else {
                        val cell = cells.getOrNull(ci++) ?: return@row
                        values.add(cell.text().trim())
                        val height = cell.attr("rowspan").toIntOrNull() ?: 1
                        if (height > 1) spans[col] = cell.text().trim() to height - 1
                    }
                }
                if (ci != cells.size) return@row
                val fields = headers.zip(values).toMap()
                (fields["课组"] ?: fields["课组名称"])?.let(::group)?.let { currentGroup = it }
                fields["教学计划"]?.takeIf { it.isNotBlank() }?.let { currentPlan = it }
                val name = (fields["课程名称"] ?: fields["课程名"])?.takeIf { it.isNotBlank() } ?: return@row
                // Only attach details when the official group and plan identify one row.
                val matchedGroup = explicitId?.let { id -> groups.singleOrNull { it.id == id } }
                    ?: currentGroup?.let { owner(it, currentPlan) } ?: return@row
                val explicitState = listOfNotNull(fields["修读情况"], fields["修读状态"], fields["课程状态"])
                    .firstOrNull { it.isNotBlank() }
                val state = explicitState ?: row.select("[title],[alt],[style]").firstNotNullOfOrNull { e ->
                    listOf(e.attr("title"), e.attr("alt")).firstOrNull { it in states } ?: styleKey(e)?.let(legend::get)
                }
                result.getOrPut(matchedGroup.id) { mutableListOf() }.add(QnuCreditCourse(fields["课程号"], name, fields["学分"]?.toDoubleOrNull(), state))
            }
        }
        return result.mapValues { (_, courses) -> courses.distinctBy { it.code to it.name } }
    }
}
