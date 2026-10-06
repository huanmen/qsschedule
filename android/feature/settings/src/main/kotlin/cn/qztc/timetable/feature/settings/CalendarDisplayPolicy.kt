package cn.qztc.timetable.feature.settings

import cn.qztc.timetable.core.domain.model.ScheduleActivity
import cn.qztc.timetable.core.domain.model.Semester
import cn.qztc.timetable.core.domain.model.Term
import java.time.LocalDate

/** Inclusion is persisted timetable state, not a default suggestion or a saved preference. */
internal fun isCalendarEventIncluded(
    event: SchoolCalendarEvent,
    semester: Semester,
    activities: List<ScheduleActivity>,
): Boolean {
    val year = event.schoolYear.substringBefore('-').toIntOrNull() ?: return false
    val term = when (event.semesterLabel) {
        "第一学期" -> Term.AUTUMN
        "第二学期" -> Term.SPRING
        else -> return false
    }
    val startYear = year + if (term == Term.SPRING) 1 else 0
    if (semester.term != term || semester.startDate.year != startYear) return false
    return activities.any {
        it.id == "calendar-event:${semester.id}:${event.id}" &&
            it.semesterId == semester.id && !it.hiddenFromSchedule
    }
}

/** Retire completed autumn sections once the following spring has started; keep stored history. */
internal fun visibleCalendarSemesters(
    semesters: List<DetectedSchoolSemester>,
    today: LocalDate = LocalDate.now(),
): List<DetectedSchoolSemester> {
    val spring = semesters.filter { it.term == Term.SPRING && !it.startDate.isAfter(today) }
        .maxByOrNull { it.startDate } ?: return semesters
    return semesters.filterNot {
        it.term == Term.AUTUMN && it.startDate < spring.startDate &&
            (it.endDate ?: spring.startDate.minusDays(1)) < today
    }
}
