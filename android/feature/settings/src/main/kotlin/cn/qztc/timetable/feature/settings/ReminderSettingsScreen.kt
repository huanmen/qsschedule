package cn.qztc.timetable.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.domain.model.*
import cn.qztc.timetable.core.ui.theme.*
import cn.qztc.timetable.feature.timetable.CourseReminderUiState
import cn.qztc.timetable.feature.timetable.ReminderMinuteChoices
import kotlinx.coroutines.launch

data class ReminderTestState(val running: Boolean = false, val message: String = "",
    val needsSystemSettings: Boolean = false, val needsConfirmation: Boolean = false)
data class ReminderPluginSource(val id: String, val name: String, val kinds: Map<String, String>)

@Composable
fun ReminderSettingsScreen(
    state: CourseReminderUiState, courses: List<Course>, semesterLabel: String, onBack: () -> Unit,
    onRemindersEnabledChange: (Boolean) -> Unit, onReminderMinutesChange: (Int) -> Unit,
    onReminderDisplayModeChange: (ReminderDisplayMode) -> Unit, testState: ReminderTestState,
    onTestReminder: () -> Unit, onSystemReminderSettings: () -> Unit, onSaveCourseReminder: (String, Int?, String?) -> Unit,
    dailyAgenda: DailyAgendaSettings = DailyAgendaSettings(), onDailyAgendaChange: (DailyAgendaSettings) -> Unit = {},
    onPreviewDailyAgenda: () -> Unit = {}, academicPluginInstalled: Boolean = false, onPluginMarket: () -> Unit = {},
    onRemindOnHolidaysChange: (Boolean) -> Unit = {}, homeworkOptions: @Composable () -> Unit = {},
    pluginSources: List<ReminderPluginSource> = if (academicPluginInstalled) listOf(ReminderPluginSource(
        "academic-records", "赛事与考证", mapOf("competition" to "赛事安排", "exam" to "考证安排", "updates" to "关注赛事资料更新"))) else emptyList(),
    onPreviewPluginReminder: suspend (String, PluginReminderSettings) -> Boolean = { _, _ -> false },
) {
    val colors = LocalQztcColors.current
    var customCourses by rememberSaveable { mutableStateOf(false) }
    if (customCourses) {
        CustomCourseReminderScreen(state, courses, semesterLabel, { customCourses = false }, onSaveCourseReminder)
        return
    }
    Column(Modifier.fillMaxSize().background(colors.solid2)) {
        QztcPageTopBar("提醒设置", onBack = onBack)
        LazyColumn(Modifier.weight(1f).imePadding().testTag("reminder-settings-list"), contentPadding = PaddingValues(QztcSpacing.lg)) {
            item("homework-reminders") { homeworkOptions() }
            item("course-reminders") {
                ReminderSettingsSection("上课提醒") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (state.enabled) "已开启" else "已关闭", Modifier.weight(1f), color = colors.ink)
                        Switch(state.enabled, onRemindersEnabledChange, Modifier.testTag("reminder-enabled"))
                    }
                    Column(Modifier.alpha(if (state.enabled) 1f else .45f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HorizontalDivider(color = colors.line)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("节假日也提醒", Modifier.weight(1f))
                            Switch(state.remindOnHolidays, onRemindOnHolidaysChange, Modifier.testTag("course-remind-holidays"), enabled = state.enabled)
                        }
                        Text("默认提前时间", style = QztcTypography.body)
                        ReminderMinuteChoices(state.minutes, onReminderMinutesChange, enabled = state.enabled)
                        Text("显示方式", style = QztcTypography.body)
                        QztcReminderModeOptions(state.displayMode == ReminderDisplayMode.SCREEN_POPUP,
                            { onReminderDisplayModeChange(if (it) ReminderDisplayMode.SCREEN_POPUP else ReminderDisplayMode.NOTIFICATION_BAR) },
                            ReminderPreviewState(testState.running, testState.message, testState.needsSystemSettings, testState.needsConfirmation),
                            onTestReminder, onSystemReminderSettings, enabled = state.enabled)
                        HorizontalDivider(color = colors.line)
                        TextButton(enabled = state.enabled, onClick = { customCourses = true }, modifier = Modifier.fillMaxWidth().testTag("custom-course-reminders")) {
                            Text("自定义课程提醒设置", Modifier.weight(1f)); Text("›")
                        }
                    }
                }
            }
            if (pluginSources.isNotEmpty()) item("other-reminders") {
                OtherReminderOptions(dailyAgenda, onDailyAgendaChange, pluginSources, onPreviewPluginReminder, onSystemReminderSettings)
            }
        }
    }
}

@Composable
private fun CustomCourseReminderScreen(state: CourseReminderUiState, courses: List<Course>, semesterLabel: String,
    onBack: () -> Unit, onSave: (String, Int?, String?) -> Unit) {
    val colors = LocalQztcColors.current
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var showExcluded by rememberSaveable { mutableStateOf(false) }
    val sorted = remember(courses) { courses.distinctBy { it.id }.sortedBy { it.name } }
    val included = sorted.filter { state.overrides[it.id] != 0 }
    val excluded = sorted.filter { state.overrides[it.id] == 0 }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(colors.solid2)) {
        QztcPageTopBar("自定义课程提醒设置", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(20.dp), modifier = Modifier.testTag("custom-course-reminder-list")) {
            item { Text(semesterLabel, style = QztcTypography.meta, color = colors.mut) }
            if (excluded.isNotEmpty()) item { TextButton(enabled = state.enabled, onClick = { showExcluded = !showExcluded }) { Text(if (showExcluded) "收起" else "添加课程") } }
            if (showExcluded) excluded.forEach { course -> item(course.id) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(course.name, Modifier.weight(1f))
                    TextButton(enabled = state.enabled, onClick = { onSave(course.id, null, state.messages[course.id]) }) { Text("加入提醒") }
                }
            } }
            if (included.isEmpty()) item { Text("暂无课程", Modifier.padding(vertical = 20.dp), color = colors.mut) }
            included.forEach { course -> item(course.id) {
                ReminderCourseRow(course, state, expanded == course.id,
                    { expanded = if (expanded == course.id) null else course.id },
                    { minutes, message -> onSave(course.id, minutes, message); expanded = null })
                HorizontalDivider(color = colors.line)
            } }
        }
    }
}

