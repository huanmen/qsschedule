package cn.qztc.timetable.feature.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CourseMenuStateTest {
    @Test
    fun `reveal stores the swiped course`() {
        val revealed = CourseMenuState().reveal("os")
        assertEquals("os", revealed.revealedId)
    }

    @Test
    fun `revealing another course replaces the panel`() {
        val switched = CourseMenuState().reveal("os").reveal("hm")
        assertEquals("hm", switched.revealedId)
    }

    @Test
    fun `dismiss hides the action panel`() {
        val dismissed = CourseMenuState().reveal("os").dismiss()
        assertNull(dismissed.revealedId)
    }
}
