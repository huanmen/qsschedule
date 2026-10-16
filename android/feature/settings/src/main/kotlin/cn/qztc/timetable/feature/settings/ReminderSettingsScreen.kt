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
import cn.qztc.timetable.core.domain.model.DailyAgendaSettings
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
    dailyAgenda: DailyAgendaSettings = DailyAgendaSettings(),
    onDailyAgendaChange: (DailyAgendaSettings) -> Unit = {},
    onPreviewDailyAgenda: () -> Unit = {},
    academicPluginInstalled: Boolean = false,
    onPluginMarket: () -> Unit = {},
    onRemindOnHolidaysChange: (Boolean) -> Unit = {},
) {
    val colors = LocalQztcColors.current
    var expandedCourse by rememberSaveable { mutableStateOf<String?>(null) }
    var showAddCourses by rememberSaveable { mutableStateOf(false) }
    val sortedCourses = remember(courses) { courses.distinctBy { it.id }.sortedBy { it.name } }
    val included = sortedCourses.filter { state.overrides[it.id] != 0 }
    val excluded = sortedCourses.filter { state.overrides[it.id] == 0 }
    Column(Modifier.fillMaxSize().background(colors.solid2)) {
        QztcPageTopBar(title = "提醒设置", onBack = onBack)
        LazyColumn(
            Modifier.weight(1f).imePadding().testTag("reminder-settings-list"),
            contentPadding = PaddingValues(QztcSpacing.lg),
        ) {
            item(key = "daily-agenda") {
                DailyAgendaOptions(dailyAgenda, onDailyAgendaChange, onPreviewDailyAgenda, academicPluginInstalled, onPluginMarket)
            }
            item(key = "course-reminders") {
                ReminderSettingsSection("上课提醒") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (state.enabled) "已开启" else "已关闭", style = QztcTypography.body, color = colors.ink)
                            Text("按当前实际学期的课程安排发送", style = QztcTypography.meta, color = colors.mut)
                        }
                        Switch(state.enabled, onRemindersEnabledChange, Modifier.testTag("reminder-enabled"))
                    }
                    HorizontalDivider(color = colors.line)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("节假日也提醒", Modifier.weight(1f), style = QztcTypography.body, color = colors.ink)
                        Switch(state.remindOnHolidays, onRemindOnHolidaysChange, Modifier.testTag("course-remind-holidays"))
                    }
                    Text("开启后，校历放假日仍按原课程提醒；调休按实际课表提醒。", style = QztcTypography.meta, color = colors.mut)
                    Text("默认提前时间", style = QztcTypography.body, color = colors.ink)
                    ReminderMinuteChoices(state.minutes, onReminderMinutesChange)
                    Text("未单独设置的课程使用这个时间", style = QztcTypography.meta, color = colors.mut)
                    HorizontalDivider(color = colors.line)
                    Text("显示方式", style = QztcTypography.body, color = colors.ink)
                    QztcReminderModeOptions(state.displayMode==ReminderDisplayMode.SCREEN_POPUP,
                        {onReminderDisplayModeChange(if(it)ReminderDisplayMode.SCREEN_POPUP else ReminderDisplayMode.NOTIFICATION_BAR)},
                        ReminderPreviewState(testState.running,testState.message,testState.needsSystemSettings,testState.needsConfirmation),
                        onTestReminder,onSystemReminderSettings)
                    HorizontalDivider(color = colors.line)
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
            if (showAddCourses) excluded.forEach { course ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(course.name, Modifier.weight(1f), style = QztcTypography.body, color = colors.mut)
                    TextButton(onClick = { onSaveCourseReminder(course.id, null, state.messages[course.id]) }) { Text("加入提醒") }
                }
            }
            included.forEachIndexed { index, course ->
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyAgendaOptions(settings: DailyAgendaSettings, onChange: (DailyAgendaSettings) -> Unit, onPreview: () -> Unit, academicPluginInstalled: Boolean, onPluginMarket: () -> Unit) {
    val colors = LocalQztcColors.current
    var choosingTime by remember { mutableStateOf(false) }
    ReminderSettingsSection("每日待办") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("每天汇总当天已加入的安排", Modifier.weight(1f), style = QztcTypography.body)
            Switch(settings.enabled, { onChange(settings.copy(enabled = it)) }, Modifier.testTag("daily-agenda-enabled"))
        }
        TextButton(onClick = { choosingTime = true }, modifier = Modifier.testTag("daily-agenda-time")) {
            Text("通知时间  %02d:%02d".format(settings.hour, settings.minute))
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { index, label ->
                val day = index + 1
                FilterChip(day in settings.weekdays, onClick = {
                    onChange(settings.copy(weekdays = if (day in settings.weekdays) settings.weekdays - day else settings.weekdays + day))
                }, label = { Text(label) }, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(4.dp))
            }
        }
        if (!academicPluginInstalled) TextButton(onClick = onPluginMarket) { Text("赛事与考证安排 · 前往插件市场") }
        if (settings.weekdays.isEmpty()) Text("请选择至少一天", style = QztcTypography.meta, color = colors.warn)
        listOf<Triple<String, Boolean, (Boolean) -> Unit>>(
            Triple("课程表与日历活动", settings.includeActivities, { value: Boolean -> onChange(settings.copy(includeActivities = value)) }),
            Triple("赛事与考证安排", settings.includeCompetitions || settings.includeExams, { value: Boolean -> onChange(settings.copy(includeCompetitions = value, includeExams = value)) }),
            Triple("当天课程", settings.includeCourses, { value: Boolean -> onChange(settings.copy(includeCourses = value)) }),
            Triple("没有安排时也通知", settings.notifyEmpty, { value: Boolean -> onChange(settings.copy(notifyEmpty = value)) }),
        ).filter { it.first != "赛事与考证安排" || academicPluginInstalled }.forEach { (title, checked, change) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = QztcTypography.body)
                Switch(checked, change)
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ReminderDisplayMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(settings.displayMode == mode, { onChange(settings.copy(displayMode = mode)) },
                    shape = SegmentedButtonDefaults.itemShape(index, ReminderDisplayMode.entries.size),
                    label = { Text(if (mode == ReminderDisplayMode.SCREEN_POPUP) "弹窗" else "通知栏") })
            }
        }
        Text("通知最多展开 ${settings.maximumItems} 项", style = QztcTypography.meta)
        Slider(settings.maximumItems.toFloat(), { onChange(settings.copy(maximumItems = it.toInt())) },
            valueRange = 1f..30f, steps = 28)
        Text("只汇总已加入的安排，取消后不再提醒。系统节电可能延迟通知。", style = QztcTypography.meta, color = colors.mut)
        TextButton(onClick = onPreview, modifier = Modifier.testTag("daily-agenda-preview")) { Text("预览今日通知") }
    }
    if (choosingTime) {
        cn.qztc.timetable.core.ui.theme.QztcTimePicker(java.time.LocalTime.of(settings.hour, settings.minute),
            onDismiss = { choosingTime = false }) { time ->
            onChange(settings.copy(hour = time.hour, minute = time.minute)); choosingTime = false
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