@Composable
private fun OtherReminderOptions(settings: DailyAgendaSettings, onChange: (DailyAgendaSettings) -> Unit,
    sources: List<ReminderPluginSource>, preview: suspend (String, PluginReminderSettings) -> Boolean, onSystemSettings: () -> Unit) {
    val scope = rememberCoroutineScope()
    ReminderSettingsSection("其他事项") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("提醒", Modifier.weight(1f)); Switch(settings.enabled, { onChange(settings.copy(enabled = it)) }, Modifier.testTag("daily-agenda-enabled"))
        }
        sources.forEach { source ->
            val options = settings.forPlugin(source.id)
            fun save(value: PluginReminderSettings) { onChange(settings.copy(plugins = settings.plugins + (source.id to value.normalized()))) }
            val enabled = settings.enabled && options.enabled
            var choosingTime by remember { mutableStateOf(false) }
            var test by remember { mutableStateOf(ReminderPreviewState()) }
            fun send(value: PluginReminderSettings) { scope.launch {
                val sent = preview(source.id, value)
                test = ReminderPreviewState(message = if (sent) "预览提醒已发送" else "请先允许通知权限", needsConfirmation = sent, needsSystemSettings = !sent)
            } }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(source.name, Modifier.weight(1f), style = QztcTypography.body)
                Switch(options.enabled, { save(options.copy(enabled = it)) }, enabled = settings.enabled)
            }
            Column(Modifier.alpha(if (enabled) 1f else .45f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                source.kinds.forEach { (id, label) -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f))
                    Switch(id in options.kinds, { value -> save(options.copy(kinds = if (value) options.kinds + id else options.kinds - id)) }, enabled = enabled)
                } }
                TextButton(enabled = enabled, onClick = { choosingTime = true }) { Text("提醒时间  %02d:%02d".format(options.hour, options.minute)) }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { index, label ->
                        val day = index + 1
                        FilterChip(day in options.weekdays, { save(options.copy(weekdays = if (day in options.weekdays) options.weekdays - day else options.weekdays + day)) },
                            enabled = enabled, label = { Text(label) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(4.dp))
                    }
                }
                QztcReminderModeOptions(options.displayMode == ReminderDisplayMode.SCREEN_POPUP, {
                    val value = options.copy(displayMode = if (it) ReminderDisplayMode.SCREEN_POPUP else ReminderDisplayMode.NOTIFICATION_BAR)
                    save(value); send(value)
                }, test, { send(options) }, onSystemSettings, enabled = enabled)
            }
            if (choosingTime && enabled) QztcTimePicker(java.time.LocalTime.of(options.hour, options.minute), { choosingTime = false }) {
                save(options.copy(hour = it.hour, minute = it.minute)); choosingTime = false
            }
        }
    }
}

@Composable
private fun ReminderSettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalQztcColors.current
    Column(Modifier.fillMaxWidth().padding(bottom = QztcSpacing.md).background(colors.solid, RoundedCornerShape(QztcCorners.content))
        .padding(QztcSpacing.lg), verticalArrangement = Arrangement.spacedBy(QztcSpacing.sm)) {
        Text(title, style = QztcTypography.section, color = colors.ink); content()
    }
}

@Composable
private fun ReminderCourseRow(course: Course, state: CourseReminderUiState, expanded: Boolean, onToggle: () -> Unit, onSave: (Int?, String?) -> Unit) {
    val colors = LocalQztcColors.current
    val overrideMinutes = state.overrides[course.id]
    val savedMessage = state.messages[course.id].orEmpty()
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable(enabled = state.enabled, onClick = onToggle).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(course.name, style = QztcTypography.courseTitle, color = colors.ink)
                Text(if (overrideMinutes == null) "跟随默认 · ${state.minutes} 分钟" else "提前 $overrideMinutes 分钟", style = QztcTypography.meta, color = colors.mut)
                if (!expanded && savedMessage.isNotBlank()) Text(savedMessage, maxLines = 2, style = QztcTypography.meta, color = colors.mut)
            }
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (expanded) "收起课程设置" else "展开课程设置", tint = colors.mut)
        }
        if (expanded && state.enabled) {
            var minutes by rememberSaveable(course.id, overrideMinutes) { mutableStateOf(overrideMinutes ?: -1) }
            var message by rememberSaveable(course.id, savedMessage) { mutableStateOf(savedMessage) }
            Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReminderMinuteChoices(minutes, { minutes = it }, allowDefault = true)
                OutlinedTextField(message, { message = it.take(240) }, Modifier.fillMaxWidth(), label = { Text("额外提醒内容") }, minLines = 2, maxLines = 4)
                Button(onClick = { onSave(minutes.takeIf { it >= 0 }, message.trim().takeIf { it.isNotEmpty() }) }, modifier = Modifier.align(Alignment.End)) { Text("保存") }
            }
        }
    }
}
