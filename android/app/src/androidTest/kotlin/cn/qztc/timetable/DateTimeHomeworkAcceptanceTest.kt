package cn.qztc.timetable

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cn.qztc.timetable.core.ui.theme.QztcTheme
import cn.qztc.timetable.core.ui.theme.QztcTimePicker
import cn.qztc.timetable.education.*
import cn.qztc.timetable.qnu.QnuSavedCredentials
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalTime
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DateTimeHomeworkAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<FeatureAcceptanceActivity>()

    @Test fun wheelCommitsInitialTimeAndFingerScrollingChangesIt() {
        var chosen: LocalTime? = null
        var visible by mutableStateOf(true)
        compose.setContent { QztcTheme { if (visible) QztcTimePicker(LocalTime.of(8, 30), {}, { chosen = it; visible = false }) } }
        compose.onNodeWithText("确定").performClick()
        compose.runOnIdle { assertEquals(LocalTime.of(8, 30), chosen); visible = true }
        compose.onNodeWithTag("time-wheel-小时").performTouchInput { swipeUp(startY = centerY + 50, endY = centerY - 50, durationMillis = 800) }
        compose.waitForIdle()
        compose.onNodeWithText("确定").performClick()
        compose.runOnIdle { assertNotEquals(8, chosen!!.hour); assertEquals(30, chosen!!.minute) }
    }

    @Test fun removedPendingHomeworkImmediatelyReturnsToPendingAndSubmittedDetailHasNoActions() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val account="oct13-homework-ui-${System.nanoTime()}"
        val store=HomeworkStore(context, account)
        val rows=JSONArray()
        listOf("pending", "submitted").forEachIndexed { index, status ->
            rows.put(JSONObject().put("id","123:$index").put("courseId","123").put("taskId",index.toString())
                .put("courseName","合成课程").put("title",if(index==0) "待提交回归条目" else "已提交回归条目")
                .put("status",status).put("deadline","2030-01-01 12:00:00").put("detailComplete",true)
                .put("content","作业正文 https://www.w3school.com.cn/").put("submissionContent","答案不应展示")
                .put("attachments",JSONArray().put(JSONObject().put("name","重复学习链接").put("url","https://www.w3school.com.cn/")))
                .put("detailUrl","https://eol.qztc.edu.cn/meol/common/homework.html"))
        }
        try {
            store.mergeAndSave(JSONObject().put("rows",rows))
            compose.setContent { QztcTheme { HomeworkScreen(QnuSavedCredentials(account,"unused"), {}, {}, autoRefresh=false) } }
            compose.onNodeWithText("移出", useUnmergedTree = true).performScrollTo().performClick()
            compose.onNodeWithText("移出这项作业？").assertIsDisplayed()
            compose.onNode(hasText("移出") and hasAnyAncestor(isDialog()), useUnmergedTree = true).performClick()
            compose.onNodeWithText("已移出 (1)").performClick()
            compose.onNodeWithText("移入").performScrollTo().performClick()
            compose.onNodeWithText("待提交回归条目").assertExists()
            compose.onNodeWithText("移出", useUnmergedTree = true).assertExists()
            compose.onNodeWithText("已提交", useUnmergedTree = true).performClick()
            compose.onNodeWithText("已提交回归条目").performScrollTo().performClick()
            compose.onNodeWithText("移出").assertDoesNotExist()
            compose.onNodeWithText("提醒").assertDoesNotExist()
            compose.onNodeWithText("加入日历").assertDoesNotExist()
            compose.onNodeWithText("答案不应展示").assertDoesNotExist()
            compose.onNodeWithText("重复学习链接").assertDoesNotExist()
        } finally {
            context.getSharedPreferences("homework-results",0).edit().remove(store.key).commit()
            context.getSharedPreferences("homework-choices",0).edit().remove("${store.key}:hidden").commit()
            val username=cn.qztc.timetable.qnu.QnuCredentialStore(context).load()?.username.orEmpty()
            HomeworkReminderScheduler.activate(context, HomeworkStore(context,username).key)
        }
    }
}
