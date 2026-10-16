package cn.qztc.timetable.core.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class BackdropContrastTest {
    @Test fun selectsReadableForegroundForDarkAndLightBackgrounds() {
        assertEquals(Color.White, readableForeground(Color.Black))
        assertEquals(Color.Black, readableForeground(Color.White))
        for (step in 0..255) {
            val value = step / 255f
            val background = Color(value, value, value)
            val foreground = readableForeground(background)
            val a = background.luminance(); val b = foreground.luminance()
            val contrast = (maxOf(a,b)+.05f)/(minOf(a,b)+.05f)
            assertTrue("gray=$step contrast=$contrast", contrast >= 4.5f)
        }
    }
}
