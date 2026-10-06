package cn.qztc.timetable.core.domain.calendar

import cn.qztc.timetable.core.domain.serialization.LocalDateSerializer
import java.time.LocalDate
import kotlinx.serialization.Serializable

enum class AcademicDayKind { HOLIDAY, MAKEUP }

/**
 * 学期校历上的一天：放假，或调休后改上指定教学周、星期的课。
 * [useCurrentWeek] / [useCurrentWeekday] 对应设置里的「本周」「本日」。
 */
@Serializable
data class AcademicDay(
    val id: String,
    val semesterId: String,
    @Serializable(LocalDateSerializer::class) val date: LocalDate,
    val kind: AcademicDayKind,
    val name: String,
    val sourceWeek: Int? = null,
    val sourceWeekday: Int? = null,
    val useCurrentWeek: Boolean = false,
    val useCurrentWeekday: Boolean = false,
) {
    companion object {
        fun idFor(semesterId: String, date: LocalDate): String = "academic:$semesterId:$date"
    }
}

val WEEKDAY_SHORT_NAMES = listOf("一", "二", "三", "四", "五", "六", "日")

fun weekdayLabel(weekday: Int): String = "周${WEEKDAY_SHORT_NAMES.getOrNull(weekday - 1) ?: weekday}"
