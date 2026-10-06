package cn.qztc.timetable.integration.qnu

import cn.qztc.timetable.core.domain.imports.ImportDiagnostic
import java.io.ByteArrayInputStream
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * 解析泉师学生课表页面。入口接收原始字节，交给 Jsoup 依据页面 meta 声明识别编码
 * （泉师页面为 GBK），避免在正确解码前把字节转成错误字符串。
 */
object QnuTimetableParser {

    private val courseStart = Regex("""^<<(.+)>>;?(\S*)$""")
    private val cellId = Regex("""^(\d)[-_](\d{1,2})$""")
    private val weekdayAttribute = Regex("""(?:weekday|week-day|day)[-_]?(\d{1,2})""", RegexOption.IGNORE_CASE)
    private val periodAttribute = Regex("""(?:period|section|row)[-_]?(\d{1,2})""", RegexOption.IGNORE_CASE)
    private val coordinateAttributeNames = setOf(
        "data-weekday",
        "data-day",
        "data-period",
        "data-section",
        "data-row",
    )
    private val weekdayNames = mapOf(
        "周一" to 1, "周二" to 2, "周三" to 3, "周四" to 4,
        "周五" to 5, "周六" to 6, "周日" to 7, "周天" to 7,
    )
    private val periodRange = Regex("""^(\d{1,2})(?:-(\d{1,2}))?$""")

    fun parse(bytes: ByteArray): QnuParsedPage {
        if (bytes.isEmpty()) {
            return QnuParsedPage(diagnostics = listOf(ImportDiagnostic.error("EMPTY_INPUT", "页面内容为空")))
        }

        val document: Document = try {
            // charsetName 传 null，让 Jsoup 从 meta 嗅探编码（GBK）。
            Jsoup.parse(ByteArrayInputStream(bytes), null, "")
        } catch (e: Exception) {
            return QnuParsedPage(
                diagnostics = listOf(ImportDiagnostic.error("DECODE_FAILED", "页面解码失败: ${e.message}")),
            )
        }
        return parseDocument(document)
    }

    /**
     * WebView 已经把页面解码成 Unicode。不要再按 GBK meta 把 UTF-8 字符串当字节重解。
     */
    fun parseHtml(html: String): QnuParsedPage {
        if (html.isBlank()) {
            return QnuParsedPage(diagnostics = listOf(ImportDiagnostic.error("EMPTY_INPUT", "页面内容为空")))
        }
        val document: Document = try {
            Jsoup.parse(html)
        } catch (e: Exception) {
            return QnuParsedPage(
                diagnostics = listOf(ImportDiagnostic.error("DECODE_FAILED", "页面解码失败: ${e.message}")),
            )
        }
        return parseDocument(document)
    }

    fun matchCourseProfile(
        name: String,
        sequence: String?,
        profiles: List<QnuCourseProfile>,
        courseCode: String? = null,
    ): QnuCourseProfile? {
        if (profiles.isEmpty()) return null
        val compact = compactCourseName(name)
        val effectiveSequence = sequence ?: embeddedSequence(name)
        val byCode = profiles.filter {
            !courseCode.isNullOrBlank() && normalizeCode(it.courseCode) == normalizeCode(courseCode)
        }
        val exactName = profiles.filter { compactCourseName(it.courseName) == compact }
        // A few versions of the academic system append the teaching-class
        // suffix to the timetable name (for example "-2-1") but keep it out
        // of the arrangement table. Use that relation only after exact name
        // matching, so two real courses with similar names remain ambiguous.
        val fuzzyName = profiles.filter {
            val profileName = compactCourseName(it.courseName)
            profileName.length >= 4 && (compact.startsWith(profileName) || profileName.startsWith(compact))
        }
        val candidates = byCode.ifEmpty { exactName.ifEmpty { fuzzyName } }
        if (candidates.isEmpty()) return null
        fun seq(value: String?) = value.orEmpty().trim().trimStart('0').ifEmpty { "0" }
        val exact = candidates.filter { !effectiveSequence.isNullOrBlank() && seq(it.sequence) == seq(effectiveSequence) }
        val eligible = exact.ifEmpty {
            when {
                effectiveSequence.isNullOrBlank() && candidates.size == 1 -> candidates
                effectiveSequence.isNullOrBlank() -> emptyList()
                else -> candidates.filter { it.sequence.isNullOrBlank() }
            }
        }.distinct()
        // Repeated rows can split metadata across the same course. Merge only
        // when the name/code/sequence selection is already unambiguous.
        return if (eligible.size == 1 || exact.isNotEmpty()) mergeProfiles(eligible) else null
    }

