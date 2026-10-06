package cn.qztc.timetable.feature.timetable

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import cn.qztc.timetable.core.domain.schedule.MeetingConflict
import cn.qztc.timetable.core.domain.schedule.MeetingConflictHit
import cn.qztc.timetable.core.ui.glass.GlassRole
import cn.qztc.timetable.core.ui.glass.GlassSurface
import cn.qztc.timetable.core.ui.motion.pressable
import cn.qztc.timetable.core.ui.theme.CoursePalette
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcCorners
import cn.qztc.timetable.core.ui.theme.QztcPageTopBar
import cn.qztc.timetable.core.ui.theme.QztcSpacing
import cn.qztc.timetable.core.ui.theme.QztcTopIconButton
import cn.qztc.timetable.core.ui.theme.QztcTypography
import cn.qztc.timetable.core.domain.model.ActivityKind
import cn.qztc.timetable.core.domain.model.DaySegment
import cn.qztc.timetable.core.domain.model.QnuPeriodDefaults
import cn.qztc.timetable.core.domain.model.ScheduleActivity
import cn.qztc.timetable.core.domain.calendar.AcademicDayKind
import cn.qztc.timetable.core.domain.calendar.AcademicCalendar
import cn.qztc.timetable.core.domain.schedule.ActivityWeekLayout
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.delay

private enum class OccurrenceStatus { UPCOMING, CURRENT, ENDED, CANCELLED }
private enum class FocusState { BEFORE, IN_CLASS, BETWEEN, AFTER_LAST, NO_CLASS, PREVIEW }
private enum class TodayDayPart(val label: String) { MORNING("早间"), AFTERNOON("午后"), EVENING("晚间") }

data class WeeklySlotDraft(
    val weekday: Int,
    val periodStart: Int,
    val periodEnd: Int,
    val weeks: List<Int> = emptyList(),
    val sourceMeetingId: String? = null,
)

data class CourseEditDraft(
    val name: String,
    val teacher: String,
    val room: String,
    val weekday: Int,
    val periodStart: Int,
    val periodEnd: Int,
    val weeks: List<Int>,
    val color: Int,
    val location: cn.qztc.timetable.core.domain.model.ScheduleLocation = cn.qztc.timetable.core.domain.model.ScheduleLocation(name = room),
    val weeklySlots: List<WeeklySlotDraft> = emptyList(),
    val overwriteConflicts: Boolean = false,
)

internal data class PendingCourseSave(
    val draft: CourseEditDraft,
    val ignoreMeetingId: String?,
    val originOccurrence: DemoOccurrence?,
    val apply: () -> Unit,
)

internal fun findCourseConflicts(
    draft: CourseEditDraft,
    scheduleState: DemoScheduleState,
    ignoreMeetingId: String? = null,
): List<MeetingConflictHit> {
    val schedule = scheduleState.importedSchedule ?: return emptyList()
    val slots = draft.weeklySlots.ifEmpty { listOf(WeeklySlotDraft(draft.weekday, draft.periodStart, draft.periodEnd)) }
    val ignoredCourse = schedule.state.meetings.firstOrNull { it.id == ignoreMeetingId }?.courseId
    return slots.flatMap { slot ->
        MeetingConflict.overlapping(
            weekday = slot.weekday,
            periodStart = slot.periodStart,
            periodEnd = slot.periodEnd,
            weeks = slot.weeks.ifEmpty { draft.weeks },
            state = schedule.state,
            semester = schedule.semester,
            ignoreMeetingId = ignoreMeetingId,
            calendar = AcademicCalendar(schedule.academicDays),
        )
    }.filterNot { ignoredCourse != null && it.courseId == ignoredCourse }
        .groupBy { "${it.meeting?.id}:${it.courseId}:${it.weekday}:${it.periodStart}-${it.periodEnd}" }
        .values.map { hits -> hits.first().copy(weeks = hits.flatMap { it.weeks }.distinct().sorted()) }
}

data class ActivityEditDraft(
    val id: String? = null,
    val name: String,
    val note: String = "",
    val timeText: String = "",
    val kind: ActivityKind,
    val weekday: Int,
    val periodStart: Int,
    val periodEnd: Int,
    val weeks: List<Int>,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val color: Int,
    val sessions: List<cn.qztc.timetable.core.domain.model.ActivitySession> = emptyList(),
    val restoreCoursesOnDelete: Boolean = true,
)

