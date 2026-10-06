package cn.qztc.timetable.core.ui.motion

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import cn.qztc.timetable.core.ui.glass.LocalGlassSettings

/**
 * 统一的按压反馈：按下轻微收缩并提亮，松开使用一次阻尼回弹。
 * 只变换绘制层，不改变布局尺寸，因此不会挤动相邻课程内容。
 */
@Composable
fun Modifier.pressable(enabled: Boolean = true): Modifier {
    val reduceMotion = LocalGlassSettings.current.reduceMotion
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) 0.955f else 1f,
        animationSpec = when {
            reduceMotion -> snap()
            pressed -> tween(
                durationMillis = MotionSpec.PRESS_IN_MS,
                easing = LinearOutSlowInEasing,
            )

            else -> tween(
                durationMillis = MotionSpec.PRESS_OUT_MS,
                easing = MotionSpec.PressOvershoot,
            )
        },
        label = "pressableScale",
    )

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .drawWithContent {
            drawContent()
            if (enabled && pressed) {
                drawRect(Color.White.copy(alpha = 0.04f))
            }
        }
        .pointerInput(enabled, reduceMotion) {
            if (enabled) {
                awaitEachGesture {
                    awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    pressed = true
                    try {
                        waitForUpOrCancellation(pass = PointerEventPass.Initial)
                    } finally {
                        pressed = false
                    }
                }
            }
        }
}
