package cn.qztc.timetable.core.domain.schedule

import cn.qztc.timetable.core.domain.calendar.AcademicDay
import cn.qztc.timetable.core.domain.calendar.AcademicDayKind
import cn.qztc.timetable.core.domain.model.ScheduleActivity
import java.time.LocalDate

/** The persisted calendar import ID identifies origin; an activity's title does not. */
object CalendarActivityDisplay {
    fun isSchoolImport(id: String) = id.startsWith("calendar-event:")
    fun inTimetable(activity: ScheduleActivity) = isSchoolImport(activity.id) && activity.calendarTimetable && !activity.hiddenFromSchedule

    fun courseAlpha(date: LocalDate, week: Int, weekday: Int,
                    days: List<AcademicDay>, activities: List<ScheduleActivity>): Float {
        if (days.any { it.date == date && it.kind == AcademicDayKind.MAKEUP }) return 1f
        return if (days.any { it.date == date && it.kind == AcademicDayKind.HOLIDAY } ||
            activities.any { inTimetable(it) && it.appliesOn(week, weekday, date) }) .42f else 1f
    }
}
