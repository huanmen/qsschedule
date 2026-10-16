package cn.qztc.timetable

import android.app.NotificationManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkInfo
import androidx.work.workDataOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cn.qztc.timetable.core.domain.model.DailyAgendaSettings
import cn.qztc.timetable.core.domain.model.ReminderDisplayMode
import cn.qztc.timetable.core.ui.theme.QztcTheme
import cn.qztc.timetable.feature.settings.ReminderSettingsScreen
import cn.qztc.timetable.feature.settings.ReminderTestState
import cn.qztc.timetable.feature.timetable.CourseReminderUiState
import cn.qztc.timetable.notifications.QztcNotifications
import cn.qztc.timetable.reminder.DailyAgendaWorker
import cn.qztc.timetable.reminder.DailyAgendaScheduler
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic UI state; does not modify the user's real reminder preferences or schedule. */
class DailyAgendaAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<FeatureAcceptanceActivity>()

    @Test fun customizeWeekdaysTimeAndPreviewNotification() {
        val options = mutableStateOf(DailyAgendaSettings())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var sent = false
        compose.setContent { QztcTheme {
            ReminderSettingsScreen(CourseReminderUiState(), emptyList(), "测试学期", {}, {}, {}, {}, ReminderTestState(), {}, {}, { _, _, _ -> },
                dailyAgenda = options.value, onDailyAgendaChange = { options.value = it },
                onPreviewDailyAgenda = { sent = QztcNotifications.notifyDailyAgenda(context, "今日待办测试", "全天  合成报名截止", ReminderDisplayMode.NOTIFICATION_BAR) })
        } }
        compose.onNodeWithTag("daily-agenda-enabled").performClick()
        compose.onNodeWithText("日", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("daily-agenda-time").performClick()
        compose.onNodeWithText("每日通知时间").assertIsDisplayed()
        compose.onNodeWithText("确定").performClick()
        compose.runOnIdle {
            assertTrue(options.value.enabled)
            assertFalse(7 in options.value.weekdays)
            assertEquals(7, options.value.hour)
            assertEquals(30, options.value.minute)
        }
        try {
            compose.onNodeWithTag("daily-agenda-preview").performScrollTo().performClick()
            compose.runOnIdle { assertTrue("System notification was rejected", sent) }
            val notification = context.getSystemService(NotificationManager::class.java).activeNotifications.single { it.id == 73001 }
            assertEquals("今日待办测试", notification.notification.extras.getString("android.title"))
            assertTrue(notification.notification.extras.getCharSequence("android.bigText").toString().contains("合成报名截止"))
        } finally { context.getSystemService(NotificationManager::class.java).cancel(73001) }
    }

}

/** Worker and persistence checks run without launching a test activity. */
class DailyAgendaWorkerAcceptanceTest {
    @Test fun allAgendaPreferencesRoundTripAndOriginalSettingsAreRestored() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AppPreferencesStore(context.qztcDataStore)
        val original = store.preferences.first().dailyAgenda
        val custom = DailyAgendaSettings(hour = 6, minute = 45, weekdays = setOf(1, 3, 5), includeActivities = false,
            includeCourses = true, includeCompetitions = false, includeExams = true, notifyEmpty = true, maximumItems = 18,
            displayMode = ReminderDisplayMode.SCREEN_POPUP)
        try { store.setDailyAgenda(custom); assertEquals(custom, store.preferences.first().dailyAgenda) }
        finally { store.setDailyAgenda(original) }
    }

    @Test fun backgroundWorkerSendsOnceAndQueuesNextRun() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AppPreferencesStore(context.qztcDataStore)
        val original = store.preferences.first().dailyAgenda
        val preferences = context.getSharedPreferences("daily-agenda", android.content.Context.MODE_PRIVATE)
        val oldSent = preferences.getString("lastSent", null)
        val manager = WorkManager.getInstance(context)
        val testRequest = OneTimeWorkRequestBuilder<DailyAgendaWorker>()
            .setInputData(workDataOf("date" to LocalDate.now().toString())).build()
        try {
            store.setDailyAgenda(DailyAgendaSettings(enabled = true, notifyEmpty = true,
                includeActivities = false, includeCompetitions = false, includeExams = false))
            preferences.edit().remove("lastSent").commit()
            manager.enqueue(testRequest).result.get(15, TimeUnit.SECONDS)
            val deadline = System.currentTimeMillis() + 20000
            var state = manager.getWorkInfoById(testRequest.id).get().state
            while (!state.isFinished && System.currentTimeMillis() < deadline) {
                kotlinx.coroutines.delay(250)
                state = manager.getWorkInfoById(testRequest.id).get().state
            }
            assertEquals(WorkInfo.State.SUCCEEDED, state)
            assertEquals(LocalDate.now().toString(), preferences.getString("lastSent", null))
            val notification = context.getSystemService(NotificationManager::class.java).activeNotifications.single { it.id == 73001 }
            assertEquals("今日待办 · 0 项", notification.notification.extras.getString("android.title"))
            assertTrue(manager.getWorkInfosForUniqueWork("daily-agenda:${LocalDate.now().plusDays(1)}").get()
                .any { it.state == WorkInfo.State.ENQUEUED })
        } finally {
            manager.cancelWorkById(testRequest.id).result.get()
            manager.cancelAllWorkByTag("daily-agenda").result.get()
            store.setDailyAgenda(original)
            preferences.edit().putString("lastSent", oldSent).remove("settings").commit()
            DailyAgendaScheduler.reschedule(context, original)
            context.getSystemService(NotificationManager::class.java).cancel(73001)
        }
    }
}
