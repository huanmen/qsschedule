package cn.qztc.timetable.core.domain.calendar

import cn.qztc.timetable.core.domain.model.Semester
import cn.qztc.timetable.core.domain.model.Term
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicCalendarTest {

    private val semester = Semester(
        id = "s1",
        name = "2026 秋",
        schoolYear = 2026,
        term = Term.AUTUMN,
        startDate = LocalDate.of(2026, 8, 31),
        totalWeeks = 20,
    )

    @Test
    fun `holiday day has no teaching slot`() {
        val date = LocalDate.of(2026, 10, 1)
        val calendar = AcademicCalendar(
            listOf(
                AcademicDay(
                    id = "h",
                    semesterId = "s1",
                    date = date,
                    kind = AcademicDayKind.HOLIDAY,
                    name = "国庆节",
                ),
            ),
        )
        val resolved = calendar.resolve(semester, date)
        assertTrue(resolved is AcademicDaySchedule.Holiday)
        assertEquals("国庆节", calendar.caption(semester, date))
        assertEquals("国庆节", calendar.headerMark(date))
    }

    @Test
    fun `makeup can use current week and a chosen weekday`() {
        val saturday = LocalDate.of(2026, 10, 10)
        val calendar = AcademicCalendar(
            listOf(
                AcademicDay(
                    id = "m",
                    semesterId = "s1",
                    date = saturday,
                    kind = AcademicDayKind.MAKEUP,
                    name = "国庆调休",
                    sourceWeekday = 1,
                    useCurrentWeek = true,
                ),
            ),
        )
        val resolved = calendar.resolve(semester, saturday) as AcademicDaySchedule.Teaching
        assertEquals(SemesterCalendar.weekOf(semester, saturday), resolved.week)
        assertEquals(1, resolved.weekday)
        assertTrue(calendar.caption(semester, saturday)!!.contains("本周"))
        assertTrue(calendar.caption(semester, saturday)!!.contains("周一"))
    }

    @Test
    fun `makeup 本日 uses the calendar weekday of that date`() {
        val saturday = LocalDate.of(2026, 10, 10)
        val calendar = AcademicCalendar(
            listOf(
                AcademicDay(
                    id = "m",
                    semesterId = "s1",
                    date = saturday,
                    kind = AcademicDayKind.MAKEUP,
                    name = "调休",
                    sourceWeek = 3,
                    useCurrentWeekday = true,
                ),
            ),
        )
        val resolved = calendar.resolve(semester, saturday) as AcademicDaySchedule.Teaching
        assertEquals(3, resolved.week)
        assertEquals(6, resolved.weekday)
    }

    @Test
    fun `official 2026 holidays include national day and makeup`() {
        val days = OfficialPublicHolidays.inRange(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 10, 10))
        assertTrue(days.any { it.date == LocalDate.of(2026, 10, 1) && it.kind == AcademicDayKind.HOLIDAY && it.name == "国庆节" })
        assertTrue(days.any { it.date == LocalDate.of(2026, 9, 25) && it.name == "中秋节" })
        val makeup = days.single { it.date == LocalDate.of(2026, 10, 10) }
        assertEquals(AcademicDayKind.MAKEUP, makeup.kind)
        assertTrue(makeup.useCurrentWeek)
    }
}
