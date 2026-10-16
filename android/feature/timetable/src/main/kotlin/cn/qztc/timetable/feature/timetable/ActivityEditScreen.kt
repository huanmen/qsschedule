package cn.qztc.timetable.feature.timetable

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.domain.model.ActivityKind
import cn.qztc.timetable.core.domain.model.QnuPeriodDefaults
import cn.qztc.timetable.core.domain.model.ScheduleActivity
import cn.qztc.timetable.core.domain.schedule.ActivityConflict
import cn.qztc.timetable.core.domain.schedule.ActivityConflictHit
import cn.qztc.timetable.core.domain.schedule.ActivityWeekLayout
import cn.qztc.timetable.core.ui.glass.GlassRole
import cn.qztc.timetable.core.ui.glass.GlassSurface
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcPageTopBar
import cn.qztc.timetable.core.ui.theme.QztcSpacing
import cn.qztc.timetable.core.ui.theme.QztcTopIconButton
import cn.qztc.timetable.core.ui.theme.QztcTypography
import cn.qztc.timetable.core.ui.theme.QztcDatePicker
import java.time.LocalDate

fun ScheduleActivity.toEditDraft(
    fallbackWeekday: Int,
    fallbackDate: LocalDate,
    totalWeeks: Int,
): ActivityEditDraft = ActivityEditDraft(
    id = id,
    name = name,
    note = note,
    timeText = "",
    kind = kind,
    weekday = weekday ?: fallbackWeekday,
    periodStart = periodStart ?: 1,
    periodEnd = periodEnd ?: (periodStart ?: 2),
    weeks = weeks.ifEmpty { listOf(1).filter { it in 1..totalWeeks.coerceAtLeast(1) }.ifEmpty { listOf(1) } },
    startDate = startDate ?: fallbackDate,
    endDate = endDate ?: startDate ?: fallbackDate,
    color = color ?: 0,
    sessions = if (id.isBlank()) emptyList() else effectiveSessions(),
    memoTime = memoTime,
)

fun ActivityEditDraft.toScheduleActivity(semesterId: String): ScheduleActivity = ScheduleActivity(
    id = id?.takeIf { it.isNotBlank() }.orEmpty(),
    semesterId = semesterId,
    name = name.trim(),
    note = note.trim(),
    timeText = "",
    kind = kind,
    color = color,
    sessions = sessions,
    memoTime = memoTime,
    weekday = weekday.takeIf { kind == ActivityKind.PERIOD },
    periodStart = periodStart.takeIf { kind == ActivityKind.PERIOD },
    periodEnd = periodEnd.takeIf { kind == ActivityKind.PERIOD },
    weeks = if (kind == ActivityKind.PERIOD) weeks else emptyList(),
    startDate = startDate.takeIf { kind == ActivityKind.DAY },
    endDate = endDate.takeIf { kind == ActivityKind.DAY },
)

internal fun findActivityConflicts(
    draft: ActivityEditDraft,
    scheduleState: DemoScheduleState,
): List<ActivityConflictHit> {
    val schedule = scheduleState.importedSchedule ?: return emptyList()
    return ActivityConflict.overlappingMeetings(
        activity = draft.toScheduleActivity(schedule.semester.id),
        state = schedule.state,
        dateOf = { week, weekday -> scheduleState.dateFor(week, weekday) },
    )
}

internal data class PendingActivitySave(
    val draft: ActivityEditDraft,
    val hits: List<ActivityConflictHit>,
    val afterSave: () -> Unit,
)

