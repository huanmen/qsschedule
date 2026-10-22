package cn.qztc.timetable.qnu

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class QnuSchoolSemesterCatalogTest {
    private val catalog = QnuSchoolSemesterCatalog(
        years = listOf(QnuSchoolOption("year-a", "2025"), QnuSchoolOption("year-b", "2026"), QnuSchoolOption("year-c", "2027")),
        terms = listOf(QnuSchoolOption("spring", "春"), QnuSchoolOption("summer", "夏"), QnuSchoolOption("fall", "秋")),
        selectedYearId = "year-a", selectedTermId = "spring",
    )

    @Test fun latestCheckIgnoresPreviouslyViewedAndFutureSemesters() {
        val latest = catalog.latestSemester(LocalDate.of(2026, 9, 28))!!
        assertEquals("qnu-2026-autumn", latest.semesterId)
        assertEquals("year-b", latest.yearId)
        assertEquals("fall", latest.termId)
    }

    @Test fun springDoesNotChooseAutumnAndJanuaryKeepsPreviousAutumn() {
        assertEquals("qnu-2026-spring", catalog.latestSemester(LocalDate.of(2026, 3, 5))!!.semesterId)
        assertEquals("qnu-2025-autumn", catalog.latestSemester(LocalDate.of(2026, 1, 10))!!.semesterId)
    }

    @Test fun explicitPreviousSemesterUsesSchoolIdsWithoutFallingBackToLatest() {
        val target = catalog.semester(2026, cn.qztc.timetable.core.domain.model.Term.SPRING)!!
        assertEquals("year-b", target.yearId)
        assertEquals("spring", target.termId)
        org.junit.Assert.assertNull(catalog.semester(2024, cn.qztc.timetable.core.domain.model.Term.AUTUMN))
    }
}