@Composable
fun TodayScreen(
    scheduleState: DemoScheduleState,
    coursePresetColors: List<Int> = CoursePalette.indices.toList(),
    reminderState: CourseReminderUiState = CourseReminderUiState(),
    onSaveReminder: (String, Int?, String?) -> Unit = { _, _, _ -> },
    onSaveCourseEdit: (DemoOccurrence, CourseEditDraft) -> Unit = { _, _ -> },
    onDeleteCourse: (DemoOccurrence) -> Unit = {},
    onSaveActivity: (ActivityEditDraft, removeMeetingIds: List<String>) -> Unit = { _, _ -> },
    onDeleteActivity: (String, Boolean) -> Unit = { _, _ -> },
    onCoverBottomBar: (Boolean) -> Unit = {},
    nowProvider: () -> LocalDateTime = { LocalDateTime.now() },
    modifier: Modifier = Modifier,
) {
    val colors = LocalQztcColors.current
    val context = LocalContext.current
    val campusPlaces = remember(context) { CampusPlaceRepository.load(context) }
    val week = scheduleState.currentWeek
    var clockDate by remember { mutableStateOf(nowProvider().toLocalDate()) }
    val todayDay = if (scheduleState.importedSchedule != null && scheduleState.isCurrentDateInImportedSemester) {
        clockDate.dayOfWeek.value
    } else {
        scheduleState.todayWeekday
    }
    val viewedDay = scheduleState.selectedDay
    var dateSheetOpen by remember { mutableStateOf(false) }
    var courseMenu by remember { mutableStateOf(CourseMenuState()) }
    var detailOccurrence by remember { mutableStateOf<DemoOccurrence?>(null) }
    var detailActivity by remember { mutableStateOf<ScheduleActivity?>(null) }
    var pendingActivitySave by remember { mutableStateOf<PendingActivitySave?>(null) }
    var pendingCourseSave by remember { mutableStateOf<PendingCourseSave?>(null) }
    var reminderOccurrence by remember { mutableStateOf<DemoOccurrence?>(null) }
    var navigationOccurrence by remember { mutableStateOf<DemoOccurrence?>(null) }
    var mapNavigationOccurrence by remember { mutableStateOf<DemoOccurrence?>(null) }
    var showCancelled by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val isToday = scheduleState.isCurrentDateInImportedSemester && viewedDay == todayDay
    val isReferenceDay = viewedDay == scheduleState.referenceWeekday
    var realNow by remember { mutableStateOf(nowProvider().toLocalTime()) }
    LaunchedEffect(scheduleState.importedSchedule) {
        if (scheduleState.importedSchedule == null) return@LaunchedEffect
        while (true) {
            val current = nowProvider()
            val previousDate = clockDate
            val wasFollowingToday = scheduleState.selectedDay == previousDate.dayOfWeek.value
            clockDate = current.toLocalDate()
            realNow = current.toLocalTime()
            if (current.toLocalDate() != previousDate && wasFollowingToday) {
                scheduleState.selectedDay = current.dayOfWeek.value
            }
            val millisToNextMinute = (60L - current.second) * 1_000L - current.nano / 1_000_000L
            delay(millisToNextMinute.coerceAtLeast(250L))
        }
    }
    val now = if (scheduleState.importedSchedule == null) scheduleState.scenario.time else realNow
    val appliedChanges = scheduleState.applied.toSet()
    val occurrences = remember(
        week,
        viewedDay,
        todayDay,
        scheduleState.currentSemesterWeek,
        appliedChanges,
        scheduleState.importedSchedule,
    ) {
        scheduleState.occurrences(week, appliedChanges)
            .filter { it.weekday == viewedDay && !it.ghost }
    }
    val visibleOccurrences = remember(occurrences) { occurrences.filterNot { it.cancelled } }
    val listedOccurrences = remember(occurrences, showCancelled) {
        if (showCancelled) occurrences else occurrences.filterNot { it.cancelled }
    }
    val isMakeupDay = scheduleState.academicDayOn(week, viewedDay)?.kind == AcademicDayKind.MAKEUP
    val isHolidayDay = scheduleState.academicDayOn(week, viewedDay)?.kind == AcademicDayKind.HOLIDAY
    val groupedOccurrences = listedOccurrences.groupBy { occurrence ->
        todayDayPartFor(scheduleState.actualPeriodFor(occurrence.periodStart).segment)
    }
    val dayActivities = remember(week, viewedDay, scheduleState.importedSchedule) {
        scheduleState.activitiesOn(week, viewedDay)
    }
    val nextUpcomingId = remember(visibleOccurrences, now, isToday) {
        if (!isToday) {
            null
        } else if (visibleOccurrences.any { it.statusAt(now, scheduleState::actualPeriodFor) == OccurrenceStatus.CURRENT }) {
            null
        } else {
            visibleOccurrences.firstOrNull { it.statusAt(now, scheduleState::actualPeriodFor) == OccurrenceStatus.UPCOMING }?.id
        }
    }
    LaunchedEffect(viewedDay) { courseMenu = CourseMenuState() }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { it }
            .collect { courseMenu = courseMenu.dismiss() }
    }

    detailOccurrence?.let { occurrence ->
        BackHandler {
            if (pendingCourseSave != null) pendingCourseSave = null else detailOccurrence = null
        }
        Box(Modifier.fillMaxSize()) {
            CourseDetailPage(
                occurrence = occurrence,
                editScheduleState = scheduleState,
                periodFor = scheduleState::actualPeriodFor,
                presetColors = coursePresetColors,
                reminderState = reminderState,
                onSaveReminder = onSaveReminder,
                totalWeeks = scheduleState.importedSchedule?.semester?.totalWeeks ?: 20,
                onSaveEdit = { draft ->
                    val hits = findCourseConflicts(draft, scheduleState, occurrence.meetingId)
                    if (hits.isEmpty() || draft.overwriteConflicts) {
                        onSaveCourseEdit(occurrence, draft)
                        detailOccurrence = null
                    } else {
                        pendingCourseSave = PendingCourseSave(draft, occurrence.meetingId, occurrence) {
                            onSaveCourseEdit(occurrence, draft)
                            detailOccurrence = null
                        }
                    }
                },
                onDelete = { onDeleteCourse(occurrence) },
                onBack = { detailOccurrence = null },
                onCoverBottomBar = onCoverBottomBar,
            )
            pendingCourseSave?.let { pending ->
                CourseConflictResolveScreen(
                    session = CourseConflictSession(pending.draft, pending.ignoreMeetingId, pending.originOccurrence),
                    scheduleState = scheduleState,
                    presetColors = coursePresetColors,
                    totalWeeks = scheduleState.importedSchedule?.semester?.totalWeeks ?: 20,
                    onSaveConflictCourse = onSaveCourseEdit,
                    onDeleteConflictCourse = onDeleteCourse,
                    onCommitOriginal = {
                        pending.apply()
                        pendingCourseSave = null
                    },
                    onDismiss = { pendingCourseSave = null },
                    onCoverBottomBar = onCoverBottomBar,
                )
            }
        }
        return
    }

    detailActivity?.let { activity ->
        DisposableEffect(Unit) {
            onCoverBottomBar(true)
            onDispose { onCoverBottomBar(false) }
        }
        val totalWeeks = scheduleState.importedSchedule?.semester?.totalWeeks ?: 20
        ActivityDetailPage(
            activity = activity,
            presetColors = coursePresetColors,
            totalWeeks = totalWeeks,
            fallbackWeekday = viewedDay,
            fallbackDate = scheduleState.dateFor(week, viewedDay),
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

    mapNavigationOccurrence?.let { occurrence ->
        BackHandler { mapNavigationOccurrence = null }
        CampusRouteScreen(
            rawDestination = occurrence.room,
            destinationLocation = occurrence.location,
            onBack = { mapNavigationOccurrence = null },
            onCoverBottomBar = onCoverBottomBar,
        )
        return
    }

    Column(modifier.fillMaxSize()) {
        TodayTopBar(
            week = week,
            dateFor = scheduleState::dateFor,
            viewedDay = viewedDay,
            returnLabel = if (isReferenceDay) null else "回今日",
            dayCaption = scheduleState.academicCaption(week, viewedDay),
            onDateClick = {
                courseMenu = courseMenu.dismiss()
                dateSheetOpen = true
            },
            onReturnToday = { scheduleState.selectedDay = scheduleState.referenceWeekday },
            showCancelled = showCancelled,
            showChangesToggle = scheduleState.hasScheduleChange(week) ||
                (1..7).any { scheduleState.academicDayOn(week, it)?.kind == AcademicDayKind.MAKEUP },
            onToggleCancelled = { showCancelled = !showCancelled },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = QztcSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(QztcSpacing.md),
        ) {
            item {
                FocusCard(
                    occurrences = visibleOccurrences,
                    now = now,
                    isToday = isToday,
                    periodFor = scheduleState::actualPeriodFor,
                    dayCaption = scheduleState.academicCaption(week, viewedDay),
                    holiday = isHolidayDay,
                )
            }
            if (visibleOccurrences.isNotEmpty()) item { DayProgress(visibleOccurrences, if (isToday) now else LocalTime.MIN, scheduleState::actualPeriodFor) }
            if (dayActivities.isNotEmpty()) {
                item {
                    Text(
                        "活动安排",
                        style = QztcTypography.section,
                        color = colors.mut,
                        modifier = Modifier.padding(horizontal = QztcSpacing.xl, vertical = QztcSpacing.xs),
                    )
                }
                items(dayActivities, key = { it.id }) { activity ->
                    GlassSurface(
                        role = GlassRole.CONTENT,
                        modifier = Modifier
                            .padding(horizontal = QztcSpacing.lg)
                            .fillMaxWidth()
                            .clickable { detailActivity = activity }
                            .pressable(),
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
            }
            if (occurrences.isNotEmpty()) {
                TodayDayPart.entries.forEach { part ->
                    val partOccurrences = groupedOccurrences[part].orEmpty()
                    if (partOccurrences.isNotEmpty()) {
                        item(key = "today-part-${part.name}") { TodayDayPartDivider(part.label) }
                        itemsIndexed(partOccurrences, key = { index, occurrence -> "${occurrence.id}-$index" }) { _, occurrence ->
                            val status = occurrence.statusAt(if (isToday) now else LocalTime.MIN, scheduleState::actualPeriodFor)
                            SwipeRevealCourseRow(
                                occurrence = occurrence,
                                status = status,
                                periodFor = scheduleState::actualPeriodFor,
                                badge = occurrence.todayBadge(status, isMakeupDay, nextUpcomingId),
                                revealed = courseMenu.revealedId == occurrence.id,
                                onRevealChange = { open ->
                                    courseMenu = if (open) courseMenu.reveal(occurrence.id) else courseMenu.dismiss()
                                },
                                onOpenDetails = { detailOccurrence = occurrence },
                                onReminder = { reminderOccurrence = occurrence },
                                onNavigate = { navigationOccurrence = occurrence },
                            )
                        }
                    }
                }
            }
        }
    }

    if (dateSheetOpen) {
        DateChoiceSheet(
            week = week,
            selectedDay = viewedDay,
            appliedChanges = scheduleState.applied.toSet(),
            semesterLabel = scheduleState.importedSchedule?.semester?.name ?: "未导入课表",
            dateFor = scheduleState::dateFor,
            markFor = { day -> scheduleState.academicCaption(week, day) },
            occurrencesFor = { selectedWeek, changes -> scheduleState.occurrences(selectedWeek, changes) },
            onSelect = { scheduleState.selectedDay = it; dateSheetOpen = false },
            onDismiss = { dateSheetOpen = false },
        )
    }
    reminderOccurrence?.let { occurrence ->
        CourseReminderSheet(
            occurrence = occurrence,
            state = reminderState,
            onSave = { minutes, message ->
                onSaveReminder(occurrence.course.id, minutes, message)
                reminderOccurrence = null
            },
            onDismiss = { reminderOccurrence = null },
        )
    }
    navigationOccurrence?.let { occurrence ->
        CourseNavigationSheet(
            occurrence = occurrence,
            onInAppMap = {
                navigationOccurrence = null
                mapNavigationOccurrence = occurrence
            },
            onExternalNavigation = {
                navigationOccurrence = null
                campusPlaces.resolve(occurrence.room)?.let { destination ->
                    openCampusExternalNavigation(context, null, destination)
                }
            },
            onDismiss = { navigationOccurrence = null },
        )
    }
}

@Composable
private fun TodayTopBar(
    week: Int,
    dateFor: (Int, Int) -> java.time.LocalDate,
    viewedDay: Int,
    returnLabel: String?,
    dayCaption: String?,
    onDateClick: () -> Unit,
    onReturnToday: () -> Unit,
    showCancelled: Boolean,
    showChangesToggle: Boolean,
    onToggleCancelled: () -> Unit,
) {
    val colors = LocalQztcColors.current
    val date = dateFor(week, viewedDay)
    val dayNames = listOf("一", "二", "三", "四", "五", "六", "日")
    GlassSurface(
        role = GlassRole.FUNCTIONAL,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        pressGlint = true,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp),
        ) {
            Text(
                compactYearMonth(date),
                style = QztcTypography.compact,
                color = colors.mut,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = QztcSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Column(Modifier.clip(RoundedCornerShape(QztcCorners.functional))
                    .clickable(onClick = onDateClick).pressable(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("第${week}周 · 周${dayNames[viewedDay - 1]}", style = QztcTypography.courseTitle, color = colors.ink)
                    if (!dayCaption.isNullOrBlank()) Text(
                        dayCaption.substringBefore(" · "), style = QztcTypography.compact,
                        color = colors.accentDeep, maxLines = 1,
                    )
                }
                if (returnLabel != null) {
                    Spacer(Modifier.size(8.dp))
                    Text(returnLabel, style = QztcTypography.compact, color = colors.accentDeep,
                        modifier = Modifier.clip(CircleShape).background(colors.accentSoft)
                            .clickable(onClick = onReturnToday).padding(horizontal = 7.dp, vertical = 3.dp))
                }
            }
            if (showChangesToggle) androidx.compose.material3.IconToggleButton(
                checked = showCancelled, onCheckedChange = { onToggleCancelled() },
                modifier = Modifier.align(Alignment.CenterEnd).size(36.dp).clip(CircleShape),
            ) {
                Icon(if (showCancelled) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                    contentDescription = "显示停课", tint = if (showCancelled) colors.accent else colors.mut, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun FocusCard(
    occurrences: List<DemoOccurrence>,
    now: LocalTime,
    isToday: Boolean,
    periodFor: (Int) -> cn.qztc.timetable.core.domain.model.Period,
    dayCaption: String? = null,
    holiday: Boolean = false,
) {
    val colors = LocalQztcColors.current
    val active = occurrences.filterNot { it.cancelled }
    val current = active.firstOrNull { it.statusAt(now, periodFor) == OccurrenceStatus.CURRENT }
    val next = active.firstOrNull { it.statusAt(now, periodFor) == OccurrenceStatus.UPCOMING }
    val focus = if (isToday) current ?: next else active.firstOrNull()
    val state = when {
        active.isEmpty() -> FocusState.NO_CLASS
        !isToday -> FocusState.PREVIEW
        current != null -> FocusState.IN_CLASS
        next == null -> FocusState.AFTER_LAST
        active.any { it.statusAt(now, periodFor) == OccurrenceStatus.ENDED } -> FocusState.BETWEEN
        else -> FocusState.BEFORE
    }
    val lateNight = isToday && (now.hour >= 23 || now.hour < 6)

    GlassSurface(role = GlassRole.CONTENT, modifier = Modifier.padding(horizontal = QztcSpacing.lg).fillMaxWidth()) {
        if (focus == null) {
            Column(Modifier.padding(QztcSpacing.xl), verticalArrangement = Arrangement.spacedBy(QztcSpacing.sm)) {
                Text(
                    when {
                        holiday && !dayCaption.isNullOrBlank() -> dayCaption
                        state == FocusState.NO_CLASS -> "今天没有课"
                        else -> "今天的课都结束啦"
                    },
                    style = QztcTypography.compact,
                    color = colors.accent,
                )
                Text(
                    when {
                        holiday && !dayCaption.isNullOrBlank() -> AcademicCalendar.holidayGreeting(dayCaption)
                        state == FocusState.NO_CLASS -> "好好休息，安排点自己的事吧"
                        else -> "辛苦啦，明天见"
                    },
                    style = QztcTypography.courseTitle,
                    color = colors.mut,
                )
                Text(
                    when {
                        holiday && !dayCaption.isNullOrBlank() -> "校历安排"
                        state == FocusState.NO_CLASS -> "今天没有需要关注的安排"
                        else -> "下一次上课会在开始前提醒你"
                    },
                    style = QztcTypography.meta,
                    color = colors.mut,
                )
            }
        } else {
            val periodStart = periodFor(focus.periodStart)
            val periodEnd = periodFor(focus.periodEnd)
            val remainingFraction = courseRemainingFraction(now, periodStart.startTime, periodEnd.endTime)
            Row(Modifier.fillMaxWidth().heightIn(min = 150.dp)) {
                Box(Modifier.padding(vertical = QztcSpacing.lg).width(4.dp).fillMaxHeight().background(focus.visualColor(colors.isDark).bar))
                Column(Modifier.padding(QztcSpacing.xl).weight(1f), verticalArrangement = Arrangement.spacedBy(QztcSpacing.sm)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(QztcSpacing.sm)) {
                        Text(periodStart.startTime.format(timeFormatter), style = QztcTypography.timeFocus, color = colors.ink)
                        Text("– ${periodEnd.endTime.format(timeFormatter)} · ${displayPeriodRangeLabel(focus.periodStart, focus.periodEnd)}", style = QztcTypography.meta, color = colors.mut)
                    }
                    Text(focus.course.name, style = QztcTypography.pageTitle, color = colors.ink)
                    Text("${focus.room} · ${focus.teacher}", style = QztcTypography.meta, color = colors.mut)
                    CourseRemainingBar(
                            fraction = if (isToday) remainingFraction else 1f,
                            color = focus.visualColor(colors.isDark).bar,
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseRemainingBar(fraction: Float, color: Color) {
    val colors = LocalQztcColors.current
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 380f),
        label = "course-remaining",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(CircleShape)
            .background(colors.endSoft)
            .testTag("focus-course-remaining-progress"),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animatedFraction)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(color),
        )
    }
}

internal fun courseRemainingFraction(now: LocalTime, start: LocalTime, end: LocalTime): Float {
    if (!now.isAfter(start)) return 1f
    if (!now.isBefore(end)) return 0f
    val totalMillis = Duration.between(start, end).toMillis().coerceAtLeast(1L)
    return (Duration.between(now, end).toMillis().toFloat() / totalMillis).coerceIn(0f, 1f)
}

@Composable
private fun DayProgress(
    occurrences: List<DemoOccurrence>,
    now: LocalTime,
    periodFor: (Int) -> cn.qztc.timetable.core.domain.model.Period,
) {
    val colors = LocalQztcColors.current
    Column(Modifier.padding(horizontal = QztcSpacing.xl)) {
        Canvas(Modifier.fillMaxWidth().height(18.dp).testTag("today-schedule-progress").padding(top = 7.dp)) {
            val start = 7 * 60 + 50
            val end = 21 * 60 + 10
            val total = (end - start).toFloat()
            drawLine(colors.endSoft, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 5.dp.toPx(), StrokeCap.Round)
            occurrences.forEach { occurrence ->
                val pStart = periodFor(occurrence.periodStart).startTime.toMinutes()
                val pEnd = periodFor(occurrence.periodEnd).endTime.toMinutes()
                val x1 = ((pStart - start) / total).coerceIn(0f, 1f) * size.width
                val x2 = ((pEnd - start) / total).coerceIn(0f, 1f) * size.width
                drawLine(occurrence.visualColor(colors.isDark).bar.copy(alpha = if (occurrence.cancelled) .25f else .85f), Offset(x1, size.height / 2), Offset(x2, size.height / 2), 5.dp.toPx(), StrokeCap.Round)
            }
            val nowX = ((now.toMinutes() - start) / total).coerceIn(0f, 1f) * size.width
            drawCircle(colors.accent, 5.5.dp.toPx(), Offset(nowX, size.height / 2))
            drawCircle(Color.White, 2.dp.toPx(), Offset(nowX, size.height / 2))
        }
    }
}

@Composable
private fun TodayDayPartDivider(label: String) {
    val colors = LocalQztcColors.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = QztcSpacing.xl, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(QztcSpacing.sm),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(colors.line2))
        Text(label, style = QztcTypography.compact, color = colors.ink,
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(colors.solid).padding(horizontal = 8.dp, vertical = 2.dp))
        Box(Modifier.weight(1f).height(1.dp).background(colors.line2))
    }
}

private fun todayDayPartFor(segment: DaySegment): TodayDayPart = when (segment) {
    DaySegment.MORNING -> TodayDayPart.MORNING
    DaySegment.NOON, DaySegment.AFTERNOON -> TodayDayPart.AFTERNOON
    DaySegment.EVENING -> TodayDayPart.EVENING
}

private fun DemoOccurrence.todayBadge(
    status: OccurrenceStatus,
    isMakeupDay: Boolean,
    nextUpcomingId: String?,
): String? = when {
    cancelled -> "停课"
    // A makeup day is a whole-day calendar state. Its label is already shown
    // in the top bar; repeating it on every course row makes the list noisy.
    moved && !isMakeupDay -> "已调课"
    status == OccurrenceStatus.CURRENT -> "当前"
    id == nextUpcomingId -> "下一节"
    status == OccurrenceStatus.ENDED -> "已结束"
    else -> null
}

@Composable
private fun SwipeRevealCourseRow(
    occurrence: DemoOccurrence,
    status: OccurrenceStatus,
    periodFor: (Int) -> cn.qztc.timetable.core.domain.model.Period,
    badge: String?,
    revealed: Boolean,
    onRevealChange: (Boolean) -> Unit,
    onOpenDetails: () -> Unit,
    onReminder: () -> Unit,
    onNavigate: () -> Unit,
) {
    val colors = LocalQztcColors.current
    val density = LocalDensity.current
    val menuWidth = 168.dp
    val menuPx = with(density) { menuWidth.toPx() }
    val corner = QztcCorners.content
    var dragging by remember(occurrence.id) { mutableStateOf(false) }
    var target by remember(occurrence.id) { mutableFloatStateOf(0f) }
    LaunchedEffect(revealed, occurrence.id) {
        if (!dragging) target = if (revealed) menuPx else 0f
    }
    val animatedReveal by animateFloatAsState(
        targetValue = target,
        animationSpec = if (dragging) snap() else spring(dampingRatio = 0.78f, stiffness = 520f),
        label = "course-glass-reveal",
    )
    val reveal = if (dragging) target else animatedReveal
    val openFraction = if (menuPx <= 0f) 0f else (reveal / menuPx).coerceIn(0f, 1f)
    val rightCorner = if (openFraction in 0.06f..0.94f) 0.dp else corner
    BoxWithConstraints(
        Modifier
            .padding(horizontal = QztcSpacing.lg)
            .fillMaxWidth(),
    ) {
        val viewport = maxWidth
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .testTag("today-course-${occurrence.id}")
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(RoundedCornerShape(topStart = corner, bottomStart = corner, topEnd = rightCorner, bottomEnd = rightCorner))
                .pointerInput(occurrence.id, menuPx) {
                    val tracker = VelocityTracker()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        tracker.resetTracking()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var locked: Boolean? = null
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val dx = change.positionChange().x
                            val adx = abs(change.position.x - down.position.x)
                            val ady = abs(change.position.y - down.position.y)
                            if (locked == null) {
                                val slop = viewConfiguration.touchSlop
                                when {
                                    adx > slop && adx > ady * 1.15f -> {
                                        locked = true
                                        dragging = true
                                    }
                                    ady > slop -> locked = false
                                }
                            }
                            if (locked == true) {
                                change.consume()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                target = (target - dx).coerceIn(0f, menuPx)
                            }
                            if (!change.pressed) break
                        }
                        if (locked == true) {
                            val velocity = tracker.calculateVelocity().x
                            val open = when {
                                velocity < -650f -> true
                                velocity > 650f -> false
                                else -> target > menuPx * 0.28f
                            }
                            target = if (open) menuPx else 0f
                            onRevealChange(open)
                        }
                        dragging = false
                    }
                },
        ) {
            GlassSurface(
                role = GlassRole.CONTENT,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .wrapContentWidth(unbounded = true, align = Alignment.Start)
                    .requiredWidth(viewport + menuWidth)
                    .offset { IntOffset(-reveal.roundToInt(), 0) },
                shape = RoundedCornerShape(corner),
                pressGlint = false,
            ) {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    CourseRowContent(
                        occurrence = occurrence,
                        status = status,
                        periodFor = periodFor,
                        badge = badge,
                        revealPx = reveal,
                        modifier = Modifier
                            .requiredWidth(viewport)
                            .clickable {
                                when {
                                    dragging -> Unit
                                    reveal > 24f -> {
                                        target = 0f
                                        onRevealChange(false)
                                    }
                                    else -> onOpenDetails()
                                }
                            },
                    )
                    Row(
                        Modifier
                            .requiredWidth(menuWidth)
                            .fillMaxHeight(),
                    ) {
                        SwipeActionChip("详情", colors.ink, onClick = { onRevealChange(false); onOpenDetails() })
                        SwipeActionChip("提醒", colors.accentDeep, onClick = { onRevealChange(false); onReminder() })
                        SwipeActionChip("导航", colors.accent, onClick = { onRevealChange(false); onNavigate() })
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeActionChip(label: String, color: Color, onClick: () -> Unit) {
    val colors = LocalQztcColors.current
    Box(
        Modifier
            .width(56.dp)
            .fillMaxHeight()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = QztcTypography.compact, color = color)
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(0.5.dp)
                .background(colors.line.copy(alpha = .45f)),
        )
    }
}

@Composable
private fun CourseRowContent(
    occurrence: DemoOccurrence,
    status: OccurrenceStatus,
    periodFor: (Int) -> cn.qztc.timetable.core.domain.model.Period,
    badge: String?,
    modifier: Modifier = Modifier,
    revealPx: Float = 0f,
) {
    val colors = LocalQztcColors.current
    val palette = occurrence.visualColor(colors.isDark)
    Row(
        modifier = modifier
            .alpha(
                when (status) {
                    OccurrenceStatus.ENDED -> .56f
                    OccurrenceStatus.CANCELLED -> .72f
                    else -> 1f
                },
            )
            .heightIn(min = 80.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(QztcSpacing.md),
    ) {
            val period = periodFor(occurrence.periodStart)
            Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(period.startTime.format(timeFormatter), style = QztcTypography.meta, color = colors.ink)
                Text(displayPeriodRangeLabel(occurrence.periodStart, occurrence.periodEnd), style = QztcTypography.compact, color = colors.mut)
            }
            Box(Modifier.width(4.dp).height(48.dp).clip(CircleShape).background(palette.bar))
            Column(Modifier.weight(1f).testTag("today-info-${occurrence.id}").layout { measurable, constraints ->
                // Time column and stripe scroll away; text stops at the left
                // inset while the action menu continues into the viewport.
                val leading=(52.dp+4.dp+QztcSpacing.md*2).roundToPx()
                val compensation=(revealPx-leading).coerceAtLeast(0f).roundToInt()
                val width=(constraints.maxWidth-compensation).coerceAtLeast(1)
                val placeable=measurable.measure(constraints.copy(minWidth=width,maxWidth=width))
                layout(constraints.maxWidth,placeable.height){placeable.placeRelative(compensation,0)}
            }.clipToBounds(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        occurrence.course.name,
                        style = QztcTypography.courseTitle.copy(textDecoration = if (occurrence.cancelled) TextDecoration.LineThrough else null),
                        color = if (occurrence.cancelled) colors.faint else colors.ink,
                        modifier = Modifier.weight(1f),
                        softWrap = true,
                    )
                    if (badge != null) {
                        Spacer(Modifier.width(QztcSpacing.sm))
                        Text(
                            badge,
                            style = QztcTypography.compact,
                            color = when {
                                badge.contains("停课") -> colors.danger
                                badge.contains("调") -> colors.warn
                                else -> colors.accentDeep
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    when {
                                        badge.contains("停课") -> colors.dangerSoft
                                        badge.contains("调") -> colors.warnSoft
                                        else -> colors.accentSoft
                                    },
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                if (occurrence.teacher.isNotBlank()) {
                    Text(occurrence.teacher, style = QztcTypography.meta, color = colors.mut)
                }
                Text(occurrence.room, style = QztcTypography.meta, color = colors.mut)
            }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun CourseDetailPage(
    occurrence: DemoOccurrence,
    periodFor: (Int) -> cn.qztc.timetable.core.domain.model.Period = DemoTimetableRepository::periodFor,
    presetColors: List<Int> = CoursePalette.indices.toList(),
    editScheduleState: DemoScheduleState? = null,
    reminderLabel: String = "跟随全局 · 提前 15 分钟",
    reminderState: CourseReminderUiState? = null,
    onSaveReminder: (String, Int?, String?) -> Unit = { _, _, _ -> },
    totalWeeks: Int = 20,
    onSaveEdit: (CourseEditDraft) -> Unit = {},
    onDelete: () -> Unit = {},
    onBack: () -> Unit,
    onCoverBottomBar: (Boolean) -> Unit = {},
) {
    val colors = LocalQztcColors.current
    val palette = occurrence.visualColor(colors.isDark)
    var editing by remember(occurrence.id) { mutableStateOf(false) }
    var routing by remember(occurrence.id) { mutableStateOf(false) }
    var editingReminder by remember(occurrence.id) { mutableStateOf(false) }
    if (routing) {
        BackHandler { routing = false }
        CampusRouteScreen(
            rawDestination = occurrence.room,
            onBack = { routing = false },
            onCoverBottomBar = onCoverBottomBar,
        )
        return
    }
    if (editing) {
        CourseEditPage(
            title = "编辑课程",
            editorKey = occurrence.id,
            initial = occurrence.toEditDraft(editScheduleState),
            conflictsFor = { findCourseConflicts(it, editScheduleState ?: DemoScheduleState(), occurrence.meetingId) },
            presetColors = presetColors,
            totalWeeks = totalWeeks,
            showDelete = true,
            onBack = { editing = false },
            onSave = { onSaveEdit(it) },
            onDelete = {
                onDelete()
                onBack()
            },
        )
        return
    }

    if (editingReminder && reminderState != null) {
        CourseReminderSheet(
            occurrence = occurrence,
            state = reminderState,
            onSave = { minutes, message ->
                onSaveReminder(occurrence.course.id, minutes, message)
                editingReminder = false
            },
            onDismiss = { editingReminder = false },
        )
    }

    BackHandler { onBack() }
    Column(Modifier.fillMaxSize()) {
        QztcPageTopBar(
            title = "课程详情",
            onBack = onBack,
            actions = {
                QztcTopIconButton(
                    icon = Icons.Outlined.Edit,
                    contentDescription = "编辑课程",
                    onClick = { editing = true },
                )
            },
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = QztcSpacing.xl, vertical = QztcSpacing.md), verticalArrangement = Arrangement.spacedBy(QztcSpacing.md)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(palette.bg(colors.isDark), colors.solid.copy(alpha = .94f))))
                .padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(occurrence.course.name, style = QztcTypography.pageTitle.copy(fontSize = 26.sp, lineHeight = 34.sp), color = colors.ink)
                Text("周${listOf("一", "二", "三", "四", "五", "六", "日")[occurrence.weekday - 1]}  " +
                    "${periodFor(occurrence.periodStart).startTime.format(timeFormatter)} – ${periodFor(occurrence.periodEnd).endTime.format(timeFormatter)}",
                    style = QztcTypography.body.copy(fontSize = 17.sp, lineHeight = 24.sp), color = colors.ink)
                Text(displayPeriodRangeLabel(occurrence.periodStart, occurrence.periodEnd), style = QztcTypography.meta, color = colors.mut)
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailMetaTag(Icons.Outlined.LocationOn, occurrence.room.ifBlank { "未填写教室" })
                    DetailMetaTag(Icons.Outlined.PersonOutline, occurrence.teacher.ifBlank { "未填写教师" })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailQuickAction(Icons.Outlined.Alarm, "提醒设置", reminderState?.summary(occurrence.course.id) ?: reminderLabel,
                    Modifier.weight(1f), enabled = reminderState != null) { editingReminder = true }
                DetailQuickAction(Icons.Outlined.NearMe, "校内导航", CampusPlaceResolver.buildingName(occurrence.room),
                    Modifier.weight(1f)) { routing = true }
                DetailQuickAction(Icons.Outlined.Edit, "编辑课程", "时间、周次、颜色",
                    Modifier.weight(1f)) { editing = true }
            }
            GlassSurface(role = GlassRole.CONTENT, modifier = Modifier.fillMaxWidth().heightIn(min = 216.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("课程信息", style = QztcTypography.section, color = colors.ink)
                    listOf("学分" to occurrence.course.credits, "课程性质" to occurrence.course.nature,
                        "选课属性" to occurrence.course.selectionAttribute, "考核方式" to occurrence.course.assessmentMethod)
                        .chunked(2).forEach { facts ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            facts.forEach { (label, value) ->
                                Column(Modifier.weight(1f).border(1.dp, colors.line, RoundedCornerShape(8.dp)).padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(label, style = QztcTypography.meta, color = colors.mut)
                                    Text(value.orEmpty().ifBlank { "未获取" }, style = QztcTypography.body, color = colors.ink)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailMetaTag(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val colors = LocalQztcColors.current
    Row(Modifier.clip(RoundedCornerShape(10.dp)).background(colors.solid.copy(alpha = .8f)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, contentDescription = null, tint = colors.mut, modifier = Modifier.size(16.dp))
        Text(text, style = QztcTypography.meta, color = colors.ink)
    }
}

@Composable
private fun DetailQuickAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String,
    modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = LocalQztcColors.current
    GlassSurface(role = GlassRole.CONTENT, modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable(enabled = enabled, onClick = onClick)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(colors.accentSoft), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = colors.accentDeep, modifier = Modifier.size(21.dp))
            }
            Text(title, style = QztcTypography.body, color = colors.ink)
            Text(subtitle, style = QztcTypography.compact, color = colors.mut,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CourseEditPage(
    title: String,
    editorKey: String,
    initial: CourseEditDraft,
    presetColors: List<Int>,
    totalWeeks: Int,
    showDelete: Boolean,
    onBack: () -> Unit,
    onSave: (CourseEditDraft) -> Unit,
    onDelete: () -> Unit = {},
    warning: String? = null,
    conflictsFor: (CourseEditDraft) -> List<MeetingConflictHit> = { emptyList() },
) {
    val colors = LocalQztcColors.current
    val paletteCount = presetColors.size
    val initialWeeks = remember(editorKey, totalWeeks) {
        initial.weeks.ifEmpty { (1..totalWeeks.coerceAtLeast(1)).toList() }.toSet()
    }
    var name by remember(editorKey) { mutableStateOf(initial.name) }
    var teacher by remember(editorKey) { mutableStateOf(initial.teacher) }
    var location by remember(editorKey) { mutableStateOf(initial.location) }
    var weekday by remember(editorKey) { mutableStateOf(initial.weekday) }
    var weeklySlots by remember(editorKey) { mutableStateOf(
        initial.weeklySlots.ifEmpty { listOf(WeeklySlotDraft(initial.weekday, initial.periodStart, initial.periodEnd, initialWeeks.sorted())) }
    ) }
    var selectedWeeks by remember(editorKey, totalWeeks) { mutableStateOf(
        weeklySlots.filter { it.weekday == weekday }.flatMap { it.weeks.ifEmpty { initialWeeks.sorted() } }.toSet()
    ) }
    val initialColorValue = remember(editorKey, paletteCount) {
        initial.color
    }
    var colorValue by remember(editorKey, paletteCount) {
        mutableStateOf(initialColorValue)
    }
    var rangeAnchor by remember(editorKey) { mutableStateOf<Int?>(null) }
    var deleteDialogOpen by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    fun currentDraft(): CourseEditDraft {
        val first = weeklySlots.firstOrNull() ?: WeeklySlotDraft(weekday, 1, 1)
        return CourseEditDraft(name.trim(), teacher.trim(), location.name.trim(),
            first.weekday, first.periodStart, first.periodEnd,
            weeklySlots.flatMap { it.weeks }.distinct().sorted(), colorValue, location,
            weeklySlots = weeklySlots)
    }
    fun changeWeeks(next: Set<Int>) {
        selectedWeeks = next
        weeklySlots = weeklySlots.map { if (it.weekday == weekday) it.copy(weeks = next.sorted()) else it }
    }
    val canSave = name.isNotBlank() && weeklySlots.isNotEmpty() && weeklySlots.all { it.weeks.isNotEmpty() }
    val conflicts = conflictsFor(currentDraft())
    var confirmCoverage by remember(editorKey) { mutableStateOf(false) }
    fun save() {
        if (!canSave) return
        if (conflicts.isNotEmpty()) confirmCoverage = true else onSave(currentDraft())
    }
    if (confirmCoverage) {
        AlertDialog(
            onDismissRequest = { confirmCoverage = false },
            title = { Text("这些课程时间重叠") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    conflicts.forEach { Text("${it.courseName}\n${it.summary()}") }
                    Text("覆盖只隐藏相交周次的冲突课次，其他星期和周次保留。")
                }
            },
            confirmButton = { TextButton(onClick = { confirmCoverage = false; onSave(currentDraft().copy(overwriteConflicts = true)) }) { Text("覆盖并保存") } },
            dismissButton = { TextButton(onClick = { confirmCoverage = false }) { Text("重新选择") } },
        )
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
                EditCourseField("课程名", name, { name = it })
                if (!warning.isNullOrBlank()) {
                    Text(warning, style = QztcTypography.meta, color = colors.danger)
                }
                ScheduleLocationField(location) { location = it }
                EditCourseField("教师", teacher, { teacher = it })
                FormLabel("上课时间")
                Text(
                    weeklySlots.filter { it.weekday == weekday }.joinToString("、") {
                        displayPeriodRangeLabel(it.periodStart, it.periodEnd)
                    }.ifEmpty { "本日尚未选择节次" },
                    style = QztcTypography.body, color = colors.accentDeep,
                )
                val selectedPeriods = weeklySlots.filter { it.weekday == weekday }
                    .flatMap { it.periodStart..it.periodEnd }.toSet()
                WeekPeriodPicker(weekday, -1, -1,
                    daysWithSlots = weeklySlots.map { it.weekday }.toSet(),
                    selectedSlotIndices = selectedPeriods.map(::displaySlotIndexForActual).toSet(),
                    onDay = { day ->
                        weekday = day
                        rangeAnchor = null
                        val daySlots = weeklySlots.filter { it.weekday == day }
                        selectedWeeks = if (daySlots.isEmpty()) initialWeeks
                            else daySlots.flatMap { it.weeks }.toSet()
                    },
                    onSlot = { slotIndex ->
                        val period = timetableDisplayPeriodSlots[slotIndex].actualStart
                        val anchor = rangeAnchor
                        weeklySlots = if (period in selectedPeriods) {
                            rangeAnchor = null
                            removeCoursePeriod(weeklySlots, weekday, period)
                        } else {
                            val range = if (anchor == null) period..period else minOf(anchor, period)..maxOf(anchor, period)
                            rangeAnchor = if (anchor == null) period else null
                            addCoursePeriods(weeklySlots, weekday, range, selectedWeeks.sorted())
                        }
                    })
                Text("点选节次，再点另一节可连选；点击已选节次取消。实心星期为当前编辑，描边带点表示已安排。",
                    style = QztcTypography.meta, color = colors.mut)
                if (conflicts.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.warnSoft).padding(12.dp)) {
                        Text("时间冲突", style = QztcTypography.body, color = colors.warn)
                        conflicts.forEach { Text("${it.courseName} · ${it.summary()}", style = QztcTypography.meta, color = colors.ink) }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    FormLabel("周${listOf("一", "二", "三", "四", "五", "六", "日")[weekday - 1]}上课周数", modifier = Modifier.weight(1f))
                    WeekSelectionShortcuts(totalWeeks = totalWeeks, selectedWeeks = selectedWeeks, onChange = ::changeWeeks)
                }
                (1..totalWeeks.coerceAtLeast(1)).toList().chunked(6).forEach { rowWeeks ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowWeeks.forEach { weekNo ->
                            EditChoice(
                                text = weekNo.toString(),
                                selected = weekNo in selectedWeeks,
                                modifier = Modifier.weight(1f),
                            ) {
                                changeWeeks(if (weekNo in selectedWeeks) selectedWeeks - weekNo else selectedWeeks + weekNo)
                            }
                        }
                    }
                }
                FormLabel("颜色")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(coursePresetColor(colorValue)),
                    )
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
                                    .clickable {
                                        colorValue = presetColor
                                    },
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
                        Text("删除课程")
                    }
                }
            }
        }
    }

    if (deleteDialogOpen) {
        AlertDialog(
            onDismissRequest = { deleteDialogOpen = false },
            title = { Text("删除这门课程？") },
            text = { Text("删除后会从当前学期课表中移除全部相关课次。") },
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

@Composable
internal fun WeekPeriodPicker(
    weekday: Int,
    startSlotIndex: Int,
    endSlotIndex: Int,
    daysWithSlots: Set<Int> = emptySet(),
    selectedSlotIndices: Set<Int>? = null,
    onDay: (Int) -> Unit,
    onSlot: (Int) -> Unit,
) {
    val colors = LocalQztcColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { index, label ->
            val selected = weekday == index + 1
            val hasSlot = index + 1 in daysWithSlots
            Box(Modifier.weight(1f).height(38.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (selected) colors.accent else colors.solid)
                .border(1.dp, if (selected) colors.accent else if (hasSlot) colors.accentDeep else colors.line, RoundedCornerShape(6.dp))
                .testTag("course-day-${index + 1}")
                .clickable { onDay(index + 1) }, contentAlignment = Alignment.Center) {
                Text(if (hasSlot && !selected) "$label·" else label, style = QztcTypography.meta, color = if (selected) colors.onAccent else if (hasSlot) colors.accentDeep else colors.ink)
            }
        }
    }
    listOf("上午" to (0..3), "午间" to (4..5), "下午" to (6..9), "晚上" to (10..12)).forEach { (group, indices) ->
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(group, Modifier.width(38.dp), style = QztcTypography.meta, color = colors.mut)
            indices.forEach { index ->
                val selected = selectedSlotIndices?.contains(index) ?: (index in startSlotIndex..endSlotIndex)
                val number = when (index) { in 0..3 -> index + 1; in 4..5 -> index - 3; else -> index - 1 }
                Box(Modifier.weight(1f).heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) colors.accentSoft else colors.solid)
                    .border(1.dp, if (selected) colors.accent else colors.line, RoundedCornerShape(6.dp))
                    .testTag("course-period-${index + 1}")
                    .clickable { onSlot(index) }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text(number.toString(), style = QztcTypography.body,
                        color = if (selected) colors.accentDeep else colors.ink)
                }
            }
            repeat(4 - indices.count()) { Spacer(Modifier.weight(1f)) }
        }
    }
}

internal fun DemoOccurrence.toEditDraft(scheduleState: DemoScheduleState? = null): CourseEditDraft = CourseEditDraft(
    name = course.name,
    teacher = teacher,
    room = room,
    weekday = weekday,
    periodStart = periodStart,
    periodEnd = periodEnd,
    weeks = weeks,
    color = course.color ?: paletteIndex,
    location = location,
    weeklySlots = scheduleState?.importedSchedule?.state?.meetings.orEmpty().filter { it.courseId == course.id }.map {
        WeeklySlotDraft(it.weekday, it.periodStart, it.periodEnd, it.weekRule.weeks, it.id)
    },
)

@Composable
internal fun WeekSelectionShortcuts(
    totalWeeks: Int,
    selectedWeeks: Set<Int>,
    onChange: (Set<Int>) -> Unit,
) {
    val weeks = (1..totalWeeks.coerceAtLeast(1)).toList()
    val allWeeks = weeks.toSet()
    val allSelected = allWeeks.isNotEmpty() && selectedWeeks.containsAll(allWeeks)
    TextButton(
        onClick = { onChange(if (allSelected) emptySet() else allWeeks) },
    ) { Text(if (allSelected) "取消全选" else "全选") }
    TextButton(onClick = { onChange(weeks.filter { it % 2 == 1 }.toSet()) }) { Text("单周") }
    TextButton(onClick = { onChange(weeks.filter { it % 2 == 0 }.toSet()) }) { Text("双周") }
}

@Composable
internal fun FormLabel(text: String, modifier: Modifier = Modifier) {
    val colors = LocalQztcColors.current
    Text(
        text,
        style = QztcTypography.meta,
        color = colors.ink,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.solid.copy(alpha = .90f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Composable
internal fun PeriodChoicePanel(
    selectedIndex: Int,
    options: List<DisplayPeriodSlot>,
    indexOffset: Int = 0,
    onSelect: (Int) -> Unit,
) {
    val colors = LocalQztcColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.solid)
            .border(1.dp, colors.line, RoundedCornerShape(12.dp)),
    ) {
        options.forEachIndexed { localIndex, option ->
            val index = localIndex + indexOffset
            Text(
                option.label,
                style = QztcTypography.body,
                color = if (index == selectedIndex) colors.accentDeep else colors.ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (index == selectedIndex) colors.accentSoft else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
internal fun EditCourseField(label: String, value: String, onValueChange: (String) -> Unit) {
    val colors = LocalQztcColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FormLabel(label)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.solid.copy(alpha = .98f))
                .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 13.dp),
            singleLine = true,
            textStyle = QztcTypography.body.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )
    }
}

@Composable
internal fun ProtectedNameText(text: String, style: androidx.compose.ui.text.TextStyle) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .clip(shape)
            .background(Color.White.copy(alpha = .78f))
            .border(1.dp, Color.White.copy(alpha = .92f), shape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = style.copy(color = Color(0xFF20342F)))
    }
}

internal fun coursePresetColor(value: Int): Color =
    if (value in CoursePalette.indices) CoursePalette[value].bar else Color(value)

@Composable
internal fun EditChoice(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalQztcColors.current
    Box(
        modifier
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.accent else colors.solid.copy(alpha = .78f))
            .border(if (selected) 0.dp else .5.dp, colors.line, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = QztcTypography.compact, color = if (selected) colors.onAccent else colors.ink)
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    val colors = LocalQztcColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = QztcTypography.meta, color = colors.mut)
        Text(value, style = QztcTypography.body, color = colors.ink,modifier=Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.End)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CourseReminderSheet(
    occurrence: DemoOccurrence,
    state: CourseReminderUiState,
    onSave: (Int?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by rememberSaveable(occurrence.course.id) { mutableStateOf(state.overrides[occurrence.course.id] ?: -1) }
    var message by rememberSaveable(occurrence.course.id) { mutableStateOf(state.messages[occurrence.course.id].orEmpty()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(bottom = QztcSpacing.xxl)) {
            Text("提前时间", style = QztcTypography.section,
                modifier = Modifier.padding(horizontal = QztcSpacing.xl, vertical = QztcSpacing.sm))
            if (!state.enabled) Text("全局提醒已关闭，保存后请在我的页面开启", style = QztcTypography.meta,
                modifier = Modifier.padding(horizontal = QztcSpacing.xl))
            Box(Modifier.padding(horizontal = QztcSpacing.xl)) {
                ReminderMinuteChoices(selected, { selected = it }, allowDefault = true)
            }
            androidx.compose.material3.OutlinedTextField(
                value = message,
                onValueChange = { message = it.take(240) },
                label = { Text("额外提醒内容（可选）") },
                placeholder = { Text("例如：带实验报告、提前交作业") },
                supportingText = { Text("${message.length}/240 · 弹窗和通知栏均显示；保留上课时间与地点") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = QztcSpacing.xl, vertical = QztcSpacing.sm),
                minLines = 2,
                maxLines = 4,
            )
            Button(
                onClick = {
                    onSave(selected.takeIf { it >= 0 }, message.trim().takeIf { it.isNotEmpty() })
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = QztcSpacing.xl),
            ) { Text("保存提醒设置") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseNavigationSheet(
    occurrence: DemoOccurrence,
    onInAppMap: () -> Unit,
    onExternalNavigation: () -> Unit,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val normalizedRoom = CampusPlaceResolver.normalize(occurrence.room)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(bottom = QztcSpacing.xxl)) {
            Text("前往 $normalizedRoom", style = QztcTypography.pageTitle, modifier = Modifier.padding(horizontal = QztcSpacing.xl, vertical = QztcSpacing.md))
            ActionRow(Icons.Outlined.NearMe, "在 APP 内查看路线", "服务器高德路线", onInAppMap)
            ActionRow(Icons.Outlined.NearMe, "在地图软件中导航", "高德地图或浏览器", onExternalNavigation)
            ActionRow(Icons.Outlined.ContentCopy, "复制教室名称", occurrence.room) {
                clipboard.setText(AnnotatedString(occurrence.room))
                onDismiss()
            }
        }
    }
}

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, onClick: () -> Unit) {
    val colors = LocalQztcColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable(onClick = onClick).padding(horizontal = QztcSpacing.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent)
        Text(title, style = QztcTypography.body, color = colors.ink, modifier = Modifier.padding(start = QztcSpacing.md).weight(1f))
        Text(value, style = QztcTypography.meta, color = colors.mut)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateChoiceSheet(
    week: Int,
    selectedDay: Int,
    appliedChanges: Set<String>,
    semesterLabel: String,
    dateFor: (Int, Int) -> java.time.LocalDate,
    markFor: (Int) -> String? = { null },
    occurrencesFor: (Int, Set<String>) -> List<DemoOccurrence>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val names = listOf("一", "二", "三", "四", "五", "六", "日")
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(bottom = QztcSpacing.xxl)) {
            Text("选择日期 · 第${week}周 · $semesterLabel", style = QztcTypography.pageTitle, modifier = Modifier.padding(horizontal = QztcSpacing.xxl, vertical = QztcSpacing.sm))
            (1..7).forEach { day ->
                val date = dateFor(week, day)
                val count = occurrencesFor(week, appliedChanges).count { it.weekday == day && !it.ghost && !it.cancelled }
                val mark = markFor(day)
                val value = buildString {
                    if (count == 0) append("无课") else append("$count 节课")
                    if (!mark.isNullOrBlank()) append(" · ${mark.substringBefore(" · ")}")
                }
                ChoiceRow("周${names[day - 1]} · ${date.monthValue}月${date.dayOfMonth}日", value, selectedDay == day) { onSelect(day) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScenarioSheet(selected: TodayScenario, onSelect: (TodayScenario) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(bottom = QztcSpacing.xxl)) {
            Text("调试时间场景", style = QztcTypography.pageTitle, modifier = Modifier.padding(horizontal = QztcSpacing.xxl, vertical = QztcSpacing.sm))
            TodayScenario.entries.forEach { scenario -> ChoiceRow(scenario.label, "", selected == scenario) { onSelect(scenario) } }
        }
    }
}

@Composable
private fun ChoiceRow(title: String, value: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable(onClick = onClick).padding(horizontal = QztcSpacing.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(title, style = QztcTypography.body, modifier = Modifier.padding(start = QztcSpacing.md).weight(1f))
        if (value.isNotEmpty()) Text(value, style = QztcTypography.meta)
    }
}

private fun DemoOccurrence.statusAt(
    now: LocalTime,
    periodFor: (Int) -> cn.qztc.timetable.core.domain.model.Period,
): OccurrenceStatus {
    if (cancelled) return OccurrenceStatus.CANCELLED
    val start = periodFor(periodStart).startTime
    val end = periodFor(periodEnd).endTime
    return when {
        !now.isBefore(end) -> OccurrenceStatus.ENDED
        !now.isBefore(start) -> OccurrenceStatus.CURRENT
        else -> OccurrenceStatus.UPCOMING
    }
}

private fun LocalTime.toMinutes(): Int = hour * 60 + minute
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
