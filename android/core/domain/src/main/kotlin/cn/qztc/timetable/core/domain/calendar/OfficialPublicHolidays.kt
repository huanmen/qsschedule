package cn.qztc.timetable.core.domain.calendar

import cn.qztc.timetable.core.domain.model.Semester
import java.time.LocalDate

/**
 * 国务院公布的法定节假日与调休上班日，用作校历初始填入。
 * 学校实际安排仍由用户改；未内置年份不推测放假或补班日期。
 */
object OfficialPublicHolidays {
    val supportedYears: Set<Int> = setOf(2025, 2026)

    fun forSemester(semester: Semester): List<AcademicDay> {
        val first = SemesterCalendar.dateOf(semester, 1, 1)
        val last = SemesterCalendar.dateOf(semester, semester.totalWeeks, 7)
        return inRange(first, last).map { it.copy(semesterId = semester.id, id = AcademicDay.idFor(semester.id, it.date)) }
    }

    fun inRange(first: LocalDate, last: LocalDate): List<AcademicDay> {
        if (last.isBefore(first)) return emptyList()
        return (first.year..last.year).flatMap { yearDays(it) }
            .filter { it.date in first..last }
            .distinctBy { it.date }
    }

    private fun yearDays(year: Int): List<AcademicDay> {
        val holidays = mutableListOf<AcademicDay>()
        val makeups = mutableListOf<AcademicDay>()
        when (year) {
            // https://www.gov.cn/zhengce/content/202411/content_6986382.htm
            2025 -> {
                addSpan(holidays, year, "元旦", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1))
                addSpan(holidays, year, "春节", LocalDate.of(2025, 1, 28), LocalDate.of(2025, 2, 4))
                addSpan(holidays, year, "清明节", LocalDate.of(2025, 4, 4), LocalDate.of(2025, 4, 6))
                addSpan(holidays, year, "劳动节", LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 5))
                addSpan(holidays, year, "端午节", LocalDate.of(2025, 5, 31), LocalDate.of(2025, 6, 2))
                addSpan(holidays, year, "国庆节", LocalDate.of(2025, 10, 1), LocalDate.of(2025, 10, 5))
                addSpan(holidays, year, "中秋节", LocalDate.of(2025, 10, 6), LocalDate.of(2025, 10, 6))
                addSpan(holidays, year, "国庆节", LocalDate.of(2025, 10, 7), LocalDate.of(2025, 10, 8))
                makeups += makeup(year, LocalDate.of(2025, 1, 26), "春节调休")
                makeups += makeup(year, LocalDate.of(2025, 2, 8), "春节调休")
                makeups += makeup(year, LocalDate.of(2025, 4, 27), "劳动节调休")
                makeups += makeup(year, LocalDate.of(2025, 9, 28), "国庆调休")
                makeups += makeup(year, LocalDate.of(2025, 10, 11), "国庆调休")
            }
            2026 -> {
                addSpan(holidays, year, "元旦", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3))
                addSpan(holidays, year, "春节", LocalDate.of(2026, 2, 15), LocalDate.of(2026, 2, 23))
                addSpan(holidays, year, "清明节", LocalDate.of(2026, 4, 4), LocalDate.of(2026, 4, 6))
                addSpan(holidays, year, "劳动节", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5))
                addSpan(holidays, year, "端午节", LocalDate.of(2026, 6, 19), LocalDate.of(2026, 6, 21))
                addSpan(holidays, year, "中秋节", LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 27))
                addSpan(holidays, year, "国庆节", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7))
                makeups += makeup(year, LocalDate.of(2026, 1, 4), "元旦调休")
                makeups += makeup(year, LocalDate.of(2026, 2, 14), "春节调休")
                makeups += makeup(year, LocalDate.of(2026, 2, 28), "春节调休")
                makeups += makeup(year, LocalDate.of(2026, 5, 9), "劳动节调休")
                makeups += makeup(year, LocalDate.of(2026, 9, 20), "国庆调休")
                makeups += makeup(year, LocalDate.of(2026, 10, 10), "国庆调休")
            }
            else -> Unit
        }
        return holidays + makeups
    }

    private fun addSpan(
        sink: MutableList<AcademicDay>,
        year: Int,
        name: String,
        start: LocalDate,
        end: LocalDate,
    ) {
        var date = start
        while (!date.isAfter(end)) {
            sink += AcademicDay(
                id = "official:$year:$date",
                semesterId = "",
                date = date,
                kind = AcademicDayKind.HOLIDAY,
                name = name,
            )
            date = date.plusDays(1)
        }
    }

    private fun makeup(year: Int, date: LocalDate, name: String) = AcademicDay(
        id = "official:$year:$date",
        semesterId = "",
        date = date,
        kind = AcademicDayKind.MAKEUP,
        name = name,
        useCurrentWeek = true,
        useCurrentWeekday = false,
    )
}
