package cn.qztc.timetable

import cn.qztc.timetable.core.ui.theme.QztcPalette
import cn.qztc.timetable.core.ui.theme.QztcThemeMode

/** Stable resource names let Android retain the correct starting window across process death. */
internal fun appLaunchTheme(mode: QztcThemeMode, palette: QztcPalette): Int = when (palette) {
    QztcPalette.ORIGINAL -> when (mode) {
        QztcThemeMode.SYSTEM -> R.style.Theme_QztcLaunch_Original_System
        QztcThemeMode.LIGHT -> R.style.Theme_QztcLaunch_Original_Light
        QztcThemeMode.DARK -> R.style.Theme_QztcLaunch_Original_Dark
    }
    QztcPalette.SODA -> when (mode) {
        QztcThemeMode.SYSTEM -> R.style.Theme_QztcLaunch_Soda_System
        QztcThemeMode.LIGHT -> R.style.Theme_QztcLaunch_Soda_Light
        QztcThemeMode.DARK -> R.style.Theme_QztcLaunch_Soda_Dark
    }
    QztcPalette.CITRUS -> when (mode) {
        QztcThemeMode.SYSTEM -> R.style.Theme_QztcLaunch_Citrus_System
        QztcThemeMode.LIGHT -> R.style.Theme_QztcLaunch_Citrus_Light
        QztcThemeMode.DARK -> R.style.Theme_QztcLaunch_Citrus_Dark
    }
}
