package cn.qztc.timetable.feature.settings

import cn.qztc.timetable.core.domain.calendar.AcademicDay
import cn.qztc.timetable.core.domain.calendar.AcademicDayKind
import com.nlf.calendar.Lunar
import com.nlf.calendar.Solar
import java.time.LocalDate

internal fun holidayNameKey(name: String): String = name.replace(Regex("调休|补班|补课|假期|放假|节"), "").trim()

internal fun holidaySelectionKey(schedule: CalendarHolidaySchedule): String {
    val first = schedule.holiday?.days?.firstOrNull() ?: schedule.makeup.first().days.first()
    return "holiday:${first.date.year}:${holidayNameKey(first.name)}:${schedule.holiday?.start ?: first.date}"
}

internal fun eventSelectionKey(event: SchoolCalendarEvent) = "event:${event.id}"

internal fun shouldShowCalendarStatus(included: Boolean, eligibleForCurrentSemester: Boolean) =
    included || eligibleForCurrentSemester

/** Festival dates are calculated; contiguous leave is only a suggestion until published/edited. */
internal object CalendarHolidayPlanning {
    private fun Solar.date() = LocalDate.of(year, month, day)

    fun festivalDates(year: Int): Map<String, LocalDate> = linkedMapOf(
        "元旦" to LocalDate.of(year, 1, 1),
        "清明节" to Lunar.fromYmd(year, 1, 1).jieQiTable.getValue("清明").date(),
        "劳动节" to LocalDate.of(year, 5, 1),
        "端午节" to Lunar.fromYmd(year, 5, 5).solar.date(),
        "中秋节" to Lunar.fromYmd(year, 8, 15).solar.date(),
        "国庆节" to LocalDate.of(year, 10, 1),
    )

    fun supplement(semesters: List<DetectedSchoolSemester>, known: List<AcademicDay>): List<AcademicDay> {
        val generated = semesters.flatMap { semester ->
            val last = semester.endDate ?: semester.startDate.plusMonths(5)
            (semester.startDate.year..last.year).flatMap { year ->
                festivalDates(year).flatMap festival@{ (name, date) ->
                    if (date !in semester.startDate..last || known.any {
                        it.kind == AcademicDayKind.HOLIDAY && it.date.year == year && holidayNameKey(it.name) == holidayNameKey(name)
                    }) return@festival emptyList()
                    val start = when {
                        name in setOf("劳动节", "国庆节") -> date
                        date.dayOfWeek.value in 1..2 -> date.minusDays(2)
                        date.dayOfWeek.value == 7 -> date.minusDays(1)
                        else -> date
                    }
                    val count = when (name) {
                        "劳动节" -> if (year >= 2025) 5 else 1
                        "国庆节" -> 7
                        else -> if (date.dayOfWeek.value == 3) 1 else 3
                    }
                    (0 until count).map { offset ->
                        val day = start.plusDays(offset.toLong())
                        AcademicDay("estimated:$year:$name:$day", "", day, AcademicDayKind.HOLIDAY, name)
                    }.filter { it.date in semester.startDate..last }
                }
            }
        }
        return (known + generated.sortedBy { if (it.name == "中秋节") 0 else 1 })
            .distinctBy(::academicDayKey).sortedBy { it.date }
    }
}

internal fun holidayMakeupSummary(schedule: CalendarHolidaySchedule): String {
    if (schedule.makeup.isNotEmpty()) return "调休：" + schedule.makeup.joinToString("、") {
        calendarDateRangeLabel(it.start, it.end)
    }
    val dates = schedule.holiday?.days.orEmpty()
    if (dates.size == 3 && dates.count { it.date.dayOfWeek.value >= 6 } == 2) return "假期含周末，无调休"
    if (dates.any { it.id.startsWith("estimated:") || it.id.startsWith("pending:") || it.id.startsWith("pending-makeup:") }) return "调休待定"
    return "无调休"
}
