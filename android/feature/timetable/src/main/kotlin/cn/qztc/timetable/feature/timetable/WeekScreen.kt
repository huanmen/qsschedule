package cn.qztc.timetable.feature.timetable

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.qztc.timetable.core.ui.glass.GlassRole
import cn.qztc.timetable.core.ui.glass.GlassSurface
import cn.qztc.timetable.core.ui.glass.LocalGlassSettings
import cn.qztc.timetable.core.ui.motion.MotionSpec
import cn.qztc.timetable.core.ui.motion.pressable
import cn.qztc.timetable.core.ui.theme.CoursePalette
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcCorners
import cn.qztc.timetable.core.ui.theme.QztcSpacing
import cn.qztc.timetable.core.ui.theme.QztcTopIconButton
import cn.qztc.timetable.core.ui.theme.QztcTypography
import cn.qztc.timetable.core.ui.theme.QztcDatePicker
import cn.qztc.timetable.core.ui.theme.FontSizeMode
import cn.qztc.timetable.core.domain.model.ActivityKind
import cn.qztc.timetable.core.domain.model.ScheduleActivity
import cn.qztc.timetable.core.domain.model.Semester
import cn.qztc.timetable.core.domain.model.WeekendDisplayMode
import cn.qztc.timetable.core.domain.calendar.AcademicDay
import cn.qztc.timetable.core.domain.calendar.AcademicDayKind
import cn.qztc.timetable.core.domain.calendar.AcademicCalendar
import cn.qztc.timetable.core.domain.calendar.SemesterCalendar
import cn.qztc.timetable.core.domain.schedule.ActivityWeekBlock
import cn.qztc.timetable.core.domain.schedule.ActivityWeekLayout
import kotlin.math.abs

private val timeColumnWidth = 44.dp
private val defaultLunchFoldedHeight = weekLunchFoldedHeightDp.dp
private val defaultLunchExpandedHeight = weekLunchExpandedHeightDp.dp
private val defaultEveningBreakHeight = weekEveningBreakHeightDp.dp
private val gridGap = weekGridGapDp.dp
private val defaultWeekHeaderHeight = 32.dp
private val weekHeaderGap = 4.dp

private data class WeekGridMetrics(
    val periodHeight: Dp,
    val lunchFoldedHeight: Dp,
    val lunchExpandedHeight: Dp,
    val eveningBreakHeight: Dp,
    val headerHeight: Dp,
) {
    fun lunchHeight(hasClass: Boolean, expanded: Boolean): Dp = when {
        hasClass -> periodHeight + periodHeight + gridGap
        expanded -> lunchExpandedHeight
        else -> lunchFoldedHeight
    }
}

private fun weekGridMetrics(periodHeight: Dp): WeekGridMetrics = WeekGridMetrics(
    periodHeight = periodHeight,
    lunchFoldedHeight = defaultLunchFoldedHeight,
    lunchExpandedHeight = maxOf(defaultLunchExpandedHeight, periodHeight),
    eveningBreakHeight = defaultEveningBreakHeight,
    headerHeight = defaultWeekHeaderHeight,
)

