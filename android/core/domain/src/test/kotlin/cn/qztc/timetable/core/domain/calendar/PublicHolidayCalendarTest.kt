package cn.qztc.timetable.core.domain.calendar

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicHolidayCalendarTest {
    private val first = LocalDate.of(2026, 10, 1)
    private val last = LocalDate.of(2026, 10, 31)
    private val shanghai = ZoneId.of("Asia/Shanghai")
    private fun millis(date: LocalDate, zone: ZoneId) = date.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test fun `timed midnight end does not import next day`() {
        val days = PublicHolidayCalendar.datesCovered(millis(first, shanghai), millis(first.plusDays(2), shanghai), false, first, last, shanghai)
        assertEquals(listOf(first, first.plusDays(1)), days)
    }
    @Test fun `all day dates use UTC even in negative offset timezone`() {
        val days = PublicHolidayCalendar.datesCovered(millis(first, ZoneOffset.UTC), millis(first.plusDays(7), ZoneOffset.UTC), true, first, last, ZoneId.of("America/Los_Angeles"))
        assertEquals(7, days.size)
        assertEquals(first, days.first())
        assertEquals(first.plusDays(6), days.last())
    }
    @Test fun `events are clipped before enumerating dates`() {
        val days = PublicHolidayCalendar.datesCovered(millis(first.minusYears(30), ZoneOffset.UTC), millis(last.plusYears(30), ZoneOffset.UTC), true, first, last, shanghai)
        assertEquals(31, days.size)
        assertTrue(PublicHolidayCalendar.datesCovered(0, 1, true, last, first, shanghai).isEmpty())
    }
    @Test fun `zero duration events still cover their date`() {
        val time = millis(first, shanghai) + 3_600_000
        assertEquals(listOf(first), PublicHolidayCalendar.datesCovered(time, time, false, first, last, shanghai))
    }
    @Test fun `manual choices survive repeated imports and work marker wins incoming conflict`() {
        val holiday = AcademicDay("a", "s", first, AcademicDayKind.HOLIDAY, "国庆节")
        val makeup = holiday.copy(kind = AcademicDayKind.MAKEUP, name = "国庆调休", sourceWeekday = 3, useCurrentWeek = true)
        assertEquals(listOf(makeup), PublicHolidayCalendar.merge(listOf(makeup), listOf(holiday)))
        val imported = PublicHolidayCalendar.merge(emptyList(), listOf(holiday, makeup))
        assertEquals(listOf(makeup), imported)
        assertEquals(imported, PublicHolidayCalendar.merge(imported, imported))
    }
    @Test fun `unsupported years do not fabricate holiday dates`() {
        assertTrue(OfficialPublicHolidays.inRange(LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 31)).isEmpty())
    }
    @Test fun `previous academic year includes 2025 national holidays and makeup`() {
        val days = OfficialPublicHolidays.inRange(LocalDate.of(2025, 9, 1), LocalDate.of(2025, 10, 31))
        assertEquals(8, days.count { it.kind == AcademicDayKind.HOLIDAY })
        assertEquals(listOf(LocalDate.of(2025, 9, 28), LocalDate.of(2025, 10, 11)), days.filter { it.kind == AcademicDayKind.MAKEUP }.map { it.date })
    }
}
