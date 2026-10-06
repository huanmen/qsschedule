package cn.qztc.timetable.feature.settings

import java.time.LocalDate

internal object CalendarDefaultInclusion {
    fun includes(event: SchoolCalendarEvent, enrollmentYear: Int?, today: LocalDate = LocalDate.now()): Boolean = when (event.type) {
        SchoolCalendarEventType.TEACHING_START -> event.name.contains("正式上课")
        SchoolCalendarEventType.REPORTING -> if (enrollmentYear == today.year) {
            event.name.contains("新生") && event.name.contains("报到")
        } else {
            event.name.contains("学生") && event.name.contains("报到") && !event.name.contains("新生")
        }
        SchoolCalendarEventType.BREAK, SchoolCalendarEventType.SPORTS_MEETING -> true
        SchoolCalendarEventType.EXAM, SchoolCalendarEventType.OTHER -> false
    }
}
