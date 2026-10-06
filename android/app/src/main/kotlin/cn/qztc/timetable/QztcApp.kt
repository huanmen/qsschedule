package cn.qztc.timetable

import android.app.ActivityManager
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import cn.qztc.timetable.core.domain.model.ActivityKind
import cn.qztc.timetable.core.domain.model.Course
import cn.qztc.timetable.core.domain.model.CourseMeeting
import cn.qztc.timetable.core.domain.model.HomeView
import cn.qztc.timetable.core.domain.model.MeetingField
import cn.qztc.timetable.core.domain.model.PeriodScheme
import cn.qztc.timetable.core.domain.model.PeriodSchemeFactory
import cn.qztc.timetable.core.domain.model.QnuPeriodDefaults
import cn.qztc.timetable.core.domain.model.ScheduleActivity
import cn.qztc.timetable.core.domain.model.Semester
import cn.qztc.timetable.core.domain.model.SourceType
import cn.qztc.timetable.core.domain.model.WeekRule
import cn.qztc.timetable.core.data.db.TimetableDatabase
import cn.qztc.timetable.core.data.db.SyncRunEntity
import cn.qztc.timetable.core.data.repository.ChangeSetRecord
import cn.qztc.timetable.core.data.repository.ChangeSetStatus
import cn.qztc.timetable.core.data.repository.ImportOutcome
import cn.qztc.timetable.core.data.repository.TimetableRepository
import cn.qztc.timetable.background.BackgroundCropScreen
import cn.qztc.timetable.background.BackgroundImageStore
import cn.qztc.timetable.background.CropPosition
import cn.qztc.timetable.background.PageBackground
import cn.qztc.timetable.export.ScheduleExportStore
import cn.qztc.timetable.core.ui.background.BackgroundMotion
import cn.qztc.timetable.core.ui.background.BackgroundSource
import cn.qztc.timetable.core.ui.glass.GlassBackdropHost
import cn.qztc.timetable.core.ui.glass.GlassQuality
import cn.qztc.timetable.core.ui.glass.GlassRole
import cn.qztc.timetable.core.ui.glass.GlassSurface
import cn.qztc.timetable.core.ui.glass.LocalGlassSettings
import cn.qztc.timetable.core.ui.motion.MotionSpec
import cn.qztc.timetable.core.ui.theme.QztcThemeMode
import cn.qztc.timetable.core.ui.theme.QztcPalette
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcTheme
import cn.qztc.timetable.core.ui.theme.QztcTypography
import cn.qztc.timetable.feature.settings.MineScreen
import cn.qztc.timetable.feature.settings.ReminderSettingsScreen
import cn.qztc.timetable.feature.settings.ReminderTestState
import cn.qztc.timetable.core.domain.model.ReminderDisplayMode
import cn.qztc.timetable.adskip.AdSkipScreen
import cn.qztc.timetable.adskip.PluginMarketScreen
import cn.qztc.timetable.feature.settings.MineSemesterOption
import cn.qztc.timetable.feature.settings.MineUiState
import cn.qztc.timetable.feature.settings.ImportedBackground
import cn.qztc.timetable.feature.settings.CourseColorSettingsScreen
import cn.qztc.timetable.feature.settings.PersonalizationScreen
import cn.qztc.timetable.feature.settings.SemesterScheduleSettingsScreen
import cn.qztc.timetable.feature.settings.PrivacyAboutScreen
import cn.qztc.timetable.feature.timetable.DemoScheduleState
import cn.qztc.timetable.feature.timetable.CourseEditDraft
import cn.qztc.timetable.feature.timetable.ActivityEditDraft
import cn.qztc.timetable.feature.timetable.DemoOccurrence
import cn.qztc.timetable.feature.timetable.ImportedSchedule
import cn.qztc.timetable.feature.timetable.TodayScreen
import cn.qztc.timetable.feature.timetable.WeekScreen
import cn.qztc.timetable.feature.timetable.DayScheduleScreen
import cn.qztc.timetable.feature.timetable.CourseReminderUiState
import cn.qztc.timetable.feature.timetable.CampusAliasSettingsScreen
import cn.qztc.timetable.feature.timetable.CampusRouteScreen
import cn.qztc.timetable.qnu.QnuCredentialStore
import cn.qztc.timetable.qnu.QnuImportScreen
import cn.qztc.timetable.qnu.QnuSemesterFactory
import cn.qztc.timetable.reminder.CourseReminderScheduler
import cn.qztc.timetable.widget.TodayWidgetProvider
import cn.qztc.timetable.notifications.QztcNotifications
import cn.qztc.timetable.snapshot.SnapshotGalleryScreen
import cn.qztc.timetable.snapshot.SnapshotRecord
import cn.qztc.timetable.snapshot.SnapshotStore
import cn.qztc.timetable.sync.QnuSyncScheduler
import cn.qztc.timetable.sync.UpcomingChangesDialog
import cn.qztc.timetable.update.AppUpdateClient
import cn.qztc.timetable.update.AppUpdateDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

private data class ReminderPermissionAction(val minutes: Int? = null)

private sealed interface AppUpdateState {
    data object Checking : AppUpdateState
    data class Available(
        val manifest: AppUpdateClient.Manifest, val downloading: Boolean = false,
        val downloadedBytes: Long = 0, val file: java.io.File? = null, val clearing: Boolean = false,
    ) : AppUpdateState
    data class Message(val title: String, val detail: String) : AppUpdateState
}

