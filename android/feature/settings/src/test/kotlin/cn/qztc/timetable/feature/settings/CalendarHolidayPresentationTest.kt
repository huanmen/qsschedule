package cn.qztc.timetable.feature.settings

import cn.qztc.timetable.core.domain.calendar.AcademicDay
import cn.qztc.timetable.core.domain.calendar.AcademicDayKind
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarHolidayPresentationTest {
    @Test fun `included holiday is not repeated in pending suggestions`() {
        val holiday = AcademicDay("holiday", "semester", LocalDate.of(2026, 10, 1), AcademicDayKind.HOLIDAY, "国庆节")
        assertTrue(buildPendingHolidaySuggestions(listOf(holiday), listOf(holiday.copy(id = "fetched"))).isEmpty())
        assertEquals(1, buildPendingHolidaySuggestions(emptyList(), listOf(holiday)).size)
    }

    @Test fun `new makeup remains pending without duplicating already saved dates`() {
        val holiday = AcademicDay("holiday", "semester", LocalDate.of(2026, 10, 1), AcademicDayKind.HOLIDAY, "国庆节")
        val makeup = AcademicDay("makeup", "semester", LocalDate.of(2026, 10, 10), AcademicDayKind.MAKEUP, "国庆调休")
        val suggestion = buildPendingHolidaySuggestions(listOf(holiday), listOf(holiday, makeup)).single()
        assertEquals(listOf(makeup), suggestion.pendingDays)
        assertEquals("国庆节", suggestion.schedule.holiday?.name)
    }

    @Test fun `named national makeup is not attached to mid autumn`() {
        fun day(date: String, name: String, kind: AcademicDayKind) = AcademicDay(date,"test",LocalDate.parse(date),kind,name)
        val days = listOf(day("2026-09-25","中秋节",AcademicDayKind.HOLIDAY),day("2026-10-01","国庆节",AcademicDayKind.HOLIDAY),
            day("2026-09-20","国庆调休",AcademicDayKind.MAKEUP))
        val groups = buildCalendarHolidaySchedules(days)
        assertTrue(groups.first { it.holiday?.name == "中秋节" }.makeup.isEmpty())
        assertEquals(1,groups.first { it.holiday?.name == "国庆节" }.makeup.size)
    }
    @Test
    fun `holiday and its makeup dates are presented as one lockable suggestion`() {
        val semesterId = "semester"
        val holidays = (1..5).map { day ->
            AcademicDay(
                id = "holiday-$day",
                semesterId = semesterId,
                date = LocalDate.of(2026, 5, day),
                kind = AcademicDayKind.HOLIDAY,
                name = "劳动节",
            )
        }
        val makeups = listOf(
            LocalDate.of(2026, 4, 26),
            LocalDate.of(2026, 5, 9),
        ).map { date ->
            AcademicDay(
                id = "makeup-$date",
                semesterId = semesterId,
                date = date,
                kind = AcademicDayKind.MAKEUP,
                name = "劳动节调休",
                useCurrentWeek = true,
            )
        }
        val officialDays = holidays + makeups

        val suggestions = buildPendingHolidaySuggestions(emptyList(), officialDays)

        assertEquals(1, suggestions.size)
        val suggestion = suggestions.single()
        assertEquals("劳动节", suggestion.schedule.holiday?.name)
        assertEquals(2, suggestion.schedule.makeup.size)
        assertEquals(officialDays.size, suggestion.pendingDays.size)
        assertFalse(isHolidaySuggestionLocked(emptySet(), suggestion))
        assertTrue(isHolidaySuggestionLocked(officialDays.map(::academicDayKey).toSet(), suggestion))
    }

    @Test
    fun `unmatched makeup range attaches to the nearest holiday instead of becoming a separate item`() {
        val holiday = AcademicDay(
            id = "holiday",
            semesterId = "semester",
            date = LocalDate.of(2026, 10, 1),
            kind = AcademicDayKind.HOLIDAY,
            name = "国庆节",
        )
        val makeup = AcademicDay(
            id = "makeup",
            semesterId = "semester",
            date = LocalDate.of(2026, 10, 10),
            kind = AcademicDayKind.MAKEUP,
            name = "调休",
        )

        val schedules = buildCalendarHolidaySchedules(listOf(holiday, makeup))

        assertEquals(1, schedules.size)
        assertEquals(holiday.date, schedules.single().holiday?.start)
        assertEquals(makeup.date, schedules.single().makeup.single().start)
    }
}