    internal fun compactCourseName(name: String): String =
        name.replace("\\s".toRegex(), "")
            .replace("　", "")
            .replace('（','(').replace('）',')')
            .replace(Regex("\\([一二三四五六七八九十0-9]+班\\)$"), "")
            .replace(Regex("[-_]\\d+(?:[-_]\\d+)+$"), "")

    private fun normalizeCode(value: String?): String = value.orEmpty().replace(Regex("[\\s\\u00a0　]"), "")

    private fun embeddedSequence(name: String): String? =
        Regex("[-_]([0-9]+(?:[-_][0-9]+)+)$").find(name)?.groupValues?.get(1)?.replace('_', '-')

    private fun mergeProfiles(profiles: List<QnuCourseProfile>): QnuCourseProfile? {
        val first = profiles.firstOrNull() ?: return null
        fun firstText(value: (QnuCourseProfile) -> String?): String? =
            profiles.asSequence().map(value).mapNotNull { profileValue(it) }.firstOrNull()
        return first.copy(
            courseName = firstText { it.courseName } ?: first.courseName,
            sequence = firstText { it.sequence },
            courseCode = firstText { it.courseCode },
            nature = firstText { it.nature },
            credits = firstText { it.credits },
            selectionAttribute = firstText { it.selectionAttribute },
            assessmentMethod = firstText { it.assessmentMethod },
        )
    }

    private fun profileValue(value: String?): String? = value?.trim()?.takeIf {
        it.isNotEmpty() && it !in setOf("-", "--", "—", "/", "null", "暂无", "无")
    }

    fun parseCourseProfilesHtml(html: String): List<QnuCourseProfile> =
        parseCourseProfiles(Jsoup.parse(html), null)

    private fun parseDocument(document: Document): QnuParsedPage {
        val diagnostics = mutableListOf<ImportDiagnostic>()
        val charset = document.charset()?.name()

        val grid = findTimetableGrid(document)
        val courseProfiles = parseCourseProfiles(document, grid)
        if (grid == null) {
            diagnostics += ImportDiagnostic.error("NO_TIMETABLE", "页面缺少可识别的课表主网格")
            return QnuParsedPage(
                charset = charset,
                courseProfiles = courseProfiles,
                diagnostics = diagnostics,
            )
        }

        val (yearText, termText) = parseTitle(document, diagnostics)
        val className = parseClassName(document)
        val periods = mutableListOf<QnuPeriodHeader>()
        val gridEntries = mutableListOf<QnuGridEntry>()
        parseGrid(grid, periods, gridEntries, diagnostics)
        val changeDetails = parseChangeDetails(document, grid, diagnostics)

        if (gridEntries.isEmpty()) {
            diagnostics += ImportDiagnostic.error(
                "EMPTY_GRID",
                "主网格中未找到任何课程记录；为避免误清空现有课表，本次导入已拒绝",
            )
        }

        return QnuParsedPage(
            charset = charset,
            yearText = yearText,
            termText = termText,
            className = className,
            periods = periods,
            gridEntries = gridEntries,
            changeDetails = changeDetails,
            courseProfiles = courseProfiles,
            diagnostics = diagnostics,
        )
    }

    private fun findTimetableGrid(document: Document): Element? =
        document.selectFirst("table#timetable")
            ?: document.select("table").firstOrNull(::isTimetableLike)

