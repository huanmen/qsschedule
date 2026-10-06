package cn.qztc.timetable.core.domain.schedule

import cn.qztc.timetable.core.domain.model.ActivityKind
import cn.qztc.timetable.core.domain.model.ScheduleActivity
import cn.qztc.timetable.core.domain.model.ActivitySession
import cn.qztc.timetable.core.domain.model.Period
import cn.qztc.timetable.core.domain.model.ScheduleLocation
import java.time.LocalDate

data class ActivityWeekBlock(
    val activityId: String,
    val name: String,
    val note: String,
    val timeText: String,
    val color: Int?,
    val startWeekday: Int,
    val endWeekday: Int,
    val periodStart: Int,
    val periodEnd: Int,
    val sessionId: String = "",
    val location: ScheduleLocation = ScheduleLocation(),
)

object ActivityWeekLayout {

    private val weekdayNames = listOf("一", "二", "三", "四", "五", "六", "日")
    private val teachingBands = listOf(1..4, 5..6, 7..10, 11..13)

    fun blocksForWeek(
        activities: List<ScheduleActivity>,
        week: Int,
        visibleDays: List<Int>,
        dateOf: (Int) -> LocalDate,
        includeLunchPeriods: Boolean = true,
        periods: List<Period> = emptyList(),
    ): List<ActivityWeekBlock> {
        if (visibleDays.isEmpty()) return emptyList()
        val result = mutableListOf<ActivityWeekBlock>()
        activities.forEach { activity ->
            if (activity.sessions.isNotEmpty()) {
                for (sameGroup in activity.sessions.groupBy { listOf(it.kind, it.location, it.startMinute, it.endMinute) }.values) {
                    val sessions = sameGroup.sortedBy { it.date ?: LocalDate.MAX }
                    if (sessions.firstOrNull()?.kind == ActivityKind.DAY) {
                        val activeDays = sessions.mapNotNull { session -> visibleDays.firstOrNull { session.appliesOn(week, it, dateOf(it)) } }.distinct()
                        for ((from, to) in contiguousRanges(visibleDays, activeDays)) {
                            val session = sessions.first { it.appliesOn(week, from, dateOf(from)) }
                            val range = session.periodRange(periods)
                            for (part in splitAroundBreaks(range.first, range.last, includeLunchPeriods)) {
                                result += ActivityWeekBlock(activity.id, activity.name, activity.note, sessionTime(session), activity.color,
                                    from, to, part.first, part.last, session.id, session.location)
                            }
                        }
                        continue
                    }
                    for (session in sessions) {
                        val range = session.periodRange(periods)
                        for (day in visibleDays.filter { session.appliesOn(week, it, dateOf(it)) }) {
                            for (part in splitAroundBreaks(range.first, range.last, includeLunchPeriods)) {
                                result += ActivityWeekBlock(activity.id, activity.name, activity.note, sessionTime(session), activity.color,
                                    day, day, part.first, part.last, session.id, session.location)
                            }
                        }
                    }
                }
                return@forEach
            }
            when (activity.kind) {
                ActivityKind.PERIOD -> {
                    val weekday = activity.weekday ?: return@forEach
                    val start = activity.periodStart ?: return@forEach
                    val end = activity.periodEnd ?: start
                    if (week !in activity.weeks || weekday !in visibleDays) return@forEach
                    appendSplitBlocks(
                        result = result,
                        activity = activity,
                        startWeekday = weekday,
                        endWeekday = weekday,
                        periodStart = start,
                        periodEnd = end,
                        includeLunch = includeLunchPeriods,
                    )
                }
                ActivityKind.DAY -> {
                    val start = activity.startDate ?: return@forEach
                    val end = activity.endDate ?: return@forEach
                    val days = visibleDays.filter { day ->
                        val date = dateOf(day)
                        !date.isBefore(start) && !date.isAfter(end)
                    }
                    if (days.isEmpty()) return@forEach
                    contiguousRanges(visibleDays, days).forEach { (from, to) ->
                        appendSplitBlocks(
                            result = result,
                            activity = activity,
                            startWeekday = from,
                            endWeekday = to,
                            periodStart = 1,
                            periodEnd = 13,
                            includeLunch = includeLunchPeriods,
                        )
                    }
                }
            }
        }
        return result
    }

