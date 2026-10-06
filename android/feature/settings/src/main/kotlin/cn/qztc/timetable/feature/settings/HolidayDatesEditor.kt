package cn.qztc.timetable.feature.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.domain.calendar.*
import cn.qztc.timetable.core.domain.model.Semester
import cn.qztc.timetable.core.ui.theme.*
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

// Unknown dates are drafts, never fabricated academic days.
internal data class MakeupDateDraft(val source: LocalDate?, val target: LocalDate?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HolidayDatesEditor(
    schedule: CalendarHolidaySchedule, semester: Semester,
    onSave: (List<AcademicDay>) -> Unit, onBack: () -> Unit,
    saving: Boolean = false, error: String? = null,
) {
    val colors = LocalQztcColors.current
    val context = LocalContext.current
    val template = calendarScheduleDays(schedule).first()
    val name = schedule.holiday?.name ?: template.name.removeSuffix("调休")
    val pending = template.id.startsWith("pending:") || template.id.startsWith("estimated:") || template.id.startsWith("pending-makeup:")
    val draftKey = "${template.date.year}:${holidayNameKey(name)}"
    val prefs = remember { context.getSharedPreferences("calendar-makeup-drafts", Context.MODE_PRIVATE) }
    val initial = remember(draftKey) {
        val fallback = schedule.makeup.flatMap { it.days }.map { MakeupDateDraft(makeupSourceDate(semester, it), it.date) }
        prefs.getString(draftKey, null)?.let { saved ->
            runCatching {
                val array = JSONArray(saved)
                (0 until array.length()).map { index ->
                    val item = array.getJSONObject(index)
                    MakeupDateDraft(item.optString("source").takeIf { it.isNotBlank() }?.let(LocalDate::parse),
                        item.optString("target").takeIf { it.isNotBlank() }?.let(LocalDate::parse))
                }
            }.getOrDefault(fallback)
        } ?: fallback
    }
    var start by remember { mutableStateOf(schedule.holiday?.start ?: template.date) }
    var end by remember { mutableStateOf(schedule.holiday?.end ?: template.date) }
    var makeup by remember { mutableStateOf(initial) }
    val initialMode = if (initial.isNotEmpty()) 2 else if (pending) 0 else 1
    var makeupMode by remember { mutableStateOf(initialMode) }
    var choosing by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun dismiss() {
        if (saving) return
        val targets = if (makeupMode == 2) makeup.mapNotNull { it.target } else emptyList()
        if (end < start || targets.distinct().size != targets.size || (schedule.holiday != null && targets.any { it in start..end })) {
            localError = "请检查日期范围，调到的日期不能重复或落在放假期间"; return
        }
        val stored = if (makeupMode == 2) makeup else emptyList()
        prefs.edit().putString(draftKey, JSONArray().apply {
            stored.forEach { put(JSONObject().put("source", it.source?.toString().orEmpty()).put("target", it.target?.toString().orEmpty())) }
        }.toString()).apply()
        if (start == (schedule.holiday?.start ?: template.date) && end == (schedule.holiday?.end ?: template.date) &&
            makeup == initial && makeupMode == initialMode) { onBack(); return }
        val prefix = when (makeupMode) { 0 -> "pending-makeup"; 1 -> "manual-none"; else -> "manual" }
        val holidays = if (schedule.holiday == null) emptyList() else generateSequence(start) { if (it < end) it.plusDays(1) else null }.map {
            AcademicDay("$prefix:${template.semesterId}:$it", template.semesterId, it, AcademicDayKind.HOLIDAY, name)
        }.toList()
        val replacements = stored.mapNotNull { draft -> draft.target?.let { target ->
            AcademicDay(AcademicDay.idFor(template.semesterId, target), template.semesterId, target, AcademicDayKind.MAKEUP, "${name}调休",
                sourceWeek = draft.source?.let { SemesterCalendar.weekOf(semester, it) }, sourceWeekday = draft.source?.dayOfWeek?.value)
        } }
        onSave(holidays + replacements)
    }
    LaunchedEffect(error, localError) { if (error != null || localError != null) sheet.show() }
    ModalBottomSheet(onDismissRequest = ::dismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().testTag("holiday-date-editor").verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(name, style = QztcTypography.section)
            if (schedule.holiday != null) {
                Text("放假日期", style = QztcTypography.section, color = colors.ink)
                CalendarDateRangeChoice(start, end, !saving, rangeName = name) { first, last ->
                    start = first
                    end = last.coerceIn(first, first.plusYears(1))
                    localError = null
                }
                HorizontalDivider()
            }
            Text("调休安排", color = colors.ink)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("待定", "不调休", "有调休").forEachIndexed { index, label ->
                    SegmentedButton(selected = makeupMode == index, enabled = !saving,
                        onClick = { makeupMode = index; localError = null; if (index == 2 && makeup.isEmpty()) makeup = listOf(MakeupDateDraft(null, null)) },
                        shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(label) }
                }
            }
            if (makeupMode == 2) {
                makeup.forEachIndexed { index, day ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { choosing = index to true }, enabled = !saving, modifier = Modifier.weight(1f)) {
                            Text(day.source?.let(::calendarDateLabel) ?: "哪天")
                        }
                        Text("的课调到", color = colors.ink)
                        TextButton(onClick = { choosing = index to false }, enabled = !saving, modifier = Modifier.weight(1f)) {
                            Text(day.target?.let(::calendarDateLabel) ?: "哪天")
                        }
                        IconButton(enabled = !saving, onClick = { makeup = makeup.filterIndexed { i, _ -> i != index } }) {
                            Icon(Icons.Outlined.Delete, "删除这条调休")
                        }
                    }
                }
                OutlinedButton(enabled = !saving, onClick = { makeup = makeup + MakeupDateDraft(null, null) }) {
                    Icon(Icons.Outlined.Add, null); Text("添加调休")
                }
            }
            if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            (localError ?: error)?.let { Text(it, color = colors.danger) }
        }
    }
    choosing?.let { (index, source) ->
        val day = makeup.getOrNull(index) ?: return@let
        fun update(date: LocalDate?) {
            makeup = makeup.toMutableList().apply { set(index, if (source) day.copy(source = date) else day.copy(target = date)) }
            localError = null
        }
        CalendarDatePicker(if (source) day.source else day.target, onDismiss = { choosing = null }, onClear = { update(null) }) {
            update(it); choosing = null
        }
    }
}

internal fun makeupSourceDate(semester: Semester, day: AcademicDay): LocalDate? {
    val week = if (day.useCurrentWeek) SemesterCalendar.weekOf(semester, day.date) else day.sourceWeek
    val weekday = if (day.useCurrentWeekday) day.date.dayOfWeek.value else day.sourceWeekday
    return if (week != null && week >= 1 && weekday != null && weekday in 1..7) SemesterCalendar.dateOf(semester, week, weekday) else null
}
