package cn.qztc.timetable.sync

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.qztc.timetable.core.data.repository.TimetableRepository
import cn.qztc.timetable.core.domain.calendar.SemesterCalendar
import cn.qztc.timetable.core.domain.model.*
import cn.qztc.timetable.core.domain.schedule.*
import cn.qztc.timetable.core.ui.theme.*
import cn.qztc.timetable.qnu.QnuImportScreen
import cn.qztc.timetable.qnu.QnuSavedCredentials
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun UpcomingChangesScreen(
    semester: Semester, repository: TimetableRepository,
    onBack: () -> Unit, onLogin: () -> Unit, onApplied: suspend () -> Unit,
    savedCredentials: QnuSavedCredentials? = null,
    checkOnOpen: Boolean = true,
    onResultReady: (String?) -> Unit = {},
    initialMessage: String? = null,
) {
    val context = LocalContext.current
    val colors = LocalQztcColors.current
    val scope = rememberCoroutineScope()
    val inbox = remember(semester.id) { ScheduleChangeInbox(context, semester.id) }
    var current by remember(semester.id) { mutableStateOf(TimetableState()) }
    var entries by remember(semester.id) { mutableStateOf(emptyList<ScheduleException>()) }
    var expiredEntries by remember(semester.id) { mutableStateOf(emptyList<ScheduleException>()) }
    var periods by remember(semester.id) { mutableStateOf(emptyList<Period>()) }
    var busy by remember { mutableStateOf(true) }
    var checking by remember { mutableStateOf(false) }
    var checkRevision by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf(initialMessage) }
    var loginNeeded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ScheduleException?>(null) }
    var past by remember { mutableStateOf(false) }
    suspend fun reload() {
        current = repository.state(semester.id)
        periods = repository.periodScheme(semester.id)?.periods.orEmpty()
        // Preserve earlier pending results when moving from the former sync UI.
        val legacyDiff = repository.pendingChangeSets(semester.id).firstOrNull()?.diff
        val legacyCourses = legacyDiff?.let { it.addedCourses + it.removedCourses + it.changedCourses.flatMap { change -> listOf(change.old, change.new) } }.orEmpty()
        val legacy = legacyDiff?.addedExceptions.orEmpty().map { entry -> entry.copy(
            courseName = ScheduleCourseNames.fullest(entry.courseName, ScheduleCourseNames.findCourse(entry.courseId, legacyCourses)?.name)) }
        inbox.receive(legacy)
        val available = inbox.read().map { entry ->
            // Earlier checks used a temporary semester prefix in course IDs.
            // Resolve their stable school code/sequence against the imported term.
            val course = ScheduleCourseNames.findCourse(entry.courseId, current.courses)
            if (course == null) entry else entry.copy(courseId = course.id,
                courseName = ScheduleCourseNames.fullest(entry.courseName, course.name))
        }.distinctBy { it.id }
        val sort = compareBy<ScheduleException>({ (it.original ?: it.replacement)?.week }, { (it.original ?: it.replacement)?.weekday }, { (it.original ?: it.replacement)?.periodStart })
        entries = available.filter { UpcomingScheduleChanges.isUpcoming(semester, it, periods, LocalDateTime.now()) }.sortedWith(sort)
        expiredEntries = available.filterNot { UpcomingScheduleChanges.isUpcoming(semester, it, periods, LocalDateTime.now()) }.sortedWith(sort)
    }
    fun check() {
        busy = true; message = null; loginNeeded = false; entries = emptyList(); expiredEntries = emptyList()
        checkRevision++; checking = true
    }
    LaunchedEffect(semester.id) {
        if (checkOnOpen) check() else { reload(); busy = false }
    }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("停课与调课", style = QztcTypography.section, color = colors.ink)
        if (checking) key(checkRevision) {
            QnuImportScreen(
                savedCredentials = savedCredentials, onBack = onBack,
                syncOnly = true, automaticCheck = true,
                onCheckFailed = { failure ->
                    checking = false; busy = false; message = failure
                    loginNeeded = failure.contains("登录") || failure.contains("账号") || failure.contains("验证码")
                },
                onImported = { candidate ->
                    checking = false
                    scope.launch {
                        try {
                            val hint = candidate.semesterHint
                            if (candidate.hasErrors) message = "通知读取失败，请重试"
                            else if (hint?.year != semester.schoolYear || hint.term != semester.term) {
                                message = "请先导入最新学期"
                            } else {
                                reload()
                                val merged = UpcomingScheduleChanges.merge(semester, current, candidate.state, periods, LocalDateTime.now(), includeExpired = true)
                                inbox.receive(merged.exceptions)
                                reload()
                            }
                        } catch (_: Exception) { message = "读取失败，原课表已保留" }
                        finally { busy = false; onResultReady(message) }
                    }
                },
            )
        }
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).testTag("upcoming-changes-list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("正在检查…", color = colors.mut)
            } else if (message != null) {
                Text(message!!, color = colors.mut)
            } else if (entries.isEmpty() && expiredEntries.isEmpty()) {
                Text("暂无新的停调课", color = colors.mut)
            }
            if (!busy && message == null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!past, { past = false }, label = { Text("进行时 (${entries.size})") })
                    FilterChip(past, { past = true }, label = { Text("过去时 (${expiredEntries.size})") })
                }
                val displayed = if (past) expiredEntries else entries
                listOf(ExceptionType.CANCEL to "停课", ExceptionType.MOVE to "调课").forEach { (type, title) ->
                    Text(title, style = QztcTypography.section, color = colors.ink)
                    val group = displayed.filter { it.type == type }
                    if (group.isEmpty()) Text("暂无${title}安排", color = colors.mut)
                    group.forEach { entry ->
                        key(entry.id) {
                            ChangeEntry(entry, current, semester, expired = past, onEdit = { editing = entry }, onApply = {
                                scope.launch {
                                    busy = true
                                    try { repository.applyScheduleException(semester.id, entry); onApplied(); reload() }
                                    catch (_: Exception) { message = "更新失败，原课表已保留" }
                                    finally { busy = false }
                                }
                            })
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (loginNeeded) TextButton(onClick = onLogin) { Text("登录教务") }
            if (message != null) TextButton(enabled = !busy, onClick = ::check) { Text("重试") }
            TextButton(onClick = onBack) { Text(if (busy) "取消" else "关闭") }
        }
    }
    editing?.let { value -> ExceptionEditor(semester, value, onDismiss = { editing = null }) { updated ->
        inbox.edit(updated)
        scope.launch { reload() }
        editing = null
    } }
}

