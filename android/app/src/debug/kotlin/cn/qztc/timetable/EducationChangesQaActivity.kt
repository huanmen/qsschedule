package cn.qztc.timetable

import android.os.Bundle
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import cn.qztc.timetable.core.data.db.TimetableDatabase
import cn.qztc.timetable.core.data.repository.TimetableRepository
import cn.qztc.timetable.core.domain.imports.ImportedTimetable
import cn.qztc.timetable.core.domain.model.*
import cn.qztc.timetable.core.domain.schedule.UpcomingScheduleChanges
import cn.qztc.timetable.core.ui.theme.QztcTheme
import cn.qztc.timetable.qnu.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/** Explicit, debug-only UI acceptance. Course candidates remain in memory. */
class EducationChangesQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra("mode") ?: "gate"
        val report = JSONObject().put("mode", mode)
        val result = File(filesDir, "education-qa-$mode.json")
        fun save() { result.writeText(report.toString()) }
        save()
        if (mode in setOf("settings", "settings-plain", "cached-study", "account")) {
            val isolated = object : ContextWrapper(this) {
                override fun getApplicationContext(): Context = this
                override fun getSharedPreferences(name: String, flags: Int): SharedPreferences =
                    super.getSharedPreferences("oct17-followup-$name", flags)
            }
            if (mode == "cached-study") {
                cn.qztc.timetable.education.GradeCache(isolated).save("qa-offline", cn.qztc.timetable.education.GradeSnapshot(listOf(
                    cn.qztc.timetable.integration.qnu.QnuGradeRecord("QA", "离线缓存验收课程", "80", "3.0", "2", "考试", "必修", "通过", "2026", "春")), System.currentTimeMillis()))
            }
            setContent { QztcTheme { CompositionLocalProvider(LocalContext provides isolated) {
                when (mode) {
                    "cached-study" -> cn.qztc.timetable.education.StudyScreen("2026 秋", emptyList(), QnuSavedCredentials("qa-offline", "unused"), { finish() }, {})
                    "account" -> {
                        var configuring by remember { mutableStateOf(false) }
                        val gate = rememberEducationAccountGate(false) { configuring = true }
                        if (configuring) EducationAccountScreen(null, { configuring = false }, { report.put("savedToIsolatedFlow", true); save(); configuring = false })
                        else Column(Modifier.padding(24.dp)) {
                            listOf("成绩与学分", "停课与调课", "作业查询").forEach { label -> TextButton(onClick = { gate { error("Unconfigured query started") } }) { Text(label) } }
                        }
                    }
                    else -> {
                        var settings by remember { mutableStateOf(cn.qztc.timetable.core.domain.model.DailyAgendaSettings(enabled = true)) }
                        var state by remember { mutableStateOf(cn.qztc.timetable.feature.timetable.CourseReminderUiState(enabled = true)) }
                        cn.qztc.timetable.feature.settings.ReminderSettingsScreen(state,
                            listOf(Course("qa-current", "qa-current-term", SourceType.QNU_EDU, "qa-current", name = "当前学期验收课程")), "2026 秋", { finish() },
                            { state = state.copy(enabled = it) }, { state = state.copy(minutes = it) }, { state = state.copy(displayMode = it) },
                            cn.qztc.timetable.feature.settings.ReminderTestState(), {}, {}, { _, _, _ -> },
                            dailyAgenda = settings, onDailyAgendaChange = { settings = it }, academicPluginInstalled = mode == "settings",
                            homeworkOptions = { cn.qztc.timetable.education.HomeworkReminderSettings() })
                    }
                }
            } } }
            return
        }
        if (mode == "gate") {
            setContent { QztcTheme {
                var configured by remember { mutableStateOf(false) }
                var queries by remember { mutableIntStateOf(0) }
                var configurations by remember { mutableIntStateOf(0) }
                val gate = rememberEducationAccountGate(configured) {
                    configurations++; configured = true
                    report.put("configurations", configurations); save()
                }
                Column(Modifier.fillMaxSize().padding(24.dp)) {
                    Text("隔离验收：不修改已保存账号")
                    listOf("成绩与学分", "停课与调课", "作业查询").forEach { label ->
                        TextButton(onClick = { gate {
                            queries++; report.put("queries", queries); save()
                        } }) { Text(label) }
                    }
                    Text("查询 $queries · 配置 $configurations")
                }
                SideEffect { report.put("queries", queries).put("configurations", configurations); save() }
            } }
            return
        }
        check(intent.getBooleanExtra("liveSchoolCheck", false))
        val year = intent.getIntExtra("year", 2026)
        val term = Term.valueOf(intent.getStringExtra("term") ?: "SPRING")
        val target = Semester(QnuSemesterFactory.id(year, term), "$year ${term.name}", year, term,
            LocalDate.of(year, if (term == Term.SPRING) 2 else 9, 1))
        val credentials = QnuCredentialStore(this).load()
        setContent { QztcTheme {
            var phase by remember { mutableStateOf("prepare") }
            var full by remember { mutableStateOf<ImportedTimetable?>(null) }
            var message by remember { mutableStateOf("准备只读检查") }
            val database = remember { TimetableDatabase.build(this) }
            val repository = remember { TimetableRepository(database) }
            var verifyUnchanged by remember { mutableStateOf<(suspend () -> Boolean)?>(null) }
            DisposableEffect(Unit) { onDispose { database.close() } }
            LaunchedEffect(Unit) {
                val semesters = repository.semesters()
                val before = semesters.associate { it.id to repository.state(it.id) }
                val settings = repository.settings()
                verifyUnchanged = { repository.semesters() == semesters && repository.settings() == settings &&
                    semesters.all { repository.state(it.id) == before.getValue(it.id) } }
                if (credentials == null) {
                    report.put("error", "未保存账号"); save(); message = "未保存账号"; phase = "done"
                } else phase = if (mode in setOf("previous", "audit")) "full" else "check"
            }
            fun fail(error: String) {
                message = error; phase = "done"
                report.put("error", error); save()
            }
            Column(Modifier.fillMaxSize()) {
                Text(message, Modifier.padding(16.dp))
                if (phase == "full" || phase == "check") key(phase) {
                    val readingFull = phase == "full"
                    QnuImportScreen(credentials, onBack = { finish() },
                        automaticImport = readingFull, automaticCheck = !readingFull,
                        showAutomaticCheck = true, syncOnly = !readingFull,
                        checkSemester = target, onCheckFailed = { fail(it) },
                        modifier = Modifier.weight(1f),
                        onImported = { candidate -> lifecycleScope.launch {
                            runCatching {
                                check(!candidate.hasErrors)
                                check(candidate.semesterHint?.year == year && candidate.semesterHint?.term == term)
                                if (readingFull) {
                                    check(candidate.state.courses.isNotEmpty() && candidate.state.meetings.isNotEmpty())
                                    full = candidate
                                    report.put("fullCourses", candidate.state.courses.size)
                                        .put("fullMeetings", candidate.state.meetings.size)
                                        .put("fullNotices", candidate.state.exceptions.size)
                                        .put("unscopedWarnings", JSONArray(candidate.diagnostics.filter { it.code == "CANCEL_SCOPE_UNCONFIRMED" }.map { it.message }.distinct()))
                                        .put("courses", JSONArray().apply {
                                            candidate.state.courses.sortedBy { it.name }.forEach { course ->
                                                put(JSONObject().put("name", course.name).put("code", course.code)
                                                    .put("sequence", course.sequence).put("credits", course.credits)
                                                    .put("nature", course.nature).put("selectionAttribute", course.selectionAttribute)
                                                    .put("meetings", JSONArray().apply {
                                                        candidate.state.meetings.filter { it.courseId == course.id }.forEach { meeting ->
                                                            put(JSONObject().put("weekday", meeting.weekday)
                                                                .put("periodStart", meeting.periodStart).put("periodEnd", meeting.periodEnd)
                                                                .put("weeks", JSONArray(meeting.weekRule.weeks)))
                                                        }
                                                    }))
                                            }
                                        }).put("exceptions", JSONArray().apply {
                                            candidate.state.exceptions.forEach { exception ->
                                                put(JSONObject().put("course", candidate.state.courses.firstOrNull { it.id == exception.courseId }?.name)
                                                    .put("courseId", exception.courseId)
                                                    .put("type", exception.type.name)
                                                    .put("original", exception.original?.let { "${it.week}/${it.weekday}/${it.periodStart}-${it.periodEnd}" })
                                                    .put("replacement", exception.replacement?.let { "${it.week}/${it.weekday}/${it.periodStart}-${it.periodEnd}" }))
                                            }
                                        })
                                    if (mode == "previous") check(candidate.diagnostics.none { it.code == "CANCEL_SCOPE_UNCONFIRMED" &&
                                        (it.message.contains("数字逻辑与数字电路") || it.message.contains("大学物理实验")) })
                                    save()
                                    if (mode == "audit") {
                                        report.put("storedTimetablesUnchanged", true).put("passed", true)
                                        save(); message = "只读解析完成，原课表未改变"; phase = "done"
                                    } else {
                                        message = "旧学期课表已在内存解析，继续核对停调课"; phase = "check"
                                    }
                                } else {
                                    val base = full?.state ?: repository.state(target.id)
                                    val merged = UpcomingScheduleChanges.merge(target, base, candidate.state,
                                        emptyList(), LocalDateTime.now(), includeExpired = true)
                                    check(candidate.state.exceptions.all { e -> listOfNotNull(e.original, e.replacement)
                                        .all { it.week > 0 && it.weekday in 1..7 && it.periodStart <= it.periodEnd } })
                                    if (mode == "current") {
                                        val course = candidate.state.courses.single { it.code == "0701417" }
                                        val notice = candidate.state.exceptions.single { it.courseId == course.id && it.type == ExceptionType.CANCEL }
                                        check(notice.original?.week == 6 && notice.original?.weekday == 4 &&
                                            notice.original?.periodStart == 3 && notice.original?.periodEnd == 4)
                                        check(merged.exceptions.any { it.original == notice.original && it.type == notice.type })
                                        report.put("screenshotNoticeMatched", true)
                                    }
                                    check(verifyUnchanged?.invoke() == true)
                                    report.put("year", year).put("term", term.name)
                                        .put("notices", candidate.state.exceptions.size)
                                        .put("matchedNotices", merged.exceptions.size)
                                        .put("slots", JSONArray().apply { candidate.state.exceptions.forEach { e ->
                                            put(JSONObject().put("type", e.type.name).put("week", e.original?.week)
                                                .put("weekday", e.original?.weekday).put("periodStart", e.original?.periodStart)
                                                .put("periodEnd", e.original?.periodEnd))
                                        } }).put("storedTimetablesUnchanged", true).put("passed", true)
                                    save(); message = "只读验收通过，原课表未改变"; phase = "done"
                                }
                            }.onFailure { fail("验收失败：${it.javaClass.simpleName}") }
                        } })
                }
            }
        } }
    }
}
