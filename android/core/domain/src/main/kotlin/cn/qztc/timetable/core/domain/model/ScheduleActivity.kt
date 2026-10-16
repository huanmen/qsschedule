package cn.qztc.timetable.core.domain.model

import cn.qztc.timetable.core.domain.serialization.LocalDateSerializer
import java.time.LocalDate
import kotlinx.serialization.Serializable

/** 活动占据课表格子的方式：按上课节次，或按自然日（可跨天）。 */
enum class ActivityKind { PERIOD, DAY }

/**
 * 日历中的活动。校历导入项同时用于周课表活动视图；手动项仅在日历中展示。
 */
@Serializable
data class ScheduleActivity(
    val id: String,
    val semesterId: String,
    val name: String,
    val note: String = "",
    val timeText: String = "",
    val kind: ActivityKind,
    val color: Int? = null,
    val weekday: Int? = null,
    val periodStart: Int? = null,
    val periodEnd: Int? = null,
    val weeks: List<Int> = emptyList(),
    @Serializable(LocalDateSerializer::class) val startDate: LocalDate? = null,
    @Serializable(LocalDateSerializer::class) val endDate: LocalDate? = null,
    val sessions: List<ActivitySession> = emptyList(),
    val hiddenFromSchedule: Boolean = false,
    val calendarTimetable: Boolean = true,
    val memoTime: ActivityMemoTime? = null,
) {
    fun effectiveSessions(): List<ActivitySession> {
        if (memoTime != null) return emptyList()
        if (sessions.isNotEmpty()) return sessions
        return when (kind) {
            ActivityKind.PERIOD -> if (weekday in 1..7 && periodStart in 1..13 && weeks.isNotEmpty()) {
                listOf(ActivitySession("$id:legacy", kind, weekday, periodStart, periodEnd ?: periodStart, weeks))
            } else emptyList()
            ActivityKind.DAY -> {
                val first = startDate ?: return emptyList()
                val last = endDate ?: first
                generateSequence(first) { if (it < last) it.plusDays(1) else null }
                    .takeWhile { it <= last }.map { ActivitySession("$id:$it", kind, date = it) }.toList()
            }
        }
    }

    fun appliesOn(week: Int, weekday: Int, date: LocalDate): Boolean = if (memoTime != null) {
        date in memoTime.dates()
    } else if (sessions.isNotEmpty()) {
        sessions.any { it.appliesOn(week, weekday, date) }
    } else when (kind) {
        ActivityKind.PERIOD ->
            this.weekday == weekday && week in weeks
        ActivityKind.DAY -> {
            val start = startDate ?: return false
            val end = endDate ?: return false
            !date.isBefore(start) && !date.isAfter(end)
        }
    }
}