@Composable
fun WeekScreen(
    scheduleState: DemoScheduleState,
    onOpenDay: (Int, Int) -> Unit,
    reminderState: CourseReminderUiState = CourseReminderUiState(),
    onSaveReminder: (String, Int?, String?) -> Unit = { _, _, _ -> },
    coursePresetColors: List<Int> = CoursePalette.indices.toList(),
    onOpenSync: () -> Unit = {},
    onSaveCourseEdit: (DemoOccurrence, CourseEditDraft) -> Unit = { _, _ -> },
    onDeleteCourse: (DemoOccurrence) -> Unit = {},
    onAddCourse: (CourseEditDraft) -> Unit = {},
    onSaveActivity: (ActivityEditDraft, removeMeetingIds: List<String>) -> Unit = { _, _ -> },
    onDeleteActivity: (String, Boolean) -> Unit = { _, _ -> },
    onSaveAcademicDays: (List<AcademicDay>) -> Unit = {},
    resetToCurrentWeekRequest: Int = 0,
    weekendDisplayMode: WeekendDisplayMode = WeekendDisplayMode.HIDE_EMPTY,
    fontSizeMode: FontSizeMode = FontSizeMode.DEFAULT,
    fontScale: Float = 1f,
    onVisualTransition: () -> Unit = {},
    onCoverBottomBar: (Boolean) -> Unit = {},
    initialWeek: Int? = null,
    onWeekChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val currentWeek = scheduleState.currentWeek
    var week by rememberSaveable(scheduleState.importedSchedule?.semester?.id) { mutableStateOf(initialWeek ?: currentWeek) }
    var weekSheetOpen by remember { mutableStateOf(false) }
    var previewPressed by remember { mutableStateOf(false) }
    var holidayView by rememberSaveable(scheduleState.importedSchedule?.semester?.id) { mutableStateOf(false) }
    var horizontalDrag by remember { mutableFloatStateOf(0f) }
    var detailOccurrence by remember { mutableStateOf<DemoOccurrence?>(null) }
    var addingCourse by remember { mutableStateOf(false) }
    var detailActivity by remember { mutableStateOf<ScheduleActivity?>(null) }
    var editingActivity by remember { mutableStateOf<ScheduleActivity?>(null) }
    var pendingActivitySave by remember { mutableStateOf<PendingActivitySave?>(null) }
    var pendingCourseSave by remember { mutableStateOf<PendingCourseSave?>(null) }
    var addSheetOpen by remember { mutableStateOf(false) }
    var editingAcademicDay by remember { mutableStateOf<AcademicDay?>(null) }
    val totalWeeks = scheduleState.importedSchedule?.semester?.totalWeeks ?: 20
    val reduceMotion = LocalGlassSettings.current.reduceMotion

    LaunchedEffect(resetToCurrentWeekRequest) {
        if (resetToCurrentWeekRequest > 0 && week != currentWeek) {
            week = currentWeek
        }
    }

    LaunchedEffect(scheduleState.importedSchedule?.semester?.id, totalWeeks) {
        week = week.coerceIn(1, totalWeeks.coerceAtLeast(1))
    }
    LaunchedEffect(week) { onWeekChanged(week) }

    fun changeWeek(next: Int) {
        val target = next.coerceIn(1, totalWeeks.coerceAtLeast(1))
        if (target != week) {
            week = target
            onWeekChanged(target)
        }
    }

    fun closeActivityPages() {
        editingActivity = null
        detailActivity = null
    }

    fun trySaveActivity(draft: ActivityEditDraft, afterSave: () -> Unit) {
        val hits = findActivityConflicts(draft, scheduleState)
        onSaveActivity(draft, hits.map { it.meeting.id })
        afterSave()
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
                reminderState = reminderState,
                onSaveReminder = onSaveReminder,
                presetColors = coursePresetColors,
                totalWeeks = totalWeeks,
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
                    totalWeeks = totalWeeks,
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

    if (addingCourse) {
        DisposableEffect(Unit) {
            onCoverBottomBar(true)
            onDispose { onCoverBottomBar(false) }
        }
        Box(Modifier.fillMaxSize()) {
            CourseEditPage(
                title = "添加课程",
                conflictsFor = { findCourseConflicts(it, scheduleState) },
                editorKey = "add-course-$week",
                initial = CourseEditDraft(
                    name = "",
                    teacher = "",
                    room = "",
                    weekday = if (week == currentWeek) scheduleState.todayWeekday else 1,
                    periodStart = 1,
                    periodEnd = 2,
                    weeks = (1..totalWeeks.coerceAtLeast(1)).toList(),
                    color = coursePresetColors.firstOrNull() ?: 0,
                ),
                presetColors = coursePresetColors,
                totalWeeks = totalWeeks,
                showDelete = false,
                onBack = { addingCourse = false },
                onSave = { draft ->
                    val hits = findCourseConflicts(draft, scheduleState)
                    if (hits.isEmpty() || draft.overwriteConflicts) {
                        onAddCourse(draft)
                        addingCourse = false
                    } else {
                        pendingCourseSave = PendingCourseSave(draft, null, null) {
                            onAddCourse(draft)
                            addingCourse = false
                        }
                    }
                },
            )
            pendingCourseSave?.let { pending ->
                CourseConflictResolveScreen(
                    session = CourseConflictSession(pending.draft, pending.ignoreMeetingId, pending.originOccurrence),
                    scheduleState = scheduleState,
                    presetColors = coursePresetColors,
                    totalWeeks = totalWeeks,
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

    editingActivity?.let { activity ->
        val isNew = activity.id.isBlank()
        DisposableEffect(Unit) {
            onCoverBottomBar(true)
            onDispose { onCoverBottomBar(false) }
        }
        ActivityEditPage(
            title = if (isNew) "添加活动" else "编辑活动",
            editorKey = activity.id.ifBlank { "add-activity-$week" },
            initial = activity.toEditDraft(
                fallbackWeekday = if (week == currentWeek) scheduleState.todayWeekday else 1,
                fallbackDate = scheduleState.dateFor(week, 1),
                totalWeeks = totalWeeks,
            ),
            presetColors = coursePresetColors,
            totalWeeks = totalWeeks,
            showDelete = !isNew,
            onBack = { editingActivity = null },
            onSave = { draft -> trySaveActivity(draft, ::closeActivityPages) },
            onDelete = {
                                onDeleteActivity(activity.id, it)
                closeActivityPages()
            },
        )
        ActivitySaveConflictHost(
            pending = pendingActivitySave,
            onConfirm = { pending, removeCourses ->
                onSaveActivity(
                    pending.draft,
                    if (removeCourses) pending.hits.map { it.meeting.id } else emptyList(),
                )
                pending.afterSave()
                pendingActivitySave = null
            },
            onDismiss = { pendingActivitySave = null },
        )
        return
    }

    detailActivity?.let { activity ->
        DisposableEffect(Unit) {
            onCoverBottomBar(true)
            onDispose { onCoverBottomBar(false) }
        }
        ActivityDetailPage(
            activity = activity,
            presetColors = coursePresetColors,
            totalWeeks = totalWeeks,
            fallbackWeekday = if (week == currentWeek) scheduleState.todayWeekday else 1,
            fallbackDate = scheduleState.dateFor(week, 1),
            onBack = { detailActivity = null },
            onSave = { draft -> trySaveActivity(draft, ::closeActivityPages) },
            onDelete = {
                onDeleteActivity(activity.id, it)
                closeActivityPages()
            },
        )
        ActivitySaveConflictHost(
            pending = pendingActivitySave,
            onConfirm = { pending, removeCourses ->
                onSaveActivity(
                    pending.draft,
                    if (removeCourses) pending.hits.map { it.meeting.id } else emptyList(),
                )
                pending.afterSave()
                pendingActivitySave = null
            },
            onDismiss = { pendingActivitySave = null },
        )
        return
    }

    val canPreview = scheduleState.pending.isNotEmpty() || scheduleState.hasScheduleChange(week)
    val previewing = canPreview && previewPressed
    val previewChanges = if (previewing) scheduleState.applied.toSet() + scheduleState.pending else scheduleState.applied.toSet()
    val includeMovedGhost = previewing && "ch1" in scheduleState.pending
    val weekDates = remember(week, scheduleState.importedSchedule) {
        (1..7).map { day -> scheduleState.dateFor(week, day) }
    }
    val monthTitle = remember(weekDates) { weekMonthTitle(weekDates) }
    val onCourseClick = remember<(DemoOccurrence) -> Unit> { { detailOccurrence = it } }
    val onActivityClick = remember<(ActivityWeekBlock) -> Unit> {
        { block ->
            detailActivity = scheduleState.activities().firstOrNull { it.id == block.activityId }
        }
    }
    Column(modifier.fillMaxSize()) {
        WeekTopBar(
            week = week,
            currentWeek = currentWeek,
            monthTitle = monthTitle,
            onPrevious = { changeWeek(week - 1) },
            onNext = { changeWeek(week + 1) },
            onTitleClick = { weekSheetOpen = true },
            onCurrentWeek = { changeWeek(currentWeek) },
            onEditClick = { addSheetOpen = true },
        )
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(week) {
                    detectHorizontalDragGestures(
                        onDragStart = { horizontalDrag = 0f },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            horizontalDrag += amount
                        },
                        onDragEnd = {
                            if (abs(horizontalDrag) >= 60.dp.toPx()) {
                                changeWeek(week + if (horizontalDrag < 0) 1 else -1)
                            }
                            horizontalDrag = 0f
                        },
                        onDragCancel = { horizontalDrag = 0f },
                    )
                },
        ) {
            AnimatedContent(
                targetState = week,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    if (reduceMotion) {
                        fadeIn(tween(120)) togetherWith fadeOut(tween(80))
                    } else {
                        val dir = if (targetState > initialState) 1 else -1
                        slideInHorizontally(
                            animationSpec = tween(MotionSpec.CONTENT_SWAP_MS),
                            initialOffsetX = { fullWidth -> dir * fullWidth },
                        ) togetherWith slideOutHorizontally(
                            animationSpec = tween(MotionSpec.CONTENT_SWAP_MS),
                            targetOffsetX = { fullWidth -> -dir * fullWidth },
                        )
                    }.using(SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> snap() }))
                },
                label = "week-switch",
            ) { displayedWeek ->
                val weekActivities = remember(displayedWeek, scheduleState.importedSchedule) {
                    scheduleState.activities()
                }
                val holidayAvailable = remember(displayedWeek, scheduleState.importedSchedule) {
                    hasWeekActivityView(scheduleState.importedSchedule, displayedWeek)
                }
                val showHolidayView = holidayView && !previewing
                val previewOriginalSchedule = previewing && scheduleState.hasScheduleChange(displayedWeek)
                val occurrences = remember(displayedWeek, previewChanges, includeMovedGhost, showHolidayView, previewOriginalSchedule, scheduleState.importedSchedule) {
                    scheduleState.occurrences(
                        week = displayedWeek,
                        appliedChanges = previewChanges,
                        includeMovedGhost = includeMovedGhost,
                        // The course view always shows the original weekly plan.
                        // Holiday hiding and makeup remapping belong exclusively
                        // to the activity/calendar view.
                        respectAcademicCalendar = showHolidayView,
                        includeHolidayCourses = false,
                        includeScheduleChanges = !previewOriginalSchedule,
                    )
                }
                val holidayBlocks = remember(displayedWeek, scheduleState.importedSchedule) {
                    holidayBlocksForWeek(displayedWeek, scheduleState)
                }
                val normalHasLunchClass = occurrences.any { it.periodStart in 5..6 && !it.ghost } ||
                    weekActivities.any { ActivityWeekLayout.occupiesLunch(it, displayedWeek) }
                val hasLunchClass = normalHasLunchClass
                val weekendHasClass = occurrences.any { !it.ghost && it.weekday >= 6 } ||
                    weekActivities.any { activity ->
                        (6..7).any { day ->
                            activity.appliesOn(displayedWeek, day, scheduleState.dateFor(displayedWeek, day))
                        }
                    } ||
                    (6..7).any { scheduleState.academicDayOn(displayedWeek, it)?.kind == AcademicDayKind.MAKEUP }
                val compactWeekend = !weekendHasClass && weekendDisplayMode == WeekendDisplayMode.FOLD_EMPTY
                val hideWeekend = !weekendHasClass && weekendDisplayMode == WeekendDisplayMode.HIDE_EMPTY
                val visibleDays = if (hideWeekend) (1..5).toList() else (1..7).toList()
                val activityBlocks = remember(displayedWeek, visibleDays, hasLunchClass, showHolidayView, scheduleState.importedSchedule) {
                    if (showHolidayView) {
                        val holidays = holidayBlocksForWeek(displayedWeek, scheduleState, visibleDays)
                        val activities = scheduleState.activityBlocks(displayedWeek, visibleDays, includeLunchPeriods = hasLunchClass)
                        holidays + activities.flatMap { block ->
                            val days = (block.startWeekday..block.endWeekday).filter { day ->
                                scheduleState.academicDayOn(displayedWeek, day)?.kind != AcademicDayKind.HOLIDAY
                            }
                            val spans = mutableListOf<IntRange>()
                            for (day in days) {
                                val previous = spans.lastOrNull()
                                if (previous != null && previous.last + 1 == day) spans[spans.lastIndex] = previous.first..day
                                else spans += day..day
                            }
                            spans.map { block.copy(startWeekday=it.first,endWeekday=it.last) }
                        }
                    } else emptyList()
                }
                val replacedOccurrences = replaceWeekCellContents(occurrences)
                val shownOccurrences = if (!showHolidayView) replacedOccurrences else replacedOccurrences.flatMap { occurrence ->
                    val keep = (occurrence.periodStart..occurrence.periodEnd).filter { period ->
                        activityBlocks.none { occurrence.weekday in it.startWeekday..it.endWeekday && period in it.periodStart..it.periodEnd }
                    }
                    val runs = mutableListOf<IntRange>()
                    for (period in keep) {
                        val last = runs.lastOrNull()
                        if (last != null && last.last + 1 == period) runs[runs.lastIndex] = last.first..period
                        else runs += period..period
                    }
                    runs.map { occurrence.copy(periodStart=it.first,periodEnd=it.last,layoutPeriodStart=it.first,layoutPeriodEnd=it.last) }
                }
                val displayedCanPreview = scheduleState.pending.isNotEmpty() || scheduleState.hasScheduleChange(displayedWeek)
                val onOpenDisplayedDay = remember(displayedWeek, onOpenDay) {
                    { day: Int -> onOpenDay(displayedWeek, day) }
                }
                Column(Modifier.fillMaxSize()) {
                    WeekHeader(
                        week = displayedWeek,
                        currentWeek = currentWeek,
                        todayDay = scheduleState.todayWeekday,
                        isCurrentSemesterDate = scheduleState.isCurrentDateInImportedSemester,
                        dateFor = scheduleState::dateFor,
                        academicDayFor = { day -> scheduleState.academicDayOn(displayedWeek, day) },
                        semester = scheduleState.importedSchedule?.semester,
                        compactWeekend = compactWeekend,
                        visibleDays = visibleDays,
                        headerHeight = defaultWeekHeaderHeight,
                        previewAvailable = displayedCanPreview,
                        holidayView = showHolidayView,
                        holidayAvailable = holidayAvailable,
                        onToggleHolidayView = { holidayView = !holidayView },
                        onPreviewPress = {
                            previewPressed = it
                        },
                        onOpenDay = onOpenDisplayedDay,
                    )
                    BoxWithConstraints(
                        Modifier
                            .weight(1f)
                            .padding(start = 14.dp, end = 14.dp, top = weekHeaderGap, bottom = 2.dp),
                    ) {
                        val gridWidth = maxWidth
                        val periodHeight = weekFittedPeriodHeightDp(
                            availableHeightDp = maxHeight.value,
                            hasLunchClass = hasLunchClass,
                        ).dp
                        val defaultCourseFontSize = QztcTypography.compact.fontSize
                        val courseFontSize = when (fontSizeMode) {
                            FontSizeMode.AUTO_FILL -> autoWeekCourseFontSize(shownOccurrences, visibleDays, compactWeekend, gridWidth, periodHeight)
                            FontSizeMode.CUSTOM -> (defaultCourseFontSize.value * fontScale.coerceIn(.75f, 1.6f)).sp
                            FontSizeMode.DEFAULT -> defaultCourseFontSize
                        }
                        val measuredHeight = measuredWeekPeriodHeight(
                            shownOccurrences, activityBlocks, visibleDays, compactWeekend, gridWidth, periodHeight, courseFontSize,
                        )
                        // Fill the available viewport when all text fits. Only
                        // grow beyond it when the densest cell really needs a
                        // scrollable grid, avoiding both clipped labels and a
                        // large empty area below eleven periods.
                        val fittedHeight = maxOf(periodHeight, measuredHeight)
                        val metrics = weekGridMetrics(fittedHeight)
                        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        WeekGrid(
                            occurrences = shownOccurrences,
                            activityBlocks = activityBlocks,
                            lunchExpanded = hasLunchClass,
                            hasLunchClass = hasLunchClass,
                            compactWeekend = compactWeekend,
                            visibleDays = visibleDays,
                            onCourseClick = onCourseClick,
                            onActivityClick = onActivityClick,
                            gridWidth = gridWidth,
                            metrics = metrics,
                            periodFor = scheduleState::periodFor,
                            courseFontSize = courseFontSize,
                            fadedDays = if (showHolidayView) emptySet() else visibleDays.filter { scheduleState.academicDayOn(displayedWeek,it)?.kind == AcademicDayKind.HOLIDAY }.toSet(),
                        )
                        }
                    }
                }
            }
        }
    }

    if (weekSheetOpen) {
        WeekChoiceSheet(
            selected = week,
            totalWeeks = totalWeeks,
            currentWeek = currentWeek,
            onSelect = { changeWeek(it); weekSheetOpen = false },
            onDismiss = { weekSheetOpen = false },
        )
    }
    if (addSheetOpen) {
        WeekAddSheet(
            onAddCourse = {
                addSheetOpen = false
                addingCourse = true
            },
            onAddActivity = {
                addSheetOpen = false
                editingActivity = ScheduleActivity(
                    id = "",
                    semesterId = scheduleState.importedSchedule?.semester?.id.orEmpty(),
                    name = "",
                    kind = ActivityKind.PERIOD,
                    weekday = if (week == currentWeek) scheduleState.todayWeekday else 1,
                    periodStart = 1,
                    periodEnd = 2,
                    weeks = listOf(week.coerceIn(1, totalWeeks.coerceAtLeast(1))),
                    startDate = scheduleState.dateFor(week, 1),
                    endDate = scheduleState.dateFor(week, 5),
                    color = coursePresetColors.firstOrNull() ?: 0,
                )
            },
            onDismiss = { addSheetOpen = false },
        )
    }
    editingAcademicDay?.let { original ->
        val schedule = scheduleState.importedSchedule
        if (schedule != null) {
            val persisted = schedule.academicDays.any { it.id == original.id }
            AcademicDayEditSheet(
                day = original,
                semester = schedule.semester,
                onSave = { updated ->
                    onSaveAcademicDays(
                        schedule.academicDays
                            .filterNot { it.id == original.id || it.date == updated.date }
                            .plus(updated),
                    )
                    editingAcademicDay = null
                },
                onDelete = if (persisted) ({
                    onSaveAcademicDays(schedule.academicDays.filterNot { it.id == original.id })
                    editingAcademicDay = null
                }) else null,
                onDismiss = { editingAcademicDay = null },
            )
        }
    }
}

