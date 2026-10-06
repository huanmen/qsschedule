package cn.qztc.timetable.integration.qnu

import java.time.LocalDate

data class QnuGradeSemester(val year: String, val term: String, val records: List<QnuGradeRecord>) {
    val id: String get() = "$year/$term"
    val label: String get() = "$year 年 · ${term.removeSuffix("学期").removeSuffix("季")}季学期"
    val shortLabel: String get() = "${year.takeLast(2)} · ${term.removeSuffix("学期").removeSuffix("季")}"
    val order: Int? get() = QnuGradeSemesters.order(year, term)
}

/** Group actual returned history, not the school's multi-decade year selector. */
object QnuGradeSemesters {
    fun group(records: List<QnuGradeRecord>): List<QnuGradeSemester> = records
        .groupBy { it.year.orEmpty() to it.term.orEmpty() }
        .map { (key, rows) -> QnuGradeSemester(key.first, key.second, rows) }
        .sortedByDescending { it.order ?: Int.MIN_VALUE }

    fun order(year: String, term: String): Int? {
        val y = year.trim().toIntOrNull()?.takeIf { it in 1900..2200 } ?: return null
        val t = when (term.trim().removeSuffix("学期").removeSuffix("季")) {
            "春" -> 0; "夏" -> 1; "秋" -> 2; else -> return null
        }
        return y * 3 + t
    }

    fun defaultExpanded(groups: List<QnuGradeSemester>, today: LocalDate = LocalDate.now()): String? {
        val currentYear = if (today.monthValue == 1) today.year - 1 else today.year
        val currentTerm = when (today.monthValue) { in 2..5 -> 0; in 6..7 -> 1; else -> 2 }
        val current = currentYear * 3 + currentTerm
        return groups.filter { (it.order ?: Int.MAX_VALUE) < current }.maxByOrNull { it.order!! }?.id
            ?: groups.maxByOrNull { it.order ?: Int.MIN_VALUE }?.id
    }
}
