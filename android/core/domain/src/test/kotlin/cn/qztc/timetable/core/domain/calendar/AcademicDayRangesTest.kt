package cn.qztc.timetable.core.domain.calendar

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class AcademicDayRangesTest {
    private fun day(date: String, name: String = "国庆节") = AcademicDay("id:$date","s",LocalDate.parse(date),AcademicDayKind.HOLIDAY,name)

    @Test fun `legacy shared holiday name becomes national day except on mid autumn`() {
        val dates = (1..8).map { day("2025-10-0$it", "国庆中秋") }
        val groups = AcademicDayRanges.group(dates)
        assertEquals(listOf("国庆节","中秋节","国庆节"),groups.map { it.name })
        assertEquals(listOf(5,1,2),groups.map { it.days.size })
        assertEquals("中秋节",AcademicDayRanges.normalize(listOf(day("2025-10-06"),day("2025-10-06","中秋节"))).single().name)
    }

    @Test fun `editing a range changes every day and preserves unrelated dates`() {
        val original = listOf(day("2026-10-01"),day("2026-10-02"))
        val next = AcademicDayRanges.replace(original+day("2026-09-25","中秋节"),original,
            original.first().copy(date=LocalDate.parse("2026-10-02"),name="学校放假"),LocalDate.parse("2026-10-04"))
        assertEquals(listOf("2026-09-25","2026-10-02","2026-10-03","2026-10-04"),next.map { it.date.toString() })
        assertTrue(next.filter { it.date.monthValue==10 }.all { it.name=="学校放假" })
    }

    @Test fun `different makeup teaching rules are separate even on adjacent days`() {
        val first=day("2026-09-20","调休").copy(kind=AcademicDayKind.MAKEUP,sourceWeekday=1,useCurrentWeek=true)
        val next=first.copy(id="next",date=first.date.plusDays(1),sourceWeekday=2)
        assertEquals(2,AcademicDayRanges.group(listOf(first,next)).size)
    }
}