@Composable
private fun WeekTopBar(
    week: Int,
    currentWeek: Int,
    monthTitle: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTitleClick: () -> Unit,
    onCurrentWeek: () -> Unit,
    onEditClick: () -> Unit,
) {
    val colors = LocalQztcColors.current
    GlassSurface(
        role = GlassRole.FUNCTIONAL,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        pressGlint = true,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 2.dp),
        ) {
            Row(
                Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QztcTopIconButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "上一周",
                    onClick = onPrevious,
                )
                if (monthTitle.isNotBlank()) {
                    Text(
                        monthTitle,
                        style = QztcTypography.compact,
                        color = colors.mut,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .height(40.dp)
                    .clip(RoundedCornerShape(QztcCorners.functional))
                    .clickable(onClick = onTitleClick)
                    .pressable()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("第${week}周", style = QztcTypography.courseTitle, color = colors.ink)
                Spacer(Modifier.size(8.dp))
                if (week == currentWeek) {
                    Text("本周", style = QztcTypography.courseTitle, color = colors.accentDeep)
                } else {
                    Text(
                        "回本周",
                        style = QztcTypography.compact,
                        color = colors.accentDeep,
                        modifier = Modifier.clip(CircleShape).background(colors.accentSoft).clickable(onClick = onCurrentWeek).padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }
            Row(
                Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QztcTopIconButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = "下一周",
                    onClick = onNext,
                )
                QztcTopIconButton(
                    icon = Icons.Outlined.Edit,
                    contentDescription = "编辑课表",
                    onClick = onEditClick,
                )
            }
        }
    }
}

