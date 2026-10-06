package cn.qztc.timetable.widget

import java.time.LocalTime

/** Each course gets one equally sized row; the bottom strip represents the following break. */
internal data class WidgetTimelinePosition(val courseFraction: Float?, val breakFraction: Float?)

internal fun widgetTimelinePosition(start: LocalTime, end: LocalTime, nextStart: LocalTime?, now: LocalTime): WidgetTimelinePosition {
    val seconds = now.toSecondOfDay()
    val from = start.toSecondOfDay()
    val until = end.toSecondOfDay()
    val course = if (seconds in from until until && until > from) (seconds - from).toFloat() / (until - from) else null
    val next = nextStart?.toSecondOfDay()
    val pause = if (next != null && next > until && seconds in until until next) (seconds - until).toFloat() / (next - until) else null
    return WidgetTimelinePosition(course, pause)
}

internal fun widgetFocusIndex(entries: List<TodayWidgetEntry>, now: LocalTime, visibleRows: Int = 3): Int {
    val currentOrNext = entries.indexOfFirst { now < it.end }.takeIf { it >= 0 } ?: entries.lastIndex.coerceAtLeast(0)
    val desired = currentOrNext - if (visibleRows >= 3) 1 else 0
    return desired.coerceIn(0, (entries.size - visibleRows).coerceAtLeast(0))
}
