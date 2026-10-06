package cn.qztc.timetable.qnu

import cn.qztc.timetable.core.domain.imports.ImportedTimetable
import cn.qztc.timetable.core.domain.imports.SemesterHint
import cn.qztc.timetable.core.domain.model.Term
import cn.qztc.timetable.core.domain.schedule.TimetableState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QnuSemesterFactoryTest {

    @Test
    fun `school labels produce stable semester id`() {
        assertEquals("qnu-2025-autumn", QnuSemesterFactory.id("2025", "秋"))
        assertEquals("qnu-2025-spring", QnuSemesterFactory.id("2025", "春"))
        assertNull(QnuSemesterFactory.id("not-a-year", "秋"))
    }

    @Test
    fun `candidate becomes independently persisted semester`() {
        val candidate = ImportedTimetable(
            semesterHint = SemesterHint(2024, Term.AUTUMN, "秋", "2024 秋"),
            periods = emptyList(),
            state = TimetableState(),
            diagnostics = emptyList(),
        )

        val semester = QnuSemesterFactory.from(candidate)

        assertEquals("qnu-2024-autumn", semester.id)
        assertEquals("2024 秋", semester.name)
        assertEquals(LocalDate.of(2024, 9, 2), semester.startDate)
    }
}
