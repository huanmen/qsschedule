package cn.qztc.timetable.education

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.ui.theme.*
import cn.qztc.timetable.integration.qnu.*
import cn.qztc.timetable.qnu.QnuSavedCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

// Reuse the encrypted, account-bound cache; each group carries its official fields.
private fun creditCache(context: Context) = GradeCache(context, "qnu_credit_progress")
private fun QnuStudyProgress.snapshot() = GradeSnapshot((groups + listOfNotNull(total)).map { g ->
    QnuGradeRecord(null, g.name, null, null, g.earned?.toString(), null, null, null,
        officialFields = buildMap {
            put("教学计划", g.plan); put("课组标识", g.id)
            g.required?.let { put("学分要求", it.toString()) }; g.requiredCount?.let { put("门数要求", it.toString()) }
            g.passedCount?.let { put("已通过门数", it.toString()) }; g.qualified?.let { put("是否合格", it.toString()) }
            put("课程明细", JSONArray().apply { g.courses.forEach { c -> put(JSONObject().apply {
                put("name", c.name); put("code", c.code); put("credits", c.credits); put("status", c.status)
            }) } }.toString())
            if (g === total) put("汇总", "true")
        })
}, System.currentTimeMillis())
private fun GradeSnapshot.progress(): QnuStudyProgress {
    fun group(r: QnuGradeRecord) = QnuCreditGroup(r.courseName, r.officialFields["学分要求"]?.toDoubleOrNull(),
        r.credits?.toDoubleOrNull(), r.officialFields["门数要求"]?.toIntOrNull(),
        r.officialFields["已通过门数"]?.toIntOrNull(), r.officialFields["是否合格"]?.toBooleanStrictOrNull(),
        runCatching { JSONArray(r.officialFields["课程明细"] ?: "[]").let { rows -> (0 until rows.length()).map { i ->
            val c = rows.getJSONObject(i)
            QnuCreditCourse(c.optString("code").takeUnless { it.isBlank() || it == "null" }, c.getString("name"),
                c.optString("credits").toDoubleOrNull(), c.optString("status").takeUnless { it.isBlank() || it == "null" })
        } } }.getOrDefault(emptyList()), r.officialFields["教学计划"].orEmpty(), r.officialFields["课组标识"].orEmpty())
    return QnuStudyProgress(records.filter { it.officialFields["汇总"] != "true" }.map(::group), records.firstOrNull { it.officialFields["汇总"] == "true" }?.let(::group))
}

