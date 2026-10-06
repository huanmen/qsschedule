package cn.qztc.timetable

import cn.qztc.timetable.core.domain.model.HomePreference
import cn.qztc.timetable.core.domain.model.HomeView
import cn.qztc.timetable.core.domain.model.WeekendDisplayMode
import cn.qztc.timetable.core.domain.model.ReminderDisplayMode
import cn.qztc.timetable.core.ui.glass.GlassQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class AppPreferencesTest {
    @Test
    fun `weekend columns default to hidden when empty`() {
        assertEquals(WeekendDisplayMode.HIDE_EMPTY, AppPreferences().weekendDisplayMode)
    }

    @Test
    fun `fold empty weekend mode is restored from persisted value`() {
        assertEquals(WeekendDisplayMode.FOLD_EMPTY, "fold_empty".toWeekendDisplayMode())
    }

    @Test
    fun `today preference starts on today`() {
        assertEquals(
            TopDestination.Today,
            AppPreferences(homePreference = HomePreference.TODAY).startDestination(),
        )
    }

    @Test
    fun `week preference starts on week timetable`() {
        assertEquals(
            TopDestination.Week,
            AppPreferences(homePreference = HomePreference.WEEK).startDestination(),
        )
    }

    @Test
    fun `last preference restores the last timetable destination`() {
        assertEquals(
            TopDestination.Week,
            AppPreferences(
                homePreference = HomePreference.LAST,
                lastDestination = HomeView.WEEK,
            ).startDestination(),
        )
    }

    @Test
    fun `last preference never starts on mine`() {
        assertEquals(
            TopDestination.Today,
            AppPreferences(
                homePreference = HomePreference.LAST,
                lastDestination = HomeView.MINE,
            ).startDestination(),
        )
    }

    @Test
    fun `glass settings map persisted values and clamp invalid input`() {
        val settings = AppPreferences(
            glassQuality = GlassQuality.BLUR_TINT,
            functionalAlpha = 2f,
            functionalBlur = -3f,
            contentAlpha = .1f,
            contentBlur = 99f,
        ).glassSettings(apiLevel = 34, lowMemory = false)

        assertEquals(GlassQuality.BLUR_TINT, settings.quality)
        assertEquals(.80f, settings.functionalAlpha)
        assertEquals(0f, settings.functionalBlur.value)
        assertEquals(.20f, settings.contentAlpha)
        assertEquals(30f, settings.contentBlur.value)
    }

    @Test
    fun `reminders default to screen popup and notification bar remains selectable`() {
        assertEquals(ReminderDisplayMode.SCREEN_POPUP, AppPreferences().reminderDisplayMode)
        assertEquals(ReminderDisplayMode.NOTIFICATION_BAR, "notification_bar".toReminderDisplayMode())
    }

    @Test
    fun `custom reminder messages round trip with multiline safe storage`() {
        val stored = mapOf("course-1" to "带实验报告\n提前五分钟到") .toCourseReminderMessageText()
        assertEquals("带实验报告\n提前五分钟到", stored.toCourseReminderMessages()["course-1"])
    }
}
