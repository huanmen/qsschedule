package cn.qztc.timetable.reminder

import cn.qztc.timetable.core.domain.model.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class PluginReminderPolicyTest {
    @Test fun oldSettingsMigrateWithoutBringingCourseAndActivitySummariesBack() {
        val old = Json.decodeFromString<DailyAgendaSettings>("""{"enabled":true,"includeCourses":true,"includeActivities":true,"hour":8,"minute":15}""")
        val options = old.pluginAgenda("academic-records")
        assertFalse(options.includeCourses); assertFalse(options.includeActivities)
        assertEquals(8, options.hour); assertEquals(15, options.minute)
        assertTrue("updates" in old.forPlugin("academic-records").kinds)
    }
    @Test fun eachPluginKeepsItsOwnTypesTimeAndDisplayMode() {
        val options = DailyAgendaSettings(enabled = true, plugins = mapOf("academic-records" to
            PluginReminderSettings(hour = 19, minute = 45, weekdays = setOf(1), kinds = setOf("exam"), displayMode = ReminderDisplayMode.SCREEN_POPUP)))
        val effective = options.pluginAgenda("academic-records")
        assertFalse(effective.includeCompetitions); assertTrue(effective.includeExams)
        assertEquals(ReminderDisplayMode.SCREEN_POPUP, effective.displayMode)
        assertEquals(LocalDateTime.of(2026, 10, 19, 19, 45), nextDailyAgendaRun(effective, LocalDateTime.of(2026, 10, 17, 1, 0)))
        assertEquals(7, options.forPlugin("other").hour)
    }
    @Test fun bothSwitchesAndASelectedTypeAreRequiredToSchedule() {
        val settings = DailyAgendaSettings(enabled = true)
        assertNull(nextDailyAgendaRun(settings.copy(enabled = false).pluginAgenda("academic-records"), LocalDateTime.now()))
        assertFalse(settings.copy(plugins = mapOf("academic-records" to PluginReminderSettings(enabled = false))).pluginAgenda("academic-records").enabled)
        assertFalse(settings.copy(plugins = mapOf("academic-records" to PluginReminderSettings(kinds = emptySet()))).pluginAgenda("academic-records").enabled)
    }
}
