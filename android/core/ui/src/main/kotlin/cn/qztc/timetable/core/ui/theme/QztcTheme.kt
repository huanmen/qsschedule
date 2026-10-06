package cn.qztc.timetable.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import cn.qztc.timetable.core.ui.glass.GlassSettings
import cn.qztc.timetable.core.ui.glass.LocalGlassSettings

@Immutable
enum class QztcThemeMode { SYSTEM, LIGHT, DARK }
enum class QztcPalette { ORIGINAL, SODA, CITRUS }

enum class FontSizeMode { AUTO_FILL, DEFAULT, CUSTOM }

private val QztcMaterialTypography = Typography(
    headlineSmall = QztcTypography.pageTitle,
    titleLarge = QztcTypography.pageTitle,
    titleMedium = QztcTypography.courseTitle,
    bodyLarge = QztcTypography.body,
    bodyMedium = QztcTypography.body,
    bodySmall = QztcTypography.meta,
    labelLarge = QztcTypography.courseTitle,
    labelMedium = QztcTypography.meta,
    labelSmall = QztcTypography.compact,
)

@Composable
fun QztcTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: QztcPalette = QztcPalette.ORIGINAL,
    glassSettings: GlassSettings = GlassSettings(),
    content: @Composable () -> Unit,
) {
    val base = when (palette) {
        QztcPalette.ORIGINAL -> if (darkTheme) QztcDarkColors else QztcLightColors
        QztcPalette.SODA -> if (darkTheme) QztcDarkColors.copy(
            accent = Color(0xFF62C6E3), accentDeep = Color(0xFF86D6EE), onAccent = Color(0xFF06212B),
            accentSoft = Color(0xFF1C3644), ink = Color(0xFFE2EEF2), mut = Color(0xFF93ABB4), faint = Color(0xFF647D86),
            appBg = Color(0xFF0C1820), solid = Color(0xFF15252F), solid2 = Color(0xFF102029),
            accentSoftBorder = Color(0xFF345567), glassFunctionalBase = Color(0xFF203541), glassContentBase = Color(0xFF15252F),
            dockSelected = Color(0xFF1D303A), dockSelectedBorder = Color(0xFF2D4A55), dockSelectedInk = Color(0xFFA4C6D0),
        ) else QztcLightColors.copy(
            accent = Color(0xFF237FA0), accentDeep = Color(0xFF1B6884), accentSoft = Color(0xFFE4F2F8),
            ink = Color(0xFF243A44), mut = Color(0xFF677C85), faint = Color(0xFF9FB0B7),
            appBg = Color(0xFFF1F7F9), solid2 = Color(0xFFF2F7F9), accentSoftBorder = Color(0xFFB9D2DC),
            glassFunctionalBase = Color(0xFFEAF3F6), glassContentBase = Color(0xFFF6FAFC),
            dockSelected = Color(0xFFDDE9EC), dockSelectedBorder = Color(0xFFC5D7DC), dockSelectedInk = Color(0xFF356575),
        )
        QztcPalette.CITRUS -> if (darkTheme) QztcDarkColors.copy(
            accent = Color(0xFFC2D67A), accentDeep = Color(0xFFD2E38F), onAccent = Color(0xFF1F2810),
            accentSoft = Color(0xFF283220), ink = Color(0xFFE7EDE0), mut = Color(0xFFA3AE99), faint = Color(0xFF6D7766),
            appBg = Color(0xFF11180F), solid = Color(0xFF1B2418), solid2 = Color(0xFF151E13),
            accentSoftBorder = Color(0xFF465239), glassFunctionalBase = Color(0xFF2B3626), glassContentBase = Color(0xFF1B2418),
            dockSelected = Color(0xFF2B3524), dockSelectedBorder = Color(0xFF3D4C33), dockSelectedInk = Color(0xFFC0D29F),
        ) else QztcLightColors.copy(
            accent = Color(0xFF687932), accentDeep = Color(0xFF55652A), accentSoft = Color(0xFFEFF5D9),
            ink = Color(0xFF38453A), mut = Color(0xFF6F7D6B), faint = Color(0xFFA6B0A0),
            appBg = Color(0xFFF5F8ED), solid2 = Color(0xFFF1F5E8), accentSoftBorder = Color(0xFFCBD6B5),
            glassFunctionalBase = Color(0xFFF0F4E7), glassContentBase = Color(0xFFF9FBF3),
            dockSelected = Color(0xFFE6ECD7), dockSelectedBorder = Color(0xFFD0DABF), dockSelectedInk = Color(0xFF596943),
        )
    }
    val colors = base
    val materialScheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            background = colors.appBg,
            onBackground = colors.ink,
            surface = colors.solid,
            onSurface = colors.ink,
            error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            background = colors.appBg,
            onBackground = colors.ink,
            surface = colors.solid,
            onSurface = colors.ink,
            error = colors.danger,
        )
    }
    CompositionLocalProvider(
        LocalQztcColors provides colors,
        LocalGlassSettings provides glassSettings,
    ) {
        MaterialTheme(
            colorScheme = materialScheme.copy(
                primaryContainer = colors.accentSoft, onPrimaryContainer = colors.accentDeep,
                secondary = colors.accentDeep, onSecondary = colors.onAccent,
                secondaryContainer = colors.accentSoft, onSecondaryContainer = colors.accentDeep,
                tertiary = colors.accentDeep, onTertiary = colors.onAccent,
                tertiaryContainer = colors.accentSoft, onTertiaryContainer = colors.accentDeep,
                surfaceTint = colors.accent, surfaceVariant = colors.solid2, onSurfaceVariant = colors.mut,
                surfaceContainerLowest = colors.solid, surfaceContainerLow = colors.solid2,
                surfaceContainer = colors.solid, surfaceContainerHigh = colors.solid2,
                surfaceContainerHighest = colors.solid2,
                outline = colors.mut, outlineVariant = colors.line2,
                inverseSurface = colors.ink, inverseOnSurface = colors.solid, inversePrimary = colors.accentSoft,
            ),
            typography = QztcMaterialTypography,
            content = content,
        )
    }
}
