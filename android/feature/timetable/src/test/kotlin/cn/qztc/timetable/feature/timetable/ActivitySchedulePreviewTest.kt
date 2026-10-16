package cn.qztc.timetable.feature.timetable

import cn.qztc.timetable.core.domain.model.*
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class ActivitySchedulePreviewTest {
    @Test fun previewRangeIncludesAdvanceRemindersAndRespectsEndAndSessions() {
        val start=LocalDate.of(2026,10,16)
        val memo=ActivityMemoTime(start,endDate=start.plusDays(20),repeat=ActivityRepeatRule(ActivityRepeatKind.DAILY,until=start.plusDays(3),bounded=true),
            remind=true,reminder=ActivityMemoReminder(daysBefore=2))
        assertEquals(start.minusDays(2) to start.plusDays(3),activityPreviewRange(memo,start))
        assertEquals(start.minusDays(2) to start.plusDays(1),activityPreviewRange(memo.copy(endDate=start.plusDays(1)),start))
        val open=memo.copy(endDate=null,repeat=ActivityRepeatRule(ActivityRepeatKind.DAILY),remind=false)
        assertEquals(start to start.plusYears(2),activityPreviewRange(open,start))
    }
    private val day=LocalDate.of(2026,10,16)
    @Test fun previewSeparatesOccurrenceAndAdvanceReminderDates() {
        val memo=ActivityMemoTime(day,endDate=day.plusDays(2),repeat=ActivityRepeatRule(ActivityRepeatKind.DAILY,bounded=true),
            remind=true,reminder=ActivityMemoReminder(daysBefore=2,excludedOccurrenceDates=listOf(day.plusDays(1).toString())))
        val flags=activityPreviewDays(memo,YearMonth.from(day))
        assertTrue(flags[day]!!.start&&flags[day]!!.repeats)
        assertTrue(flags[day.plusDays(2)]!!.end)
        assertTrue(flags[day.minusDays(2)]!!.reminder)
        assertFalse(flags[day.minusDays(1)]!!.reminder)
        assertTrue(flags[day.plusDays(1)]!!.excluded&&flags[day.plusDays(1)]!!.occurs)
        assertFalse(flags[day.plusDays(3)]!!.occurs)
    }
    @Test fun weekPreviewOnlyMarksGeneratedSelectedDaysAndReminders() {
        val rule=ActivityWeekRecurrence("t","秋季",day.minusDays(4),listOf(1,2),listOf(5),until=day.plusWeeks(1),from=day)
        val memo=ActivityMemoTime(recurrence=rule,remind=true,reminder=ActivityMemoReminder())
        val flags=activityPreviewDays(memo,YearMonth.from(day))
        assertTrue(flags[day]!!.repeats&&flags[day]!!.reminder)
        assertTrue(flags[day.plusWeeks(1)]!!.end)
        assertFalse(flags[day.plusDays(1)]!!.occurs)
    }
    @Test fun continuousRangeOnlyOffersItsActualReminderOccurrence() {
        val memo=ActivityMemoTime(day,endDate=day.plusDays(2),remind=true,reminder=ActivityMemoReminder())
        val flags=activityPreviewDays(memo,YearMonth.from(day))
        assertTrue(flags[day]!!.occurrenceStart)
        assertTrue(flags[day.plusDays(1)]!!.occurs)
        assertFalse(flags[day.plusDays(1)]!!.occurrenceStart)
        assertFalse(flags[day.plusDays(2)]!!.occurrenceStart)
    }
}
