package cn.qztc.timetable.core.ui.theme

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.time.YearMonth

/** Horizontal intent wins only after touch slop, leaving vertical sheet scrolling intact. */
@Composable
fun Modifier.calendarMonthSwipe(month:YearMonth,minimum:YearMonth?=null,maximum:YearMonth?=null,
    onMonthChange:(YearMonth)->Unit):Modifier {
    val callback=rememberUpdatedState(onMonthChange)
    val threshold=with(LocalDensity.current) {48.dp.toPx()}
    return pointerInput(month,minimum,maximum,threshold) {
        var distance=0f
        detectHorizontalDragGestures(onDragStart={distance=0f},onHorizontalDrag={change,amount ->
            change.consume();distance+=amount
        },onDragCancel={distance=0f},onDragEnd={
            val next=when {distance<=-threshold->month.plusMonths(1);distance>=threshold->month.minusMonths(1);else->month}
            if(next!=month&&(minimum==null||next>=minimum)&&(maximum==null||next<=maximum))callback.value(next)
            distance=0f
        })
    }
}
