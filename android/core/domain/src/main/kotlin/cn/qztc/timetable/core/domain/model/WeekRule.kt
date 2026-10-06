package cn.qztc.timetable.core.domain.model

import kotlinx.serialization.Serializable

/**
 * 上课周次集合。[weeks] 已排序去重，[rawText] 保留教务原文用于诊断与展示。
 */
@Serializable
data class WeekRule(
    val weeks: List<Int>,
    val rawText: String,
) {
    init {
        require(weeks.isNotEmpty()) { "weeks must not be empty" }
    }

    operator fun contains(week: Int): Boolean = week in weeks

    companion object {
        fun of(weeks: Collection<Int>, rawText: String = "") =
            WeekRule(weeks.distinct().sorted(), rawText)
    }
}

/**
 * 解析教务周次文本。支持已观察到的全部样式：
 * `1-16周`、`第13周`、`1-16双`、`1-16单`、`3-17周单`、`3-18双`，以及顿号/逗号分隔的组合。
 * 无法识别时返回 null，由调用方生成诊断信息，不允许静默猜测。
 */
object WeekRuleText {

    private val singleWeek = Regex("""^第(\d{1,2})周$""")
    private val rangeOrWeek = Regex("""^(\d{1,2})(?:-(\d{1,2}))?(?:周)?([单双])?$""")

    fun parse(text: String): WeekRule? {
        val normalized = text.replace("\u00a0", " ").trim()
        if (normalized.isEmpty()) return null
        val weeks = sortedSetOf<Int>()
        for (part in normalized.split(',', '，', '、')) {
            val piece = part.trim()
            if (piece.isEmpty()) return null
            val parsed = parsePiece(piece) ?: return null
            weeks.addAll(parsed)
        }
        if (weeks.isEmpty()) return null
        return WeekRule(weeks.toList(), text)
    }

    private fun parsePiece(piece: String): List<Int>? {
        singleWeek.matchEntire(piece)?.let { m ->
            return listOf(m.groupValues[1].toInt())
        }
        val m = rangeOrWeek.matchEntire(piece) ?: return null
        val start = m.groupValues[1].toInt()
        val end = m.groupValues[2].ifEmpty { m.groupValues[1] }.toInt()
        if (start !in 1..53 || end < start || end > 53) return null
        val parity = m.groupValues[3]
        return (start..end).filter { week ->
            when (parity) {
                "单" -> week % 2 == 1
                "双" -> week % 2 == 0
                else -> true
            }
        }.ifEmpty { null }
    }
}
