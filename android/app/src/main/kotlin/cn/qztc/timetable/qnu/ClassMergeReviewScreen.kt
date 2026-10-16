package cn.qztc.timetable.qnu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.testTag
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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.domain.imports.PersonalClassMerger
import cn.qztc.timetable.core.domain.schedule.TimetableState
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcPageTopBar
import cn.qztc.timetable.core.ui.theme.QztcSpacing
import cn.qztc.timetable.core.ui.theme.QztcTypography

@Composable
fun ClassMergeReviewScreen(
    result: PersonalClassMerger.Result,
    onConfirm: (TimetableState) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalQztcColors.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val selected = remember(result) {
        mutableStateMapOf<String, Boolean>().apply {
            val conflictingClassMeetings = result.overlapConflicts.map { it.classMeetingId }.toSet()
            result.extras.forEach { extra ->
                // A conflicting class row is visible for review but is not
                // silently added on confirmation. The user can still opt in.
                put(extra.meeting.id, extra.meeting.id !in conflictingClassMeetings)
            }
        }
    }
    val extraNames = remember(result) { mutableStateMapOf<String, String>() }
    val extraTeachers = remember(result) { mutableStateMapOf<String, String>() }
    val extraRooms = remember(result) { mutableStateMapOf<String, String>() }
    val editingExtras = remember(result) { mutableStateMapOf<String, Boolean>() }
    val teachers = remember(result) {
        mutableStateMapOf<String, String>().apply {
            result.teacherConflicts.forEach { conflict ->
                put(
                    conflict.meetingId,
                    conflict.personalTeacher.orEmpty(),
                )
            }
        }
    }

    Column(modifier.fillMaxSize().background(colors.solid2)) {
        QztcPageTopBar(
            title = "合并课表",
            subtitle = "相同课程保留个人课表，班级多出的课程请勾选或编辑",
            onBack = onBack,
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = QztcSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(QztcSpacing.md),
        ) {
            if (result.teacherConflicts.isNotEmpty()) {
                item { Text("教师差异", style = QztcTypography.section, color = colors.mut) }
                items(result.teacherConflicts, key = { "teacher:${it.meetingId}" }) { conflict ->
                    val key = conflict.meetingId
                    Column(Modifier.fillMaxWidth().background(colors.solid).padding(QztcSpacing.md)) {
                        Text(conflict.courseName, style = QztcTypography.courseTitle, color = colors.ink)
                        Text("周${conflict.weekday} 第${conflict.periodStart}-${conflict.periodEnd}节", style = QztcTypography.meta, color = colors.mut)
                        Text(
                            "个人：${conflict.personalTeacher ?: "无"} · 班级还写了 ${conflict.extraTeachers.joinToString("、")}",
                            style = QztcTypography.meta,
                            color = colors.mut,
                        )
                        OutlinedTextField(
                            value = teachers[key].orEmpty(),
                            onValueChange = { teachers[key] = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("采用的教师名") },
                            singleLine = true,
                        )
                    }
                }
            }
            if (result.overlapConflicts.isNotEmpty()) {
                item { Text("时间冲突", style = QztcTypography.section, color = colors.danger) }
                items(result.overlapConflicts, key = { "overlap:${it.personalMeetingId}:${it.classMeetingId}" }) { conflict ->
                    Column(
                        Modifier.fillMaxWidth().background(colors.solid).clickable {
                            val extraIndex = result.extras.indexOfFirst { it.meeting.id == conflict.classMeetingId }
                            if (extraIndex >= 0) scope.launch {
                                val before = (if (result.teacherConflicts.isEmpty()) 0 else result.teacherConflicts.size + 1) +
                                    result.overlapConflicts.size + 1 + 1
                                listState.animateScrollToItem(before + extraIndex)
                            }
                        }.padding(QztcSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("个人课表：${conflict.personalCourseName}", style = QztcTypography.courseTitle, color = colors.ink)
                        Text("班级课表：${conflict.classCourseName}", style = QztcTypography.courseTitle, color = colors.ink)
                        Text(
                            "周${conflict.weekday} · 个人第${conflict.personalPeriodStart}-${conflict.personalPeriodEnd}节，与班级第${conflict.classPeriodStart}-${conflict.classPeriodEnd}节重叠",
                            style = QztcTypography.meta,
                            color = colors.danger,
                        )
                        Text("重叠周次：${conflict.weeks.joinToString("、")}", style = QztcTypography.meta, color = colors.mut)
                        Text("请取消勾选班级多出的课程，或返回修改来源课表。", style = QztcTypography.meta, color = colors.mut)
                    }
                }
            }
            if (result.extras.isNotEmpty()) {
                item { Text("班级多出的课程", style = QztcTypography.section, color = colors.mut) }
                items(result.extras, key = { "extra:${it.meeting.id}" }) { extra ->
                    val extraKey = extra.meeting.id
                    Column(
                        Modifier.fillMaxWidth().testTag("merge-extra-$extraKey").background(colors.solid).padding(QztcSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selected[extraKey] == true,
                                onCheckedChange = { selected[extraKey] = it },
                            )
                            Text("加入个人课表", style = QztcTypography.meta, color = colors.mut)
                            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                            TextButton(onClick = { editingExtras[extraKey] = editingExtras[extraKey] != true }) {
                                Text(if (editingExtras[extraKey] == true) "收起编辑" else "编辑")
                            }
                        }
                        if (editingExtras[extraKey] != true) {
                            Text(extraNames[extraKey] ?: extra.course.name, style = QztcTypography.courseTitle, color = colors.ink)
                            Text(listOf(extraTeachers[extraKey] ?: extra.meeting.teacher.orEmpty(),
                                extraRooms[extraKey] ?: extra.meeting.room.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "),
                                style = QztcTypography.meta, color = colors.mut)
                        } else {
                        OutlinedTextField(
                            value = extraNames[extraKey] ?: extra.course.name,
                            onValueChange = { extraNames[extraKey] = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("课程名") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = extraTeachers[extraKey] ?: extra.meeting.teacher.orEmpty(),
                            onValueChange = { extraTeachers[extraKey] = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("教师") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = extraRooms[extraKey] ?: extra.meeting.room.orEmpty(),
                            onValueChange = { extraRooms[extraKey] = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("地点") },
                            singleLine = true,
                        )
                        }
                        Text(
                            "周${extra.meeting.weekday} 第${extra.meeting.periodStart}-${extra.meeting.periodEnd}节",
                            style = QztcTypography.meta,
                            color = colors.mut,
                        )
                    }
                }
            }
            if (result.extras.isEmpty() && result.teacherConflicts.isEmpty() && result.overlapConflicts.isEmpty()) {
                item { Text("两份课表没有需要确认的差异，将按个人课表导入。", style = QztcTypography.body, color = colors.ink) }
            }
        }
        Button(
            onClick = {
                val extras = result.extras.filter { selected[it.meeting.id] == true }.map { extra ->
                    val key = extra.meeting.id
                    PersonalClassMerger.ExtraSlot(
                        course = extra.course.copy(name = extraNames[key] ?: extra.course.name),
                        meeting = extra.meeting.copy(
                            teacher = extraTeachers[key] ?: extra.meeting.teacher,
                            room = extraRooms[key] ?: extra.meeting.room,
                        ),
                    )
                }
                val teacherByMeeting = buildMap {
                    result.preferred.meetings.forEach { meeting ->
                        val key = meeting.id
                        teachers[key]?.takeIf { it.isNotBlank() }?.let { put(meeting.id, it) }
                    }
                }
                onConfirm(PersonalClassMerger.apply(result.preferred, extras, teacherByMeeting))
            },
            modifier = Modifier.fillMaxWidth().padding(QztcSpacing.lg),
        ) {
            Text("确认导入")
        }
    }
}
