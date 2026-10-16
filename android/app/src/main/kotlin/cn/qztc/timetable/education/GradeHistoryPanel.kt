package cn.qztc.timetable.education

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import cn.qztc.timetable.core.ui.theme.*
import cn.qztc.timetable.integration.qnu.QnuGradeRecord
import cn.qztc.timetable.integration.qnu.QnuGradeSemesters
import cn.qztc.timetable.qnu.QnuSavedCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Stable
internal class GradeHistoryState {
    var snapshot by mutableStateOf<GradeSnapshot?>(null)
    var running by mutableStateOf(false)
    var status by mutableStateOf("正在读取本地成绩…")
    var needsLogin by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var attempt by mutableIntStateOf(0)
    fun refresh() {
        attempt += 1; running = true; failed = false; needsLogin = false
        status = "正在连接教务系统…"
    }
}

@Composable
internal fun rememberGradeHistory(credentials: QnuSavedCredentials?): GradeHistoryState {
    val context = LocalContext.current
    val state = remember(credentials) { GradeHistoryState() }
    LaunchedEffect(state) {
        if (credentials == null) {
            state.status = "请先在导入课表时保存教务账号"; state.needsLogin = true
        } else {
            state.snapshot = withContext(Dispatchers.IO) { GradeCache(context).load(credentials.username) }
            state.status = if (state.snapshot == null) "点击右上角刷新获取成绩" else ""
        }
    }
    return state
}

@Composable
internal fun GradeHistoryLoader(state: GradeHistoryState, credentials: QnuSavedCredentials?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember(state, state.attempt) { mutableStateOf(false) }
    if (state.running && !saving && credentials != null) key(state.attempt) {
        GradeAcquisition(credentials, onStatus = { state.status = it }, onSuccess = { records ->
            saving = true
            state.status = "正在保存成绩…"
            scope.launch {
                val snapshot = GradeSnapshot(records, System.currentTimeMillis())
                val saved = withContext(Dispatchers.IO) { runCatching { GradeCache(context).save(credentials.username, snapshot) }.isSuccess }
                state.snapshot = snapshot
                state.running = false
                state.failed = !saved
                state.status = if (saved) "已获取全部学期成绩" else "成绩已读取，本地保存失败，请重试"
            }
        }, onFailure = { message, login ->
            state.running = false; state.failed = true; state.needsLogin = login; state.status = message
        })
    }
}