@Composable
internal fun CreditProgressPanel(credentials: QnuSavedCredentials?, onConfigureAccount: () -> Unit, initialSnapshot: GradeSnapshot? = null, active: Boolean = true) {
    val context = LocalContext.current
    val colors = LocalQztcColors.current
    var snapshot by remember(credentials) { mutableStateOf(initialSnapshot) }
    var running by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("") }
    var adviceOpen by remember { mutableStateOf(false) }
    var expandedGroups by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var toastMessage by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf<QnuStudyProgress?>(initialSnapshot?.progress()) }
    LaunchedEffect(snapshot) { progress = withContext(Dispatchers.Default) { snapshot?.progress() } }
    LaunchedEffect(status, running) {
        if (status.isNotBlank()) { toastMessage = status; delay(3500); toastMessage = "" }
    }
    LaunchedEffect(credentials) {
        if (credentials != null) snapshot = withContext(Dispatchers.IO) { creditCache(context).load(credentials.username) }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(QztcSpacing.md)) {
            item {
                Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(colors.accentSoft, colors.solid)), RoundedCornerShape(QztcCorners.content))
                    .border(1.dp, colors.accentSoftBorder, RoundedCornerShape(QztcCorners.content)).height(304.dp).padding(QztcSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("学分进度", Modifier.weight(1f), style = QztcTypography.pageTitle, color = colors.ink)
                        IconButton(onClick = { running = true; attempt++; status = "正在获取修读进程…" }, enabled = credentials != null && !running) {
                            if (running) CircularProgressIndicator(Modifier.size(21.dp), strokeWidth = 2.dp, color = colors.accentDeep)
                            else Icon(Icons.Outlined.Refresh, "刷新学分", tint = colors.accentDeep)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        val total = progress?.total
                        val required = total?.required
                        val earned = total?.earned
                        val ratio = if (required != null && required > 0 && earned != null) (earned / required).coerceIn(0.0, 1.0) else null
                            Box(Modifier.size(86.dp).padding(3.dp).drawBehind {
                                drawArc(colors.line, -90f, 360f, false, style = Stroke(5.dp.toPx()))
                                ratio?.let { drawArc(colors.accentDeep, -90f, (it * 360).toFloat(), false, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round)) }
                            }, contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(creditNumber(earned), style = QztcTypography.pageTitle, color = colors.ink)
                                    Text("已获学分", style = QztcTypography.compact, color = colors.mut)
                                }
                            }
                        Column(Modifier.weight(1f)) {
                            val pending = progress?.groups?.filter { it.qualified == false }
                            Text(if (progress == null) "尚未获取修读进程" else if (progress?.groups?.any { it.qualified == null } == true) "课组状态待更新" else "${pending?.size} 个课组待完成", style = QztcTypography.courseTitle, color = colors.ink)
                            pending.orEmpty().take(2).forEach { g ->
                                val creditGap = g.required?.let { r -> g.earned?.let { (r - it).coerceAtLeast(0.0) } }
                                val countGap = g.requiredCount?.let { r -> g.passedCount?.let { (r - it).coerceAtLeast(0) } }
                                val gap = if (creditGap != null && creditGap > 0) "尚差 ${creditNumber(creditGap)} 学分" else if (countGap != null && countGap > 0) "尚差 $countGap 门" else "待完成"
                                Text("${g.name} $gap", style = QztcTypography.compact, color = colors.mut)
                            }
                            progress?.groups?.firstOrNull { it.name.contains("跨学科") }?.let { cross ->
                                val requirement = cross.required?.takeIf { it > 0 }?.let { " / ${creditNumber(it)}" }.orEmpty()
                                Text("跨学科 ${creditNumber(cross.earned)}$requirement 学分", Modifier.padding(top = 8.dp), style = QztcTypography.meta, color = colors.accentDeep)
                            }
                            progress?.groups?.firstOrNull { it.name.contains("艺术") }?.let { art ->
                                Text("其中艺术 ${creditNumber(art.earned)} / ${creditNumber(art.required)}", style = QztcTypography.compact, color = colors.mut)
                            }
                        }
                    }
                    val cross = progress?.groups?.firstOrNull { it.name.contains("跨学科") }
                    val art = progress?.groups?.firstOrNull { it.name.contains("艺术") }
                    if (cross?.earned != null && art?.earned != null && (cross.earned!! < 7 || art.earned!! < 2)) {
                        Text("选修学分解读与选课建议 ›", style = QztcTypography.meta, color = colors.accentDeep,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp).clickable { adviceOpen = true }.padding(vertical = 6.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val total = progress?.total
                        val remaining = total?.required?.let { r -> total.earned?.let { (r - it).coerceAtLeast(0.0) } }
                        val ratio = total?.required?.takeIf { it > 0 }?.let { r -> total.earned?.let { it / r * 100 } }
                        listOf("尚差总学分" to creditNumber(remaining), "已达标课组" to (progress?.let { "${it.groups.count { g -> g.qualified == true }} / ${it.groups.size}" } ?: "—"),
                            "总学分进度" to (ratio?.let { "%.1f%%".format(it) } ?: "—")).forEach { (label, value) ->
                            Column(Modifier.weight(1f).heightIn(min = 76.dp).background(colors.solid.copy(alpha = .65f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 14.dp), verticalArrangement = Arrangement.Center) {
                                Text(value, style = QztcTypography.section, color = colors.accentDeep, maxLines = 1)
                                Text(label, style = QztcTypography.compact, color = colors.mut)
                            }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (credentials == null) TextButton(onClick = onConfigureAccount) { Text("设置教务账号") }
                }
            }
            progress?.groups.orEmpty().forEachIndexed { index, group ->
                val groupKey = "credit:$index:${group.id}"
                val expanded = groupKey in expandedGroups
                item(key = groupKey) {
                val shape = if (expanded && group.courses.isNotEmpty()) RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp) else RoundedCornerShape(8.dp)
                Column(Modifier.fillMaxWidth().padding(top = 10.dp).background(colors.solid, shape).clickable(remember { MutableInteractionSource() }, indication = null) {
                    expandedGroups = if (expanded) expandedGroups - groupKey else expandedGroups + groupKey
                }.padding(14.dp)) {
                    Row {
                        Text(group.name, Modifier.weight(1f), style = QztcTypography.courseTitle, color = colors.ink)
                        Text(when (group.qualified) { true -> "已达标"; false -> "未达标"; null -> "状态未提供" }, style = QztcTypography.compact, color = colors.mut)
                        Icon(if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                            if (expanded) "收起课组课程" else "展开课组课程", Modifier.size(18.dp), tint = colors.faint)
                    }
                    if (group.plan.isNotBlank()) Text(group.plan, style = QztcTypography.compact, color = colors.mut)
                    val required = group.required
                    val earned = group.earned
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${creditNumber(earned)} / ${creditNumber(required)} 学分", style = QztcTypography.meta, color = colors.accentDeep)
                        Text("${group.passedCount ?: "—"} / ${group.requiredCount ?: "—"} 门", style = QztcTypography.meta, color = colors.mut)
                    }
                    group.progressRatio?.let { ratio -> LinearProgressIndicator(progress = { ratio },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = colors.accentDeep, trackColor = colors.line,
                        drawStopIndicator = {}) }
                    if (expanded) {
                        if (group.courses.isEmpty()) Text("课程明细尚未获取，请刷新学分", style = QztcTypography.meta, color = colors.mut)
                    }
                }
                }
                if (expanded) itemsIndexed(group.courses, key = { courseIndex, _ -> "$groupKey:course:$courseIndex" }) { courseIndex, course ->
                    val shape = if (courseIndex == group.courses.lastIndex) RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp) else RoundedCornerShape(0.dp)
                    Column(Modifier.fillMaxWidth().background(colors.solid, shape).padding(horizontal = 14.dp)) {
                            HorizontalDivider(color = colors.line)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(course.name, Modifier.weight(1f).padding(vertical = 10.dp), style = QztcTypography.body, color = colors.ink)
                                Text("${creditNumber(course.credits)} 学分", style = QztcTypography.compact, color = colors.mut)
                                Text(creditCourseStatus(course.status), style = QztcTypography.compact, color = colors.accentDeep)
                            }
                            }
                }
            }
        }
        if (active && toastMessage.isNotBlank()) Snackbar(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)) {
            Text(toastMessage)
        }
        if (active && adviceOpen) ElectiveAdviceSheet(progress, onDismiss = { adviceOpen = false })
        if (running && credentials != null) key(attempt) {
            GradeAcquisition(credentials, onStatus = { status = it }, onSuccess = {}, onFailure = { message, _ -> running = false; status = message }, scriptAsset = "academic/credits.js", onStudyProgress = { html, complete ->
                    runCatching {
                        val previous = progress
                        val mergedProgress = withContext(Dispatchers.Default) {
                        val incoming = QnuStudyProgressParser.parse(html)
                        incoming.copy(total = incoming.total ?: previous?.total, groups = incoming.groups.map { group ->
                            val cached = previous?.groups?.singleOrNull { it.id.isNotBlank() && it.id == group.id }
                                ?: previous?.groups?.singleOrNull { it.name == group.name && it.plan == group.plan }
                                    ?.takeIf { incoming.groups.count { g -> g.name == group.name && g.plan == group.plan } == 1 }
                            if (group.courses.isEmpty()) group.copy(courses = cached?.courses.orEmpty()) else group
                        }) }
                        val merged = withContext(Dispatchers.Default) { mergedProgress.snapshot() }
                        withContext(Dispatchers.IO) { creditCache(context).save(credentials.username, merged) }
                        snapshot = merged
                        val courseCount = mergedProgress.groups.sumOf { it.courses.size }
                        val missing = mergedProgress.groups.count { it.courses.isEmpty() }
                        status = if (complete) "修读进程已更新 · $courseCount 条课程明细" +
                            (if (missing > 0) " · $missing 个课组未提供明细" else "") +
                            ""
                            else "课组概况已保存 · 已获取 $courseCount 条课程明细"
                    }.onFailure {
                        if (it is CancellationException) throw it
                        status = "修读进程读取失败，原有记录已保留"
                    }
                    if (complete) running = false
            })
        }
    }
}

