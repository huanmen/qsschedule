package cn.qztc.timetable.core.domain.model

import cn.qztc.timetable.core.domain.serialization.LocalDateSerializer
import java.time.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class ScheduleLocation(
    val name: String = "",
    val placeId: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    init {
        require(name.length <= 160)
        require((latitude == null) == (longitude == null))
        require(latitude == null || (latitude.isFinite() && latitude in -90.0..90.0))
        require(longitude == null || (longitude.isFinite() && longitude in -180.0..180.0))
    }
}

@Serializable
data class ActivitySession(
    val id: String,
    val kind: ActivityKind,
    val weekday: Int? = null,
    val periodStart: Int? = null,
    val periodEnd: Int? = null,
    val weeks: List<Int> = emptyList(),
    @Serializable(LocalDateSerializer::class) val date: LocalDate? = null,
    val startMinute: Int? = null,
    val endMinute: Int? = null,
    val location: ScheduleLocation = ScheduleLocation(),
) {
    init {
        require(id.isNotBlank())
        when (kind) {
            ActivityKind.PERIOD -> {
                require(weekday in 1..7)
                require(periodStart in 1..13 && periodEnd in (periodStart ?: 1)..13)
                require(weeks.isNotEmpty() && weeks.all { it > 0 })
            }
            ActivityKind.DAY -> {
                require(date != null)
                require((startMinute == null) == (endMinute == null))
                require(startMinute == null || (startMinute in 0..1439 && endMinute in (startMinute + 1)..1440))
            }
        }
    }

    fun appliesOn(week: Int, weekday: Int, date: LocalDate): Boolean = when (kind) {
        ActivityKind.PERIOD -> this.weekday == weekday && week in weeks
        ActivityKind.DAY -> this.date == date
    }

    fun periodRange(periods: List<Period>): IntRange {
        if (kind == ActivityKind.PERIOD) return requireNotNull(periodStart)..requireNotNull(periodEnd)
        if (startMinute == null) return 1..13
        val hits = periods.filter { it.startTime.hour * 60 + it.startTime.minute < requireNotNull(endMinute) &&
            it.endTime.hour * 60 + it.endTime.minute > startMinute }
        if (hits.isNotEmpty()) return hits.minOf { it.no }..hits.maxOf { it.no }
        val nearest = periods.minByOrNull { kotlin.math.abs(it.startTime.hour * 60 + it.startTime.minute - startMinute) }?.no ?: 1
        return nearest..nearest
    }

    fun overlaps(occurrence: ClassOccurrence, periods: List<Period>): Boolean {
        if (!appliesOn(occurrence.week, occurrence.weekday, occurrence.date)) return false
        if (kind == ActivityKind.PERIOD) return requireNotNull(periodStart) <= occurrence.periodEnd && requireNotNull(periodEnd) >= occurrence.periodStart
        if (startMinute == null) return true
        val start = periods.find { it.no == occurrence.periodStart }?.startTime ?: return false
        val end = periods.find { it.no == occurrence.periodEnd }?.endTime ?: return false
        return start.hour * 60 + start.minute < requireNotNull(endMinute) && end.hour * 60 + end.minute > startMinute
    }
}
