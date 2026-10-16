package cn.qztc.timetable.plugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.domain.model.Course
import cn.qztc.timetable.core.domain.model.CourseMeeting
import cn.qztc.timetable.core.ui.plugin.PluginCard
import cn.qztc.timetable.core.ui.plugin.PluginPage
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcTypography
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

@Composable
fun ScheduleQaPluginScreen(semesterLabel: String, courses: List<Course>, meetings: List<CourseMeeting>, onBack: () -> Unit) {
    val colors = LocalQztcColors.current
    var question by rememberSaveable { mutableStateOf("") }
    var answer by rememberSaveable { mutableStateOf("") }
    PluginPage("AI 课表问答", onBack) {
        PluginCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.QuestionAnswer, null, tint = colors.accentDeep)
                    Column { Text("当前课表", style = QztcTypography.courseTitle, color = colors.ink); Text(semesterLabel, style = QztcTypography.meta, color = colors.mut) }
                }
                OutlinedTextField(question, { if (it.length <= 200) question = it }, Modifier.fillMaxWidth(), singleLine = false,
                    label = { Text("问问你的课表") }, placeholder = { Text("例如：数据结构周几上课？") })
                Button(onClick = { answer = answerScheduleQuestion(question, courses, meetings) }, modifier = Modifier.fillMaxWidth()) { Text("查询") }
            }
        }
        if (answer.isNotBlank()) PluginCard { Text(answer, Modifier.padding(16.dp), color = colors.ink) }
        Text("答案来自本地课表；后续可在插件设置中接入你自己的模型接口。", Modifier.padding(horizontal = 16.dp), style = QztcTypography.meta, color = colors.mut)
    }
}

private fun answerScheduleQuestion(question: String, courses: List<Course>, meetings: List<CourseMeeting>): String {
    val hit = courses.firstOrNull { question.contains(it.name, true) || (!it.code.isNullOrBlank() && question.contains(it.code!!, true)) }
    if (hit == null) return "没有找到对应课程。可以直接输入课程名称或课程号。"
    val slots = meetings.filter { it.courseId == hit.id }
    if (slots.isEmpty()) return "${hit.name} 已在课表中，但没有可显示的上课安排。"
    val day = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")
    return slots.joinToString("\n") { slot ->
        val weekday = day.getOrElse(slot.weekday) { "周${slot.weekday}" }
        "${hit.name}：$weekday · 第 ${slot.periodStart}-${slot.periodEnd} 节${slot.room?.let { room -> " · $room" }.orEmpty()}${slot.teacher?.let { teacher -> " · $teacher" }.orEmpty()}"
    }
}

@Composable
fun ExamSchedulePluginScreen(semesterLabel: String, onBack: () -> Unit) {
    val colors = LocalQztcColors.current
    var name by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(7).toString()) }
    var time by rememberSaveable { mutableStateOf("09:00") }
    var room by rememberSaveable { mutableStateOf("") }
    var saved by rememberSaveable { mutableStateOf(false) }
    var datePicker by remember { mutableStateOf(false) }
    var timePicker by remember { mutableStateOf(false) }
    if (datePicker) cn.qztc.timetable.core.ui.theme.QztcDatePicker(LocalDate.parse(date), onDismiss = { datePicker = false }) {
        date = it.toString(); datePicker = false
    }
    if (timePicker) cn.qztc.timetable.core.ui.theme.QztcTimePicker(LocalTime.parse(time), onDismiss = { timePicker = false }) {
        time = it.toString(); timePicker = false
    }
    PluginPage("考试日程 & 倒计时", onBack) {
        PluginCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.CalendarMonth, null, tint = colors.accentDeep)
                    Column { Text("考试安排", style = QztcTypography.courseTitle, color = colors.ink); Text(semesterLabel, style = QztcTypography.meta, color = colors.mut) }
                }
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("考试名称") }, singleLine = true)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedButton(onClick = { datePicker = true }, Modifier.weight(1f)) { Text(date) }
                    androidx.compose.material3.OutlinedButton(onClick = { timePicker = true }, Modifier.weight(1f)) { Text(time) }
                }
                OutlinedTextField(room, { room = it }, Modifier.fillMaxWidth(), label = { Text("地点（可选）") }, singleLine = true)
                Button(onClick = { saved = true }, modifier = Modifier.fillMaxWidth()) { Text("保存考试") }
            }
        }
        if (saved) PluginCard {
            val countdown = runCatching {
                val target = LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time))
                val minutes = ChronoUnit.MINUTES.between(LocalDateTime.now(), target)
                if (minutes < 0) "已结束" else "还剩 ${minutes / (24 * 60)} 天 ${(minutes % (24 * 60)) / 60} 小时 ${minutes % 60} 分钟"
            }.getOrDefault("日期或时间格式不正确")
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name.ifBlank { "未命名考试" }, style = QztcTypography.courseTitle, color = colors.ink)
                Text("$date $time${room.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}", style = QztcTypography.meta, color = colors.mut)
                Text(countdown, color = colors.accentDeep)
            }
        }
    }
}
