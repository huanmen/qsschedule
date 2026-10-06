package cn.qztc.timetable.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 主题色板，取值与 HTML 原型 v3 的 CSS token 一一对应
 * （prototype/index.html 中 #app.light / #app.dark 两组变量）。
 */
@Immutable
data class QztcColors(
    val isDark: Boolean,
    val appBg: Color,
    val ink: Color,
    val mut: Color,
    val faint: Color,
    val line: Color,
    val line2: Color,
    val accent: Color,
    val accentDeep: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val accentSoftBorder: Color,
    val danger: Color,
    val dangerSoft: Color,
    val warn: Color,
    val warnSoft: Color,
    val okSoft: Color,
    val okInk: Color,
    val endSoft: Color,
    val solid: Color,
    val solid2: Color,
    /** 功能层玻璃的基色（透明度由 GlassSettings 控制） */
    val glassFunctionalBase: Color,
    /** 内容层玻璃的基色 */
    val glassContentBase: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val dockSelected: Color = accentSoft,
    val dockSelectedBorder: Color = accentSoftBorder,
    val dockSelectedInk: Color = accentDeep,
)

val QztcLightColors = QztcColors(
    isDark = false,
    appBg = Color(0xFFE9F1EF),
    ink = Color(0xFF1E3A34),
    mut = Color(0xFF5F7A72),
    faint = Color(0xFF8A9C96),
    line = Color(0x1A22423B),
    line2 = Color(0x2922423B),
    accent = Color(0xFF0E8A7B),
    accentDeep = Color(0xFF0B6D61),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0x210E8A7B),
    accentSoftBorder = Color(0x380E8A7B),
    danger = Color(0xFFB4304A),
    dangerSoft = Color(0xFFFBE3E8),
    warn = Color(0xFFB45309),
    warnSoft = Color(0xFFFFF3E0),
    okSoft = Color(0xFFD9F0E6),
    okInk = Color(0xFF1C5E4A),
    endSoft = Color(0x1422423B),
    solid = Color(0xFFFFFFFF),
    solid2 = Color(0xFFF2F5F4),
    glassFunctionalBase = Color(0xFFFFFFFF),
    glassContentBase = Color(0xFFFFFFFF),
    glassBorder = Color(0xB8FFFFFF),
    glassHighlight = Color(0xD9FFFFFF),
)

val QztcDarkColors = QztcColors(
    isDark = true,
    appBg = Color(0xFF0B141F),
    ink = Color(0xFFE6F1EC),
    mut = Color(0xFF93ACA4),
    faint = Color(0xFF6E857F),
    line = Color(0x1AE6F1EC),
    line2 = Color(0x2BE6F1EC),
    accent = Color(0xFF37C6AE),
    accentDeep = Color(0xFF5ADCC5),
    onAccent = Color(0xFF04241E),
    accentSoft = Color(0x2937C6AE),
    accentSoftBorder = Color(0x4D37C6AE),
    danger = Color(0xFFF38AA0),
    dangerSoft = Color(0x38D64565),
    warn = Color(0xFFF0B263),
    warnSoft = Color(0x33D68320),
    okSoft = Color(0x3D30A07D),
    okInk = Color(0xFF8FE3C3),
    endSoft = Color(0x1AE6F1EC),
    solid = Color(0xFF15222B),
    solid2 = Color(0xFF101B23),
    glassFunctionalBase = Color(0xFF2C404A),
    glassContentBase = Color(0xFF122028),
    glassBorder = Color(0x33FFFFFF),
    glassHighlight = Color(0x38FFFFFF),
)

val LocalQztcColors = staticCompositionLocalOf { QztcLightColors }

/**
 * 课程色板：bar 双主题共用（色条/进度段），bg/tx 分主题。
 * 变更状态不允许只用颜色表达，必须同时有文字或图标。
 */
@Immutable
data class CourseColor(
    val bar: Color,
    val lightBg: Color,
    val lightTx: Color,
    val darkBg: Color,
    val darkTx: Color,
) {
    fun bg(dark: Boolean): Color = if (dark) darkBg else lightBg
    fun glassBg(dark: Boolean, contentAlpha: Float): Color {
        val color = bg(dark)
        val alpha = contentAlpha.coerceIn(0.20f, 0.95f)
        return if (dark) color.copy(alpha = (color.alpha * (alpha / 0.50f)).coerceIn(0.14f, 0.78f))
        // A translucent pastel card over a custom black background can still
        // collapse into the same luminance as the ink. Keep light cards
        // sufficiently opaque; dark-theme cards already use light text.
        else color.copy(alpha = alpha.coerceAtLeast(0.78f))
    }
    fun tx(dark: Boolean): Color = if (dark) darkTx else lightTx
}

val CoursePalette: List<CourseColor> = listOf(
    CourseColor(Color(0xFF2E8B6F), Color(0xFFD9F0E6), Color(0xFF1C5E4A), Color(0x4D2E8B6F), Color(0xFF9FE3CD)),
    CourseColor(Color(0xFF3A7CB8), Color(0xFFD9EAF8), Color(0xFF1F5480), Color(0x4D3A7CB8), Color(0xFFA9D3F5)),
    CourseColor(Color(0xFFC08A2D), Color(0xFFFBEBD0), Color(0xFF7A5417), Color(0x4DC08A2D), Color(0xFFF2D49A)),
    CourseColor(Color(0xFF7A6BC6), Color(0xFFE6E2F6), Color(0xFF4A3D8F), Color(0x527A6BC6), Color(0xFFCFC4F5)),
    CourseColor(Color(0xFF5273B8), Color(0xFFDCE4F5), Color(0xFF2F4A85), Color(0x4D5273B8), Color(0xFFB6C9F0)),
    CourseColor(Color(0xFFC96A57), Color(0xFFFBE1DC), Color(0xFF8F3E2E), Color(0x4DC96A57), Color(0xFFF5BFB3)),
    CourseColor(Color(0xFF7C9A3D), Color(0xFFE4EED2), Color(0xFF4C6320), Color(0x4D7C9A3D), Color(0xFFD5E8A6)),
    CourseColor(Color(0xFF3D9E96), Color(0xFFD5F0EE), Color(0xFF256E68), Color(0x4D3D9E96), Color(0xFFA9E6E0)),
)