@Composable
private fun ChangeEntry(entry: ScheduleException, current: TimetableState, semester: Semester, expired: Boolean, onEdit: () -> Unit, onApply: () -> Unit) {
    val colors = LocalQztcColors.current
    val existing = current.exceptions.any { it.id == entry.id }
    val applied = current.exceptions.any { it.copy(courseName = null) == entry.copy(courseName = null) }
    val schoolKey = entry.courseId.removePrefix("qnu:").substringAfterLast(':')
    val course = ScheduleCourseNames.findCourse(entry.courseId, current.courses)
    val legacyName = schoolKey.substringBeforeLast('#').takeIf { it.any { c -> c in '\u4e00'..'\u9fff' } }
    Surface(Modifier.fillMaxWidth().then(if (expired) Modifier else Modifier.clickable(onClick = onEdit)), color = colors.solid, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(ScheduleCourseNames.fullest(entry.courseName, course?.name, legacyName) ?: "课程名称待更新，请重新检查",
                modifier = Modifier.fillMaxWidth(), style = QztcTypography.courseTitle, color = colors.ink,
                softWrap = true, maxLines = Int.MAX_VALUE)
            Text(if (entry.type == ExceptionType.MOVE) "从 ${slotLabel(semester, entry.original)}\n→ ${slotLabel(semester, entry.replacement)}"
                else slotLabel(semester, entry.original), color = colors.ink)
            entry.note?.takeIf { it.isNotBlank() }?.let { Text(it, color = colors.mut) }
            if (!expired) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (applied) "已加入课表" else if (existing) "与课表不同" else "未加入课表", color = if (applied) colors.mut else colors.warn)
                    TextButton(onClick = onEdit) { Text("编辑") }
                }
                if (!applied) Button(onClick = onApply) { Text(if (existing) "更新到课表" else "加入课表") }
            }
        }
    }
}

