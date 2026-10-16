package cn.qztc.timetable.education

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.RenderProcessGoneDetail
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.TextButton
import android.view.View
import cn.qztc.timetable.core.ui.theme.QztcTopIconButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcPageTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.LocalTime
import cn.qztc.timetable.feature.timetable.ActivityEditDraft
import cn.qztc.timetable.feature.timetable.ActivityMemoEditor
import cn.qztc.timetable.feature.timetable.toEditDraft
import cn.qztc.timetable.core.domain.model.ScheduleActivity
import cn.qztc.timetable.core.domain.model.ActivityKind

internal data class AcademicCalendarEntry(
    val id: String, val title: String, val date: LocalDate,
    val time: LocalTime?, val stage: String, val timetable: Boolean,
    val kind: String = "activity",
)

internal fun readAcademicCalendarEntries(array: JSONArray, maximumEntries: Int = 500): List<AcademicCalendarEntry> {
    require(array.length() <= maximumEntries)
    return (0 until array.length()).map { index ->
        val row = array.getJSONObject(index)
        val id = row.getString("id")
        val title = row.getString("title").trim()
        require(id.length in 1..120 && title.length in 1..160)
        AcademicCalendarEntry(id, title, LocalDate.parse(row.getString("date")),
            row.optString("time").takeIf { it.isNotBlank() }?.let(LocalTime::parse),
            row.optString("stage").take(160), row.optBoolean("timetable"),
            row.optString("kind").takeIf { it in setOf("activity", "competition", "exam", "homework") }
                ?: if (id.startsWith("exam-")) "exam" else if (row.optString("origin").contains("赛事")) "competition" else "activity")
    }.also { require(it.map { entry -> entry.id }.distinct().size == it.size) }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun AcademicHubScreen(
    initialTab: String = "competitions",
    academicDays: List<cn.qztc.timetable.core.domain.calendar.AcademicDay> = emptyList(),
    initialCalendarDate: String? = null,
    onCalendarDateConsumed: () -> Unit = {},
    rootPage: Boolean = false,
    active: Boolean = true,
    semesterLabel: String,
    onBack: () -> Unit,
    onEvents: suspend (List<AcademicCalendarEntry>) -> Unit,
    onSchoolCalendar: () -> Unit = {},
    onActivityManagement: () -> Unit = {},
    onPluginMarket: () -> Unit = {},
    activities: List<cn.qztc.timetable.core.domain.model.ScheduleActivity> = emptyList(),
    semester: cn.qztc.timetable.core.domain.model.Semester? = null,
    activityPresetColors: List<Int> = emptyList(),
    onSaveActivity: (ActivityEditDraft) -> Unit = {},
    onDeleteActivity: (String) -> Unit = {},
    onCoverBottomBar: (Boolean) -> Unit = {},
    onContentReady: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = LocalQztcColors.current
    val scope = rememberCoroutineScope()
    val academicPluginRevision = cn.qztc.timetable.plugin.rememberAcademicRecordsRevision()
    val academicPluginInstalled = academicPluginRevision > 0L
    var agendaRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val prefs = context.getSharedPreferences("academic-agenda", android.content.Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == "entries") agendaRevision++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val back by rememberUpdatedState(onBack)
    val contentCommitted by rememberUpdatedState(onContentReady)
    val saveEvents by rememberUpdatedState(onEvents)
    val schoolCalendar by rememberUpdatedState(onSchoolCalendar)
    val latestActivities by rememberUpdatedState(activities)
    val latestSaveActivity by rememberUpdatedState(onSaveActivity)
    var view by remember { mutableStateOf<WebView?>(null) }
    var title by remember { mutableStateOf(if (initialTab == "calendar") "学年日历" else "赛事与考证") }
    var detailTitle by remember { mutableStateOf<String?>(null) }
    var calendarAway by remember { mutableStateOf(false) }
    var calendarLabel by remember { mutableStateOf(LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("M.d"))) }
    var navigationState by rememberSaveable { mutableStateOf<String?>(null) }
    var pdfUrl by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var calendarMenuExpanded by remember { mutableStateOf(false) }
    var addingActivityDate by rememberSaveable { mutableStateOf<String?>(null) }
    var editingActivityId by rememberSaveable { mutableStateOf<String?>(null) }
    val addActivity by rememberUpdatedState<(LocalDate) -> Unit>({ date ->
        addingActivityDate = date.toString()
    })
    DisposableEffect(active, addingActivityDate != null || editingActivityId != null) {
        onCoverBottomBar(active && (addingActivityDate != null || editingActivityId != null))
        onDispose { onCoverBottomBar(false) }
    }
    var exportText by remember { mutableStateOf("") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(exportText) }
                    ?: error("无法保存日历")
            } }
            error = result.exceptionOrNull()?.message
        }
    }
    var cardColorRevision by remember {mutableIntStateOf(0)}
    DisposableEffect(context) {
        val prefs=cn.qztc.timetable.feature.settings.CalendarCardColors.preferences(context)
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener {_,_->cardColorRevision++}
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    val configuration = remember(context, semesterLabel, colors, activities, academicDays, semester, agendaRevision, academicPluginInstalled, activityPresetColors,cardColorRevision) { JSONObject().apply {
        put("academicPluginInstalled", academicPluginInstalled)
        put("activityPresetColors", JSONArray(activityPresetColors.map { cn.qztc.timetable.feature.timetable.activityColorCss(it) }))
        put("activityColors",JSONObject(cn.qztc.timetable.feature.settings.CalendarCardColors.read(context)))
        put("semester", semesterLabel)
        put("homeworkEvents", JSONArray().apply {
            AcademicAgendaStore(context).read().filter { it.kind == "homework" }.forEach { entry -> put(JSONObject().apply {
                put("id", entry.id); put("title", entry.title); put("date", entry.date.toString())
                put("time", entry.time?.toString().orEmpty()); put("stage", entry.stage)
                put("kind", "homework"); put("origin", "课程作业"); put("timetable", false)
            }) }
        })
        val schoolYear = cn.qztc.timetable.feature.settings.CourseArrangementProfile.schoolYear()
        val school = cn.qztc.timetable.feature.settings.SchoolCalendarApi.cached(context, schoolYear, "academic-calendar")
        val inclusion = context.getSharedPreferences("calendar-inclusion", android.content.Context.MODE_PRIVATE)
        put("schoolEvents", JSONArray().apply {
            cn.qztc.timetable.feature.settings.calendarImportEvents(school?.events.orEmpty())
                .forEach { event -> put(JSONObject().apply {
                put("id", "school:${event.id}"); put("title", event.name)
                put("date", event.startDate.toString()); put("startDate", event.startDate.toString()); put("endDate", event.endDate?.toString())
                put("time", ""); put("stage", event.detail); put("origin", "学校校历")
                put("timetable", false)
            }) }
            (school?.days.orEmpty() + academicDays).distinctBy { "${it.date}:${it.kind}" }?.forEach { day -> put(JSONObject().apply {
                put("id", "school-day:${day.id}"); put("title", day.name); put("date", day.date.toString())
                put("time", ""); put("stage", if (day.kind == cn.qztc.timetable.core.domain.calendar.AcademicDayKind.HOLIDAY) "放假" else "调课")
                put("origin", "学校校历"); put("timetable", false)
            }) }
        })
        put("activities", JSONArray().apply {
            activities.filterNot { it.id.startsWith("academic:") || it.hiddenFromSchedule ||
                (it.id.startsWith("calendar-event:") && school?.events.orEmpty().any { event ->
                    it.id.endsWith(":${event.id}") || it.id.endsWith(":${event.id}:end")
                }) }.forEach { activity ->
                val memo = activity.memoTime
                if (memo != null) memo.slots().forEachIndexed { slotIndex, slot -> slot.dates().forEach { date -> put(JSONObject().apply {
                    put("id", "native:${activity.id}:$slotIndex:$date"); put("activityId", activity.id)
                    put("title", activity.name); put("date", date.toString())
                    activity.color?.let { put("color", cn.qztc.timetable.feature.timetable.activityColorCss(it)) }
                    put("startDate", slot.date?.toString()); put("endDate", slot.endDate?.toString())
                    put("time", if (date == slot.date) slot.minute?.let { "%02d:%02d".format(it / 60, it % 60) }.orEmpty() else "")
                    put("timeRange", if (slot.endDate != null && slot.endDate != slot.date) slot.label()
                        else if (slot.endDate == slot.date && slot.minute != null && slot.endMinute != null)
                        "%02d:%02d — %02d:%02d".format(slot.minute!! / 60, slot.minute!! % 60, slot.endMinute!! / 60, slot.endMinute!! % 60) else "")
                    put("stage", activity.note); put("content",activity.note);put("location",memo.location)
                    put("startTime",slot.minute?.let {"%02d:%02d".format(it/60,it%60)}.orEmpty())
                    put("endTime",slot.endMinute?.let {"%02d:%02d".format(it/60,it%60)}.orEmpty())
                    put("origin", "手动活动"); put("timetable", false)
                }) } }
                activity.effectiveSessions().forEach { session ->
                    val dates = session.date?.let { listOf(it) } ?: semester?.let { term -> session.weeks.map {
                        cn.qztc.timetable.core.domain.calendar.SemesterCalendar.dateOf(term, it, session.weekday ?: 1)
                    } }.orEmpty()
                    dates.forEach { date -> put(JSONObject().apply {
                    put("id", "native:${session.id}:$date"); put("activityId", activity.id); put("title", activity.name); put("date", date.toString())
                    activity.color?.let { put("color", cn.qztc.timetable.feature.timetable.activityColorCss(it)) }
                    put("startDate", activity.startDate?.toString()); put("endDate", activity.endDate?.toString())
                    put("time", ""); put("stage", activity.note); put("origin", if(activity.id.startsWith("calendar-event:")) "学校校历" else "手动活动"); put("timetable", cn.qztc.timetable.core.domain.schedule.CalendarActivityDisplay.inTimetable(activity))
                }) } }
            }
        })
        put("undatedActivities", JSONArray().apply {
            activities.filter { it.memoTime?.let {memo ->memo.date==null&&memo.recurrence==null&&memo.repeat==null} == true }.forEach { activity ->
                put(JSONObject().put("activityId", activity.id).put("title", activity.name).put("stage",activity.note)
                    .put("content",activity.note).put("location",activity.memoTime?.location.orEmpty())
                    .put("color", activity.color?.let { cn.qztc.timetable.feature.timetable.activityColorCss(it) }))
            }
        })
        put("notificationsEnabled", androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled())
        put("colors", JSONObject().apply {
            fun putColor(key: String, value: androidx.compose.ui.graphics.Color) =
                put(key, "#%06x".format(value.toArgb() and 0xffffff))
            putColor("--base", colors.solid2); putColor("--surface", colors.solid)
            putColor("--ink", colors.ink); putColor("--mut", colors.mut)
            putColor("--accent", colors.accentDeep); putColor("--soft", colors.accentSoft.compositeOver(colors.solid))
            putColor("--summary", colors.solid2); putColor("--line", colors.line2.compositeOver(colors.solid))
        })
    }.toString() }
    val configured = remember { arrayOfNulls<String>(1) }
    var runtimeReady by remember { mutableStateOf(false) }
    var contentReady by remember { mutableStateOf(false) }
    LaunchedEffect(initialCalendarDate, contentReady, active) {
        if (initialCalendarDate != null && contentReady && active) {
            val date = runCatching { LocalDate.parse(initialCalendarDate) }.getOrNull()
            if (date != null) view?.evaluateJavascript("window.nativeOpenCalendarDate?.(${JSONObject.quote(date.toString())});", null)
            onCalendarDateConsumed()
        }
    }
    var viewGeneration by remember { mutableIntStateOf(0) }
    fun configure(web: WebView, force: Boolean = false) {
        if (!runtimeReady) return
        if (force || configured[0] != configuration) {
            val restore = if (configured[0] == null) navigationState ?: "null" else "null"
            configured[0] = configuration
            web.evaluateJavascript("window.nativeAcademicConfigure?.($configuration,$restore)", null)
        }
    }
    val handleBack = {
        view?.evaluateJavascript("window.saveAcademicNavigation?.(); window.academicHandleBack?.() ?? false") {
            if (it != "true") back()
        } ?: back()
    }
    BackHandler(enabled = active, onBack = handleBack)
    val latestConfigure by rememberUpdatedState<(WebView) -> Unit>({ configure(it) })
    DisposableEffect(lifecycleOwner, active, viewGeneration) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && active) view?.let { web ->
                web.onResume(); web.visibility = if (contentReady) View.VISIBLE else View.INVISIBLE
                web.post {
                    // Resume the retained compositor without rebuilding its DOM or layout.
                    web.invalidate()
                    latestConfigure(web)
                    web.evaluateJavascript("window.nativeAcademicEnsureContent?.(${navigationState ?: "null"})", null)
                }
            } else if (event == Lifecycle.Event.ON_STOP) view?.let { web ->
                web.evaluateJavascript("window.saveAcademicNavigation?.()", null)
                web.onPause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(colors.solid2)) {
        val calendarTop = title == "学年日历" && detailTitle == null
        QztcPageTopBar(if (calendarTop) calendarLabel else detailTitle ?: title,
            centeredTitle = calendarTop,
            onTitleClick = if (calendarTop) ({ view?.evaluateJavascript("window.calendarGoToday?.()", null); Unit }) else null,
            titleTrailing = if (calendarTop && calendarAway) ({
                Text("回今日", style = cn.qztc.timetable.core.ui.theme.QztcTypography.compact, color = colors.accentDeep,
                    modifier = Modifier.clip(CircleShape).background(colors.accentSoft).clickable { view?.evaluateJavascript("window.calendarGoToday?.()", null) }.padding(horizontal = 7.dp, vertical = 3.dp))
            }) else null,
            onBack = if (rootPage && detailTitle == null) null else handleBack,
            navigation = if (calendarTop) ({ QztcTopIconButton(Icons.Outlined.Share, "分享日历", onClick = { view?.evaluateJavascript("window.exportCalendar?.()", null) }) }) else null,
            actions = {
            if (title == "学年日历" && detailTitle == null) {
                Box {
                    QztcTopIconButton(Icons.Outlined.MoreVert, "更多", onClick = { calendarMenuExpanded = true })
                    DropdownMenu(expanded = calendarMenuExpanded, onDismissRequest = { calendarMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("添加活动") }, onClick = {
                            calendarMenuExpanded = false
                            view?.evaluateJavascript("window.calendarAdd?.()", null)
                        })
                        DropdownMenuItem(text = { Text("查看校历") }, onClick = {
                            calendarMenuExpanded = false
                            schoolCalendar()
                        })
                        DropdownMenuItem(text = { Text("活动安排") }, onClick = {
                            calendarMenuExpanded = false
                            onActivityManagement()
                        })
                    }
                }
            }
        })
        error?.let { Text(it, Modifier.padding(16.dp), color = colors.danger) }
        key(viewGeneration, academicPluginRevision) { AndroidView(modifier = Modifier.weight(1f), factory = {
            if (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) WebView.setWebContentsDebuggingEnabled(true)
            WebView(context).apply {
                // Keep the previous WebView frame hidden while the academic shell restores
                // its tab and scroll state; this removes the old-page flash on entry.
                visibility = View.INVISIBLE
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setBackgroundColor(colors.solid2.toArgb())
                val loader = WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context)).build()
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(v: WebView, request: WebResourceRequest): WebResourceResponse? {
                        if (request.url.host == "appassets.androidplatform.net" && request.url.path == "/assets/academic-hub/audited-record-data.js") {
                            val script = cn.qztc.timetable.plugin.AcademicRecordsPlugin.script(context) ?: "window.AuditedRecordData={records:[],aliases:{}};".toByteArray()
                            return WebResourceResponse("application/javascript", "utf-8", ByteArrayInputStream(script))
                        }
                        loader.shouldInterceptRequest(request.url)?.let { return it }
                        if (academicPluginInstalled && request.url.host == "huanmen.com.cn" && request.url.path in setOf(
                                "/qsschedule/api/exam-notices", "/qsschedule/api/competition-notices",
                                "/qsschedule/api/official-knowledge", "/qsschedule/api/competition-catalog")) return null
                        return WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                    }
                    override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean =
                        request.url.host != "appassets.androidplatform.net"
                    override fun onPageFinished(v: WebView, url: String) {
                        runtimeReady = true
                        configure(v, force = true)
                    }
                    override fun onRenderProcessGone(v: WebView, detail: RenderProcessGoneDetail): Boolean {
                        runtimeReady = false; contentReady = false; configured[0] = null; detailTitle = null
                        viewGeneration++
                        return true
                    }
                }
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                    contentCommitted()
                    error = "请更新系统 WebView 后查看赛事"
                } else {
                    WebViewCompat.addWebMessageListener(this, "AcademicHost", setOf("https://appassets.androidplatform.net")) { web, message, _, mainFrame, _ ->
                        if (!mainFrame) return@addWebMessageListener
                        val raw = message.data ?: return@addWebMessageListener
                        if (raw.length > 300_000) return@addWebMessageListener
                        runCatching {
                            val data = JSONObject(raw)
                            when (data.getString("action")) {
                                "ready" -> {
                                    runtimeReady = true
                                    configure(web)
                                }
                                "rendered" -> {
                                    // JavaScript layout precedes Chromium's visual commit.
                                    web.postVisualStateCallback(0L, object : WebView.VisualStateCallback() {
                                        override fun onComplete(requestId: Long) {
                                            if (view !== web) return
                                            contentReady = true
                                            if (active) web.visibility = View.VISIBLE
                                            contentCommitted()
                                        }
                                    })
                                }
                                "position" -> { if (raw.length < 10_000) navigationState = data.toString() }
                                "competition-follow" -> {
                                    val names = data.getJSONArray("names")
                                    require(names.length() <= 500)
                                    CompetitionUpdates.follow(context, names)
                                }
                                "back" -> back()
                                "school-calendar" -> schoolCalendar()
                                "plugin-market" -> onPluginMarket()
                                "add-activity" -> addActivity(LocalDate.parse(data.getString("date")))
                                "edit-activity" -> {
                                    val id = data.getString("activityId")
                                    if (id.startsWith("calendar-event:")) schoolCalendar()
                                    else if (latestActivities.any { it.id == id }) editingActivityId = id
                                }
                                "tab" -> { title = if (data.optString("tab") == "calendar") "学年日历" else "赛事与考证" }
                                "activity-color" -> {
                                    val activity = latestActivities.firstOrNull { it.id == data.getString("activityId") && !it.id.startsWith("calendar-event:") }
                                    activity?.let {
                                        val selectedColor = data.optString("color").takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) }
                                        latestSaveActivity(it.toEditDraft(LocalDate.now().dayOfWeek.value, it.startDate ?: LocalDate.now(), semester?.totalWeeks ?: 20)
                                            .copy(color = selectedColor?.let(android.graphics.Color::parseColor) ?: activityPresetColors.firstOrNull() ?: 0))
                                    }
                                }
                                "calendar-node-color" -> {
                                    cn.qztc.timetable.feature.settings.CalendarCardColors.set(context,data.getString("key"),
                                        data.optString("color").takeIf {it.matches(Regex("#[0-9a-fA-F]{6}"))})
                                }
                                "calendar-date" -> { calendarLabel = data.optString("label").take(30); calendarAway = data.optBoolean("away") }
                                "detail" -> { detailTitle = data.optString("title").takeUnless { it.isBlank() || it == "null" }?.take(160) }
                                "open" -> {
                                    val uri = Uri.parse(data.getString("url"))
                                    require(uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank())
                                    if (uri.scheme == "https" && uri.path?.lowercase()?.endsWith(".pdf") == true) {
                                        pdfUrl = uri.toString()
                                    } else {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    }
                                }
                                "events" -> {
                                    val entries = readAcademicCalendarEntries(data.getJSONArray("events"))
                                    val agenda = AcademicAgendaStore(context)
                                    val retained = agenda.read().filter { it.kind == "homework" ||
                                        (!academicPluginInstalled && it.kind in setOf("competition", "exam")) }
                                    agenda.save((retained + entries.filterNot { it.kind == "homework" ||
                                        (!academicPluginInstalled && it.kind in setOf("competition", "exam")) }).distinctBy { it.id })
                                    scope.launch {
                                        error = runCatching { saveEvents(entries) }.exceptionOrNull()?.message
                                    }
                                }
                                "export" -> {
                                    // A full year also includes expanded school dates and activity sessions.
                                    val entries = readAcademicCalendarEntries(data.getJSONArray("events"), maximumEntries = 5000)
                                    exportText = academicIcs(entries)
                                    export.launch("泉师学年日历.ics")
                                }
                            }
                        }.onFailure { error = "操作未完成，请重试" }
                    }
                    loadUrl("https://appassets.androidplatform.net/assets/academic-hub/mobile.html#${initialTab.takeIf { it in setOf("competitions", "calendar", "exams") } ?: "competitions"}")
                }
                view = this
            }
        }, update = {
            val visibility = if (active && contentReady) View.VISIBLE else View.INVISIBLE
            if (it.visibility != visibility) {
                it.visibility = visibility
                if (active) it.onResume() else it.onPause()
            }
            configure(it)
        }, onRelease = {
            if (view === it) view = null
            it.stopLoading(); it.destroy()
        }) }
    }
    pdfUrl?.let { url -> AcademicPdfPreviewScreen(url = url, onBack = { pdfUrl = null }) }
    addingActivityDate?.let { selected ->
        val date = LocalDate.parse(selected)
        val initial = remember(selected) {
            ScheduleActivity(id = "", semesterId = "calendar", name = "", kind = ActivityKind.DAY,
                startDate = date, endDate = date, color = activityPresetColors.firstOrNull())
                .toEditDraft(date.dayOfWeek.value, date, semester?.totalWeeks ?: 20)
        }
        ActivityMemoEditor(title = "添加活动", editorKey = "calendar-add-$selected", initial = initial, presetColors = activityPresetColors, semester = semester,
            onPreviewReminder={popup,title,detail ->cn.qztc.timetable.reminder.activityReminderPreview(context,popup,title,detail)},
            onSystemReminderSettings={popup ->cn.qztc.timetable.reminder.openActivityReminderSettings(context,popup)},
            onBack = { addingActivityDate = null },
            onSave = { draft -> onSaveActivity(draft); addingActivityDate = null })
    }
    editingActivityId?.let { id ->
        activities.firstOrNull { it.id == id && !it.id.startsWith("calendar-event:") }?.let { activity ->
            ActivityMemoEditor(title = "编辑活动", editorKey = id, presetColors = activityPresetColors, semester = semester,
                onPreviewReminder={popup,title,detail ->cn.qztc.timetable.reminder.activityReminderPreview(context,popup,title,detail)},
                onSystemReminderSettings={popup ->cn.qztc.timetable.reminder.openActivityReminderSettings(context,popup)},
                initial = activity.toEditDraft(LocalDate.now().dayOfWeek.value, activity.startDate ?: LocalDate.now(), semester?.totalWeeks ?: 20),
                onBack = { editingActivityId = null },
                onSave = { draft -> onSaveActivity(draft); editingActivityId = null },
                onDelete = { onDeleteActivity(id); editingActivityId = null })
        }
    }
    }
}

internal fun academicIcs(entries: List<AcademicCalendarEntry>): String {
    fun escape(value: String) = value.replace("\\", "\\\\").replace("\n", "\\n").replace(";", "\\;").replace(",", "\\,")
    val format = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(java.time.ZoneOffset.UTC)
    val stamp = format.format(java.time.Instant.now())
    return buildString {
        append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//QSSchedule//Academic Calendar//ZH\r\n")
        entries.forEach { entry ->
            append("BEGIN:VEVENT\r\nUID:${escape(entry.id)}@qsschedule\r\n")
            append("DTSTAMP:$stamp\r\n")
            if (entry.time == null) {
                append("DTSTART;VALUE=DATE:${entry.date.toString().replace("-", "")}\r\n")
            } else {
                val start = entry.date.atTime(entry.time).atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant()
                append("DTSTART:${format.format(start)}\r\n")
            }
            append("SUMMARY:${escape(entry.title)}\r\nDESCRIPTION:${escape(entry.stage)}\r\nEND:VEVENT\r\n")
        }
        append("END:VCALENDAR\r\n")
    }
}
