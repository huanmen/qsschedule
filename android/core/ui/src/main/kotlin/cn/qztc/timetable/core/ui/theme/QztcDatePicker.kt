package cn.qztc.timetable.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QztcDatePicker(date: LocalDate?, onDismiss: () -> Unit, onClear: (() -> Unit)? = null,
    markedDates: Set<LocalDate> = emptySet(), referenceDates:Set<LocalDate> = emptySet(), onChoose: (LocalDate) -> Unit) {
    var selected by remember(date) { mutableStateOf(date ?: LocalDate.now()) }
    var month by remember(date) { mutableStateOf(YearMonth.from(selected)) }
    QztcCalendarSheet(
        title = "选择日期",
        onDismiss = onDismiss,
        onConfirm = { onChoose(selected) },
        onClear = onClear?.let { { it(); onDismiss() } },
        onToday = { selected = LocalDate.now(); month = YearMonth.from(selected) },
    ) {
        QztcCalendarMonth(month, selected, selected, { month = it }, {
            selected = it; month = YearMonth.from(it)
        }, markedDates = markedDates,referenceDates=referenceDates)
    }
}

/** Shared bottom sheet keeps the title, month navigation and confirmation in separate rows. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun QztcCalendarSheet(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onClear: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    onToday: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = LocalQztcColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.solid,
    ) {
        Column(Modifier.fillMaxWidth().semantics { testTagsAsResourceId = true }.testTag("calendar-sheet")) {
            Row(Modifier.fillMaxWidth().padding(horizontal=24.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f).padding(vertical = 8.dp), style = QztcTypography.section, color = colors.ink)
                if(onToday!=null)TextButton(onClick=onToday) {Text("回今日")}
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                content()
            }
            if (onClear != null) TextButton(onClick = onClear, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("待定")
            }
            Button(onClick = onConfirm,enabled=confirmEnabled,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp)) { Text("确定") }
        }
    }
}

@Composable
fun QztcCalendarMonth(
    month: YearMonth,
    start: LocalDate,
    end: LocalDate,
    onMonthChange: (YearMonth) -> Unit,
    onDate: (LocalDate) -> Unit,
    markedDates: Set<LocalDate> = emptySet(),
    isDateEnabled:(LocalDate)->Boolean = {true},
    minimumMonth:YearMonth? = null,
    maximumMonth:YearMonth? = null,
    referenceDates:Set<LocalDate> = emptySet(),
) {
    val colors = LocalQztcColors.current
    val first = month.atDay(1)
    val gridStart = first.minusDays((first.dayOfWeek.value - 1).toLong())
    Column(Modifier.fillMaxWidth().calendarMonthSwipe(month,minimumMonth,maximumMonth,onMonthChange)
        .padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonthChange(month.minusMonths(1)) },enabled=minimumMonth==null||month>minimumMonth) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "上个月")
            }
            Text("${month.year}年${month.monthValue}月", Modifier.weight(1f),
                textAlign = TextAlign.Center, style = QztcTypography.body, color = colors.ink)
            IconButton(onClick = { onMonthChange(month.plusMonths(1)) },enabled=maximumMonth==null||month<maximumMonth) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = "下个月")
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { day ->
                Text(day, Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = QztcTypography.meta, color = colors.mut)
            }
        }
        repeat(6) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { day ->
                    val date = gridStart.plusDays((week * 7 + day).toLong())
                    val inRange = date >= start && date <= end
                    val edge = date == start || date == end
                    val reference=date in referenceDates
                    val enabled=isDateEnabled(date)
                    Column(Modifier.weight(1f).heightIn(min = 50.dp)
                        .padding(2.dp)
                        .background(if(reference)colors.solid else if (edge) colors.accent else if (inRange) colors.accentSoft else colors.solid,
                            RoundedCornerShape(6.dp))
                        .then(if(reference)Modifier.border(2.dp,colors.accent,RoundedCornerShape(6.dp))else Modifier)
                        .clickable(enabled=enabled) { onDate(date) }.testTag("calendar-date-$date"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        Text(date.dayOfMonth.toString(), style = QztcTypography.body,
                            color = if(!enabled)colors.mut.copy(alpha=.3f) else if(reference)colors.accentDeep else if (edge) colors.onAccent else if (date.monthValue == month.monthValue) colors.ink else colors.mut)
                        Box(Modifier.size(5.dp).background(if(date in markedDates) {
                            if(edge&&!reference) colors.onAccent else colors.accent
                        } else androidx.compose.ui.graphics.Color.Transparent, androidx.compose.foundation.shape.CircleShape)
                            .testTag(if(date in markedDates) "activity-dot-$date" else "empty-dot-$date"))
                    }
                }
            }
        }
    }
}
