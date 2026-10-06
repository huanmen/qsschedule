package cn.qztc.timetable

import android.app.Notification
import android.app.NotificationManager
import android.os.SystemClock
import android.accessibilityservice.AccessibilityServiceInfo
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cn.qztc.timetable.core.domain.model.*
import cn.qztc.timetable.core.domain.schedule.TimetableState
import cn.qztc.timetable.core.ui.theme.QztcTheme
import cn.qztc.timetable.feature.timetable.*
import cn.qztc.timetable.notifications.QztcNotifications
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Uses synthetic course data only; never edits the user's timetable or preferences. */
class CourseReminderAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<FeatureAcceptanceActivity>()

    private fun state(): DemoScheduleState {
        val semester = Semester("reminder-ui", "合成学期", 2026, Term.AUTUMN, LocalDate.of(2026, 9, 28), 20)
        val course = Course("reminder-ui-course", semester.id, SourceType.QNU_EDU, "synthetic", name = "合成提醒课程")
        val meeting = CourseMeeting("reminder-ui-meeting", course.id, 1, 1, 2, WeekRule.of(listOf(1)), "测试教师", "测试教室", SourceType.QNU_EDU)
        return DemoScheduleState().apply {
            loadImported(ImportedSchedule(semester, PeriodSchemeFactory.forSemester(semester.id).periods, TimetableState(listOf(course), listOf(meeting))))
        }
    }

    @Test fun weekDetailSavesAndReopensCustomReminder() {
        val settings = mutableStateOf(CourseReminderUiState(enabled = true))
        val saved = AtomicReference<Triple<String, Int?, String?>>()
        val schedule = state()
        compose.setContent { QztcTheme {
            WeekScreen(schedule, onOpenDay = { _, _ -> }, initialWeek = 1, reminderState = settings.value,
                onSaveReminder = { id, minutes, message ->
                    saved.set(Triple(id, minutes, message))
                    settings.value = settings.value.copy(overrides = mapOf(id to (minutes ?: 15)), messages = mapOf(id to message.orEmpty()))
                })
        } }
        compose.onNodeWithText("合成提醒课程").performClick()
        compose.onNodeWithText("提醒设置").performScrollTo().performClick()
        compose.onNodeWithText("自定义").performScrollTo().performClick()
        compose.onNodeWithText("提前多少分钟").performTextReplacement("30")
        compose.onNodeWithText("确定").performClick()
        compose.onNodeWithText("额外提醒内容（可选）").performScrollTo().performTextInput("带实验报告\n提前交作业")
        compose.onNodeWithText("保存提醒设置").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(Triple("reminder-ui-course", 30, "带实验报告\n提前交作业"), saved.get()) }
        compose.onNodeWithText("提醒设置").performScrollTo().performClick()
        compose.onNodeWithText("带实验报告\n提前交作业").performScrollTo().assertIsDisplayed()
    }

    @Test fun dayDetailCanDisableOneCourse() {
        val saved = AtomicReference<Triple<String, Int?, String?>>()
        val schedule = state()
        compose.setContent { QztcTheme {
            DayScheduleScreen(schedule, week = 1, day = 1, onBack = {}, reminderState = CourseReminderUiState(enabled = true),
                onSaveReminder = { id, minutes, message -> saved.set(Triple(id, minutes, message)) })
        } }
        compose.onNodeWithText("提醒设置").performScrollTo().performClick()
        compose.onNodeWithText("本课不提醒").performScrollTo().performClick()
        compose.onNodeWithText("保存提醒设置").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(Triple("reminder-ui-course", 0, null), saved.get()) }
    }

    @Test fun devicePostsBothChannelsWithCustomContent() {
        compose.setContent { QztcTheme { androidx.compose.material3.Text("通知验收（合成数据）") } }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("Device notification permission is required", QztcNotifications.canPost(context))
        val manager = context.getSystemService(NotificationManager::class.java)
        val marker = "合成提醒验收-0930"
        val popupId = 903001
        val barId = 903002
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val originalInfo = automation.serviceInfo
        try {
            automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
            QztcNotifications.notifyCourseReminder(context, marker, "带测试资料\n9月30日 08:20 · 测试教室", popupId, ReminderDisplayMode.SCREEN_POPUP)
            var bannerObserved = false
            val until = SystemClock.uptimeMillis() + 4000
            while (SystemClock.uptimeMillis() < until && !bannerObserved) {
                bannerObserved = automation.windows.any { window ->
                    val root = window.root
                    root?.packageName?.toString() == "com.android.systemui" && root.findAccessibilityNodeInfosByText(marker).isNotEmpty()
                }
                if (!bannerObserved) SystemClock.sleep(200)
            }
            println("COURSE_REMINDER_HEADS_UP_OBSERVED=$bannerObserved")
            val popup = manager.activeNotifications.single { it.id == popupId }.notification
            assertEquals("course_reminders_popup", popup.channelId)
            assertEquals(NotificationManager.IMPORTANCE_HIGH, manager.getNotificationChannel(popup.channelId).importance)
            assertTrue(popup.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains("带测试资料"))
            QztcNotifications.notifyCourseReminder(context, "合成通知栏验收", "测试内容", barId, ReminderDisplayMode.NOTIFICATION_BAR)
            SystemClock.sleep(500)
            val bar = manager.activeNotifications.single { it.id == barId }.notification
            assertEquals("course_reminders", bar.channelId)
            assertNotEquals(bar.channelId, popup.channelId)
        } finally {
            manager.cancel(popupId)
            manager.cancel(barId)
            automation.serviceInfo = originalInfo
        }
    }
}
