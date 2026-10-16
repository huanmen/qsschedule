package cn.qztc.timetable.education

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.Duration
import cn.qztc.timetable.core.ui.theme.*
import cn.qztc.timetable.core.ui.glass.GlassRole
import cn.qztc.timetable.core.ui.glass.GlassSurface
import cn.qztc.timetable.qnu.CampusNetworkDialog
import cn.qztc.timetable.qnu.QnuSavedCredentials
import org.json.JSONObject

/** Reads existing assignments only; submitting or revising remains on the school site. */
@Composable
internal fun HomeworkScreen(credentials: QnuSavedCredentials?, onBack: () -> Unit, onConfigureAccount: () -> Unit, autoRefresh: Boolean = true) {
    val context = LocalContext.current
    val colors = LocalQztcColors.current
    val store = remember(credentials?.username) { HomeworkStore(context, credentials?.username.orEmpty()) }
    val snapshot = remember(store) { store.snapshot() }
    var records by remember(store) { mutableStateOf(runCatching { snapshot?.let(HomeworkPayload::read).orEmpty() }.getOrDefault(emptyList())) }
    var savedAt by remember(store) { mutableLongStateOf(snapshot?.optLong("savedAt") ?: 0L) }
    var message by remember { mutableStateOf("") }
    var courseNames by remember(store) { mutableStateOf(allCourseNames(records, snapshot)) }
    var checkNetwork by remember { mutableStateOf(false) }
    var querying by remember { mutableStateOf(false) }
    LaunchedEffect(credentials?.username) { if (credentials != null && autoRefresh) querying = true }
    var revision by remember { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf("pending") }
    var selectedCourse by rememberSaveable { mutableStateOf("") }
    var showCourses by remember { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var reminderId by rememberSaveable { mutableStateOf<String?>(null) }
    var hiddenIds by remember { mutableStateOf(setOf<String>()) }
    var showDeleted by rememberSaveable { mutableStateOf(false) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var calendarIds by remember { mutableStateOf(AcademicAgendaStore(context).read().map { it.id }.toSet()) }
    val notices = remember { SnackbarHostState() }
    LaunchedEffect(message, querying) { if (message.isNotBlank() && !querying) notices.showSnackbar(message, duration = SnackbarDuration.Short) }
    val choices = remember(store) { HomeworkChoices(context, store.key) }
    LaunchedEffect(store) { hiddenIds = choices.hidden(); HomeworkReminderScheduler.activate(context, store.key) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    var partialPayload by remember { mutableStateOf<JSONObject?>(null) }
    val listState = rememberLazyListState()
    fun remove(item: HomeworkItem) {
        choices.hide(item.id, true); hiddenIds = choices.hidden()
        val agenda = AcademicAgendaStore(context)
        agenda.save(agenda.read().filterNot { it.id == homeworkCalendarId(store.key, item.id) })
        calendarIds = agenda.read().map { it.id }.toSet()
        HomeworkReminderScheduler.reschedule(context)
        if (detailId == item.id) detailId = null
        deleteId = null; message = "已移出，可在已移出中恢复"
    }
    fun addCalendar(item: HomeworkItem) {
        val date = item.deadlineDate ?: run { message = "未提供截止时间，暂无法加入日历"; return }
        val agenda = AcademicAgendaStore(context)
        val entry = AcademicCalendarEntry(homeworkCalendarId(store.key, item.id), "${item.courseName} · ${item.title}".take(160),
            date.toLocalDate(), date.toLocalTime(), "作业截止 · ${item.label()}", false, "homework")
        agenda.save(agenda.read().filterNot { it.id == entry.id } + entry)
        calendarIds = agenda.read().map { it.id }.toSet(); message = "已加入日历"
    }
    fun open(url: String) {
        if (HomeworkPayload.resourceUrl(url)) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { message = "没有可打开学校网页的浏览器" }
    }
    fun refresh() { if (credentials == null) onConfigureAccount() else checkNetwork = true }
    BackHandler { if (detailId != null) detailId = null else onBack() }
    val detail = records.firstOrNull { it.id == detailId }
    val reminder = records.firstOrNull { it.id == reminderId }
    val menuCourses = if (showDeleted) records.filter { it.id in hiddenIds }.map { it.courseName }.distinct() else courseNames
    LaunchedEffect(showDeleted, menuCourses) {
        if (selectedCourse.isNotBlank() && selectedCourse !in menuCourses) selectedCourse = ""
    }
    Box(Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize()) {
        QztcPageTopBar(title = if (detail != null) "作业详情" else "作业查询",
            onBack = { if (detailId != null) detailId = null else onBack() },
            actions = { if (detail == null) QztcTopIconButton(Icons.Outlined.Refresh, "查询作业", onClick = { if (!querying) refresh() })
                else QztcTopIconButton(Icons.Outlined.OpenInNew, "学校原网页", onClick = { open(detail.detailUrl) }) })
        if (detail != null) {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(Brush.verticalGradient(listOf(colors.accentSoft, colors.solid))).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.Top) {
                            Text(detail.courseName, Modifier.weight(1f), style = QztcTypography.meta, color = colors.mut)
                            Surface(color = colors.accentSoft, shape = RoundedCornerShape(8.dp)) {
                                Text(detail.label(), Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = QztcTypography.meta, color = colors.accentDeep)
                            }
                        }
                        Text(detail.title, style = QztcTypography.pageTitle.copy(fontSize = 26.sp, lineHeight = 34.sp), color = colors.ink)
                        Text("截止 ${detail.deadline.ifBlank { "无" }}", style = QztcTypography.body, color = colors.ink)
                    }
                }
                item { GlassSurface(GlassRole.CONTENT, Modifier.fillMaxWidth()) { HomeworkTimedFacts(detail, listOf("截止时间" to detail.deadline, "开始时间" to detail.startAt,
                    "发布人" to detail.publisher, "提交时间" to detail.submittedAt,
                    "提交状态" to detail.label(), "我的成绩" to (detail.grade.ifBlank { "无" } + detail.fullMark.takeIf { it.isNotBlank() }?.let { " / $it" }.orEmpty()),
                    "布置对象" to detail.targets, "批阅方式" to detail.reviewMethod) +
                    (if (homeworkHasStudentReview(detail.reviewMethod)) listOf("互评 / 自评时间" to listOf(detail.reviewStart, detail.reviewEnd).filter(String::isNotBlank).joinToString(" 至 ")) else emptyList()) +
                    listOf("多次提交" to when (detail.multiSubmit) { true -> "允许"; false -> "仅一次"; null -> "无" }) +
                    if (detail.feedback.isNotBlank()) listOf("批阅反馈" to detail.feedback) else emptyList()) } }
                if (detail.needsSubmission() && detail.id !in hiddenIds) item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HomeworkAction("移出", Modifier.weight(1f)) { deleteId = detail.id }
                        HomeworkAction("提醒", Modifier.weight(1f)) { reminderId = detail.id }
                        HomeworkAction(if (homeworkCalendarId(store.key, detail.id) in calendarIds) "已加入日历" else "加入日历", Modifier.weight(1f)) { addCalendar(detail) }
                    }
                }
                item { GlassSurface(GlassRole.CONTENT, Modifier.fillMaxWidth()) { HomeworkFacts(listOf("作业内容" to
                    detail.content.ifBlank { if (detail.detailComplete) "无" else "详情尚未读取，请重新查询" })) } }
            }
        } else {
            LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    val pendingCourses = records.filter { it.needsSubmission() && it.id !in hiddenIds }.map { it.courseName }.distinct()
                    GlassSurface(GlassRole.CONTENT, Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("待提交作业", style = QztcTypography.section.copy(fontSize = QztcTypography.section.fontSize * 1.1f), color = colors.ink)
                            if (savedAt > 0) Text("查询于 " + java.time.Instant.ofEpochMilli(savedAt).atZone(java.time.ZoneId.systemDefault())
                                .format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")), style = QztcTypography.meta.copy(fontSize = 11.sp), color = colors.mut)
                        }
                        Text(if (pendingCourses.isEmpty()) "暂无待提交作业" else pendingCourses.joinToString("、"), style = QztcTypography.body, color = colors.ink)
                    } }
                    if (querying) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp), color = colors.accent)
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("pending" to "待提交", "submitted" to "已提交", "all" to "全部 / 往期").forEach { (id, label) ->
                            FilterChip(selected = filter == id && !showDeleted, onClick = { filter = id; showDeleted = false }, label = { Text(label) })
                        }
                        FilterChip(selected = showDeleted, onClick = { showDeleted = true }, label = { Text("已移出 (${hiddenIds.size})") })
                    }
                    Box {
                        var menuWidth by remember { mutableIntStateOf(0) }
                        val width = with(LocalDensity.current) { menuWidth.toDp() }
                        OutlinedButton(onClick = { showCourses = true }, Modifier.fillMaxWidth().onSizeChanged { menuWidth = it.width }) { Text(if (selectedCourse.isBlank()) "全部课程" else selectedCourse, Modifier.weight(1f)) }
                        DropdownMenu(expanded = showCourses, onDismissRequest = { showCourses = false }, modifier = Modifier.width(width).heightIn(max = 320.dp).background(colors.solid)) {
                            (listOf("") + menuCourses).distinct().forEach { name -> DropdownMenuItem(
                                text = { Text(name.ifBlank { "全部课程" }, style = QztcTypography.body, color = if (name == selectedCourse) colors.accentDeep else colors.ink) }, onClick = { selectedCourse = name; showCourses = false }) }
                        }
                    }
                }
                val filtered = records.filter { (it.id in hiddenIds) == showDeleted && (selectedCourse.isBlank() || it.courseName == selectedCourse) &&
                    (showDeleted || filter == "all" || filter == "submitted" && it.status == "submitted" || filter == "pending" && it.needsSubmission()) }
                    .sortedWith(compareBy<HomeworkItem> { when {
                        it.archiveTerm.isNotBlank() -> 2
                        it.status != "submitted" && it.deadlineDate?.isBefore(java.time.LocalDateTime.now()) != true -> 0
                        else -> 1
                    } }
                        .thenByDescending { if (filter == "pending") java.time.LocalDateTime.MAX else it.deadlineDate }
                        .thenBy { if (filter == "pending") it.deadlineDate else null })
                items(filtered, key = { it.id }) { item ->
                    GlassSurface(GlassRole.CONTENT, Modifier.fillMaxWidth().clickable { detailId = item.id }) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(item.courseName, Modifier.weight(1f), style = QztcTypography.meta, color = colors.mut)
                                Text(item.label(), style = QztcTypography.meta, color = colors.accentDeep)
                            }
                            Text(item.title, style = QztcTypography.courseTitle, color = colors.ink)
                            if (item.archiveTerm.isNotBlank()) Text(item.archiveLabel.ifBlank { "归档记录" }, style = QztcTypography.meta, color = colors.mut)
                            HomeworkTimedFacts(item, listOf("截止时间" to item.deadline, "提交状态" to item.label()) + if (item.grade.isNotBlank()) listOf("成绩" to item.grade) else emptyList())
                            if (!item.detailComplete) Text("详情待补充", style = QztcTypography.meta, color = colors.mut)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (showDeleted) TextButton(onClick = {
                                    choices.hide(item.id, false); hiddenIds = choices.hidden()
                                    showDeleted = false; selectedCourse = ""
                                    filter = if (item.needsSubmission()) "pending" else if (item.status == "submitted") "submitted" else "all"
                                    HomeworkReminderScheduler.reschedule(context)
                                    message = "已移入作业列表"
                                }) { Text("移入") }
                                else if (item.needsSubmission()) {
                                    TextButton(onClick = { reminderId = item.id }) { Text("提醒") }
                                    TextButton(onClick = { addCalendar(item) }) { Text(if (homeworkCalendarId(store.key, item.id) in calendarIds) "已加入日历" else "加入日历") }
                                    TextButton(onClick = { deleteId = item.id }) { Text("移出") }
                                }
                            }
                        }
                    }
                }
                if (filtered.isEmpty()) item {
                    Text(if (records.isEmpty()) "点击查询，读取教学平台的作业记录。" else "此筛选下没有作业。", style = QztcTypography.body, color = colors.mut)
                    if (!querying && credentials == null) Button(onClick = ::refresh) { Text("配置教务账号") }
                }
                if (errors.isNotEmpty()) item {
                    Text("部分课程未完成查询，已保留旧记录", style = QztcTypography.meta, color = colors.mut)
                    errors.take(8).forEach { Text(it, style = QztcTypography.meta, color = colors.mut) }
                }
            }
        }
    }
        SnackbarHost(notices, Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(16.dp))
    }
    val deleting = records.firstOrNull { it.id == deleteId }
    if (deleting != null) AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("移出这项作业？") },
        text = { Text("将从列表移出，并取消它的提醒和日历安排。可在已移出中恢复。") },
        confirmButton = { TextButton(onClick = { remove(deleting) }) { Text("移出") } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text("取消") } })
    if (checkNetwork) CampusNetworkDialog(onDismiss = { checkNetwork = false }, onReady = { checkNetwork = false; revision++; querying = true })
    if (reminder != null) HomeworkReminderDialog(reminder, choices.options(reminder.id), onDismiss = { reminderId = null }) { options ->
        choices.save(reminder.id, options); HomeworkReminderScheduler.reschedule(context); reminderId = null
    }
    if (querying && credentials != null) key(revision) {
        HomeworkAcquisition(credentials,
            onProgress = { payload ->
                message = payload.optString("status", "正在查询…")
                if (payload.has("rows")) runCatching { HomeworkPayload.read(payload) }.onSuccess { incoming ->
                    partialPayload = payload
                    val previous = records.associateBy { it.id }
                    records = (records + incoming.map { previous[it.id]?.refreshedBy(it) ?: it }).associateBy { it.id }.values.toList()
                    courseNames = allCourseNames(records, payload)
                }
            },
            onComplete = { payload ->
                runCatching {
                    val incoming = HomeworkPayload.read(payload)
                    require(incoming.isNotEmpty() || payload.optJSONArray("errors")?.length() == 0) { "没有完成任何课程读取" }
                    val failures = payload.optJSONArray("errors")
                    errors = (0 until (failures?.length() ?: 0)).map { failures!!.getString(it) }
                    val merged = store.mergeAndSave(payload)
                    records = HomeworkPayload.read(merged)
                    savedAt = System.currentTimeMillis(); courseNames = allCourseNames(records, merged)
                    message = "已读取 ${incoming.size} 项作业" + if (errors.isEmpty()) "" else "，部分课程保留上次记录"
                }.onFailure { message = "查询未完成，原有记录保留" }
                querying = false
            }, onFailure = { error ->
                partialPayload?.takeIf { (it.optJSONArray("rows")?.length() ?: 0) > 0 }?.let {
                    runCatching { records = HomeworkPayload.read(store.mergeAndSave(it)); savedAt = System.currentTimeMillis() }
                }
                message = error; querying = false
            })
    }
}

