package cn.qztc.timetable

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cn.qztc.timetable.core.ui.theme.*
import java.io.File
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Uses an empty host and local synthetic dates; never opens the user's timetable. */
class ThemeCalendarAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<FeatureAcceptanceActivity>()

    @Test fun calendarUsesEachPaletteAndCanChangeMonthAndSave() {
        val appearance = mutableStateOf(QztcPalette.SODA to false)
        var chosen: LocalDate? = null
        compose.setContent {
            val (palette, dark) = appearance.value
            QztcTheme(darkTheme = dark, palette = palette) {
                val colors = LocalQztcColors.current
                assertEquals(colors.accentSoft, MaterialTheme.colorScheme.secondaryContainer)
                assertEquals(colors.solid, MaterialTheme.colorScheme.surfaceContainer)
                assertEquals(colors.accentDeep, MaterialTheme.colorScheme.onSecondaryContainer)
                key(palette, dark) {
                    QztcDatePicker(LocalDate.of(2026, 10, 1), onDismiss = {}, onChoose = { chosen = it })
                }
            }
        }
        for (palette in listOf(QztcPalette.SODA, QztcPalette.CITRUS)) {
            for (dark in listOf(false, true)) {
                compose.runOnIdle { appearance.value = palette to dark }
                compose.onNodeWithText("2026年10月").assertIsDisplayed()
                val folder = File(compose.activity.getExternalFilesDir(null), "qa-20261004").apply { mkdirs() }
                val bitmap = compose.onNodeWithTag("calendar-sheet").captureToImage().asAndroidBitmap()
                File(folder, "calendar-${palette.name}-${if (dark) "dark" else "light"}.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
        compose.onNodeWithContentDescription("下个月").performClick()
        compose.onNodeWithText("2026年11月").assertIsDisplayed()
        // The six-row grid also contains December 5; the first 5 is November 5.
        compose.onAllNodesWithText("5", useUnmergedTree = true).onFirst().performClick()
        compose.onNodeWithText("确定").performClick()
        compose.runOnIdle { assertEquals(LocalDate.of(2026, 11, 5), chosen) }
    }
}