@Composable
internal fun GradeHistoryPanel(state: GradeHistoryState, onConfigureAccount: () -> Unit) {
    val colors = LocalQztcColors.current
    val groups = remember(state.snapshot) { QnuGradeSemesters.group(state.snapshot?.records.orEmpty()) }
    val defaultId = remember(groups.map { it.id }) { QnuGradeSemesters.defaultExpanded(groups) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(defaultId) { if (selectedId == null) selectedId = defaultId }
    var query by rememberSaveable { mutableStateOf("") }
    var filterField by rememberSaveable { mutableStateOf("全部") }
    var filterValue by rememberSaveable { mutableStateOf("全部") }
    var valueFocused by rememberSaveable { mutableStateOf(false) }
    var fieldMenuOpen by remember { mutableStateOf(false) }
    var valueMenuOpen by remember { mutableStateOf(false) }
    val focusOffset by animateFloatAsState(if (valueFocused) 1f else 0f, label = "filter-focus")
    val selectedSemester = groups.firstOrNull { it.id == selectedId } ?: groups.firstOrNull()
    val allRecords = remember(selectedSemester, state.snapshot) {
        val history = state.snapshot?.records.orEmpty()
        selectedSemester?.records.orEmpty().groupBy { it.courseCode.orEmpty() + "|" + it.courseName }.values.map { attempts ->
            val combined = mergeAttempts(attempts)
            inheritMakeupGpa(combined, history)
        }
    }
    val weightedCredits = remember(allRecords) { allRecords.mapNotNull { record ->
        val score = record.score?.toDoubleOrNull() ?: return@mapNotNull null
        val credit = record.credits?.toDoubleOrNull() ?: return@mapNotNull null
        score to credit
    } }
    val weightedAverage = remember(weightedCredits) { weightedCredits.takeIf { it.sumOf { row -> row.second } > 0 }?.let { rows ->
        rows.sumOf { it.first * it.second } / rows.sumOf { it.second }
    } }
    val averageGpa = remember(allRecords) { allRecords.mapNotNull { it.gpa?.toDoubleOrNull() }.takeIf { it.isNotEmpty() }?.average() }
    val credits = remember(allRecords) { allRecords.mapNotNull { it.credits?.toDoubleOrNull() }.sum() }
    val filterFields = listOf("开课院系", "选课类型", "考试性质", "是否缓考", "学分", "绩点", "学时")
    fun fieldValue(record: QnuGradeRecord, field: String): String? = when (field) {
        "选课类型" -> record.selection
        "考试性质" -> record.examType
        "学分" -> record.credits
        "绩点" -> record.gpa
        "是否缓考" -> record.officialFields["是否缓考"] ?: record.officialFields["缓考"]
        else -> record.officialFields[field]?.takeIf(String::isNotBlank)
    }
    val filterValues = remember(allRecords, filterField) {
        if (filterField == "全部") emptyList() else allRecords.mapNotNull { fieldValue(it, filterField) }.distinct().sorted()
    }
    LaunchedEffect(filterValues, filterField) {
        if (filterValues.size == 1) filterValue = filterValues.single()
        else if (filterValue !in filterValues) filterValue = "全部"
    }
    val visibleRecords = remember(allRecords, query, filterField, filterValue) {
        allRecords.filter { (query.isBlank() || it.courseName.contains(query, true)) &&
            (filterField == "全部" || filterValue == "全部" || fieldValue(it, filterField) == filterValue) }
    }
    LazyColumn(
        Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(QztcSpacing.md),
        verticalArrangement = Arrangement.spacedBy(QztcSpacing.sm),
    ) {
        item {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(colors.accentSoft, colors.solid)), RoundedCornerShape(QztcCorners.content)).border(1.dp, colors.accentSoftBorder, RoundedCornerShape(QztcCorners.content)).height(304.dp).padding(QztcSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("我的成绩", style = QztcTypography.pageTitle, color = colors.ink)
                            Text("${visibleRecords.size} 条成绩", style = QztcTypography.meta, color = colors.accentDeep)
                        }
                    }
                    IconButton(onClick = { state.refresh() }, enabled = !state.running && !state.needsLogin) {
                        if (state.running) CircularProgressIndicator(Modifier.size(21.dp), strokeWidth = 2.dp, color = colors.accentDeep)
                        else Icon(Icons.Outlined.Refresh, "刷新全部成绩", tint = colors.accentDeep)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("加权平均分" to (weightedAverage?.let { "%.1f".format(it) } ?: "—"),
                        "平均绩点" to (averageGpa?.let { "%.2f".format(it) } ?: "—"),
                        "已修学分" to (if (allRecords.isEmpty()) "—" else "%.1f".format(credits))).forEach { (label, value) ->
                        Column(Modifier.weight(1f).background(colors.solid.copy(alpha = .65f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 7.dp)) {
                            Text(value, style = QztcTypography.section, color = colors.accentDeep, maxLines = 1)
                            Text(label, style = QztcTypography.compact, color = colors.mut)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("按课程查询") })
                Row(Modifier.fillMaxWidth()) {
                    Text("筛选字段", Modifier.weight(1f).wrapContentWidth(Alignment.CenterHorizontally), style = QztcTypography.compact, color = colors.mut)
                    Text("筛选值", Modifier.weight(1f).wrapContentWidth(Alignment.CenterHorizontally), style = QztcTypography.compact, color = colors.mut)
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    Box(Modifier.offset(x = maxWidth * focusOffset / 2).width(maxWidth / 2).height(44.dp)
                        .background(colors.accentSoft))
                    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            GradeFilterText(filterField, "筛选字段", true) { valueFocused = false; valueMenuOpen = false; fieldMenuOpen = !fieldMenuOpen }
                            DropdownMenu(expanded = fieldMenuOpen, onDismissRequest = { fieldMenuOpen = false }, modifier = Modifier.heightIn(max = 240.dp).background(colors.solid)) {
                                (listOf("全部") + filterFields).forEach { field ->
                                    GradeFilterMenuItem(field, field == filterField) {
                                        fieldMenuOpen = false
                                        filterField = field
                                        val next = if (field == "全部") emptyList() else allRecords.mapNotNull { fieldValue(it, field) }.distinct().sorted()
                                        filterValue = if (next.size == 1) next.single() else "全部"
                                        valueFocused = next.size > 1
                                        valueMenuOpen = next.size > 1
                                    }
                                }
                            }
                        }
                        Box(Modifier.width(1.dp).height(44.dp).background(colors.line))
                        Box(Modifier.weight(1f)) {
                            GradeFilterText(filterValue, "筛选值", filterField != "全部" && filterValues.isNotEmpty()) {
                                valueFocused = true; fieldMenuOpen = false; valueMenuOpen = !valueMenuOpen
                            }
                            DropdownMenu(expanded = valueMenuOpen, onDismissRequest = { valueMenuOpen = false }, modifier = Modifier.heightIn(max = 240.dp).background(colors.solid)) {
                                (listOf("全部") + filterValues).forEach { value ->
                                    GradeFilterMenuItem(value, value == filterValue) {
                                        filterValue = value; valueMenuOpen = false; valueFocused = false
                                    }
                                }
                            }
                        }
                    }
                }
                state.snapshot?.let { snapshot ->
                    val date = remember(snapshot.updatedAt) { DateTimeFormatter.ofPattern("M月d日 HH:mm").format(Instant.ofEpochMilli(snapshot.updatedAt).atZone(ZoneId.systemDefault())) }
                    Text("上次获取 $date", style = QztcTypography.compact, color = colors.faint)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (state.needsLogin) {
                    TextButton(onClick = onConfigureAccount) { Text("设置教务账号 / 完成验证", color = colors.accentDeep) }
                }
                if (!state.running && state.snapshot?.records?.isEmpty() == true) {
                    Text("教务系统暂无成绩记录", style = QztcTypography.body, color = colors.mut)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEach { semester ->
                    FilterChip(selected = selectedId == semester.id, onClick = { selectedId = semester.id }, label = { Text(semester.shortLabel) })
                }
            }
        }
        itemsIndexed(visibleRecords, key = { index, record -> "${selectedSemester?.id}|$index|${record.courseCode}|${record.courseName}" }) { _, record -> GradeHistoryRow(record) }
    }
}

