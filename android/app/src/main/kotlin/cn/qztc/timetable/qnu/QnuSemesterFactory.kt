package cn.qztc.timetable.qnu

import cn.qztc.timetable.core.domain.imports.ImportedTimetable
import cn.qztc.timetable.core.domain.model.Semester
import cn.qztc.timetable.core.domain.model.Term
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object QnuSemesterFactory {

    fun id(year: Int, term: Term): String = "qnu-$year-${term.name.lowercase()}"

    fun id(yearText: String, termText: String): String? {
        val year = yearText.trim().toIntOrNull() ?: return null
        val term = term(termText) ?: return null
        return id(year, term)
    }

    fun from(candidate: ImportedTimetable): Semester {
        val today = LocalDate.now()
        val year = candidate.semesterHint?.year ?: today.year
        val term = candidate.semesterHint?.term
            ?: if (today.monthValue in 2..6) Term.SPRING else Term.AUTUMN
        val name = candidate.semesterHint?.raw?.trim()?.ifEmpty { null }
            ?: "$year ${term.label()}"
        return Semester(
            id = id(year, term),
            name = name,
            schoolYear = year,
            term = term,
            startDate = estimatedStartDate(year, term),
        )
    }

    fun term(text: String): Term? = when (text.trim()) {
        "春" -> Term.SPRING
        "夏" -> Term.SUMMER
        "秋" -> Term.AUTUMN
        else -> null
    }

    private fun estimatedStartDate(year: Int, term: Term): LocalDate = when (term) {
        Term.SPRING -> LocalDate.of(year, 2, 1)
            .with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY))
        Term.SUMMER -> LocalDate.of(year, 6, 1)
            .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
        Term.AUTUMN -> LocalDate.of(year, 9, 1)
            .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
    }
}

fun Term.label(): String = when (this) {
    Term.SPRING -> "春"
    Term.SUMMER -> "夏"
    Term.AUTUMN -> "秋"
}
