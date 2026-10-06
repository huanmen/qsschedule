package cn.qztc.timetable.core.domain.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

object PublicHolidayCalendar {
    fun datesCovered(startMillis: Long, endMillis: Long, allDay: Boolean, first: LocalDate, last: LocalDate, zone: ZoneId): List<LocalDate> {
        if (last < first) return emptyList()
        val eventZone = if (allDay) ZoneOffset.UTC else zone
        val start = Instant.ofEpochMilli(startMillis).atZone(eventZone).toLocalDate()
        // CalendarContract event end is exclusive, including timed events ending at midnight.
        val inclusiveEnd = if (endMillis > startMillis) endMillis - 1 else startMillis
        val end = Instant.ofEpochMilli(inclusiveEnd).atZone(eventZone).toLocalDate()
        val clippedStart = maxOf(first, start)
        val clippedEnd = minOf(last, end)
        if (clippedEnd < clippedStart) return emptyList()
        return generateSequence(clippedStart) { it.plusDays(1) }.takeWhile { it <= clippedEnd }.toList()
    }

    fun merge(current: List<AcademicDay>, incoming: List<AcademicDay>): List<AcademicDay> {
        val existing = current.mapTo(mutableSetOf()) { it.date }
        val newDays = incoming.groupBy { it.date }.values.map { entries ->
            entries.firstOrNull { it.kind == AcademicDayKind.MAKEUP } ?: entries.first()
        }.filter { it.date !in existing }
        return (current + newDays).sortedBy { it.date }
    }
}
