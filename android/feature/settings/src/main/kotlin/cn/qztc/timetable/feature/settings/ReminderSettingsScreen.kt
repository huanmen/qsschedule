package cn.qztc.timetable.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.domain.model.Course
import cn.qztc.timetable.core.domain.model.ReminderDisplayMode
import cn.qztc.timetable.core.ui.theme.*
import cn.qztc.timetable.feature.timetable.CourseReminderUiState
import cn.qztc.timetable.feature.timetable.ReminderMinuteChoices

data class ReminderTestState(
    val running: Boolean = false,
    val message: String = "",
    val needsSystemSettings: Boolean = false,
    val needsConfirmation: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSettingsScreen(
    state: CourseReminderUiState,
    courses: List<Course>,
    semesterLabel: String,
    onBack: () -> Unit,
    onRemindersEnabledChange: (Boolean) -> Unit,
    onReminderMinutesChange: (Int) -> Unit,
    onReminderDisplayModeChange: (ReminderDisplayMode) -> Unit,
    testState: ReminderTestState,
    onTestReminder: () -> Unit,
    onSystemReminderSettings: () -> Unit,
    onSaveCourseReminder: (String, Int?, String?) -> Unit,
) {
    val colors = LocalQztcColors.current
    var expandedCourse by rememberSaveable { mutableStateOf<String?>(null) }
    var showAddCourses by rememberSaveable { mutableStateOf(false) }
    var reportedMissing by remember(testState) { mutableStateOf(false) }
    var confirmedReceived by remember(testState) { mutableStateOf(false) }
    val sortedCourses = remember(courses) { courses.distinctBy { it.id }.sortedBy { it.name } }
    val included = sortedCourses.filter { state.overrides[it.id] != 0 }
    val excluded = sortedCourses.filter { state.overrides[it.id] == 0 }
    Column(Modifier.fillMaxSize().background(colors.solid2)) {
        QztcPageTopBar(title = "提醒设置", onBack = onBack)
        LazyColumn(
            Modifier.weight(1f).imePadding().testTag("reminder-settings-list"),
            contentPadding = PaddingValues(QztcSpacing.lg),
        ) {
            item(key = "enabled") {
                ReminderSettingsSection("上课提醒") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (state.enabled) "已开启" else "已关闭", style = QztcTypography.body, color = colors.ink)
                            Text("按当前实际学期的课程安排发送", style = QztcTypography.meta, color = colors.mut)
                        }
                        Switch(state.enabled, onRemindersEnabledChange, Modifier.testTag("reminder-enabled"))
                    }
                }
            }
            item(key = "timing") {
                ReminderSettingsSection("默认提前时间") {
                    ReminderMinuteChoices(state.minutes, onReminderMinutesChange)
                    Text("未单独设置的课程使用这个时间", style = QztcTypography.meta, color = colors.mut)
                }
            }
            item(key = "display") {
                ReminderSettingsSection("显示方式") {
                    val modes = listOf(ReminderDisplayMode.SCREEN_POPUP, ReminderDisplayMode.NOTIFICATION_BAR)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        modes.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = state.displayMode == mode,
                                onClick = { onReminderDisplayModeChange(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                                label = { Text(if (mode == ReminderDisplayMode.SCREEN_POPUP) "弹窗提醒" else "仅通知栏") },
                            )
                        }
                    }
                    if (testState.running) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(when {
                        reportedMissing -> "未收到测试提醒，请调整系统通知设置后重试。"
                        confirmedReceived -> "已确认收到提醒"
                        testState.message.isNotBlank() -> testState.message
                        else -> "点击一种显示方式，会自动发送测试提醒。"
                    }, style = QztcTypography.meta, color = colors.mut)
                    if (testState.needsConfirmation && !reportedMissing && !confirmedReceived) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { confirmedReceived = true }) { Text("已收到") }
                            TextButton(onClick = { reportedMissing = true }) { Text("没有收到") }
                        }
                    }
                    if (testState.needsSystemSettings || reportedMissing) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onSystemReminderSettings) { Text("系统通知设置") }
                            TextButton(onClick = onTestReminder, enabled = !testState.running) { Text("重新测试") }
                        }
                    }
                }
            }
            item(key = "course-heading") {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("已加入提醒的课程", style = QztcTypography.section, color = colors.ink)
                        Text(semesterLabel, style = QztcTypography.meta, color = colors.mut)
                    }
                    if (excluded.isNotEmpty()) TextButton(onClick = { showAddCourses = !showAddCourses }) {
                        Text(if (showAddCourses) "收起" else "添加课程")
                    }
                }
                if (included.isEmpty()) Text(
                    if (sortedCourses.isEmpty()) "导入课表后，可在这里设置课程提醒。" else "还没有加入提醒的课程",
                    style = QztcTypography.body, color = colors.mut, modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            if (showAddCourses) itemsIndexed(excluded, key = { _, it -> "excluded:${it.id}" }) { _, course ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(course.name, Modifier.weight(1f), style = QztcTypography.body, color = colors.mut)
                    TextButton(onClick = { onSaveCourseReminder(course.id, null, state.messages[course.id]) }) { Text("加入提醒") }
                }
            }
            itemsIndexed(included, key = { _, it -> "course:${it.id}" }) { index, course ->
                ReminderCourseRow(course, state, expandedCourse == course.id,
                    onToggle = { expandedCourse = if (expandedCourse == course.id) null else course.id },
                    onSave = { minutes, message ->
                        onSaveCourseReminder(course.id, minutes, message)
                        expandedCourse = null
                    })
                if (index < included.lastIndex) HorizontalDivider(color = colors.line)
            }
        }
    }
}

