package cn.qztc.timetable.core.ui.glass

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 玻璃质量三档。三档只改材质与动效，页面结构与操作路径必须完全一致
 * （产品硬约束，见 prototype/交接文档_设计到Compose.md §3）。
 */
enum class GlassQuality {
    /** API 33+：backdrop 采样 + AGSL 折射/色散/按压光扫 */
    FULL_LENS,

    /** API 31-32：backdrop 模糊 + 色调，无折射 */
    BLUR_TINT,

    /** API 26-30 / 低内存 / 户外高可读：不透明高对比表面 */
    SOLID_SURFACE,
}

/** 玻璃分两层：功能层（导航/控制/短时浮层）与内容层（课程卡等正文表面）。 */
enum class GlassRole { FUNCTIONAL, CONTENT }

/**
 * 用户可调的玻璃参数（“我的 › 玻璃透明度”），透明度与模糊度按层分开。
 * 默认值即原型定稿值；持久化到 DataStore 由任务 T8 完成。
 */
data class GlassSettings(
    val quality: GlassQuality = GlassQuality.FULL_LENS,
    val functionalAlpha: Float = 0.45f,
    val functionalBlur: Dp = 13.dp,
    val contentAlpha: Float = 0.50f,
    val contentBlur: Dp = 14.dp,
    val outdoorReadable: Boolean = false,
    val reduceMotion: Boolean = false,
    val apiLevel: Int = Int.MAX_VALUE,
    val lowMemory: Boolean = false,
) {
    /** 户外、低内存与旧系统只改变材质，不删除功能入口。 */
    val effectiveQuality: GlassQuality
        get() = when {
            outdoorReadable || lowMemory || apiLevel < 31 -> GlassQuality.SOLID_SURFACE
            quality == GlassQuality.FULL_LENS && apiLevel < 33 -> GlassQuality.BLUR_TINT
            else -> quality
        }
}

val LocalGlassSettings = staticCompositionLocalOf { GlassSettings() }
