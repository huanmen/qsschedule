package cn.qztc.timetable.core.ui.motion

import androidx.compose.animation.core.CubicBezierEasing

/**
 * 全局动效规格，数值即 HTML 原型 v3 定稿值。
 * 所有页面共用同一组节奏；reduceMotion 时一律换成 0-160ms 直切或短淡变。
 */
object MotionSpec {
    /** 按下反馈：只缩放/高光，不改布局尺寸 */
    const val PRESS_IN_MS = 100

    /** 松开回弹：高阻尼弹簧，最多一次轻微越界 */
    const val PRESS_OUT_MS = 420
    val PressOvershoot = CubicBezierEasing(0.3f, 1.7f, 0.4f, 1f)

    /** 状态内容替换（AnimatedContent） */
    const val CONTENT_SWAP_MS = 260

    /** 进入课程详情的共享元素过渡 */
    const val ENTER_DETAIL_MS = 360

    /** 水滴菜单分相位时刻（自菜单打开起计） */
    const val DROP_CONDENSE_AT_MS = 0      // 凝聚，历时 120ms
    const val DROP_FALL_AT_MS = 110        // 坠落，历时 180ms，重力式 ease-in
    const val DROP_SPREAD_AT_MS = 300      // 漫开+涨满，历时 240ms
    val DropCondense = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    val DropFall = CubicBezierEasing(0.5f, 0f, 0.88f, 0.6f)
    val PoolSpread = CubicBezierEasing(0.3f, 1.25f, 0.45f, 1f)
    val PoolRise = CubicBezierEasing(0.34f, 1.3f, 0.5f, 1f)

    /** 底栏选中胶囊移动 / 玻璃容器弹开 */
    val DockPill = CubicBezierEasing(0.3f, 1.5f, 0.4f, 1f)

    /** 按压光扫（一次性，禁止常驻循环） */
    const val GLINT_SWEEP_MS = 550

    /** 选中行旋转彩边周期；reduceMotion 时停转、保留静态描边 */
    const val SELECTED_EDGE_SPIN_MS = 3400
}