@Composable
private fun ActivitySaveConflictHost(
    pending: PendingActivitySave?,
    onConfirm: (PendingActivitySave, removeCourses: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    pending ?: return
    ActivityConflictDialog(
        hits = pending.hits,
        onRemoveCourses = { onConfirm(pending, true) },
        onKeepCourses = { onConfirm(pending, false) },
        onDismiss = onDismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekAddSheet(
    onAddCourse: () -> Unit,
    onAddActivity: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalQztcColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(bottom = QztcSpacing.xxl)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp)
                    .clickable(onClick = onAddCourse)
                    .padding(horizontal = QztcSpacing.xl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = null, tint = colors.accent)
                Column(Modifier.padding(start = QztcSpacing.md).weight(1f)) {
                    Text("添加课程", style = QztcTypography.body, color = colors.ink)
                    Text("自行填写课程名、时间和教室", style = QztcTypography.meta, color = colors.mut)
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp)
                    .clickable(onClick = onAddActivity)
                    .padding(horizontal = QztcSpacing.xl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Event, contentDescription = null, tint = colors.accent)
                Column(Modifier.padding(start = QztcSpacing.md).weight(1f)) {
                    Text("添加活动", style = QztcTypography.body, color = colors.ink)
                    Text("按课程时间合并格子，或按日期跨天合并", style = QztcTypography.meta, color = colors.mut)
                }
            }
        }
    }
}

@Composable
private fun WeekHeader(
    week: Int,
    currentWeek: Int,
    todayDay: Int,
    isCurrentSemesterDate: Boolean,
    dateFor: (Int, Int) -> java.time.LocalDate,
    academicDayFor: (Int) -> cn.qztc.timetable.core.domain.calendar.AcademicDay?,
    semester: cn.qztc.timetable.core.domain.model.Semester?,
    compactWeekend: Boolean,
    visibleDays: List<Int>,
    headerHeight: Dp,
    previewAvailable: Boolean,
    holidayView: Boolean,
    holidayAvailable: Boolean,
    onToggleHolidayView: () -> Unit,
    onPreviewPress: (Boolean) -> Unit,
    onOpenDay: (Int) -> Unit,
) {
    val colors = LocalQztcColors.current
    val names = listOf("一", "二", "三", "四", "五", "六", "日")
    Row(
        Modifier
            .fillMaxWidth()
            .height(headerHeight)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(gridGap),
    ) {
        run {
            GlassSurface(
                    role = GlassRole.CONTENT,
                    modifier = Modifier
                        .width(timeColumnWidth)
                        .height(headerHeight)
                        .pointerInput(previewAvailable, holidayAvailable) {
                            detectTapGestures(
                                onLongPress = { if (previewAvailable) onPreviewPress(true) },
                                onPress = {
                                    tryAwaitRelease()
                                    if (previewAvailable) onPreviewPress(false)
                                },
                                onTap = { onToggleHolidayView() },
                            )
                        },
                    shape = RoundedCornerShape(10.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (holidayView) Icons.Outlined.Book else Icons.Outlined.MenuBook,
                        contentDescription = (if (holidayView) "切换课程视图" else "切换活动视图") +
                            if (previewAvailable) "，长按查看变化预览" else "",
                        tint = if (holidayView || previewAvailable) colors.accent else colors.mut,
                        modifier = Modifier.size(14.dp).testTag(if (holidayView) "week-book-closed" else "week-book-open"),
                    )
                    if (previewAvailable) Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(6.dp).clip(CircleShape).background(colors.warn))
                }
            }
        }
        val groups = mutableListOf<MutableList<Int>>()
        visibleDays.forEach { day ->
            val previous = groups.lastOrNull()?.lastOrNull()
            val mark = academicDayFor(day)
            if (holidayView && mark?.kind == AcademicDayKind.HOLIDAY && previous == day-1 &&
                academicDayFor(previous)?.name == mark.name) groups.last().add(day)
            else groups.add(mutableListOf(day))
        }
        BoxWithConstraints(Modifier.weight(1f).height(headerHeight)) {
        val unit = (maxWidth - gridGap * (visibleDays.size - 1)) / visibleDays.sumOf { dayColumnWeight(it,compactWeekend).toDouble() }.toFloat()
        groups.forEach { group ->
            val day = group.first()
            val mark = academicDayFor(day)
            val holiday = holidayView && mark?.kind == AcademicDayKind.HOLIDAY
            val sourceDate = if (mark?.kind == AcademicDayKind.MAKEUP && semester != null) {
                val resolved = scheduleStateDate(semester, mark)
                resolved ?: dateFor(week,day)
            } else dateFor(week,day)
            val isToday = isCurrentSemesterDate && week == currentWeek && todayDay in group
            val color = if (mark?.kind == AcademicDayKind.MAKEUP) colors.danger else if(isToday) colors.onAccent else colors.ink
            val before = visibleDays.takeWhile { it != day }
            val x = unit * before.sumOf { dayColumnWeight(it,compactWeekend).toDouble() }.toFloat() + gridGap * before.size
            val width = unit * group.sumOf { dayColumnWeight(it,compactWeekend).toDouble() }.toFloat() + gridGap * (group.size-1)
            Box(Modifier.offset(x=x).width(width)
                .height(headerHeight).clip(RoundedCornerShape(10.dp))
                .background(if(isToday && mark?.kind != AcademicDayKind.MAKEUP) colors.accent else colors.solid.copy(alpha=.78f))
                .clickable { onOpenDay(day) }, contentAlignment=Alignment.Center) {
                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(if(holiday) mark!!.name.take(2) else "周${names[sourceDate.dayOfWeek.value-1]}",style=QztcTypography.compact,color=color)
                    val actualDate = dateFor(week, day)
                    val weekStartDate = dateFor(week, 1)
                    Text(if(holiday && group.size>1) "${scheduleDayNumber(actualDate)}–${scheduleDayNumber(dateFor(week,group.last()))}"
                        else scheduleDayLabel(actualDate, weekStartDate),style=QztcTypography.compact,color=color)
                }
            }
        }
        }
    }
}

