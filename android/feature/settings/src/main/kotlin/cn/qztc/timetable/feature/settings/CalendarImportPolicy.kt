package cn.qztc.timetable.feature.settings

import cn.qztc.timetable.core.domain.model.ActivityKind
import cn.qztc.timetable.core.domain.model.ScheduleActivity

/** Official breaks contribute only their published boundaries, never every vacation day. */
fun calendarImportEvents(events: List<SchoolCalendarEvent>): List<SchoolCalendarEvent> = events.flatMap { event ->
    if (event.type != SchoolCalendarEventType.BREAK) listOf(event) else buildList {
        add(event.copy(name = "${event.name}开始", endDate = event.startDate))
        event.endDate?.takeIf { it != event.startDate }?.let { end ->
            add(event.copy(id = "${event.id}:end", name = "${event.name}结束", startDate = end, endDate = end))
        }
    }
}

fun SchoolCalendarEvent.toCalendarActivity(semesterId: String): ScheduleActivity = ScheduleActivity(
    id = "calendar-event:$semesterId:$id", semesterId = semesterId, name = name,
    note = detail, kind = ActivityKind.DAY, startDate = startDate, endDate = endDate ?: startDate,
    calendarTimetable = type == SchoolCalendarEventType.BREAK || type == SchoolCalendarEventType.SPORTS_MEETING,
)
