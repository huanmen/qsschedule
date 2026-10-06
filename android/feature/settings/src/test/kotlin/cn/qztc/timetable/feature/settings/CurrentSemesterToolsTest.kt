package cn.qztc.timetable.feature.settings

import cn.qztc.timetable.core.domain.model.Semester
import cn.qztc.timetable.core.domain.model.Term
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CurrentSemesterToolsTest {
    @Test fun historicalAndFutureTermsAreHiddenBeforeCalendarLoads() {
        val today = LocalDate.of(2026, 10, 4)
        val current = Semester("s", "秋", 2026, Term.AUTUMN, LocalDate.of(2026, 8, 31), 18)
        assertTrue(CourseArrangementProfile.isCurrentSemester(current, null, today))
        assertFalse(CourseArrangementProfile.isCurrentSemester(current.copy(startDate = LocalDate.of(2025, 9, 1)), null, today))
        assertFalse(CourseArrangementProfile.isCurrentSemester(current.copy(term = Term.SPRING), null, today))
    }
    @Test fun schoolCalendarDatesOverrideMonthGuessAtTermBoundary() {
        val spring = Semester("s", "春", 2026, Term.SPRING, LocalDate.of(2026, 3, 2), 18)
        val autumn = DetectedSchoolSemester("2025-2026", Term.AUTUMN, "第一学期", LocalDate.of(2025, 9, 1), LocalDate.of(2026, 1, 30))
        assertFalse(CourseArrangementProfile.isCurrentSemester(spring, autumn, LocalDate.of(2026, 2, 1)))
    }
}