    private fun isTimetableLike(table: Element): Boolean {
        val cells = table.select("td")
        val hasSemanticCoordinates = cells.any { cell ->
            cellId.matches(cell.id()) ||
                cell.attributes().asList().any { attribute ->
                    attribute.key.lowercase() in coordinateAttributeNames ||
                        weekdayAttribute.matches(attribute.key) || periodAttribute.matches(attribute.key)
                } ||
                cell.classNames().any { className ->
                    weekdayAttribute.matches(className) || periodAttribute.matches(className)
                }
        }
        val hasWeekdayHeaderAndCourse = table.select("tr").any(::isWeekdayHeader) &&
            cells.any { cell -> cellLines(cell).firstOrNull()?.let(courseStart::matches) == true }
        return hasSemanticCoordinates || hasWeekdayHeaderAndCourse
    }

    private fun parseTitle(document: Document, diagnostics: MutableList<ImportDiagnostic>): Pair<String?, String?> {
        val titleText = document.selectFirst("#title, [id*=title i]")?.text()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: document.title().trim().takeIf { it.isNotEmpty() }
        if (titleText.isNullOrEmpty()) {
            diagnostics += ImportDiagnostic.warning("NO_TITLE", "页面缺少可识别的学期标题")
            return null to null
        }
        val year = Regex("""(\d{4})""").find(titleText)?.groupValues?.get(1)
        val term = Regex("""[春夏秋]""").find(titleText)?.value
            ?: when {
                titleText.contains("第一学期") || titleText.contains("第1学期") -> "秋"
                titleText.contains("第二学期") || titleText.contains("第2学期") -> "春"
                else -> null
            }
        if (year == null || term == null) {
            diagnostics += ImportDiagnostic.warning("TITLE_UNRECOGNIZED", "无法从标题识别学年学期: $titleText")
        }
        return year to term
    }

    private fun parseClassName(document: Document): String? =
        document.select("td").firstOrNull { it.ownText().contains("班级：") }
            ?.ownText()?.substringAfter("班级：")?.trim()?.ifEmpty { null }

    private fun parseGrid(
        grid: Element,
        periods: MutableList<QnuPeriodHeader>,
        entries: MutableList<QnuGridEntry>,
        diagnostics: MutableList<ImportDiagnostic>,
    ) {
        val rows = grid.select("tr")
        for ((rowIndex, row) in rows.withIndex()) {
            if (rowIndex == 0 && isWeekdayHeader(row)) continue
            row.selectFirst("th")?.let { th ->
                val lines = cellLines(th)
                periods += QnuPeriodHeader(
                    rowIndex = rowIndex,
                    label = lines.getOrNull(0) ?: "",
                    startText = lines.getOrNull(1),
                    endText = lines.getOrNull(3) ?: lines.getOrNull(2)?.takeIf { it != "┆" },
                )
            }
            for ((cellIndex, cell) in row.select("td").withIndex()) {
                parseCell(
                    cell = cell,
                    fallbackWeekday = cellIndex + 1,
                    fallbackPeriodRow = rowIndex,
                    entries = entries,
                    diagnostics = diagnostics,
                )
            }
        }
    }

