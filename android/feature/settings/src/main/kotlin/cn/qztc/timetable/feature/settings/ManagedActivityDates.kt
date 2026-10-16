package cn.qztc.timetable.feature.settings

import cn.qztc.timetable.core.domain.calendar.*
import cn.qztc.timetable.core.domain.model.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Display grouping ignores the provenance of leave days, while preserving different makeup rules. */
internal fun managedSchoolDayRanges(days: List<AcademicDay>): List<AcademicDayRange> = AcademicDayRanges.group(days.map {
    if(it.kind==AcademicDayKind.HOLIDAY) it.copy(semesterId="",sourceWeek=null,sourceWeekday=null,useCurrentWeek=false,useCurrentWeekday=false)
    else it
})

internal fun managedActivityDates(activity: ScheduleActivity, term: Semester?): List<LocalDate> =
    activity.memoTime?.dates() ?: activity.effectiveSessions().flatMap { session ->
        session.date?.let(::listOf) ?: term?.takeIf { it.id==activity.semesterId }?.let { semester ->
            session.weeks.map { SemesterCalendar.dateOf(semester,it,session.weekday?:1) }
        }.orEmpty()
    }.distinct().sorted()

/** Undated plans stay active; repeated plans expire only after their final occurrence. */
internal fun managedActivityEnd(activity: ScheduleActivity, term: Semester?): LocalDateTime? =
    if(activity.memoTime?.let {memo ->memo.repeat?.let {it.until==null&&(!it.bounded||memo.endDate==null)}}==true)null else
    activity.memoTime?.let { memo ->
        val end=memo.repeat?.until
        memo.slots(end?.minusDays(3700),end).maxOfOrNull {slot ->
            (slot.endDate ?: slot.date!!).atTime(LocalTime.MAX)
        }
    } ?: managedActivityDates(activity,term).maxOrNull()?.atTime(LocalTime.MAX)
