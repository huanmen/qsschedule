package cn.qztc.timetable.core.ui.glass

import org.junit.Assert.assertEquals
import org.junit.Test

class GlassSettingsTest {
    @Test
    fun outdoorReadableAlwaysUsesSolidSurface() {
        val settings = GlassSettings(
            quality = GlassQuality.FULL_LENS,
            outdoorReadable = true,
        )

        assertEquals(GlassQuality.SOLID_SURFACE, settings.effectiveQuality)
    }

    @Test
    fun qualityIsPreservedWhenOutdoorModeIsOff() {
        assertEquals(
            GlassQuality.FULL_LENS,
            GlassSettings(quality = GlassQuality.FULL_LENS).effectiveQuality,
        )
        assertEquals(
            GlassQuality.BLUR_TINT,
            GlassSettings(quality = GlassQuality.BLUR_TINT).effectiveQuality,
        )
    }

    @Test
    fun fullLensFallsBackByApiLevel() {
        assertEquals(
            GlassQuality.BLUR_TINT,
            GlassSettings(quality = GlassQuality.FULL_LENS, apiLevel = 32).effectiveQuality,
        )
        assertEquals(
            GlassQuality.SOLID_SURFACE,
            GlassSettings(quality = GlassQuality.FULL_LENS, apiLevel = 30).effectiveQuality,
        )
    }

    @Test
    fun lowMemoryAlwaysUsesSolidSurface() {
        assertEquals(
            GlassQuality.SOLID_SURFACE,
            GlassSettings(quality = GlassQuality.FULL_LENS, lowMemory = true).effectiveQuality,
        )
    }
}
