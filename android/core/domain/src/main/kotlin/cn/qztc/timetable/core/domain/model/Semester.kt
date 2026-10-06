package cn.qztc.timetable.core.domain.model

import cn.qztc.timetable.core.domain.serialization.LocalDateSerializer
import java.time.LocalDate
import kotlinx.serialization.Serializable

enum class Term { SPRING, SUMMER, AUTUMN }

/**
 * 一个独立保存的学期。[startDate] 是第 1 周内的任意一天，周次计算时会归一到该周周一。
 */
@Serializable
data class Semester(
    val id: String,
    val name: String,
    val schoolYear: Int,
    val term: Term,
    @Serializable(LocalDateSerializer::class) val startDate: LocalDate,
    val totalWeeks: Int = DEFAULT_TOTAL_WEEKS,
    val archived: Boolean = false,
) {
    fun containsDate(date: LocalDate): Boolean {
        val monday = startDate.minusDays((startDate.dayOfWeek.value - 1).toLong())
        val last = monday.plusWeeks(totalWeeks.toLong()).minusDays(1)
        return !date.isBefore(monday) && !date.isAfter(last)
    }

    companion object {
        const val DEFAULT_TOTAL_WEEKS = 20

        /** 提醒始终跟“今天所在学期”；查看往期课表时不改提醒来源。 */
        fun forReminders(semesters: List<Semester>, today: LocalDate): Semester? =
            semesters.firstOrNull { it.containsDate(today) }
                ?: semesters.filter { !it.startDate.isAfter(today) }.maxByOrNull { it.startDate }
    }
}