@Composable
private fun HomeworkFacts(rows: List<Pair<String, String>>) {
    val colors = LocalQztcColors.current
    Column(Modifier.fillMaxWidth().border(1.dp, colors.line2)) {
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) HorizontalDivider(color = colors.line2)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Text(label, Modifier.width(90.dp).fillMaxHeight().background(colors.accentSoft).padding(10.dp), style = QztcTypography.meta, color = colors.mut)
                Box(Modifier.width(1.dp).fillMaxHeight().background(colors.line2))
                Text(value.takeUnless { it.isBlank() || it == "平台未提供" } ?: "无", Modifier.weight(1f).padding(10.dp), style = QztcTypography.body, color = colors.ink)
            }
        }
    }
}

internal fun homeworkRemaining(deadline: LocalDateTime?, now: LocalDateTime): String {
    if (deadline == null) return "无"
    val duration = Duration.between(now, deadline)
    if (duration.isNegative || duration.isZero) return "已截止"
    val minutes = duration.toMinutes()
    if (minutes == 0L) return "不足1分钟"
    return buildString {
        if (minutes >= 1440) append("${minutes / 1440}天")
        if (minutes / 60 % 24 > 0) append("${minutes / 60 % 24}小时")
        if (minutes % 60 > 0) append("${minutes % 60}分钟")
    }
}

