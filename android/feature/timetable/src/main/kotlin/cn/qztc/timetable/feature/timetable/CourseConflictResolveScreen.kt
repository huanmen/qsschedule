package cn.qztc.timetable.feature.timetable

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import cn.qztc.timetable.core.domain.calendar.weekdayLabel
import cn.qztc.timetable.core.domain.schedule.MeetingConflictHit
import cn.qztc.timetable.core.ui.glass.GlassRole
import cn.qztc.timetable.core.ui.glass.GlassSurface
import cn.qztc.timetable.core.ui.motion.pressable
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcPageTopBar
import cn.qztc.timetable.core.ui.theme.QztcSpacing
import cn.qztc.timetable.core.ui.theme.QztcTypography

internal data class CourseConflictSession(
    val draft: CourseEditDraft,
    val ignoreMeetingId: String?,
    val originOccurrence: DemoOccurrence?,
)

internal fun MeetingConflictHit.toDemoOccurrence(scheduleState: DemoScheduleState): DemoOccurrence? {
    val schedule = scheduleState.importedSchedule ?: return null
    val course = schedule.state.courses.firstOrNull { it.id == courseId } ?: return null
    val meeting = meeting ?: return null
    return DemoOccurrence(
        course = course,
        paletteIndex = course.color ?: 0,
        weekday = meeting.weekday,
        periodStart = meeting.periodStart,
        periodEnd = meeting.periodEnd,
        room = meeting.room.orEmpty(),
        teacher = meeting.teacher.orEmpty(),
        meetingId = meeting.id,
        weeks = meeting.weekRule.weeks,
    )
}

internal fun MeetingConflictHit.summary(): String {
    val rule = meeting?.weekRule?.weeks ?: weeks
    val parity = when {
        rule.isNotEmpty() && rule.all { it % 2 == 1 } -> "单周 · "
        rule.isNotEmpty() && rule.all { it % 2 == 0 } -> "双周 · "
        else -> ""
    }
    val weekText = if (weeks.isEmpty()) "相关周次" else "第 " + weeks.joinToString("、") + " 周"
    return "${weekdayLabel(weekday)} · ${displayPeriodRangeLabel(periodStart, periodEnd)} · $parity$weekText"
}

