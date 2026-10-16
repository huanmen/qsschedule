package cn.qztc.timetable.feature.timetable

import cn.qztc.timetable.core.domain.model.ActivityMemoTime
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ActivityEditorSelectionTest {
    private val start=LocalDate.of(2026,10,19) // Monday
    @Test fun shrinkingEndTrimsWeeksWeekdaysAndBothKindsOfExcludedDates() {
        val result=trimActivityEditorSelection(listOf(ActivityMemoTime(start,600,start.plusDays(1),540)),"term",
            listOf(1,2,3),listOf(1,2,5),start.plusWeeks(3),start,false,1,2,listOf(1,2,5),
            listOf(start.minusDays(2).toString(),start.plusDays(2).toString()),
            listOf(start.toString(),start.plusDays(1).toString(),start.plusWeeks(1).toString()))
        assertEquals(listOf(1),result.weeks)
        assertEquals(listOf(1),result.weekdays)
        assertEquals(start.plusDays(1),result.until)
        assertEquals(listOf(1),result.excludedWeekdays)
        assertEquals(listOf(start.toString()),result.excludedOccurrences)
        assertEquals(listOf(start.minusDays(2).toString()),result.excludedDates)
    }
    @Test fun addingAnEndToWeeklyRuleDropsLaterWeekdaysButKeepsValidSelections() {
        val result=trimActivityEditorSelection(listOf(ActivityMemoTime(start,600,start.plusDays(1),660)),"weekly",
            emptyList(),listOf(1,2,5),null,null,false,1,0,listOf(2,5),emptyList(),listOf(start.plusDays(4).toString()))
        assertEquals(listOf(1,2),result.weekdays)
        assertEquals(listOf(2),result.excludedWeekdays)
        assertTrue(result.excludedOccurrences.isEmpty())
        assertNull(result.until)
    }
    @Test fun independentCustomPointsAreNotClippedToFirstSessionsEnd() {
        val later=start.plusDays(10)
        val result=trimActivityEditorSelection(listOf(ActivityMemoTime(start,endDate=start.plusDays(1)),ActivityMemoTime(later)),
            "points",emptyList(),emptyList(),null,null,true,1,0,emptyList(),emptyList(),listOf(later.toString()))
        assertEquals(listOf(later.toString()),result.excludedOccurrences)
    }
    @Test fun summaryIncludesStartOnceWhileKeepingSelectedRepeatDays() {
        val slot=ActivityMemoTime(start,600)
        val lines=activityRepeatSummary("weekly",emptyList(),listOf(5),listOf(slot),false,"1")
        assertEquals(listOf("开始日期：2026-10-19","星期五","10:00"),lines)
        val points=activityRepeatSummary("points",emptyList(),emptyList(),listOf(slot),false,"1")
        assertEquals(listOf(slot.label()),points)
    }
}
