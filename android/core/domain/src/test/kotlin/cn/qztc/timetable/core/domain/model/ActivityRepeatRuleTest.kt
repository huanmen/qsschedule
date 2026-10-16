package cn.qztc.timetable.core.domain.model

import java.time.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ActivityRepeatRuleTest {
    private val day=LocalDate.of(2026,10,15)
    @Test fun dailyAndIntervalKeepAnchorAcrossQueryWindows() {
        val rule=ActivityRepeatRule(ActivityRepeatKind.INTERVAL,intervalDays=3)
        assertEquals(listOf(day.plusDays(3),day.plusDays(6)),rule.dates(day,day.plusDays(1),day.plusDays(8)))
        val memo=ActivityMemoTime(day,540,repeat=rule)
        assertTrue(memo.onDate(day.plusDays(1)).isEmpty())
        assertEquals(1,memo.onDate(day.plusDays(3)).size)
        assertEquals(memo,Json.decodeFromString<ActivityMemoTime>(Json.encodeToString(memo)))
    }
    @Test fun monthlySkipsMissingDayWithoutDriftingAndHonorsRepeatEnd() {
        val anchor=LocalDate.of(2026,1,31)
        val rule=ActivityRepeatRule(ActivityRepeatKind.MONTHLY,until=LocalDate.of(2026,4,30))
        assertEquals(listOf(anchor,LocalDate.of(2026,3,31)),rule.dates(anchor,anchor,anchor.plusMonths(5)))
    }
    @Test fun weeklySelectionAndOverlappingRepeatingRangesAreKept() {
        val rule=ActivityRepeatRule(ActivityRepeatKind.WEEKLY,listOf(1,5))
        assertEquals(listOf(day.plusDays(1),day.plusDays(4),day.plusDays(8)),rule.dates(day,day,day.plusDays(8)))
        val memo=ActivityMemoTime(day,endDate=day.plusDays(2),repeat=ActivityRepeatRule(ActivityRepeatKind.DAILY))
        assertEquals(3,memo.onDate(day.plusDays(2)).size)
    }
    @Test fun aPastFixedPointIsNeverDroppedByDefaultExpansion() {
        val old=ActivityMemoTime(LocalDate.of(2020,1,1))
        assertEquals(listOf(old.date),old.dates())
        assertEquals(1,old.onDate(old.date!!).size)
    }
}
