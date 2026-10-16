package cn.qztc.timetable.feature.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cn.qztc.timetable.core.domain.calendar.AcademicDayKind
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfficialHolidayApiTest {
    private val body = """{"year":2026,"days":[{"date":"2026-10-01","name":"国庆节","isOffDay":true},{"date":"2026-10-10","name":"国庆节","isOffDay":false}]}"""

    @Test fun publishedLeaveAndMakeupHaveSameSemanticsAfterCacheReload() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val days = OfficialHolidayApi.parse(body, 2026, "semester")
        assertEquals(AcademicDayKind.HOLIDAY, days.first().kind)
        assertEquals(AcademicDayKind.MAKEUP, days.last().kind)
        assertTrue(days.last().useCurrentWeek)
        context.getSharedPreferences("official-holidays", Context.MODE_PRIVATE).edit()
            .putString("year:2026", body).commit()
        assertEquals(days, OfficialHolidayApi.cached(context, 2026, "semester"))
    }

    @Test fun wrongYearUnpublishedAndMalformedDataAreRejected() {
        assertTrue(runCatching { OfficialHolidayApi.parse(body, 2025, "test") }.isFailure)
        assertTrue(runCatching { OfficialHolidayApi.parse("""{"year":2027,"days":[]}""", 2027, "test") }.isFailure)
        assertTrue(runCatching { OfficialHolidayApi.parse(body.replace("2026-10-01", "2026-02-30"), 2026, "test") }.isFailure)
        assertTrue(runCatching { OfficialHolidayApi.parse(body.replace("false", "null"), 2026, "test") }.isFailure)
    }

    @Test fun existingAppCacheRestoresMakeupWeekBehavior() {
        val old = """{"days":[{"date":"2026-10-10","name":"国庆节","kind":"MAKEUP"}]}"""
        assertTrue(OfficialHolidayApi.parse(old, 2026, "test").single().useCurrentWeek)
    }

    @Test fun actualPublic2026FeedPreservesAllMakeupsThroughGroupingAndImport() {
        val feed=javaClass.getResourceAsStream("/holiday-cn-2026.json")!!.bufferedReader().use {it.readText()}
        val days=OfficialHolidayApi.parse(feed,2026,"term")
        val makeups=days.filter {it.kind==AcademicDayKind.MAKEUP}
        assertEquals(listOf("2026-01-04","2026-02-14","2026-02-28","2026-05-09","2026-09-20","2026-10-10"),makeups.map {it.date.toString()})
        val imported=buildCalendarHolidaySchedules(days).flatMap(::calendarScheduleDays)
        assertEquals(makeups.map {it.date}.toSet(),imported.filter {it.kind==AcademicDayKind.MAKEUP}.map {it.date}.toSet())
        assertTrue(imported.filter {it.kind==AcademicDayKind.MAKEUP}.all {it.useCurrentWeek})
        val context=ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("official-holidays",0).edit().putString("year:2026",feed).commit()
        assertEquals(days,OfficialHolidayApi.cached(context,2026,"term"))
    }
}