@Composable
internal fun ActivityDetailPage(
    activity: ScheduleActivity,
    presetColors: List<Int>,
    totalWeeks: Int,
    fallbackWeekday: Int,
    fallbackDate: LocalDate,
    onBack: () -> Unit,
    onSave: (ActivityEditDraft) -> Unit,
    onDelete: (Boolean) -> Unit,
) {
    val colors = LocalQztcColors.current
    val palette = visualCourseColor(activity.color ?: 0)
    var editing by remember(activity.id) { mutableStateOf(false) }
    if (editing) {
        ActivityEditPage(
            title = "编辑活动",
            editorKey = activity.id,
            initial = activity.toEditDraft(fallbackWeekday, fallbackDate, totalWeeks),
            presetColors = presetColors,
            totalWeeks = totalWeeks,
            showDelete = !activity.id.startsWith("calendar-event:"),
            showHide = activity.id.startsWith("calendar-event:"),
            onBack = { editing = false },
            onSave = onSave,
            onDelete = onDelete,
            onHide = { onDelete(true) },
        )
        return
    }

    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(if (colors.isDark) colors.solid2 else Color.White)) {
        QztcPageTopBar(
            title = "活动详情",
            onBack = onBack,
            actions = {
                QztcTopIconButton(
                    icon = Icons.Outlined.Edit,
                    contentDescription = "编辑活动",
                    onClick = { editing = true },
                )
            },
        )
        Column(Modifier.padding(QztcSpacing.xl), verticalArrangement = Arrangement.spacedBy(QztcSpacing.lg)) {
            Box(Modifier.width(56.dp).height(6.dp).clip(CircleShape).background(palette.bar))
            ProtectedNameText(activity.name, QztcTypography.pageTitle)
            GlassSurface(role = GlassRole.CONTENT, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(QztcSpacing.xl), verticalArrangement = Arrangement.spacedBy(QztcSpacing.md)) {
                    ActivityDetailLine("类型", if (activity.kind == ActivityKind.PERIOD) "按课程时间" else "按日期")
                    ActivityDetailLine("时间", ActivityWeekLayout.displayTime(activity).ifBlank { "—" })
                    if (activity.kind == ActivityKind.PERIOD) {
                        val weekday = activity.weekday
                        if (weekday != null) {
                            ActivityDetailLine("星期", "周${listOf("一", "二", "三", "四", "五", "六", "日")[weekday - 1]}")
                        }
                        val start = activity.periodStart
                        if (start != null) {
                            val end = activity.periodEnd ?: start
                            ActivityDetailLine("节次", displayPeriodRangeLabel(start, end))
                        }
                        if (activity.weeks.isNotEmpty()) {
                            ActivityDetailLine("周次", activity.weeks.joinToString("、") { "第${it}周" })
                        }
                    }
                    if (activity.note.isNotBlank()) {
                        ActivityDetailLine("备注", activity.note)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ActivityConflictDialog(
    hits: List<ActivityConflictHit>,
    onRemoveCourses: () -> Unit,
    onKeepCourses: () -> Unit,
    onDismiss: () -> Unit,
) {
    val names = hits.map { it.courseName }.distinct()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("与现有课程冲突") },
        text = {
            Text("活动时间和以下课程重叠。覆盖仅用于活动视图，课程视图仍显示原课。\n\n${names.joinToString("\n")}")
        },
        confirmButton = {
            Button(onClick = onRemoveCourses) { Text("活动视图覆盖") }
        },
        dismissButton = {
            TextButton(onClick = onKeepCourses) { Text("保留课程") }
        },
    )
}

@Composable
private fun ActivityDetailLine(label: String, value: String) {
    val colors = LocalQztcColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = QztcTypography.meta, color = colors.mut)
        Text(value, style = QztcTypography.body, color = colors.ink, modifier = Modifier.padding(start = QztcSpacing.md))
    }
}

