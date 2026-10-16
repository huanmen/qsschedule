package cn.qztc.timetable.feature.settings

import java.time.LocalDate

internal object CalendarDefaultInclusion {
    fun includes(event: SchoolCalendarEvent, enrollmentYear: Int?, today: LocalDate = LocalDate.now()): Boolean = when (event.type) {
        SchoolCalendarEventType.BREAK, SchoolCalendarEventType.SPORTS_MEETING -> true
        else -> false
    }
}
