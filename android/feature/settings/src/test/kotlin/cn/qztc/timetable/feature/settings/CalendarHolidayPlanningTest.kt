package cn.qztc.timetable.feature.settings

import cn.qztc.timetable.core.domain.calendar.*
import cn.qztc.timetable.core.domain.model.Semester
import cn.qztc.timetable.core.domain.model.Term
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CalendarHolidayPlanningTest {
    private fun semester(start: String, end: String, term: Term = Term.AUTUMN) =
        DetectedSchoolSemester("2026-2027",term,if(term==Term.SPRING) "第二学期" else "第一学期",LocalDate.parse(start),LocalDate.parse(end))

    @Test fun pendingStatusIsHiddenOutsideCurrentSemesterButIncludedStatusRemains() {
        assertFalse(shouldShowCalendarStatus(included = false, eligibleForCurrentSemester = false))
        assertTrue(shouldShowCalendarStatus(included = false, eligibleForCurrentSemester = true))
        assertTrue(shouldShowCalendarStatus(included = true, eligibleForCurrentSemester = false))
    }

    @Test fun lunarFestivalsAndSolarTermHaveActualGregorianDates() {
        val dates=CalendarHolidayPlanning.festivalDates(2026)
        assertEquals(LocalDate.of(2026,9,25),dates["中秋节"])
        assertEquals(LocalDate.of(2026,6,19),dates["端午节"])
        assertEquals(LocalDate.of(2026,4,5),dates["清明节"])
        assertTrue(CalendarHolidayPlanning.festivalDates(2025).getValue("中秋节")>LocalDate.of(2025,10,1))
        assertTrue(dates.getValue("中秋节")<LocalDate.of(2026,10,1))
    }

    @Test fun autumnIncludesNextJanuaryButNotSpringFestivalOrSpringHolidays() {
        val result=CalendarHolidayPlanning.supplement(listOf(semester("2026-08-31","2027-01-15")),emptyList())
        assertEquals(setOf("中秋节","国庆节","元旦"),result.map { it.name }.toSet())
        assertTrue(result.any { it.name=="元旦" && it.date.year==2027 })
        assertEquals(result.sortedBy { it.date },result)
    }

    @Test fun futureSpringHasItsThreeHolidaysWithoutInventedMakeupDates() {
        val result=CalendarHolidayPlanning.supplement(listOf(semester("2027-02-22","2027-07-02",Term.SPRING)),emptyList())
        assertEquals(setOf("清明节","劳动节","端午节"),result.map { it.name }.toSet())
        assertTrue(result.all { it.kind==AcademicDayKind.HOLIDAY && it.id.startsWith("estimated:") })
        assertEquals(5,result.count { it.name=="劳动节" })
    }

    @Test fun publishedOrEditedHolidayDatesOverrideEstimates() {
        val manual=AcademicDay("manual","test",LocalDate.of(2026,9,26),AcademicDayKind.HOLIDAY,"中秋节")
        val result=CalendarHolidayPlanning.supplement(listOf(semester("2026-08-31","2027-01-15")),listOf(manual))
        assertEquals(listOf(manual),result.filter { it.name=="中秋节" })
    }

    @Test fun threeDaysWithTwoWeekendDaysNeedNoMakeupButActualMakeupAlwaysWins() {
        val days=(25..27).map { AcademicDay("h$it","test",LocalDate.of(2026,9,it),AcademicDayKind.HOLIDAY,"中秋节") }
        val schedule=buildCalendarHolidaySchedules(days).single()
        assertEquals("假期含周末，无调休",holidayMakeupSummary(schedule))
        val makeup=days.first().copy(date=LocalDate.of(2026,9,20),kind=AcademicDayKind.MAKEUP,name="中秋调休")
        assertTrue(holidayMakeupSummary(buildCalendarHolidaySchedules(days+makeup).single()).startsWith("调休："))
        val unknown=days.first().copy(id="estimated:x",name="劳动节")
        assertEquals("调休待定",holidayMakeupSummary(buildCalendarHolidaySchedules(listOf(unknown)).single()))
    }

    @Test fun makeupCourseDateRoundTripsThroughSemesterWeekAndWeekday() {
        val semester = Semester("s", "2026 秋", 2026, Term.AUTUMN, LocalDate.of(2026, 8, 31), 20)
        val day = AcademicDay(
            id = "m",
            semesterId = semester.id,
            date = LocalDate.of(2026, 10, 10),
            kind = AcademicDayKind.MAKEUP,
            name = "国庆调休",
            sourceWeek = 5,
            sourceWeekday = 3,
        )
        assertEquals(LocalDate.of(2026, 9, 30), makeupSourceDate(semester, day))
        assertNull(makeupSourceDate(semester, day.copy(sourceWeek = null, sourceWeekday = null)))
    }
}
