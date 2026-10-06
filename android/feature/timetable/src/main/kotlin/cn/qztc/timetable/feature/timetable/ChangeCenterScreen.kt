package cn.qztc.timetable.feature.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.data.repository.ChangeSetRecord
import cn.qztc.timetable.core.data.repository.ChangeSetStatus
import cn.qztc.timetable.core.domain.diff.MeetingChange
import cn.qztc.timetable.core.domain.diff.CourseChange
import cn.qztc.timetable.core.domain.model.CourseMeeting
import cn.qztc.timetable.core.ui.glass.GlassRole
import cn.qztc.timetable.core.ui.glass.GlassSurface
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcPageTopBar
import cn.qztc.timetable.core.ui.theme.QztcSpacing
import cn.qztc.timetable.core.ui.theme.QztcTypography
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class ChangeConfirmation(val id: String, val action: ChangeAction)
private enum class ChangeAction { APPLY, IGNORE, REVERT }

@Composable
fun ChangeCenterScreen(
    changeSets: List<ChangeSetRecord>,
    onBack: () -> Unit,
    onApply: (String) -> Unit,
    onIgnore: (String) -> Unit,
    onRevert: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalQztcColors.current
    val pending = changeSets.filter { it.status == ChangeSetStatus.PENDING }.take(1)
    // 首次导入的快照是空课表；它属于导入记录，不应提供一个会清空全部课程的“恢复”按钮。
    val history = changeSets.filter { it.status != ChangeSetStatus.PENDING && !it.snapshot.isEmpty }.take(1)
    var confirmation by remember { mutableStateOf<ChangeConfirmation?>(null) }
    Column(modifier.fillMaxSize().background(colors.appBg)) {
        QztcPageTopBar(
            title = "变化中心",
            subtitle = "确认后才会改动本机课表",
            onBack = onBack,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(QztcSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(QztcSpacing.md),
        ) {
            item {
                GlassSurface(GlassRole.CONTENT, Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(QztcSpacing.lg), horizontalArrangement = Arrangement.SpaceBetween) {
                        Summary("待处理", pending.size.toString(), colors.warn)
                        Summary("改动", pending.sumOf { it.changedCount }.toString(), colors.accent)
                        Summary("增减", pending.sumOf { it.addedCount + it.removedCount }.toString(), colors.mut)
                    }
                }
            }
            if (pending.isEmpty()) {
                item { Text("没有待确认的变化", style = QztcTypography.courseTitle, color = colors.mut, modifier = Modifier.padding(vertical = 28.dp)) }
            } else {
                items(pending, key = { it.id }) { record ->
                    ChangeSetCard(
                        record = record,
                        onApply = { confirmation = ChangeConfirmation(record.id, ChangeAction.APPLY) },
                        onIgnore = { confirmation = ChangeConfirmation(record.id, ChangeAction.IGNORE) },
                    )
                }
            }
            if (history.isNotEmpty()) {
                item { Text("处理记录", style = QztcTypography.section, color = colors.mut, modifier = Modifier.padding(top = 8.dp)) }
                items(history, key = { "history-${it.id}" }) { record ->
                    HistoryCard(
                        record = record,
                        onRevert = { confirmation = ChangeConfirmation(record.id, ChangeAction.REVERT) },
                        onApplyAgain = { confirmation = ChangeConfirmation(record.id, ChangeAction.APPLY) },
                    )
                }
            }
        }
    }
    confirmation?.let { request ->
        val copy = when (request.action) {
            ChangeAction.APPLY -> "应用后，今日页和周课表会按变化后的安排显示。"
            ChangeAction.IGNORE -> "忽略后会保留当前课表，之后仍可在处理记录中应用。"
            ChangeAction.REVERT -> "恢复后会回到这次变更应用前的课表。"
        }
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = {
                Text(
                    when (request.action) {
                        ChangeAction.APPLY -> "应用这次变化？"
                        ChangeAction.IGNORE -> "忽略这次变化？"
                        ChangeAction.REVERT -> "恢复变更前课表？"
                    },
                )
            },
            text = { Text(copy) },
            confirmButton = {
                Button(onClick = {
                    when (request.action) {
                        ChangeAction.APPLY -> onApply(request.id)
                        ChangeAction.IGNORE -> onIgnore(request.id)
                        ChangeAction.REVERT -> onRevert(request.id)
                    }
                    confirmation = null
                }) { Text("确认") }
            },
            dismissButton = { OutlinedButton(onClick = { confirmation = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun ChangeSetCard(record: ChangeSetRecord, onApply: () -> Unit, onIgnore: () -> Unit) {
    val colors = LocalQztcColors.current
    GlassSurface(GlassRole.CONTENT, Modifier.fillMaxWidth()) {
        Column(Modifier.padding(QztcSpacing.lg), verticalArrangement = Arrangement.spacedBy(QztcSpacing.sm)) {
            Text("教务系统 · ${formatTime(record.createdAt)}", style = QztcTypography.meta, color = colors.mut)
            DiffRows(record)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(QztcSpacing.sm)) {
                OutlinedButton(onClick = onIgnore, modifier = Modifier.weight(1f)) { Text("忽略") }
                Button(onClick = onApply, modifier = Modifier.weight(1f)) { Text("应用") }
            }
        }
    }
}

@Composable
private fun HistoryCard(record: ChangeSetRecord, onRevert: () -> Unit, onApplyAgain: () -> Unit) {
    val colors = LocalQztcColors.current
    GlassSurface(GlassRole.CONTENT, Modifier.fillMaxWidth()) {
        Column(Modifier.padding(QztcSpacing.lg), verticalArrangement = Arrangement.spacedBy(QztcSpacing.sm)) {
            Text("${record.statusLabel()} · ${formatTime(record.resolvedAt ?: record.createdAt)}", style = QztcTypography.courseTitle, color = colors.ink)
            Text("${record.addedCount} 项新增 · ${record.changedCount} 项修改 · ${record.removedCount} 项移除", style = QztcTypography.meta, color = colors.mut)
            when (record.status) {
                ChangeSetStatus.APPLIED -> OutlinedButton(onClick = onRevert, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Outlined.Undo, null)
                    Text("恢复到变更前", modifier = Modifier.padding(start = 8.dp))
                }
                ChangeSetStatus.IGNORED -> Button(onClick = onApplyAgain, modifier = Modifier.fillMaxWidth()) { Text("重新应用") }
                else -> Unit
            }
        }
    }
}

@Composable
private fun DiffRows(record: ChangeSetRecord) {
    val colors = LocalQztcColors.current
    val addedCourses = record.diff.addedCourses.associateBy { it.id }
    record.diff.changedCourses.forEach { CourseMetadataChangeRow(it) }
    record.diff.changedMeetings.forEach { change -> ChangedMeetingRow(change) }
    record.diff.addedMeetings.forEach { meeting ->
        Text("新增 · ${addedCourses[meeting.courseId]?.name ?: meeting.courseId}", style = QztcTypography.courseTitle, color = colors.ink)
        Text(meeting.describe(), style = QztcTypography.body, color = colors.warn)
    }
    record.diff.removedMeetings.forEach { meeting ->
        Text("取消原排课", style = QztcTypography.courseTitle, color = colors.ink)
        Text(meeting.describe(), style = QztcTypography.body, color = colors.mut, textDecoration = TextDecoration.LineThrough)
    }
    if (record.diff.addedExceptions.isNotEmpty() || record.diff.removedExceptions.isNotEmpty()) {
        Text("调停补课明细已变更", style = QztcTypography.body, color = colors.warn)
    }
    if (record.diff.changedCourses.isEmpty() && record.diff.changedMeetings.isEmpty() && record.diff.addedMeetings.isEmpty() && record.diff.removedMeetings.isEmpty() && record.diff.addedExceptions.isEmpty() && record.diff.removedExceptions.isEmpty()) {
        Text("课程条目发生变化，请查看导入后的课表。", style = QztcTypography.body, color = colors.mut)
    }
}

@Composable
private fun CourseMetadataChangeRow(change: CourseChange) {
    val colors = LocalQztcColors.current
    Text("课程详情更新 · ${change.new.name}", style = QztcTypography.courseTitle, color = colors.ink)
    val changes = buildList {
        if (change.old.nature != change.new.nature) add("课程性质：${change.old.nature.orEmpty()}→${change.new.nature.orEmpty()}")
        if (change.old.credits != change.new.credits) add("学分：${change.old.credits.orEmpty()}→${change.new.credits.orEmpty()}")
        if (change.old.selectionAttribute != change.new.selectionAttribute) add("选课属性：${change.old.selectionAttribute.orEmpty()}→${change.new.selectionAttribute.orEmpty()}")
        if (change.old.assessmentMethod != change.new.assessmentMethod) add("考核方式：${change.old.assessmentMethod.orEmpty()}→${change.new.assessmentMethod.orEmpty()}")
    }
    Text(changes.joinToString("\n"), style = QztcTypography.body, color = colors.warn)
}

@Composable
private fun ChangedMeetingRow(change: MeetingChange) {
    val colors = LocalQztcColors.current
    Text("课程安排更新", style = QztcTypography.courseTitle, color = colors.ink)
    Text(change.old.describe(), style = QztcTypography.body, color = colors.mut, textDecoration = TextDecoration.LineThrough)
    val fields = change.fields.joinToString("、") { "${it.field.label}：${it.oldValue.orEmpty()}→${it.newValue.orEmpty()}" }
    Text("${change.new.describe()}${if (fields.isBlank()) "" else "\n$fields"}", style = QztcTypography.body, color = colors.warn)
}

private val cn.qztc.timetable.core.domain.model.MeetingField.label: String
    get() = when (this) {
        cn.qztc.timetable.core.domain.model.MeetingField.TEACHER -> "教师"
        cn.qztc.timetable.core.domain.model.MeetingField.ROOM -> "教室"
        cn.qztc.timetable.core.domain.model.MeetingField.WEEKS -> "周次"
    }

private fun CourseMeeting.describe(): String {
    val day = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日").getOrElse(weekday) { "星期$weekday" }
    return "$day ${displayPeriodRangeLabel(periodStart, periodEnd)} · ${room ?: "教室待定"}${teacher?.let { " · $it" }.orEmpty()}"
}

private fun ChangeSetRecord.statusLabel(): String = when (status) {
    ChangeSetStatus.APPLIED -> "已应用"
    ChangeSetStatus.IGNORED -> "已忽略"
    ChangeSetStatus.REVERTED -> "已恢复"
    ChangeSetStatus.SUPERSEDED -> "已被新变化替代"
    ChangeSetStatus.PENDING -> "待处理"
}

@Composable
private fun Summary(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = QztcTypography.pageTitle, color = color)
        Text(label, style = QztcTypography.meta, color = LocalQztcColors.current.mut)
    }
}

private fun formatTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