@Composable
private fun GradeFilterText(value: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalQztcColors.current
    Row(Modifier.fillMaxWidth().height(44.dp)
        .clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled, role = Role.DropdownList, onClickLabel = label, onClick = onClick)
        .padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(value, Modifier.weight(1f), style = QztcTypography.body, color = if (enabled) colors.ink else colors.faint,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Outlined.ExpandMore, label, Modifier.size(18.dp), tint = if (enabled) colors.mut else colors.faint)
    }
}

@Composable
private fun GradeFilterMenuItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalQztcColors.current
    DropdownMenuItem(text = { Text(label, style = QztcTypography.body, color = if (selected) colors.accentDeep else colors.ink) },
        onClick = onClick, modifier = Modifier.background(if (selected) colors.accentSoft else androidx.compose.ui.graphics.Color.Transparent))
}

internal fun mergeAttempts(records: List<QnuGradeRecord>): QnuGradeRecord {
    fun isRetake(r: QnuGradeRecord) = r.examType?.contains("补考") == true || r.examType?.contains("重修") == true
    val retake = records.lastOrNull(::isRetake)
    val normal = records.lastOrNull { !isRetake(it) }
    if (retake == null) return records.last()
    if (normal == null) return retake
    val fields = normal.officialFields.toMutableMap()
    normal.score?.takeIf { it.isNotBlank() }?.let { fields["正常考试"] = it }
    retake.score?.let { fields["总评"] = it }
    val failed = records.lastOrNull { candidate ->
        !isRetake(candidate) && ((candidate.score?.toDoubleOrNull() ?: 100.0) < 60.0 || candidate.status?.contains("不及格") == true || candidate.status?.contains("未通过") == true)
    }
    val inheritedGpa = if (retake.examType?.contains("补考") == true) failed?.gpa ?: normal.gpa else retake.gpa
    inheritedGpa?.let { fields["绩点"] = it }
    return normal.copy(score = retake.score, gpa = inheritedGpa, status = retake.status, examType = retake.examType, officialFields = fields)
}

internal fun inheritMakeupGpa(record: QnuGradeRecord, history: List<QnuGradeRecord>): QnuGradeRecord {
    if (record.examType?.contains("补考") != true) return record
    val order = QnuGradeSemesters.order(record.year.orEmpty(), record.term.orEmpty())
    val original = history.filter { candidate ->
        (if (!record.courseCode.isNullOrBlank()) candidate.courseCode == record.courseCode else candidate.courseName == record.courseName) &&
            candidate.examType?.contains("补考") != true && candidate.examType?.contains("重修") != true &&
            (candidate.score?.toDoubleOrNull()?.let { it < 60 } == true || candidate.status in setOf("不及格", "未通过")) &&
            QnuGradeSemesters.order(candidate.year.orEmpty(), candidate.term.orEmpty())?.let { order == null || it <= order } != false
    }.maxByOrNull { QnuGradeSemesters.order(it.year.orEmpty(), it.term.orEmpty()) ?: Int.MIN_VALUE }
    return original?.gpa?.let { record.copy(gpa = it, officialFields = record.officialFields + ("绩点" to it)) } ?: record
}

