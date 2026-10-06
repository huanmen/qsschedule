package cn.qztc.timetable.feature.settings

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.domain.calendar.AcademicDay
import cn.qztc.timetable.core.domain.calendar.AcademicDayKind
import cn.qztc.timetable.core.domain.calendar.AcademicDayRange
import cn.qztc.timetable.core.domain.calendar.AcademicDayRanges
import cn.qztc.timetable.core.domain.calendar.SemesterCalendar
import cn.qztc.timetable.core.domain.model.PeriodScheme
import cn.qztc.timetable.core.domain.model.ActivityKind
import cn.qztc.timetable.core.domain.model.ScheduleActivity
import cn.qztc.timetable.core.domain.model.Semester
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcPageTopBar
import cn.qztc.timetable.core.ui.theme.QztcSpacing
import cn.qztc.timetable.core.ui.theme.QztcTopIconButton
import cn.qztc.timetable.core.ui.theme.QztcTypography
import cn.qztc.timetable.core.ui.theme.CoursePalette
import cn.qztc.timetable.core.domain.schedule.ActivityWeekLayout
import cn.qztc.timetable.feature.timetable.ActivityEditPage
import cn.qztc.timetable.feature.timetable.toEditDraft
import cn.qztc.timetable.feature.timetable.toScheduleActivity
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SemesterScheduleSettingsScreen(
    semester: Semester,
    periodScheme: PeriodScheme,
    academicDays: List<AcademicDay> = emptyList(),
    onSave: (Semester, PeriodScheme, List<AcademicDay>) -> Unit,
    onBack: () -> Unit,
    onAcademicDaysChange: suspend (Semester, List<AcademicDay>) -> Unit = { _, _ -> },
    onImport: (() -> Unit)? = null,
    onViewScheduleChanges: () -> Unit = {},
    onCalendarEventsChange: suspend (List<SchoolCalendarEvent>) -> Unit = {},
    onCalendarEventRemove: suspend (SchoolCalendarEvent) -> Unit = {},
    onTimelineDaysChange: suspend (List<AcademicDay>, List<AcademicDay>) -> Unit = { _, _ -> },
    onCalendarSemesterDetected: suspend (DetectedSchoolSemester, Boolean) -> Unit = { _, _ -> },
    enrollmentYear: Int? = null,
    semesterOptions: List<Semester> = emptyList(),
    onSelectSemester: (Semester) -> Unit = {},
    activities: List<ScheduleActivity> = emptyList(),
    importedCourseCount: Int = 0,
    importedTeachingWeeks: Int? = null,
    onSaveActivity: suspend (ScheduleActivity) -> Unit = {},
    onDeleteActivity: suspend (String) -> Unit = {},
    showSemesterTools: Boolean = true,
    activityPresetColors: List<Int> = CoursePalette.indices.toList(),
    modifier: Modifier = Modifier,
) {
    val colors = LocalQztcColors.current
    val context = LocalContext.current
    var startDate by remember(semester) { mutableStateOf(semester.startDate) }
    var days by remember(academicDays, semester.id) { mutableStateOf(AcademicDayRanges.normalize(academicDays)) }
    val candidateStore = remember(semester.id) { CalendarCandidateStore(context, semester.id) }
    val timelineStore = remember(context) { CalendarCandidateStore(context,"timeline:${CourseArrangementProfile.schoolYear()}") }
    var fetchedDays by remember(semester.id) { mutableStateOf(candidateStore.load()) }
    val eventEdits=remember(context){CalendarEventEdits(context)}
    val selectionStore = remember(context) { CalendarSelectionStore(context) }
    var selections by remember { mutableStateOf(selectionStore.load()) }
    var editorError by remember { mutableStateOf<String?>(null) }
    var editingEvent by remember { mutableStateOf<SchoolCalendarEvent?>(null) }
    var editingTimelineHoliday by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<CalendarHolidaySchedule?>(null) }
    var writing by remember { mutableStateOf(false) }
    var officialStartDate by remember(semester.id) { mutableStateOf<LocalDate?>(null) }
    var detectedCalendarSemesters by remember(semester.id) { mutableStateOf(emptyList<DetectedSchoolSemester>()) }
    var detectedCurrentSemester by remember(semester.id) { mutableStateOf<DetectedSchoolSemester?>(null) }
    var detectedCalendarEvents by remember(semester.id) { mutableStateOf(emptyList<SchoolCalendarEvent>()) }
    var detectedCalendarPdfUrl by remember(semester.id) { mutableStateOf<String?>(null) }
    var calendarYear by remember { mutableStateOf(CourseArrangementProfile.schoolYear().substringBefore("-").toInt()) }
    var showPdf by remember { mutableStateOf(false) }
    var previewDays by remember { mutableStateOf(emptyList<AcademicDay>()) }
    var chooseSemester by remember { mutableStateOf(false) }
    var choosingStartDate by remember(semester.id) { mutableStateOf(false) }
    var calendarPreview by remember { mutableStateOf(false) }
    var upcomingOnly by remember { mutableStateOf(false) }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var editingActivity by remember { mutableStateOf<ScheduleActivity?>(null) }
    val workingSemester = semester.copy(startDate = startDate)
    val latestSemester by rememberUpdatedState(workingSemester)
    val currentSemesterSelected = showSemesterTools && CourseArrangementProfile.isCurrentSemester(
        semester, detectedCurrentSemester,
    )
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    val pendingHolidaySuggestions = remember(days, fetchedDays) {
        buildPendingHolidaySuggestions(days, fetchedDays)
    }
    val includedHolidaySchedules = remember(days) { buildCalendarHolidaySchedules(days) }
    val savedDayKeys = remember(days) { days.map(::academicDayKey).toSet() }
    fun canInclude(date: LocalDate): Boolean {
        val current = detectedCurrentSemester
        return if (current != null) current.contains(date) else {
            val today = LocalDate.now()
            today in SemesterCalendar.dateOf(workingSemester, 1, 1)..SemesterCalendar.dateOf(workingSemester, workingSemester.totalWeeks, 7) &&
                SemesterCalendar.weekOf(workingSemester, date) != null
        }
    }
    fun eventIncluded(event: SchoolCalendarEvent): Boolean = isCalendarEventIncluded(event, workingSemester, activities)
    fun holidayIncluded(schedule: CalendarHolidaySchedule): Boolean =
        calendarScheduleDays(schedule).any { academicDayKey(it) in savedDayKeys }
    fun select(key: String, included: Boolean) {
        selectionStore.set(key,included);selections=selectionStore.load()
    }
    suspend fun includeDefaultEvents(events: List<SchoolCalendarEvent>) {
        val defaults = events.filter { event ->
            val key = eventSelectionKey(event)
            (selections[key] ?: CalendarDefaultInclusion.includes(event, enrollmentYear)) &&
                canInclude(event.startDate) && activities.none { it.id.endsWith(":${event.id}") }
        }
        if (defaults.isNotEmpty()) onCalendarEventsChange(defaults)
        events.filter { selections[eventSelectionKey(it)] == null && CalendarDefaultInclusion.includes(it, enrollmentYear) }
            .filter { canInclude(it.startDate) }
            .forEach { select(eventSelectionKey(it), true) }
    }
    fun updateCatalog(next: List<AcademicDay>) {
        fetchedDays = next
        candidateStore.save(next)
    }
    fun removeFetchedHolidayDays(keys: Set<String>) {
        updateCatalog(fetchedDays.filterNot { academicDayKey(it) in keys })
    }
    fun toggleEvent(event: SchoolCalendarEvent) {
        if(writing || importing || !canInclude(event.startDate)) return
        val included=eventIncluded(event)
        writing=true
        scope.launch {
            try {
                detectedCalendarSemesters.firstOrNull { it.contains(event.startDate) }
                    ?.let { onCalendarSemesterDetected(it, !included) }
                if(included) onCalendarEventRemove(event) else onCalendarEventsChange(listOf(event))
                select(eventSelectionKey(event),!included)
            } catch(error:Exception) { syncMessage="课表更新失败，请重试" }
            finally { writing=false }
        }
    }
    fun toggleHoliday(schedule: CalendarHolidaySchedule, includeMissing: Boolean = false) {
        if(writing || importing || !canInclude(schedule.holiday?.start ?: schedule.makeup.first().start)) return
        val included=holidayIncluded(schedule) && !includeMissing
        val entries=calendarScheduleDays(schedule)
        writing=true
        scope.launch {
            try {
                val referenceDate = schedule.holiday?.start ?: schedule.makeup.firstOrNull()?.start
                referenceDate?.let { date ->
                    detectedCalendarSemesters.firstOrNull { it.contains(date) }
                        ?.let { onCalendarSemesterDetected(it, !included) }
                }
                onTimelineDaysChange(if(included) entries else emptyList(),if(included) emptyList() else entries)
                val first=SemesterCalendar.dateOf(workingSemester,1,1)
                val last=SemesterCalendar.dateOf(workingSemester,workingSemester.totalWeeks,7)
                val keys=entries.map(::academicDayKey).toSet()
                days=AcademicDayRanges.normalize(days.filterNot { academicDayKey(it) in keys } +
                    if(included) emptyList() else entries.filter { it.date in first..last }.map { it.copy(semesterId=semester.id) })
                if (included) {
                    // Unlocking removes the dates from the timetable, but keeps the
                    // fetched candidate visible so it can be deleted explicitly.
                    updateCatalog((fetchedDays + entries).distinctBy(::academicDayKey))
                }
                select(holidaySelectionKey(schedule),!included)
                timelineStore.save((timelineStore.load()+entries).distinctBy(::academicDayKey))
            } catch(error:Exception) { syncMessage="课表更新失败，请重试" }
            finally { writing=false }
        }
    }
    fun removeHolidaySuggestion(suggestion: PendingHolidaySuggestion) {
        if (writing || importing) return
        val keys = suggestion.pendingDays.map(::academicDayKey).toSet()
        removeFetchedHolidayDays(keys)
        previewDays = previewDays.filterNot { academicDayKey(it) in keys }
        syncMessage = "已移除这项待加入安排"
    }
    fun persist(next: List<AcademicDay>, nextStart: LocalDate = startDate) {
        if (writing) return
        writing = true
        scope.launch {
            try {
                onAcademicDaysChange(semester.copy(startDate=nextStart), next)
                days = next
            } catch (error: Exception) {
                syncMessage = error.message ?: "更新失败，原安排已保留"
            } finally { writing = false }
        }
    }
    BackHandler(enabled = writing) { }
    fun loadOfficialHolidays(openPreview: Boolean = true) {
        if (importing || writing) return
        val requestSemester = workingSemester
        importing = true
        syncMessage = "正在从官网获取最新校历安排"
        scope.launch {
            try {
                val schoolYearStart = if (LocalDate.now().monthValue <= 7) LocalDate.now().year - 1 else LocalDate.now().year
                val years = listOf(schoolYearStart, schoolYearStart + 1)
                val schoolYear = "$schoolYearStart-${schoolYearStart + 1}"
                val result = withContext(Dispatchers.IO) {
                    // A refresh must finish a real school website request. Cached and
                    // bundled data must never be reported as a successful refresh.
                    val school = SchoolCalendarApi.loadFromWebsite(context, schoolYear, requestSemester.id)
                    val official = runCatching { OfficialHolidayApi.load(context, years, requestSemester.id) }.getOrDefault(emptyList())

                    Triple(school, official, school?.days.orEmpty() + official)
                }
                if (latestSemester != requestSemester) {
                    syncMessage = "学期日期已变化，请重新导入"
                } else {
                    val school = result.first
                    val today = LocalDate.now()
                    val detected = school?.detectedSemesters().orEmpty()
                    val current = school?.semesterFor(today)
                    detectedCalendarSemesters = detected
                    detectedCurrentSemester = current
                    current?.let { onCalendarSemesterDetected(it, false) }
                    detectedCalendarEvents = eventEdits.merge(school?.events.orEmpty())
                    includeDefaultEvents(detectedCalendarEvents)
                    detectedCalendarPdfUrl = school?.pdfUrl
                    previewDays = AcademicDayRanges.normalize(result.third.filter(timelineStore::acceptsOfficial).filter { day ->
                        detected.any { day.date in it.startDate..(it.endDate ?: it.startDate.plusMonths(5)) }
                    } + timelineStore.load())
                    if(openPreview) calendarPreview = true
                    val first = SemesterCalendar.dateOf(requestSemester, 1, 1)
                    val last = SemesterCalendar.dateOf(requestSemester, requestSemester.totalWeeks, 7)
                    val activeStart = current?.startDate ?: first
                    val activeEnd = current?.endDate ?: last
                    val appliesToSemester = current == null ||
                        (current.term == requestSemester.term && current.startDate.year == requestSemester.startDate.year)
                    val allowed = previewDays.map { it.copy(name = it.name.trim(),semesterId=semester.id) }
                        .filter { appliesToSemester }
                        .filter(candidateStore::acceptsOfficial)
                        .filter { it.date in activeStart..activeEnd }
                        .filter { setOf("元旦","清明","劳动","端午","中秋","国庆").any { token -> it.name.contains(token) } }
                        .distinctBy { "${it.date}:${it.kind}" }
                    // Refresh the catalog without opting any item into the timetable.
                    updateCatalog((fetchedDays + allowed).distinctBy(::academicDayKey))
                    if (appliesToSemester) {
                        val firstIncluded = SemesterCalendar.dateOf(workingSemester, 1, 1)
                        val lastIncluded = SemesterCalendar.dateOf(workingSemester, workingSemester.totalWeeks, 7)
                        val defaults = buildCalendarHolidaySchedules(allowed).filter { schedule ->
                            val key = holidaySelectionKey(schedule)
                            selections[key] ?: true
                        }
                        defaults.filter { selections[holidaySelectionKey(it)] == null }
                            .forEach { select(holidaySelectionKey(it), true) }
                        val automaticDays = defaults.flatMap(::calendarScheduleDays)
                            .filter { it.date in firstIncluded..lastIncluded }
                            .map { it.copy(semesterId = semester.id) }
                            .distinctBy(::academicDayKey)
                        val mergedDays = AcademicDayRanges.normalize(days + automaticDays)
                        if (mergedDays != days) {
                            onTimelineDaysChange(emptyList(), automaticDays)
                            onAcademicDaysChange(workingSemester, mergedDays)
                            days = mergedDays
                        }
                    }
                    val suggestedStart = school?.takeIf { appliesToSemester }?.let { CourseArrangementProfile.teachingStart(it, enrollmentYear) }
                    if (suggestedStart != null) { officialStartDate = suggestedStart; startDate = suggestedStart }
                    calendarYear = schoolYearStart
                    if(suggestedStart != null && suggestedStart != semester.startDate)
                        onAcademicDaysChange(semester.copy(startDate=startDate),days)
                    syncMessage = "校历已重新获取 · " + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) + " · " + when {
                        !appliesToSemester -> "已读取当前校历，历史学期的日期保持不变"
                        current != null -> "已识别当前为${current.label}，并确定本学期起止日期"
                        detected.isNotEmpty() -> "已识别官网校历的学期日期，当前处于学期间隔"
                        school?.sourceUrl != null -> "已从官网校历获取最新学期安排"
                        fetchedDays.isEmpty() -> "当前学期没有可用的节假日安排"
                        else -> "已获取校历安排"
                    }
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                syncMessage = "校历获取失败，已保留上次数据：" + (error.message ?: "请检查网络后重试")
            } finally { importing = false }
        }
    }

    LaunchedEffect(semester.id) {
        val school = withContext(Dispatchers.IO) {
            SchoolCalendarApi.cached(context, CourseArrangementProfile.schoolYear(), semester.id)
        }
        detectedCalendarSemesters = school?.detectedSemesters().orEmpty()
        detectedCurrentSemester = school?.semesterFor(LocalDate.now())
        detectedCurrentSemester?.let { onCalendarSemesterDetected(it, false) }
        detectedCalendarEvents = eventEdits.merge(school?.events.orEmpty())
        includeDefaultEvents(detectedCalendarEvents)
        detectedCalendarPdfUrl = school?.pdfUrl
        previewDays = withContext(Dispatchers.IO) {
            val semesters = school?.detectedSemesters().orEmpty()
            semesters.flatMap { detected ->
                (detected.startDate.year..(detected.endDate?:detected.startDate.plusMonths(5)).year)
                    .flatMap { OfficialHolidayApi.cached(context,it,semester.id) }.filter { detected.contains(it.date) }
            }.filter(timelineStore::acceptsOfficial) + timelineStore.load()
        }
        officialStartDate = school?.let { CourseArrangementProfile.teachingStart(it, enrollmentYear) }
        val today=LocalDate.now()
        val current=school?.semesterFor(today)
        val fetchedAt = context.getSharedPreferences("school-calendar", Context.MODE_PRIVATE)
            .getLong("fetched:${CourseArrangementProfile.schoolYear()}", 0L)
        if (school != null) {
            syncMessage = if (fetchedAt > 0) "显示上次获取的校历 · " +
                java.time.Instant.ofEpochMilli(fetchedAt).atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                else "显示上次保存的校历"
        }
        // Retry on the next visit after a failure, and discover each new school year
        // even when the selected semester already has locally saved holidays.
        if (school == null || System.currentTimeMillis() - fetchedAt > 24 * 60 * 60 * 1000L) {
            loadOfficialHolidays(openPreview = false)
        }
    }

    if (showPdf && detectedCalendarPdfUrl != null) {
        SchoolCalendarPdfScreen(detectedCalendarPdfUrl!!, onBack = { showPdf = false },
            onDownload = { downloadSchoolCalendarPdf(context, detectedCalendarPdfUrl!!, calendarYear) })
        return
    }
    if (chooseSemester) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { chooseSemester = false }) {
            Text(
                "选择学期",
                style = QztcTypography.section,
                color = colors.ink,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            semesterOptions.forEach { option ->
                androidx.compose.material3.ListItem(headlineContent = { Text(option.name) },
                    trailingContent = { if (option.id == semester.id) Text("已选", color = colors.accent) },
                    colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { chooseSemester = false; onSelectSemester(option) })
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (choosingStartDate) {
        CalendarDatePicker(
            date = startDate,
            onDismiss = { choosingStartDate = false },
            onChoose = { next ->
                choosingStartDate = false
                startDate = next
                persist(days, next)
            },
        )
    }
    editingEvent?.let { original ->
        CalendarEventEditor(original, saving=writing,error=editorError,onBack={editingEvent=null;editorError=null},onSave={ updated ->
            if(!writing) {
                writing=true
                scope.launch {
                try {
                    val events=detectedCalendarEvents.map { if(it.id==updated.id)updated else it }
                    if(eventIncluded(original)) onCalendarEventsChange(listOf(updated))
                    eventEdits.save(updated);detectedCalendarEvents=events;editingEvent=null
                    if(eventIncluded(original) && updated.type==SchoolCalendarEventType.TEACHING_START && original.startDate==startDate) {
                        onAcademicDaysChange(semester.copy(startDate=updated.startDate),days);startDate=updated.startDate
                    }
                    editorError=null
                } catch(error:Exception){editorError="保存失败，请重试"} finally {writing=false}
                }
            }
        })
    }
    editingSchedule?.let { schedule ->
        HolidayDatesEditor(schedule, semester=workingSemester,saving=writing,error=editorError,onBack = { editingSchedule = null;editorError=null }, onSave = { replacement ->
            val originalKeys = calendarScheduleDays(schedule).map(::academicDayKey).toSet()
            if(!writing) {
                writing=true
                scope.launch {
                try {
                    val first=SemesterCalendar.dateOf(semester,1,1)
                    val last=SemesterCalendar.dateOf(semester,semester.totalWeeks,7)
                    val included=holidayIncluded(schedule)
                    if(included) {
                        onTimelineDaysChange(calendarScheduleDays(schedule),replacement)
                        days=AcademicDayRanges.normalize(days.filterNot { academicDayKey(it) in originalKeys } + replacement.filter { it.date in first..last }.map { it.copy(semesterId=semester.id) })
                    }
                    candidateStore.keepCustomization(calendarScheduleDays(schedule) + replacement)
                    updateCatalog(fetchedDays.filterNot { academicDayKey(it) in originalKeys } + replacement.filter { it.date in first..last })
                    timelineStore.keepCustomization(calendarScheduleDays(schedule) + replacement)
                    timelineStore.save(timelineStore.load().filterNot { academicDayKey(it) in originalKeys }+replacement)
                    previewDays=previewDays.filterNot { academicDayKey(it) in originalKeys }+replacement
                    select(holidaySelectionKey(schedule),false)
                    buildCalendarHolidaySchedules(replacement).forEach { select(holidaySelectionKey(it),included) }
                    editingTimelineHoliday=false;editingSchedule=null
                    editorError=null
                } catch(error:Exception){editorError="日期保存失败，请重试"} finally {writing=false}
                }
            }
        })
    }
    if (calendarPreview) {
        CalendarPreviewScreen(
            year = calendarYear,
            days = (previewDays + fetchedDays + days).distinctBy { "${it.date}:${it.kind}" }.sortedBy { it.date },
            semesters = detectedCalendarSemesters,
            events = detectedCalendarEvents,
            pdfUrl = detectedCalendarPdfUrl,
            upcomingOnly = upcomingOnly,
            canInclude = ::canInclude,
            onBack = { calendarPreview = false; upcomingOnly = false },
            onEditEvent = { if(!writing&&!importing) {editorError=null;editingEvent=it} },
            onEditHoliday = { if(!writing&&!importing) {editorError=null;editingTimelineHoliday=true;editingSchedule=it} },
            eventIncluded=::eventIncluded,holidayIncluded=::holidayIncluded,
            onToggleEvent=::toggleEvent,onToggleHoliday={ toggleHoliday(it) },
            onRemoveHoliday = { schedule ->
                val keys = calendarScheduleDays(schedule).map(::academicDayKey).toSet()
                removeFetchedHolidayDays(keys)
                previewDays = previewDays.filterNot { academicDayKey(it) in keys }
                syncMessage = "已移除这项待加入安排"
            },
            enabled=!writing&&!importing,
            refreshing=importing,
            message=syncMessage,
            onOpenPdf = { showPdf = true },
            onRefresh = { loadOfficialHolidays(openPreview = true) },
        )
        return
    }
    editingActivity?.let { original ->
        ActivityEditPage(
            title = if (original.name.isBlank()) "添加活动" else "编辑活动",
            editorKey = original.id,
            initial = original.toEditDraft(
                fallbackWeekday = 1,
                fallbackDate = startDate,
                totalWeeks = semester.totalWeeks,
            ),
            presetColors = activityPresetColors,
            totalWeeks = semester.totalWeeks,
            showDelete = original.name.isNotBlank() && !original.id.startsWith("calendar-event:"),
            showHide = original.id.startsWith("calendar-event:"),
            onBack = { editingActivity = null },
            onSave = { draft ->
                scope.launch {
                    runCatching { onSaveActivity(draft.toScheduleActivity(semester.id)) }
                        .onSuccess { editingActivity = null; syncMessage = "活动已同步到课表" }
                        .onFailure { syncMessage = it.message ?: "活动保存失败" }
                }
            },
            onDelete = {
                scope.launch {
                    runCatching { onDeleteActivity(original.id) }
                        .onSuccess { editingActivity = null; syncMessage = "活动已删除" }
                        .onFailure { syncMessage = it.message ?: "活动删除失败" }
                }
            },
            onHide = {
                scope.launch {
                    runCatching { onDeleteActivity(original.id) }
                        .onSuccess {
                            detectedCalendarEvents.firstOrNull { original.id.endsWith(":${it.id}") }
                                ?.let { select(eventSelectionKey(it), false) }
                            editingActivity = null
                            syncMessage = "校历安排已从课表隐藏"
                        }
                        .onFailure { syncMessage = it.message ?: "隐藏失败" }
                }
            },
        )
        return
    }
    Column(modifier.fillMaxSize().background(colors.solid2)) {
        CourseArrangementTopBar(semester, onBack = { if (!writing) onBack() },
            onChoose = { chooseSemester = true })
        LazyColumn(
            modifier = Modifier.weight(1f).testTag("calendar-settings-list"),
            contentPadding = PaddingValues(start = QztcSpacing.lg, end = QztcSpacing.lg, bottom = QztcSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(QztcSpacing.md),
        ) {
            item {
                val currentWeek = (java.time.temporal.ChronoUnit.DAYS.between(
                    workingSemester.startDate, LocalDate.now()) / 7 + 1).toInt().takeIf { !LocalDate.now().isBefore(workingSemester.startDate) }
                val teachingWeeks = importedTeachingWeeks?.takeIf { it > 0 }
                val progress = if (teachingWeeks != null) (currentWeek ?: 0).toFloat() / teachingWeeks else 0f
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(colors.solid).border(1.dp, colors.line, RoundedCornerShape(18.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(semester.name, style = QztcTypography.section, color = colors.ink)
                            Text(
                                when {
                                    teachingWeeks == null -> "导入教务课表后显示实际教学进度"
                                    currentWeek == null -> "尚未开学 · 课程排至第 $teachingWeeks 周"
                                    currentWeek > teachingWeeks -> "本学期课程已结束 · 共 $teachingWeeks 周"
                                    else -> "当前第 $currentWeek 周 · 还有 ${teachingWeeks - currentWeek} 周结课"
                                },
                                style = QztcTypography.meta, color = colors.mut,
                            )
                        }
                        Text("切换学期", Modifier.clip(RoundedCornerShape(8.dp))
                            .background(colors.accentSoft).clickable(enabled = !writing) { chooseSemester = true }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                            style = QztcTypography.meta, color = colors.accentDeep)
                    }
                    Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(colors.accentSoft)) {
                        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(5.dp).background(colors.accent))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ArrangementStat("开学日期", formatAcademicDate(startDate), Modifier.weight(1f)) { choosingStartDate = true }
                        ArrangementStat("本周", currentWeek?.let { "第 $it 周" } ?: "未开学", Modifier.weight(1f))
                        ArrangementStat("课程教学周", teachingWeeks?.let { "$it 周" } ?: "待导入", Modifier.weight(1f))
                    }
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(colors.solid).border(1.dp, colors.line, RoundedCornerShape(8.dp)),
                ) {
                    onImport?.let { action ->
                        Row(Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(colors.okSoft),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.School, contentDescription = null, tint = colors.okInk)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("教务课表", style = QztcTypography.body, color = colors.ink)
                                Text(if (importedCourseCount > 0) "已导入 · $importedCourseCount 门课程" else "尚未导入课程",
                                    style = QztcTypography.meta, color = colors.mut)
                            }
                            Text("登录 / 导入", Modifier.clip(RoundedCornerShape(8.dp))
                                .background(colors.accentSoft).clickable(onClick = action)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                style = QztcTypography.meta, color = colors.accentDeep)
                        }
                        HorizontalDivider(color = colors.line)
                    }
                    if (currentSemesterSelected) {
                        ArrangementActionRow("检查停课与调课", "检查当前学期接下来是否有停课与调课安排", onClick = onViewScheduleChanges)
                        HorizontalDivider(color = colors.line)
                    }
                    if (currentSemesterSelected) {
                        ArrangementActionRow(
                            "查看校历",
                            if (importing) "正在获取最新校历…"
                            else if (syncMessage?.startsWith("校历获取失败") == true) syncMessage
                            else calendarArrangementSummary(previewDays + fetchedDays + days, detectedCalendarEvents),
                            enabled = !importing && !writing,
                        ) {
                            upcomingOnly = false; calendarPreview = true
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("活动安排", style = QztcTypography.section, color = colors.ink)
                        if (activities.isNotEmpty()) {
                            Text(activityArrangementSummary(activities), style = QztcTypography.meta, color = colors.mut)
                        }
                    }
                    Text("添加", modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .background(colors.accentSoft).clickable {
                            val date = LocalDate.now().coerceIn(
                                SemesterCalendar.dateOf(workingSemester, 1, 1),
                                SemesterCalendar.dateOf(workingSemester, workingSemester.totalWeeks, 7),
                            )
                            editingActivity = ScheduleActivity(
                                id = "activity:${semester.id}:${System.currentTimeMillis()}",
                                semesterId = semester.id,
                                name = "",
                                kind = ActivityKind.DAY,
                                startDate = date,
                                endDate = date,
                            )
                        }.padding(horizontal = 14.dp, vertical = 9.dp),
                        style = QztcTypography.body, color = colors.accentDeep)
                }
            }
            if (activities.isNotEmpty()) {
                items(activities, key = { it.id }) { activity ->
                    Row(
                        Modifier.fillMaxWidth().clickable { editingActivity = activity }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(activity.name, style = QztcTypography.body, color = colors.ink)
                            val range = activity.startDate?.let { start ->
                                val end = activity.endDate ?: start
                                if (start == end) calendarDateLabel(start) else "${calendarDateLabel(start)} - ${calendarDateLabel(end)}"
                            } ?: "按课程时间"
                            Text(
                                listOf(range, activity.note.takeUnless { it == "官网校历标注为暂定时间" }.orEmpty())
                                    .filter { it.isNotBlank() }.joinToString(" · "),
                                style = QztcTypography.meta,
                                color = colors.mut,
                            )
                        }
                        Icon(Icons.Outlined.Edit, contentDescription = "编辑活动", tint = colors.mut)
                    }
                    HorizontalDivider(color = colors.line)
                }
            }
            if (activities.isEmpty()) {
                item { Text("暂无活动安排", style = QztcTypography.meta, color = colors.mut) }
            }
            item {
                Text("节假日安排", style = QztcTypography.section, color = colors.ink)
            }
            if (includedHolidaySchedules.isNotEmpty()) {
                items(includedHolidaySchedules, key = { "included-holiday:${holidaySelectionKey(it)}" }) { schedule ->
                    CalendarSwipeRow(
                        key = "included-holiday:${holidaySelectionKey(schedule)}",
                        included = true,
                        enabled = !writing && !importing,
                        onEdit = { editorError = null; editingSchedule = schedule },
                        onToggle = { toggleHoliday(schedule) },
                        onRightSwipe = { toggleHoliday(schedule) },
                        showStatus = true,
                        canToggle = canInclude(schedule.holiday?.start ?: schedule.makeup.first().start),
                    ) {
                        val holiday = schedule.holiday
                        Text(
                            "${holiday?.let { calendarDateRangeLabel(it.start, it.end) } ?: ""}  ${holiday?.name ?: "调休"}",
                            style = QztcTypography.body,
                            color = colors.ink,
                        )
                        Text(holidayMakeupSummary(schedule), style = QztcTypography.meta, color = colors.mut)
                    }
                }
            }
            validationMessage?.let { item { Text(it, style = QztcTypography.meta, color = colors.danger) } }
            if (!importing) syncMessage?.let { item { Text(it, style = QztcTypography.meta, color = colors.mut) } }
            if (includedHolidaySchedules.isEmpty() && pendingHolidaySuggestions.isEmpty()) {
                item { Text("暂无后续安排", style = QztcTypography.meta, color = colors.mut) }
            }
            if (pendingHolidaySuggestions.isNotEmpty()) {
                item { Text("接下来的停课与调课安排", style = QztcTypography.section, color = colors.mut) }
                items(pendingHolidaySuggestions, key = { it.key }) { suggestion ->
                    CalendarSwipeRow(
                        key = suggestion.key,
                        included = false,
                        enabled = !writing && !importing,
                        onEdit = { editorError=null;editingSchedule = suggestion.schedule },
                        onToggle = { toggleHoliday(suggestion.schedule, includeMissing = true) },
                        onRightSwipe = { toggleHoliday(suggestion.schedule, includeMissing = true) },
                        onLeftSwipe = { removeHolidaySuggestion(suggestion) },
                        canToggle = canInclude(suggestion.schedule.holiday?.start ?: suggestion.schedule.makeup.first().start),
                    ) {
                        suggestion.schedule.holiday?.let { Text("${calendarDateRangeLabel(it.start,it.end)}  ${it.name}",style=QztcTypography.body,color=colors.ink) }
                        Text(holidayMakeupSummary(suggestion.schedule),style=QztcTypography.meta,color=colors.mut)
                    }
                }
            }
        }
    }
}