internal fun hasWeekActivityView(schedule: ImportedSchedule?, week: Int): Boolean =
    schedule?.let {
        val dates = (1..7).associateWith { day -> SemesterCalendar.dateOf(it.semester, week, day) }
        val first = dates.getValue(1)
        val last = dates.getValue(7)
        it.academicDays.any { day -> day.date in first..last } ||
            it.activities.any { activity ->
                dates.any { (weekday, date) -> activity.appliesOn(week, weekday, date) }
            }
    } == true

private fun holidayBlocksForWeek(
    week: Int,
    scheduleState: DemoScheduleState,
    visibleDays: List<Int> = (1..7).toList(),
): List<ActivityWeekBlock> {
    val days = visibleDays.mapNotNull { day ->
        scheduleState.academicDayOn(week, day)
            ?.takeIf { it.kind == AcademicDayKind.HOLIDAY }
            ?.let { day to it.name.ifBlank { "放假" } }
    }
    if (days.isEmpty()) return emptyList()
    val result = mutableListOf<ActivityWeekBlock>()
    var from = days.first().first
    var previous = from
    var name = days.first().second
    fun append() {
        result += ActivityWeekBlock(
            activityId = "holiday:$week:$from",
            name = name,
            note = AcademicCalendar.holidayGreeting(name),
            timeText = "全天",
            color = 0,
            startWeekday = from,
            endWeekday = previous,
            periodStart = 1,
            periodEnd = 13,
        )
    }
    days.drop(1).forEach { (day, dayName) ->
        if (day == previous + 1 && dayName == name) {
            previous = day
        } else {
            append()
            from = day
            previous = day
            name = dayName
        }
    }
    append()
    return result
}

@Composable
private fun measuredWeekPeriodHeight(
    occurrences: List<DemoOccurrence>, blocks: List<ActivityWeekBlock>, visibleDays: List<Int>,
    compactWeekend: Boolean, width: Dp, minimum: Dp, courseFontSize: TextUnit,
): Dp {
    val measurer = rememberTextMeasurer(cacheSize = 128)
    val density = LocalDensity.current
    val style = QztcTypography.compact.copy(fontSize = courseFontSize, lineHeight = courseFontSize * 1.24f)
    return remember(occurrences,blocks,visibleDays,compactWeekend,width,minimum,density,style) {
        val weight = visibleDays.sumOf { dayColumnWeight(it,compactWeekend).toDouble() }.toFloat()
        val unit = (width-timeColumnWidth-gridGap*visibleDays.size)/weight
        fun height(text: String, available: Dp): Dp {
            if(text.isBlank())return 0.dp
            val pixels = with(density) { kotlin.math.floor(available.toPx()).toInt().coerceAtLeast(1) }
            return with(density) { measurer.measure(text,style,constraints=Constraints(maxWidth=pixels)).size.height.toDp() }
        }
        var required = minimum.coerceAtMost(44.dp)
        fun include(needed: Dp, periods: Int) {
            val count=periods.coerceAtLeast(1)
            val perPeriod=(needed-gridGap*(count-1))/count
            val rounded=with(density) { kotlin.math.ceil(perPeriod.toPx()).toInt().toDp() }
            required=maxOf(required,rounded)
        }
        for (o in occurrences.filter { it.weekday in visibleDays && !it.ghost }) {
            val available = unit*dayColumnWeight(o.weekday,compactWeekend)-8.dp-1.dp
            val badgeWidth = if(o.cancelled||o.moved) with(density) { measurer.measure(if(o.cancelled) "停" else "调",style).size.width.toDp() }+2.dp else 0.dp
            val titleWidth = available-badgeWidth
            val detailGap = if (o.teacher.isNotBlank() && o.room.isNotBlank()) 1.dp else 0.dp
            val needed = height(o.course.name,titleWidth)+height(o.teacher,available)+height(o.room,available)+detailGap+12.dp
            include(needed,o.layoutPeriodEnd-o.layoutPeriodStart+1)
        }
        for (block in blocks) {
            val span = visibleDays.filter { it in block.startWeekday..block.endWeekday }
            if(span.isEmpty())continue
            val columns = span.sumOf { dayColumnWeight(it,compactWeekend).toDouble() }.toFloat()
            val available = unit*columns+gridGap*(span.size-1)-8.dp
            val needed = height(block.name,available)+height(block.note,available)+10.dp
            include(needed,block.periodEnd-block.periodStart+1)
        }
        required
    }
}

@Composable
private fun autoWeekCourseFontSize(
    occurrences: List<DemoOccurrence>,
    visibleDays: List<Int>,
    compactWeekend: Boolean,
    width: Dp,
    periodHeight: Dp,
): TextUnit {
    val measurer = rememberTextMeasurer(cacheSize = 128)
    val density = LocalDensity.current
    return remember(occurrences, visibleDays, compactWeekend, width, periodHeight, density) {
        val weight = visibleDays.sumOf { dayColumnWeight(it, compactWeekend).toDouble() }.toFloat()
        val unit = (width - timeColumnWidth - gridGap * visibleDays.size) / weight
        val cards = occurrences.filter { it.weekday in visibleDays && !it.ghost }.map { occurrence ->
            val available = unit * dayColumnWeight(occurrence.weekday, compactWeekend) - 9.dp
            val statusWidth = if (occurrence.cancelled || occurrence.moved) 10.dp else 0.dp
            val texts = listOf(
                occurrence.course.name to (available - statusWidth).coerceAtLeast(1.dp),
                occurrence.teacher to available,
                occurrence.room to available,
            ).filter { (text, _) -> text.isNotBlank() }
            val count = (occurrence.layoutPeriodEnd - occurrence.layoutPeriodStart + 1).coerceAtLeast(1)
            val availableHeight = periodHeight * count + gridGap * (count - 1) - 12.dp
            texts to availableHeight
        }
        if (cards.isEmpty()) return@remember QztcTypography.compact.fontSize

        fun fits(fontSize: TextUnit): Boolean {
            val style = QztcTypography.compact.copy(fontSize = fontSize, lineHeight = fontSize * 1.24f)
            return cards.all { (texts, availableHeight) ->
                val textHeight = texts.sumOf { (text, available) ->
                    val maxWidth = with(density) { available.roundToPx().coerceAtLeast(1) }
                    measurer.measure(text, style, constraints = Constraints(maxWidth = maxWidth)).size.height
                }
                val needed = textHeight + with(density) { (texts.size - 1).coerceAtLeast(0).dp.toPx().toInt() }
                with(density) { needed.toDp() } <= availableHeight
            }
        }

        val largestFit = (21..56).map { it / 2f }.lastOrNull { fits(it.sp) }
        (largestFit ?: 10.5f).sp
    }
}

