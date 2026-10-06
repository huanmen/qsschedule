package cn.qztc.timetable.feature.timetable

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.domain.model.ScheduleActivity
import cn.qztc.timetable.core.domain.schedule.ActivityWeekLayout
import cn.qztc.timetable.core.ui.glass.GlassRole
import cn.qztc.timetable.core.ui.glass.GlassSurface
import cn.qztc.timetable.core.ui.motion.pressable
import cn.qztc.timetable.core.ui.theme.CoursePalette
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcPageTopBar
import cn.qztc.timetable.core.ui.theme.QztcSpacing
import cn.qztc.timetable.core.ui.theme.QztcTypography

@Composable
fun DayScheduleScreen(
    scheduleState: DemoScheduleState,
    week: Int,
    day: Int,
    onBack: () -> Unit,
    reminderState: CourseReminderUiState = CourseReminderUiState(),
    onSaveReminder: (String, Int?, String?) -> Unit = { _, _, _ -> },
    coursePresetColors: List<Int> = CoursePalette.indices.toList(),
    onSaveActivity: (ActivityEditDraft, List<String>) -> Unit = { _, _ -> },
    onDeleteActivity: (String, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val colors = LocalQztcColors.current
    val dayName = listOf("一", "二", "三", "四", "五", "六", "日")[day - 1]
    val date = scheduleState.dateFor(week, day)
    val appliedChanges = scheduleState.applied.toSet()
    val occurrences = remember(week, day, appliedChanges, scheduleState.importedSchedule) {
        scheduleState.occurrences(week, appliedChanges).filter { it.weekday == day && !it.ghost }
    }
    val dayActivities = remember(week, day, scheduleState.importedSchedule) {
        scheduleState.activitiesOn(week, day)
    }
    var detailActivity by remember { mutableStateOf<ScheduleActivity?>(null) }
    var reminderOccurrence by remember { mutableStateOf<DemoOccurrence?>(null) }
    var pendingActivitySave by remember { mutableStateOf<PendingActivitySave?>(null) }
    val totalWeeks = scheduleState.importedSchedule?.semester?.totalWeeks ?: 20

    detailActivity?.let { activity ->
        ActivityDetailPage(
            activity = activity,
            presetColors = coursePresetColors,
            totalWeeks = totalWeeks,
            fallbackWeekday = day,
            fallbackDate = date,
            onBack = { detailActivity = null },
            onSave = { draft ->
                val hits = findActivityConflicts(draft, scheduleState)
                onSaveActivity(draft, hits.map { it.meeting.id })
                detailActivity = null
            },
            onDelete = {
                onDeleteActivity(activity.id, it)
                detailActivity = null
            },
        )
        pendingActivitySave?.let { pending ->
            ActivityConflictDialog(
                hits = pending.hits,
                onRemoveCourses = {
                    onSaveActivity(pending.draft, pending.hits.map { it.meeting.id })
                    pending.afterSave()
                    pendingActivitySave = null
                },
                onKeepCourses = {
                    onSaveActivity(pending.draft, emptyList())
                    pending.afterSave()
                    pendingActivitySave = null
                },
                onDismiss = { pendingActivitySave = null },
            )
        }
        return
    }

    reminderOccurrence?.let { occurrence ->
        CourseReminderSheet(
            occurrence = occurrence,
            state = reminderState,
            onSave = { minutes, message -> onSaveReminder(occurrence.course.id, minutes, message) },
            onDismiss = { reminderOccurrence = null },
        )
    }
    Column(modifier.fillMaxSize().background(colors.solid2)) {
        QztcPageTopBar(
            title = "周${dayName} · ${date.monthValue}月${date.dayOfMonth}日",
            subtitle = scheduleState.academicCaption(week, day) ?: "第${week}周当天安排",
            onBack = onBack,
        )
        val dayCaption = scheduleState.academicCaption(week, day)
        if (occurrences.isEmpty() && dayActivities.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(dayCaption ?: "这一天没有课程或活动", style = QztcTypography.body, color = colors.mut)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(QztcSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(QztcSpacing.md),
            ) {
                items(dayActivities, key = { it.id }) { activity ->
                    GlassSurface(
                        role = GlassRole.CONTENT,
                        modifier = Modifier.fillMaxWidth().clickable { detailActivity = activity }.pressable(),
                    ) {
                        Column(Modifier.padding(QztcSpacing.lg), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(activity.name, style = QztcTypography.courseTitle, color = colors.ink)
                            Text(ActivityWeekLayout.displayTime(activity), style = QztcTypography.meta, color = colors.mut)
                            if (activity.note.isNotBlank()) {
                                Text(activity.note, style = QztcTypography.meta, color = colors.mut)
                            }
                        }
                    }
                }
                itemsIndexed(occurrences, key = { index, occurrence -> "${occurrence.id}-$index" }) { _, occurrence ->
                    val periodStart = scheduleState.actualPeriodFor(occurrence.periodStart)
                    val periodEnd = scheduleState.actualPeriodFor(occurrence.periodEnd)
                    GlassSurface(role = GlassRole.CONTENT, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(QztcSpacing.lg),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(occurrence.visualColor(colors.isDark).bar))
                            Spacer(Modifier.width(QztcSpacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(occurrence.course.name, style = QztcTypography.courseTitle, color = colors.ink)
                                Spacer(Modifier.height(4.dp))
                                Text(occurrence.teacher, style = QztcTypography.meta, color = colors.mut)
                                androidx.compose.material3.TextButton(onClick = { reminderOccurrence = occurrence }) {
                                    Text("提醒设置")
                                }
                                Text("${CampusPlaceResolver.normalize(occurrence.room)} · ${displayPeriodRangeLabel(occurrence.periodStart, occurrence.periodEnd)}", style = QztcTypography.meta, color = colors.mut)
                            }
                            Text("${periodStart.startTime}\n${periodEnd.endTime}", style = QztcTypography.meta, color = colors.mut)
                        }
                    }
                }
            }
        }
    }
}
