package cn.qztc.timetable.core.domain.model

import java.time.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ActivityPlanTest {
    @Test fun oldMemoJsonIsReadAndMultiplePointsKeepSeparateClocks() {
        val old=Json.decodeFromString<ActivityMemoTime>("""{"date":"2026-10-15","minute":540}""")
        assertFalse(old.remind)
        val memo=old.copy(additional=listOf(ActivityMemoTime(old.date,600),ActivityMemoTime(old.date!!.plusDays(2))),remind=true)
        val restored=Json.decodeFromString<ActivityMemoTime>(Json.encodeToString(memo))
        assertEquals(memo,restored)
        assertEquals(listOf(540,600),restored.onDate(old.date!!).map {it.minute})
        assertEquals(listOf(old.date,old.date!!.plusDays(2)),restored.dates())
    }
    @Test fun weeklyDatesUseSavedSemesterAnchorAndNeverFillTheGap() {
        val anchor=LocalDate.of(2026,8,31)
        val recurrence=ActivityWeekRecurrence("term","秋季",anchor,listOf(1,3),listOf(1,5),480)
        val memo=ActivityMemoTime(recurrence=recurrence)
        assertEquals(listOf(anchor,anchor.plusDays(4),anchor.plusDays(14),anchor.plusDays(18)),memo.dates())
        assertTrue(memo.onDate(anchor.plusDays(7)).isEmpty())
        assertTrue(memo.label().contains("1、3"))
    }
    @Test fun multipleRangesDeduplicateDatesButPreserveDistinctAppointments() {
        val first=LocalDate.of(2026,10,15)
        val memo=ActivityMemoTime(first,540,first.plusDays(1),600,additional=listOf(ActivityMemoTime(first.plusDays(1),660,first.plusDays(2),720)))
        assertEquals(3,memo.dates().size)
        assertEquals(2,memo.onDate(first.plusDays(1)).size)
    }
}
