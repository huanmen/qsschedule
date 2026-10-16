package cn.qztc.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.ui.glass.*
import cn.qztc.timetable.core.ui.theme.QztcTheme
import cn.qztc.timetable.education.CreditProgressPanel
import cn.qztc.timetable.education.GradeSnapshot
import cn.qztc.timetable.education.GradeHistoryState
import cn.qztc.timetable.education.GradeHistoryPanel
import cn.qztc.timetable.education.RetainedStudyTab
import cn.qztc.timetable.integration.qnu.QnuGradeRecord
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AcademicHandoffAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<FeatureAcceptanceActivity>()

    @Test fun duplicateCreditGroupsFromLegacyCacheRenderAndExpandIndependently() {
        val snapshot = GradeSnapshot(listOf("32", "2", "1").mapIndexed { index, earned ->
            QnuGradeRecord(null, "通识课程（必修）", null, null, earned, null, null, null,
                officialFields = mapOf("学分要求" to "36", "是否合格" to "false", "课程明细" to
                    "[{\"name\":\"合成课程$index\",\"credits\":2,\"status\":\"通过\"}]"))
        }, 1L)
        compose.setContent { QztcTheme(darkTheme = false) { CreditProgressPanel(null, {}, initialSnapshot = snapshot) } }
        compose.onAllNodesWithText("通识课程（必修）").assertCountEquals(3)
        compose.onAllNodesWithText("通识课程（必修）")[0].performClick()
        compose.onNodeWithText("合成课程0").assertIsDisplayed()
        compose.onNodeWithText("合成课程1").assertDoesNotExist()
        compose.onAllNodesWithText("通识课程（必修）")[1].performClick()
        compose.onNodeWithText("合成课程1").assertIsDisplayed()
    }

    @Test fun pausingEffectsKeepsGlassBrightness() {
        var suspended by mutableStateOf(false)
        compose.setContent {
            QztcTheme(darkTheme = false) {
                GlassBackdropHost(background = { Box(Modifier.fillMaxSize().background(Color(0xFF183655))) },
                    suspendEffects = suspended, content = {
                        GlassSurface(GlassRole.FUNCTIONAL, Modifier.padding(30.dp).size(200.dp, 120.dp).testTag("glass")) {
                            Box(Modifier.fillMaxSize())
                        }
                    })
            }
        }
        compose.waitForIdle()
        val before = compose.onNodeWithTag("glass").captureToImage().toPixelMap()
        compose.runOnIdle { suspended = true }
        val after = compose.onNodeWithTag("glass").captureToImage().toPixelMap()
        var maximum = 0f
        for (y in 20 until before.height - 20 step 7) for (x in 20 until before.width - 20 step 7) {
            val a = before[x, y]; val b = after[x, y]
            maximum = maxOf(maximum, kotlin.math.abs(a.red - b.red), kotlin.math.abs(a.green - b.green), kotlin.math.abs(a.blue - b.blue))
        }
        assertTrue("Glass brightness changed by $maximum", maximum < .03f)
    }

    @Test fun switchingStudyTabsRetainsPanelsWithoutReloading() {
        var tab by mutableIntStateOf(0)
        val mounts = intArrayOf(0, 0)
        val grades = GradeHistoryState()
        val credits = GradeSnapshot(listOf(QnuGradeRecord(null, "验收课组", null, null, "2", null, null, null,
            officialFields = mapOf("学分要求" to "4", "是否合格" to "false", "课程明细" to
                "[{\"name\":\"验收课程\",\"credits\":2,\"status\":\"通过\"}]"))), 1L)
        compose.setContent {
            QztcTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize()) {
                    RetainedStudyTab(tab == 0) {
                        LaunchedEffect(Unit) { mounts[0]++ }
                        GradeHistoryPanel(grades, {})
                    }
                    RetainedStudyTab(tab == 1) {
                        LaunchedEffect(Unit) { mounts[1]++ }
                        CreditProgressPanel(null, {}, initialSnapshot = credits)
                    }
                }
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("保留查询")
        compose.runOnIdle { tab = 1 }
        compose.onNodeWithText("验收课组").performClick()
        compose.onNodeWithText("验收课程").assertIsDisplayed()
        repeat(8) {
            compose.runOnIdle { tab = 0 }
            compose.onNodeWithText("保留查询").assertIsDisplayed()
            compose.runOnIdle { tab = 1 }
            compose.onNodeWithText("验收课程").assertIsDisplayed()
        }
        compose.runOnIdle { assertArrayEquals(intArrayOf(1, 1), mounts) }
    }

    @Test fun expandedLargeCreditGroupScrollsToLastCourse() {
        val courses = org.json.JSONArray().apply { repeat(180) { index ->
            put(org.json.JSONObject().put("name", "长列表课程$index").put("credits", 1).put("status", "已选课"))
        } }
        val snapshot = GradeSnapshot(listOf(QnuGradeRecord(null, "长列表验收课组", null, null, "0", null, null, null,
            officialFields = mapOf("学分要求" to "7", "是否合格" to "false", "课程明细" to courses.toString()))), 1L)
        compose.setContent { QztcTheme { CreditProgressPanel(null, {}, initialSnapshot = snapshot) } }
        compose.onNodeWithText("长列表验收课组").performClick()
        compose.onNodeWithText("长列表课程0").assertExists()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("长列表课程179"))
        compose.onNodeWithText("长列表课程179").assertIsDisplayed()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("长列表验收课组"))
        compose.onNodeWithText("长列表验收课组").performClick()
        compose.onNodeWithText("长列表课程0").assertDoesNotExist()
    }
}
