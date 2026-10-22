package cn.qztc.timetable.qnu

import android.graphics.Bitmap
import android.graphics.Canvas
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.net.http.SslError
import android.view.ActionMode
import android.view.View
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.RenderProcessGoneDetail
import android.view.ViewGroup
import android.view.WindowManager
import android.os.Build
import androidx.core.view.WindowCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cn.qztc.timetable.core.domain.imports.ImportedTimetable
import cn.qztc.timetable.core.domain.imports.PersonalClassMerger
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcPageTopBar
import cn.qztc.timetable.core.ui.theme.QztcSpacing
import cn.qztc.timetable.core.ui.theme.QztcTopIconButton
import cn.qztc.timetable.core.ui.theme.QztcTypography
import cn.qztc.timetable.integration.qnu.QnuTimetableImporter
import cn.qztc.timetable.integration.qnu.QnuTimetableParser
import cn.qztc.timetable.integration.qnu.QnuCourseProfile
import cn.qztc.timetable.integration.qnu.android.QnuEndpointPolicy
import cn.qztc.timetable.integration.qnu.android.QnuSessionCaptureResult
import cn.qztc.timetable.integration.qnu.android.QnuSessionSnapshot
import cn.qztc.timetable.integration.qnu.android.QnuSessionStore
import cn.qztc.timetable.integration.qnu.android.QnuWebSessionCapture
import cn.qztc.timetable.snapshot.SnapshotStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.json.JSONTokener
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 前台教务登录与首次导入页面。WebView 只负责学校网页交互，解析和持久化由原生层完成。
 * 页面外跳被限制在泉师教务和统一身份认证域名内。用户已授权的课表 Cookie 可加密保留在本机，
 * 仅用于后续受限同步，绝不写入数据库、日志或导出文件。
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun QnuImportScreen(
    savedCredentials: QnuSavedCredentials?,
    onSaveCredentials: (QnuSavedCredentials) -> Unit = {},
    onClearCredentials: () -> Unit = {},
    onBack: () -> Unit,
    onImported: (ImportedTimetable) -> Unit,
    onSnapshotSaved: () -> Unit = {},
    syncOnly: Boolean = false,
    automaticCheck: Boolean = false,
    automaticImport: Boolean = false,
    showAutomaticCheck: Boolean = false,
    campusNetworkChecked: Boolean = false,
    checkSemester: cn.qztc.timetable.core.domain.model.Semester? = null,
    onCheckFailed: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val content: @Composable () -> Unit = {
        QnuImportContent(savedCredentials, onSaveCredentials, onClearCredentials, onBack,
            onImported, onSnapshotSaved, syncOnly, automaticCheck, automaticImport,
            showAutomaticCheck, campusNetworkChecked, checkSemester, onCheckFailed, modifier)
    }
    if (automaticCheck && !showAutomaticCheck) {
        content()
    } else {
        // Xiaomi's root-level content recognizer runs before Window.Callback. A separate
        // browser window avoids its Activity-decor capture path, without global settings
        // changes, hidden APIs, or disabling normal WebView gestures.
        Dialog(onDismissRequest = onBack, properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
        )) {
            val view = LocalView.current
            val colors = LocalQztcColors.current
            SideEffect {
                val window = (view.parent as? DialogWindowProvider)?.window
                if (window != null) {
                    // This is a full page, not a modal scrim. Style this window rather
                    // than the Activity window so the MIUI long-press isolation stays intact.
                    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                        WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                        WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
                    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                    window.statusBarColor = Color.White.toArgb()
                    window.navigationBarColor = colors.solid2.toArgb()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        window.isStatusBarContrastEnforced = false
                        window.isNavigationBarContrastEnforced = false
                    }
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = true
                        isAppearanceLightNavigationBars = !colors.isDark
                    }
                }
            }
            Box(Modifier.fillMaxSize().background(colors.solid2)) {
                Box(Modifier.fillMaxSize().navigationBarsPadding()) { content() }
                // Paint the inset explicitly: a transparent dialog status bar can
                // otherwise expose the Activity's wallpaper instead of this page.
                Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(Color.White))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun QnuImportContent(
    savedCredentials: QnuSavedCredentials?,
    onSaveCredentials: (QnuSavedCredentials) -> Unit,
    onClearCredentials: () -> Unit,
    onBack: () -> Unit,
    onImported: (ImportedTimetable) -> Unit,
    onSnapshotSaved: () -> Unit,
    syncOnly: Boolean,
    automaticCheck: Boolean,
    automaticImport: Boolean,
    showAutomaticCheck: Boolean,
    campusNetworkChecked: Boolean,
    checkSemester: cn.qztc.timetable.core.domain.model.Semester?,
    onCheckFailed: (String) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val hostActivity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = LocalQztcColors.current
    val backgroundCheck = automaticCheck && !showAutomaticCheck
    val scope = rememberCoroutineScope()
    val capture = remember { QnuWebSessionCapture() }
    val sessionStore = remember(context) { QnuSessionStore(context) }
    val snapshotStore = remember(context) { SnapshotStore(context) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(QnuEndpointPolicy.INITIAL_WEBVIEW_URL) }
    var status by remember { mutableStateOf("正在打开泉师教务系统…") }
    var operationError by remember { mutableStateOf<String?>(null) }
    var capturedSession by remember { mutableStateOf<QnuSessionSnapshot?>(null) }
    var importStarted by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var captureChoice by remember { mutableStateOf(false) }
    var personalReason by remember { mutableStateOf("") }
    var classReason by remember { mutableStateOf("") }
    var semesterCatalog by remember { mutableStateOf<QnuSchoolSemesterCatalog?>(null) }
    var selectedSemester by remember { mutableStateOf<QnuSchoolSemester?>(null) }
    var selectedViewKind by remember { mutableStateOf(QnuTimetableKind.PERSONAL) }
    var pendingSemester by remember { mutableStateOf<QnuSchoolSemester?>(null) }
    var showSemesterPicker by remember { mutableStateOf(false) }
    var semesterPickerOffered by remember { mutableStateOf(false) }
    var intranetGate by remember { mutableStateOf(if (campusNetworkChecked) IntranetGate.Ready else IntranetGate.Checking) }
    var pageLoading by remember { mutableStateOf(true) }
    var showAccount by remember { mutableStateOf(savedCredentials == null) }
    var personalBuffer by remember { mutableStateOf<ImportedTimetable?>(null) }
    var classBuffer by remember { mutableStateOf<ImportedTimetable?>(null) }
    var mergeReview by remember { mutableStateOf<PersonalClassMerger.Result?>(null) }
    var accountUser by remember(savedCredentials) { mutableStateOf(savedCredentials?.username.orEmpty()) }
    var accountPassword by remember(savedCredentials) { mutableStateOf(savedCredentials?.password.orEmpty()) }
    val diagnostics = remember(context) { QnuImportDiagnostics(context) }
    var importJob by remember { mutableStateOf<Job?>(null) }
    var rendererFailed by remember { mutableStateOf(false) }
    var checkStarted by remember { mutableStateOf(false) }
    var loginSubmitted by remember { mutableStateOf(false) }

    fun openView(kind: QnuTimetableKind) {
        val view = webView ?: return
        if (pageLoading || importStarted || capturing) return
        operationError = null
        scope.launch {
            pageLoading = true
            status = "正在打开${kind.label}…"
            try {
                if (kind == QnuTimetableKind.ARRANGEMENT && isCourseArrangementPage(view)) {
                    pageLoading = false
                    status = "当前查看：${kind.label}"
                    return@launch
                }
                val opened = if (kind == QnuTimetableKind.ARRANGEMENT) openCourseArrangement(view)
                else clickSchoolTimetable(view, kind)
                check(opened) { "页面上没有找到${kind.label}入口" }
                if (kind == QnuTimetableKind.ARRANGEMENT) {
                    check(waitForCourseArrangement(view)) { "${kind.label}未加载" }
                    pageLoading = false
                } else {
                    check(waitUntil { !pageLoading && QnuEndpointPolicy.isTimetableUrl(currentUrl) }) { "${kind.label}未加载" }
                }
                status = "当前查看：${kind.label}"
                operationError = null
            } catch (error: Exception) {
                pageLoading = false
                operationError = error.message ?: "无法打开${kind.label}"
                status = operationError!!
            }
        }
    }

    LaunchedEffect(automaticCheck) {
        if (automaticCheck) {
            delay(90_000)
            onCheckFailed("教务检查超时，请稍后重试")
        }
    }

    LaunchedEffect(Unit) {
        diagnostics.record("open_import")
        if (campusNetworkChecked) return@LaunchedEffect
        intranetGate = if (withContext(Dispatchers.IO) { QnuIntranetProbe.isReachable() }) {
            IntranetGate.Ready
        } else {
            IntranetGate.NeedVpn
        }
        if (automaticCheck && intranetGate == IntranetGate.NeedVpn) onCheckFailed("无法连接教务，请连接校园网或校园 VPN 后重试")
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && intranetGate != IntranetGate.Ready) {
                scope.launch {
                    if (withContext(Dispatchers.IO) { QnuIntranetProbe.isReachable() }) {
                        intranetGate = IntranetGate.Ready
                        status = "正在打开泉师教务系统…"
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        onDispose {
            importJob?.cancel()
            webView?.apply {
                stopLoading()
                clearHistory()
                // 教务页面只用于本次导入；清理网页资源缓存，避免每次退出都留下大体积文件。
                clearCache(true)
                android.webkit.WebStorage.getInstance().apply {
                    deleteOrigin("https://jw.qztc.edu.cn")
                    deleteOrigin("https://id.qztc.edu.cn")
                }
                // The academic portal can leave tens of megabytes of page assets in
                // WebView's shared cache. Cookies and the encrypted session store remain.
                webViewClient = WebViewClient()
                removeAllViews()
                destroy()
            }
            webView = null
        }
    }

    fun failOneClick(message: String, reopenSemesterPicker: Boolean = false) {
        importStarted = false
        operationError = message
        status = message
        if (automaticCheck) onCheckFailed(message)
        if (reopenSemesterPicker) showSemesterPicker = semesterCatalog != null
    }

    fun rememberSession(url: String) {
        when (val result = capture.capture(url)) {
            is QnuSessionCaptureResult.Captured -> {
                runCatching { sessionStore.save(result.session) }
                    .onFailure { status = "无法安全保存本次教务会话，请重新登录后再试" }
                capturedSession = result.session
            }
            QnuSessionCaptureResult.NoActiveSession -> Unit
            QnuSessionCaptureResult.RejectedUrl -> Unit
        }
    }

    suspend fun finishMergedImport(personal: ImportedTimetable?, classTable: ImportedTimetable?) {
        val personalWithNotices = if (personal != null && classTable != null) mergeClassNotices(personal, classTable) else personal
        personalBuffer = personalWithNotices
        classBuffer = classTable
        when {
            personalWithNotices != null && classTable != null -> {
                val review = withContext(Dispatchers.Default) { PersonalClassMerger.merge(personalWithNotices.state, classTable.state) }
                if (review.needsReview) {
                    webView?.stopLoading()
                    webView?.onPause()
                    importStarted = false
                    status = "已读取个人课表和班级课表，请确认班级课表多出的课程或教师"
                    mergeReview = review
                } else {
                    status = "已合并个人课表与班级课表，正在写入本机…"
                    onImported(personalWithNotices.copy(state = review.preferred))
                }
            }
            personal != null -> {
                status = "已读取个人课表（班级课表暂不可用），正在写入本机…"
                onImported(personal)
            }
            classTable != null -> {
                status = "已读取班级课表（个人课表暂不可用），正在写入本机…"
                onImported(classTable)
            }
            else -> failOneClick(listOf(personalReason,classReason).filter { it.isNotBlank() }.joinToString("；").ifBlank { "未读取到课表页面，请确认登录与学期" })
        }
    }

    fun startImport() {
        if(importStarted || capturing || pageLoading || rendererFailed) return
        operationError = null
        val view = webView ?: return
        val semester = selectedSemester
        if (semester == null) {
            status = "请先选择要导入的学期"
            showSemesterPicker = semesterCatalog != null
            return
        }
        val semesterId = semester.semesterId
        val selectionUrl = QnuEndpointPolicy.courseSelectionUrl(semester.yearId, semester.termId)
        if (semesterId == null || selectionUrl == null) {
            status = "无法确认课表所属学期，请重新选择"
            showSemesterPicker = semesterCatalog != null
            return
        }
        importStarted = true
        diagnostics.record("begin_import")
        personalBuffer = null
        classBuffer = null
        status = if (syncOnly || automaticCheck) "正在检查停调课…" else "正在自动读取个人课表和班级课表…"
        importJob?.cancel()
        importJob = scope.launch {
            try {
                // Reload the selected semester even if another semester's entry
                // page is already open. Mark loading before the WebView callback.
                pageLoading=true
                view.loadUrl(selectionUrl)
                if (!waitUntil { !pageLoading && QnuEndpointPolicy.isCourseSelectionUrl(currentUrl) }) {
                    failOneClick("无法打开学期课表入口，请确认已登录后再试")
                    return@launch
                }
                val confirmedSemester=withTimeout(6_000) { suspendCancellableCoroutine<QnuSchoolSemester?> { continuation ->
                    readSemesterCatalog(view) { catalog ->
                        if(continuation.isActive)continuation.resume(catalog.selectedSemester())
                    }
                } }
                if(!semester.sameSchoolIds(confirmedSemester)) {
                    failOneClick("教务页面学期与选择不一致，请重新选择学期",true)
                    return@launch
                }
                if (!syncOnly && !automaticCheck) {
                    status = "正在读取课程安排表…"
                    diagnostics.record("read_arrangements")
                }
                // The course arrangement table is a separate menu entry on
                // some semesters. Open it before reading, then return to the
                // selected-semester page for the timetable links.
                pageLoading = true
                val arrangementOpened = !syncOnly && !automaticCheck && openCourseArrangement(view)
                if (arrangementOpened) {
                    waitUntil(20_000) {
                        !pageLoading && QnuEndpointPolicy.isTrustedAcademicUrl(currentUrl)
                    }
                    delay(450)
                } else {
                    pageLoading = false
                }
                val arrangementProfiles = if (syncOnly || automaticCheck) emptyList() else readCourseProfiles(view)
                diagnostics.record(
                    if (arrangementProfiles.isEmpty()) "arrangements_missing"
                    else "arrangements_read_${arrangementProfiles.size}",
                )
                if (arrangementOpened) {
                    status = "正在返回学期课表入口…"
                    pageLoading = true
                    view.loadUrl(selectionUrl)
                    if (!waitUntil { !pageLoading && QnuEndpointPolicy.isCourseSelectionUrl(currentUrl) }) {
                        failOneClick("无法返回学期课表入口，请重新导入")
                        return@launch
                    }
                    delay(350)
                }
                status = "正在打开个人课表…"
                pageLoading = true
                if (!clickSchoolTimetable(view, QnuTimetableKind.PERSONAL)) {
                    failOneClick("页面上没有找到个人课表入口")
                    return@launch
                }
                check(waitUntil { !pageLoading && QnuEndpointPolicy.isTimetableUrl(currentUrl) }) { "个人课表未加载" }
                delay(450)
                rememberSession(currentUrl)
                if (syncOnly || automaticCheck) {
                    status = "正在读取停调课通知…"
                    val html = readTimetableHtml(view, READ_CHANGE_NOTICES_SCRIPT)
                    val personalNotices = html?.let { withContext(Dispatchers.Default) { QnuTimetableImporter.importChangesHtml(it, semesterId) } }
                    if (personalNotices == null || personalNotices.hasErrors) {
                        failOneClick("个人课表通知读取失败，请重试")
                        return@launch
                    }
                    // The school may list a dated cancellation only on the class
                    // timetable. A personal-grid badge alone carries no scope.
                    status = "正在检查班级课表的停调课通知…"
                    pageLoading = true
                    view.loadUrl(selectionUrl)
                    check(waitUntil { !pageLoading && QnuEndpointPolicy.isCourseSelectionUrl(currentUrl) }) { "无法返回学期入口" }
                    pageLoading = true
                    val hasClassTable = clickSchoolTimetable(view, QnuTimetableKind.CLASS)
                    val classNotices = if (hasClassTable) {
                        check(waitUntil { !pageLoading && QnuEndpointPolicy.isTimetableUrl(currentUrl) }) { "班级课表未加载" }
                        delay(450)
                        val classHtml = readTimetableHtml(view, READ_CHANGE_NOTICES_SCRIPT)
                        val parsed = classHtml?.let { withContext(Dispatchers.Default) { QnuTimetableImporter.importChangesHtml(it, semesterId) } }
                        check(parsed != null && !parsed.hasErrors) { "班级课表通知读取失败" }
                        parsed
                    } else null
                    val changes = personalNotices.copy(state = personalNotices.state.copy(
                        courses = (personalNotices.state.courses + classNotices?.state?.courses.orEmpty()).distinctBy { it.id },
                        exceptions = (personalNotices.state.exceptions + classNotices?.state?.exceptions.orEmpty()).distinctBy { it.id },
                    ))
                    onImported(changes.copy(
                        semesterHint = cn.qztc.timetable.core.domain.imports.SemesterHint(
                            year = semester.yearLabel.toIntOrNull(), term = QnuSemesterFactory.term(semester.termLabel),
                            termText = semester.termLabel, raw = semester.label)
                    ))
                    return@launch
                }
                status = "正在解析个人课表…"
                diagnostics.record("parse_personal")
                val personal = parseTimetableFromWebView(view, semesterId, "个人课表") { personalReason = it }
                    ?.let { QnuTimetableImporter.applyCourseProfiles(it, arrangementProfiles) }

                status = "正在打开班级课表…"
                pageLoading=true
                view.loadUrl(selectionUrl)
                if (!waitUntil { !pageLoading && QnuEndpointPolicy.isCourseSelectionUrl(currentUrl) }) {
                    finishMergedImport(personal, null)
                    return@launch
                }
                delay(450)
                pageLoading = true
                val classOpened = clickSchoolTimetable(view, QnuTimetableKind.CLASS)
                val classTable = if (classOpened) {
                    check(waitUntil { !pageLoading && QnuEndpointPolicy.isTimetableUrl(currentUrl) }) { "班级课表未加载" }
                    delay(450)
                    rememberSession(currentUrl)
                    status = "正在解析班级课表…"
                    diagnostics.record("parse_class")
                    parseTimetableFromWebView(view, semesterId, "班级课表") { classReason = it }
                        ?.let { QnuTimetableImporter.applyCourseProfiles(it, arrangementProfiles) }
                } else {
                    classReason = "班级课表入口不存在"
                    null
                }
                if (personal == null && classTable == null) {
                    failOneClick(
                        message = listOf(personalReason, classReason)
                            .filter { it.isNotBlank() }
                            .joinToString("；")
                            .ifBlank { "个人课表和班级课表都为空，请确认已选学期存在课程" },
                        reopenSemesterPicker = false,
                    )
                    return@launch
                }
                diagnostics.record("merge_ready")
                finishMergedImport(personal, classTable)
            } catch (cancelled: CancellationException) {
                diagnostics.record("cancel_import")
                throw cancelled
            } catch (error: Exception) {
                diagnostics.record("import_error",error)
                failOneClick("自动导入失败，请确认已登录并连上学校内网后再试")
            } finally {
                importStarted = false
            }
        }
    }

    if (captureChoice) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { captureChoice = false },
            title = { Text("截图内容") },
            text = { Column {
                listOf("个人课表","班级课表","课程安排界面").forEachIndexed { index, label ->
                    TextButton(onClick = {
                        captureChoice = false
                        val web = webView ?: return@TextButton
                        val selected = selectedSemester ?: run { status="请先选择学期";return@TextButton }
                        val url = QnuEndpointPolicy.courseSelectionUrl(selected.yearId,selected.termId) ?: return@TextButton
                        operationError = null
                        capturing = true
                        scope.launch {
                            try {
                                status="正在打开$label"
                                pageLoading=true;web.loadUrl(url)
                                check(waitUntil { !pageLoading && QnuEndpointPolicy.isCourseSelectionUrl(currentUrl) }) { "课程安排页面未加载" }
                                if(index<2) {
                                    check(clickSchoolTimetable(web, if(index==0) QnuTimetableKind.PERSONAL else QnuTimetableKind.CLASS)) { "$label 入口不存在" }
                                    check(waitUntil { !pageLoading && QnuEndpointPolicy.isTimetableUrl(currentUrl) }) { "$label 未加载" }
                                }
                                status="正在生成完整截图"
                                val privacyProbe=context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
                                    java.io.File(context.cacheDir,"qa-redact-captures").isFile
                                val bitmap=withTimeout(60_000) { captureFullWebPage(web,privacyProbe) }
                                try { withContext(Dispatchers.IO) { snapshotStore.saveWebPage(bitmap) } } finally { bitmap.recycle() }
                                onSnapshotSaved();status="$label 截图已保存"
                            } catch(error: Exception) {
                                operationError=error.message ?: "$label 截图失败，请重试"
                                status=operationError!!
                            } finally { capturing=false;pageLoading=false }
                        }
                    }) { Text(label) }
                }
            } }, confirmButton = { TextButton(onClick={captureChoice=false}){Text("取消")} },
        )
    }

    // 顶栏和系统返回都离开导入页。教务登录会堆很多网页历史，
    // 如果把返回交给 WebView，用户会一直停在学校页面里出不去。
    BackHandler(onBack = onBack)

    mergeReview?.let { review ->
        ClassMergeReviewScreen(
            result = review,
            onConfirm = { merged ->
                val source = personalBuffer ?: classBuffer
                if (source != null) {
                    val accepted = source.copy(state = merged)
                    onImported(classBuffer?.let { mergeClassNotices(accepted, it) } ?: accepted)
                }
            },
            onBack = { mergeReview = null; webView?.onResume() },
        )
        return
    }

    if (intranetGate != IntranetGate.Ready) {
        if (backgroundCheck) return
        androidx.compose.ui.window.Dialog(onDismissRequest = onBack) {
        androidx.compose.material3.Surface(color = colors.solid, shape = androidx.compose.material3.MaterialTheme.shapes.large) {
            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                if (intranetGate == IntranetGate.Checking) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(color = colors.accent)
                        Text("正在检查是否已连接校园网", style = QztcTypography.body, color = colors.ink)
                    }
                } else {
                    Column(
                        Modifier.padding(horizontal = QztcSpacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(QztcSpacing.lg),
                    ) {
                        Text("教务系统在校园网内", style = QztcTypography.pageTitle, color = colors.ink)
                        Text(
                            "请连接校园网或校园 VPN 后重试。",
                            style = QztcTypography.body,
                            color = colors.mut,
                        )
                        Button(
                            onClick = {
                                if (EasyConnectBridge.isInstalled(context)) {
                                    EasyConnectBridge.open(context)
                                    status = "请在 VPN 中完成连接后返回"
                                } else {
                                    status = "未安装 EasyConnect，请先安装校园 VPN"
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (EasyConnectBridge.isInstalled(context)) "打开校园 VPN" else "未安装校园 VPN")
                        }
                        TextButton(
                            onClick = {
                                operationError = null
                                intranetGate = IntranetGate.Checking
                                scope.launch {
                                    intranetGate = if (withContext(Dispatchers.IO) { QnuIntranetProbe.isReachable() }) IntranetGate.Ready else IntranetGate.NeedVpn
                                }
                            },
                        ) {
                            Text("重新检查")
                        }
                        if (status.isNotBlank() && intranetGate == IntranetGate.NeedVpn) {
                            Text(status, style = QztcTypography.meta, color = colors.mut)
                        }
                    }
                }
            }
        }
        }
        return
    }

    Column(if (backgroundCheck) modifier.size(1.dp).alpha(0f) else modifier.fillMaxSize().background(colors.solid2)) {
        if (!backgroundCheck) {
        QztcPageTopBar(
            title = "教务导入",
            onBack = onBack,
            actions = {
                QztcTopIconButton(
                    icon = Icons.Outlined.Download,
                    contentDescription = "导入当前查看的学期",
                    onClick = { startImport() },
                    enabled = !importStarted && !capturing && !pageLoading && selectedSemester != null,
                )
                QztcTopIconButton(
                    icon = Icons.Outlined.CalendarMonth,
                    contentDescription = "查看其他学期",
                    onClick = { showSemesterPicker = true },
                    enabled = !importStarted && !capturing && semesterCatalog != null,
                )
                QztcTopIconButton(
                    icon = Icons.Outlined.CameraAlt,
                    contentDescription = "保存教务课表截图",
                    onClick = { captureChoice = true },
                    enabled = !importStarted && !capturing && QnuEndpointPolicy.isSnapshotCaptureUrl(currentUrl),
                )
                QztcTopIconButton(
                    icon = Icons.Outlined.Refresh,
                    contentDescription = "刷新页面",
                    onClick = { operationError=null;webView?.reload() },
                    enabled = !importStarted && !capturing,
                )
            },
        )
        TextButton(onClick = { showAccount = !showAccount }) {
            Text(if (showAccount) "收起账号" else if (savedCredentials != null) "已保存教务账号" else "登录自动填充")
        }
        if (showAccount) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = QztcSpacing.lg, vertical = QztcSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(QztcSpacing.sm),
            ) {
                OutlinedTextField(
                    value = accountUser,
                    onValueChange = { accountUser = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("教务账号") },
                    trailingIcon = { IconButton(onClick = { accountUser = "" }) { Icon(Icons.Outlined.Close, "清空教务账号") } },
                )
                OutlinedTextField(
                    value = accountPassword,
                    onValueChange = { accountPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("密码") },
                    trailingIcon = { IconButton(onClick = { accountPassword = "" }) { Icon(Icons.Outlined.Close, "清空密码") } },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(QztcSpacing.sm)) {
                    TextButton(
                        onClick = {
                            if (accountUser.isNotBlank() && accountPassword.isNotEmpty()) {
                                val credentials = QnuSavedCredentials(accountUser.trim(), accountPassword)
                                onSaveCredentials(credentials)
                                showAccount = false
                                if (QnuEndpointPolicy.isAuthenticationUrl(currentUrl)) webView?.let { autofillCredentials(it, credentials, force = true) }
                                status = "教务账号已加密保存，可用于课表导入及成绩查询"
                            } else {
                                status = "请输入账号和密码后再保存"
                            }
                        },
                    ) { Text("加密保存") }
                        TextButton(
                            onClick = {
                                loginSubmitted = false; checkStarted = false; semesterPickerOffered = false
                                selectedSemester = null; pendingSemester = null
                                webView?.let(::openQnuLogin)
                            },
                        ) { Text("重新登录") }
                }
            }
        }
        if(!pageLoading && !importStarted && !capturing && (operationError?:status).isNotBlank()) {
            Text(operationError?:status,style=QztcTypography.meta,color=if(operationError!=null)colors.danger else colors.mut,modifier=Modifier.fillMaxWidth().padding(horizontal=QztcSpacing.lg,vertical=4.dp))
        }
        }
        Box(if (backgroundCheck) Modifier.size(1.dp) else Modifier.weight(1f).fillMaxWidth()) {
            if(rendererFailed) {
                Column(Modifier.padding(QztcSpacing.xl)) {
                    Text("教务网页异常退出，原课表和登录会话已保留。")
                    Button(onClick = { rendererFailed=false;pageLoading=true;diagnostics.record("retry_renderer") }) { Text("重新打开网页") }
                }
            } else AndroidView(
                modifier = if (backgroundCheck) Modifier.requiredSize(360.dp, 600.dp) else Modifier.fillMaxSize(),
                factory = {
                    if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                        WebView.setWebContentsDebuggingEnabled(true)
                    }
                    TimetablePortalWebView(context).also { view ->
                        webView = view
                        view.setBackgroundColor(colors.solid2.toArgb())
                        view.settings.apply {
                            cacheMode = WebSettings.LOAD_NO_CACHE
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            allowFileAccess = false
                            allowContentAccess = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        }
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
                        fun handleNavigation(url: String, isMainFrame: Boolean): Boolean {
                            QnuEndpointPolicy.upgradeInsecureAcademicCasCallback(url)?.let { secureUrl ->
                                status = "统一认证完成，正在安全进入教务系统…"
                                view.post { view.loadUrl(secureUrl) }
                                return true
                            }
                            if (!QnuEndpointPolicy.isAllowedWebViewUrl(url)) {
                                status = "已阻止离开泉师登录范围"
                                return true
                            }
                            if (!isMainFrame && QnuEndpointPolicy.isTimetableUrl(url)) {
                                status = "正在打开课表页…"
                                view.post { view.loadUrl(url) }
                                return true
                            }
                            return false
                        }
                        view.webViewClient = object : WebViewClient() {
                            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                                diagnostics.record("renderer_gone",rendererCrashed=detail.didCrash())
                                importJob?.cancel();importStarted=false;pageLoading=false;rendererFailed=true
                                if(webView===view)webView=null
                                (view.parent as? ViewGroup)?.removeView(view)
                                view.destroy()
                                if (automaticCheck) onCheckFailed("教务网页异常退出，请重试")
                                return true
                            }
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                handleNavigation(request.url.toString(), request.isForMainFrame)

                            @Suppress("DEPRECATION")
                            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                                handleNavigation(url, true)

                            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                if(view!==webView)return
                                diagnostics.record("page_started")
                                currentUrl = url
                                pageLoading = true
                                if (importStarted) return
                                status = if (QnuEndpointPolicy.isPortalMainUrl(url)) {
                                    "登录成功，正在进入教务系统…"
                                } else if (QnuEndpointPolicy.isAuthenticationUrl(url)) {
                                    "请在页面中完成登录和验证码"
                                } else if (QnuEndpointPolicy.isAcademicHomeUrl(url)) {
                                    "正在打开学期列表…"
                                } else if (QnuEndpointPolicy.isCourseSelectionUrl(url)) {
                                    "正在读取可导入学期…"
                                } else {
                                    "正在加载教务页面…"
                                }
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                if(view!==webView)return
                                diagnostics.record("page_finished")
                                currentUrl = url
                                pageLoading = false
                                if (QnuEndpointPolicy.isTimetableUrl(url)) {
                                    view.evaluateJavascript(BLOCK_TIMETABLE_LONG_PRESS_SCRIPT, null)
                                }
                                if (QnuEndpointPolicy.isAcademicHomeUrl(url) || QnuEndpointPolicy.isCourseSelectionUrl(url) || QnuEndpointPolicy.isTimetableUrl(url)) {
                                    view.evaluateJavascript("""
                                        (() => {
                                          function read(doc, depth) {
                                            const field = doc.querySelector('input[name="xh"],input[id="xh"],input[name="studentId"]');
                                            if (field && /^\d{9,12}$/.test(field.value.trim())) return field.value.trim();
                                            const match = (doc.body?.innerText || '').match(/学号\s*[:：]?\s*(\d{9,12})(?!\d)/);
                                            if (match) return match[1];
                                            if (depth < 3) for (const frame of doc.querySelectorAll('iframe,frame')) {
                                              try { const value = read(frame.contentDocument, depth + 1); if (value) return value; } catch (_) {}
                                            }
                                            return '';
                                          }
                                          return read(document, 0);
                                        })()
                                    """.trimIndent()) { encoded ->
                                        val number = runCatching { JSONTokener(encoded).nextValue() as? String }.getOrNull().orEmpty()
                                        cn.qztc.timetable.feature.settings.CourseArrangementProfile.record(context, number.ifBlank { accountUser })
                                    }
                                }
                                when {
                                    QnuEndpointPolicy.isPortalMainUrl(url) -> {
                                        status = "登录成功，正在进入教务系统…"
                                        installPortalSsoBridge(view)
                                    }
                                    QnuEndpointPolicy.isAuthenticationUrl(url) -> {
                                        status = if (savedCredentials == null) {
                                            "请在页面中完成登录和验证码"
                                        } else {
                                            "已从加密设置中填入账号，请确认后登录"
                                        }
                                        if (automaticCheck || automaticImport) {
                                            if (savedCredentials == null) onCheckFailed("尚未保存教务账号，请先在登录教务中保存")
                                            else if (!loginSubmitted) {
                                                loginSubmitted = true
                                                autofillCredentials(view, savedCredentials, autoSubmit = true)
                                                scope.launch {
                                                    delay(15_000)
                                                    if (QnuEndpointPolicy.isAuthenticationUrl(currentUrl)) onCheckFailed("登录未完成，请在登录教务中检查账号或完成验证码")
                                                }
                                            }
                                        } else savedCredentials?.let { autofillCredentials(view, it) }
                                    }
                                    QnuEndpointPolicy.isAcademicHomeUrl(url) -> {
                                        status = "正在打开学期列表…"
                                        view.post { view.loadUrl(QnuEndpointPolicy.CURRENT_TIMETABLE_ENTRY_URL) }
                                    }
                                    QnuEndpointPolicy.isCourseSelectionUrl(url) -> {
                                        readSemesterCatalog(view) { catalog ->
                                            if(view!==webView)return@readSemesterCatalog
                                            semesterCatalog = catalog
                                            val pageSemester = catalog.selectedSemester()
                                            val requested = pendingSemester
                                            when {
                                                importStarted -> {
                                                    if (selectedSemester == null) selectedSemester = pageSemester
                                                }
                                                (automaticCheck || automaticImport) && !checkStarted -> {
                                                    selectedSemester = checkSemester?.let { target -> catalog.semester(target.schoolYear, target.term) }
                                                        ?: if (checkSemester == null) catalog.latestSemester() else null
                                                    if (selectedSemester == null) onCheckFailed(if (checkSemester == null) "未找到最新学期的课表入口" else "未找到所选学期的课表入口")
                                                    else { checkStarted = true; startImport() }
                                                }
                                                automaticCheck || automaticImport -> Unit
                                                requested != null && requested.sameSchoolIds(pageSemester) -> {
                                                    pendingSemester = null
                                                    selectedSemester = requested
                                                    status = "已选择 ${requested.label}，正在打开${selectedViewKind.label}…"
                                                    scope.launch {
                                                        delay(100)
                                                        openView(selectedViewKind)
                                                    }
                                                }
                                                else -> {
                                                    selectedSemester = pageSemester
                                                    status = "请选择要导入的学期"
                                                    if (!semesterPickerOffered) {
                                                        semesterPickerOffered = true
                                                        showSemesterPicker = true
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    QnuEndpointPolicy.isTimetableUrl(url) -> {
                                        rememberSession(url)
                                        if (!importStarted) {
                                            status = when (capture.capture(url)) {
                                                is QnuSessionCaptureResult.Captured -> "已到达课表页，选择学期后可以导入"
                                                QnuSessionCaptureResult.NoActiveSession -> "未找到登录会话，请重新登录"
                                                QnuSessionCaptureResult.RejectedUrl -> "当前页面地址不受信任"
                                            }
                                        }
                                    }
                                }
                            }

                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                if (request.isForMainFrame) {
                                    diagnostics.record("page_network_error")
                                    pageLoading = false
                                    intranetGate = IntranetGate.NeedVpn
                                    if (automaticCheck) onCheckFailed("教务页面连接失败，请检查校园网或校园 VPN")
                                    status = "无法打开教务页面，请先连接校园 VPN"
                                }
                            }

                            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                                handler.cancel()
                                status = "学校页面安全连接失败，请检查系统时间和 VPN"
                                if (automaticCheck) onCheckFailed(status)
                            }
                        }
                        if (automaticCheck || automaticImport) view.loadUrl(QnuEndpointPolicy.INITIAL_WEBVIEW_URL)
                        else openQnuLogin(view)
                    }
                },
                update = { view ->
                    webView = view
                    if (currentUrl.isBlank()) view.loadUrl(QnuEndpointPolicy.INITIAL_WEBVIEW_URL)
                },
            )
            if (!backgroundCheck && (pageLoading || importStarted || capturing)) {
                Box(
                    Modifier.fillMaxSize().background(colors.solid2.copy(alpha = if (pageLoading) 0.88f else 0.32f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(color = colors.accent)
                        Text(status, style = QztcTypography.body, color = colors.ink)
                    }
                }
            }
        }
    }

    val catalog = semesterCatalog
    if (showSemesterPicker && catalog != null) {
        QnuSemesterPickerSheet(
            catalog = catalog,
            selected = selectedSemester,
            viewKind = selectedViewKind,
            onViewKindChange = { selectedViewKind = it },
            onSelect = { semester ->
                val selectionUrl = QnuEndpointPolicy.courseSelectionUrl(semester.yearId, semester.termId)
                if (selectionUrl == null) {
                    status = "学校返回的学期参数无效"
                } else {
                    showSemesterPicker = false
                    operationError = null
                    pendingSemester = semester
                    selectedSemester = semester
                    importStarted = false
                    capturedSession = null
                    personalBuffer = null
                    classBuffer = null
                    status = "已选 ${semester.label}，正在准备读取课表"
                    pageLoading = true
                    webView?.loadUrl(selectionUrl)
                }
            },
            onDismiss = { showSemesterPicker = false },
        )
    }
}

