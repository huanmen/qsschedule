package cn.qztc.timetable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cn.qztc.timetable.core.domain.model.*
import cn.qztc.timetable.core.ui.theme.QztcTheme
import cn.qztc.timetable.feature.settings.*
import cn.qztc.timetable.feature.timetable.CourseReminderUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Synthetic UI state only: no account, timetable, reminder or plugin storage is changed. */
class Release150CorrectionsTest {
    @get:Rule val compose = createAndroidComposeRule<FeatureAcceptanceActivity>()

    @Test fun emptySemesterStillShowsOverviewAndImportAction() {
        var imports = 0
        compose.setContent {
            QztcTheme { SemesterProgressCard(null, null, "导入课表", { imports++ }) }
        }
        compose.onNodeWithText("尚未导入课表").assertIsDisplayed()
        compose.onNodeWithText("开学日期").assertIsDisplayed()
        compose.onNodeWithContentDescription("切换学期").assertDoesNotExist()
        compose.onNodeWithText("导入课表").performClick()
        compose.runOnIdle { assertEquals(1, imports) }
    }

    @Test fun dailyAgendaChildrenFollowMasterSwitch() {
        var previews = 0
        compose.setContent {
            var daily by remember { mutableStateOf(DailyAgendaSettings(enabled = false)) }
            QztcTheme {
                ReminderSettingsScreen(CourseReminderUiState(enabled = false), emptyList(), "合成学期", {},
                    {}, {}, {}, ReminderTestState(), {}, {}, { _, _, _ -> },
                    dailyAgenda = daily, onDailyAgendaChange = { daily = it }, onPreviewDailyAgenda = { previews++ })
            }
        }
        compose.onNodeWithTag("daily-agenda-time").assertIsNotEnabled().performClick()
        compose.onNodeWithText("一").assertIsNotEnabled()
        compose.onNodeWithText("赛事与考证安排 · 前往插件市场").assertIsNotEnabled()
        compose.onNodeWithTag("daily-agenda-preview").performScrollTo().assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(0, previews) }
        compose.onNodeWithTag("daily-agenda-enabled").performScrollTo().performClick()
        compose.onNodeWithTag("daily-agenda-time").assertIsEnabled()
        compose.onNodeWithText("一").assertIsEnabled()
        compose.onNodeWithTag("daily-agenda-preview").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, previews) }
    }

    @Test fun courseReminderChildrenFollowMasterSwitchAndCollapseEditor() {
        var previews = 0
        val course = Course("fixture", "term", SourceType.MANUAL, "fixture", name = "合成课程")
        compose.setContent {
            var state by remember { mutableStateOf(CourseReminderUiState(enabled = false)) }
            QztcTheme {
                ReminderSettingsScreen(state, listOf(course), "合成学期", {},
                    { state = state.copy(enabled = it) }, { state = state.copy(minutes = it) },
                    { state = state.copy(displayMode = it) }, ReminderTestState(), { previews++ }, {}, { _, _, _ -> })
            }
        }
        compose.onNodeWithTag("reminder-enabled").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("course-remind-holidays").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("5 分钟").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("reminder-mode-notification").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("reminder-mode-preview").performScrollTo().assertIsNotEnabled().performClick()
        compose.onNodeWithText("合成课程").performScrollTo().assertHasClickAction().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, previews) }
        compose.onNodeWithTag("reminder-enabled").performScrollTo().performClick()
        compose.onNodeWithTag("course-remind-holidays").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("5 分钟").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("reminder-mode-notification").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("reminder-mode-preview").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, previews) }
        compose.onNodeWithText("合成课程").performScrollTo().performClick()
        compose.onNodeWithText("额外提醒内容").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("reminder-enabled").performScrollTo().performClick()
        compose.onNodeWithText("额外提醒内容").assertDoesNotExist()
    }

    @Test fun privacyOnlyDescribesAvailableFeatures() {
        compose.setContent { QztcTheme { PrivacyAboutScreen({}) } }
        compose.onNodeWithText("联网与插件").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("AI", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("模型", substring = true).assertCountEquals(0)
    }
}
