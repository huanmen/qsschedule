package cn.qztc.timetable

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.data.db.TimetableDatabase
import cn.qztc.timetable.core.data.repository.TimetableRepository
import cn.qztc.timetable.core.domain.model.*
import cn.qztc.timetable.core.domain.schedule.OccurrenceExpander
import cn.qztc.timetable.core.ui.theme.QztcTheme
import cn.qztc.timetable.education.*
import cn.qztc.timetable.feature.timetable.*
import cn.qztc.timetable.qnu.QnuSavedCredentials
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** Direct UI QA; every preference/database is explicitly isolated from user data. */
class Oct13IsolatedQaActivity : ComponentActivity() {
    private val account = "oct13-isolated-fixture"
    private lateinit var qaContext: Context
    private val prefNames = linkedSetOf<String>()
    private fun report() {
        val store = HomeworkStore(qaContext, account)
        val choices = HomeworkChoices(qaContext, store.key)
        File(filesDir, "oct13-isolated-result.json").writeText(JSONObject().apply {
            put("hidden", JSONArray(choices.hidden().toList()))
            put("rows", HomeworkPayload.read(store.snapshot() ?: JSONObject()).size)
            put("calendarRows", AcademicAgendaStore(qaContext).read().size)
            put("pendingReminder", JSONObject().apply {
                val value = choices.options("123:0")
                put("enabled", value.enabled); put("once", value.once?.toString())
                put("daily", value.daily?.toString()); put("beforeMinutes", value.beforeMinutes)
            })
            put("scheduled", qaContext.getSharedPreferences("homework-alarms", 0)
                .getStringSet("scheduled", emptySet()).orEmpty().size)
        }.toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        qaContext = object : ContextWrapper(this) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                prefNames.add(name)
                return super.getSharedPreferences("oct13-isolated-$name", mode)
            }
        }
        val mode = intent.getStringExtra("mode") ?: "homework"
        if (mode == "cleanup") {
            HomeworkReminderScheduler.activate(qaContext, "empty")
            (prefNames + setOf("homework-results", "homework-choices", "homework-alarms", "academic-agenda"))
                .forEach { getSharedPreferences("oct13-isolated-$it", 0).edit().clear().commit() }
            deleteDatabase("oct13-isolated-coverage.db")
            File(filesDir, "oct13-isolated-cleanup.json").writeText("{\"cleaned\":true}")
            finish(); return
        }
        val store = HomeworkStore(qaContext, account)
        if (mode == "homework" && store.snapshot() == null) {
            val deadline = LocalDate.now().plusDays(3).atTime(23, 0).toString().replace('T', ' ')
            store.mergeAndSave(JSONObject().put("rows", JSONArray().apply {
                listOf("pending", "submitted").forEachIndexed { index, status ->
                    put(JSONObject().put("id", "123:$index").put("courseId", "123").put("taskId", "$index")
                        .put("courseName", "隔离验收课程").put("title", if (index == 0) "待提交验收作业" else "已提交验收作业")
                        .put("status", status).put("deadline", deadline).put("detailComplete", true)
                        .put("content", "作业正文 https://www.w3school.com.cn/")
                        .put("submissionContent", "答案不应展示").put("attachments", JSONArray())
                        .put("detailUrl", "https://eol.qztc.edu.cn/meol/common/homework.html"))
                }
            }))
        }
        setContent { QztcTheme { CompositionLocalProvider(LocalContext provides qaContext) {
            Column(Modifier.fillMaxSize()) {
                TextButton(onClick = { report() }) { Text("验收数据 · 检查存储") }
                Box(Modifier.weight(1f)) {
                    if (mode == "coverage") CoverageFixture()
                    else HomeworkScreen(QnuSavedCredentials(account, "unused"), { finish() }, { finish() }, autoRefresh = false)
                }
            }
        } } }
    }

    @Composable private fun CoverageFixture() {
        var ready by remember { mutableStateOf(false) }
        val state = remember { DemoScheduleState() }
        LaunchedEffect(Unit) {
            val database = TimetableDatabase.build(this@Oct13IsolatedQaActivity, "oct13-isolated-coverage.db")
            try {
                val repo = TimetableRepository(database)
                val monday = LocalDate.now().minusDays((LocalDate.now().dayOfWeek.value - 1).toLong())
                val semester = Semester("isolated", "覆盖验收", monday.year, Term.AUTUMN, monday, 3)
                val course = Course("isolated-course", semester.id, SourceType.MANUAL, "isolated-course", name = "覆盖前原课程")
                val meeting = CourseMeeting("isolated-meeting", course.id, 1, 1, 2, WeekRule.of(listOf(1, 2, 3)), "验收教师", "验收教室", SourceType.MANUAL)
                repo.upsertSemester(semester)
                val scheme = PeriodSchemeFactory.forSemester(semester.id)
                repo.upsertPeriodScheme(scheme)
                repo.updateCourseAndMeeting(course, null, meeting)
                val activity = ScheduleActivity("isolated-activity", semester.id, "覆盖验收活动", kind = ActivityKind.PERIOD,
                    weekday = 1, periodStart = 1, periodEnd = 2, weeks = listOf(1))
                repo.saveActivityWithCoverage(activity, listOf(meeting.id))
                val saved = repo.state(semester.id)
                val activityWeeks = OccurrenceExpander.effective(semester, saved).map { it.week }
                val courseWeeks = OccurrenceExpander.effective(semester, saved.courseView()).map { it.week }
                check(activityWeeks == listOf(2, 3) && courseWeeks == listOf(1, 2, 3))
                state.loadImported(ImportedSchedule(semester, scheme.periods, saved, activities = listOf(activity)))
                File(filesDir, "oct13-isolated-coverage.json").writeText(JSONObject().put("activityWeeks", JSONArray(activityWeeks))
                    .put("courseWeeks", JSONArray(courseWeeks)).put("passed", true).toString())
                ready = true
            } finally { database.close() }
        }
        if (ready) WeekScreen(state, { _, _ -> }) else Text("准备隔离覆盖数据…")
    }
}