    /**
     * 午间（5-6）和晚间休息把课表分成独立格子。跨过这些分隔的活动拆成多块，
     * 而不是连成一条贯穿休息带的色块。
     */
    fun splitAroundBreaks(
        periodStart: Int,
        periodEnd: Int,
        includeLunch: Boolean,
    ): List<IntRange> = teachingBands.mapNotNull { band ->
        if (band == 5..6 && !includeLunch) return@mapNotNull null
        val start = maxOf(periodStart, band.first)
        val end = minOf(periodEnd, band.last)
        if (start <= end) start..end else null
    }

    fun occupiesLunch(activity: ScheduleActivity, week: Int): Boolean {
        if (activity.sessions.isNotEmpty()) return activity.sessions.any {
            it.kind == ActivityKind.PERIOD && week in it.weeks && requireNotNull(it.periodStart) <= 6 && requireNotNull(it.periodEnd) >= 5
        }
        if (activity.kind != ActivityKind.PERIOD || week !in activity.weeks) return false
        val start = activity.periodStart ?: return false
        val end = activity.periodEnd ?: start
        return start <= 6 && end >= 5
    }

    private fun appendSplitBlocks(
        result: MutableList<ActivityWeekBlock>,
        activity: ScheduleActivity,
        startWeekday: Int,
        endWeekday: Int,
        periodStart: Int,
        periodEnd: Int,
        includeLunch: Boolean,
    ) {
        val timeText = displayTime(activity)
        splitAroundBreaks(periodStart, periodEnd, includeLunch).forEach { range ->
            result += ActivityWeekBlock(
                activityId = activity.id,
                name = activity.name,
                note = activity.note,
                timeText = timeText,
                color = activity.color,
                startWeekday = startWeekday,
                endWeekday = endWeekday,
                periodStart = range.first,
                periodEnd = range.last,
            )
        }
    }

    fun displayTime(activity: ScheduleActivity): String {
        if (activity.sessions.isNotEmpty()) return activity.sessions.joinToString("\n", transform = ::sessionTime)
        if (activity.timeText.isNotBlank()) return activity.timeText
        return when (activity.kind) {
            ActivityKind.PERIOD -> {
                val weekday = activity.weekday ?: return ""
                val start = activity.periodStart ?: return ""
                val end = activity.periodEnd ?: start
                val period = if (start == end) "第${start}节" else "第${start}-${end}节"
                "周${weekdayNames[weekday - 1]} $period"
            }
            ActivityKind.DAY -> {
                val start = activity.startDate ?: return ""
                val end = activity.endDate ?: start
                if (start == end) {
                    "${start.monthValue}月${start.dayOfMonth}日"
                } else {
                    "${start.monthValue}月${start.dayOfMonth}日–${end.monthValue}月${end.dayOfMonth}日"
                }
            }
        }
    }

    fun sessionTime(session: ActivitySession): String = when (session.kind) {
        ActivityKind.PERIOD -> "周${weekdayNames[requireNotNull(session.weekday) - 1]} 第${session.periodStart}-${session.periodEnd}节 · ${session.weeks.joinToString("、")}周"
        ActivityKind.DAY -> "${session.date}" + if (session.startMinute == null) " 全天" else " ${clock(session.startMinute)}-${clock(requireNotNull(session.endMinute))}"
    }

    private fun clock(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

    internal fun contiguousRanges(visibleDays: List<Int>, selected: List<Int>): List<Pair<Int, Int>> {
        if (selected.isEmpty()) return emptyList()
        val indexByDay = visibleDays.withIndex().associate { it.value to it.index }
        val ordered = selected.sortedBy { indexByDay[it] ?: Int.MAX_VALUE }
        val ranges = mutableListOf<Pair<Int, Int>>()
        var rangeStart = ordered.first()
        var previous = ordered.first()
        ordered.drop(1).forEach { day ->
            val previousIndex = indexByDay[previous]
            val currentIndex = indexByDay[day]
            if (previousIndex != null && currentIndex == previousIndex + 1 && day == previous + 1) {
                previous = day
            } else {
                ranges += rangeStart to previous
                rangeStart = day
                previous = day
            }
        }
        ranges += rangeStart to previous
        return ranges
    }
}
