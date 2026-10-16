package cn.qztc.timetable.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.abs

@Composable
fun QztcTimePicker(time: LocalTime, onDismiss: () -> Unit, onChoose: (LocalTime) -> Unit) {
    QztcHourMinutePicker("选择时间", time.hour, time.minute, 23, onDismiss) { hour, minute ->
        onChoose(LocalTime.of(hour, minute))
    }
}

/** Shared wheel for clock times and reminder durations. Only the centered values are committed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QztcHourMinutePicker(title: String, hour: Int, minute: Int, maxHour: Int = 23,
                        onDismiss: () -> Unit, onChoose: (Int, Int) -> Unit) {
    val colors = LocalQztcColors.current
    val hours = rememberLazyListState(hour.coerceIn(0, maxHour))
    val minutes = rememberLazyListState(minute.coerceIn(0, 59))
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.solid) {
        Column(Modifier.fillMaxWidth().padding(20.dp).testTag("time-wheel")) {
            Text(title, style = QztcTypography.section, color = colors.ink)
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TimeWheel(hours, maxHour, "小时", Modifier.weight(1f))
                Text(":", style = QztcTypography.section, color = colors.mut)
                TimeWheel(minutes, 59, "分钟", Modifier.weight(1f))
            }
            Button(onClick = { onChoose(centeredValue(hours), centeredValue(minutes)) },
                enabled = !hours.isScrollInProgress && !minutes.isScrollInProgress,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("确定") }
        }
    }
}

private fun centeredValue(state: LazyListState): Int {
    val layout = state.layoutInfo
    val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
    return layout.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index
        ?: state.firstVisibleItemIndex
}

/** Same snapping wheels as course reminders, with disabled endpoint values. */
internal fun closestAllowedClockMinute(value:Int,isAllowed:(Int)->Boolean):Int? =
    (0..1439).filter(isAllowed).minByOrNull {abs(it-value)}

@Composable
fun QztcClockWheels(minute:Int,onChange:(Int)->Unit,isAllowed:(Int)->Boolean={true},onScrolling:(Boolean)->Unit={}) {
    val colors=LocalQztcColors.current
    val hours=rememberLazyListState(minute/60)
    val minutes=rememberLazyListState(minute%60)
    val hour by remember {derivedStateOf {centeredValue(hours)}}
    val part by remember {derivedStateOf {centeredValue(minutes)}}
    LaunchedEffect(hours.isScrollInProgress,minutes.isScrollInProgress) {onScrolling(hours.isScrollInProgress||minutes.isScrollInProgress)}
    LaunchedEffect(hour,part,hours.isScrollInProgress,minutes.isScrollInProgress) {
        if(!hours.isScrollInProgress&&!minutes.isScrollInProgress) {
            closestAllowedClockMinute(hour*60+part,isAllowed)?.let {value ->
                onChange(value)
                // A clamped value can equal the existing model value. In that case
                // its effect will not restart, so align the visible wheels here too.
                if(hour!=value/60)hours.scrollToItem(value/60)
                if(part!=value%60)minutes.scrollToItem(value%60)
            }
        }
    }
    LaunchedEffect(minute) {
        if(!hours.isScrollInProgress&&!minutes.isScrollInProgress) {
            if(centeredValue(hours)!=minute/60)hours.scrollToItem(minute/60)
            if(centeredValue(minutes)!=minute%60)minutes.scrollToItem(minute%60)
        }
    }
    Row(Modifier.fillMaxWidth().testTag("datetime-wheels"),verticalAlignment=Alignment.CenterVertically) {
        TimeWheel(hours,23,"小时",Modifier.weight(1f)) {h ->(0..59).any {isAllowed(h*60+it)}}
        Text(":",color=colors.mut)
        TimeWheel(minutes,59,"分钟",Modifier.weight(1f)) {m ->isAllowed(hour*60+m)}
    }
}

@Composable
private fun TimeWheel(state: LazyListState, max: Int, label: String, modifier: Modifier,isAllowed:(Int)->Boolean={true}) {
    val colors = LocalQztcColors.current
    val scope = rememberCoroutineScope()
    val selected by remember { derivedStateOf { centeredValue(state) } }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = QztcTypography.meta, color = colors.mut)
        Box(Modifier.fillMaxWidth().height(160.dp)) {
            Box(Modifier.align(Alignment.Center).fillMaxWidth().height(32.dp)
                .padding(horizontal = 8.dp).background(colors.accentSoft, RoundedCornerShape(10.dp)))
            LazyColumn(state = state, modifier = Modifier.fillMaxSize().testTag("time-wheel-$label")
                .semantics { contentDescription = "$label，滑动选择" },
                contentPadding = PaddingValues(vertical = 64.dp),
                flingBehavior = rememberSnapFlingBehavior(state)) {
                items((0..max).toList(), key = { it }) { value ->
                    Text(value.toString().padStart(2, '0'), Modifier.fillMaxWidth().height(32.dp)
                        .clickable(enabled=isAllowed(value)) { scope.launch { state.animateScrollToItem(value) } }
                        .testTag("wheel-$label-$value").wrapContentHeight(),
                        textAlign = TextAlign.Center, fontSize = if (value == selected) 24.sp else 18.sp,
                        color = if(!isAllowed(value))colors.mut.copy(alpha=.25f)else if (value == selected) colors.accentDeep else colors.mut)
                }
            }
        }
    }
}
