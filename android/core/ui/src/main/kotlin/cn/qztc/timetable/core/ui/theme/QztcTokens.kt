package cn.qztc.timetable.core.ui.theme

import androidx.compose.ui.unit.dp

/** 页面和组件共用的间距节奏。 */
object QztcSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
}

/**
 * 圆角只描述形状等级，不绑定具体组件，避免业务层到处散落数值。
 */
object QztcCorners {
    val functional = 22.dp
    val content = 20.dp
    val courseBlock = 10.dp
    val pill = 999.dp
}
