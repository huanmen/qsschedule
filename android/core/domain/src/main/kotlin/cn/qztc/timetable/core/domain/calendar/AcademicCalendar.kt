package cn.qztc.timetable.core.domain.calendar

import cn.qztc.timetable.core.domain.model.Semester
import java.time.LocalDate

sealed class AcademicDaySchedule {
    data class Holiday(val name: String) : AcademicDaySchedule()
    data class Teaching(
        val week: Int,
        val weekday: Int,
        val mark: AcademicDay? = null,
    ) : AcademicDaySchedule()
}

/**
 * 把放假、调休日映射成「这一天实际上哪一周周几的课」。
 */
class AcademicCalendar(days: List<AcademicDay> = emptyList()) {
    private val byDate = AcademicDayRanges.normalize(days).associateBy { it.date }

    val isEmpty: Boolean get() = byDate.isEmpty()

    fun dayOn(date: LocalDate): AcademicDay? = byDate[date]

    fun resolve(semester: Semester, date: LocalDate): AcademicDaySchedule {
        val override = byDate[date]
        if (override?.kind == AcademicDayKind.HOLIDAY) {
            return AcademicDaySchedule.Holiday(override.name.ifBlank { "放假" })
        }
        val naturalWeek = SemesterCalendar.weekOf(semester, date)
        val naturalWeekday = SemesterCalendar.weekdayOf(date)
        if (override?.kind == AcademicDayKind.MAKEUP) {
            val week = if (override.useCurrentWeek) naturalWeek else override.sourceWeek
            val weekday = if (override.useCurrentWeekday) naturalWeekday else override.sourceWeekday
            if (week != null && weekday != null && weekday in 1..7 && week >= 1) {
                return AcademicDaySchedule.Teaching(week, weekday, override)
            }
            if (naturalWeek != null) {
                return AcademicDaySchedule.Teaching(naturalWeek, naturalWeekday, override)
            }
        }
        return if (naturalWeek != null) {
            AcademicDaySchedule.Teaching(naturalWeek, naturalWeekday, override)
        } else {
            AcademicDaySchedule.Teaching(1, naturalWeekday, override)
        }
    }

    fun headerMark(date: LocalDate): String? {
        val day = byDate[date] ?: return null
        return when (day.kind) {
            AcademicDayKind.HOLIDAY -> day.name.ifBlank { "放假" }.take(4)
            AcademicDayKind.MAKEUP -> "调休"
        }
    }

    fun caption(semester: Semester, date: LocalDate): String? {
        val day = byDate[date] ?: return null
        return when (day.kind) {
            AcademicDayKind.HOLIDAY -> day.name.ifBlank { "放假" }
            AcademicDayKind.MAKEUP -> makeupCaption(semester, date, day)
        }
    }

    private fun makeupCaption(semester: Semester, date: LocalDate, day: AcademicDay): String {
        val name = day.name.ifBlank { "调休" }
        val weekPart = when {
            day.useCurrentWeek -> "本周"
            day.sourceWeek != null -> "第${day.sourceWeek}周"
            else -> "未选周次"
        }
        val dayPart = when {
            day.useCurrentWeekday -> "本日"
            day.sourceWeekday != null -> weekdayLabel(day.sourceWeekday)
            else -> "未选星期"
        }
        val resolved = resolve(semester, date)
        val resolvedHint = if (resolved is AcademicDaySchedule.Teaching &&
            (day.useCurrentWeek || day.useCurrentWeekday)
        ) {
            "（第${resolved.week}周${weekdayLabel(resolved.weekday)}）"
        } else {
            ""
        }
        return "$name · 上${weekPart}${dayPart}的课$resolvedHint"
    }

    companion object {
        val EMPTY = AcademicCalendar()

        fun holidayGreeting(name: String?): String {
            val value = name.orEmpty()
            return when {
                value.contains("元旦") -> "元旦快乐"
                value.contains("春节") -> "新春快乐"
                value.contains("清明") -> "清明安康"
                value.contains("劳动") -> "劳动节快乐"
                value.contains("端午") -> "端午安康"
                value.contains("中秋") -> "中秋快乐"
                value.contains("国庆") -> "国庆快乐"
                value.isNotBlank() && value != "放假" -> "假期愉快"
                else -> "好好休息，安排点自己的事吧"
            }
        }
    }
}
