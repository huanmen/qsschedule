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

    private val singleWeek = Regex("""^第?([^周单双,，、\-~～至]+)周?$""")
    private val rangeOrWeek = Regex("""^第?([^周单双,，、\-~～至]+)(?:[-~～至]([^周单双,，、]+))?(?:周)?([单双])?$""")

    fun parse(text: String): WeekRule? {
        val normalized = text.replace("\u00a0", " ").replace(Regex("\\s+"), "").trim()
        if (normalized.isEmpty()) return null
        val weeks = sortedSetOf<Int>()
        // 教务页面常把多个短期周次写成“第16、17周”或“16,17周”。
        // 先去掉共享的“第/周”标记，再逐项解析，避免整条记录被丢弃。
        val listText = normalized
            .removePrefix("第")
            .removeSuffix("周")
        for (part in listText.split(',', '，', '、')) {
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
            return chineseOrArabic(m.groupValues[1])?.let(::listOf)
        }
        val m = rangeOrWeek.matchEntire(piece) ?: return null
        val start = chineseOrArabic(m.groupValues[1]) ?: return null
        val end = m.groupValues[2].ifEmpty { m.groupValues[1] }.let(::chineseOrArabic) ?: return null
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

    /** 处理教务系统偶尔返回的中文数字（含大写、繁体），如“十六”“壹拾陆”。 */
    private fun chineseOrArabic(value: String): Int? {
        value.toIntOrNull()?.let { return it.takeIf { week -> week in 1..53 } }
        val digits = mapOf(
            '零' to 0, '〇' to 0, '○' to 0,
            '一' to 1, '壹' to 1, '①' to 1,
            '二' to 2, '贰' to 2, '貳' to 2, '兩' to 2, '两' to 2, '②' to 2,
            '三' to 3, '叁' to 3, '參' to 3, '③' to 3,
            '四' to 4, '肆' to 4, '④' to 4,
            '五' to 5, '伍' to 5, '⑤' to 5,
            '六' to 6, '陆' to 6, '陸' to 6, '⑥' to 6,
            '七' to 7, '柒' to 7, '⑦' to 7,
            '八' to 8, '捌' to 8, '⑧' to 8,
            '九' to 9, '玖' to 9, '⑨' to 9,
        )
        val units = mapOf('十' to 10, '拾' to 10, '百' to 100, '佰' to 100)
        if (value.isEmpty() || value.any { it !in digits && it !in units }) return null
        var section = 0
        var number = 0
        value.forEach { char ->
            val digit = digits[char]
            if (digit != null) number = digit
            else {
                val unit = units[char] ?: return null
                if (unit == 10 && number == 0) number = 1
                section += number * unit
                number = 0
            }
        }
        return (section + number).takeIf { it in 1..53 }
    }
}