/** Modal entry point used by Course Arrangement. It keeps the check local to
 * the settings screen instead of navigating to another page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpcomingChangesDialog(
    semester: Semester,
    repository: TimetableRepository,
    onDismiss: () -> Unit,
    onLogin: () -> Unit,
    onApplied: suspend () -> Unit,
    savedCredentials: QnuSavedCredentials?,
    checkOnOpen: Boolean = true,
) {
    val colors = LocalQztcColors.current
    var latestSemester by remember { mutableStateOf<Semester?>(null) }
    var resultReady by remember { mutableStateOf(!checkOnOpen) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        latestSemester = repository.semesters().filter { repository.state(it.id).courses.isNotEmpty() }
            .maxWithOrNull(compareBy({ it.schoolYear }, { it.term.ordinal })) ?: semester
    }
    val content: @Composable () -> Unit = {
        latestSemester?.let { latest -> UpcomingChangesScreen(
            semester = latest, repository = repository, onBack = onDismiss, onLogin = onLogin,
            onApplied = onApplied, savedCredentials = savedCredentials, checkOnOpen = checkOnOpen && !resultReady,
            onResultReady = { resultMessage = it; resultReady = true }, initialMessage = resultMessage,
        ) } ?: Text("正在检查…", Modifier.padding(24.dp))
    }
    if (resultReady) ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.solid) { content() }
    else Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(.92f),
            shape = MaterialTheme.shapes.large,
            color = colors.solid,
            contentColor = colors.ink,
        ) {
            content()
        }
    }
}

private fun slotLabel(semester: Semester, slot: SlotRef?): String = slot?.let {
    "${SemesterCalendar.dateOf(semester, it.week, it.weekday)} · 第${if (it.periodStart >= 7) it.periodStart - 2 else it.periodStart}–${if (it.periodEnd >= 7) it.periodEnd - 2 else it.periodEnd}节" + it.room?.takeIf(String::isNotBlank)?.let { room -> " · $room" }.orEmpty()
} ?: "待定"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExceptionEditor(semester: Semester, value: ScheduleException, onDismiss: () -> Unit, onSave: (ScheduleException) -> Unit) {
    var original by remember(value.id) { mutableStateOf(value.original) }
    var replacement by remember(value.id) { mutableStateOf(value.replacement) }
    var note by remember(value.id) { mutableStateOf(value.note.orEmpty()) }
    var invalid by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("编辑${if (value.type == ExceptionType.CANCEL) "停课" else "调课"}", style = QztcTypography.section)
            original?.let { SlotEditor("原安排", semester, it) { original = it } }
            replacement?.let { SlotEditor("调到", semester, it) { replacement = it } }
            OutlinedTextField(note, { note = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth())
            invalid?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                val updated = value.copy(original = original, replacement = replacement, note = note)
                if (listOfNotNull(original, replacement).any { it.periodStart !in 1..13 || it.periodEnd !in it.periodStart..13 || it.week !in 1..semester.totalWeeks }) invalid = "请检查日期和节次"
                else onSave(updated)
            }) { Text("保存安排") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotEditor(label: String, semester: Semester, slot: SlotRef, onChange: (SlotRef) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    TextButton(onClick = { picking = true }) { Text("$label：${SemesterCalendar.dateOf(semester, slot.week, slot.weekday)}") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(slot.periodStart.toString(), { it.toIntOrNull()?.let { n -> onChange(slot.copy(periodStart = n)) } }, label = { Text("起始节次") }, modifier = Modifier.weight(1f), singleLine = true)
        OutlinedTextField(slot.periodEnd.toString(), { it.toIntOrNull()?.let { n -> onChange(slot.copy(periodEnd = n)) } }, label = { Text("结束节次") }, modifier = Modifier.weight(1f), singleLine = true)
    }
    OutlinedTextField(slot.room.orEmpty(), { onChange(slot.copy(room = it)) }, label = { Text("教室") }, modifier = Modifier.fillMaxWidth())
    if (picking) {
        QztcDatePicker(SemesterCalendar.dateOf(semester, slot.week, slot.weekday), onDismiss = { picking = false }) { selected ->
            val week = SemesterCalendar.weekOf(semester, selected)
            if (week != null) {
                onChange(slot.copy(week = week, weekday = selected.dayOfWeek.value))
                picking = false
            }
        }
    }
}
