package cn.qztc.timetable.education

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
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
            state.refresh()
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
    var expanded by remember(defaultId) { mutableStateOf(setOfNotNull(defaultId)) }
    LazyColumn(
        Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(QztcSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(QztcSpacing.md),
    ) {
        item {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(colors.accentSoft, colors.solid)), RoundedCornerShape(QztcCorners.content)).border(1.dp, colors.accentSoftBorder, RoundedCornerShape(QztcCorners.content)).padding(QztcSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(QztcSpacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("我的成绩", style = QztcTypography.pageTitle, color = colors.ink)
                        Text(if (state.snapshot == null) "全部学期" else "${groups.size} 个学期 · ${state.snapshot!!.records.size} 条成绩",
                            style = QztcTypography.meta, color = colors.mut)
                    }
                    IconButton(onClick = { state.refresh() }, enabled = !state.running && !state.needsLogin) {
                        if (state.running) CircularProgressIndicator(Modifier.size(21.dp), strokeWidth = 2.dp, color = colors.accentDeep)
                        else Icon(Icons.Outlined.Refresh, "刷新全部成绩", tint = colors.accentDeep)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("已读学期" to groups.size.toString(), "成绩记录" to (state.snapshot?.records?.size?.toString() ?: "—")).forEach { (label, value) ->
                        Column(Modifier.weight(1f).background(colors.solid.copy(alpha = .65f), RoundedCornerShape(12.dp)).padding(12.dp)) {
                            Text(value, style = QztcTypography.pageTitle, color = colors.accentDeep)
                            Text(label, style = QztcTypography.compact, color = colors.mut)
                        }
                    }
                }
                Text(state.status, style = QztcTypography.meta, color = if (state.failed) colors.danger else colors.mut)
                state.snapshot?.let { snapshot ->
                    val date = remember(snapshot.updatedAt) { DateTimeFormatter.ofPattern("M月d日 HH:mm").format(Instant.ofEpochMilli(snapshot.updatedAt).atZone(ZoneId.systemDefault())) }
                    Text("上次获取 $date", style = QztcTypography.compact, color = colors.faint)
                }
                if (state.needsLogin) {
                    TextButton(onClick = onConfigureAccount) { Text("设置教务账号 / 完成验证", color = colors.accentDeep) }
                } else if (state.failed && !state.running) {
                    TextButton(onClick = { state.refresh() }) { Text("重新获取", color = colors.accentDeep) }
                }
                if (!state.running && state.snapshot?.records?.isEmpty() == true) {
                    Text("教务系统暂无成绩记录", style = QztcTypography.body, color = colors.mut)
                }
            }
        }
        groups.forEach { semester ->
            val isExpanded = semester.id in expanded
            item(key = "term-" + semester.id) {
                Row(
                    Modifier.fillMaxWidth().background(if (isExpanded) colors.accentSoft else colors.solid, RoundedCornerShape(QztcCorners.content))
                        .semantics { stateDescription = if (isExpanded) "已展开" else "已折叠" }
                        .clickable(role = Role.Button) { expanded = if (isExpanded) expanded - semester.id else expanded + semester.id }
                        .padding(QztcSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(semester.label, style = QztcTypography.section, color = colors.ink)
                        Text("${semester.records.size} 条成绩", style = QztcTypography.meta, color = colors.mut)
                    }
                    Icon(if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        if (isExpanded) "收起成绩" else "展开成绩", tint = colors.mut)
                }
            }
            if (isExpanded) items(semester.records.size, key = { "grade-${semester.id}-$it" }) { i -> GradeHistoryRow(semester.records[i]) }
        }
    }
}

@Composable
private fun GradeHistoryRow(record: QnuGradeRecord) {
    val colors = LocalQztcColors.current
    var detailsOpen by remember(record) { mutableStateOf(false) }
    val failed = record.status in setOf("不及格", "未通过")
    val tint = if (failed) colors.danger else colors.accentDeep
    Column(Modifier.fillMaxWidth().background(colors.solid, RoundedCornerShape(QztcCorners.content))
        .border(1.dp, if (detailsOpen) colors.accentSoftBorder else colors.line, RoundedCornerShape(QztcCorners.content))
        .clickable(role = Role.Button) { detailsOpen = !detailsOpen }.padding(14.dp)) {
    Row(Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QztcSpacing.md)) {
        Box(Modifier.widthIn(min = 54.dp, max = 90.dp).background(if (failed) colors.dangerSoft else colors.accentSoft, RoundedCornerShape(16.dp)).padding(10.dp), contentAlignment = Alignment.Center) {
            Text(record.score ?: "未公布", style = QztcTypography.section, color = tint)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(record.courseName, style = QztcTypography.courseTitle, color = colors.ink)
            val details = listOfNotNull(record.credits?.let { "$it 学分" }, record.gpa?.let { "绩点 $it" })
            if (details.isNotEmpty()) Text(details.joinToString(" · "), style = QztcTypography.meta, color = colors.mut)
            record.examType?.takeUnless { it in setOf("正常考试", "正常", "初修") }?.let {
                Text(it, style = QztcTypography.compact, color = colors.mut)
            }
        }
        Icon(if (detailsOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (detailsOpen) "收起课程成绩详情" else "展开课程成绩详情", tint = colors.faint)
    }
    if (detailsOpen) {
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.line)
        val order = listOf("平时", "期中", "期末", "实验", "口语", "过程性成绩", "总评", "绩点", "学分", "学时", "学年", "学期", "开课院系", "课程号", "课程名", "课序号", "课组", "考核方式", "选课属性", "备注", "考试性质", "是否缓考", "及格标志")
        val fields = if (record.officialFields.isNotEmpty()) {
            (order.filter { it in record.officialFields } + record.officialFields.keys.filterNot { it in order })
                .map { it to record.officialFields[it]?.takeIf(String::isNotBlank) }
        } else listOf("学年" to record.year, "学期" to record.term, "课程号" to record.courseCode,
            "课序号" to record.courseSequence, "选课属性" to record.selection, "考核方式" to record.assessment,
            "考试性质" to record.examType, "及格标志" to record.status)
        if (record.officialFields.isEmpty()) Text("此缓存尚未包含完整字段，请刷新成绩补全", style = QztcTypography.compact, color = colors.mut)
        fields.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (label, value) -> Column(Modifier.weight(1f)) {
                    Text(label, style = QztcTypography.compact, color = colors.faint)
                    Text(value ?: "未提供", style = QztcTypography.body, color = colors.ink)
                } }
            }
        }
    }
    }
}
