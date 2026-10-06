package cn.qztc.timetable.widget

import cn.qztc.timetable.core.domain.calendar.AcademicCalendar
import cn.qztc.timetable.core.domain.calendar.AcademicDayKind
import cn.qztc.timetable.core.domain.calendar.OfficialPublicHolidays
import cn.qztc.timetable.core.domain.model.ClassOccurrence
import cn.qztc.timetable.core.domain.model.Semester
import cn.qztc.timetable.core.domain.schedule.OccurrenceExpander
import cn.qztc.timetable.core.domain.schedule.TimetableState
import java.time.LocalDate

internal data class WidgetDaySchedule(
    val occurrences: List<ClassOccurrence>,
    val holidayName: String?,
)

/** Both widgets follow the applied timetable, including moves and calendar makeup days. */
internal fun widgetDaySchedule(
    semester: Semester,
    state: TimetableState,
    calendar: AcademicCalendar,
    date: LocalDate,
): WidgetDaySchedule = WidgetDaySchedule(
    occurrences = OccurrenceExpander.effective(semester, state, calendar)
        .filter { it.date == date && it.isActive },
    holidayName = widgetHolidayName(calendar, date),
)

/** A badge does not opt a holiday into the timetable or silently cancel courses. */
internal fun widgetHolidayName(calendar: AcademicCalendar, date: LocalDate): String? =
    (calendar.dayOn(date) ?: OfficialPublicHolidays.inRange(date, date).firstOrNull())
        ?.takeIf { it.kind == AcademicDayKind.HOLIDAY }
        ?.name?.ifBlank { "放假" }
