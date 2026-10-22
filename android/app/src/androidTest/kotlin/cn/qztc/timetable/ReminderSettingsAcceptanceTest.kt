package cn.qztc.timetable

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.qztc.timetable.core.domain.model.Course
import cn.qztc.timetable.core.domain.model.SourceType
import cn.qztc.timetable.core.domain.model.ReminderDisplayMode
import cn.qztc.timetable.core.ui.theme.QztcTheme
import cn.qztc.timetable.feature.timetable.CourseReminderUiState
import cn.qztc.timetable.feature.settings.ReminderSettingsScreen
import cn.qztc.timetable.feature.settings.ReminderTestState
import cn.qztc.timetable.update.AppUpdateClient
import cn.qztc.timetable.update.AppUpdateDialog
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated emulator UI checks; all courses and update metadata are synthetic. */
@RunWith(AndroidJUnit4::class)
class ReminderSettingsAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<FeatureAcceptanceActivity>()

    private fun screen(test: ReminderTestState = ReminderTestState(), onMinutes: (Int) -> Unit = {}, onMode: (ReminderDisplayMode) -> Unit = {}) {
        compose.setContent { QztcTheme {
            ReminderSettingsScreen(CourseReminderUiState(), emptyList(), "合成学期", {}, {}, onMinutes, onMode, test, {}, {}, { _, _, _ -> })
        } }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), name)
        file.outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun customMinutesRejectOutOfRangeAndSaveValidInput() {
        var saved = 0
        screen(onMinutes = { saved = it })
        compose.onNodeWithText("20 分钟").assertDoesNotExist()
        compose.onNodeWithText("自定义").performScrollTo().performClick()
        compose.onNodeWithText("提前多少分钟").performTextReplacement("61")
        compose.onNodeWithText("确定").assertIsNotEnabled()
        compose.onNodeWithText("提前多少分钟").performTextReplacement("25")
        compose.onNodeWithText("确定").performClick()
        compose.runOnIdle { assertEquals(25, saved) }
    }

    @Test fun modeClickTriggersCallbackAndSystemSettingsStayHiddenInitially() {
        var mode: ReminderDisplayMode? = null
        screen(onMode = { mode = it })
        compose.onNodeWithText("仅通知栏").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(ReminderDisplayMode.NOTIFICATION_BAR, mode) }
        compose.onNodeWithText("系统通知设置").assertDoesNotExist()
        compose.onNodeWithTag("reminder-settings-list").performScrollToNode(hasText("自定义课程提醒设置"))
        compose.onNodeWithText("自定义课程提醒设置").assertIsDisplayed()
        screenshot("reminder-settings-0.3.3.png")
    }

    @Test fun missingPreviewExposesSystemSettings() {
        screen(ReminderTestState(message = "系统已接收测试通知", needsConfirmation = true))
        compose.onNodeWithText("系统通知设置").assertDoesNotExist()
        compose.onNodeWithText("没有收到").performScrollTo().performClick()
        compose.onNodeWithText("系统通知设置").performScrollTo().assertIsDisplayed()
    }

    @Test fun courseListSavesCustomMinutesAndCanRemoveAndRejoinCourse() {
        val course = Course("synthetic-reminder", "synthetic-semester", SourceType.QNU_EDU, "synthetic", name = "合成课程")
        val state = mutableStateOf(CourseReminderUiState(enabled = true))
        compose.setContent { QztcTheme {
            ReminderSettingsScreen(state.value, listOf(course), "合成学期", {}, {}, {}, {}, ReminderTestState(), {}, {}, { id, minutes, _ ->
                state.value = state.value.copy(overrides = minutes?.let { mapOf(id to it) } ?: emptyMap())
            })
        } }
        compose.onNodeWithTag("reminder-settings-list").performScrollToNode(hasText("自定义课程提醒设置"))
        compose.onNodeWithText("自定义课程提醒设置").performClick()
        compose.onNodeWithText("合成课程").performClick()
        compose.onAllNodesWithText("自定义").onLast().performScrollTo().performClick()
        compose.onNodeWithText("提前多少分钟").performTextReplacement("25")
        compose.onNodeWithText("确定").performClick()
        compose.onNodeWithText("保存").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(25, state.value.overrides[course.id]) }
        compose.onNodeWithText("合成课程").performScrollTo().performClick()
        compose.onNodeWithText("本课不提醒").performScrollTo().performClick()
        compose.onNodeWithText("保存").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, state.value.overrides[course.id]) }
        compose.onNodeWithText("添加课程").performScrollTo().performClick()
        compose.onNodeWithText("加入提醒").performScrollTo().performClick()
        compose.runOnIdle { assertNull(state.value.overrides[course.id]) }
    }

    @Test fun downloadDialogShowsProgressAndSupportsHideAndClear() {
        var hidden = false
        var cleared = false
        compose.setContent { QztcTheme {
            AppUpdateDialog(AppUpdateClient.Manifest(7, "测试版本", "https://gitee.com/synthetic.apk", "0".repeat(64), 32L * 1024 * 1024),
                "0.3.3", true, 16L * 1024 * 1024, false, false,
                { hidden = true }, {}, {}, { cleared = true })
        } }
        compose.onNodeWithText("50% · 16.0 MB / 32.0 MB").assertIsDisplayed()
        screenshot("update-progress-0.3.3.png")
        compose.onNodeWithText("暂时收起").performClick()
        compose.runOnIdle { assertTrue(hidden) }
        compose.onNodeWithText("取消并清除").performClick()
        compose.runOnIdle { assertTrue(cleared) }
    }
}