internal fun homeworkHasStudentReview(method: String) = method.contains("自评") || method.contains("互评")

@Composable private fun HomeworkTimedFacts(item: HomeworkItem, rows: List<Pair<String, String>>) {
    val pending = item.status in setOf("pending", "returned") && item.archiveTerm.isBlank()
    val now by produceState(LocalDateTime.now(), item.id, pending) {
        if (pending) while (true) { delay(60_000); value = LocalDateTime.now() }
    }
    HomeworkFacts(rows.flatMap { row -> if (pending && row.first == "截止时间")
        listOf(row, "剩余时间" to homeworkRemaining(item.deadlineDate, now)) else listOf(row) })
}

@Composable private fun HomeworkAction(label: String, modifier: Modifier, action: () -> Unit) {
    val colors = LocalQztcColors.current
    val shape = RoundedCornerShape(18.dp)
    val base = colors.solid.copy(alpha = 1f)
    val soft = colors.accentSoft.compositeOver(base)
    val symbol = when (label) {
        "移出" -> Icons.Outlined.RemoveCircleOutline
        "提醒" -> Icons.Outlined.Alarm
        else -> Icons.Outlined.CalendarMonth
    }
    Surface(onClick = action, modifier = modifier, shape = shape, color = base,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.accentSoftBorder.compositeOver(base)),
        shadowElevation = 1.dp) {
        Column(Modifier.background(Brush.verticalGradient(listOf(base, soft))).padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(30.dp).background(soft, RoundedCornerShape(10.dp)), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Icon(symbol, null, Modifier.size(18.dp), tint = colors.ink)
            }
            Text(label, style = QztcTypography.meta.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = colors.ink)
        }
    }
}

