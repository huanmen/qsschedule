package cn.qztc.timetable.feature.settings

import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate

class CourseArrangementProfileTest {
    private val today=LocalDate.of(2026,9,17)
    @Test fun studentNumberUsesAdmissionYearNotSelectedSemester() {
        assertEquals(2023,CourseArrangementProfile.enrollmentYear("231308000",today))
        assertEquals(2026,CourseArrangementProfile.enrollmentYear("261308000",today))
        assertNull(CourseArrangementProfile.enrollmentYear("271308000",today))
        assertNull(CourseArrangementProfile.enrollmentYear("teacher",today))
    }
    @Test fun reportingIsNotMistakenForTeaching() {
        val calendar=SchoolCalendarResult(emptyList(),LocalDate.of(2026,8,31),LocalDate.of(2027,2,22),
            LocalDate.of(2027,1,15),LocalDate.of(2027,7,2),schoolYear="2026-2027")
        assertEquals(LocalDate.of(2026,8,31),CourseArrangementProfile.teachingStart(calendar,2023,today))
        assertNull(CourseArrangementProfile.teachingStart(calendar,2026,today))
        assertEquals("2026-2027",CourseArrangementProfile.schoolYear(LocalDate.of(2027,1,2)))
    }
}
