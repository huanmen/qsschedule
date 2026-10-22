package cn.qztc.timetable.reminder

import cn.qztc.timetable.core.domain.model.*
import cn.qztc.timetable.core.domain.calendar.AcademicCalendar
import cn.qztc.timetable.core.domain.calendar.SemesterCalendar
import cn.qztc.timetable.core.domain.schedule.OccurrenceExpander
import cn.qztc.timetable.feature.timetable.ImportedSchedule
import cn.qztc.timetable.education.AcademicCalendarEntry
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class DailyAgendaItem(val id: String, val title: String, val time: LocalTime?, val detail: String, val kind: String)

internal fun nextDailyAgendaRun(settings: DailyAgendaSettings, now: LocalDateTime): LocalDateTime? {
    val options = settings.normalized()
    if (!options.enabled || options.weekdays.isEmpty()) return null
    return (0L..7L).map { now.toLocalDate().plusDays(it).atTime(options.hour, options.minute) }
        .firstOrNull { it.isAfter(now) && it.dayOfWeek.value in options.weekdays }
}

internal fun buildDailyAgenda(
    date: LocalDate, settings: DailyAgendaSettings,
    schedules: List<ImportedSchedule>, entries: List<AcademicCalendarEntry>,
    calendarMemos: List<ScheduleActivity> = emptyList(),
): List<DailyAgendaItem> {
    fun include(kind: String) = when (kind) {
        "course" -> settings.includeCourses
        "exam" -> settings.includeExams
        "competition" -> settings.includeCompetitions
        else -> settings.includeActivities
    }
    val result = mutableListOf<DailyAgendaItem>()
    entries.filter { it.date == date && include(it.kind) }.forEach {
        result += DailyAgendaItem("academic:${it.id}", it.title, it.time, it.stage, it.kind)
    }
    if (settings.includeActivities) calendarMemos.filterNot { it.hiddenFromSchedule }.forEach { activity ->
        activity.memoTime?.let { memo ->
            memo.onDate(date).forEachIndexed { index, slot -> result += DailyAgendaItem("${activity.id}:$index", activity.name,
                slot.minute?.takeIf { date == slot.date }?.let { LocalTime.of(it / 60, it % 60) },
                listOf(memo.location, activity.note).filter(String::isNotBlank).joinToString(" · "), "activity") }
        }
    }
    for (schedule in schedules) {
        val periods = schedule.periods.associateBy { it.no }
        val week = SemesterCalendar.weekOf(schedule.semester, date) ?: -1
        schedule.activities.filterNot { it.hiddenFromSchedule }.forEach { activity ->
            val entryId = activity.id.removePrefix("academic:${schedule.semester.id}:")
            val academic = entries.firstOrNull { it.id == entryId }
            val kind = academic?.kind ?: if (activity.id.startsWith("academic:")) {
                if (entryId.startsWith("exam-")) "exam" else "competition"
            } else "activity"
            activity.memoTime?.let { memo ->
                if (settings.includeActivities) memo.onDate(date).forEachIndexed { index, slot ->
                    val time = slot.minute?.takeIf { date == slot.date }?.let { LocalTime.of(it / 60, it % 60) }
                    result += DailyAgendaItem("${activity.id}:$index", activity.name, time,
                        listOf(memo.location, activity.note).filter(String::isNotBlank).joinToString(" · "), "activity") }
            }
            if (include(kind)) activity.effectiveSessions().filter { it.appliesOn(week, date.dayOfWeek.value, date) }.forEach sessionLoop@{ session ->
                if (academic?.date == date) return@sessionLoop
                val time = session.startMinute?.let { LocalTime.of(it / 60, it % 60) }
                    ?: session.periodStart?.let { periods[it]?.startTime }
                result += DailyAgendaItem("${activity.id}:${session.id}", activity.name, time,
                    listOf(activity.note, session.location.name).filter(String::isNotBlank).joinToString(" · "), kind)
            }
        }
        if (settings.includeCourses) {
            val courses = schedule.state.courses.associateBy { it.id }
            OccurrenceExpander.effective(schedule.semester, schedule.state, AcademicCalendar(schedule.academicDays))
                .filter { it.date == date && it.state != OccurrenceState.CANCELLED }.forEach { occurrence ->
                    val course = courses[occurrence.courseId] ?: return@forEach
                    result += DailyAgendaItem("course:${course.id}:$date:${occurrence.periodStart}", course.name,
                        periods[occurrence.periodStart]?.startTime, occurrence.room.orEmpty(), "course")
                }
        }
    }
    return result.distinctBy { listOf(it.title, it.time, it.detail, it.kind) }
        .sortedWith(compareBy<DailyAgendaItem> { if (it.kind == "homework") 0 else 1 }.thenBy { it.time ?: LocalTime.MIN }.thenBy { it.title })
}

internal fun dailyAgendaDetail(items: List<DailyAgendaItem>, maximum: Int): String =
    if (items.isEmpty()) "今天没有已加入的待办安排。" else buildString {
        items.take(maximum.coerceAtLeast(1)).forEach { item ->
            append(item.title)
            if (item.detail.isNotBlank()) append(" · ${item.detail.take(120)}")
            append('\n')
        }
        if (items.size > maximum) append("另有 ${items.size - maximum} 项，请打开日历查看。")
    }.trim().take(3500)
