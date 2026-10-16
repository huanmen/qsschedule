package cn.qztc.timetable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.clearAndSetSemantics

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
import androidx.compose.material.icons.outlined.ViewWeek
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import cn.qztc.timetable.feature.settings.toCalendarActivity
import cn.qztc.timetable.feature.timetable.CourseEditDraft
import cn.qztc.timetable.feature.timetable.ActivityEditDraft
import cn.qztc.timetable.feature.timetable.toEditDraft
import cn.qztc.timetable.feature.timetable.toScheduleActivity
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
import cn.qztc.timetable.education.StudyScreen
import cn.qztc.timetable.education.AcademicPortalScreen
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
import cn.qztc.timetable.announcement.AnnouncementStore
import cn.qztc.timetable.announcement.AnnouncementScreen
import cn.qztc.timetable.announcement.ImportantAnnouncementDialog
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

private data class ReminderPermissionAction(val minutes: Int? = null,
    val dailyAgenda: cn.qztc.timetable.core.domain.model.DailyAgendaSettings? = null)

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
    Week("周课表", Icons.Outlined.ViewWeek),
    Calendar("日历", Icons.Outlined.CalendarMonth),
    Mine("我的", Icons.Outlined.Person),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QztcApp(openTodayOnStart: Boolean = false, openHomeworkOnStart: Boolean = false, openCalendarOnStart: String? = null,openAdSkipOnStart:Boolean=false,
    launchRequestId: Int = 0, onReadyToDraw: () -> Unit = {}) {
    val context = LocalContext.current
    val academicPluginInstalled = cn.qztc.timetable.plugin.rememberAcademicRecordsInstalled()
    val preferencesStore = remember { AppPreferencesStore(context.qztcDataStore) }
    val credentialStore = remember(context) { QnuCredentialStore(context) }
    val timetableDatabase = remember(context) { TimetableDatabase.build(context) }
    val timetableRepository = remember(timetableDatabase) { TimetableRepository(timetableDatabase) }
    var calendarMemos by remember { mutableStateOf<List<ScheduleActivity>>(emptyList()) }
    var calendarMemosLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(timetableRepository) {
        calendarMemos = timetableRepository.activities("calendar")
        calendarMemosLoaded = true
    }
    LaunchedEffect(calendarMemos, calendarMemosLoaded) {
        if (calendarMemosLoaded) cn.qztc.timetable.reminder.ActivityReminderScheduler.update(context, calendarMemos)
    }
    val renderResources = remember(context) { (context.applicationContext as QztcApplication).renderResources }
    val preparedAppearance by renderResources.appearance.collectAsStateWithLifecycle()
    val loadedPreferences = preparedAppearance?.preferences
    // Do not compose a default theme, dock or wallpaper before the persisted appearance.
    val preferences = loadedPreferences ?: return
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
        LaunchedEffect(preferences.themeMode, preferences.palette, darkTheme) {
            (context as? android.app.Activity)?.let { activity ->
                activity.window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(
                    colors.appBg.toArgb()))
                if (Build.VERSION.SDK_INT >= 31) activity.splashScreen.setSplashScreenTheme(
                    appLaunchTheme(preferences.themeMode, preferences.palette))
            }
        }
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        val courseReminderState = CourseReminderUiState(
            enabled = preferences.remindersEnabled && QztcNotifications.canPost(context),
            minutes = preferences.reminderMinutes,
            displayMode = preferences.reminderDisplayMode,
            overrides = preferences.courseReminderOverrides,
            messages = preferences.courseReminderMessages,
            remindOnHolidays = preferences.remindOnHolidays,
        )
        val saveReminder: (String, Int?, String?) -> Unit = { courseId, minutes, message ->
            scope.launch { preferencesStore.setCourseReminder(courseId, minutes, message) }
        }
        val demoScheduleState = remember { DemoScheduleState() }
        var current by rememberSaveable { mutableStateOf<TopDestination?>(null) }
        var localScheduleLoaded by remember { mutableStateOf(false) }
        var calendarContentReady by remember { mutableStateOf(false) }
        var firstFrameReported by remember { mutableStateOf(false) }
        var calendarVisited by remember { mutableStateOf(false) }
        val retainedTimetablePages = remember { androidx.compose.runtime.mutableStateListOf<TopDestination>() }
        // Prepare the local calendar after the first frame, outside the navigation tap.
        LaunchedEffect(loadedPreferences != null) {
            if (loadedPreferences != null) {
                withFrameNanos { }
                withFrameNanos { }
                calendarVisited = true
            }
        }
        var transientParentDestination by rememberSaveable { mutableStateOf<TopDestination?>(null) }
        var suspendVisualEffects by remember { mutableStateOf(false) }
        var scrollingVisualEffects by remember { mutableStateOf(false) }
        val lastVisualScrollAt = remember { longArrayOf(0L) }
        val visualScrollObserver = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (available != Offset.Zero) {
                        lastVisualScrollAt[0] = android.os.SystemClock.uptimeMillis()
                        scrollingVisualEffects = true
                    }
                    return Offset.Zero
                }
            }
        }
        LaunchedEffect(scrollingVisualEffects) {
            if (scrollingVisualEffects) {
                do { delay(180) } while (android.os.SystemClock.uptimeMillis() - lastVisualScrollAt[0] < 180)
                scrollingVisualEffects = false
            }
        }
        var visualTransitionToken by remember { mutableIntStateOf(0) }
        var weekResetRequest by rememberSaveable { mutableIntStateOf(0) }
        val settingsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
        val personalizationScrollState = rememberScrollState()
        var settingsRestorePending by rememberSaveable { mutableStateOf(false) }
        var savedSettingsItemIndex by rememberSaveable { mutableIntStateOf(0) }
        var savedSettingsItemOffset by rememberSaveable { mutableIntStateOf(0) }
        var secondaryPage by rememberSaveable { mutableStateOf(SecondaryPage.NONE) }
        var studyInitialTab by rememberSaveable { mutableIntStateOf(0) }
        val pageStates = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
        var secondaryParents by rememberSaveable { mutableStateOf(emptyList<String>()) }
        val announcementStore = remember(context) { AnnouncementStore(context) }
        val announcementRevision by announcementStore.revision.collectAsStateWithLifecycle()
        val announcementItems = remember(announcementRevision) { announcementStore.items() }
        val importantAnnouncement = remember(announcementRevision) { announcementStore.important() }
        LaunchedEffect(announcementStore) { announcementStore.refresh() }
        if (importantAnnouncement != null && secondaryPage == SecondaryPage.NONE && current != null) {
            ImportantAnnouncementDialog(importantAnnouncement,
                onDismiss = { announcementStore.dismiss(importantAnnouncement) },
                onOpen = { announcementStore.dismiss(importantAnnouncement); secondaryPage = SecondaryPage.ANNOUNCEMENTS })
        }
        var upcomingChangesDialog by rememberSaveable { mutableStateOf(false) }
        var upcomingChangesAlreadyChecked by rememberSaveable { mutableStateOf(false) }
        var importReturnPage by rememberSaveable { mutableStateOf(ImportReturnPage.NONE) }
        var importCampusDialog by remember { mutableStateOf(false) }
        var homeworkCampusDialog by remember { mutableStateOf(false) }
        var importCampusChecked by remember { mutableStateOf(false) }
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
        var calendarOpenDate by remember { mutableStateOf<String?>(null) }
        var coverBottomBar by remember { mutableStateOf(false) }
        val imeVisible = WindowInsets.isImeVisible
        val savedCredentials = remember(credentialStore, credentialRevision) { credentialStore.load() }
        var homeworkLaunchHandled by remember(launchRequestId) { mutableStateOf(false) }
        var todayLaunchHandled by remember(launchRequestId) { mutableStateOf(false) }
        var calendarLaunchHandled by remember(launchRequestId) { mutableStateOf(false) }
        LaunchedEffect(launchRequestId, openHomeworkOnStart, current) {
            if (openHomeworkOnStart && current != null && !homeworkLaunchHandled) {
                homeworkLaunchHandled = true
                current = TopDestination.Mine; secondaryPage = SecondaryPage.HOMEWORK
            }
        }
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
                    if (action.dailyAgenda != null) {
                        preferencesStore.setDailyAgenda(action.dailyAgenda.copy(enabled = granted))
                    } else if (granted) {
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
            scope.launch { announcementStore.refresh() }
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
            val savedDays = timetableRepository.academicDays(semester.id)
            val savedActivities = timetableRepository.activities(semester.id, includeHidden = true)
            val defaults = cn.qztc.timetable.feature.settings.CalendarScheduleDefaults.cached(context, semester, savedDays, savedActivities)
            if (defaults.days.isNotEmpty() || defaults.removedDayIds.isNotEmpty()) timetableRepository.replaceAcademicDays(semester.id, savedDays.filterNot { saved -> saved.id in defaults.removedDayIds || defaults.days.any { it.date == saved.date } } + defaults.days)
            defaults.activities.forEach { timetableRepository.upsertActivity(it) }
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

        LaunchedEffect(activeSemesterId) {
            val id = activeSemesterId ?: return@LaunchedEffect
            val semester = timetableRepository.semester(id) ?: return@LaunchedEffect
            cn.qztc.timetable.feature.settings.CalendarScheduleDefaults.refreshPublicCache(context, semester)
            if (activeSemesterId == id) refreshSchedule(id)
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
            try {
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
            } finally {
                localScheduleLoaded = true
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
        LaunchedEffect(savedCredentials?.username) {
            val name = savedCredentials?.username.orEmpty().trim()
            val account = java.security.MessageDigest.getInstance("SHA-256").digest(name.toByteArray()).joinToString("") { "%02x".format(it) }
            cn.qztc.timetable.education.HomeworkReminderScheduler.activate(context, account)
        }

        LaunchedEffect(preferences.dailyAgenda, loadedPreferences != null) {
            if (loadedPreferences != null) cn.qztc.timetable.reminder.DailyAgendaScheduler.reschedule(context, preferences.dailyAgenda)
        }

        LaunchedEffect(
            preferences.remindersEnabled,
            preferences.reminderMinutes,
            preferences.reminderDisplayMode,
            preferences.courseReminderOverrides,
            preferences.courseReminderMessages,
            preferences.remindOnHolidays,
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
                remindOnHolidays = preferences.remindOnHolidays,
            )
        }

        LaunchedEffect(launchRequestId, loadedPreferences, openTodayOnStart) {
            val loaded = loadedPreferences
            if (openTodayOnStart && loaded != null && !todayLaunchHandled) {
                todayLaunchHandled = true; current = TopDestination.Today; secondaryPage = SecondaryPage.NONE
            }
            else if (current == null && loaded != null) current = loaded.startDestination()
        }
        var adSkipLaunchHandled by remember(launchRequestId) {mutableStateOf(false)}
        LaunchedEffect(launchRequestId,openAdSkipOnStart,loadedPreferences) {
            if(openAdSkipOnStart&&loadedPreferences!=null&&!adSkipLaunchHandled) {
                adSkipLaunchHandled=true
                current=TopDestination.Mine
                secondaryPage=SecondaryPage.AD_SKIP
            }
        }
        LaunchedEffect(launchRequestId,openCalendarOnStart, loadedPreferences) {
            if (openCalendarOnStart != null && loadedPreferences != null && !calendarLaunchHandled && runCatching {LocalDate.parse(openCalendarOnStart)}.isSuccess) {
                calendarLaunchHandled = true
                calendarOpenDate = openCalendarOnStart
                current = TopDestination.Calendar; secondaryPage = SecondaryPage.NONE
            }
        }

        LaunchedEffect(current) {
            when (current) {
                TopDestination.Today -> preferencesStore.setLastDestination(HomeView.TODAY)
                TopDestination.Week -> preferencesStore.setLastDestination(HomeView.WEEK)
                TopDestination.Calendar -> preferencesStore.setLastDestination(HomeView.CALENDAR)
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
                    val semesterId = if (draft.memoTime != null) "calendar" else activeSemesterId ?: "calendar"
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
                        memoTime = draft.memoTime,
                        calendarTimetable = false,
                    )
                    timetableRepository.saveActivityWithCoverage(activity, removeMeetingIds)
                    calendarMemos = timetableRepository.activities("calendar")
                    activeSemesterId?.let { refreshSchedule(it) }
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
                        val semesterId = activeSemesterId ?: error("当前学期不可用")
                        val eventId = activityId.removePrefix("calendar-event:$semesterId:")
                        context.getSharedPreferences("calendar-inclusion", Context.MODE_PRIVATE)
                            .edit().putBoolean("event:$eventId", false).apply()
                    }
                    timetableRepository.deleteActivity(activityId, restore)
                    calendarMemos = timetableRepository.activities("calendar")
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
            if (secondaryPage != SecondaryPage.NONE && secondaryPage != page) secondaryParents = secondaryParents + secondaryPage.name
            if (destination == TopDestination.Mine && secondaryPage == SecondaryPage.NONE) {
                savedSettingsItemIndex = settingsListState.firstVisibleItemIndex
                savedSettingsItemOffset = settingsListState.firstVisibleItemScrollOffset
            }
            secondaryPage = page
        }
        val closeSecondaryPage = {
            if (destination == TopDestination.Mine) settingsRestorePending = true
            secondaryPage = secondaryParents.lastOrNull()?.let(SecondaryPage::valueOf) ?: SecondaryPage.NONE
            secondaryParents = secondaryParents.dropLast(1)
        }
        LaunchedEffect(destination, secondaryPage, settingsRestorePending) {
            if (destination == TopDestination.Mine && secondaryPage == SecondaryPage.NONE && settingsRestorePending) {
                withFrameNanos { }
                settingsListState.scrollToItem(savedSettingsItemIndex, savedSettingsItemOffset)
                settingsRestorePending = false
            }
        }

        LaunchedEffect(firstFrameReported) {
            if (!firstFrameReported || permissionHistory.getBoolean("requested",false)) return@LaunchedEffect
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
                SecondaryPage.SEMESTER_SCHEDULE -> closeSecondaryPage()
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

        var backdropColor by remember { mutableStateOf<Color?>(null) }
        val initialPageNeedsWebFrame = (destination == TopDestination.Calendar && !showingSecondaryPage) ||
            secondaryPage == SecondaryPage.ACADEMIC_CALENDAR
        LaunchedEffect(localScheduleLoaded, calendarMemosLoaded, backdropColor, initialPageNeedsWebFrame, calendarContentReady) {
            if (!firstFrameReported && localScheduleLoaded && calendarMemosLoaded && backdropColor != null &&
                (!initialPageNeedsWebFrame || calendarContentReady)) {
                firstFrameReported = true
                onReadyToDraw()
            }
        }
        GlassBackdropHost(
            backdropColor = backdropColor,
            background = {
                PageBackground(
                    preferences = loadedPreferences,
                    preparedImage = preparedAppearance?.background,
                    darkTheme = darkTheme,
                    reduceMotion = glassSettings.reduceMotion || suspendVisualEffects || scrollingVisualEffects || showingSecondaryPage,
                    importedImagePaths = importedBackgrounds.map { it.path },
                    onBackdropColor = { backdropColor = it },
                )
            },
            // Avoid backdrop capture competing with lists and page transitions.
            suspendEffects = suspendVisualEffects || scrollingVisualEffects || showingSecondaryPage ||
                destination == TopDestination.Mine,
            content = {
            Scaffold(
                modifier = Modifier.nestedScroll(visualScrollObserver),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (!showingSecondaryPage && !coverBottomBar && !imeVisible) {
                        GlassDock(destination) { dest ->
                            if (dest == destination && secondaryPage == SecondaryPage.NONE) return@GlassDock
                            pauseVisualEffects()
                            transientParentDestination = null
                            if (dest == TopDestination.Week) weekResetRequest += 1
                            if (dest == TopDestination.Today) {
                                demoScheduleState.selectedDay = demoScheduleState.todayWeekday
                            }
                            current = dest
                            secondaryPage = SecondaryPage.NONE
                            secondaryParents = emptyList()
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
                    val requestedPage = PageKey(destination, if (showingSecondaryPage) secondaryPage else if (destination == TopDestination.Calendar) SecondaryPage.ACADEMIC_CALENDAR else SecondaryPage.NONE)
                    // Every calendar entrance reuses the same loaded WebView and navigation state.
                    val pageKey = if (requestedPage.secondary == SecondaryPage.ACADEMIC_CALENDAR)
                        PageKey(TopDestination.Calendar, SecondaryPage.ACADEMIC_CALENDAR) else requestedPage
                    val pageContent: @Composable (PageKey) -> Unit = { page ->
                        if (page.secondary != SecondaryPage.NONE) {
                            when (page.secondary) {
                            SecondaryPage.COURSE_TOOLS -> cn.qztc.timetable.education.CourseImportToolsScreen(
                                onBack = { closeSecondaryPage() },
                                onImport = { importReturnPage = ImportReturnPage.COURSE_TOOLS; secondaryPage = SecondaryPage.QNU_IMPORT },
                                onCheckChanges = { upcomingChangesDialog = true },
                                canCheckChanges = activeSemester != null,
                            )
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
                                onOpenCalendar = { date ->
                                    calendarOpenDate = date.toString()
                                    openSecondaryPage(SecondaryPage.ACADEMIC_CALENDAR)
                                },
                                onBack = { closeSecondaryPage() },
                            )

                            SecondaryPage.REMINDER_SETTINGS -> ReminderSettingsScreen(
                                academicPluginInstalled = academicPluginInstalled,
                                onPluginMarket = { openSecondaryPage(SecondaryPage.PLUGIN_MARKET) },
                                dailyAgenda = preferences.dailyAgenda,
                                onDailyAgendaChange = { settings ->
                                    if (settings.enabled && !QztcNotifications.canPost(context) && Build.VERSION.SDK_INT >= 33) {
                                        pendingReminderAction = ReminderPermissionAction(dailyAgenda = settings)
                                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else scope.launch { preferencesStore.setDailyAgenda(settings) }
                                },
                                onPreviewDailyAgenda = {
                                    scope.launch {
                                        val sent = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            cn.qztc.timetable.reminder.DailyAgendaScheduler.preview(context, preferences.dailyAgenda)
                                        }
                                        snackbarHostState.showSnackbar(if (sent) "今日待办通知已发送" else "请先允许通知权限")
                                    }
                                },
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
                                onRemindOnHolidaysChange = { value -> scope.launch { preferencesStore.setRemindOnHolidays(value) } },
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
                                onReviewPlugin = { openSecondaryPage(SecondaryPage.REVIEW_PLUGIN) },
                                onScheduleQaPlugin = { openSecondaryPage(SecondaryPage.SCHEDULE_QA_PLUGIN) },
                                onExamSchedulePlugin = { openSecondaryPage(SecondaryPage.EXAM_SCHEDULE_PLUGIN) },
                                onAcademicRecordsPlugin = { openSecondaryPage(SecondaryPage.ACADEMIC_HUB) },
                            )
                            SecondaryPage.REVIEW_PLUGIN -> cn.qztc.timetable.review.ReviewScreen(onBack = { closeSecondaryPage() })
                            SecondaryPage.SCHEDULE_QA_PLUGIN -> cn.qztc.timetable.plugin.ScheduleQaPluginScreen(
                                semesterLabel = activeSemester?.name ?: "尚未导入课表",
                                courses = demoScheduleState.importedSchedule?.state?.courses.orEmpty(),
                                meetings = demoScheduleState.importedSchedule?.state?.meetings.orEmpty(),
                                onBack = { closeSecondaryPage() },
                            )
                            SecondaryPage.EXAM_SCHEDULE_PLUGIN -> cn.qztc.timetable.plugin.ExamSchedulePluginScreen(
                                semesterLabel = activeSemester?.name ?: "尚未导入课表",
                                onBack = { closeSecondaryPage() },
                            )
                            SecondaryPage.STUDY -> StudyScreen(
                                initialTab = studyInitialTab,
                                semesterLabel = activeSemester?.name ?: "尚未导入课表",
                                courses = demoScheduleState.importedSchedule?.state?.courses.orEmpty(),
                                credentials = savedCredentials,
                                onBack = { closeSecondaryPage() },
                                onConfigureAccount = { importReturnPage = ImportReturnPage.STUDY; secondaryPage = SecondaryPage.QNU_IMPORT },
                            )
                            SecondaryPage.HOMEWORK -> cn.qztc.timetable.education.HomeworkScreen(
                                credentials = savedCredentials,
                                onBack = { closeSecondaryPage() },
                                onConfigureAccount = { importReturnPage = ImportReturnPage.HOMEWORK; secondaryPage = SecondaryPage.QNU_IMPORT },
                            )
                            SecondaryPage.ACADEMIC_HUB, SecondaryPage.ACADEMIC_CALENDAR -> cn.qztc.timetable.education.AcademicHubScreen(
                                onPluginMarket = { openSecondaryPage(SecondaryPage.PLUGIN_MARKET) },
                                initialTab = if (page.secondary == SecondaryPage.ACADEMIC_CALENDAR) "calendar" else "competitions",
                                academicDays = demoScheduleState.importedSchedule?.academicDays.orEmpty(),
                                initialCalendarDate = calendarOpenDate.takeIf { page.secondary == SecondaryPage.ACADEMIC_CALENDAR },
                                onCalendarDateConsumed = { calendarOpenDate = null },
                                rootPage = destination == TopDestination.Calendar && secondaryPage == SecondaryPage.NONE,
                                active = pageKey == page,
                                onContentReady = { if (page.secondary == SecondaryPage.ACADEMIC_CALENDAR) calendarContentReady = true },
                                semesterLabel = activeSemester?.name ?: "尚未导入课表",
                                onSchoolCalendar = { openSecondaryPage(SecondaryPage.SEMESTER_SCHEDULE) },
                                onActivityManagement = { openSecondaryPage(SecondaryPage.ACTIVITY_MANAGEMENT) },
                                activities = (demoScheduleState.activities() + calendarMemos).distinctBy { it.id },
                                semester = activeSemester,
                                activityPresetColors = preferences.coursePresetColors,
                                onCoverBottomBar = { coverBottomBar = it },
                                onSaveActivity = { draft ->
                                    saveActivity(draft, emptyList())
                                },
                                onDeleteActivity = { id -> deleteActivity(id, true) },
                                onBack = { closeSecondaryPage() },
                                onEvents = { entries ->
                                    // User-confirmed events are calendar-only, including old opt-ins.
                                    if (academicPluginInstalled) timetableRepository.semesters().forEach { semester ->
                                        timetableRepository.activities(semester.id).filter { it.id.startsWith("academic:${semester.id}:") }
                                            .forEach { timetableRepository.deleteActivity(it.id, restoreCourses = true) }
                                    }
                                    activeSemesterId?.let { refreshSchedule(it) }
                                },
                            )
                            SecondaryPage.GRADE_PORTAL -> AcademicPortalScreen(
                                title = "成绩查询",
                                credentials = savedCredentials,
                                onBack = { secondaryPage = SecondaryPage.STUDY },
                            )
                            SecondaryPage.EVALUATION_PORTAL -> AcademicPortalScreen(
                                title = "快速评教",
                                credentials = savedCredentials,
                                onBack = { secondaryPage = SecondaryPage.STUDY },
                            )
                            SecondaryPage.ANNOUNCEMENTS -> AnnouncementScreen(
                                store = announcementStore,
                                onBack = { closeSecondaryPage() },
                                onUpdate = { checkForUpdates() },
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

                            SecondaryPage.ACTIVITY_MANAGEMENT -> {
                                val term = activeSemester ?: demoScheduleState.importedSchedule?.semester ?: importedSemesters.firstOrNull()
                                val managedActivities = (demoScheduleState.activities() + calendarMemos).distinctBy { it.id }
                                var editingId by remember { mutableStateOf<String?>(null) }
                                var addingDate by remember { mutableStateOf<LocalDate?>(null) }
                                val editorVisible = pageKey == page && (editingId != null || addingDate != null)
                                DisposableEffect(editorVisible) {
                                    coverBottomBar = editorVisible
                                    onDispose { coverBottomBar = false }
                                }
                                cn.qztc.timetable.feature.settings.ActivityManagementScreen(
                                    activities = managedActivities, semester = term, semesterOptions = importedSemesters,
                                    academicDays = demoScheduleState.importedSchedule?.academicDays.orEmpty(),
                                    onBack = { closeSecondaryPage() }, onEdit = { editingId = it }, onAdd = { addingDate = it },
                                    onDelete = { ids -> ids.forEach {deleteActivity(it,true)} },
                                    presetColors = preferences.coursePresetColors,
                                    onColor = { id,color -> managedActivities.firstOrNull {it.id==id}?.let { selected ->
                                        saveActivity(selected.toEditDraft(LocalDate.now().dayOfWeek.value,selected.startDate?:LocalDate.now(),term?.totalWeeks?:20).copy(color=color),emptyList())
                                    } },
                                    activityColor = { Color(android.graphics.Color.parseColor(cn.qztc.timetable.feature.timetable.activityColorCss(it))) })
                                val activity = editingId?.let { id -> managedActivities.firstOrNull { it.id == id } }
                                    ?: addingDate?.let { date -> cn.qztc.timetable.core.domain.model.ScheduleActivity(
                                        id = "", semesterId = "calendar", name = "", kind = cn.qztc.timetable.core.domain.model.ActivityKind.DAY,
                                        startDate = date, endDate = date, color = preferences.coursePresetColors.firstOrNull()) }
                                activity?.let { selected ->
                                    cn.qztc.timetable.feature.timetable.ActivityMemoEditor(
                                        title = if (editingId == null) "添加活动" else "编辑活动", editorKey = selected.id.ifBlank { "management-add-$addingDate" },
                                        initial = selected.toEditDraft(LocalDate.now().dayOfWeek.value, selected.startDate ?: LocalDate.now(), term?.totalWeeks ?: 20),
                                        presetColors = preferences.coursePresetColors,
                                        semester = term,
                                        onPreviewReminder={popup,title,detail ->cn.qztc.timetable.reminder.activityReminderPreview(context,popup,title,detail)},
                                        onSystemReminderSettings={popup ->cn.qztc.timetable.reminder.openActivityReminderSettings(context,popup)},
                                        onBack = { editingId = null; addingDate = null },
                                        onSave = { draft -> saveActivity(draft, emptyList()); editingId = null; addingDate = null },
                                        onDelete = editingId?.let { id -> ({ deleteActivity(id, true); editingId = null }) })
                                }
                            }
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
                                        calendarMode = true,
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
                                                cn.qztc.timetable.feature.settings.calendarImportEvents(matching).forEach { event ->
                                                    timetableRepository.upsertActivity(event.toCalendarActivity(target.id))
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
                                            fun key(day: cn.qztc.timetable.core.domain.calendar.AcademicDay)="${day.date}:${day.kind}"
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
                                        activities = (demoScheduleState.activities() + calendarMemos).distinctBy { it.id },
                                        onOpenCalendar = { date ->
                                            calendarOpenDate = date.toString()
                                            openSecondaryPage(SecondaryPage.ACADEMIC_CALENDAR)
                                        },
                                        importedCourseCount = demoScheduleState.importedSchedule?.state?.courses?.size ?: 0,
                                        importedTeachingWeeks = demoScheduleState.importedSchedule
                                            ?.takeIf { it.semester.id == semester.id }?.state?.importedTeachingWeeks,
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
                                automaticCheck = importReturnPage == ImportReturnPage.SYNC_CENTER,
                                automaticImport = false,
                                showAutomaticCheck = true,
                                campusNetworkChecked = importCampusChecked,
                                onCheckFailed = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
                                savedCredentials = savedCredentials,
                                onSaveCredentials = { credentials ->
                                    runCatching { credentialStore.save(credentials) }
                                        .onSuccess {
                                            credentialRevision += 1
                                            scope.launch { snackbarHostState.showSnackbar("教务账号已加密保存，可用于课表与成绩获取") }
                                        }
                                        .onFailure { error ->
                                            scope.launch {
                                                snackbarHostState.showSnackbar(error.message ?: "教务账号保存失败")
                                            }
                                        }
                                },
                                onClearCredentials = {
                                    credentialStore.clear()
                                    cn.qztc.timetable.education.GradeCache(context).clear()
                                    cn.qztc.timetable.education.GradeCache(context, "qnu_credit_progress").clear()
                                    credentialRevision += 1
                                    scope.launch { snackbarHostState.showSnackbar("已清除保存的教务账号") }
                                },
                                onBack = {
                                    importCampusChecked = false
                                    val returnPage = when (importReturnPage) {
                                        ImportReturnPage.STUDY -> SecondaryPage.STUDY
                                        ImportReturnPage.HOMEWORK -> SecondaryPage.HOMEWORK
                                        ImportReturnPage.SYNC_CENTER -> SecondaryPage.NONE
                                        ImportReturnPage.COURSE_ARRANGEMENT -> SecondaryPage.SEMESTER_SCHEDULE
                                        ImportReturnPage.COURSE_TOOLS -> SecondaryPage.COURSE_TOOLS
                                        ImportReturnPage.NONE, ImportReturnPage.DIRECT -> SecondaryPage.NONE
                                    }
                                    if (returnPage == SecondaryPage.NONE) closeSecondaryPage() else secondaryPage = returnPage
                                    importReturnPage = ImportReturnPage.NONE
                                },
                                onSnapshotSaved = { refreshSnapshots() },
                                onImported = { candidate ->
                                    scope.launch {
                                        if (importReturnPage == ImportReturnPage.SYNC_CENTER) {
                                            runCatching {
                                                val target = requireNotNull(timetableRepository.semesters().filter { timetableRepository.state(it.id).courses.isNotEmpty() }
                                                    .maxWithOrNull(compareBy({ it.schoolYear }, { it.term.ordinal })))
                                                val hint = candidate.semesterHint
                                                require(!candidate.hasErrors && hint?.year == target.schoolYear && hint.term == target.term)
                                                val next = cn.qztc.timetable.core.domain.schedule.UpcomingScheduleChanges.merge(
                                                    target, timetableRepository.state(target.id), candidate.state,
                                                    timetableRepository.periodScheme(target.id)?.periods.orEmpty(), java.time.LocalDateTime.now())
                                                cn.qztc.timetable.sync.ScheduleChangeInbox(context, target.id).receive(next.exceptions)
                                                closeSecondaryPage()
                                                upcomingChangesAlreadyChecked = true
                                                upcomingChangesDialog = true
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
                                                val savedDays = timetableRepository.academicDays(semester.id)
            val savedActivities = timetableRepository.activities(semester.id)
            val defaults = cn.qztc.timetable.feature.settings.CalendarScheduleDefaults.cached(context, semester, savedDays, savedActivities)
            if (defaults.days.isNotEmpty() || defaults.removedDayIds.isNotEmpty()) timetableRepository.replaceAcademicDays(semester.id, savedDays.filterNot { saved -> saved.id in defaults.removedDayIds || defaults.days.any { it.date == saved.date } } + defaults.days)
            defaults.activities.forEach { timetableRepository.upsertActivity(it) }
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
                                                ImportReturnPage.COURSE_TOOLS -> SecondaryPage.COURSE_TOOLS
                                                ImportReturnPage.SYNC_CENTER -> if (syncOnlyImport) SecondaryPage.SYNC_CENTER else SecondaryPage.NONE
                                                ImportReturnPage.NONE, ImportReturnPage.DIRECT -> SecondaryPage.NONE
                                                ImportReturnPage.STUDY -> SecondaryPage.STUDY
                                                ImportReturnPage.HOMEWORK -> SecondaryPage.HOMEWORK
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
                                active = pageKey == page,
                                scheduleState = demoScheduleState,
                                coursePresetColors = preferences.coursePresetColors,
                                reminderState = courseReminderState,
                                onSaveReminder = saveReminder,
                                onSaveCourseEdit = saveCourseEdit,
                                onDeleteCourse = deleteCourse,
                                onOpenCalendar = { date ->
                                    calendarOpenDate = date.toString()
                                    openSecondaryPage(SecondaryPage.ACADEMIC_CALENDAR)
                                },
                                onCoverBottomBar = { coverBottomBar = it },
                            )
                            TopDestination.Week -> WeekScreen(
                                active = pageKey == page,
                                reminderState = courseReminderState,
                                onSaveReminder = saveReminder,
                                scheduleState = demoScheduleState,
                                coursePresetColors = preferences.coursePresetColors,
                                onSaveCourseEdit = saveCourseEdit,
                                onDeleteCourse = deleteCourse,
                                onAddCourse = addCourse,
                                onOpenCalendar = { date ->
                                    calendarOpenDate = date.toString()
                                    openSecondaryPage(SecondaryPage.ACADEMIC_CALENDAR)
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
                            TopDestination.Calendar -> Unit // Calendar uses the shared academic page above.
                            TopDestination.Mine -> MineScreen(
                                active = pageKey == page,
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
                                    teachingWeeks = demoScheduleState.importedSchedule?.state?.importedTeachingWeeks,
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
                                    importReturnPage = ImportReturnPage.DIRECT
                                    importCampusDialog = true
                                },
                                onStudyClick = { studyInitialTab = 0; openSecondaryPage(SecondaryPage.STUDY) },
                                onCreditsClick = { studyInitialTab = 1; openSecondaryPage(SecondaryPage.STUDY) },
                                onCheckChangesClick = {
                                    upcomingChangesAlreadyChecked = false
                                    upcomingChangesDialog = true
                                },
                                onSettingsVisibilityChange = { coverBottomBar = it },
                                onCompetitionsClick = { openSecondaryPage(SecondaryPage.ACADEMIC_HUB) },
                                onCalendarClick = { openSecondaryPage(SecondaryPage.ACADEMIC_CALENDAR) },
                                pinnedPlugins = cn.qztc.timetable.plugin.PluginRegistry.plugins.filter {
                                    it.installedVersion(context) > 0 && cn.qztc.timetable.plugin.PluginPins.contains(context, it.descriptor.id)
                                }.map { plugin -> cn.qztc.timetable.feature.settings.MinePinnedPlugin(plugin.descriptor.name) {
                                    openSecondaryPage(when (plugin.descriptor.id) {
                                        "adskip" -> SecondaryPage.AD_SKIP
                                        "review" -> SecondaryPage.REVIEW_PLUGIN
                                        "academic-records" -> SecondaryPage.ACADEMIC_HUB
                                        "schedule-qa" -> SecondaryPage.SCHEDULE_QA_PLUGIN
                                        else -> SecondaryPage.EXAM_SCHEDULE_PLUGIN
                                    })
                                } },
                                onEvaluationClick = { openSecondaryPage(SecondaryPage.EVALUATION_PORTAL) },
                                onCampusAliasClick = {
                                    openSecondaryPage(SecondaryPage.CAMPUS_ALIASES)
                                },
                                onCampusMapClick = { openSecondaryPage(SecondaryPage.CAMPUS_MAP) },
                                onHomeworkClick = { homeworkCampusDialog = true },
                                onStartDateClick = {
                                    openSecondaryPage(SecondaryPage.ACTIVITY_MANAGEMENT)
                                },
                                onSchoolCalendarClick = { openSecondaryPage(SecondaryPage.SEMESTER_SCHEDULE) },
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
                                onAnnouncementsClick = { openSecondaryPage(SecondaryPage.ANNOUNCEMENTS) },
                                unreadAnnouncements = announcementItems.count { !announcementStore.read(it) },
                                onPrivacyAboutClick = {
                                    openSecondaryPage(SecondaryPage.PRIVACY_ABOUT)
                                },
                                onCheckUpdateClick = { checkForUpdates() },
                            )
                            }
                        }
                    }
                    // A page fade exposes the wallpaper through the material and
                    // composes both screens at once. Replace pages at full opacity.
                    val calendarKey = PageKey(TopDestination.Calendar, SecondaryPage.ACADEMIC_CALENDAR)
                    val timetableRoot = pageKey.secondary == SecondaryPage.NONE &&
                        pageKey.destination in listOf(TopDestination.Today, TopDestination.Week, TopDestination.Mine)
                    val mountedTimetables = if (timetableRoot) (retainedTimetablePages + pageKey.destination).distinct() else retainedTimetablePages.toList()
                    androidx.compose.runtime.SideEffect {
                        if (pageKey == calendarKey) calendarVisited = true
                        if (timetableRoot && pageKey.destination !in retainedTimetablePages) retainedTimetablePages.add(pageKey.destination)
                    }
                    if (calendarVisited) {
                        val calendarActive = pageKey == calendarKey
                        cn.qztc.timetable.education.RetainedStudyTab(calendarActive) {
                            pageStates.SaveableStateProvider("${calendarKey.destination}:${calendarKey.secondary}") { pageContent(calendarKey) }
                        }
                    }
                    mountedTimetables.forEach { destinationKey ->
                        val retainedKey = PageKey(destinationKey, SecondaryPage.NONE)
                        androidx.compose.runtime.key(destinationKey) {
                            cn.qztc.timetable.education.RetainedStudyTab(pageKey == retainedKey) {
                                pageStates.SaveableStateProvider("${retainedKey.destination}:${retainedKey.secondary}") { pageContent(retainedKey) }
                            }
                        }
                    }
                    if (pageKey != calendarKey && !timetableRoot) pageStates.SaveableStateProvider("${pageKey.destination}:${pageKey.secondary}") { pageContent(pageKey) }
                    }
                }
            }
        }
        )
        if (homeworkCampusDialog) cn.qztc.timetable.qnu.CampusNetworkDialog(
            onDismiss = { homeworkCampusDialog = false },
            onReady = { homeworkCampusDialog = false; openSecondaryPage(SecondaryPage.HOMEWORK) },
        )
        if (importCampusDialog) cn.qztc.timetable.qnu.CampusNetworkDialog(
            onDismiss = { importCampusDialog = false },
            onReady = { importCampusDialog = false; importCampusChecked = true; openSecondaryPage(SecondaryPage.QNU_IMPORT) },
        )
        if (upcomingChangesDialog) {
            val semester = activeSemester ?: demoScheduleState.importedSchedule?.semester
            if (semester != null) {
                UpcomingChangesDialog(
                    semester = semester,
                    savedCredentials = savedCredentials,
                    repository = timetableRepository,
                    checkOnOpen = !upcomingChangesAlreadyChecked,
                    onDismiss = { upcomingChangesDialog = false; upcomingChangesAlreadyChecked = false },
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
    COURSE_TOOLS,
    ACADEMIC_HUB,
    ACADEMIC_CALENDAR,
    ACTIVITY_MANAGEMENT,
    REMINDER_SETTINGS,
    STORAGE,
    AD_SKIP,
    PLUGIN_MARKET,
    REVIEW_PLUGIN,
    SCHEDULE_QA_PLUGIN,
    EXAM_SCHEDULE_PLUGIN,
    STUDY,
    HOMEWORK,
    GRADE_PORTAL,
    EVALUATION_PORTAL,
    ANNOUNCEMENTS,
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
    DIRECT,
    COURSE_TOOLS,
    STUDY,
    HOMEWORK,
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
        .filter { it.schoolYear in (currentYear - 5)..(currentYear + 1) && state(it.id).courses.isNotEmpty() }
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
