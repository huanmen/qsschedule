package cn.qztc.timetable.feature.timetable

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import cn.qztc.timetable.core.domain.model.ActivityKind
import cn.qztc.timetable.core.domain.model.ActivitySession
import cn.qztc.timetable.core.domain.model.ScheduleLocation
import cn.qztc.timetable.core.domain.schedule.ActivityWeekLayout
import cn.qztc.timetable.core.ui.theme.QztcPageTopBar
import cn.qztc.timetable.core.ui.theme.QztcTopIconButton
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcDatePicker
import java.util.UUID

@Composable
fun ActivityEditPage(
    title: String, editorKey: String, initial: ActivityEditDraft, presetColors: List<Int>, totalWeeks: Int,
    showDelete: Boolean, onBack: () -> Unit, onSave: (ActivityEditDraft) -> Unit, onDelete: (Boolean) -> Unit = {},
    showHide: Boolean = false, onHide: () -> Unit = {},
) {
    var name by remember(editorKey) { mutableStateOf(initial.name) }
    var note by remember(editorKey) { mutableStateOf(initial.note) }
    var sessions by remember(editorKey) { mutableStateOf(initial.sessions.ifEmpty { initial.toScheduleActivity("").effectiveSessions() }) }
    var editing by remember { mutableStateOf<ActivitySession?>(null) }
    var draft by remember(editing?.id) { mutableStateOf(editing) }
    var deleteOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val colors = LocalQztcColors.current
    BackHandler { if (editing != null) editing = null else onBack() }

    fun save() {
        if (editing == null && name.isNotBlank() && sessions.isNotEmpty())
            onSave(initial.copy(name = name.trim(), note = note.trim(), sessions = sessions, kind = sessions.first().kind))
    }
    LazyColumn(
        Modifier.fillMaxSize().background(if (colors.isDark) colors.solid2 else Color.White),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { QztcPageTopBar(title, onBack = onBack, actions = {
            if (showHide) QztcTopIconButton(Icons.Outlined.VisibilityOff, "从课表隐藏", onClick = { deleteOpen = true })
            QztcTopIconButton(Icons.Outlined.Check, "保存活动", onClick = ::save, enabled = editing == null && name.isNotBlank() && sessions.isNotEmpty())
        }) }
        item { Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EditCourseField("活动名称", name, { name = it })
            EditCourseField("活动备注", note, { note = it })
        } }
        itemsIndexed(sessions, key = { _, row -> row.id }) { index, row ->
            Row(Modifier.fillMaxWidth().clickable { editing = row }.padding(start = 16.dp)) {
                Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                    Text("${index+1}  ${activitySessionLabel(row)}")
                    Text(row.location.name.ifBlank { "未填写地点" }, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { sessions = sessions.filterNot { it.id == row.id } }) { Icon(Icons.Outlined.Delete, "删除第${index+1}场") }
            }
            if (editing?.id == row.id && draft != null) {
                ActivitySessionInlineEditor(requireNotNull(draft), context, totalWeeks, onChange = { draft = it },
                    onCancel = { editing = null }, onSave = {
                        sessions = sessions.map { current -> if (current.id == row.id) requireNotNull(draft) else current }
                        editing = null
                    })
            }
        }
        item { OutlinedButton(onClick = {
            editing = (sessions.lastOrNull() ?: ActivitySession("new", ActivityKind.PERIOD, initial.weekday, initial.periodStart, initial.periodEnd, initial.weeks.ifEmpty { listOf(1) }))
                .copy(id = UUID.randomUUID().toString())
        }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Icon(Icons.Outlined.Add, null); Text("添加场次") } }
        if (editing != null && sessions.none { it.id == editing?.id } && draft != null) item {
            ActivitySessionInlineEditor(requireNotNull(draft), context, totalWeeks, onChange = { draft = it },
                onCancel = { editing = null }, onSave = {
                    sessions = sessions + requireNotNull(draft)
                    editing = null
                })
        }
        if (showDelete && !showHide) item { TextButton(onClick = { deleteOpen = true }, modifier = Modifier.padding(horizontal = 16.dp)) { Icon(Icons.Outlined.Delete, null); Text("删除活动") } }
    }
    if(deleteOpen) AlertDialog(onDismissRequest = { deleteOpen = false }, title = { Text(if (showHide) "从课表隐藏？" else "删除活动？") },
        text = { Text(if (showHide) "该校历安排不会删除，只会从活动安排和课表中隐藏；仍可在校历中重新加入。" else "删除后会恢复被活动覆盖的课程。") },
        confirmButton = { TextButton(onClick = { deleteOpen = false; if (showHide) onHide() else onDelete(true) }) { Text(if (showHide) "隐藏" else "删除") } },
        dismissButton = { TextButton(onClick = { deleteOpen = false }) { Text("取消") } })
}