@Composable
private fun GradeHistoryRow(record: QnuGradeRecord) {
    val colors = LocalQztcColors.current
    var detailsOpen by remember(record) { mutableStateOf(false) }
    val failed = record.status in setOf("不及格", "未通过")
    val tint = if (failed) colors.danger else colors.accentDeep
    Column(Modifier.fillMaxWidth().background(colors.solid, RoundedCornerShape(QztcCorners.content))
        .border(1.dp, if (detailsOpen) colors.accentSoftBorder else colors.line, RoundedCornerShape(QztcCorners.content))
        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { detailsOpen = !detailsOpen }.padding(horizontal = 10.dp, vertical = 9.dp)) {
    Row(Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QztcSpacing.md)) {
        val score = record.score?.toDoubleOrNull()?.coerceIn(0.0, 100.0)
        Box(Modifier.size(48.dp).drawBehind {
            drawArc(colors.line, -90f, 360f, false, style = Stroke(3.dp.toPx()))
            if (score != null) drawArc(if (failed) colors.danger else colors.accentDeep, -90f, (score / 100f * 360f).toFloat(), false,
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }, contentAlignment = Alignment.Center) {
            Text(record.score ?: "未公布", style = QztcTypography.section, color = tint)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val retakeLabel = record.examType?.takeIf { it.contains("补考") }?.let { " · 补考" }.orEmpty()
            Text(record.courseName + retakeLabel, style = QztcTypography.courseTitle, color = colors.ink)
            val details = listOfNotNull(record.credits?.let { "$it 学分" }, record.gpa?.let { "绩点 $it" })
            if (details.isNotEmpty()) Text(details.joinToString(" · "), style = QztcTypography.meta, color = colors.mut)
        }
        Icon(if (detailsOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (detailsOpen) "收起课程成绩详情" else "展开课程成绩详情", tint = colors.faint)
    }
    if (detailsOpen) {
        val order = listOf("平时", "期中", "期末", "实验", "口语", "过程性成绩", "总评", "正常考试", "绩点", "学分", "学时", "开课院系", "课程号", "课程名", "课序号", "课组", "考核方式", "选课属性", "备注", "考试性质", "是否缓考")
        val optional = setOf("实验", "口语", "过程性成绩")
        val hidden = setOf("学年", "学期", "及格", "及格标志")
        val fields = if (record.officialFields.isNotEmpty()) {
            (order.filter { it in record.officialFields } + record.officialFields.keys.filterNot { it in order })
                .mapNotNull { label -> record.officialFields[label]?.takeIf(String::isNotBlank)?.let { label to it } }
                .filter { it.first !in hidden && (it.first !in optional || it.second.isNotBlank()) }
        } else listOfNotNull(
            record.courseCode?.let { "课程号" to it },
            record.courseSequence?.let { "课序号" to it },
            record.selection?.let { "选课属性" to it },
            record.assessment?.let { "考核方式" to it },
            record.examType?.let { "考试性质" to it }
        )
        if (record.officialFields.isEmpty()) Text("此缓存尚未包含完整字段，请刷新成绩补全", style = QztcTypography.compact, color = colors.mut)
        val scoreLabels = setOf("平时", "期中", "期末", "实验", "口语", "过程性成绩", "总评", "正常考试")
        val creditLabels = setOf("学分", "绩点", "学时")
        val examLabels = setOf("考核方式", "考试性质", "是否缓考", "缓考", "备注")
        val sections = listOf("成绩构成" to fields.filter { it.first in scoreLabels },
            "课程与教学" to fields.filter { it.first !in scoreLabels + creditLabels + examLabels },
            "学分与课时" to fields.filter { it.first in creditLabels },
            "考核与考试" to fields.filter { it.first in examLabels })
        sections.filter { it.second.isNotEmpty() }.forEach { (title, values) -> GradeDetailSection(title, values, colors) }
    }
    }
}

@Composable
private fun GradeDetailSection(title: String, fields: List<Pair<String, String>>, colors: QztcColors) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = colors.line)
        Text("  $title  ", style = QztcTypography.compact, color = colors.mut)
        HorizontalDivider(Modifier.weight(1f), color = colors.line)
    }
    val columns = if (title == "成绩构成" || title == "学分与课时") 3 else 2
    fields.chunked(columns).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { (label, value) -> Column(Modifier.weight(1f)) {
                Text(label, style = QztcTypography.compact, color = colors.faint)
                Text(value, style = QztcTypography.body, color = colors.ink)
            } }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}