@Composable
private fun WeekGrid(
    occurrences: List<DemoOccurrence>,
    activityBlocks: List<ActivityWeekBlock>,
    lunchExpanded: Boolean,
    hasLunchClass: Boolean,
    compactWeekend: Boolean,
    visibleDays: List<Int>,
    onCourseClick: (DemoOccurrence) -> Unit,
    onActivityClick: (ActivityWeekBlock) -> Unit,
    gridWidth: Dp,
    metrics: WeekGridMetrics,
    periodFor: (Int) -> cn.qztc.timetable.core.domain.model.Period,
    courseFontSize: TextUnit,
    fadedDays: Set<Int> = emptySet(),
) {
    val totalWeight = visibleDays.sumOf { dayColumnWeight(it, compactWeekend).toDouble() }.toFloat()
    val unitWidth = (gridWidth - timeColumnWidth - gridGap * visibleDays.size) / totalWeight
    val lunchHeight = metrics.lunchHeight(hasLunchClass, lunchExpanded)
    val density = LocalDensity.current
    val periodPx = with(density) { metrics.periodHeight.roundToPx() }
    val gapPx = with(density) { gridGap.roundToPx() }
    val lunchPx = with(density) { lunchHeight.roundToPx() }
    val eveningPx = with(density) { metrics.eveningBreakHeight.roundToPx() }
    val snappedPeriodHeight = with(density) { periodPx.toDp() }
    val snappedGap = with(density) { gapPx.toDp() }
    val snappedLunchHeight = with(density) { lunchPx.toDp() }
    val snappedEveningHeight = with(density) { eveningPx.toDp() }
    val snappedMetrics = metrics.copy(
        periodHeight = snappedPeriodHeight,
        eveningBreakHeight = snappedEveningHeight,
    )
    val totalHeight = with(density) {
        val totalPx = if (hasLunchClass) {
            periodPx * 13 + eveningPx + gapPx * 13
        } else {
            periodPx * 11 + lunchPx + eveningPx + gapPx * 12
        }
        totalPx.toDp()
    }
    // Merged cells own their slots. Drawing empty cells below transparent merged
    // cells makes the individual borders show through like an overlay.
    val occupiedSlots = remember(occurrences, activityBlocks) {
        buildSet {
            occurrences.forEach { occurrence ->
                for (period in occurrence.layoutPeriodStart..occurrence.layoutPeriodEnd) add(occurrence.weekday to period)
            }
            activityBlocks.forEach { block ->
                for (day in block.startWeekday..block.endWeekday) {
                    for (period in block.periodStart..block.periodEnd) add(day to period)
                }
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(totalHeight)) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(snappedGap),
        ) {
            (1..4).forEach { PeriodGridRow(it, visibleDays, compactWeekend, periodFor, snappedMetrics, occupiedSlots) }
            if (hasLunchClass) {
                PeriodGridRow(5, visibleDays, compactWeekend, periodFor, snappedMetrics, occupiedSlots, numberLabel = "午1")
                PeriodGridRow(6, visibleDays, compactWeekend, periodFor, snappedMetrics, occupiedSlots, numberLabel = "午2")
            } else {
                LunchFoldedRow(snappedLunchHeight)
            }
            (7..10).forEach { PeriodGridRow(it, visibleDays, compactWeekend, periodFor, snappedMetrics, occupiedSlots) }
            EveningBreakRow(snappedMetrics)
            (11..13).forEach { PeriodGridRow(it, visibleDays, compactWeekend, periodFor, snappedMetrics, occupiedSlots) }
        }
        occurrences.forEachIndexed { index, occurrence ->
            key(occurrence.id, index, occurrence.weekday, occurrence.periodStart, occurrence.periodEnd) {
                MergedCourseCell(
                    occurrence = occurrence,
                    unitWidth = unitWidth,
                    compactWeekend = compactWeekend,
                    lunchHeightPx = lunchPx,
                    hasLunchClass = hasLunchClass,
                    eveningBreakHeightPx = eveningPx,
                    periodHeightPx = periodPx,
                    gridGapPx = gapPx,
                    visibleDays = visibleDays,
                    metrics = snappedMetrics,
                    courseFontSize = courseFontSize,
                    holidayAlpha = if (occurrence.weekday in fadedDays) .42f else 1f,
                    onClick = onCourseClick,
                )
            }
        }
        // Keep lunch and evening separators visible beneath all-day holidays.
        val separatedBlocks = activityBlocks.flatMap { block ->
            if (!block.activityId.startsWith("holiday:")) listOf(block) else buildList {
                add(block.copy(periodStart = 1, periodEnd = if (hasLunchClass) 6 else 4))
                add(block.copy(periodStart = 7, periodEnd = 10))
                add(block.copy(periodStart = 11, periodEnd = 13))
            }
        }
        separatedBlocks.forEachIndexed { index, block ->
            key(block.activityId, block.sessionId, index, block.startWeekday, block.endWeekday, block.periodStart, block.periodEnd) {
                MergedActivityCell(
                    block = block,
                    unitWidth = unitWidth,
                    compactWeekend = compactWeekend,
                    lunchHeightPx = lunchPx,
                    hasLunchClass = hasLunchClass,
                    eveningBreakHeightPx = eveningPx,
                    periodHeightPx = periodPx,
                    gridGapPx = gapPx,
                    visibleDays = visibleDays,
                    metrics = snappedMetrics,
                    onClick = onActivityClick,
                )
            }
        }
    }
}

