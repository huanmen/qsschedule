package cn.qztc.timetable.core.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 课程表专用排版角色。字号固定，系统字体放大时由容器滚动承接，
 * 数字使用等宽数字以保证时间和节次对齐。
 */
object QztcTypography {
    val timeFocus = TextStyle(
        fontSize = 29.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight(800),
        fontFeatureSettings = "tnum",
    )

    val pageTitle = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight(800),
    )

    val section = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight(700),
        letterSpacing = 2.sp,
    )

    val courseTitle = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight(650),
    )

    val body = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight(450),
    )

    val meta = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight(550),
        fontFeatureSettings = "tnum",
    )

    val compact = TextStyle(
        fontSize = 10.5.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight(650),
        fontFeatureSettings = "tnum",
    )
}