@Composable
private fun ReminderSettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalQztcColors.current
    Column(Modifier.fillMaxWidth().padding(bottom = QztcSpacing.md)
        .background(colors.solid, RoundedCornerShape(QztcCorners.content)).padding(QztcSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(QztcSpacing.sm)) {
        Text(title, style = QztcTypography.section, color = colors.ink)
        content()
    }
}

@Composable
private fun ReminderCourseRow(course: Course, state: CourseReminderUiState, expanded: Boolean, onToggle: () -> Unit, onSave: (Int?, String?) -> Unit) {
    val colors = LocalQztcColors.current
    val overrideMinutes = state.overrides[course.id]
    val savedMessage = state.messages[course.id].orEmpty()
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(course.name, style = QztcTypography.courseTitle, color = colors.ink)
                Text(if (overrideMinutes == null) "跟随默认 · ${state.minutes} 分钟" else "提前 $overrideMinutes 分钟",
                    style = QztcTypography.meta, color = colors.mut)
                if (!expanded && savedMessage.isNotBlank()) Text(savedMessage, maxLines = 2, style = QztcTypography.meta, color = colors.mut)
            }
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (expanded) "收起课程设置" else "展开课程设置", tint = colors.mut)
        }
        if (expanded) {
            var minutes by rememberSaveable(course.id, overrideMinutes) { mutableStateOf(overrideMinutes ?: -1) }
            var message by rememberSaveable(course.id, savedMessage) { mutableStateOf(savedMessage) }
            Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReminderMinuteChoices(minutes, { minutes = it }, allowDefault = true)
                OutlinedTextField(value = message, onValueChange = { message = it.take(240) },
                    label = { Text("额外提醒内容") }, placeholder = { Text("例如：带实验报告") },
                    supportingText = { Text("${message.length}/240 · 留空仅显示课程信息") },
                    minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
                Button(onClick = { onSave(minutes.takeIf { it >= 0 }, message.trim().takeIf { it.isNotEmpty() }) }, modifier = Modifier.align(Alignment.End)) { Text("保存") }
            }
        }
    }
}