    private fun parseCell(
        cell: Element,
        fallbackWeekday: Int,
        fallbackPeriodRow: Int,
        entries: MutableList<QnuGridEntry>,
        diagnostics: MutableList<ImportDiagnostic>,
    ) {
        val id = cell.id()
        val coordinates = cellCoordinates(cell, fallbackWeekday, fallbackPeriodRow)
        if (coordinates == null) {
            if (cellLines(cell).isNotEmpty()) diagnostics += ImportDiagnostic.warning(
                "CELL_COORDINATES_UNRECOGNIZED",
                "无法识别的课程格坐标: '$id'",
            )
            return
        }
        val (weekday, periodRow) = coordinates
        val lines = cellLines(cell)
        if (lines.isEmpty()) return

        var index = 0
        while (index < lines.size) {
            val start = courseStart.matchEntire(lines[index])
            if (start == null) {
                diagnostics += ImportDiagnostic.warning(
                    "CELL_LINE_UNRECOGNIZED",
                    "课程格 $id 中无法归属的文本: '${lines[index]}'",
                )
                index++
                continue
            }
            val blockEnd = (index + 1 until lines.size).firstOrNull { courseStart.matchEntire(lines[it]) != null }
                ?: lines.size
            val block = lines.subList(index, blockEnd)
            if (block.size < 5) {
                diagnostics += ImportDiagnostic.warning(
                    "CELL_BLOCK_INCOMPLETE",
                    "课程格 $id 记录不足 5 段: ${block.joinToString(" | ")}",
                )
            } else {
                entries += QnuGridEntry(
                    weekday = weekday,
                    periodRow = periodRow,
                    courseName = start.groupValues[1].trim(),
                    sequence = start.groupValues[2].trim().ifEmpty { null },
                    room = block[1].ifBlank { null },
                    teacher = block[2].ifBlank { null },
                    weekText = block[3],
                    typeText = block[4],
                    extraStatuses = block.drop(5),
                )
            }
            index = blockEnd
        }
    }

    private fun parseChangeDetails(
        document: Document,
        timetableGrid: Element,
        diagnostics: MutableList<ImportDiagnostic>,
    ): List<QnuChangeDetail> {
        val detailRows: (Element) -> Boolean = { table ->
            table.id() != "timetable" && table.select("tr").any { row -> row.select("> td").size >= 10 }
        }
        val tables = document.select("table").filter { it != timetableGrid && detailRows(it) &&
            (it.text().contains("停课") || it.text().contains("调课") || it.text().contains("补课") || it.text().contains("代课")) }
        if(tables.isEmpty())return emptyList()
        val details = mutableListOf<QnuChangeDetail>()
        for(table in tables) {
        var context: QnuChangeDetail? = null
        for (row in table.select("tr").filter { it.closest("table")===table }) {
            if (row.select("th").isNotEmpty()) continue // 两行表头
            val cells = row.select("> td")
            val texts = cells.map { cleanText(it.text()) }
            when {
                texts.size >= 17 -> {
                    val detail = QnuChangeDetail(
                        typeText = texts[0].orEmpty(),
                        courseCode = texts[1],
                        courseName = texts[2],
                        sequence = texts[3],
                        teacher = texts[4],
                        substituteTeacher = texts[5],
                        hoursText = texts[6],
                        original = timePlace(texts.subList(7, 12)),
                        makeup = timePlace(texts.subList(12, 17)),
                        isContinuation = false,
                    )
                    details += detail
                    context = detail
                }
                texts.size == 10 -> {
                    val parent = context
                    if (parent == null) {
                        diagnostics += ImportDiagnostic.warning(
                            "DETAIL_ORPHAN_ROW",
                            "调停补明细出现无主续行: ${texts.joinToString(" | ")}",
                        )
                    } else {
                        details += parent.copy(
                            original = timePlace(texts.subList(0, 5)),
                            makeup = timePlace(texts.subList(5, 10)),
                            isContinuation = true,
                        )
                    }
                }
                else -> diagnostics += ImportDiagnostic.warning(
                    "DETAIL_ROW_UNRECOGNIZED",
                    "调停补明细行有 ${texts.size} 列，无法识别: ${texts.joinToString(" | ")}",
                )
            }
        }
        }
        return details.distinct()
    }

