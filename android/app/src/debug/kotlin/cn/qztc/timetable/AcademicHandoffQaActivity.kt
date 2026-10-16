package cn.qztc.timetable

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.ui.glass.*
import cn.qztc.timetable.core.ui.theme.QztcTheme
import cn.qztc.timetable.education.CreditProgressPanel
import cn.qztc.timetable.education.GradeSnapshot
import cn.qztc.timetable.integration.qnu.QnuGradeRecord
import cn.qztc.timetable.core.domain.imports.PersonalClassMerger
import cn.qztc.timetable.core.domain.model.*
import cn.qztc.timetable.core.domain.schedule.TimetableState
import cn.qztc.timetable.qnu.ClassMergeReviewScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Synthetic, debug-only verification that does not modify the user's account/cache. */
class AcademicHandoffQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra("mode") ?: "credits"
        val resultFile = File(filesDir, "handoff-$mode.json")
        var contentPasses = 0
        setContent {
            QztcTheme(darkTheme = false) {
                if (mode == "homework-live") {
                    val credentials = remember { cn.qztc.timetable.qnu.QnuCredentialStore(this).load() }
                    var status by remember { mutableStateOf("准备读取教学平台") }
                    var running by remember { mutableStateOf(credentials != null) }
                    androidx.compose.material3.Text(status, Modifier.padding(24.dp))
                    if (running && credentials != null) cn.qztc.timetable.education.HomeworkAcquisition(credentials,
                        onProgress = { status = it.optString("status"); resultFile.writeText(JSONObject().put("status", status).toString()) },
                        onComplete = { result ->
                            val records = cn.qztc.timetable.education.HomeworkPayload.read(result)
                            cn.qztc.timetable.education.HomeworkStore(this, credentials.username).mergeAndSave(result)
                            resultFile.writeText(result.put("validatedCount", records.size).toString()); status = "读取完成"; running = false
                        },
                        onFailure = { error -> resultFile.writeText(JSONObject().put("error", error).toString()); status = error; running = false })
                    LaunchedEffect(Unit) { if (credentials == null) resultFile.writeText(JSONObject().put("error", "没有已保存账号").toString()) }
                } else if (mode == "homework-ui") {
                    val credentials = remember { cn.qztc.timetable.qnu.QnuCredentialStore(this).load() }
                    remember(credentials) {
                        if (credentials != null) {
                            val live = File(filesDir, "handoff-homework-live.json")
                            if (live.exists()) cn.qztc.timetable.education.HomeworkStore(this@AcademicHandoffQaActivity, credentials.username)
                                .mergeAndSave(JSONObject(live.readText()))
                        }; true
                    }
                    cn.qztc.timetable.education.HomeworkScreen(credentials, onBack = { finish() }, onConfigureAccount = { finish() }, autoRefresh = false)
                    LaunchedEffect(Unit) {
                        delay(1500)
                        runCatching { val bitmap = capture();File(filesDir, "handoff-homework-ui.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) };bitmap.recycle() }
                            .onFailure { resultFile.writeText(JSONObject().put("error", it.toString()).toString()) }
                    }
                } else if (mode in setOf("credits", "credits-stress", "electives")) {
                    val snapshot = remember { if (mode == "credits-stress") GradeSnapshot(listOf(
                        QnuGradeRecord(null, "长列表验收课组", null, null, "0", null, null, null,
                            officialFields = mapOf("学分要求" to "180", "是否合格" to "false", "课程明细" to
                                org.json.JSONArray().apply { repeat(180) { i -> put(JSONObject().put("name", "长列表课程$i")
                                    .put("credits", 1).put("status", "已选课成绩未知")) } }.toString()))), 1L)
                    else if (mode == "electives") GradeSnapshot(listOf("跨学科" to "5", "艺术" to "0").map { (name, earned) ->
                        QnuGradeRecord(null, name, null, null, earned, null, null, null,
                            officialFields = mapOf("学分要求" to if (name == "艺术") "2" else "7", "是否合格" to "false", "课程明细" to
                                "[{\"name\":\"在修艺术课\",\"code\":\"art\",\"credits\":2,\"status\":\"已选课成绩未知\"}]"))
                    }, 1L)
                    else GradeSnapshot(listOf("32", "2", "1").mapIndexed { index, earned ->
                        QnuGradeRecord(null, "通识课程（必修）", null, null, earned, null, null, null,
                            officialFields = mapOf("学分要求" to "36", "是否合格" to "false", "课程明细" to
                                "[{\"name\":\"合成课程$index\",\"credits\":2,\"status\":\"通过\"}]"))
                    }, 1L) }
                    CreditProgressPanel(null, {}, initialSnapshot = snapshot)
                    LaunchedEffect(Unit) { delay(1500);resultFile.writeText(JSONObject().put("mode", mode).put("rendered", true).toString()) }
                } else if (mode == "calendar-swipe") {
                    cn.qztc.timetable.feature.settings.CalendarSwipeQaPanel()
                } else if (mode == "merge") {
                    val result = remember {
                        val course = Course("qa-personal", "qa", SourceType.QNU_EDU, "qa-personal", name = "个人验收课程")
                        val meeting = CourseMeeting("qa-personal", course.id, 1, 1, 2, WeekRule.of(listOf(1)), "验收教师", "验收地点", SourceType.QNU_EDU)
                        val extras = (1..180).map { i -> PersonalClassMerger.ExtraSlot(course.copy(id = "qa-course$i", name = "班级验收课程$i"),
                            meeting.copy(id = "qa-extra$i", courseId = "qa-course$i")) }
                        PersonalClassMerger.Result(TimetableState(listOf(course), listOf(meeting)), extras, emptyList(), listOf(
                            PersonalClassMerger.OverlapConflict(course.name, "班级验收课程180", 1, 1, 2, 1, 2, listOf(1), meeting.id, "qa-extra180")))
                    }
                    ClassMergeReviewScreen(result, onConfirm = { merged ->
                        resultFile.writeText(JSONObject().put("confirmed", true).put("courses", merged.courses.size)
                            .put("edited", merged.courses.any { it.name == "EditedQA" }).toString())
                    }, onBack = { finish() })
                } else {
                    var suspended by remember { mutableStateOf(false) }
                    val qaContent: @Composable () -> Unit = remember {
                        {
                            SideEffect { contentPasses++ }
                            GlassSurface(GlassRole.FUNCTIONAL, Modifier.padding(30.dp).size(200.dp, 120.dp)) { Box(Modifier.fillMaxSize()) }
                        }
                    }
                    GlassBackdropHost(background = { Box(Modifier.fillMaxSize().background(Color(0xFF183655))) },
                        suspendEffects = suspended, content = qaContent)
                    LaunchedEffect(Unit) {
                        try {
                            delay(1000)
                            val before = capture()
                            var maximum = 0
                            repeat(6) {
                                suspended = !suspended; delay(300)
                                val after = capture()
                                for (y in 0 until before.height step 7) for (x in 0 until before.width step 7) {
                                    val a = before.getPixel(x, y); val b = after.getPixel(x, y)
                                    for (shift in listOf(0, 8, 16)) maximum = maxOf(maximum,
                                        kotlin.math.abs((a shr shift and 255) - (b shr shift and 255)))
                                }
                                after.recycle()
                            }
                            before.recycle()
                            resultFile.writeText(JSONObject().put("mode", mode).put("maximumChannelDelta", maximum)
                                .put("toggles", 6).put("contentPasses", contentPasses)
                                .put("passed", maximum <= 8 && contentPasses <= 2).toString())
                        } catch (e: Exception) { resultFile.writeText(JSONObject().put("error", e.toString()).toString()) }
                    }
                }
            }
        }
    }

    private suspend fun capture(): Bitmap = suspendCancellableCoroutine { continuation ->
        val root = window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(window, bitmap, { result ->
            if (result == PixelCopy.SUCCESS) continuation.resume(bitmap)
            else { bitmap.recycle(); continuation.resumeWithException(IllegalStateException("PixelCopy $result")) }
        }, Handler(Looper.getMainLooper()))
    }
}