@Composable
internal fun ActivitySessionTimingPage(
    title: String,
    editorKey: String,
    initial: ActivityEditDraft,
    presetColors: List<Int>,
    totalWeeks: Int,
    showDelete: Boolean,
    onBack: () -> Unit,
    onSave: (ActivityEditDraft) -> Unit,
    onDelete: () -> Unit = {},
) {
    val colors = LocalQztcColors.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    var name by remember(editorKey) { mutableStateOf(initial.name) }
    var note by remember(editorKey) { mutableStateOf(initial.note) }
    var kind by remember(editorKey) { mutableStateOf(initial.kind) }
    var weekday by remember(editorKey) { mutableStateOf(initial.weekday) }
    var startSlotIndex by remember(editorKey) { mutableStateOf(displaySlotIndexForActual(initial.periodStart)) }
    var endSlotIndex by remember(editorKey) { mutableStateOf(displaySlotIndexForActual(initial.periodEnd)) }
    var choosingRangeEnd by remember(editorKey) { mutableStateOf(false) }
    var selectedWeeks by remember(editorKey, totalWeeks) {
        mutableStateOf(initial.weeks.ifEmpty { setOf(1) }.toSet())
    }
    var startDate by remember(editorKey) { mutableStateOf(initial.startDate) }
    var endDate by remember(editorKey) { mutableStateOf(initial.endDate) }
    var colorValue by remember(editorKey) { mutableStateOf(initial.color) }
    var deleteDialogOpen by remember { mutableStateOf(false) }
    val canSave = name.isNotBlank() && when (kind) {
        ActivityKind.PERIOD -> selectedWeeks.isNotEmpty()
        ActivityKind.DAY -> !endDate.isBefore(startDate)
    }

    fun save() {
        if (!canSave) return
        val startSlot = timetableDisplayPeriodSlots[startSlotIndex]
        val endSlot = timetableDisplayPeriodSlots[endSlotIndex]
        onSave(
            ActivityEditDraft(
                id = initial.id,
                name = name.trim(),
                note = note.trim(),
                timeText = "",
                kind = kind,
                weekday = weekday,
                periodStart = startSlot.actualStart,
                periodEnd = endSlot.actualEnd,
                weeks = selectedWeeks.sorted(),
                startDate = startDate,
                endDate = if (endDate.isBefore(startDate)) startDate else endDate,
                color = colorValue,
            ),
        )
    }

    var dateRequest by remember { mutableStateOf<Pair<LocalDate, (LocalDate) -> Unit>?>(null) }
    fun pickDate(current: LocalDate, onPicked: (LocalDate) -> Unit) { dateRequest = current to onPicked }
    dateRequest?.let { (current, update) ->
        QztcDatePicker(current, onDismiss = { dateRequest = null }) { update(it); dateRequest = null }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(if (colors.isDark) colors.solid2 else Color.White),
        contentPadding = PaddingValues(bottom = QztcSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(QztcSpacing.md),
    ) {
        item {
            QztcPageTopBar(
                title = title,
                onBack = onBack,
                actions = {
                    QztcTopIconButton(
                        icon = Icons.Outlined.Check,
                        contentDescription = "保存",
                        onClick = ::save,
                        enabled = canSave,
                    )
                },
            )
        }
        item {
            Column(
                Modifier.padding(horizontal = QztcSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(QztcSpacing.md),
            ) {
                if (showDelete) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditChoice("按课程时间", kind == ActivityKind.PERIOD, Modifier.weight(1f)) {
                        kind = ActivityKind.PERIOD
                    }
                    EditChoice("按日期", kind == ActivityKind.DAY, Modifier.weight(1f)) {
                        kind = ActivityKind.DAY
                    }
                }
                if (kind == ActivityKind.PERIOD) {
                    FormLabel("上课时间")
                    Text(
                        "周${listOf("一", "二", "三", "四", "五", "六", "日")[weekday - 1]} · " +
                            displayPeriodRangeLabel(timetableDisplayPeriodSlots[startSlotIndex].actualStart,
                                timetableDisplayPeriodSlots[endSlotIndex].actualEnd),
                        style = QztcTypography.body, color = colors.accentDeep,
                    )
                    WeekPeriodPicker(weekday, startSlotIndex, endSlotIndex,
                        onDay = { weekday = it; choosingRangeEnd = false },
                        onSlot = { slotIndex ->
                            if (!choosingRangeEnd || slotIndex < startSlotIndex) {
                                startSlotIndex = slotIndex
                                endSlotIndex = slotIndex
                                choosingRangeEnd = true
                            } else {
                                endSlotIndex = slotIndex
                                choosingRangeEnd = false
                            }
                        })
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        FormLabel("活动周数", modifier = Modifier.weight(1f))
                        WeekSelectionShortcuts(totalWeeks = totalWeeks, selectedWeeks = selectedWeeks) { selectedWeeks = it }
                    }
                    (1..totalWeeks.coerceAtLeast(1)).toList().chunked(6).forEach { rowWeeks ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rowWeeks.forEach { weekNo ->
                                EditChoice(
                                    text = weekNo.toString(),
                                    selected = weekNo in selectedWeeks,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    selectedWeeks = if (weekNo in selectedWeeks) selectedWeeks - weekNo else selectedWeeks + weekNo
                                }
                            }
                            repeat(6 - rowWeeks.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                } else {
                    FormLabel("开始日期")
                    OutlinedButton(
                        onClick = { pickDate(startDate) { picked -> startDate = picked; if (endDate.isBefore(picked)) endDate = picked } },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = colors.solid),
                    ) {
                        Text("${startDate.monthValue}月${startDate.dayOfMonth}日")
                    }
                    FormLabel("结束日期")
                    OutlinedButton(
                        onClick = { pickDate(endDate) { picked -> endDate = if (picked.isBefore(startDate)) startDate else picked } },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = colors.solid),
                    ) {
                        Text("${endDate.monthValue}月${endDate.dayOfMonth}日")
                    }
                }
                FormLabel("颜色")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(coursePresetColor(colorValue)))
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        Text("当前颜色", style = QztcTypography.compact, color = colors.ink)
                        Text("从下方调色盘选择", style = QztcTypography.meta, color = colors.mut)
                    }
                }
                if (presetColors.isEmpty()) {
                    Text("暂无预选颜色，请先到“我的”管理调色盘。", style = QztcTypography.meta, color = colors.mut)
                }
                presetColors.chunked(6).forEach { rowColors ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowColors.forEach { presetColor ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(CircleShape)
                                    .background(coursePresetColor(presetColor))
                                    .border(
                                        width = if (colorValue == presetColor) 2.dp else 0.dp,
                                        color = if (colorValue == presetColor) colors.accent else Color.Transparent,
                                        shape = CircleShape,
                                    )
                                    .clickable { colorValue = presetColor },
                            )
                        }
                        repeat(6 - rowColors.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                if (showDelete) {
                    OutlinedButton(
                        onClick = { deleteDialogOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.danger),
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("删除活动")
                    }
                }
            }
        }
    }

    if (deleteDialogOpen) {
        AlertDialog(
            onDismissRequest = { deleteDialogOpen = false },
            title = { Text("删除这个活动？") },
            text = { Text("删除后会从当前学期课表中移除。") },
            confirmButton = {
                Button(
                    onClick = {
                        deleteDialogOpen = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.danger),
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteDialogOpen = false }) { Text("取消") } },
        )
    }
}
