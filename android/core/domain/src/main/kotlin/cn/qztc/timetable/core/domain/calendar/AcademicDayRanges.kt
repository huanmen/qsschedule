package cn.qztc.timetable.core.domain.calendar

import java.time.LocalDate

data class AcademicDayRange(val days: List<AcademicDay>) {
    val start get() = days.first().date
    val end get() = days.last().date
    val name get() = days.first().name
}

object AcademicDayRanges {
    fun normalize(days: List<AcademicDay>): List<AcademicDay> = days.map { day ->
        val name = day.name.trim()
        val normalized = if (name.contains("中秋") && name.contains("国庆")) {
            if (day.date.year == 2025 && day.date != LocalDate.of(2025,10,6)) {
                if (day.kind == AcademicDayKind.MAKEUP) "国庆调休" else "国庆节"
            } else if (day.kind == AcademicDayKind.MAKEUP) "中秋调休" else "中秋节"
        } else name.ifBlank { if(day.kind == AcademicDayKind.HOLIDAY) "放假" else "调休" }
        day.copy(name = normalized)
    }.groupBy { it.date }.values.map { sameDay ->
        sameDay.firstOrNull { it.kind == AcademicDayKind.HOLIDAY && it.name.contains("中秋") } ?: sameDay.first()
    }.sortedBy { it.date }

    fun group(days: List<AcademicDay>): List<AcademicDayRange> {
        val result = mutableListOf<AcademicDayRange>()
        var current = mutableListOf<AcademicDay>()
        for (day in normalize(days)) {
            val previous = current.lastOrNull()
            val sameRule = previous != null && day.copy(id = previous.id, date = previous.date) == previous
            if (previous != null && (day.date != previous.date.plusDays(1) || !sameRule)) {
                result += AcademicDayRange(current.toList()); current = mutableListOf()
            }
            current += day
        }
        if(current.isNotEmpty()) result += AcademicDayRange(current)
        return result
    }

    fun replace(days: List<AcademicDay>, original: List<AcademicDay>, draft: AcademicDay, end: LocalDate): List<AcademicDay> {
        require(end >= draft.date && end <= draft.date.plusYears(1))
        val oldDates = original.map { it.date }.toSet()
        val updated = generateSequence(draft.date) { if(it < end) it.plusDays(1) else null }.map { date ->
            draft.copy(id = AcademicDay.idFor(draft.semesterId,date), date = date)
        }.toList()
        val nextDates = updated.map { it.date }.toSet()
        return normalize(days.filterNot { it.date in oldDates || it.date in nextDates } + updated)
    }
}