@Composable
internal fun CourseConflictResolveScreen(
    session: CourseConflictSession,
    scheduleState: DemoScheduleState,
    presetColors: List<Int>,
    totalWeeks: Int,
    onSaveConflictCourse: (DemoOccurrence, CourseEditDraft) -> Unit,
    onDeleteConflictCourse: (DemoOccurrence) -> Unit,
    onCommitOriginal: () -> Unit,
    onDismiss: () -> Unit,
    onCoverBottomBar: (Boolean) -> Unit = {},
) {
    val colors = LocalQztcColors.current
    var hits by remember { mutableStateOf(findCourseConflicts(session.draft, scheduleState, session.ignoreMeetingId)) }
    var selectedIndex by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<DemoOccurrence?>(null) }
    var editorWarning by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(scheduleState.importedSchedule, session.draft, session.ignoreMeetingId) {
        hits = findCourseConflicts(session.draft, scheduleState, session.ignoreMeetingId)
        if (selectedIndex >= hits.size) selectedIndex = 0
    }

    DisposableEffect(Unit) {
        onCoverBottomBar(true)
        onDispose { onCoverBottomBar(false) }
    }

    editing?.let { occurrence ->
        BackHandler { editing = null; editorWarning = null }
        CourseEditPage(
            title = "改冲突课程",
            editorKey = "conflict-${occurrence.meetingId ?: occurrence.id}",
            initial = occurrence.toEditDraft(scheduleState),
            conflictsFor = { findCourseConflicts(it, scheduleState, occurrence.meetingId) },
            presetColors = presetColors,
            totalWeeks = totalWeeks,
            showDelete = true,
            warning = editorWarning,
            onBack = { editing = null; editorWarning = null },
            onSave = { draft ->
                val nested = findCourseConflicts(draft, scheduleState, occurrence.meetingId)
                if (nested.isEmpty() || draft.overwriteConflicts) {
                    onSaveConflictCourse(occurrence, draft)
                    editing = null
                    editorWarning = null
                } else {
                    editorWarning = "这门课改完后仍和 ${nested.joinToString("、") { it.courseName }} 冲突，请继续改时间或周次，也可以先去改那些课。"
                }
            },
            onDelete = {
                onDeleteConflictCourse(occurrence)
                editing = null
                editorWarning = null
            },
        )
        return
    }

    BackHandler(onBack = onDismiss)
    Column(Modifier.fillMaxSize().background(colors.solid2)) {
        QztcPageTopBar(
            title = "时间冲突",
            subtitle = "改完这些课、确认没有重叠后才能保存",
            onBack = onDismiss,
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = QztcSpacing.lg, end = QztcSpacing.lg, bottom = QztcSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(QztcSpacing.md),
        ) {
            item {
                Text(
                    "当前要保存的是「${session.draft.name}」· ${weekdayLabel(session.draft.weekday)} · ${displayPeriodRangeLabel(session.draft.periodStart, session.draft.periodEnd)}。停课或已调走的位置可以直接占用。",
                    style = QztcTypography.meta,
                    color = colors.mut,
                )
            }
            if (hits.isEmpty()) {
                item {
                    GlassSurface(role = GlassRole.CONTENT, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(QztcSpacing.lg), verticalArrangement = Arrangement.spacedBy(QztcSpacing.sm)) {
                            Text("冲突已经消除", style = QztcTypography.courseTitle, color = colors.ink)
                            Text("可以保存当前这门课了。", style = QztcTypography.meta, color = colors.mut)
                        }
                    }
                }
            } else {
                item {
                    Text("点某一门课去改，可以来回切换。", style = QztcTypography.section, color = colors.mut)
                }
                if (hits.size > 1) {
                    item {
                        hits.chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth().padding(bottom = QztcSpacing.sm), horizontalArrangement = Arrangement.spacedBy(QztcSpacing.sm)) {
                                row.forEach { hit ->
                                    val index = hits.indexOf(hit)
                                    EditChoice(
                                        text = hit.courseName.take(6),
                                        selected = selectedIndex == index,
                                        modifier = Modifier.weight(1f),
                                    ) { selectedIndex = index.coerceAtLeast(0) }
                                }
                                repeat(3 - row.size) {
                                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                itemsIndexed(hits, key = { _, hit -> hit.meeting?.id ?: "${hit.courseId}-${hit.weeks}" }) { index, hit ->
                    GlassSurface(
                        role = GlassRole.CONTENT,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressable()
                            .then(
                                if (selectedIndex == index) Modifier else Modifier,
                            ),
                    ) {
                        Column(Modifier.padding(QztcSpacing.lg), verticalArrangement = Arrangement.spacedBy(QztcSpacing.sm)) {
                            Text(hit.courseName, style = QztcTypography.courseTitle, color = colors.ink)
                            Text(hit.summary(), style = QztcTypography.meta, color = colors.mut)
                            if (hit.meeting == null) {
                                Text("这是调入或补课占用的时间，请改当前课程，或先在课表里处理那次调课。", style = QztcTypography.meta, color = colors.danger)
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        selectedIndex = index
                                        val target = hit.toDemoOccurrence(scheduleState)
                                        if (target != null) {
                                            editorWarning = null
                                            editing = target
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(if (selectedIndex == index) "修改这门课" else "改成这门课")
                                }
                            }
                        }
                    }
                }
            }
        }
        Column(
            Modifier.padding(horizontal = QztcSpacing.lg, vertical = QztcSpacing.md),
            verticalArrangement = Arrangement.spacedBy(QztcSpacing.sm),
        ) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("返回修改当前课程")
            }
            Button(
                onClick = onCommitOriginal,
                enabled = hits.isEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存当前课程")
            }
        }
    }
}