@Composable
private fun ArrangementStat(label: String, value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val colors = LocalQztcColors.current
    Column(modifier.clip(RoundedCornerShape(8.dp)).background(colors.solid2)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(9.dp)) {
        Text(label, style = QztcTypography.meta, color = colors.mut)
        Text(value, style = QztcTypography.meta, color = colors.ink, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ArrangementActionRow(title: String, detail: String?, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = LocalQztcColors.current
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
            .background(if (title.contains("停课")) colors.warnSoft else colors.accentSoft),
            contentAlignment = Alignment.Center) {
            Icon(if (title.contains("停课")) Icons.Outlined.SyncProblem else Icons.Outlined.Event,
                contentDescription = null, modifier = Modifier.size(20.dp),
                tint = if (title.contains("停课")) colors.warn else colors.accentDeep)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = QztcTypography.body, color = colors.ink)
            detail?.let { Text(it, style = QztcTypography.meta, color = colors.mut) }
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.mut)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ActivityArrangementEditor(
    activity: ScheduleActivity,
    onBack: () -> Unit,
    onSave: (ScheduleActivity) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalQztcColors.current
    var name by remember(activity.id) { mutableStateOf(activity.name) }
    var note by remember(activity.id) { mutableStateOf(activity.note) }
    var start by remember(activity.id) { mutableStateOf(activity.startDate ?: LocalDate.now()) }
    var end by remember(activity.id) { mutableStateOf(activity.endDate ?: start) }
    var confirmDelete by remember { mutableStateOf(false) }
    var dateRequest by remember { mutableStateOf<Pair<LocalDate, (LocalDate) -> Unit>?>(null) }
    fun pick(current: LocalDate, update: (LocalDate) -> Unit) { dateRequest = current to update }
    dateRequest?.let { (current, update) ->
        CalendarDatePicker(current, onDismiss = { dateRequest = null }) { update(it); dateRequest = null }
    }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(colors.solid2)) {
        QztcPageTopBar(title = if (activity.name.isBlank()) "添加活动" else "编辑活动", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(QztcSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(QztcSpacing.md),
        ) {
            androidx.compose.material3.OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("活动名称") }, singleLine = true)
            androidx.compose.material3.OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("备注") }, minLines = 2)
            Text("活动日期", style = QztcTypography.section, color = colors.ink)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(QztcSpacing.sm)) {
                OutlinedButton(onClick = { pick(start) { value -> start = value; if (end.isBefore(value)) end = value } }, modifier = Modifier.weight(1f)) { Text(calendarDateLabel(start)) }
                OutlinedButton(onClick = { pick(end) { value -> end = if (value.isBefore(start)) start else value } }, modifier = Modifier.weight(1f)) { Text(calendarDateLabel(end)) }
            }
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(activity.copy(name = name.trim(), note = note.trim(), kind = ActivityKind.DAY, startDate = start, endDate = end, weekday = null, periodStart = null, periodEnd = null, weeks = emptyList()))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("完成") }
            if (activity.name.isNotBlank()) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("删除活动") }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这个活动？") },
            text = { Text("删除后会同步从课表移除。") },
            confirmButton = { Button(onClick = { confirmDelete = false; onDelete() }) { Text("删除") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}


@Composable
private fun CalendarPreviewScreen(
    year: Int,
    days: List<AcademicDay>,
    semesters: List<DetectedSchoolSemester>,
    events: List<SchoolCalendarEvent>,
    pdfUrl: String?,
    onBack: () -> Unit,
    upcomingOnly: Boolean,
    canInclude: (LocalDate) -> Boolean,
    onOpenPdf: () -> Unit,
    onRefresh: () -> Unit,
    onEditEvent: (SchoolCalendarEvent) -> Unit,
    onEditHoliday: (CalendarHolidaySchedule) -> Unit,
    eventIncluded: (SchoolCalendarEvent) -> Boolean,
    holidayIncluded: (CalendarHolidaySchedule) -> Boolean,
    onToggleEvent: (SchoolCalendarEvent) -> Unit,
    onToggleHoliday: (CalendarHolidaySchedule) -> Unit,
    onRemoveHoliday: (CalendarHolidaySchedule) -> Unit,
    enabled: Boolean,
    message: String?,
    refreshing: Boolean = false,
) {
    BackHandler(onBack = onBack)
    val colors = LocalQztcColors.current
    var mode by remember { mutableStateOf(CalendarTimelineMode.ALL) }
    val today = LocalDate.now()
    val visibleDays = remember(days, upcomingOnly, today) { if (upcomingOnly) days.filter { !it.date.isBefore(today) } else days }
    val visibleEvents = remember(events, upcomingOnly, today) {
        if (upcomingOnly) events.filter { !(it.endDate ?: it.startDate).isBefore(today) } else events
    }
    val holidaySchedules = remember(visibleDays,semesters) { buildCalendarHolidaySchedules(CalendarHolidayPlanning.supplement(semesters,visibleDays)) }
    val timelines = remember(semesters, visibleEvents, holidaySchedules, mode, upcomingOnly, today) {
        buildCalendarTimelines(semesters, visibleEvents, holidaySchedules, mode).mapNotNull { timeline ->
            if (!upcomingOnly) timeline else timeline.copy(
                entries = timeline.entries.filter { !(it.endDate ?: it.startDate).isBefore(today) },
            ).takeIf { it.entries.isNotEmpty() }
        }
    }
    val title = if (upcomingOnly) "停课与调课" else semesters.firstOrNull()?.schoolYear?.let { "$it 学年校历" } ?: "$year 年校历"
    Column(Modifier.fillMaxSize().background(colors.solid2)) {
        QztcPageTopBar(title = title, onBack = onBack, actions = {
            if (pdfUrl != null) QztcTopIconButton(
                icon = Icons.Outlined.Visibility,
                contentDescription = "查看校历原文件",
                onClick = onOpenPdf,
            )
            val transition = rememberInfiniteTransition(label = "calendar-refresh")
            val rotation by transition.animateFloat(
                initialValue = 0f, targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
                label = "calendar-refresh-rotation",
            )
            QztcTopIconButton(
                icon = Icons.Outlined.Refresh,
                contentDescription = "重新获取校历",
                onClick = onRefresh,
                enabled = enabled,
                modifier = Modifier.rotate(if (refreshing) rotation else 0f),
            )
        })
        LazyColumn(
            Modifier.weight(1f).testTag("calendar-preview-list"),
            contentPadding = PaddingValues(QztcSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(QztcSpacing.lg),
        ) {
            item {
                Text("活动安排", style = QztcTypography.section, color = colors.ink)
                Row(
                    Modifier.fillMaxWidth().padding(top = QztcSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(QztcSpacing.sm),
                ) {
                    CalendarTimelineModeButton(
                        label = "总时间线",
                        selected = mode == CalendarTimelineMode.ALL,
                        onClick = { mode = CalendarTimelineMode.ALL },
                        modifier = Modifier.weight(1f),
                    )
                    CalendarTimelineModeButton(
                        label = "放假与调休",
                        selected = mode == CalendarTimelineMode.HOLIDAYS,
                        onClick = { mode = CalendarTimelineMode.HOLIDAYS },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            items(timelines, key = { it.label }) { timeline ->
                CalendarSemesterTimeline(timeline,enabled=enabled,
                    canInclude=canInclude,
                    included={ entry -> entry.event?.let(eventIncluded) ?: entry.holiday?.let(holidayIncluded) ?: false },
                    onToggle={ entry -> entry.event?.let(onToggleEvent);entry.holiday?.let(onToggleHoliday) },
                    onRemoveHoliday=onRemoveHoliday,
                    allowHolidayRemoval=upcomingOnly,
                    onEdit={ entry -> entry.event?.let(onEditEvent);entry.holiday?.let(onEditHoliday) })
            }
            if (timelines.isEmpty()) item {
                Text("当前校历没有可显示的活动安排", style = QztcTypography.body, color = colors.mut)
            }
            message?.let { item { Text(it, style = QztcTypography.meta, color = if (it.contains("失败")) colors.danger else colors.mut) } }
        }
    }
}

@Composable
private fun CalendarTimelineModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}

@Composable
private fun CalendarSemesterTimeline(timeline: CalendarSemesterTimelineModel, enabled: Boolean,
    canInclude: (LocalDate) -> Boolean,
    included:(CalendarTimelineEntry)->Boolean,onToggle:(CalendarTimelineEntry)->Unit,
    onRemoveHoliday: (CalendarHolidaySchedule) -> Unit,
    allowHolidayRemoval: Boolean,
    onEdit: (CalendarTimelineEntry) -> Unit) {
    val colors = LocalQztcColors.current
    Column(Modifier.fillMaxWidth()) {
        Text(
            "${calendarDateRangeLabel(timeline.startDate, timeline.endDate)}  ${timeline.label}",
            style = QztcTypography.section,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        HorizontalDivider(Modifier.padding(top = QztcSpacing.sm), color = colors.line2)
        timeline.entries.forEachIndexed { index, entry ->
            val today=LocalDate.now()
            val active=today in entry.startDate..(entry.endDate?:entry.startDate)
            val next=timeline.entries.getOrNull(index+1)?.startDate
            val between=today>(entry.endDate?:entry.startDate) && next!=null && today<next
            val key=entry.event?.let(::eventSelectionKey) ?: entry.holiday?.let(::holidaySelectionKey) ?: "date:${entry.startDate}:${entry.name}"
            val actionable = entry.event != null || entry.holiday != null
            val leftSwipe: (() -> Unit)? = if (allowHolidayRemoval && entry.holiday != null && !included(entry)) {
                { onRemoveHoliday(entry.holiday) }
            } else null
            CalendarSwipeRow(
                key,
                included(entry),
                enabled && actionable,
                onToggle={onToggle(entry)},
                onRightSwipe={onToggle(entry)},
                onLeftSwipe=leftSwipe,
                onEdit={onEdit(entry)},
                showStatus=actionable && shouldShowCalendarStatus(included(entry), canInclude(entry.startDate)),
                canToggle=canInclude(entry.startDate),
            ) {
                CalendarTimelineEntryRow(entry, isLast = index == timeline.entries.lastIndex, active=active)
            }
            if(between) CalendarTodayMarker(today)
        }
    }
}

@Composable
private fun CalendarTimelineEntryRow(entry: CalendarTimelineEntry, isLast: Boolean, active: Boolean) {
    val colors = LocalQztcColors.current
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 54.dp).padding(top = 8.dp)) {
        Box(Modifier.width(22.dp).fillMaxHeight(), contentAlignment = if(active) Alignment.Center else Alignment.TopCenter) {
            Box(Modifier.width(2.dp).fillMaxHeight().background(colors.line2))
            Box(Modifier.size(if(active)14.dp else 10.dp).clip(CircleShape).background(if(active)colors.warn else colors.accentDeep))
        }
        Column(Modifier.weight(1f).padding(start = 6.dp, bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    calendarDateRangeLabel(entry.startDate, entry.endDate),
                    style = QztcTypography.body,
                    color = colors.accentDeep,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
                Spacer(Modifier.width(8.dp))
                Text(entry.name, style = QztcTypography.body, color = colors.ink, modifier = Modifier.weight(1f))
            }
            if (active) CalendarTodayLine(LocalDate.now())
            if (entry.detail?.isNotBlank() == true || !isLast) {
                HorizontalDivider(Modifier.padding(top = 8.dp), color = colors.line)
            }
            entry.detail?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = QztcTypography.meta,
                    color = colors.mut,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun CalendarTodayMarker(today: LocalDate) {
    val colors = LocalQztcColors.current
    Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(22.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Box(Modifier.width(2.dp).fillMaxHeight().background(colors.line2))
            Box(Modifier.size(14.dp).clip(CircleShape).background(colors.warn))
        }
        CalendarTodayLine(today, Modifier.weight(1f).padding(start = 6.dp))
    }
}

@Composable
private fun CalendarTodayLine(today: LocalDate, modifier: Modifier = Modifier) {
    val colors = LocalQztcColors.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = colors.warn.copy(alpha = .55f))
        Text(
            "今天 ${calendarDateLabel(today)}",
            style = QztcTypography.meta,
            color = colors.warn,
            modifier = Modifier.padding(horizontal = 10.dp),
            maxLines = 1,
        )
        HorizontalDivider(Modifier.weight(1f), color = colors.warn.copy(alpha = .55f))
    }
}

private enum class CalendarTimelineMode { ALL, HOLIDAYS }

private data class CalendarTimelineEntry(
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val name: String,
    val detail: String? = null,
    val semesterLabel: String,
    val event: SchoolCalendarEvent? = null,
    val holiday: CalendarHolidaySchedule? = null,
)

private fun calendarArrangementSummary(
    days: List<AcademicDay>,
    events: List<SchoolCalendarEvent>,
    today: LocalDate = LocalDate.now(),
): String {
    data class Arrangement(val name: String, val start: LocalDate, val end: LocalDate)
    val holidays = buildCalendarHolidaySchedules(days).mapNotNull { schedule ->
        val holiday = schedule.holiday ?: return@mapNotNull null
        Arrangement(holiday.name, holiday.start, holiday.end)
    }
    val activities = events.map { event ->
        Arrangement(event.name, event.startDate, event.endDate ?: event.startDate)
    }
    val all = (holidays + activities).distinctBy { "${it.name}:${it.start}:${it.end}" }.sortedBy { it.start }
    val current = holidays.firstOrNull { today >= it.start && today <= it.end }
        ?: activities.firstOrNull { today >= it.start && today <= it.end }
    if (current != null) return "当前：${current.name}"
    val next = all.firstOrNull { it.start.isAfter(today) }
    return next?.let { "下一项：${it.name} · ${calendarDateRangeLabel(it.start, it.end)}" } ?: "暂无后续安排"
}

private fun activityArrangementSummary(activities: List<ScheduleActivity>, today: LocalDate = LocalDate.now()): String {
    val dated = activities.flatMap { activity ->
        buildList {
            activity.startDate?.let { start -> add(Triple(activity.name, start, activity.endDate ?: start)) }
            activity.sessions.forEach { session ->
                session.date?.let { add(Triple(activity.name, it, it)) }
            }
        }
    }.distinct().sortedBy { it.second }
    val current = dated.firstOrNull { today >= it.second && today <= it.third }
    if (current != null) return "当前：${current.first}"
    val next = dated.firstOrNull { it.second.isAfter(today) }
    return next?.let { "下一项：${it.first} · ${calendarDateRangeLabel(it.second, it.third)}" }
        ?: "暂无后续安排"
}

private data class CalendarSemesterTimelineModel(
    val label: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val entries: List<CalendarTimelineEntry>,
)

internal data class CalendarHolidaySchedule(
    val holiday: AcademicDayRange?,
    val makeup: List<AcademicDayRange>,
)

internal data class PendingHolidaySuggestion(
    val key: String,
    val schedule: CalendarHolidaySchedule,
    val pendingDays: List<AcademicDay>,
)

internal fun academicDayKey(day: AcademicDay): String = "${day.date}:${day.kind}"

internal fun isHolidaySuggestionLocked(
    savedDayKeys: Set<String>,
    suggestion: PendingHolidaySuggestion,
): Boolean = suggestion.pendingDays.isNotEmpty() &&
    suggestion.pendingDays.any { academicDayKey(it) in savedDayKeys }

internal fun calendarScheduleDays(schedule: CalendarHolidaySchedule): List<AcademicDay> =
    buildList {
        schedule.holiday?.days?.let(::addAll)
        schedule.makeup.forEach { addAll(it.days) }
    }

internal fun buildPendingHolidaySuggestions(
    savedDays: List<AcademicDay>,
    pendingDays: List<AcademicDay>,
): List<PendingHolidaySuggestion> {
    if (pendingDays.isEmpty()) return emptyList()
    val pendingKeys = pendingDays.map(::academicDayKey).toSet() - savedDays.map(::academicDayKey).toSet()
    if (pendingKeys.isEmpty()) return emptyList()
    val merged = (savedDays + pendingDays).distinctBy(::academicDayKey)
    return buildCalendarHolidaySchedules(merged)
        .mapNotNull { schedule ->
            val pending = calendarScheduleDays(schedule).filter { academicDayKey(it) in pendingKeys }
            if (pending.isEmpty()) return@mapNotNull null
            val start = schedule.holiday?.start ?: pending.minOf { it.date }
            PendingHolidaySuggestion(
                key = "pending-holiday:$start:${schedule.holiday?.name.orEmpty()}",
                schedule = schedule,
                pendingDays = pending,
            )
        }
}

internal fun buildCalendarHolidaySchedules(days: List<AcademicDay>): List<CalendarHolidaySchedule> {
    val holidays = AcademicDayRanges.group(days.filter { it.kind == AcademicDayKind.HOLIDAY })
    val result = holidays.map { CalendarHolidaySchedule(it, emptyList()) }.toMutableList()
    fun holidayKey(name: String) = name.replace(Regex("调休|补班|补课|假期|放假|节"), "").trim()
    fun distance(makeup: AcademicDayRange, holiday: AcademicDayRange): Long = when {
        makeup.end < holiday.start -> java.time.temporal.ChronoUnit.DAYS.between(makeup.end, holiday.start)
        makeup.start > holiday.end -> java.time.temporal.ChronoUnit.DAYS.between(holiday.end, makeup.start)
        else -> 0
    }
    AcademicDayRanges.group(days.filter { it.kind == AcademicDayKind.MAKEUP }).forEach { makeup ->
        val key = holidayKey(makeup.name)
        val eligible = holidays.indices.filter { i ->
            val named = holidayKey(holidays[i].name)
            // Named makeup dates may not be stolen by a nearer, different holiday.
            if (key.isNotEmpty()) key == named && distance(makeup, holidays[i]) <= 30
            else distance(makeup, holidays[i]) <= 10
        }
        val index = eligible.minByOrNull { distance(makeup, holidays[it]) }
        if (index != null) result[index] = result[index].copy(makeup = result[index].makeup + makeup)
        else result += CalendarHolidaySchedule(null, listOf(makeup))
    }
    return result.sortedBy { it.holiday?.start ?: it.makeup.first().start }
}

private fun buildCalendarTimelines(
    semesters: List<DetectedSchoolSemester>,
    events: List<SchoolCalendarEvent>,
    holidaySchedules: List<CalendarHolidaySchedule>,
    mode: CalendarTimelineMode,
): List<CalendarSemesterTimelineModel> {
    fun semesterLabelFor(date: LocalDate): String =
        semesters.filter { !it.startDate.isAfter(date) }.maxByOrNull { it.startDate }?.label
            ?: semesters.minByOrNull { kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(date, it.startDate)) }?.label
            .orEmpty()

    val eventEntries = events
        .filter { mode == CalendarTimelineMode.ALL || it.type == SchoolCalendarEventType.BREAK }
        .map { event ->
            CalendarTimelineEntry(
                event=event,
                startDate = event.startDate,
                endDate = event.endDate,
                name = event.name,
                detail = event.detail.takeIf {
                    event.type == SchoolCalendarEventType.SPORTS_MEETING &&
                        it.isNotBlank() && !it.contains("暂定")
                },
                semesterLabel = event.semesterLabel.ifBlank { semesterLabelFor(event.startDate) },
            )
        }

    val holidayEntries = holidaySchedules.map { schedule ->
        val holiday = schedule.holiday
        val start = holiday?.start ?: schedule.makeup.minOf { it.start }
        val end = holiday?.end ?: schedule.makeup.maxOf { it.end }
        CalendarTimelineEntry(
            startDate = start,
            endDate = end,
            holiday=schedule,
            name = holiday?.name ?: schedule.makeup.first().name.ifBlank { "调休" },
            detail = (if(holiday?.days?.any { it.id.startsWith("estimated:") }==true) "预计放假 · " else "") + holidayMakeupSummary(schedule),
            semesterLabel = semesterLabelFor(start),
        )
    }

    val sourceEntries = eventEntries + holidayEntries
    return visibleCalendarSemesters(semesters).mapNotNull { semester ->
        var entries = sourceEntries.filter { it.semesterLabel == semester.label }
        if (mode == CalendarTimelineMode.ALL && entries.none {
                it.startDate == semester.startDate && it.name == "正式上课"
            }) {
            entries = entries + CalendarTimelineEntry(
                startDate = semester.startDate,
                name = "正式上课",
                semesterLabel = semester.label,
            )
        }
        entries = entries.sortedWith(compareBy<CalendarTimelineEntry> { it.startDate }.thenBy { it.name })
        if (entries.isEmpty()) return@mapNotNull null
        CalendarSemesterTimelineModel(
            label = when (semester.term) {
                cn.qztc.timetable.core.domain.model.Term.AUTUMN -> "秋季学期"
                cn.qztc.timetable.core.domain.model.Term.SPRING -> "春季学期"
                cn.qztc.timetable.core.domain.model.Term.SUMMER -> "暑期学期"
            },
            startDate = semester.startDate,
            endDate = semester.endDate,
            entries = entries,
        )
    }
}

internal fun calendarDateRangeLabel(start: LocalDate, end: LocalDate?): String {
    if (end == null || end == start) return calendarDateLabel(start)
    val endText = if (end.year == start.year) {
        "%02d/%02d".format(end.monthValue, end.dayOfMonth)
    } else {
        calendarDateLabel(end)
    }
    return "${calendarDateLabel(start)} - $endText"
}

internal fun calendarDateLabel(date: LocalDate): String =
    "%04d/%02d/%02d".format(date.year, date.monthValue, date.dayOfMonth)


private fun openSchoolCalendarPdf(context: Context, url: String) {
    val uri = Uri.parse(url)
    val pdfIntent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(pdfIntent) }.onFailure {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Toast.makeText(context, "设备没有可打开校历原文件的应用", Toast.LENGTH_SHORT).show() }
    }
}

private fun downloadSchoolCalendarPdf(context: Context, url: String, year: Int) {
    runCatching {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("泉师校历-$year.pdf")
            .setDescription("正在下载学校官网校历原文件")
            .setMimeType("application/pdf")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "泉师校历-$year.pdf")
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        Toast.makeText(context, "已开始下载，完成后可在下载目录查看", Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, "校历下载失败，请稍后重试", Toast.LENGTH_SHORT).show()
    }
}

private fun formatAcademicDate(date: LocalDate): String = calendarDateLabel(date)