/** Clear only live cookies of the two school hosts; encrypted saved credentials stay available. */
private fun openQnuLogin(view: WebView) {
    val cookies = CookieManager.getInstance()
    listOf("https://jw.qztc.edu.cn/academic/index_frame.jsp", "https://id.qztc.edu.cn/portal/login.html", "https://id.qztc.edu.cn/authserver/login").forEach { url ->
        cookies.getCookie(url).orEmpty().split(';').map { it.substringBefore('=').trim() }.filter { it.isNotBlank() }.forEach { name ->
            listOf("/", "/academic", "/academic/", "/portal", "/portal/", "/authserver", "/authserver/").forEach { path ->
                cookies.setCookie(url, "$name=; Path=$path; Max-Age=0; Secure")
            }
        }
    }
    cookies.flush()
    view.loadUrl(QnuEndpointPolicy.INITIAL_WEBVIEW_URL)
}

private enum class IntranetGate { Checking, NeedVpn, Ready }

/** Captures the full school page at a desktop-sized width, rather than only the visible WebView. */
internal suspend fun captureFullWebPage(view: WebView, redactForVerification: Boolean = false): Bitmap = suspendCancellableCoroutine { continuation ->
    val originalWidth = view.width.coerceAtLeast(1)
    val originalHeight = view.height.coerceAtLeast(1)
    val originalScrollX = view.scrollX
    val originalScrollY = view.scrollY
    // Give the page a desktop-like CSS viewport only during capture. The
    // original mobile size is restored before this function returns.
    val captureWidth = DESKTOP_CAPTURE_WIDTH_PX.coerceAtMost(MAX_CAPTURE_WIDTH_PX)
    val prepareScript = """
        (() => {
          const docs = [];
          const visit = (doc) => {
            if (!doc || docs.includes(doc) || docs.length >= 32) return;
            docs.push(doc);
            for (const frame of doc.querySelectorAll('iframe,frame')) {
              try { visit(frame.contentDocument); } catch (_) {}
            }
          };
          visit(document);
          if ($redactForVerification) {
            for (const doc of docs) {
              if (!doc.body) continue;
              const walker = doc.createTreeWalker(doc.body, NodeFilter.SHOW_TEXT);
              const textNodes = [];
              while (walker.nextNode()) if (!['SCRIPT','STYLE','NOSCRIPT'].includes(walker.currentNode.parentElement?.tagName)) textNodes.push(walker.currentNode);
              for (const node of textNodes) node.textContent = node.textContent.replace(/[\u3400-\u9fff]/g,'示').replace(/[A-Za-z]/g,'x').replace(/[0-9]/g,'0');
              for (const input of doc.querySelectorAll('input')) if(input.type !== 'hidden') input.value='';
              for (const img of doc.querySelectorAll('img')) img.style.visibility='hidden';
            }
            window.__qztcRedactedCaptureNodes = 1;
          }
          const elements = docs.flatMap(doc => [doc.documentElement, doc.body, ...doc.querySelectorAll('*')])
            .filter((el, index, list) => el && list.indexOf(el) === index);
          window.__qztcCaptureStyles = [];
          for (const el of elements.reverse()) {
            const style = el.ownerDocument.defaultView.getComputedStyle(el);
            let frameDocument = null;
            try { frameDocument = el.contentDocument; } catch (_) {}
            const frameHeight = frameDocument?.documentElement ? Math.max(
              frameDocument.documentElement.scrollHeight || 0,
              frameDocument.body?.scrollHeight || 0,
            ) : 0;
            if (frameHeight > 0) {
              if(!window.__qztcCaptureStyles.some(item=>item.el===el)) window.__qztcCaptureStyles.push({el, value: el.getAttribute('style')});
              el.style.setProperty('height', frameHeight + 'px', 'important');
              el.style.setProperty('max-height', 'none', 'important');
            }
            if (el.scrollHeight <= el.clientHeight + 2 && frameHeight <= 0 && !['auto', 'scroll'].includes(style.overflowY)) continue;
            if(!window.__qztcCaptureStyles.some(item=>item.el===el)) window.__qztcCaptureStyles.push({el, value: el.getAttribute('style')});
            el.style.setProperty('height', Math.max(el.scrollHeight, el.clientHeight) + 'px', 'important');
            el.style.setProperty('max-height', 'none', 'important');
            el.style.setProperty('overflow', 'visible', 'important');
          }
          for (const doc of docs) {
            for(const el of [doc.documentElement,doc.body].filter(Boolean)) if(!window.__qztcCaptureStyles.some(item=>item.el===el)) window.__qztcCaptureStyles.push({el,value:el.getAttribute('style')});
            doc.documentElement?.style.setProperty('overflow', 'visible', 'important');
            doc.body?.style.setProperty('overflow', 'visible', 'important');
          }
          // Chromium composites iframes as separate surfaces. Expanding an
          // iframe can otherwise stretch its old visible frame in an offscreen
          // capture. Flatten same-origin frame content into a temporary styled
          // snapshot so the complete timetable participates in the top-level
          // document draw.
          window.__qztcCaptureFrameSnapshots = [];
          const copyComputedStyle = (source, target) => {
            const style = source.ownerDocument.defaultView.getComputedStyle(source);
            for (let i = 0; i < style.length; i++) {
              const name = style[i];
              target.style.setProperty(name, style.getPropertyValue(name), style.getPropertyPriority(name));
            }
            if ('value' in source && 'value' in target) target.value = source.value;
            if (source.tagName === 'IMG' && target.tagName === 'IMG') {
              target.src = source.currentSrc || source.src;
              target.removeAttribute('srcset');
            }
          };
          for (const frame of document.querySelectorAll('iframe,frame')) {
            let frameDocument = null;
            try { frameDocument = frame.contentDocument; } catch (_) {}
            if (!frameDocument?.body) continue;
            const snapshot = document.createElement('div');
            snapshot.dataset.qztcFrameSnapshot = 'true';
            for (const child of frameDocument.body.childNodes) snapshot.appendChild(child.cloneNode(true));
            const sources = [frameDocument.body, ...frameDocument.body.querySelectorAll('*')];
            const targets = [snapshot, ...snapshot.querySelectorAll('*')];
            for (let i = 0; i < Math.min(sources.length, targets.length); i++) copyComputedStyle(sources[i], targets[i]);
            const rect = frame.getBoundingClientRect();
            snapshot.style.setProperty('width', rect.width + 'px', 'important');
            snapshot.style.setProperty('min-height', frameDocument.documentElement.scrollHeight + 'px', 'important');
            snapshot.style.setProperty('overflow', 'visible', 'important');
            frame.replaceWith(snapshot);
            window.__qztcCaptureFrameSnapshots.push({snapshot, frame});
          }
          return 'ok';
        })()
    """.trimIndent()
    val metricsScript = """
        (() => {
          const docs = [];
          const visit = (doc) => {
            if (!doc || docs.includes(doc) || docs.length >= 32) return;
            docs.push(doc);
            for (const frame of doc.querySelectorAll('iframe,frame')) {
              try { visit(frame.contentDocument); } catch (_) {}
            }
          };
          visit(document);
          const root = document.documentElement;
          const body = document.body;
          const elements = docs.flatMap(doc => [doc.documentElement, doc.body, ...doc.querySelectorAll('*')]);
          const right = elements.reduce((value, el) => Math.max(value, (el.getBoundingClientRect?.().right || 0) + window.scrollX), 0);
          const bottom = elements.reduce((value, el) => Math.max(value, (el.getBoundingClientRect?.().bottom || 0) + window.scrollY), 0);
          return JSON.stringify({
            width: Math.max(root?.scrollWidth || 0, body?.scrollWidth || 0, right),
            height: Math.max(root?.scrollHeight || 0, body?.scrollHeight || 0, bottom),
            viewportWidth: window.innerWidth || root?.clientWidth || 0
          });
        })()
    """.trimIndent()
    val restoreScript = """
        (() => {
          for (const item of window.__qztcCaptureFrameSnapshots || []) item.snapshot.replaceWith(item.frame);
          delete window.__qztcCaptureFrameSnapshots;
          for (const item of window.__qztcCaptureStyles || []) {
            if (item.value == null) item.el.removeAttribute('style');
            else item.el.setAttribute('style', item.value);
          }
          delete window.__qztcCaptureStyles;
          return 'ok';
        })()
    """.trimIndent()

    fun restoreView() {
        view.evaluateJavascript(restoreScript, null)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(originalWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(originalHeight, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, originalWidth, originalHeight)
        view.scrollTo(originalScrollX, originalScrollY)
    }

    fun fail(error: Throwable) {
        restoreView()
        if (continuation.isActive) continuation.resumeWith(Result.failure(error))
    }

    continuation.invokeOnCancellation { view.post(::restoreView) }

    view.post {
        if (!continuation.isActive) return@post
        view.measure(
            View.MeasureSpec.makeMeasureSpec(captureWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(originalHeight, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, captureWidth, originalHeight)
        view.scrollTo(0, 0)
        view.evaluateJavascript(prepareScript) {
            view.postDelayed({
                view.evaluateJavascript(metricsScript) { encoded ->
                if (!continuation.isActive) {
                    restoreView()
                    return@evaluateJavascript
                }
                runCatching {
                    check(!encoded.isNullOrBlank() && encoded != "null") { "无法读取页面尺寸" }
                    val parsed = JSONTokener(encoded).nextValue()
                    val root = when (parsed) {
                        is JSONObject -> parsed
                        is String -> JSONObject(parsed)
                        else -> error("无法读取页面尺寸")
                    }
                    val viewportWidth = root.optDouble("viewportWidth", 0.0)
                    @Suppress("DEPRECATION") val pageScale = if (viewportWidth > 0.0) {
                        captureWidth.toDouble() / viewportWidth
                    } else {
                        view.scale.toDouble()
                    }
                    val pageHeight = (root.optDouble("height", 0.0)*pageScale).roundToInt().coerceAtLeast(originalHeight)
                    val pageWidth = (root.optDouble("width", 0.0)*pageScale).roundToInt().coerceAtLeast(captureWidth)
                    check(pageWidth in 1..16000 && pageHeight in 1..60000) { "课表页面异常过大，请重试" }
                    val scaleDown=minOf(1.0,MAX_CAPTURE_WIDTH_PX.toDouble()/pageWidth,MAX_CAPTURE_HEIGHT_PX.toDouble()/pageHeight,
                        kotlin.math.sqrt(MAX_CAPTURE_PIXELS.toDouble()/(pageWidth.toLong()*pageHeight)))
                    CaptureBitmapSize(
                        width = (pageWidth * scaleDown).toInt().coerceAtLeast(1),
                        height = (pageHeight * scaleDown).toInt().coerceAtLeast(1),
                    )
                }.onSuccess { bitmapSize ->
                    fun drawPicture(attempt: Int) {
                        if (!continuation.isActive) { restoreView(); return }
                        view.measure(
                            View.MeasureSpec.makeMeasureSpec(captureWidth, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(originalHeight, View.MeasureSpec.EXACTLY),
                        )
                        view.layout(0, 0, captureWidth, originalHeight)
                        view.scrollTo(0, 0)
                        @Suppress("DEPRECATION")
                        val picture = view.capturePicture()
                        if (picture.width <= 0 || picture.height <= 0) {
                            if (attempt < MAX_CAPTURE_PICTURE_ATTEMPTS) {
                                view.invalidate()
                                view.postDelayed({ drawPicture(attempt + 1) }, CAPTURE_PICTURE_RETRY_MS)
                            } else {
                                fail(IllegalStateException("无法生成完整课表截图"))
                            }
                            return
                        }
                        runCatching {
                            val bitmap = Bitmap.createBitmap(
                                bitmapSize.width,
                                bitmapSize.height,
                                Bitmap.Config.ARGB_8888,
                            )
                            Canvas(bitmap).drawPicture(
                                picture,
                                android.graphics.RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()),
                            )
                            bitmap
                        }.onSuccess { bitmap ->
                            restoreView()
                            continuation.resumeWith(Result.success(bitmap))
                        }.onFailure(::fail)
                    }
                    view.invalidate()
                    view.postVisualStateCallback(
                        System.nanoTime(),
                        object : WebView.VisualStateCallback() {
                            override fun onComplete(requestId: Long) {
                                view.post { drawPicture(0) }
                            }
                        },
                    )
                }.onFailure(::fail)
                }
            }, CAPTURE_LAYOUT_SETTLE_MS)
        }
    }
}

private const val DESKTOP_CAPTURE_WIDTH_PX = 1920
private const val MAX_CAPTURE_WIDTH_PX = 4096
private const val MAX_CAPTURE_HEIGHT_PX = 12000
private const val MAX_CAPTURE_PIXELS = 12_000_000L
private const val CAPTURE_LAYOUT_SETTLE_MS = 600L
private const val MAX_CAPTURE_PICTURE_ATTEMPTS = 6
private const val CAPTURE_PICTURE_RETRY_MS = 180L

private data class CaptureBitmapSize(val width: Int, val height: Int)

internal fun autofillCredentials(view: WebView, credentials: QnuSavedCredentials, force: Boolean = false, autoSubmit: Boolean = false) {
    val username = JSONObject.quote(credentials.username)
    val password = JSONObject.quote(credentials.password)
    view.evaluateJavascript(
        """
        (() => {
          if (window.__qnuCredentialAutofillStarted && !$force) return;
          clearInterval(window.__qnuCredentialAutofillTimer);
          window.__qnuCredentialAutofillStarted = true;
          const setValue = (element, value) => {
            if (!element) return;
            const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set;
            if (setter) setter.call(element, value); else element.value = value;
            element.dispatchEvent(new Event('input', { bubbles: true }));
            element.dispatchEvent(new Event('change', { bubbles: true }));
          };
          const documents = (root) => {
            const result = [root];
            for (const frame of root.querySelectorAll('frame, iframe')) {
              try {
                if (frame.contentDocument) result.push(...documents(frame.contentDocument));
              } catch (_) {}
            }
            return result;
          };
          let attempts = 0;
          const fill = () => {
            attempts += 1;
            let filled = false;
            try {
              for (const doc of documents(document)) {
                const password = doc.querySelector('input[type="password"]');
                const username = doc.querySelector(
                  'input[autocomplete="username"], input[name*="username" i], input[id*="username" i], input[name*="user" i], input[id*="user" i], input[name*="account" i], input[id*="account" i], input[placeholder*="账号"], input[placeholder*="学号"]'
                );
                if (username && password) {
                  setValue(username, $username);
                  setValue(password, $password);
                  if ($autoSubmit && !window.__qnuAutoLoginSubmitted) {
                    const visible = el => !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length);
                    const captcha = [...doc.querySelectorAll('input')].find(el => visible(el) && /captcha|验证码|校验码/i.test([el.name,el.id,el.placeholder].join(' ')) && !el.value);
                    const login = [...doc.querySelectorAll('button,input[type="submit"],input[type="button"],a')].find(el => visible(el) && !el.disabled && /^(登录|登 录|登 陆|Login|Sign in)$/i.test((el.textContent || el.value || '').trim()));
                    if (login && !captcha) {
                      window.__qnuAutoLoginSubmitted = true;
                      setTimeout(() => login.click(), 100);
                    }
                  }
                  filled = true;
                  break;
                }
              }
            } catch (_) {}
            if (filled || attempts >= 80) clearInterval(timer);
          };
          const timer = window.__qnuCredentialAutofillTimer = setInterval(fill, 250);
          fill();
        })();
        """.trimIndent(),
        null,
    )
}

/** The school portal opens a blank popup and posts its SSO form into it; keep that POST in this WebView. */
internal fun installPortalSsoBridge(view: WebView) {
    view.evaluateJavascript(
        """
        (() => {
          if (window.__qnuPortalBridgeInstalled) return;
          window.__qnuPortalBridgeInstalled = true;
          let attempts = 0;
          let launched = false;
          const patch = () => {
            attempts += 1;
            try {
              const menuFrame = document.querySelector('iframe#menuFrame, frame#menuFrame, iframe[name="menuFrame"], frame[name="menuFrame"]');
              if (menuFrame?.contentWindow) {
                menuFrame.contentWindow.open = () => window.top;
                const entry = menuFrame.contentDocument?.querySelector('li[title*="教务管理系统"]');
                if (entry && !launched) {
                  launched = true;
                  clearInterval(timer);
                  entry.click();
                  return;
                }
              }
            } catch (_) {}
            if (attempts >= 120) clearInterval(timer);
          };
          const timer = setInterval(patch, 250);
          patch();
        })();
        """.trimIndent(),
        null,
    )
}

internal data class QnuSchoolOption(val id: String, val label: String)

internal data class QnuSchoolSemester(
    val yearId: String,
    val yearLabel: String,
    val termId: String,
    val termLabel: String,
) {
    val label: String = "$yearLabel $termLabel"
    val semesterId: String? = QnuSemesterFactory.id(yearLabel, termLabel)
}

internal data class QnuSchoolSemesterCatalog(
    val years: List<QnuSchoolOption>,
    val terms: List<QnuSchoolOption>,
    val selectedYearId: String,
    val selectedTermId: String,
) {
    fun semester(year: Int, term: cn.qztc.timetable.core.domain.model.Term): QnuSchoolSemester? {
        val schoolYear = years.singleOrNull { it.label.trim() == year.toString() } ?: return null
        val schoolTerm = terms.singleOrNull { QnuSemesterFactory.term(it.label) == term } ?: return null
        return QnuSchoolSemester(schoolYear.id, schoolYear.label, schoolTerm.id, schoolTerm.label)
    }
    fun latestSemester(today: LocalDate = LocalDate.now()): QnuSchoolSemester? {
        val currentYear = if (today.monthValue == 1) today.year - 1 else today.year
        val currentTerm = when (today.monthValue) { in 2..5 -> 0; in 6..7 -> 1; else -> 2 }
        return years.flatMap { year -> terms.map { term -> QnuSchoolSemester(year.id, year.label, term.id, term.label) } }
            .filter { candidate ->
                val year = candidate.yearLabel.toIntOrNull() ?: return@filter false
                val term = QnuSemesterFactory.term(candidate.termLabel)?.ordinal ?: return@filter false
                candidate.semesterId != null && (year < currentYear || year == currentYear && term <= currentTerm)
            }
            .maxWithOrNull(compareBy({ it.yearLabel.toIntOrNull() ?: 0 }, { QnuSemesterFactory.term(it.termLabel)?.ordinal ?: -1 }))
    }
    fun selectedSemester(): QnuSchoolSemester? {
        val year = years.firstOrNull { it.id == selectedYearId } ?: return null
        val term = terms.firstOrNull { it.id == selectedTermId } ?: return null
        return QnuSchoolSemester(year.id, year.label, term.id, term.label)
    }
}

private fun QnuSchoolSemester.sameSchoolIds(other: QnuSchoolSemester?): Boolean =
    other != null && yearId == other.yearId && termId == other.termId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QnuSemesterPickerSheet(
    catalog: QnuSchoolSemesterCatalog,
    selected: QnuSchoolSemester?,
    viewKind: QnuTimetableKind,
    onViewKindChange: (QnuTimetableKind) -> Unit,
    onSelect: (QnuSchoolSemester) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalQztcColors.current
    val years = remember(catalog) {
        val currentYear = LocalDate.now().year
        catalog.years
            .filter { option ->
                val year = option.label.take(4).toIntOrNull()
                year == null || year in (currentYear - 5)..currentYear
            }
            .sortedByDescending { it.label.toIntOrNull() ?: Int.MIN_VALUE }
    }
    val initialYearIndex = remember(years, catalog.selectedYearId) {
        years.indexOfFirst { it.id == catalog.selectedYearId }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialYearIndex)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = QztcSpacing.xxl)) {
            Text(
                "选择查看学期",
                style = QztcTypography.pageTitle,
                color = colors.ink,
                modifier = Modifier.padding(horizontal = QztcSpacing.xl, vertical = QztcSpacing.sm),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = QztcSpacing.lg, vertical = QztcSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(QztcSpacing.xs),
            ) {
                listOf(QnuTimetableKind.PERSONAL, QnuTimetableKind.CLASS, QnuTimetableKind.ARRANGEMENT).forEach { kind ->
                    TextButton(
                        onClick = { onViewKindChange(kind) },
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (kind == viewKind) colors.accentSoft else Color.Transparent,
                                RoundedCornerShape(12.dp),
                            ),
                    ) {
                        Text(kind.label, color = if (kind == viewKind) colors.accentDeep else colors.mut)
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                state = listState,
            ) {
                items(years, key = QnuSchoolOption::id) { year ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = QztcSpacing.lg, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            year.label,
                            style = QztcTypography.body,
                            color = colors.ink,
                            modifier = Modifier.padding(start = QztcSpacing.sm).weight(1f),
                        )
                        catalog.terms.forEach { term ->
                            val option = QnuSchoolSemester(year.id, year.label, term.id, term.label)
                            val isSelected = option.sameSchoolIds(selected)
                            TextButton(
                                onClick = { onSelect(option) },
                                modifier = Modifier
                                    .padding(horizontal = 1.dp)
                                    .background(
                                        if (isSelected) colors.accentSoft else Color.Transparent,
                                        RoundedCornerShape(12.dp),
                                    ),
                            ) {
                                Text(
                                    term.label,
                                    color = if (isSelected) colors.accentDeep else colors.mut,
                                    style = QztcTypography.body,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun readSemesterCatalog(view: WebView, onResult: (QnuSchoolSemesterCatalog) -> Unit) {
    view.evaluateJavascript(
        """
        (() => JSON.stringify({
          years: Array.from(document.querySelector('select[name="year"]')?.options || [])
            .map(option => ({ id: option.value, label: option.textContent.trim() })),
          terms: Array.from(document.querySelector('select[name="term"]')?.options || [])
            .map(option => ({ id: option.value, label: option.textContent.trim() })),
          selectedYearId: document.querySelector('select[name="year"]')?.value || '',
          selectedTermId: document.querySelector('select[name="term"]')?.value || ''
        }))()
        """.trimIndent(),
    ) { encoded ->
        runCatching {
            val jsonText = JSONTokener(encoded).nextValue() as String
            val root = JSONObject(jsonText)
            val years = root.getJSONArray("years").let { array ->
                List(array.length()) { index ->
                    array.getJSONObject(index).let { QnuSchoolOption(it.getString("id"), it.getString("label")) }
                }
            }
            val terms = root.getJSONArray("terms").let { array ->
                List(array.length()) { index ->
                    array.getJSONObject(index).let { QnuSchoolOption(it.getString("id"), it.getString("label")) }
                }
            }
            QnuSchoolSemesterCatalog(
                years = years,
                terms = terms,
                selectedYearId = root.getString("selectedYearId"),
                selectedTermId = root.getString("selectedTermId"),
            )
        }.getOrNull()?.takeIf { it.years.isNotEmpty() && it.terms.isNotEmpty() }?.let(onResult)
    }
}

private fun clickSchoolTimetableSync(
    view: WebView,
    kind: QnuTimetableKind,
    onResult: (Boolean) -> Unit,
) {
    val label = kind.pageButton
    view.evaluateJavascript(
        """
        (() => {
          const button = Array.from(document.querySelectorAll('input, button, a'))
            .find(element => (element.value || element.textContent || '').replace(/\s+/g, '') === '$label');
          if (!button) return false;
          window.open = (url) => {
            window.location.href = new URL(url, document.baseURI).href;
            return window;
          };
          button.click();
          return true;
        })();
        """.trimIndent(),
    ) { encoded -> onResult(encoded == "true") }
}

private suspend fun clickSchoolTimetable(view: WebView, kind: QnuTimetableKind): Boolean =
    suspendCancellableCoroutine { continuation ->
        view.post {
            clickSchoolTimetableSync(view, kind) { found ->
                if (continuation.isActive) continuation.resume(found)
            }
        }
    }

/**
 * Opens the course-arrangement/detail page when the school portal exposes it
 * as a menu item. The portal uses nested same-origin frames and occasionally
 * opens the item in a popup, so scan accessible frames and bridge window.open
 * back to the current WebView.
 */
private suspend fun openCourseArrangement(view: WebView): Boolean =
    suspendCancellableCoroutine { continuation ->
        view.post {
            view.evaluateJavascript(
                """
                (() => {
                  const docs = [];
                  const visit = (doc) => {
                    if (!doc || docs.includes(doc) || docs.length >= 32) return;
                    docs.push(doc);
                    for (const frame of doc.querySelectorAll('iframe,frame')) {
                      try { visit(frame.contentDocument); } catch (_) {}
                    }
                  };
                  visit(document);
                  const wanted = /课程安排|教学安排|课程详情|课程信息|已选课程|选课结果/;
                  const excluded = /个人课表|班级课表|查看课表|导入课程/;
                  const normalize = value => (value || '').replace(/[\\s\\u00a0\\u3000]+/g, '');
                  const candidates = [];
                  for (const doc of docs) for (const element of doc.querySelectorAll(
                    'a,button,input[type=button],input[type=submit],input[type=image],[role=button]'
                  )) {
                    const label = normalize(
                      element.textContent || element.value || element.title || element.getAttribute('aria-label')
                    );
                    const href = normalize(element.getAttribute('href'));
                    if ((!wanted.test(label) && !wanted.test(href)) || excluded.test(label)) continue;
                    const rect = element.getBoundingClientRect();
                    if (rect.width <= 0 || rect.height <= 0) continue;
                    candidates.push({ element, doc, label, href });
                  }
                  const hit = candidates[0];
                  if (!hit) return false;
                  try {
                    const open = (url) => {
                      if (url) window.top.location.href = new URL(url, document.baseURI).href;
                      else window.top.location.href = window.top.location.href;
                    };
                    for (const doc of docs) {
                      try { doc.defaultView.open = open; } catch (_) {}
                    }
                    const href = hit.element.getAttribute('href');
                    if (href && href !== '#' && !/^javascript:/i.test(href)) {
                      window.top.location.href = new URL(href, hit.doc.baseURI).href;
                    } else {
                      hit.element.click();
                    }
                    return true;
                  } catch (_) {
                    return false;
                  }
                })();
                """.trimIndent(),
            ) { encoded ->
                if (continuation.isActive) continuation.resume(encoded == "true")
            }
        }
    }

private suspend fun waitUntil(timeoutMs: Long = 45_000, predicate: () -> Boolean): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        if (predicate()) return true
        delay(200)
    }
    return predicate()
}

private suspend fun parseTimetableFromWebView(view: WebView, semesterId: String, label: String, onReason: (String) -> Unit): ImportedTimetable? {
    var reason = "$label 页面未加载或会话失效"
    var previous: String? = null
    repeat(8) { attempt ->
        delay(if (attempt == 0) 400 else 350)
        val html = readTimetableHtml(view) ?: return@repeat
        if(html == previous)return@repeat
        previous=html
        val imported = withContext(Dispatchers.Default) {
            QnuTimetableImporter.importHtml(html, semesterId)
        }
        val missingGrid = imported.diagnostics.any { diagnostic ->
            diagnostic.code == "NO_TIMETABLE" || diagnostic.code == "EMPTY_INPUT" || diagnostic.code == "DECODE_FAILED"
        }
        reason = timetableImportFailureReason(label, imported)
        if (!missingGrid && !imported.hasErrors && (imported.state.meetings.isNotEmpty() || imported.state.exceptions.isNotEmpty())) { onReason("");return imported }
    }
    onReason(reason)
    return null
}

internal fun timetableImportFailureReason(label: String, imported: ImportedTimetable): String {
    val errorCodes = imported.diagnostics
        .filter { it.severity == cn.qztc.timetable.core.domain.imports.DiagnosticSeverity.ERROR }
        .map { it.code }
        .distinct()
    return when {
        errorCodes.any { it == "NO_TIMETABLE" || it == "EMPTY_INPUT" || it == "DECODE_FAILED" } ->
            "$label 未找到课表表格，请确认已登录"
        "EMPTY_GRID" in errorCodes ->
            "${label}为空，所选学期没有课程；现有课表不会被覆盖"
        errorCodes.isNotEmpty() -> "$label 解析失败：${errorCodes.joinToString("、")}"
        imported.state.meetings.isEmpty() && imported.state.exceptions.isEmpty() ->
            "${label}为空，所选学期没有课程；现有课表不会被覆盖"
        else -> "$label 格式未识别"
    }
}

internal suspend fun readCourseProfiles(view: WebView): List<QnuCourseProfile> {
    var previous: List<QnuCourseProfile> = emptyList()
    repeat(8) {
        val html=readTimetableHtml(view,READ_COURSE_PROFILES_SCRIPT)
        val profiles=withContext(Dispatchers.Default) { html?.let(QnuTimetableParser::parseCourseProfilesHtml).orEmpty() }
        if(profiles.isNotEmpty() && profiles==previous)return profiles
        previous=profiles
        delay(300)
    }
    return previous
}

private suspend fun readTimetableHtml(view: WebView, script: String = READ_TIMETABLE_HTML_SCRIPT): String? = withTimeout(6_000) {
    val encoded = suspendCancellableCoroutine<String?> { continuation ->
        view.post {
            if(!continuation.isActive)return@post
            view.evaluateJavascript(script) { result ->
                if (continuation.isActive) continuation.resume(result)
            }
        }
    }
    withContext(Dispatchers.Default) {
        if(encoded.isNullOrBlank()||encoded=="null")null else {
            val html=JSONTokener(encoded).nextValue() as? String
            require(html!="__QZTC_TOO_LARGE__") { "课表页面过大，请重新打开学期后重试" }
            html?.takeIf { it.isNotBlank() }
        }
    }
}

private enum class QnuTimetableKind(val label: String, val pageButton: String) {
    PERSONAL("个人课表", "个人课表"),
    CLASS("班级课表", "班级课表"),
    ARRANGEMENT("课程安排", "课程安排"),
}

private const val READ_TIMETABLE_HTML_SCRIPT = """
(() => {
  const docs = [];
  const collect = (doc) => {
    if (!doc || docs.includes(doc) || docs.length >= 32) return;
    docs.push(doc);
    for (const frame of doc.querySelectorAll('iframe, frame')) {
      try { collect(frame.contentDocument); } catch (e) {}
    }
  };
  collect(document);
  const preferred = docs.find(doc => doc.querySelector('#timetable, table.infolist_hr')) || docs[0];
  const html = preferred?.documentElement?.outerHTML || '';
  return html.length > 2000000 ? '__QZTC_TOO_LARGE__' : html;
})();
"""

private const val BLOCK_TIMETABLE_LONG_PRESS_SCRIPT = """
(() => {
  const visited = new Set();
  function install(doc) {
    if (!doc || visited.has(doc)) return;
    visited.add(doc);
    try {
      if (!doc.getElementById('__qztc_disable_timetable_selection')) {
        const style = doc.createElement('style');
        style.id = '__qztc_disable_timetable_selection';
        style.textContent = 'table, table * { -webkit-user-select: none !important; user-select: none !important; -webkit-touch-callout: none !important; }';
        (doc.head || doc.documentElement).appendChild(style);
      }
      doc.addEventListener('contextmenu', event => {
        if (event.target && event.target.closest && event.target.closest('table')) {
          event.preventDefault();
          event.stopImmediatePropagation();
        }
      }, true);
      doc.addEventListener('selectstart', event => {
        if (event.target && event.target.closest && event.target.closest('table')) {
          event.preventDefault();
        }
      }, true);
      for (const frame of doc.querySelectorAll('iframe,frame')) {
        try { install(frame.contentDocument); } catch (_) {}
      }
    } catch (_) {}
  }
  install(document);
})();
"""

private class TimetablePortalWebView(context: Context) : WebView(context) {
    override fun performLongClick(): Boolean = true

    override fun performLongClick(x: Float, y: Float): Boolean = true

    override fun startActionMode(callback: ActionMode.Callback): ActionMode? = null

    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? = null
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val READ_CHANGE_NOTICES_SCRIPT = """
(() => {
  const docs=[];
  function visit(d){if(!d||docs.includes(d)||docs.length>=32)return;docs.push(d);for(const f of d.querySelectorAll('iframe,frame'))try{visit(f.contentDocument);}catch(_){}}
  visit(document);
  const gridOf=d=>d.querySelector('table#timetable')||[...d.querySelectorAll('table')].find(t=>t.querySelector('td[id="1-1"],td[id="1_1"]')||(/星期一/.test(t.textContent)&&/星期日|星期天/.test(t.textContent)));
  const d=docs.find(d=>gridOf(d));
  if(!d)return '';
  const grid=gridOf(d);
  const tables=[...d.querySelectorAll('table')].filter(t=>t!==grid&&!t.contains(grid)&&!grid.contains(t)&&(grid.compareDocumentPosition(t)&4)&&/停课|调课|补课|代课/.test(t.textContent)&&[...t.rows].some(r=>r.cells.length>=10));
  const title=[...d.querySelectorAll('#title,h1,h2,td')].find(e=>/20\d{2}.*[春夏秋].*课表|20\d{2}.*学期.*课表/.test(e.textContent)&&!e.querySelector('table'))?.textContent||d.title;
  const heading=d.createElement('div');heading.id='title';heading.textContent=title;
  return heading.outerHTML+'<table id="timetable"></table>'+tables.map(t=>t.outerHTML).join('');
})();
"""
