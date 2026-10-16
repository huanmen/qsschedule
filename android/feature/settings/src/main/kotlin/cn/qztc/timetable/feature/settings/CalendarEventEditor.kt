package cn.qztc.timetable.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarEventEditor(event: SchoolCalendarEvent, onSave: (SchoolCalendarEvent) -> Unit, onBack: () -> Unit, saving: Boolean = false, error: String? = null) {
    var start by remember(event.id){mutableStateOf(event.startDate)}
    var end by remember(event.id){mutableStateOf(event.endDate?:event.startDate)}
    val range=event.endDate!=null && event.endDate!=event.startDate || event.type==SchoolCalendarEventType.BREAK || event.type==SchoolCalendarEventType.SPORTS_MEETING
    val colors=LocalQztcColors.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun dismiss() {
        if (saving) return
        val updated = event.copy(startDate = start, endDate = if (range) end else event.endDate?.let { start })
        if (updated == event) onBack() else onSave(updated)
    }
    LaunchedEffect(error) { if (error != null) sheet.show() }
    ModalBottomSheet(onDismissRequest = ::dismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().testTag("calendar-event-editor").padding(horizontal = 20.dp).padding(bottom = 24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(event.name, Modifier.weight(1f), style = QztcTypography.section)
            }
            if (range) CalendarDateRangeChoice(start, end, !saving, "安排日期", event.name) { first, last -> start = first; end = last }
            else CalendarDateChoice("日期", start, !saving) { start = it }
            if(saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it,color=colors.danger) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarDateRangeChoice(
    start: LocalDate,
    end: LocalDate,
    enabled: Boolean = true,
    title: String = "放假日期",
    rangeName: String? = null,
    onChange: (LocalDate, LocalDate) -> Unit,
) {
    val colors = LocalQztcColors.current
    var choosing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().testTag("calendar-date-range")) {
        Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { choosing = true }.padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${start.monthValue}月${start.dayOfMonth}日 – ${end.monthValue}月${end.dayOfMonth}日", color = colors.accentDeep)
            Text("${ChronoUnit.DAYS.between(start, end) + 1} 天", color = colors.mut)
        }
    }
    if (choosing) {
        var draftStart by remember(start) { mutableStateOf(start) }
        var draftEnd by remember(end) { mutableStateOf(end) }
        var selectingStart by remember { mutableStateOf(true) }
        var displayedMonth by remember { mutableStateOf(YearMonth.from(start)) }
        QztcCalendarSheet(
            title = listOfNotNull(rangeName?.takeIf { it.isNotBlank() }, title).joinToString("  "),
            onDismiss = { choosing = false },
            onConfirm = {
                onChange(draftStart, draftEnd.coerceAtLeast(draftStart))
                choosing = false
            },
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    Text("开始 ${draftStart.monthValue}月${draftStart.dayOfMonth}日", color = if (selectingStart) colors.accentDeep else colors.ink,
                        style = QztcTypography.body, fontWeight = if (selectingStart) FontWeight.Bold else FontWeight.Normal,
                        textDecoration = if (selectingStart) TextDecoration.Underline else TextDecoration.None,
                        modifier = Modifier.clickable { selectingStart = true; displayedMonth = YearMonth.from(draftStart) }.padding(vertical = 12.dp))
                    Text("-", color = colors.mut)
                    Text("结束 ${draftEnd.monthValue}月${draftEnd.dayOfMonth}日", color = if (selectingStart) colors.ink else colors.accentDeep,
                        style = QztcTypography.body, fontWeight = if (selectingStart) FontWeight.Normal else FontWeight.Bold,
                        textDecoration = if (selectingStart) TextDecoration.None else TextDecoration.Underline,
                        modifier = Modifier.clickable { selectingStart = false; displayedMonth = YearMonth.from(draftEnd) }.padding(vertical = 12.dp))
                }
                QztcCalendarMonth(displayedMonth, draftStart, draftEnd,
                    onMonthChange = { displayedMonth = it },
                    onDate = { picked ->
                        if (selectingStart) {
                            draftStart = picked
                            if (draftEnd < picked) draftEnd = picked
                            selectingStart = false
                        } else {
                            draftEnd = picked.coerceAtLeast(draftStart)
                        }
                        displayedMonth = YearMonth.from(picked)
                    })
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarDateChoice(
    label: String,
    date: LocalDate,
    enabled: Boolean = true,
    valueText: String = calendarDateLabel(date),
    onChange: (LocalDate) -> Unit,
) {
    val colors = LocalQztcColors.current
    var choosing by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().testTag("calendar-date-$label").clickable(enabled = enabled) { choosing = true }
        .padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = colors.ink)
        Text(valueText, color = colors.accentDeep)
    }
    if (choosing) CalendarDatePicker(date, onDismiss = { choosing = false }) { onChange(it); choosing = false }
}

@Composable
internal fun CalendarDatePicker(date: LocalDate?, onDismiss: () -> Unit, onClear: (() -> Unit)? = null, onChoose: (LocalDate) -> Unit) {
    QztcDatePicker(date, onDismiss, onClear, onChoose = onChoose)
}
