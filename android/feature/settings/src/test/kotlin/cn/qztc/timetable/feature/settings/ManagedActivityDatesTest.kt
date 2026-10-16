package cn.qztc.timetable.feature.settings

import cn.qztc.timetable.core.domain.calendar.*
import cn.qztc.timetable.core.domain.model.*
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class ManagedActivityDatesTest {
    @Test fun endlessPlansDoNotExpireAndTodaysElapsedClockStaysActive() {
        val date=LocalDate.of(2026,10,15)
        val activity=ScheduleActivity("memo","calendar","计划",kind=ActivityKind.DAY,memoTime=ActivityMemoTime(date,0))
        assertEquals(date.atTime(LocalTime.MAX),managedActivityEnd(activity,null))
        assertNull(managedActivityEnd(activity.copy(memoTime=ActivityMemoTime(date,repeat=ActivityRepeatRule(ActivityRepeatKind.DAILY))),null))
        val until=date.plusDays(10)
        assertEquals(until.atTime(LocalTime.MAX),managedActivityEnd(activity.copy(memoTime=ActivityMemoTime(date,repeat=ActivityRepeatRule(ActivityRepeatKind.DAILY,until=until))),null))
    }
    @Test fun nationalDayLeaveIsOneRangeDespiteMixedSemesterAndSourceRules() {
        val days=(1..7).map {day ->AcademicDay("day$day",if(day<4)"school" else "term",LocalDate.of(2026,10,day),
            AcademicDayKind.HOLIDAY,"国庆节",sourceWeek=day,useCurrentWeek=day%2==0)} +
            AcademicDay("makeup","term",LocalDate.of(2026,10,10),AcademicDayKind.MAKEUP,"国庆调休",useCurrentWeek=true)
        val ranges=managedSchoolDayRanges(days)
        assertEquals(2,ranges.size)
        assertEquals(7,ranges.first().days.size)
        assertEquals(AcademicDayKind.MAKEUP,ranges.last().days.single().kind)
    }
    @Test fun recurringMemoDatesIgnoreTheCurrentlyViewedSemester() {
        val date=LocalDate.of(2026,8,31)
        val memo=ActivityMemoTime(recurrence=ActivityWeekRecurrence("original","秋季",date,listOf(1,3),listOf(2),540))
        val activity=ScheduleActivity("memo","calendar","计划",kind=ActivityKind.DAY,memoTime=memo)
        val other=Semester("other","其他",2027,Term.SPRING,LocalDate.of(2027,2,1))
        assertEquals(listOf(date.plusDays(1),date.plusDays(15)),managedActivityDates(activity,other))
    }
    @Test fun expiryUsesTheLastPointOrRangeAndPreservesUndatedPlans() {
        val date=LocalDate.of(2026,10,15)
        val memo=ActivityMemoTime(date,540,additional=listOf(ActivityMemoTime(date.plusDays(2),600,date.plusDays(3),720)))
        val activity=ScheduleActivity("memo","calendar","计划",kind=ActivityKind.DAY,memoTime=memo)
        assertEquals(date.plusDays(3).atTime(LocalTime.MAX),managedActivityEnd(activity,null))
        assertEquals(date.atTime(LocalTime.MAX),managedActivityEnd(activity.copy(memoTime=ActivityMemoTime(date)),null))
        assertNull(managedActivityEnd(activity.copy(memoTime=ActivityMemoTime()),null))
    }
    @Test fun aWeeklyPlanExpiresAfterItsFinalSelectedWeekAndWeekday() {
        val monday=LocalDate.of(2026,8,31)
        val activity=ScheduleActivity("memo","calendar","计划",kind=ActivityKind.DAY,
            memoTime=ActivityMemoTime(recurrence=ActivityWeekRecurrence("term","秋季",monday,listOf(1,3),listOf(1,5),600)))
        assertEquals(monday.plusDays(18).atTime(LocalTime.MAX),managedActivityEnd(activity,null))
    }
}