private fun allCourseNames(records: List<HomeworkItem>, payload: JSONObject?): List<String> = buildList {
    val courses = payload?.optJSONArray("courses")
    repeat(courses?.length() ?: 0) { add(courses!!.getJSONObject(it).optString("name")) }
    addAll(records.map { it.courseName })
}.filter(String::isNotBlank).distinct()

internal fun homeworkCalendarId(account: String, id: String): String = "homework:${account.take(16)}:$id"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeworkReminderDialog(item: HomeworkItem, initial: HomeworkReminderOptions, onDismiss: () -> Unit, onSave: (HomeworkReminderOptions) -> Unit) {
    val context = LocalContext.current
    var enabled by remember(item.id) { mutableStateOf(initial.enabled) }
    var onceEnabled by remember(item.id) { mutableStateOf(initial.once != null) }
    var dailyEnabled by remember(item.id) { mutableStateOf(initial.daily != null) }
    var beforeEnabled by remember(item.id) { mutableStateOf(initial.beforeMinutes != null) }
    val now = java.time.LocalDateTime.now()
    var onceDate by remember(item.id) { mutableStateOf(initial.once?.toLocalDate() ?: now.toLocalDate()) }
    var onceTime by remember(item.id) { mutableStateOf(initial.once?.toLocalTime() ?: now.plusHours(1).toLocalTime().withSecond(0).withNano(0)) }
    var dailyTime by remember(item.id) { mutableStateOf(initial.daily ?: java.time.LocalTime.of(8, 0)) }
    var hours by remember(item.id) { mutableStateOf(((initial.beforeMinutes ?: 60) / 60).toString()) }
    var minutes by remember(item.id) { mutableStateOf(((initial.beforeMinutes ?: 60) % 60).toString()) }
    var error by remember { mutableStateOf("") }
    var timePicker by remember { mutableStateOf<String?>(null) }
    var datePicker by remember { mutableStateOf(false) }
    var permissionAllowed by remember { mutableStateOf(cn.qztc.timetable.notifications.QztcNotifications.canPost(context)) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionAllowed = it }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = LocalQztcColors.current.solid) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("作业提醒", style = QztcTypography.section)
            Text(item.title, style = QztcTypography.body)
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Text("自动提醒", Modifier.weight(1f)); Switch(enabled, { enabled = it }) }
            if (enabled) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(onceEnabled, { onceEnabled = it }); Text("指定日期与时间") }
                if (onceEnabled) Row { TextButton(onClick = { datePicker = true }) { Text(onceDate.toString()) }; TextButton(onClick = { timePicker = "once" }) { Text(onceTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))) } }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(dailyEnabled, { dailyEnabled = it }); Text("每天提醒"); TextButton(onClick = { timePicker = "daily" }) { Text(dailyTime.toString()) } }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(beforeEnabled, { beforeEnabled = it }); Text("截止前提醒") }
                if (beforeEnabled) TextButton(onClick = { timePicker = "before" }) { Text("截止前 ${hours} 小时 ${minutes} 分钟") }
                Text(if (item.needsSubmission()) "可以组合多种提醒；查询到已提交后自动停止。" else "此作业无需提交，提醒不会发送。", style = QztcTypography.meta)
                if (!permissionAllowed) TextButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 33) permissions.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }) { Text("开启通知权限") }
                if (!HomeworkReminderScheduler.exactAllowed(context)) TextButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 31) context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                }) { Text("允许准时提醒（否则系统可能延后）") }
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            Button(onClick = {
                val offset = (hours.toIntOrNull() ?: 0) * 60 + (minutes.toIntOrNull() ?: 0)
                val once = if (onceEnabled) onceDate.atTime(onceTime) else null
                val options = HomeworkReminderOptions(enabled, once, dailyTime.takeIf { dailyEnabled }, offset.takeIf { beforeEnabled })
                error = when {
                    enabled && onceEnabled && once?.isAfter(java.time.LocalDateTime.now()) != true -> "指定提醒时间需晚于当前时间"
                    enabled && onceEnabled && item.deadlineDate?.let { once!!.isAfter(it) } == true -> "提醒时间不能晚于截止时间"
                    enabled && beforeEnabled && (offset !in 1..525600 || (minutes.toIntOrNull() ?: 0) !in 0..59) -> "请输入有效的小时和分钟"
                    enabled && beforeEnabled && item.deadlineDate == null -> "此作业没有截止时间，请使用其他提醒方式"
                    enabled && !onceEnabled && !dailyEnabled && !beforeEnabled -> "请至少选择一种提醒方式"
                    else -> ""
                }
                if (error.isBlank()) onSave(options)
            }, Modifier.fillMaxWidth()) { Text("保存提醒") }
        }
    }
    val pickerMode = timePicker
    if (pickerMode != null) {
        if (pickerMode == "before") QztcHourMinutePicker("截止前提醒", hours.toIntOrNull() ?: 1, minutes.toIntOrNull() ?: 0, 8760,
            onDismiss = { timePicker = null }) { h, m -> hours = h.toString(); minutes = m.toString(); timePicker = null }
        else QztcTimePicker(if (pickerMode == "once") onceTime else dailyTime, onDismiss = { timePicker = null }) { time ->
            if (pickerMode == "once") onceTime = time else dailyTime = time
            timePicker = null
        }
    }
    if (datePicker) {
        QztcDatePicker(onceDate, onDismiss = { datePicker = false }) { onceDate = it; datePicker = false }
    }
}
