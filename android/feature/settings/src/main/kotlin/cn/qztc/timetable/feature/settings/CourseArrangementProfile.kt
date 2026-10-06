package cn.qztc.timetable.feature.settings

import android.content.Context
import cn.qztc.timetable.core.domain.model.Term
import java.time.LocalDate

object CourseArrangementProfile {
    fun isCurrentSemester(
        semester: cn.qztc.timetable.core.domain.model.Semester,
        detected: DetectedSchoolSemester?,
        today: LocalDate = LocalDate.now(),
    ): Boolean {
        val term = detected?.term ?: if (today.monthValue in 2..7) Term.SPRING else Term.AUTUMN
        val startYear = detected?.startDate?.year ?: if (today.monthValue == 1) today.year - 1 else today.year
        return semester.term == term && semester.startDate.year == startYear
    }

    fun enrollmentYear(studentNumber: String, today: LocalDate = LocalDate.now()): Int? {
        if (!studentNumber.matches(Regex("\\d{9,12}"))) return null
        return (2000 + studentNumber.take(2).toInt()).takeIf { it in 2000..today.year }
    }

    fun record(context: Context, studentNumber: String) {
        val year = enrollmentYear(studentNumber) ?: return
        context.getSharedPreferences("course-arrangement", Context.MODE_PRIVATE).edit().putInt("enrollment-year", year).apply()
    }

    fun year(context: Context): Int? = context.getSharedPreferences("course-arrangement", Context.MODE_PRIVATE)
        .getInt("enrollment-year", 0).takeIf { it > 0 }

    fun schoolYear(today: LocalDate = LocalDate.now()): String {
        val first = if (today.monthValue < 8) today.year - 1 else today.year
        return "$first-${first + 1}"
    }

    fun teachingStart(calendar: SchoolCalendarResult, enrollment: Int?, today: LocalDate = LocalDate.now()): LocalDate? {
        val semester = calendar.semesterFor(today) ?: return null
        val freshman = enrollment == today.year && semester.term == Term.AUTUMN
        if (freshman) {
            // Reporting/military training does not establish the first teaching week.
            return calendar.events.firstOrNull {
                it.semesterLabel == semester.label && it.type == SchoolCalendarEventType.TEACHING_START && it.name.contains("新生")
            }?.startDate
        }
        return semester.startDate
    }
}