    private fun parseCourseProfiles(document: Document, timetableGrid: Element?): List<QnuCourseProfile> {
        val profiles = mutableListOf<QnuCourseProfile>()
        for (table in document.select("table")) {
            if (timetableGrid != null && table === timetableGrid) continue
            val rows=table.select("tr").filter { it.closest("table")===table }
            val expanded=expandedProfileRows(rows)
            val headerIndex = expanded.indexOfFirst { cells ->
                val headers = cells.map(::profileHeader)
                headers.size >= 3 && headers.any { it.contains("课程名") || it.contains("课程名称") } &&
                    headers.any { it.contains("学分") || it.contains("课程性质") || it.contains("课程属性") || it.contains("选课属性") || it.contains("课程类型") || it.contains("课程类别") || it.contains("课程分类") || it.contains("考核方式") || it.contains("考核类型") || it.contains("考试方式") || it.contains("考试性质") || it.contains("考核性质") || it == "性质" }
            }
            if(headerIndex<0)continue
            val headers = expanded[headerIndex].map(::profileHeader)
            val nameIdx = headers.indexOfFirst { it.contains("课程名") || it.contains("课程名称") }
            val natureIdx = headers.indexOfFirst { it.contains("课程性质") || it.contains("课程类型") || it.contains("课程类别") || it.contains("课程分类") || it == "性质" || it == "类型" }
            val selectionIdx = headers.indexOfFirst { it.contains("选课属性") || it.contains("课程属性") || it.contains("选课性质") || it.contains("选课类别") }
            val creditIdx = headers.indexOfFirst { it.contains("学分") || it.contains("学分数") || it.contains("学分值") }
            val assessmentIdx = headers.indexOfFirst { it.contains("考核方式") || it.contains("考核类型") || it.contains("考核性质") || it.contains("考核形式") || it.contains("考试方式") || it.contains("考试性质") || it.contains("考试形式") || it == "考核" }
            if (nameIdx < 0 || listOf(natureIdx, selectionIdx, creditIdx, assessmentIdx).all { it < 0 }) continue
            val codeIdx = headers.indexOfFirst { it.contains("课程号") || it.contains("课程代码") || it.contains("课程编号") }
            val seqIdx = headers.indexOfFirst { it.contains("课序") || it.contains("教学班号") }
            for (cells in expanded.drop(headerIndex+1)) {
                if (cells.size <= nameIdx) continue
                val name = cells.getOrNull(nameIdx)?.takeIf { it.isNotBlank() } ?: continue
                if (name.contains("课程名")) continue
                profiles += QnuCourseProfile(
                    courseName = name,
                    sequence = seqIdx.takeIf { it >= 0 }?.let { profileValue(cells.getOrNull(it)) },
                    courseCode = codeIdx.takeIf { it >= 0 }?.let { profileValue(cells.getOrNull(it)) },
                    // Older pages call the selection attribute "课程属性". Keep
                    // the legacy nature fallback while also retaining the
                    // semantic field for the new course detail view.
                    nature = natureIdx.takeIf { it >= 0 }?.let { profileValue(cells.getOrNull(it)) }
                        ?: selectionIdx.takeIf { it >= 0 }?.let { profileValue(cells.getOrNull(it)) },
                    credits = creditIdx.takeIf { it >= 0 }?.let { profileValue(cells.getOrNull(it)) },
                    selectionAttribute = selectionIdx.takeIf { it >= 0 }?.let { profileValue(cells.getOrNull(it)) },
                    assessmentMethod = assessmentIdx.takeIf { it >= 0 }?.let { profileValue(cells.getOrNull(it)) },
                )
            }
        }
        return profiles.distinct()
    }

    private fun profileHeader(value: String): String = value.replace(Regex("[\\s\\u00a0　]+"), "")

    private fun expandedProfileRows(rows: List<Element>): List<List<String>> {
        val pending=mutableMapOf<Int,Pair<String,Int>>()
        return rows.map { row ->
            val cells=mutableMapOf<Int,String>()
            pending.toMap().forEach { (column,span) ->
                cells[column]=span.first
                if(span.second<=1)pending.remove(column) else pending[column]=span.first to span.second-1
            }
            var column=0
            for(cell in row.children().filter { it.tagName() in setOf("td","th") }) {
                while(column in cells)column++
                val text=cleanText(cell.text()).orEmpty()
                val width=(cell.attr("colspan").toIntOrNull()?:1).coerceIn(1,64)
                val height=(cell.attr("rowspan").toIntOrNull()?:1).coerceIn(1,200)
                repeat(width) {
                    cells[column]=text
                    if(height>1)pending[column]=text to height-1
                    column++
                }
            }
            List((cells.keys.maxOrNull()?:-1)+1){cells[it].orEmpty()}
        }
    }