/** 一级目的地。启动首页可配置为今日/周课表/记住上次（T2），“我的”不可为首页。 */
enum class TopDestination(val label: String, val icon: ImageVector) {
    Today("今日", Icons.Outlined.Schedule),
    Week("周课表", Icons.Outlined.CalendarMonth),
    Mine("我的", Icons.Outlined.Person),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QztcApp(openTodayOnStart: Boolean = false) {
    val context = LocalContext.current
    val preferencesStore = remember { AppPreferencesStore(context.qztcDataStore) }
    val credentialStore = remember(context) { QnuCredentialStore(context) }
    val timetableDatabase = remember(context) { TimetableDatabase.build(context) }
    val timetableRepository = remember(timetableDatabase) { TimetableRepository(timetableDatabase) }
    val loadedPreferences by preferencesStore.preferences.collectAsStateWithLifecycle(
        initialValue = null,
    )
    val preferences = loadedPreferences ?: AppPreferences()
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (preferences.themeMode) {
        QztcThemeMode.SYSTEM -> systemDark
        QztcThemeMode.LIGHT -> false
        QztcThemeMode.DARK -> true
    }
    val lowMemory = remember(context) {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice
    }

    QztcTheme(
        darkTheme = darkTheme,
        palette = preferences.palette,
        glassSettings = preferences.glassSettings(Build.VERSION.SDK_INT, lowMemory),
    ) {
        val colors = LocalQztcColors.current
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        val courseReminderState = CourseReminderUiState(
            enabled = preferences.remindersEnabled && QztcNotifications.canPost(context),
            minutes = preferences.reminderMinutes,
            displayMode = preferences.reminderDisplayMode,
            overrides = preferences.courseReminderOverrides,
            messages = preferences.courseReminderMessages,
        )
        val saveReminder: (String, Int?, String?) -> Unit = { courseId, minutes, message ->
            scope.launch { preferencesStore.setCourseReminder(courseId, minutes, message) }
        }
        val demoScheduleState = remember { DemoScheduleState() }
        var current by remember { mutableStateOf<TopDestination?>(null) }
        var transientParentDestination by rememberSaveable { mutableStateOf<TopDestination?>(null) }
        var suspendVisualEffects by remember { mutableStateOf(false) }
        var visualTransitionToken by remember { mutableIntStateOf(0) }
        var weekResetRequest by rememberSaveable { mutableIntStateOf(0) }
        val settingsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
        val personalizationScrollState = rememberScrollState()
        var settingsRestorePending by rememberSaveable { mutableStateOf(false) }
        var savedSettingsItemIndex by rememberSaveable { mutableIntStateOf(0) }
        var savedSettingsItemOffset by rememberSaveable { mutableIntStateOf(0) }
        var secondaryPage by rememberSaveable { mutableStateOf(SecondaryPage.NONE) }
        var upcomingChangesDialog by rememberSaveable { mutableStateOf(false) }
        var importReturnPage by rememberSaveable { mutableStateOf(ImportReturnPage.NONE) }
        var credentialRevision by rememberSaveable { mutableIntStateOf(0) }
        var cropImagePath by rememberSaveable { mutableStateOf<String?>(null) }
        var importedSemesters by remember { mutableStateOf<List<Semester>>(emptyList()) }
        var activeSemesterId by rememberSaveable { mutableStateOf<String?>(null) }
        var activeSemester by remember { mutableStateOf<Semester?>(null) }
        var activePeriodScheme by remember { mutableStateOf<PeriodScheme?>(null) }
        var changeSets by remember { mutableStateOf<List<ChangeSetRecord>>(emptyList()) }
        var syncRuns by remember { mutableStateOf<List<SyncRunEntity>>(emptyList()) }
        var snapshots by remember { mutableStateOf<List<SnapshotRecord>>(emptyList()) }
        var importedBackgrounds by remember { mutableStateOf<List<ImportedBackground>>(emptyList()) }
        var dayDetailWeek by rememberSaveable { mutableIntStateOf(1) }
        var dayDetailDay by rememberSaveable { mutableIntStateOf(1) }
        var coverBottomBar by remember { mutableStateOf(false) }
        val imeVisible = WindowInsets.isImeVisible
        val savedCredentials = remember(credentialStore, credentialRevision) { credentialStore.load() }
        val backgroundImageStore = remember(context) { BackgroundImageStore(context) }
        val snapshotStore = remember(context) { SnapshotStore(context) }
        val exportStore = remember(context) { ScheduleExportStore(context) }
        var pendingReminderAction by remember { mutableStateOf<ReminderPermissionAction?>(null) }
        var notificationAllowed by remember { mutableStateOf(QztcNotifications.canPost(context)) }
        var updateState by remember { mutableStateOf<AppUpdateState?>(null) }
        var updateDialogVisible by remember { mutableStateOf(false) }
        var updateDownloadJob by remember { mutableStateOf<Job?>(null) }
        var updateDownloadControl by remember { mutableStateOf<AppUpdateClient.DownloadControl?>(null) }
        var reminderTestState by remember { mutableStateOf(ReminderTestState()) }
        var reminderTestJob by remember { mutableStateOf<Job?>(null) }
        var retestReminderOnResume by remember { mutableStateOf(false) }
        val permissionHistory = remember(context) { context.getSharedPreferences("startup-permissions", Context.MODE_PRIVATE) }
        val startupPermissions = rememberLauncherForActivityResult(RequestMultiplePermissions()) {
            notificationAllowed = QztcNotifications.canPost(context)
        }
        val notificationPermission = rememberLauncherForActivityResult(RequestPermission()) { granted ->
            notificationAllowed = granted
            val action = pendingReminderAction
            pendingReminderAction = null
            scope.launch {
                if (action != null) {
                    if (granted) {
                        action.minutes?.let { preferencesStore.setReminderMinutes(it) }
                        preferencesStore.setRemindersEnabled(true)
                    } else {
                        preferencesStore.setRemindersEnabled(false)
                    }
                    snackbarHostState.showSnackbar(
                        if (granted) "已允许通知提醒" else "未允许通知，提醒已保持关闭",
                    )
                }
            }
        }
        val photoPicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
            if (uri != null) {
                scope.launch {
                    try {
                        cropImagePath = backgroundImageStore.import(uri)
                        secondaryPage = SecondaryPage.BACKGROUND_CROP
                    } catch (error: Exception) {
                        snackbarHostState.showSnackbar(error.message ?: "无法读取所选图片")
                    }
                }
            }
        }

        suspend fun refreshSyncState(semesterId: String? = activeSemesterId) {
            if (semesterId == null) {
                changeSets = emptyList()
                syncRuns = emptyList()
            } else {
                changeSets = timetableRepository.changeSets(semesterId)
                syncRuns = timetableRepository.syncRuns(semesterId)
            }
        }

        fun checkForUpdates() {
            updateDialogVisible = true
            if (updateState is AppUpdateState.Available || updateState == AppUpdateState.Checking) return
            updateState = AppUpdateState.Checking
            scope.launch {
                AppUpdateClient.check(BuildConfig.VERSION_CODE).fold(
                    onSuccess = { manifest ->
                        updateState = if (manifest == null) {
                            AppUpdateState.Message("已经是最新版本", "当前版本 ${BuildConfig.VERSION_NAME}")
                        } else {
                            AppUpdateState.Available(manifest)
                        }
                    },
                    onFailure = { error ->
                        updateState = AppUpdateState.Message("检查更新失败", error.message ?: "请稍后重试")
                    },
                )
            }
        }

        fun startUpdateDownload(manifest: AppUpdateClient.Manifest) {
            if (updateDownloadJob?.isActive == true) return
            val control = AppUpdateClient.DownloadControl()
            updateDownloadControl = control
            updateState = AppUpdateState.Available(manifest, downloading = true)
            updateDownloadJob = scope.launch {
                AppUpdateClient.download(context, manifest, control) { copied, _ ->
                    scope.launch {
                        val active = updateState as? AppUpdateState.Available
                        if (updateDownloadControl === control && active?.downloading == true && !active.clearing) {
                            updateState = active.copy(downloadedBytes = copied)
                        }
                    }
                }.fold(
                    onSuccess = { file ->
                        updateState = AppUpdateState.Available(manifest, downloadedBytes = manifest.sizeBytes, file = file)
                    },
                    onFailure = { error ->
                        updateState = AppUpdateState.Message("下载更新失败", error.message ?: "请稍后重试")
                        updateDialogVisible = true
                    },
                )
            }
        }

        fun clearUpdateDownload() {
            val active = updateState as? AppUpdateState.Available ?: return
            if (active.clearing) return
            updateState = active.copy(clearing = true)
            val job = updateDownloadJob
            val control = updateDownloadControl
            job?.cancel()
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { control?.cancel() }
                    job?.cancelAndJoin()
                    AppUpdateClient.clearDownloads(context)
                    updateDownloadJob = null
                    updateDownloadControl = null
                    updateState = null
                    updateDialogVisible = false
                    snackbarHostState.showSnackbar("已退出更新，下载文件已清除")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    updateState = AppUpdateState.Message("清除失败", error.message ?: "请重试")
                    updateDialogVisible = true
                }
            }
        }

        fun testReminderMode(mode: ReminderDisplayMode, saveMode: Boolean = false) {
            reminderTestJob?.cancel()
            reminderTestState = ReminderTestState(running = true, message = "正在发送并检查测试提醒…")
            reminderTestJob = scope.launch {
                try {
                    if (saveMode) preferencesStore.setReminderDisplayMode(mode)
                    val result = QztcNotifications.testCourseReminder(context, mode)
                    notificationAllowed = QztcNotifications.canPost(context)
                    reminderTestState = ReminderTestState(
                        message = result.issue ?: if (mode == ReminderDisplayMode.SCREEN_POPUP)
                            "系统已接收测试通知，请确认顶部是否出现弹窗。" else "测试通知已发出，请在通知栏确认。",
                        needsSystemSettings = result.issue != null,
                        needsConfirmation = result.posted,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    reminderTestState = ReminderTestState(message = "未能完成通知测试，请检查系统通知设置。", needsSystemSettings = true)
                }
            }
        }

        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            notificationAllowed = QztcNotifications.canPost(context)
            if (retestReminderOnResume) {
                retestReminderOnResume = false
                testReminderMode(preferences.reminderDisplayMode)
            }
        }

        fun refreshSnapshots() {
            snapshots = snapshotStore.records()
        }

        fun refreshBackgrounds() {
            importedBackgrounds = backgroundImageStore.listImportedImages().mapIndexed { index, path ->
                ImportedBackground(path = path, label = "背景 ${index + 1}")
            }
        }

        suspend fun refreshSchedule(semesterId: String) {
            val semester = checkNotNull(timetableRepository.semester(semesterId))
            val periodScheme = timetableRepository.periodScheme(semester.id)
                ?: PeriodSchemeFactory.forSemester(semester.id)
            val imported = timetableRepository.importedSchedule(semester)
            // Keep settings and the loaded timetable on the same semester list.
            // The list used to be refreshed only during startup/import, so a
            // valid loaded timetable could still make Course Arrangement fall
            // back to the empty-state screen.
            importedSemesters = timetableRepository.semestersWithinImportWindow()
            demoScheduleState.loadImported(imported)
            activeSemesterId = semester.id
            activeSemester = semester
            activePeriodScheme = periodScheme
            refreshSyncState(semester.id)
            TodayWidgetProvider.requestUpdate(context)
        }

        suspend fun ensureCalendarSemester(
            detected: cn.qztc.timetable.feature.settings.DetectedSchoolSemester,
        ): Semester {
            val startYear = detected.schoolYear.substringBefore('-').toIntOrNull()
                ?: detected.startDate.year
            val semesterYear = if (detected.term == cn.qztc.timetable.core.domain.model.Term.SPRING) {
                startYear + 1
            } else {
                startYear
            }
            val termId = if (detected.term == cn.qztc.timetable.core.domain.model.Term.SPRING) "spring" else "autumn"
            val id = "qnu-$semesterYear-$termId"
            val existing = timetableRepository.semester(id)
            val hasCourses = existing?.let { timetableRepository.state(it.id).courses.isNotEmpty() } == true
            val endDate = detected.endDate ?: detected.startDate.plusWeeks(19).plusDays(6)
            val totalWeeks = ((java.time.temporal.ChronoUnit.DAYS.between(detected.startDate, endDate) + 7) / 7)
                .toInt()
                .coerceIn(1, 30)
            val target = when {
                existing == null -> Semester(
                    id = id,
                    name = "$semesterYear ${if (detected.term == cn.qztc.timetable.core.domain.model.Term.SPRING) "春" else "秋"}",
                    schoolYear = semesterYear,
                    term = detected.term,
                    startDate = detected.startDate,
                    totalWeeks = totalWeeks,
                )
                hasCourses -> existing
                else -> existing.copy(startDate = detected.startDate, totalWeeks = totalWeeks)
            }
            timetableRepository.upsertSemester(target)
            if (timetableRepository.periodScheme(target.id) == null) {
                timetableRepository.upsertPeriodScheme(PeriodSchemeFactory.forSemester(target.id))
            }
            importedSemesters = timetableRepository.semestersWithinImportWindow()
            return target
        }

        LaunchedEffect(timetableRepository) {
            val settings = timetableRepository.settings()
            val available = timetableRepository.semestersWithinImportWindow()
            importedSemesters = available
            val semester = available.firstOrNull { it.id == settings.activeSemesterId }
                ?: available.firstOrNull()
            if (semester != null) {
                refreshSchedule(semester.id)
                if (settings.activeSemesterId != semester.id) {
                    timetableRepository.updateSettings(settings.copy(activeSemesterId = semester.id))
                }
            }
        }

        // Course Arrangement can be opened before the first database restore
        // finishes. Resolve the first persisted semester with actual courses at
        // the entry point instead of treating the transient empty state as a
        // request to import again.
        LaunchedEffect(secondaryPage) {
            if (secondaryPage != SecondaryPage.SEMESTER_SCHEDULE) return@LaunchedEffect
            if (activeSemester != null && demoScheduleState.importedSchedule != null) return@LaunchedEffect

            val persisted = withContext(Dispatchers.IO) {
                timetableRepository.semesters()
                    .firstOrNull { timetableRepository.state(it.id).courses.isNotEmpty() }
            }
            val loaded = demoScheduleState.importedSchedule?.semester ?: persisted
            if (loaded != null) {
                refreshSchedule(loaded.id)
            }
        }

        LaunchedEffect(preferences.syncEnabled, preferences.syncIntervalHours) {
            if (preferences.syncEnabled) {
                QnuSyncScheduler.schedule(context, preferences.syncIntervalHours)
            } else {
                QnuSyncScheduler.cancel(context)
            }
        }

        LaunchedEffect(context) {
            QztcNotifications.ensureChannels(context)
        }

        LaunchedEffect(
            preferences.remindersEnabled,
            preferences.reminderMinutes,
            preferences.reminderDisplayMode,
            preferences.courseReminderOverrides,
            preferences.courseReminderMessages,
            demoScheduleState.importedSchedule,
        ) {
            val reminderSemester = Semester.forReminders(timetableRepository.semesters(), LocalDate.now())
            val reminderSchedule = reminderSemester?.let { timetableRepository.importedSchedule(it) }
            CourseReminderScheduler.reschedule(
                context = context,
                schedule = reminderSchedule,
                enabled = preferences.remindersEnabled,
                minutesBefore = preferences.reminderMinutes,
                displayMode = preferences.reminderDisplayMode,
                courseOverrides = preferences.courseReminderOverrides,
                courseMessages = preferences.courseReminderMessages,
            )
        }

        LaunchedEffect(loadedPreferences, openTodayOnStart) {
            val loaded = loadedPreferences
            if (current == null && loaded != null) {
                current = if (openTodayOnStart) TopDestination.Today else loaded.startDestination()
            }
        }

        LaunchedEffect(current) {
            when (current) {
                TopDestination.Today -> preferencesStore.setLastDestination(HomeView.TODAY)
                TopDestination.Week -> preferencesStore.setLastDestination(HomeView.WEEK)
                else -> Unit
            }
        }

        LaunchedEffect(visualTransitionToken) {
            if (visualTransitionToken == 0) return@LaunchedEffect
            val token = visualTransitionToken
            delay(260)
            if (visualTransitionToken == token) suspendVisualEffects = false
        }

        val pauseVisualEffects = {
            suspendVisualEffects = true
            visualTransitionToken += 1
        }

        val saveCourseEdit: (DemoOccurrence, CourseEditDraft) -> Unit = { occurrence, draft ->
            scope.launch {
                runCatching {
                    val semesterId = activeSemesterId ?: occurrence.course.semesterId
                    val state = timetableRepository.state(semesterId)
                    val course = state.courses.firstOrNull { it.id == occurrence.course.id }
                        ?: error("这门课程不在本地课表中")
                    val previousMeeting = state.meetings.firstOrNull { it.id == occurrence.meetingId }
                        ?: state.meetings.firstOrNull {
                            it.courseId == occurrence.course.id &&
                                it.weekday == occurrence.weekday &&
                                it.periodStart == occurrence.periodStart &&
                                it.periodEnd == occurrence.periodEnd
                        }
                    val slots = draft.weeklySlots.ifEmpty { listOf(cn.qztc.timetable.feature.timetable.WeeklySlotDraft(draft.weekday, draft.periodStart, draft.periodEnd)) }
                    val meetings = slots.map { slot ->
                        val source = state.meetings.firstOrNull { it.id == slot.sourceMeetingId }
                        CourseMeeting(
                            id = CourseMeeting.stableId(course.id, slot.weekday, slot.periodStart, slot.periodEnd),
                            courseId = course.id,
                            weekday = slot.weekday,
                            periodStart = slot.periodStart,
                            periodEnd = slot.periodEnd,
                            weekRule = WeekRule.of(slot.weeks.ifEmpty { draft.weeks }, "自定义周次"),
                            teacher = if (source != null && draft.teacher == occurrence.teacher) source.teacher else draft.teacher.trim().ifEmpty { null },
                            room = if (source != null && draft.room == occurrence.room) source.room else draft.room.trim().ifEmpty { null },
                            sourceType = previousMeeting?.sourceType ?: SourceType.MANUAL,
                            overrideMask = (previousMeeting?.overrideMask ?: emptySet()) + setOf(MeetingField.TEACHER, MeetingField.ROOM, MeetingField.WEEKS),
                        )
                    }
                    timetableRepository.replaceCourseMeetings(course.copy(name = draft.name.trim(), color = draft.color), meetings,
                        location = draft.location.takeIf { it != occurrence.location },
                        overwriteConflicts = draft.overwriteConflicts,
                        locations = if (draft.location == occurrence.location) {
                            val bindings = timetableRepository.placeBindings(semesterId)
                            slots.mapNotNull { slot ->
                                bindings[slot.sourceMeetingId]?.let { binding ->
                                    CourseMeeting.stableId(course.id, slot.weekday, slot.periodStart, slot.periodEnd) to binding
                                }
                            }.toMap()
                        } else emptyMap())
                    refreshSchedule(semesterId)
                    snackbarHostState.showSnackbar("课程已保存")
                }.onFailure { error ->
                    snackbarHostState.showSnackbar(error.message ?: "课程保存失败")
                }
            }
        }

        LaunchedEffect(backgroundImageStore) {
            refreshBackgrounds()
        }

        val deleteCourse: (DemoOccurrence) -> Unit = { occurrence ->
            scope.launch {
                runCatching {
                    timetableRepository.deleteCourse(occurrence.course.id)
                    activeSemesterId?.let { refreshSchedule(it) }
                    snackbarHostState.showSnackbar("课程已删除")
                }.onFailure { error ->
                    snackbarHostState.showSnackbar(error.message ?: "课程删除失败")
                }
            }
        }

        val addCourse: (CourseEditDraft) -> Unit = { draft ->
            scope.launch {
                runCatching {
                    val semesterId = activeSemesterId ?: error("请先导入课表后再添加课程")
                    val courseId = "manual:$semesterId:${UUID.randomUUID()}"
                    val course = Course(
                        id = courseId,
                        semesterId = semesterId,
                        sourceType = SourceType.MANUAL,
                        sourceKey = courseId,
                        name = draft.name.trim(),
                        color = draft.color,
                    )
                    val meetings = (draft.weeklySlots.ifEmpty { listOf(cn.qztc.timetable.feature.timetable.WeeklySlotDraft(draft.weekday, draft.periodStart, draft.periodEnd)) }).map { slot ->
                        CourseMeeting(
                            id = CourseMeeting.stableId(courseId, slot.weekday, slot.periodStart, slot.periodEnd), courseId = courseId,
                            weekday = slot.weekday, periodStart = slot.periodStart, periodEnd = slot.periodEnd,
                            weekRule = WeekRule.of(slot.weeks.ifEmpty { draft.weeks }, "自定义周次"), teacher = draft.teacher.trim().ifEmpty { null }, room = draft.room.trim().ifEmpty { null },
                            sourceType = SourceType.MANUAL, overrideMask = setOf(MeetingField.TEACHER, MeetingField.ROOM, MeetingField.WEEKS),
                        )
                    }
                    timetableRepository.replaceCourseMeetings(course, meetings, draft.location, overwriteConflicts = draft.overwriteConflicts)
                    refreshSchedule(semesterId)
                    snackbarHostState.showSnackbar("课程已添加")
                }.onFailure { error ->
                    snackbarHostState.showSnackbar(error.message ?: "课程添加失败")
                }
            }
        }

        val saveActivity: (ActivityEditDraft, List<String>) -> Unit = { draft, removeMeetingIds ->
            scope.launch {
                runCatching {
                    val semesterId = activeSemesterId ?: error("请先导入课表后再添加活动")
                    val activity = ScheduleActivity(
                        id = draft.id?.takeIf { it.isNotBlank() } ?: "activity:$semesterId:${UUID.randomUUID()}",
                        semesterId = semesterId,
                        name = draft.name.trim(),
                        note = draft.note.trim(),
                        timeText = "",
                        kind = draft.kind,
                        color = draft.color,
                        weekday = draft.weekday.takeIf { draft.kind == ActivityKind.PERIOD },
                        periodStart = draft.periodStart.takeIf { draft.kind == ActivityKind.PERIOD },
                        periodEnd = draft.periodEnd.takeIf { draft.kind == ActivityKind.PERIOD },
                        weeks = if (draft.kind == ActivityKind.PERIOD) draft.weeks else emptyList(),
                        startDate = draft.startDate.takeIf { draft.kind == ActivityKind.DAY },
                        endDate = draft.endDate.takeIf { draft.kind == ActivityKind.DAY },
                        sessions = draft.sessions,
                    )
                    timetableRepository.saveActivityWithCoverage(activity, removeMeetingIds)
                    refreshSchedule(semesterId)
                    snackbarHostState.showSnackbar(
                        if (removeMeetingIds.isEmpty()) "活动已保存" else "活动已保存，冲突课次已暂时隐藏",
                    )
                }.onFailure { error ->
                    snackbarHostState.showSnackbar(error.message ?: "活动保存失败")
                }
            }
        }

        val deleteActivity: (String, Boolean) -> Unit = { activityId, restore ->
            scope.launch {
                runCatching {
                    if (activityId.startsWith("calendar-event:")) {
                        val eventId = activityId.substringAfterLast(':')
                        context.getSharedPreferences("calendar-inclusion", Context.MODE_PRIVATE)
                            .edit().putBoolean("event:$eventId", false).apply()
                    }
                    timetableRepository.deleteActivity(activityId, restore)
                    activeSemesterId?.let { refreshSchedule(it) }
                    snackbarHostState.showSnackbar(if (activityId.startsWith("calendar-event:")) "校历安排已从课表隐藏" else "活动已删除")
                }.onFailure { error ->
                    snackbarHostState.showSnackbar(error.message ?: "活动删除失败")
                }
            }
        }

        val destination = current ?: loadedPreferences?.startDestination() ?: TopDestination.Today
        val showingSecondaryPage = secondaryPage != SecondaryPage.NONE
        val glassSettings = LocalGlassSettings.current

        val openSecondaryPage: (SecondaryPage) -> Unit = { page ->
            if (destination == TopDestination.Mine && secondaryPage == SecondaryPage.NONE) {
                savedSettingsItemIndex = settingsListState.firstVisibleItemIndex
                savedSettingsItemOffset = settingsListState.firstVisibleItemScrollOffset
            }
            secondaryPage = page
        }
        val closeSecondaryPage = {
            if (destination == TopDestination.Mine) settingsRestorePending = true
            secondaryPage = SecondaryPage.NONE
        }
        LaunchedEffect(destination, secondaryPage, settingsRestorePending) {
            if (destination == TopDestination.Mine && secondaryPage == SecondaryPage.NONE && settingsRestorePending) {
                withFrameNanos { }
                settingsListState.scrollToItem(savedSettingsItemIndex, savedSettingsItemOffset)
                settingsRestorePending = false
            }
        }

        LaunchedEffect(loadedPreferences != null) {
            if (loadedPreferences == null || permissionHistory.getBoolean("requested",false)) return@LaunchedEffect
            val missing = buildList {
                if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationAllowed) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionHistory.edit().putBoolean("requested",true).apply()
            if (missing.isNotEmpty()) startupPermissions.launch(missing.toTypedArray())
        }

        BackHandler(enabled = showingSecondaryPage && secondaryPage != SecondaryPage.QNU_IMPORT) {
            when (secondaryPage) {
                SecondaryPage.COURSE_COLORS -> secondaryPage = SecondaryPage.PERSONALIZATION
                SecondaryPage.BACKGROUND_CROP -> cropImagePath?.let { path ->
                    backgroundImageStore.discardCandidate(path)
                    cropImagePath = null
                    secondaryPage = SecondaryPage.PERSONALIZATION
                } ?: run { secondaryPage = SecondaryPage.PERSONALIZATION }
                else -> closeSecondaryPage()
            }
        }
        BackHandler(enabled = !showingSecondaryPage && transientParentDestination != null) {
            current = transientParentDestination
            transientParentDestination = null
        }

        GlassBackdropHost(
            background = {
                PageBackground(
                    preferences = loadedPreferences,
                    darkTheme = darkTheme,
                    reduceMotion = glassSettings.reduceMotion || suspendVisualEffects,
                    importedImagePaths = importedBackgrounds.map { it.path },
                )
            },
            // The native campus WebView remains outside the backdrop source;
            // keep the wallpaper source active so the map page's glass top bar
            // matches the other pages without capturing Leaflet frames.
            suspendEffects = suspendVisualEffects ||
                (destination == TopDestination.Mine && secondaryPage == SecondaryPage.NONE) ||
                secondaryPage == SecondaryPage.CAMPUS_ALIASES,
            content = {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (!showingSecondaryPage && !coverBottomBar && !imeVisible) {
                        GlassDock(destination) { dest ->
                            if (dest == destination && secondaryPage == SecondaryPage.NONE) return@GlassDock
                            transientParentDestination = null
                            if (dest == TopDestination.Week) weekResetRequest += 1
                            if (dest == TopDestination.Today) {
                                demoScheduleState.selectedDay = demoScheduleState.todayWeekday
                            }
                            current = dest
                            secondaryPage = SecondaryPage.NONE
                        }
                    }
                },
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        // Let the background host remain visible behind pages;
                        // an opaque fill here hides the selected image/scene.
                        .background(Color.Transparent)
                        .padding(padding),
                ) {
                    if (loadedPreferences != null) {
                    val pageKey = PageKey(destination, if (showingSecondaryPage) secondaryPage else SecondaryPage.NONE)
                    val pageContent: @Composable (PageKey) -> Unit = { page ->
                        if (page.secondary != SecondaryPage.NONE) {
                            when (page.secondary) {
                            SecondaryPage.COURSE_COLORS -> CourseColorSettingsScreen(
                                presetColors = preferences.coursePresetColors,
                                onColorsChange = { values ->
                                    scope.launch { preferencesStore.setCoursePresetColors(values) }
                                },
                                courses = demoScheduleState.importedSchedule?.state?.courses.orEmpty().sortedBy { it.name },
                                onCourseColorChange = { courseId, value ->
                                    scope.launch {
                                        runCatching {
                                            val semesterId = activeSemesterId ?: error("请先选择学期")
                                            val course = timetableRepository.state(semesterId).courses.first { it.id == courseId }
                                            timetableRepository.updateCourseAndMeeting(course.copy(color = value), null, null)
                                            refreshSchedule(semesterId)
                                        }.onFailure { error -> snackbarHostState.showSnackbar(error.message ?: "课程颜色保存失败") }
                                    }
                                },
                                onBack = { secondaryPage = SecondaryPage.PERSONALIZATION },
                            )

                            SecondaryPage.DAY_DETAIL -> DayScheduleScreen(
                                reminderState = courseReminderState,
                                onSaveReminder = saveReminder,
                                scheduleState = demoScheduleState,
                                week = dayDetailWeek,
                                day = dayDetailDay,
                                coursePresetColors = preferences.coursePresetColors,
                                onSaveActivity = saveActivity,
                                onDeleteActivity = deleteActivity,
                                onBack = { closeSecondaryPage() },
                            )

                            SecondaryPage.REMINDER_SETTINGS -> ReminderSettingsScreen(
                                state = courseReminderState,
                                courses = demoScheduleState.importedSchedule?.state?.courses.orEmpty(),
                                semesterLabel = demoScheduleState.importedSchedule?.semester?.name ?: "尚未导入课表",
                                onBack = { closeSecondaryPage() },
                                onSaveCourseReminder = saveReminder,
                                testState = reminderTestState,
                                onTestReminder = { testReminderMode(preferences.reminderDisplayMode) },
                                onSystemReminderSettings = {
                                    retestReminderOnResume = true
                                    runCatching { context.startActivity(QztcNotifications.reminderSettingsIntent(context, preferences.reminderDisplayMode)) }
                                        .onFailure {
                                            retestReminderOnResume = false
                                            scope.launch { snackbarHostState.showSnackbar("无法打开系统设置，请在手机设置中开启课程表通知") }
                                        }
                                },
                                onReminderDisplayModeChange = { mode -> testReminderMode(mode, saveMode = true) },
                                onRemindersEnabledChange = { value ->
                                    if (value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !QztcNotifications.canPost(context)) {
                                        if (pendingReminderAction == null) {
                                            pendingReminderAction = ReminderPermissionAction()
                                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    } else {
                                        scope.launch { preferencesStore.setRemindersEnabled(value) }
                                    }
                                },
                                onReminderMinutesChange = { minutes ->
                                    scope.launch { preferencesStore.setReminderMinutes(minutes) }
                                },
                            )

                            SecondaryPage.PRIVACY_ABOUT -> PrivacyAboutScreen(
                                onBack = { closeSecondaryPage() },
                            )

                            SecondaryPage.AD_SKIP -> AdSkipScreen(
                                onBack = { closeSecondaryPage() },
                            )

                            SecondaryPage.PLUGIN_MARKET -> PluginMarketScreen(
                                onBack = { closeSecondaryPage() },
                                onManagePlugin = { openSecondaryPage(SecondaryPage.AD_SKIP) },
                            )

                            SecondaryPage.PERSONALIZATION -> PersonalizationScreen(
                                themeMode = preferences.themeMode,
                                palette = preferences.palette,
                                onPaletteChange = { palette -> scope.launch { preferencesStore.setPalette(palette) } },
                                backgroundCarouselEnabled = preferences.backgroundCarouselEnabled,
                                backgroundCarouselIntervalSeconds = preferences.backgroundCarouselIntervalSeconds,
                                personalizationScrollState = personalizationScrollState,
                                onThemeModeChange = { mode ->
                                    scope.launch { preferencesStore.setThemeMode(mode) }
                                },
                                glassQuality = preferences.glassQuality,
                                onGlassQualityChange = { quality ->
                                    scope.launch { preferencesStore.setGlassQuality(quality) }
                                },
                                functionalAlpha = preferences.functionalAlpha,
                                functionalBlur = preferences.functionalBlur,
                                contentAlpha = preferences.contentAlpha,
                                contentBlur = preferences.contentBlur,
                                widgetBackgroundAlpha = preferences.widgetBackgroundAlpha,
                                glassWidgetBackgroundAlpha = preferences.glassWidgetBackgroundAlpha,
                                onWidgetBackgroundAlphaChange = { value, glass ->
                                    scope.launch {
                                        preferencesStore.setWidgetBackgroundAlpha(value, glass)
                                        TodayWidgetProvider.requestUpdate(context)
                                    }
                                },
                                onFunctionalAlphaChange = { value ->
                                    scope.launch { preferencesStore.setFunctionalAlpha(value) }
                                },
                                onFunctionalBlurChange = { value ->
                                    scope.launch { preferencesStore.setFunctionalBlur(value) }
                                },
                                onContentAlphaChange = { value ->
                                    scope.launch { preferencesStore.setContentAlpha(value) }
                                },
                                onContentBlurChange = { value ->
                                    scope.launch { preferencesStore.setContentBlur(value) }
                                },
                                onResetFunctionalGlassParameters = {
                                    scope.launch { preferencesStore.resetFunctionalGlassParameters() }
                                },
                                onResetContentGlassParameters = {
                                    scope.launch { preferencesStore.resetContentGlassParameters() }
                                },
                                backgroundSource = preferences.backgroundSource,
                                backgroundMotion = preferences.backgroundMotion,
                                importedBackgrounds = importedBackgrounds,
                                selectedBackgroundPath = preferences.backgroundImagePath,
                                onBackgroundSourceChange = { source ->
                                    scope.launch {
                                        preferencesStore.setBackgroundSource(source)
                                        if (preferences.backgroundMotion == BackgroundMotion.OFF) {
                                            preferencesStore.setBackgroundMotion(BackgroundMotion.STATIC)
                                        }
                                    }
                                },
                                onBackgroundMotionChange = { motion ->
                                    scope.launch { preferencesStore.setBackgroundMotion(motion) }
                                },
                                onBackgroundCarouselEnabledChange = { enabled ->
                                    scope.launch { preferencesStore.setBackgroundCarouselEnabled(enabled) }
                                },
                                onBackgroundCarouselIntervalSecondsChange = { seconds ->
                                    scope.launch { preferencesStore.setBackgroundCarouselIntervalSeconds(seconds) }
                                },
                                onBackgroundImageSelect = { path ->
                                    scope.launch {
                                        preferencesStore.setBackgroundImage(
                                            path,
                                            preferences.backgroundPositionX,
                                            preferences.backgroundPositionY,
                                        )
                                    }
                                },
                                onBackgroundImageDelete = { path ->
                                    scope.launch {
                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                backgroundImageStore.deleteImportedImage(path)
                                            }
                                            preferencesStore.clearBackgroundImage(path)
                                        }.onSuccess {
                                            refreshBackgrounds()
                                            snackbarHostState.showSnackbar("背景图片已删除")
                                        }.onFailure { error ->
                                            snackbarHostState.showSnackbar(error.message ?: "无法删除背景图片")
                                        }
                                    }
                                },
                                onChooseBackgroundImage = {
                                    photoPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                                },
                                presetColors = preferences.coursePresetColors,
                                textPrimaryColor = preferences.textPrimaryColor,
                                textSecondaryColor = preferences.textSecondaryColor,
                                textTertiaryColor = preferences.textTertiaryColor,
                                onTextPrimaryColorChange = { color -> scope.launch { preferencesStore.setTextPrimaryColor(color) } },
                                onTextSecondaryColorChange = { color -> scope.launch { preferencesStore.setTextSecondaryColor(color) } },
                                onTextTertiaryColorChange = { color -> scope.launch { preferencesStore.setTextTertiaryColor(color) } },
                                onPresetColorsChange = { values ->
                                    scope.launch { preferencesStore.setCoursePresetColors(values) }
                                },
                                onEditCurrentCourseColors = { secondaryPage = SecondaryPage.COURSE_COLORS },
                                onBack = { closeSecondaryPage() },
                            )

                            SecondaryPage.SEMESTER_SCHEDULE -> {
                                // A loaded timetable is authoritative even if
                                // the surrounding settings state has not
                                // finished its first recomposition yet.
                                val semester = activeSemester
                                    ?: activeSemesterId?.let { id -> importedSemesters.firstOrNull { it.id == id } }
                                    ?: demoScheduleState.importedSchedule?.semester
                                    ?: importedSemesters.firstOrNull()
                                val periodScheme = activePeriodScheme
                                    ?: semester?.let { PeriodSchemeFactory.forSemester(it.id) }
                                if (semester != null && periodScheme != null) {
                                    SemesterScheduleSettingsScreen(
                                        semester = semester,
                                        showSemesterTools = activeSemesterId != null,
                                        onViewScheduleChanges = { upcomingChangesDialog = true },
                                        periodScheme = periodScheme,
                                        academicDays = demoScheduleState.importedSchedule?.academicDays.orEmpty(),
                                        onBack = { closeSecondaryPage() },
                                        onAcademicDaysChange = { updatedSemester, days ->
                                                run {
                                                    timetableRepository.saveCalendar(updatedSemester, days)
                                                    importedSemesters = timetableRepository.semestersWithinImportWindow()
                                                    refreshSchedule(updatedSemester.id)
                                                }
                                        },
                                        onCalendarEventsChange = { events ->
                                            for (target in timetableRepository.semesters()) {
                                                val matching=events.filter { event ->
                                                    val first=event.schoolYear.substringBefore('-').toIntOrNull()
                                                    val spring=event.semesterLabel=="第二学期"
                                                    val eventYear=first?.plus(if(spring)1 else 0)
                                                    eventYear==target.startDate.year && (if(spring) cn.qztc.timetable.core.domain.model.Term.SPRING else cn.qztc.timetable.core.domain.model.Term.AUTUMN)==target.term
                                                }
                                                matching.forEach { event ->
                                                    timetableRepository.upsertActivity(cn.qztc.timetable.core.domain.model.ScheduleActivity(
                                                        id="calendar-event:${target.id}:${event.id}",semesterId=target.id,name=event.name,
                                                        note=if(event.type==cn.qztc.timetable.feature.settings.SchoolCalendarEventType.SPORTS_MEETING)event.detail else "",
                                                        kind=cn.qztc.timetable.core.domain.model.ActivityKind.DAY,
                                                        startDate=event.startDate,endDate=event.endDate?:event.startDate))
                                                }
                                            }
                                            refreshSchedule(activeSemesterId ?: semester.id)
                                        },
                                        onCalendarEventRemove = { event ->
                                            context.getSharedPreferences("calendar-inclusion", Context.MODE_PRIVATE)
                                                .edit().putBoolean("event:${event.id}", false).apply()
                                            for(target in timetableRepository.semesters())
                                                timetableRepository.hideCalendarActivity("calendar-event:${target.id}:${event.id}")
                                            refreshSchedule(activeSemesterId ?: semester.id)
                                        },
                                        onTimelineDaysChange = { original, replacement ->
                                            fun key(day: cn.qztc.timetable.core.domain.calendar.AcademicDay)="${day.date}:${day.kind}:${day.name}"
                                            val oldKeys=original.map(::key).toSet()
                                            for(target in timetableRepository.semesters()) {
                                                val first=cn.qztc.timetable.core.domain.calendar.SemesterCalendar.dateOf(target,1,1)
                                                val last=cn.qztc.timetable.core.domain.calendar.SemesterCalendar.dateOf(target,target.totalWeeks,7)
                                                if((original+replacement).none { it.date in first..last })continue
                                                val previous=timetableRepository.academicDays(target.id)
                                                if(original.isNotEmpty() && previous.none { key(it) in oldKeys }) continue
                                                val next=previous.filterNot { key(it) in oldKeys } +
                                                    replacement.filter { it.date in first..last }.map { it.copy(semesterId=target.id,id=cn.qztc.timetable.core.domain.calendar.AcademicDay.idFor(target.id,it.date)) }
                                                timetableRepository.replaceAcademicDays(target.id,cn.qztc.timetable.core.domain.calendar.AcademicDayRanges.normalize(next))
                                            }
                                            refreshSchedule(activeSemesterId ?: semester.id)
                                        },
                                        onCalendarSemesterDetected = { detected, activate ->
                                            val target = ensureCalendarSemester(detected)
                                            if (activate && activeSemesterId != target.id) {
                                                refreshSchedule(target.id)
                                                val settings = timetableRepository.settings()
                                                timetableRepository.updateSettings(settings.copy(activeSemesterId = target.id))
                                            }
                                        },
                                        enrollmentYear = cn.qztc.timetable.feature.settings.CourseArrangementProfile.year(context),
                                        onImport = {
                                            importReturnPage = ImportReturnPage.COURSE_ARRANGEMENT
                                            secondaryPage = SecondaryPage.QNU_IMPORT
                                        },
                                        semesterOptions = importedSemesters,
                                        activities = demoScheduleState.activities(),
                                        importedCourseCount = demoScheduleState.importedSchedule?.state?.courses?.size ?: 0,
                                        importedTeachingWeeks = demoScheduleState.importedSchedule
                                            ?.takeIf { it.semester.id == semester.id }?.state?.importedTeachingWeeks,
                                        activityPresetColors = preferences.coursePresetColors,
                                        onSaveActivity = { activity ->
                                            timetableRepository.upsertActivity(activity)
                                            refreshSchedule(semester.id)
                                        },
                                        onDeleteActivity = { activityId ->
                                            timetableRepository.deleteActivity(activityId, restoreCourses = true)
                                            if (activityId.startsWith("calendar-event:")) {
                                                val eventId = activityId.substringAfterLast(':')
                                                context.getSharedPreferences("calendar-inclusion", Context.MODE_PRIVATE)
                                                    .edit().putBoolean("event:$eventId", false).apply()
                                            }
                                            refreshSchedule(semester.id)
                                        },
                                        onSelectSemester = { selected ->
                                            scope.launch {
                                                runCatching {
                                                    refreshSchedule(selected.id)
                                                    val settings = timetableRepository.settings()
                                                    timetableRepository.updateSettings(settings.copy(activeSemesterId = selected.id))
                                                }.onFailure { snackbarHostState.showSnackbar(it.message ?: "切换学期失败") }
                                            }
                                        },
                                        onSave = { updatedSemester, updatedScheme, days ->
                                            scope.launch {
                                                runCatching {
                                                    timetableRepository.upsertSemester(updatedSemester)
                                                    timetableRepository.upsertPeriodScheme(updatedScheme)
                                                    timetableRepository.replaceAcademicDays(updatedSemester.id, days)
                                                    importedSemesters = timetableRepository.semestersWithinImportWindow()
                                                    refreshSchedule(updatedSemester.id)
                                                    closeSecondaryPage()
                                                    snackbarHostState.showSnackbar("学期与校历已保存")
                                                }.onFailure { error ->
                                                    snackbarHostState.showSnackbar(error.message ?: "学期与校历保存失败")
                                                }
                                            }
                                        },
                                    )
                                } else {
                                    cn.qztc.timetable.feature.settings.CourseArrangementWelcome(
                                        onBack = { closeSecondaryPage() },
                                        onImport = { importReturnPage = ImportReturnPage.COURSE_ARRANGEMENT; secondaryPage = SecondaryPage.QNU_IMPORT },
                                    )
                                }
                            }

                            SecondaryPage.STORAGE -> cn.qztc.timetable.storage.StorageSettingsScreen(
                                activeBackground = preferences.backgroundImagePath,
                                onBack = { closeSecondaryPage() },
                                onFilesChanged = { refreshSnapshots(); refreshBackgrounds() },
                                snapshots = snapshots,
                                onShareSnapshot = { record -> snapshotStore.share(record) },
                                onSaveSnapshot = { record ->
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) { snapshotStore.saveToGallery(record) }
                                        snackbarHostState.showSnackbar(
                                            if (result.isSuccess) "已保存到相册" else result.exceptionOrNull()?.message ?: "保存到相册失败",
                                        )
                                    }
                                },
                                onDeleteSnapshot = { record -> snapshotStore.remove(record); refreshSnapshots() },
                            )

                            SecondaryPage.BACKGROUND_CROP -> cropImagePath?.let { path ->
                                BackgroundCropScreen(
                                    path = path,
                                    initialPosition = CropPosition(
                                        preferences.backgroundPositionX,
                                        preferences.backgroundPositionY,
                                    ),
                                    onCancel = {
                                        backgroundImageStore.discardCandidate(path)
                                        cropImagePath = null
                                        secondaryPage = SecondaryPage.PERSONALIZATION
                                    },
                                    onApply = { position ->
                                        scope.launch {
                                            runCatching {
                                                val stablePath = backgroundImageStore.finalizeCandidate(path)
                                                preferencesStore.setBackgroundImage(stablePath, position.x, position.y)
                                            }.onSuccess {
                                                refreshBackgrounds()
                                                cropImagePath = null
                                                secondaryPage = SecondaryPage.PERSONALIZATION
                                            }.onFailure { error ->
                                                snackbarHostState.showSnackbar(error.message ?: "无法保存背景图片")
                                            }
                                        }
                                    },
                                )
                            }

                            SecondaryPage.SNAPSHOTS -> SnapshotGalleryScreen(
                                records = snapshots,
                                syncRuns = syncRuns,
                                onBack = { closeSecondaryPage() },
                                onShare = { record -> snapshotStore.share(record) },
                                onSave = { record ->
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) { snapshotStore.saveToGallery(record) }
                                        snackbarHostState.showSnackbar(
                                            if (result.isSuccess) "已保存到相册" else result.exceptionOrNull()?.message ?: "保存到相册失败",
                                        )
                                    }
                                },
                                onDelete = { record ->
                                    snapshotStore.remove(record)
                                    refreshSnapshots()
                                },
                                hasSchedule = demoScheduleState.importedSchedule != null,
                                onExportJson = {
                                    demoScheduleState.importedSchedule?.let { schedule ->
                                        scope.launch {
                                            runCatching {
                                                withContext(Dispatchers.IO) { exportStore.writeJson(schedule) }
                                            }.onSuccess { file ->
                                                exportStore.share(file, "application/json")
                                            }.onFailure { error ->
                                                snackbarHostState.showSnackbar(error.message ?: "导出 JSON 失败")
                                            }
                                        }
                                    }
                                },
                                onExportIcs = {
                                    demoScheduleState.importedSchedule?.let { schedule ->
                                        scope.launch {
                                            runCatching {
                                                withContext(Dispatchers.IO) { exportStore.writeIcs(schedule) }
                                            }.onSuccess { file ->
                                                exportStore.share(file, "text/calendar")
                                            }.onFailure { error ->
                                                snackbarHostState.showSnackbar(error.message ?: "导出 ICS 失败")
                                            }
                                        }
                                    }
                                },
                            )

                            SecondaryPage.SYNC_CENTER -> {
                                LaunchedEffect(Unit) {
                                    secondaryPage = SecondaryPage.SEMESTER_SCHEDULE
                                    upcomingChangesDialog = true
                                }
                            }

                            SecondaryPage.QNU_IMPORT -> QnuImportScreen(
                                syncOnly = importReturnPage == ImportReturnPage.SYNC_CENTER,
                                savedCredentials = savedCredentials,
                                onSaveCredentials = { credentials ->
                                    runCatching { credentialStore.save(credentials) }
                                        .onSuccess {
                                            credentialRevision += 1
                                            scope.launch { snackbarHostState.showSnackbar("教务账号已加密保存，仅用于本页自动填充") }
                                        }
                                        .onFailure { error ->
                                            scope.launch {
                                                snackbarHostState.showSnackbar(error.message ?: "教务账号保存失败")
                                            }
                                        }
                                },
                                onClearCredentials = {
                                    credentialStore.clear()
                                    credentialRevision += 1
                                    scope.launch { snackbarHostState.showSnackbar("已清除保存的教务账号") }
                                },
                                onBack = {
                                    val returnPage = when (importReturnPage) {
                                        ImportReturnPage.SYNC_CENTER -> SecondaryPage.SEMESTER_SCHEDULE
                                        ImportReturnPage.COURSE_ARRANGEMENT -> SecondaryPage.SEMESTER_SCHEDULE
                                        ImportReturnPage.NONE -> SecondaryPage.NONE
                                    }
                                    if (returnPage == SecondaryPage.NONE) closeSecondaryPage() else secondaryPage = returnPage
                                    importReturnPage = ImportReturnPage.NONE
                                },
                                onSnapshotSaved = { refreshSnapshots() },
                                onImported = { candidate ->
                                    scope.launch {
                                        if (importReturnPage == ImportReturnPage.SYNC_CENTER) {
                                            runCatching {
                                                val target = requireNotNull(timetableRepository.semesters().maxWithOrNull(compareBy({ it.schoolYear }, { it.term.ordinal })))
                                                val hint = candidate.semesterHint
                                                require(!candidate.hasErrors && hint?.year == target.schoolYear && hint.term == target.term)
                                                val next = cn.qztc.timetable.core.domain.schedule.UpcomingScheduleChanges.merge(
                                                    target, timetableRepository.state(target.id), candidate.state,
                                                    timetableRepository.periodScheme(target.id)?.periods.orEmpty(), java.time.LocalDateTime.now())
                                                cn.qztc.timetable.sync.ScheduleChangeInbox(context, target.id).receive(next.exceptions)
                                                secondaryPage = SecondaryPage.SYNC_CENTER
                                                importReturnPage = ImportReturnPage.NONE
                                            }.onFailure { snackbarHostState.showSnackbar("未读到当前学期完整的停调课记录，原课表已保留") }
                                            return@launch
                                        }
                                        runCatching {
                                            // Capture the launch origin before the import coroutine
                                            // mutates navigation state. A normal first import must
                                            // never inherit the sync-center destination.
                                            val returnPage = importReturnPage
                                            val syncOnlyImport = returnPage == ImportReturnPage.SYNC_CENTER
                                            val inferred = QnuSemesterFactory.from(candidate)
                                            val existing = timetableRepository.semester(inferred.id)
                                            val calendar = cn.qztc.timetable.feature.settings.SchoolCalendarApi.cached(context,
                                                cn.qztc.timetable.feature.settings.CourseArrangementProfile.schoolYear(), inferred.id)
                                            val current = calendar?.semesterFor(LocalDate.now())
                                            val start = if (current?.term == inferred.term && inferred.schoolYear == LocalDate.now().year)
                                                calendar?.let { cn.qztc.timetable.feature.settings.CourseArrangementProfile.teachingStart(it,
                                                    cn.qztc.timetable.feature.settings.CourseArrangementProfile.year(context)) } else null
                                            val semester = inferred.copy(startDate = existing?.startDate ?: start ?: inferred.startDate)
                                            timetableRepository.upsertSemester(semester)
                                            val scopedCandidate = if(syncOnlyImport) candidate.copy(state = cn.qztc.timetable.core.domain.schedule.UpcomingScheduleChanges.merge(
                                                semester,timetableRepository.state(semester.id),candidate.state,
                                                timetableRepository.periodScheme(semester.id)?.periods.orEmpty(),java.time.LocalDateTime.now()).filterBySource(SourceType.QNU_EDU)) else candidate
                                            val outcome = timetableRepository.importCandidate(semester, scopedCandidate)
                                            if (outcome is ImportOutcome.Pending) {
                                                QztcNotifications.notifyScheduleChange(
                                                    context,
                                                    outcome.diff.addedCount + outcome.diff.changedCount + outcome.diff.removedCount,
                                                    outcome.changeSetId.hashCode(),
                                                )
                                            }
                                            if (outcome !is ImportOutcome.Failed) {
                                                val imported = timetableRepository.importedSchedule(semester)
                                                if (imported.state.courses.isNotEmpty()) {
                                                    activeSemesterId = semester.id
                                                    demoScheduleState.loadImported(imported)
                                                    activeSemester = semester
                                                    activePeriodScheme = timetableRepository.periodScheme(semester.id)
                                                        ?: cn.qztc.timetable.core.domain.model.PeriodSchemeFactory.forSemester(semester.id)
                                                    importedSemesters = timetableRepository.semestersWithinImportWindow()
                                                    val settings = timetableRepository.settings()
                                                    timetableRepository.updateSettings(
                                                        settings.copy(activeSemesterId = semester.id),
                                                    )
                                                    refreshSyncState(semester.id)
                                                }
                                            }
                                            val nextPage = when (returnPage) {
                                                ImportReturnPage.COURSE_ARRANGEMENT -> SecondaryPage.SEMESTER_SCHEDULE
                                                ImportReturnPage.SYNC_CENTER -> if (syncOnlyImport) SecondaryPage.SYNC_CENTER else SecondaryPage.NONE
                                                ImportReturnPage.NONE -> SecondaryPage.NONE
                                            }
                                            if (nextPage == SecondaryPage.NONE) closeSecondaryPage() else secondaryPage = nextPage
                                            importReturnPage = ImportReturnPage.NONE
                                            val message = when (outcome) {
                                                is ImportOutcome.Applied -> "已导入 ${candidate.state.courses.size} 门课程"
                                                is ImportOutcome.NoChange -> "课表没有变化"
                                                is ImportOutcome.Pending -> "发现课表变化，原课表已保留"
                                                is ImportOutcome.Failed -> "课表解析失败，原课表未改变"
                                            }
                                            val detailWarning=candidate.diagnostics.filter { it.code in setOf("COURSE_DETAILS_INCOMPLETE","CANCEL_SCOPE_UNCONFIRMED") }.map { it.message }.distinct().joinToString("；").takeIf { it.isNotBlank() }
                                            snackbarHostState.showSnackbar(listOfNotNull(message,detailWarning).joinToString("；"))
                                        }.onFailure { error ->
                                            snackbarHostState.showSnackbar(error.message ?: "课表导入失败，原课表未改变")
                                        }
                                    }
                                },
                            )

                            SecondaryPage.CAMPUS_ALIASES, SecondaryPage.CAMPUS_MAP -> CampusRouteScreen(
                                rawDestination = "",
                                initialMode = if (secondaryPage == SecondaryPage.CAMPUS_ALIASES) "edit" else "view",
                                onBack = { closeSecondaryPage() },
                                onCoverBottomBar = { coverBottomBar = it },
                            )

                            SecondaryPage.NONE -> Unit
                            }
                        } else {
                            when (page.destination) {
                            TopDestination.Today -> TodayScreen(
                                scheduleState = demoScheduleState,
                                coursePresetColors = preferences.coursePresetColors,
                                reminderState = courseReminderState,
                                onSaveReminder = saveReminder,
                                onSaveCourseEdit = saveCourseEdit,
                                onDeleteCourse = deleteCourse,
                                onSaveActivity = saveActivity,
                                onDeleteActivity = deleteActivity,
                                onCoverBottomBar = { coverBottomBar = it },
                            )
                            TopDestination.Week -> WeekScreen(
                                reminderState = courseReminderState,
                                onSaveReminder = saveReminder,
                                scheduleState = demoScheduleState,
                                coursePresetColors = preferences.coursePresetColors,
                                onSaveCourseEdit = saveCourseEdit,
                                onDeleteCourse = deleteCourse,
                                onAddCourse = addCourse,
                                onSaveActivity = saveActivity,
                                onDeleteActivity = deleteActivity,
                                onSaveAcademicDays = { updated ->
                                    demoScheduleState.importedSchedule?.semester?.id?.let { semesterId ->
                                        scope.launch {
                                            timetableRepository.replaceAcademicDays(
                                                semesterId,
                                                cn.qztc.timetable.core.domain.calendar.AcademicDayRanges.normalize(updated),
                                            )
                                            refreshSchedule(semesterId)
                                        }
                                    }
                                },
                                resetToCurrentWeekRequest = weekResetRequest,
                                initialWeek = demoScheduleState.currentWeek,
                                weekendDisplayMode = preferences.weekendDisplayMode,
                                fontSizeMode = preferences.fontSizeMode,
                                fontScale = preferences.fontScale,
                                onVisualTransition = pauseVisualEffects,
                                onCoverBottomBar = { coverBottomBar = it },
                                onOpenDay = { selectedWeek, day ->
                                    if (
                                        demoScheduleState.isCurrentDateInImportedSemester &&
                                        selectedWeek == demoScheduleState.currentWeek &&
                                        day == demoScheduleState.todayWeekday
                                    ) {
                                        demoScheduleState.selectedDay = day
                                        transientParentDestination = TopDestination.Week
                                        current = TopDestination.Today
                                    } else {
                                        dayDetailWeek = selectedWeek
                                        dayDetailDay = day
                                        secondaryPage = SecondaryPage.DAY_DETAIL
                                    }
                                },
                            )
                            TopDestination.Mine -> MineScreen(
                                listState = settingsListState,
                                state = MineUiState(
                                    homePreference = preferences.homePreference,
                                    themeMode = preferences.themeMode,
                                    syncEnabled = preferences.syncEnabled,
                                    syncIntervalHours = preferences.syncIntervalHours,
                                    remindersEnabled = preferences.remindersEnabled && notificationAllowed,
                                    reminderMinutes = preferences.reminderMinutes,
                                    reminderDisplayMode = preferences.reminderDisplayMode,
                                    fontSizeMode = preferences.fontSizeMode,
                                    fontScale = preferences.fontScale,
                                    weekendDisplayMode = preferences.weekendDisplayMode,
                                    pendingChanges = changeSets.count { it.status == ChangeSetStatus.PENDING },
                                    backgroundLabel = when {
                                        preferences.backgroundMotion == BackgroundMotion.OFF -> "已关闭"
                                        preferences.backgroundSource == BackgroundSource.IMAGE -> "自定义图片"
                                        else -> "内置场景"
                                    },
                                    eduAccountConfigured = savedCredentials != null,
                                    activeSemesterId = activeSemesterId,
                                    activeSemesterLabel = importedSemesters
                                        .firstOrNull { it.id == activeSemesterId }?.name ?: "尚未导入",
                                    activeSemesterStartDateLabel = importedSemesters
                                        .firstOrNull { it.id == activeSemesterId }
                                        ?.startDate
                                        ?.let { "${it.year}年${it.monthValue}月${it.dayOfMonth}日" }
                                        ?: "未设置",
                                    activeSemester = activeSemester,
                                    periodScheme = activePeriodScheme,
                                    semesters = importedSemesters.map { MineSemesterOption(it.id, it.name) },
                                    updateLabel = when (val state = updateState) {
                                        AppUpdateState.Checking -> "正在检查"
                                        is AppUpdateState.Available -> when {
                                            state.clearing -> "正在清除更新文件"
                                            state.downloading -> "下载中 ${state.downloadedBytes * 100 / state.manifest.sizeBytes.coerceAtLeast(1)}%"
                                            state.file != null -> "已下载，点击安装"
                                            else -> "发现 ${state.manifest.latestVersionName}"
                                        }
                                        is AppUpdateState.Message -> state.title
                                        null -> "检查最新版本"
                                    },
                                ),
                                onHomePreferenceChange = { value ->
                                    scope.launch { preferencesStore.setHomePreference(value) }
                                },
                                onWeekendDisplayModeChange = { value ->
                                    scope.launch { preferencesStore.setWeekendDisplayMode(value) }
                                },
                                onPersonalizationClick = {
                                    openSecondaryPage(SecondaryPage.PERSONALIZATION)
                                },
                                onSyncCenterClick = {
                                    scope.launch {
                                        refreshSyncState()
                                        openSecondaryPage(SecondaryPage.SYNC_CENTER)
                                    }
                                },
                                onReminderSettingsClick = { openSecondaryPage(SecondaryPage.REMINDER_SETTINGS) },
                                onFontSizeModeChange = { mode ->
                                    scope.launch { preferencesStore.setFontSizeMode(mode) }
                                },
                                onFontScaleChange = { scale ->
                                    scope.launch { preferencesStore.setFontScale(scale) }
                                },
                                onEduImportClick = {
                                    importReturnPage = ImportReturnPage.NONE
                                    openSecondaryPage(SecondaryPage.QNU_IMPORT)
                                },
                                onCampusAliasClick = {
                                    openSecondaryPage(SecondaryPage.CAMPUS_ALIASES)
                                },
                                onCampusMapClick = { openSecondaryPage(SecondaryPage.CAMPUS_MAP) },
                                onStartDateClick = {
                                    openSecondaryPage(SecondaryPage.SEMESTER_SCHEDULE)
                                },
                                onSemesterSelect = { semesterId ->
                                    scope.launch {
                                        runCatching {
                                            val semester = checkNotNull(timetableRepository.semester(semesterId))
                                            refreshSchedule(semester.id)
                                            val settings = timetableRepository.settings()
                                            timetableRepository.updateSettings(
                                                settings.copy(activeSemesterId = semester.id),
                                            )
                                            snackbarHostState.showSnackbar("已切换到 ${semester.name}")
                                        }.onFailure { error ->
                                            snackbarHostState.showSnackbar(error.message ?: "切换学期失败")
                                        }
                                    }
                                },
                                onDataClick = {
                                    refreshSnapshots()
                                    openSecondaryPage(SecondaryPage.SNAPSHOTS)
                                },
                                onStorageClick = { refreshSnapshots(); openSecondaryPage(SecondaryPage.STORAGE) },
                                onAdSkipClick = { openSecondaryPage(SecondaryPage.AD_SKIP) },
                                onPluginMarketClick = { openSecondaryPage(SecondaryPage.PLUGIN_MARKET) },
                                onPrivacyAboutClick = {
                                    openSecondaryPage(SecondaryPage.PRIVACY_ABOUT)
                                },
                                onCheckUpdateClick = { checkForUpdates() },
                            )
                            }
                        }
                    }
                    // Native WebViews must draw directly in the host. A cached
                    // transition layer can stop presenting their new frames.
                    if (pageKey.secondary in setOf(SecondaryPage.CAMPUS_MAP, SecondaryPage.CAMPUS_ALIASES, SecondaryPage.QNU_IMPORT)) {
                        pageContent(pageKey)
                    } else {
                        AnimatedContent(
                            targetState = pageKey,
                            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(160)) },
                            label = "page-content-transition",
                            modifier = Modifier.fillMaxSize(),
                        ) { pageContent(it) }
                    }
                    }
                }
            }
        }
        )
        if (upcomingChangesDialog) {
            val semester = activeSemester ?: demoScheduleState.importedSchedule?.semester
            if (semester != null) {
                UpcomingChangesDialog(
                    semester = semester,
                    savedCredentials = savedCredentials,
                    repository = timetableRepository,
                    onDismiss = { upcomingChangesDialog = false },
                    onLogin = {
                        upcomingChangesDialog = false
                        importReturnPage = ImportReturnPage.SYNC_CENTER
                        secondaryPage = SecondaryPage.QNU_IMPORT
                    },
                    onApplied = { refreshSchedule(semester.id) },
                )
            }
        }
        updateState?.takeIf { updateDialogVisible }?.let { state ->
            when (state) {
                AppUpdateState.Checking -> AlertDialog(
                    onDismissRequest = {},
                    title = { Text("检查更新") },
                    text = { Text("正在检查最新版本…") },
                    confirmButton = {},
                )
                is AppUpdateState.Message -> AlertDialog(
                    onDismissRequest = { updateState = null },
                    title = { Text(state.title) },
                    text = { Text(state.detail) },
                    confirmButton = {
                        TextButton(onClick = { updateState = null }) { Text("知道了") }
                    },
                )
                is AppUpdateState.Available -> AppUpdateDialog(
                    manifest = state.manifest, currentVersion = BuildConfig.VERSION_NAME,
                    downloading = state.downloading, downloadedBytes = state.downloadedBytes,
                    ready = state.file != null, clearing = state.clearing,
                    onHide = { updateDialogVisible = false },
                    onDownload = { startUpdateDownload(state.manifest) },
                    onClear = { clearUpdateDownload() },
                    onInstall = {
                        state.file?.let { file ->
                            runCatching { AppUpdateClient.install(context, file) }
                                .onSuccess { updateDialogVisible = false }
                                .onFailure { error -> updateState = AppUpdateState.Message("无法打开安装器", error.message ?: "请稍后重试") }
                        }
                    },
                )
            }
        }
    }
}

