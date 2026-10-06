package cn.qztc.timetable.core.domain.calendar

import cn.qztc.timetable.core.domain.model.Semester
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * 周次与日期换算。开学日期归一到所在周的周一，节假日不改变周次编号（F-TERM-03）。
 */
object SemesterCalendar {

    fun firstMonday(startDate: LocalDate): LocalDate =
        startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** 第 [week] 周星期 [weekday]（1=周一）的日期。 */
    fun dateOf(semester: Semester, week: Int, weekday: Int): LocalDate {
        require(week >= 1) { "week must be >= 1" }
        require(weekday in 1..7) { "weekday must be 1..7" }
        return firstMonday(semester.startDate).plusDays(((week - 1) * 7 + (weekday - 1)).toLong())
    }

    /** [date] 属于第几周；早于学期开始或超出总周数时返回 null。 */
    fun weekOf(semester: Semester, date: LocalDate): Int? {
        val monday = firstMonday(semester.startDate)
        if (date.isBefore(monday)) return null
        val week = ChronoUnit.DAYS.between(monday, date).toInt() / 7 + 1
        return week.takeIf { it <= semester.totalWeeks }
    }

    /** 1=周一..7=周日。 */
    fun weekdayOf(date: LocalDate): Int = date.dayOfWeek.value
}
