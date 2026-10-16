package cn.qztc.timetable.core.domain.model

import java.time.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ActivityBoundedRepeatTest {
    private val day=LocalDate.of(2026,10,16)
    @Test fun boundedDailyUsesWindowInsteadOfRepeatingTheWholeRange() {
        val memo=ActivityMemoTime(day,600,day.plusDays(3),540,repeat=ActivityRepeatRule(ActivityRepeatKind.DAILY,bounded=true))
        val slots=memo.slots(day,day.plusDays(20))
        assertEquals(listOf(day,day.plusDays(1),day.plusDays(2)),slots.map {it.date})
        assertTrue(slots.all {it.endDate==null&&it.minute==600})
        assertEquals(memo,Json.decodeFromString<ActivityMemoTime>(Json.encodeToString(memo)))
    }
    @Test fun allDayDailyIncludesEndDateAndMonthlyDoesNotDrift() {
        val daily=ActivityMemoTime(day,endDate=day.plusDays(2),repeat=ActivityRepeatRule(ActivityRepeatKind.DAILY,bounded=true))
        assertEquals(3,daily.slots(day,day.plusMonths(4)).size)
        assertTrue(daily.slots(day,day.plusDays(2)).all {it.minute==null&&it.endMinute==null})
        val monthly=ActivityMemoTime(day,600,day.plusMonths(2),599,repeat=ActivityRepeatRule(ActivityRepeatKind.MONTHLY,bounded=true))
        assertEquals(listOf(day,day.plusMonths(1)),monthly.slots(day,day.plusYears(1)).map {it.date})
    }
    @Test fun termWeeksRespectBothDatesAndEndMinute() {
        val monday=LocalDate.of(2026,10,12)
        val rule=ActivityWeekRecurrence("term","秋季",monday,listOf(1,2,3),listOf(1,5),600,
            until=day.plusWeeks(1),from=day,untilMinute=599)
        assertEquals(listOf(day,day.plusDays(3)),rule.slots().map {it.date})
    }
    @Test fun olderJsonKeepsLegacyRepeatingRangesAndDefaultsNewExclusions() {
        val rule=Json.decodeFromString<ActivityRepeatRule>("{\"kind\":\"DAILY\"}")
        assertFalse(rule.bounded)
        val memo=ActivityMemoTime(day,endDate=day.plusDays(2),repeat=rule)
        assertEquals(3,memo.onDate(day.plusDays(2)).size)
        assertTrue(Json.decodeFromString<ActivityMemoReminder>("{}").excludedOccurrenceDates.isEmpty())
    }
}
