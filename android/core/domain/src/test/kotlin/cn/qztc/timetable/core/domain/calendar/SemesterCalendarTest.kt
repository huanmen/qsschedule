package cn.qztc.timetable.core.domain.calendar

import cn.qztc.timetable.core.domain.model.Semester
import cn.qztc.timetable.core.domain.model.Term
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SemesterCalendarTest {

    // 2024-09-02 是周一
    private val semester = Semester(
        id = "s1",
        name = "2024 秋",
        schoolYear = 2024,
        term = Term.AUTUMN,
        startDate = LocalDate.of(2024, 9, 2),
        totalWeeks = 20,
    )

    @Test
    fun `dateOf maps week and weekday`() {
        assertEquals(LocalDate.of(2024, 9, 2), SemesterCalendar.dateOf(semester, 1, 1))
        assertEquals(LocalDate.of(2024, 9, 8), SemesterCalendar.dateOf(semester, 1, 7))
        assertEquals(LocalDate.of(2024, 9, 9), SemesterCalendar.dateOf(semester, 2, 1))
        assertEquals(LocalDate.of(2024, 12, 27), SemesterCalendar.dateOf(semester, 17, 5))
    }

    @Test
    fun `non monday start is normalized`() {
        val wednesdayStart = semester.copy(startDate = LocalDate.of(2024, 9, 4))
        assertEquals(LocalDate.of(2024, 9, 2), SemesterCalendar.dateOf(wednesdayStart, 1, 1))
        assertEquals(1, SemesterCalendar.weekOf(wednesdayStart, LocalDate.of(2024, 9, 2)))
    }

    @Test
    fun `weekOf inverse of dateOf`() {
        assertEquals(1, SemesterCalendar.weekOf(semester, LocalDate.of(2024, 9, 2)))
        assertEquals(1, SemesterCalendar.weekOf(semester, LocalDate.of(2024, 9, 8)))
        assertEquals(17, SemesterCalendar.weekOf(semester, LocalDate.of(2024, 12, 27)))
    }

    @Test
    fun `weekOf outside semester returns null`() {
        assertNull(SemesterCalendar.weekOf(semester, LocalDate.of(2024, 8, 30)))
        assertNull(SemesterCalendar.weekOf(semester, LocalDate.of(2025, 6, 1)))
    }

    @Test
    fun `weekdayOf uses monday as 1`() {
        assertEquals(1, SemesterCalendar.weekdayOf(LocalDate.of(2024, 9, 2)))
        assertEquals(7, SemesterCalendar.weekdayOf(LocalDate.of(2024, 9, 8)))
    }

    @Test
    fun `reminder semester stays on the current term even if another is viewed`() {
        val autumn = semester
        val spring = semester.copy(
            id = "s0",
            name = "2024 春",
            term = Term.SPRING,
            startDate = LocalDate.of(2024, 2, 26),
        )
        val today = LocalDate.of(2024, 10, 8)
        assertEquals(autumn.id, Semester.forReminders(listOf(spring, autumn), today)?.id)
        assertEquals(spring.id, Semester.forReminders(listOf(spring, autumn), LocalDate.of(2024, 4, 1))?.id)
    }
}
