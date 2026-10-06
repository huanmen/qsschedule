package cn.qztc.timetable.feature.timetable

import cn.qztc.timetable.core.ui.theme.CoursePalette
import kotlin.math.abs
import kotlin.math.roundToInt

/** Stable imported-course colors that remain distinct after the built-in palette is exhausted. */
fun automaticCourseColorToken(index: Int): Int {
    require(index >= 0)
    if (index < CoursePalette.size) return index

    val hue = (18.0 + index * 137.507764).mod(360.0)
    val saturation = 0.48 + (index % 3) * 0.055
    val lightness = 0.50 + (index % 2) * 0.045
    return hslToArgb(hue, saturation, lightness)
}

private fun hslToArgb(hue: Double, saturation: Double, lightness: Double): Int {
    val chroma = (1 - abs(2 * lightness - 1)) * saturation
    val segment = hue / 60.0
    val x = chroma * (1 - abs(segment.mod(2.0) - 1))
    val (r1, g1, b1) = when {
        segment < 1 -> Triple(chroma, x, 0.0)
        segment < 2 -> Triple(x, chroma, 0.0)
        segment < 3 -> Triple(0.0, chroma, x)
        segment < 4 -> Triple(0.0, x, chroma)
        segment < 5 -> Triple(x, 0.0, chroma)
        else -> Triple(chroma, 0.0, x)
    }
    val match = lightness - chroma / 2
    val red = ((r1 + match) * 255).roundToInt().coerceIn(0, 255)
    val green = ((g1 + match) * 255).roundToInt().coerceIn(0, 255)
    val blue = ((b1 + match) * 255).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
}
