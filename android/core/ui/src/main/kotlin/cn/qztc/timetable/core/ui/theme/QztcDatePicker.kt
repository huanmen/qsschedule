package cn.qztc.timetable.core.ui.theme

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QztcDatePicker(date: LocalDate?, onDismiss: () -> Unit, onClear: (() -> Unit)? = null, onChoose: (LocalDate) -> Unit) {
    var selected by remember(date) { mutableStateOf(date ?: LocalDate.now()) }
    var month by remember(date) { mutableStateOf(YearMonth.from(selected)) }
    QztcCalendarSheet(
        title = "选择日期",
        onDismiss = onDismiss,
        onConfirm = { onChoose(selected) },
        onClear = onClear?.let { { it(); onDismiss() } },
    ) {
        QztcCalendarMonth(month, selected, selected, { month = it }, {
            selected = it; month = YearMonth.from(it)
        })
    }
}

/** Shared bottom sheet keeps the title, month navigation and confirmation in separate rows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QztcCalendarSheet(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onClear: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = LocalQztcColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.solid,
    ) {
        Column(Modifier.fillMaxWidth().testTag("calendar-sheet")) {
            Text(title, Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = QztcTypography.section, color = colors.ink)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                content()
            }
            if (onClear != null) TextButton(onClick = onClear, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("待定")
            }
            Button(onClick = onConfirm,
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
) {
    val colors = LocalQztcColors.current
    val first = month.atDay(1)
    val gridStart = first.minusDays((first.dayOfWeek.value - 1).toLong())
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "上个月")
            }
            Text("${month.year}年${month.monthValue}月", Modifier.weight(1f),
                textAlign = TextAlign.Center, style = QztcTypography.body, color = colors.ink)
            IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) {
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
                    Column(Modifier.weight(1f).heightIn(min = 50.dp)
                        .padding(2.dp)
                        .background(if (edge) colors.accent else if (inRange) colors.accentSoft else colors.solid,
                            RoundedCornerShape(6.dp))
                        .clickable { onDate(date) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        Text(date.dayOfMonth.toString(), style = QztcTypography.body,
                            color = if (edge) colors.onAccent else if (date.monthValue == month.monthValue) colors.ink else colors.mut)
                    }
                }
            }
        }
    }
}