private enum class SecondaryPage {
    REMINDER_SETTINGS,
    STORAGE,
    AD_SKIP,
    PLUGIN_MARKET,
    NONE,
    COURSE_COLORS,
    DAY_DETAIL,
    PRIVACY_ABOUT,
    PERSONALIZATION,
    BACKGROUND_CROP,
    SYNC_CENTER,
    SNAPSHOTS,
    QNU_IMPORT,
    CAMPUS_ALIASES,
    CAMPUS_MAP,
    SEMESTER_SCHEDULE,
}

private enum class ImportReturnPage {
    COURSE_ARRANGEMENT,
    NONE,
    SYNC_CENTER,
}

private suspend fun TimetableRepository.importedSchedule(semester: Semester): ImportedSchedule =
    ImportedSchedule(
        semester = semester,
        periods = periodScheme(semester.id)?.periods ?: QnuPeriodDefaults.periods(),
        state = state(semester.id),
        activities = activities(semester.id),
        academicDays = academicDays(semester.id),
        placeBindings = placeBindings(semester.id),
    )

private suspend fun TimetableRepository.semestersWithinImportWindow(): List<Semester> {
    val currentYear = LocalDate.now().year
    return semesters()
        .filter { it.schoolYear in (currentYear - 5)..(currentYear + 1) }
}

