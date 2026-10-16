package cn.qztc.timetable.feature.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcTypography
import kotlin.math.roundToInt

@Composable
internal fun CalendarSwipeRow(
    key: String, included: Boolean, enabled: Boolean, onToggle: () -> Unit,
    onEdit: () -> Unit, showStatus: Boolean = true,
    onRightSwipe: (() -> Unit)? = null,
    canToggle: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = LocalQztcColors.current
    var offset by remember(key) { mutableFloatStateOf(0f) }
    var dragging by remember(key) { mutableStateOf(false) }
    val animated by animateFloatAsState(offset, tween(if(dragging) 0 else 160), label="calendar-inclusion-swipe")
    val threshold = with(LocalDensity.current) { 80.dp.toPx() }
    val limit = with(LocalDensity.current) { 120.dp.toPx() }
    val toggle by rememberUpdatedState(onToggle)
    val rightAction = if (included) "解锁" else "采纳"
    val latestRight by rememberUpdatedState(onRightSwipe)
    val rightColor = if (included) colors.warn else colors.okInk
    Box(Modifier.fillMaxWidth().clipToBounds().testTag("calendar-swipe-$key").semantics {
        customActions = buildList {
            add(CustomAccessibilityAction(if (included) "解锁" else "采纳") {
                if (enabled && canToggle) { (onRightSwipe ?: toggle)(); true } else false
            })
        }
    }.clickable(enabled=enabled,onClick=onEdit).pointerInput(key,included,enabled,canToggle) {
        detectHorizontalDragGestures(
            onDragStart={dragging=true},
            onHorizontalDrag={ change, amount -> if(enabled && canToggle) {
                change.consume()
                offset = (offset + amount).coerceIn(if (included) -limit else 0f, limit)
            } },
            onDragEnd={
                if (enabled && canToggle) {
                    when {
                        offset >= threshold -> (latestRight ?: toggle)()
                        included && offset <= -threshold -> toggle()
                    }
                }
                offset=0f;dragging=false
            },
            onDragCancel={offset=0f;dragging=false},
        )
    }) {
        if (animated != 0f) Row(Modifier.matchParentSize(), verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement = if (animated < 0f) Arrangement.End else Arrangement.Start) {
            Row(
                Modifier.fillMaxHeight().width(120.dp).background(rightColor).padding(start=16.dp),
                verticalAlignment=Alignment.CenterVertically,
            ) {
                Icon(if (included) Icons.Outlined.EventBusy else Icons.Outlined.EventAvailable, null, tint=colors.solid)
                Spacer(Modifier.width(6.dp))
                Text(rightAction,color=colors.solid,style=QztcTypography.meta)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(animated.roundToInt(), 0) }
                .background(colors.solid2)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) { content() }
            if (showStatus) {
                Text(
                    if (included) "已加入课表" else "待加入课表",
                    style = QztcTypography.meta,
                    color = if (included) colors.okInk else colors.warn,
                    modifier = Modifier.padding(start = 8.dp, end = 4.dp),
                )
            }
        }
    }
}
