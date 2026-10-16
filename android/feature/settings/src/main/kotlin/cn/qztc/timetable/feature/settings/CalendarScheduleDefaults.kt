package cn.qztc.timetable.feature.settings

import android.content.Context
import cn.qztc.timetable.core.domain.calendar.AcademicDay
import cn.qztc.timetable.core.domain.calendar.SemesterCalendar
import cn.qztc.timetable.core.domain.model.ActivityKind
import cn.qztc.timetable.core.domain.model.ScheduleActivity
import cn.qztc.timetable.core.domain.model.Semester
import cn.qztc.timetable.core.domain.model.Term

/** Reuses saved public calendars without making a network request on timetable refresh. */
object CalendarScheduleDefaults {
    data class Additions(val days: List<AcademicDay>, val activities: List<ScheduleActivity>, val removedDayIds: Set<String> = emptySet())

    suspend fun refreshPublicCache(context: Context, semester: Semester) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val firstYear = semester.startDate.year - if (semester.term == Term.SPRING) 1 else 0
        val schoolYear = "$firstYear-${firstYear + 1}"
        val prefs = context.getSharedPreferences("calendar-default-refresh", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(schoolYear, 0L) < 24 * 60 * 60 * 1000L) return@withContext
        prefs.edit().putLong(schoolYear, now).apply()
        // Both endpoints are public; keep saved data if either source is unavailable.
        runCatching { SchoolCalendarApi.load(context, schoolYear, semester.id) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        runCatching { OfficialHolidayApi.load(context, (firstYear..firstYear + 1).toList(), semester.id) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        Unit
    }

    fun cached(context: Context, semester: Semester, days: List<AcademicDay>, activities: List<ScheduleActivity>): Additions {
        val firstYear = semester.startDate.year - if (semester.term == Term.SPRING) 1 else 0
        val schoolYear = "$firstYear-${firstYear + 1}"
        val school = SchoolCalendarApi.cached(context, schoolYear, semester.id)
        val publicDays = (school?.days.orEmpty() + (firstYear..firstYear + 1).flatMap {
            OfficialHolidayApi.cached(context, it, semester.id)
        })
        val candidates = CalendarCandidateStore(context, semester.id)
        val timeline = CalendarCandidateStore(context, "timeline:$schoolYear")
        val officialGroups = publicDays.map { "${it.date.year}:${holidayNameKey(it.name)}" }.toSet()
        val removed = days.filter { day ->
            (!candidates.acceptsOfficial(day) || !timeline.acceptsOfficial(day)) &&
                "${day.date.year}:${holidayNameKey(day.name)}" in officialGroups &&
                publicDays.none { it.date == day.date }
        }.map { it.id }.toSet()
        return plan(semester, days, activities, publicDays, school?.events.orEmpty(),
            emptyMap(), CourseArrangementProfile.year(context)).copy(removedDayIds = removed)
    }

    internal fun plan(
        semester: Semester, savedDays: List<AcademicDay>, savedActivities: List<ScheduleActivity>,
        candidateDays: List<AcademicDay>, events: List<SchoolCalendarEvent>,
        selections: Map<String, Boolean>, enrollmentYear: Int?,
    ): Additions {
        val first = SemesterCalendar.dateOf(semester, 1, 1)
        val last = SemesterCalendar.dateOf(semester, semester.totalWeeks, 7)
        val days = candidateDays.filter { day ->
            day.date in first..last && !day.id.startsWith("estimated:")
        }.distinctBy { it.date }.map { it.copy(semesterId = semester.id, id = AcademicDay.idFor(semester.id, it.date)) }
            .filter { day -> savedDays.none { it == day } }
        val activities = calendarImportEvents(events).filter { event ->
            val spring = event.semesterLabel == "第二学期"
            val year = event.schoolYear.substringBefore('-').toIntOrNull()?.plus(if (spring) 1 else 0)
            year == semester.startDate.year && (if (spring) Term.SPRING else Term.AUTUMN) == semester.term
        }.map { it.toCalendarActivity(semester.id) }
            .filter { event -> savedActivities.none { it.copy(sessions = emptyList()) == event } }
        return Additions(days, activities)
    }
}
