package cn.qztc.timetable.core.ui.glass

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

val LocalBackdropColor = compositionLocalOf<Color?> { null }
val LocalGlassForeground = compositionLocalOf<Color?> { null }

fun readableForeground(background: Color): Color {
    val luminance = background.luminance()
    return if ((luminance + .05f) / .05f >= 1.05f / (luminance + .05f)) Color.Black else Color.White
}
