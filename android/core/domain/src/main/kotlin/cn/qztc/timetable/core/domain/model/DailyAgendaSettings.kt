package cn.qztc.timetable.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class DailyAgendaSettings(
    val enabled: Boolean = false,
    val hour: Int = 7,
    val minute: Int = 30,
    val weekdays: Set<Int> = (1..7).toSet(),
    val includeCourses: Boolean = false,
    val includeActivities: Boolean = true,
    val includeCompetitions: Boolean = true,
    val includeExams: Boolean = true,
    val notifyEmpty: Boolean = false,
    val displayMode: ReminderDisplayMode = ReminderDisplayMode.NOTIFICATION_BAR,
    val maximumItems: Int = 10,
) {
    fun normalized() = copy(hour = hour.coerceIn(0, 23), minute = minute.coerceIn(0, 59),
        weekdays = weekdays.filter { it in 1..7 }.toSet(), maximumItems = maximumItems.coerceIn(1, 30))
}
