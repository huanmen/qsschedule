package cn.qztc.timetable.background

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundCropScreenTest {
    @Test
    fun `horizontal overflow converts drag to normalized position`() {
        val result = updateCropPosition(
            current = CropPosition(.5f, .5f),
            drag = Offset(100f, 80f),
            frame = IntSize(400, 800),
            image = IntSize(800, 800),
        )
        assertEquals(.25f, result.x, .001f)
        assertEquals(.5f, result.y, .001f)
    }

    @Test
    fun `position is clamped for large drags`() {
        val result = updateCropPosition(
            current = CropPosition(.5f, .5f),
            drag = Offset(-10_000f, 10_000f),
            frame = IntSize(400, 800),
            image = IntSize(800, 1600),
        )
        assertEquals(.5f, result.x, .001f)
        assertEquals(.5f, result.y, .001f)
    }
}
