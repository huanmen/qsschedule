package cn.qztc.timetable.core.domain.schedule

import cn.qztc.timetable.core.domain.model.*
import cn.qztc.timetable.core.domain.calendar.SemesterCalendar
import java.time.LocalDateTime

/** Sync only dated cancellations/moves still relevant now; never replace term courses. */
object UpcomingScheduleChanges {
    fun isUpcoming(semester: Semester, exception: ScheduleException, periods: List<Period>, now: LocalDateTime): Boolean {
        fun relevant(slot: SlotRef?): Boolean {
            if (slot == null || slot.week !in 1..semester.totalWeeks || slot.weekday !in 1..7) return false
            val date = SemesterCalendar.dateOf(semester, slot.week, slot.weekday)
            val end = periods.firstOrNull { it.no == slot.periodEnd }?.endTime ?: java.time.LocalTime.MAX
            return date.atTime(end).isAfter(now)
        }
        return exception.type in setOf(ExceptionType.CANCEL, ExceptionType.MOVE) && (relevant(exception.original) || relevant(exception.replacement))
    }
    fun merge(semester: Semester, current: TimetableState, incoming: TimetableState, periods: List<Period>, now: LocalDateTime,
        includeExpired: Boolean = false): TimetableState {
        fun relevant(slot: SlotRef?): Boolean {
            if(slot==null)return false
            val date=SemesterCalendar.dateOf(semester,slot.week,slot.weekday)
            val end=periods.firstOrNull { it.no==slot.periodEnd }?.endTime ?: java.time.LocalTime.MAX
            return date.atTime(end).isAfter(now)
        }
        fun upcoming(e: ScheduleException) = e.type in setOf(ExceptionType.CANCEL,ExceptionType.MOVE) && (relevant(e.original)||relevant(e.replacement))
        val updates=incoming.exceptions.filter { if (includeExpired) it.type in setOf(ExceptionType.CANCEL, ExceptionType.MOVE) else upcoming(it) }.mapNotNull { exception ->
            val course=incoming.courses.firstOrNull { it.id==exception.courseId } ?: return@mapNotNull null
            fun sequence(value: String?) = value?.trim()?.let { it.toIntOrNull()?.toString() ?: it }
            fun name(value: String) = value.replace(Regex("[\\s\\u00a0　]"), "").replace('（', '(').replace('）', ')')
            val matches=current.courses.filter { it.sourceType==SourceType.QNU_EDU &&
                sequence(it.sequence)==sequence(course.sequence) &&
                ((course.code!=null && it.code==course.code) || (name(it.name)==name(course.name) && (it.code==null || course.code==null))) }
            val target=matches.singleOrNull() ?: return@mapNotNull null
            val anchor=exception.original ?: exception.replacement ?: return@mapNotNull null
            exception.copy(courseId=target.id,id=ScheduleException.stableId(exception.type,target.id,anchor),
                courseName=ScheduleCourseNames.fullest(exception.courseName, course.name, target.name))
        }
        // Missing details may be a partial school response: retain existing
        // exceptions unless the same stable occurrence has an explicit update.
        val byId=current.exceptions.associateBy { it.id }.toMutableMap()
        updates.forEach { byId[it.id]=it }
        return current.copy(exceptions=byId.values.sortedBy { it.id })
    }
}
