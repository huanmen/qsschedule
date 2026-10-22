package cn.qztc.timetable

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cn.qztc.timetable.core.ui.theme.QztcTheme
import cn.qztc.timetable.qnu.rememberEducationAccountGate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EducationAccountGateTest {
    @get:Rule val compose = createAndroidComposeRule<FeatureAcceptanceActivity>()

    @Test fun missingAccountBlocksEveryQueryAndOffersConfiguration() {
        var queries = 0
        var configurations = 0
        val labels = listOf("成绩与学分", "停课与调课", "作业查询")
        compose.setContent { QztcTheme {
            val gate = rememberEducationAccountGate(false) { configurations++ }
            Column { labels.forEach { label -> TextButton(onClick = { gate { queries++ } }) { Text(label) } } }
        } }
        labels.forEach { label ->
            compose.onNodeWithText(label).performClick()
            compose.onNodeWithText("请先配置教务账号").assertIsDisplayed()
            compose.onNodeWithText("取消").performClick()
        }
        compose.onNodeWithText(labels.first()).performClick()
        compose.onNodeWithText("去配置").performClick()
        compose.runOnIdle { assertEquals(0, queries); assertEquals(1, configurations) }
    }

    @Test fun configuredAccountOpensQueryWithoutPrompt() {
        var queries = 0
        compose.setContent { QztcTheme {
            val gate = rememberEducationAccountGate(true) { error("Unexpected configuration") }
            TextButton(onClick = { gate { queries++ } }) { Text("查询") }
        } }
        compose.onNodeWithText("查询").performClick()
        compose.onNodeWithText("请先配置教务账号").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, queries) }
    }
}