private fun activitySessionLabel(session: ActivitySession): String {
    if (session.kind != ActivityKind.DAY || session.date == null) return ActivityWeekLayout.sessionTime(session)
    val date = session.date!!
    val weekday = listOf("一", "二", "三", "四", "五", "六", "日")[date.dayOfWeek.value - 1]
    val time = if (session.startMinute == null || session.endMinute == null) "全天"
        else "%02d:%02d-%02d:%02d".format(
            session.startMinute!! / 60, session.startMinute!! % 60,
            session.endMinute!! / 60, session.endMinute!! % 60)
    return "${date.monthValue}月${date.dayOfMonth}日 周$weekday · $time"
}

@Composable
private fun ActivitySessionInlineEditor(
    session: ActivitySession,
    context: android.content.Context,
    totalWeeks: Int,
    onChange: (ActivitySession) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val colors = LocalQztcColors.current
    var choosingRangeEnd by remember(session.id) { mutableStateOf(false) }
    var choosingDate by remember(session.id) { mutableStateOf(false) }
    if (choosingDate) QztcDatePicker(session.date, onDismiss = { choosingDate = false }) {
        onChange(session.copy(date = it))
        choosingDate = false
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = session.kind == ActivityKind.PERIOD, onClick = {
                onChange(ActivitySession(session.id, ActivityKind.PERIOD, weekday = 1, periodStart = 1,
                    periodEnd = 2, weeks = listOf(1), location = session.location))
            }, label = { Text("按课程时间") })
            FilterChip(selected = session.kind == ActivityKind.DAY, onClick = {
                onChange(ActivitySession(session.id, ActivityKind.DAY,
                    date = session.date ?: java.time.LocalDate.now(), location = session.location))
            }, label = { Text("按日期") })
        }
        if (session.kind == ActivityKind.PERIOD) {
            val startIndex = displaySlotIndexForActual(session.periodStart ?: 1)
            val endIndex = displaySlotIndexForActual(session.periodEnd ?: session.periodStart ?: 1)
            Text(ActivityWeekLayout.sessionTime(session), color = colors.accentDeep)
            WeekPeriodPicker(session.weekday ?: 1, startIndex, endIndex,
                onDay = { onChange(session.copy(weekday = it)); choosingRangeEnd = false },
                onSlot = { index ->
                    if (!choosingRangeEnd || index < startIndex) {
                        val slot = timetableDisplayPeriodSlots[index]
                        onChange(session.copy(periodStart = slot.actualStart, periodEnd = slot.actualEnd))
                        choosingRangeEnd = true
                    } else {
                        onChange(session.copy(periodEnd = timetableDisplayPeriodSlots[index].actualEnd))
                        choosingRangeEnd = false
                    }
                })
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("活动周数", Modifier.weight(1f), color = colors.ink)
                WeekSelectionShortcuts(totalWeeks, session.weeks.toSet()) {
                    if (it.isNotEmpty()) onChange(session.copy(weeks = it.sorted()))
                }
            }
            (1..totalWeeks.coerceAtLeast(1)).toList().chunked(6).forEach { rowWeeks ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowWeeks.forEach { week ->
                        EditChoice(week.toString(), week in session.weeks, Modifier.weight(1f)) {
                            val weeks = session.weeks.toSet()
                            val updated = if (week in weeks) weeks - week else weeks + week
                            if (updated.isNotEmpty()) onChange(session.copy(weeks = updated.sorted()))
                        }
                    }
                }
            }
        } else {
            OutlinedButton(onClick = { choosingDate = true }, modifier = Modifier.fillMaxWidth()) {
                Text(session.date?.toString().orEmpty())
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = session.startMinute == null, onCheckedChange = { allDay ->
                    onChange(session.copy(startMinute = if (allDay) null else 540,
                        endMinute = if (allDay) null else 600))
                })
                Text("全天")
            }
            val startMinute = session.startMinute
            val endMinute = session.endMinute
            if (startMinute != null && endMinute != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MinuteInput("开始时间", startMinute, Modifier.weight(1f)) { value ->
                        if (value < 1439) onChange(session.copy(startMinute = value, endMinute = maxOf(endMinute, value + 1)))
                    }
                    MinuteInput("结束时间", endMinute, Modifier.weight(1f)) { value ->
                        if (value > 0) onChange(session.copy(startMinute = minOf(startMinute, value - 1), endMinute = value))
                    }
                }
            }
        }
        ScheduleLocationField(session.location) { onChange(session.copy(location = it)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("取消") }
            TextButton(onClick = onSave, enabled = session.kind != ActivityKind.PERIOD || session.weeks.isNotEmpty()) {
                Text("保存场次", color = colors.accentDeep)
            }
        }
    }
}

@Composable
private fun MinuteInput(label: String, minute: Int, modifier: Modifier = Modifier, onMinuteChange: (Int) -> Unit) {
    var text by remember(minute) { mutableStateOf("%02d:%02d".format(minute / 60, minute % 60)) }
    OutlinedTextField(
        value = text,
        onValueChange = { next ->
            text = next.take(5)
            val digits = next.filter(Char::isDigit)
            if (digits.length == 4) {
                val hour = digits.substring(0, 2).toIntOrNull() ?: -1
                val mins = digits.substring(2).toIntOrNull() ?: -1
                if (hour in 0..23 && mins in 0..59) onMinuteChange(hour * 60 + mins)
            }
        },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
    )
}