private data class PageKey(
    val destination: TopDestination,
    val secondary: SecondaryPage,
)

@Composable
private fun GlassDock(selected: TopDestination, onSelect: (TopDestination) -> Unit) {
    val colors = LocalQztcColors.current
    GlassSurface(
        role = GlassRole.FUNCTIONAL,
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        pressGlint = false,
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .height(52.dp),
        ) {
            val itemWidth = maxWidth / TopDestination.entries.size
            val selectedPosition by animateFloatAsState(
                targetValue = selected.ordinal.toFloat(),
                animationSpec = tween(120, easing = MotionSpec.DockPill),
                label = "dock-pill",
            )
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            x = (itemWidth * selectedPosition + 6.dp).roundToPx(),
                            y = 4.dp.roundToPx(),
                        )
                    }
                    .size(itemWidth - 12.dp, 44.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.dockSelected)
                    .border(1.dp, colors.dockSelectedBorder, RoundedCornerShape(16.dp)),
            )
            Row(Modifier.fillMaxSize()) {
                TopDestination.entries.forEach { destination ->
                    val interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onSelect(destination) },
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            destination.icon,
                            contentDescription = destination.label,
                            tint = if (destination == selected) colors.dockSelectedInk else colors.mut,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            destination.label,
                            style = QztcTypography.meta,
                            color = if (destination == selected) colors.dockSelectedInk else colors.mut,
                        )
                    }
                }
            }
        }
    }
}