@Composable
private fun PeriodGridRow(
    periodNo: Int,
    visibleDays: List<Int>,
    compactWeekend: Boolean,
    periodFor: (Int) -> cn.qztc.timetable.core.domain.model.Period,
    metrics: WeekGridMetrics,
    occupiedSlots: Set<Pair<Int, Int>>,
    numberLabel: String = gridPeriodLabel(periodNo),
) {
    val colors = LocalQztcColors.current
    val contentAlpha = LocalGlassSettings.current.contentAlpha
    val period = periodFor(periodNo)
    Row(
        Modifier.fillMaxWidth().height(metrics.periodHeight).clipToBounds(),
        horizontalArrangement = Arrangement.spacedBy(gridGap),
    ) {
        Box(
            modifier = Modifier
                .width(timeColumnWidth)
                .height(metrics.periodHeight)
                .clip(RoundedCornerShape(9.dp))
                .background(colors.solid.copy(alpha = LocalGlassSettings.current.functionalAlpha.coerceIn(.20f, .80f))),
        ) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(numberLabel, style = QztcTypography.compact, color = colors.ink)
                Text(period.startTime.toString(), style = QztcTypography.compact, color = colors.mut)
                if (metrics.periodHeight >= 48.dp) {
                    Text(period.endTime.toString(), style = QztcTypography.compact, color = colors.mut)
                }
            }
        }
        Row(
            Modifier
                .weight(1f)
                .height(metrics.periodHeight),
            horizontalArrangement = Arrangement.spacedBy(gridGap),
        ) {
            visibleDays.forEach { day ->
                val slotModifier = Modifier.weight(dayColumnWeight(day, compactWeekend)).fillMaxHeight()
                if ((day to periodNo) in occupiedSlots) {
                    Spacer(slotModifier)
                    return@forEach
                }
                Box(
                    slotModifier
                        .testTag("week-empty-$day-$periodNo")
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.solid.copy(alpha = (contentAlpha * 0.55f).coerceIn(.12f, .55f)))
                        .border(0.5.dp, colors.line, RoundedCornerShape(10.dp)),
                )
            }
        }
    }
}

@Composable
private fun LunchFoldedRow(height: Dp) {
    val colors = LocalQztcColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.solid.copy(alpha = LocalGlassSettings.current.functionalAlpha.coerceIn(.20f, .80f))),
        contentAlignment = Alignment.Center,
    ) {
        Text("午间休息", style = QztcTypography.compact, color = colors.mut)
    }
}

@Composable
private fun EveningBreakRow(metrics: WeekGridMetrics) {
    val colors = LocalQztcColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(metrics.eveningBreakHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.solid.copy(alpha = LocalGlassSettings.current.functionalAlpha.coerceIn(.20f, .80f))),
        contentAlignment = Alignment.Center,
    ) {
        Text("晚间休息", style = QztcTypography.compact, color = colors.mut)
    }
}