    private fun rowCells(row: Element): List<String> =
        row.children()
            .filter { it.tagName().equals("th", ignoreCase = true) || it.tagName().equals("td", ignoreCase = true) }
            .map { cleanText(it.text()).orEmpty() }

    private fun isWeekdayHeader(row: Element): Boolean {
        val text = row.text()
        return row.select("th").size > 1 && weekdayNames.keys.any(text::contains)
    }

    private fun timePlace(texts: List<String?>): QnuTimePlace? {
        val (periodStart, periodEnd) = texts[3]?.let { text ->
            periodRange.matchEntire(text)?.let { m ->
                val start = m.groupValues[1].toInt()
                start to m.groupValues[2].ifEmpty { m.groupValues[1] }.toInt()
            }
        } ?: (null to null)
        val place = QnuTimePlace(
            dateText = texts[0],
            week = texts[1]?.let { Regex("(?:第)?(\\d+)(?:周)?").matchEntire(it.trim())?.groupValues?.get(1)?.toIntOrNull() },
            weekday = texts[2]?.let { weekdayNames[it] ?: weekdayNames[it.removePrefix("星期").removePrefix("周")] ?: it.toIntOrNull()?.takeIf { n -> n in 1..7 } },
            periodStart = periodStart,
            periodEnd = periodEnd,
            room = texts[4],
        )
        return place.takeUnless { it.isEmpty }
    }

    private fun cellCoordinates(cell: Element, fallbackWeekday: Int, fallbackPeriodRow: Int): Pair<Int, Int>? {
        val idMatch = cellId.matchEntire(cell.id())
        if (idMatch != null) return idMatch.groupValues[1].toInt() to idMatch.groupValues[2].toInt()

        val weekday = sequenceOf("data-weekday", "data-day")
            .mapNotNull { cell.attr(it).toIntOrNull() }
            .firstOrNull()
            ?: cell.classNames().asSequence().mapNotNull { weekdayAttribute.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }.firstOrNull()
        val period = sequenceOf("data-period", "data-section", "data-row")
            .mapNotNull { cell.attr(it).toIntOrNull() }
            .firstOrNull()
            ?: cell.classNames().asSequence().mapNotNull { periodAttribute.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }.firstOrNull()
        val resolvedWeekday = (weekday ?: fallbackWeekday).takeIf { it in 1..7 }
        val resolvedPeriod = (period ?: fallbackPeriodRow).takeIf { it in 1..20 }
        if (resolvedWeekday == null || resolvedPeriod == null) return null
        return resolvedWeekday to resolvedPeriod
    }

    /** 单元格按换行和常见块级元素切分为行并清理空白。 */
    private fun cellLines(cell: Element): List<String> {
        val lines = mutableListOf<StringBuilder>()
        var current = StringBuilder()

        fun lineBreak() {
            if (current.isNotEmpty()) lines += current
            current = StringBuilder()
        }

        fun walk(node: Node) {
            when {
                node is TextNode -> current.append(node.text())
                node is Element && node.tagName() == "br" -> lineBreak()
                node is Element && node.tagName() in setOf("div", "p", "li") -> {
                    lineBreak()
                    node.childNodes().forEach(::walk)
                    lineBreak()
                }
                else -> node.childNodes().forEach(::walk)
            }
        }
        cell.childNodes().forEach(::walk)
        lineBreak()
        return lines.mapNotNull { cleanText(it.toString()) }
    }

    private fun cleanText(text: String): String? =
        text.replace('\u00a0', ' ').trim().ifEmpty { null }
}
