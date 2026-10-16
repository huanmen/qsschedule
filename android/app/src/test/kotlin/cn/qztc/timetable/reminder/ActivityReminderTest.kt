package cn.qztc.timetable.reminder

import cn.qztc.timetable.core.domain.model.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ActivityReminderTest {
    @Test fun excludingRepeatedWeekdayAlsoSkipsItsAdvanceReminder() {
        val day=LocalDate.of(2026,10,16)
        val memo=ActivityMemoTime(day,endDate=day.plusDays(10),repeat=ActivityRepeatRule(ActivityRepeatKind.WEEKLY,listOf(1,5),bounded=true),
            remind=true,reminder=ActivityMemoReminder(daysBefore=2,excludedOccurrenceWeekdays=listOf(5)))
        val activity=ScheduleActivity("week-exclusion","calendar","周计划",kind=ActivityKind.DAY,memoTime=memo)
        assertTrue(activityReminderMoments(listOf(activity),day).all {it.occurrenceDate.dayOfWeek.value==1})
        assertEquals(2,activityReminderMoments(listOf(activity),day).size)
    }
    @Test fun excludedOccurrenceUsesEventDateNotTheEarlierSendingDate() {
        val date=LocalDate.of(2026,10,16)
        val memo=ActivityMemoTime(date,endDate=date.plusDays(2),repeat=ActivityRepeatRule(ActivityRepeatKind.DAILY,bounded=true),remind=true,
            reminder=ActivityMemoReminder(daysBefore=2,excludedOccurrenceDates=listOf(date.plusDays(1).toString())))
        val activity=ScheduleActivity("excluded","calendar","活动",kind=ActivityKind.DAY,memoTime=memo)
        val items=activityReminderMoments(listOf(activity),date)
        assertEquals(listOf(date,date.plusDays(2)),items.map {it.occurrenceDate})
        assertEquals(listOf(date.minusDays(2),date),items.map {it.at.toLocalDate()})
    }
    @Test fun aRepeatStartingFarInTheFutureStillSchedulesItsFirstReminder() {
        val today=LocalDate.of(2026,10,15)
        val first=today.plusYears(20)
        val memo=ActivityMemoTime(first,repeat=ActivityRepeatRule(ActivityRepeatKind.MONTHLY),remind=true,
            reminder=ActivityMemoReminder(daysBefore=2,minute=480))
        val activity=ScheduleActivity("future","calendar","未来计划",kind=ActivityKind.DAY,memoTime=memo)
        val next=activityReminderMoments(listOf(activity),today).first()
        assertEquals(first.minusDays(2).atTime(8,0),next.at)
        assertEquals(first,next.occurrenceDate)
    }
    @Test fun everyRepeatedOccurrenceHonorsAdvanceClockAndSkippedSendingDates() {
        val date=LocalDate.of(2026,10,15)
        val memo=ActivityMemoTime(date,840,repeat=ActivityRepeatRule(ActivityRepeatKind.DAILY,until=date.plusDays(3)),remind=true,
            reminder=ActivityMemoReminder(1,510,excludedWeekdays=listOf(6),excludedDates=listOf(date.toString()),displayMode=ReminderDisplayMode.SCREEN_POPUP))
        val activity=ScheduleActivity("repeat","calendar","只写标题",kind=ActivityKind.DAY,memoTime=memo)
        val items=activityReminderMoments(listOf(activity),date)
        assertEquals(listOf(date.minusDays(1),date.plusDays(1)),items.map {it.at.toLocalDate()})
        assertTrue(items.all {it.at.hour==8&&it.at.minute==30})
        assertEquals(listOf(date,date.plusDays(2)),items.map {it.occurrenceDate})
    }
    @Test fun eachSelectedWeekDayAndRangeBoundaryGetsAReminderAndUndatedDoesNot() {
        val date=LocalDate.of(2026,10,15)
        fun activity(id:String,memo:ActivityMemoTime)=ScheduleActivity(id,"calendar",id,kind=ActivityKind.DAY,memoTime=memo)
        val items=activityReminderMoments(listOf(
            activity("range",ActivityMemoTime(date,600,date.plusDays(1),660,remind=true)),
            activity("week",ActivityMemoTime(recurrence=ActivityWeekRecurrence("term","秋季",date.minusDays(3),listOf(1,3),listOf(1,5)),remind=true)),
            activity("off",ActivityMemoTime(date)),activity("undated",ActivityMemoTime(remind=true))))
        assertEquals(6,items.size)
        assertEquals(setOf("开始","结束"),items.filter {it.activity.id=="range"}.map {it.boundary}.toSet())
        assertTrue(items.filter {it.activity.id=="week"}.all {it.at.hour==9})
    }
}