private fun creditNumber(value: Double?): String = value?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "—"

internal fun electiveRecommendation(total: Double, art: Double): String {
    val totalGap = (7 - total).coerceAtLeast(0.0)
    val artGap = (2 - art).coerceAtLeast(0.0)
    if (total >= 7 && art >= 2) return "选修学分已达标，无需补选。"
    if (total + artGap > 8) return "先核对艺术课程归类或替换认定，继续加课会超过 8 学分。"
    if (totalGap <= 2 && artGap <= 2) {
        val amount = maxOf(totalGap, artGap)
        return "已获跨学科 ${creditNumber(total)} 分，其中艺术 ${creditNumber(art)} 分。优先选一门 ${creditNumber(amount)} 学分的${if (artGap > 0) "艺术" else "其他"}课；没有对应课程时拆成 1 分课。"
    }
    return "尚差跨学科 ${creditNumber(totalGap)} 分，其中艺术 ${creditNumber(artGap)} 分。优先补艺术类，再补其他课；尽量选择 2 分课减少门数，分学期完成。线上学分是否已达 4 分上限需按实际授课方式核对。"
}

internal fun creditCourseStatus(status: String?): String = when (status) {
    "最高成绩已及格", "最高成绩已及格再次选课", "及格", "通过" -> "已及格"
    "已选课成绩未知", "已选课", "修读中" -> "已选课程成绩未知"
    "未选课", "未修读" -> "未选课"
    else -> status?.replace("最高成绩", "") ?: "状态未提供"
}