@Composable
private fun MergedCourseCell(
    occurrence: DemoOccurrence,
    unitWidth: Dp,
    compactWeekend: Boolean,
    lunchHeightPx: Int,
    hasLunchClass: Boolean,
    eveningBreakHeightPx: Int,
    periodHeightPx: Int,
    gridGapPx: Int,
    visibleDays: List<Int>,
    metrics: WeekGridMetrics,
    courseFontSize: TextUnit,
    holidayAlpha: Float,
    onClick: (DemoOccurrence) -> Unit,
) {
    if (occurrence.weekday !in visibleDays) return
    val colors = LocalQztcColors.current
    val palette = occurrence.visualColor(colors.isDark)
    val contentAlpha = LocalGlassSettings.current.contentAlpha
    val density = LocalDensity.current
    val y = with(density) {
        weekGridRowOffsetPx(
            occurrence.layoutPeriodStart,
            periodHeightPx,
            lunchHeightPx,
            hasLunchClass,
            eveningBreakHeightPx,
            gridGapPx,
        ).toDp()
    }
    val height = with(density) {
        (
            weekGridRowOffsetPx(
                occurrence.layoutPeriodEnd,
                periodHeightPx,
                lunchHeightPx,
                hasLunchClass,
                eveningBreakHeightPx,
                gridGapPx,
            ) + periodHeightPx -
                weekGridRowOffsetPx(
                    occurrence.layoutPeriodStart,
                    periodHeightPx,
                    lunchHeightPx,
                    hasLunchClass,
                    eveningBreakHeightPx,
                    gridGapPx,
                )
            ).toDp()
    }
    val periodCount = occurrence.layoutPeriodEnd - occurrence.layoutPeriodStart + 1
    val shape = RoundedCornerShape(QztcCorners.courseBlock)
    val precedingDays = visibleDays.count { it < occurrence.weekday }
    val precedingWeight = visibleDays.filter { it < occurrence.weekday }
        .sumOf { dayColumnWeight(it, compactWeekend).toDouble() }
        .toFloat()
    val currentDayWeight = dayColumnWeight(occurrence.weekday, compactWeekend)
    val blockModifier = Modifier
        .offset(
            x = timeColumnWidth + gridGap + unitWidth * precedingWeight + gridGap * precedingDays,
            y = y,
        )
        .size(unitWidth * currentDayWeight, height)
        .testTag("week-course-${occurrence.id}")
        // Alpha must apply after positioning. A parent alpha layer at the grid
        // origin clips offset holiday courses outside its own cell-sized bounds.
        .alpha(holidayAlpha * if (occurrence.cancelled || occurrence.ghost) .58f else 1f)
        .clickable(enabled = !occurrence.ghost) { onClick(occurrence) }
        .pressable()

    if (occurrence.ghost) {
        Box(
            blockModifier.border(1.dp, colors.warn, shape).clip(shape).background(colors.warnSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text("已调至\n周四", style = QztcTypography.compact, color = colors.warn, textAlign = TextAlign.Center)
        }
        return
    }

    Box(
        modifier = blockModifier
            .clip(shape)
            .background(palette.glassBg(colors.isDark, contentAlpha)),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    occurrence.course.name,
                    style = QztcTypography.compact.copy(fontSize = courseFontSize, lineHeight = courseFontSize * 1.24f, textDecoration = if (occurrence.cancelled) TextDecoration.LineThrough else null),
                    color = palette.tx(colors.isDark),
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f),
                )
                if (occurrence.cancelled || occurrence.moved) {
                    Text(
                        if (occurrence.cancelled) "停" else "调",
                        style = QztcTypography.compact,
                        color = if (occurrence.cancelled) colors.danger else colors.warn,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                if (occurrence.teacher.isNotBlank()) {
                    Text(
                        occurrence.teacher,
                        style = QztcTypography.compact.copy(fontSize = courseFontSize, lineHeight = courseFontSize * 1.24f),
                        color = palette.tx(colors.isDark).copy(alpha = .84f),
                        overflow = TextOverflow.Clip,
                    )
                }
                Text(
                    occurrence.room,
                    style = QztcTypography.compact.copy(fontSize = courseFontSize, lineHeight = courseFontSize * 1.24f),
                    color = palette.tx(colors.isDark).copy(alpha = .72f),
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

@Composable
private fun MergedActivityCell(
    block: ActivityWeekBlock,
    unitWidth: Dp,
    compactWeekend: Boolean,
    lunchHeightPx: Int,
    hasLunchClass: Boolean,
    eveningBreakHeightPx: Int,
    periodHeightPx: Int,
    gridGapPx: Int,
    visibleDays: List<Int>,
    metrics: WeekGridMetrics,
    onClick: (ActivityWeekBlock) -> Unit,
) {
    val spanned = visibleDays.filter { it in block.startWeekday..block.endWeekday }
    if (spanned.isEmpty()) return
    val colors = LocalQztcColors.current
    val palette = visualCourseColor(block.color ?: 0)
    val contentAlpha = LocalGlassSettings.current.contentAlpha
    val density = LocalDensity.current
    val y = with(density) {
        weekGridRowOffsetPx(
            block.periodStart,
            periodHeightPx,
            lunchHeightPx,
            hasLunchClass,
            eveningBreakHeightPx,
            gridGapPx,
        ).toDp()
    }
    val height = with(density) {
        (
            weekGridRowOffsetPx(
                block.periodEnd,
                periodHeightPx,
                lunchHeightPx,
                hasLunchClass,
                eveningBreakHeightPx,
                gridGapPx,
            ) + periodHeightPx -
                weekGridRowOffsetPx(
                    block.periodStart,
                    periodHeightPx,
                    lunchHeightPx,
                    hasLunchClass,
                    eveningBreakHeightPx,
                    gridGapPx,
                )
            ).toDp()
    }
    val shape = RoundedCornerShape(QztcCorners.courseBlock)
    val precedingDays = visibleDays.count { it < spanned.first() }
    val precedingWeight = visibleDays.filter { it < spanned.first() }
        .sumOf { dayColumnWeight(it, compactWeekend).toDouble() }
        .toFloat()
    val spanWeight = spanned.sumOf { dayColumnWeight(it, compactWeekend).toDouble() }.toFloat()
    val innerGaps = (spanned.size - 1).coerceAtLeast(0)
    val blockModifier = Modifier
        .offset(
            x = timeColumnWidth + gridGap + unitWidth * precedingWeight + gridGap * precedingDays,
            y = y,
        )
        .size(unitWidth * spanWeight + gridGap * innerGaps, height)
        .clickable { onClick(block) }
        .pressable()
    val compact = block.periodEnd - block.periodStart + 1 <= 1
    Box(
        modifier = blockModifier
            .clip(shape)
            .background(palette.glassBg(colors.isDark, contentAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                block.name,
                style = QztcTypography.compact,
                color = palette.tx(colors.isDark),
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
            )
            if (block.note.isNotBlank()) {
                Text(
                    block.note,
                    style = QztcTypography.compact,
                    color = palette.tx(colors.isDark).copy(alpha = .72f),
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun gridPeriodLabel(periodNo: Int): String = when (periodNo) {
    in 1..4 -> periodNo.toString()
    5 -> "午1"
    6 -> "午2"
    in 7..13 -> (periodNo - 2).toString()
    else -> periodNo.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AcademicDayEditSheet(
    day: AcademicDay,
    semester: Semester,
    onSave: (AcademicDay) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val colors = LocalQztcColors.current
    var date by remember(day.id) { mutableStateOf(day.date) }
    var name by remember(day.id) { mutableStateOf(day.name) }
    var sourceDate by remember(day.id) { mutableStateOf(scheduleStateDate(semester, day)) }
    var error by remember(day.id) { mutableStateOf<String?>(null) }
    var pickingSource by remember(day.id) { mutableStateOf<Boolean?>(null) }
    pickingSource?.let { source ->
        QztcDatePicker(if (source) sourceDate ?: date else date, onDismiss = { pickingSource = null }) {
            if (source) sourceDate = it else date = it
            error = null
            pickingSource = null
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .testTag("week-academic-day-editor")
                .verticalScroll(rememberScrollState())
                .padding(horizontal = QztcSpacing.xl)
                .padding(bottom = QztcSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(QztcSpacing.md),
        ) {
            Text(
                if (day.kind == AcademicDayKind.HOLIDAY) "放假设置" else "调休设置",
                style = QztcTypography.pageTitle,
                color = colors.ink,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(if (day.kind == AcademicDayKind.HOLIDAY) "节假日名称" else "调休名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (day.kind == AcademicDayKind.HOLIDAY) WeekDateChoice(
                label = "放假日期",
                date = date,
            ) { date = it; error = null }
            if (day.kind == AcademicDayKind.MAKEUP) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { pickingSource = true },
                        modifier = Modifier.weight(1f),
                    ) { Text(sourceDate?.let(::weekDateLabel) ?: "哪天") }
                    Text("的课调到", style = QztcTypography.body, color = colors.ink)
                    TextButton(
                        onClick = { pickingSource = false },
                        modifier = Modifier.weight(1f),
                    ) { Text(weekDateLabel(date)) }
                }
                if (sourceDate != null) {
                    TextButton(onClick = { sourceDate = null }) { Text("暂不指定原课程日期") }
                }
            }
            error?.let { Text(it, style = QztcTypography.meta, color = colors.danger) }
            Button(
                onClick = {
                    val sourceWeek = sourceDate?.let { SemesterCalendar.weekOf(semester, it) }
                    onSave(
                        day.copy(
                            id = AcademicDay.idFor(day.semesterId, date),
                            date = date,
                            name = name.trim().ifBlank { if (day.kind == AcademicDayKind.HOLIDAY) "放假" else "调休" },
                            sourceWeek = sourceWeek,
                            sourceWeekday = sourceDate?.dayOfWeek?.value,
                            useCurrentWeek = false,
                            useCurrentWeekday = false,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("完成") }
            onDelete?.let { delete ->
                OutlinedButton(onClick = delete, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("删除这项设置")
                }
            }
        }
    }
}

@Composable
private fun WeekDateChoice(
    label: String,
    date: java.time.LocalDate,
    value: String = weekDateLabel(date),
    onChange: (java.time.LocalDate) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    val colors = LocalQztcColors.current
    if (picking) QztcDatePicker(date, onDismiss = { picking = false }) {
        onChange(it)
        picking = false
    }
    Row(
        Modifier
            .fillMaxWidth()
            .testTag("week-date-$label")
            .clickable { picking = true }
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = QztcTypography.body, color = colors.ink)
        Text(value, style = QztcTypography.body, color = colors.accentDeep)
    }
}

private fun weekDateLabel(date: java.time.LocalDate): String =
    "%04d/%02d/%02d".format(date.year, date.monthValue, date.dayOfMonth)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekChoiceSheet(selected: Int, totalWeeks: Int, currentWeek: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalQztcColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = QztcSpacing.xl).padding(bottom = QztcSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("选择周次", style = QztcTypography.pageTitle, modifier = Modifier.padding(vertical = QztcSpacing.sm))
            (1..totalWeeks.coerceAtLeast(1)).chunked(5).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { week ->
                        Box(
                            Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (week == selected) colors.accent else colors.solid)
                                .border(if (week == currentWeek) 2.dp else 1.dp,
                                    if (week == currentWeek) colors.accentDeep else colors.line, RoundedCornerShape(8.dp))
                                .clickable { onSelect(week) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(week.toString(), style = QztcTypography.body,
                                color = if (week == selected) colors.onAccent else colors.ink)
                        }
                    }
                    repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}


private fun scheduleStateDate(semester: cn.qztc.timetable.core.domain.model.Semester, mark: cn.qztc.timetable.core.domain.calendar.AcademicDay): java.time.LocalDate? {
    val week = if(mark.useCurrentWeek) cn.qztc.timetable.core.domain.calendar.SemesterCalendar.weekOf(semester,mark.date) else mark.sourceWeek
    val day = if(mark.useCurrentWeekday) mark.date.dayOfWeek.value else mark.sourceWeekday
    return if(week != null && day != null) cn.qztc.timetable.core.domain.calendar.SemesterCalendar.dateOf(semester,week,day) else null
}
