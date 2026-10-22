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
    val plugins: Map<String, PluginReminderSettings> = emptyMap(),
) {
    fun normalized() = copy(hour = hour.coerceIn(0, 23), minute = minute.coerceIn(0, 59),
        weekdays = weekdays.filter { it in 1..7 }.toSet(), maximumItems = maximumItems.coerceIn(1, 30))

    fun forPlugin(id: String): PluginReminderSettings = plugins[id] ?: PluginReminderSettings(
        hour = hour, minute = minute, weekdays = weekdays, displayMode = displayMode,
        kinds = if (id == "academic-records") buildSet {
            if (includeCompetitions) add("competition")
            if (includeExams) add("exam")
            add("updates")
        } else setOf("information"),
    )

    fun pluginAgenda(id: String): DailyAgendaSettings {
        val plugin = forPlugin(id).normalized()
        return copy(enabled = enabled && plugin.enabled && plugin.kinds.isNotEmpty(), hour = plugin.hour,
            minute = plugin.minute, weekdays = plugin.weekdays, displayMode = plugin.displayMode,
            includeCourses = false, includeActivities = false, includeCompetitions = "competition" in plugin.kinds,
            includeExams = "exam" in plugin.kinds, notifyEmpty = false, maximumItems = 30)
    }
}

@Serializable
data class PluginReminderSettings(
    val enabled: Boolean = true,
    val hour: Int = 7,
    val minute: Int = 30,
    val weekdays: Set<Int> = (1..7).toSet(),
    val kinds: Set<String> = setOf("competition", "exam"),
    val displayMode: ReminderDisplayMode = ReminderDisplayMode.NOTIFICATION_BAR,
) {
    fun normalized() = copy(hour = hour.coerceIn(0, 23), minute = minute.coerceIn(0, 59), weekdays = weekdays.filter { it in 1..7 }.toSet())
}
