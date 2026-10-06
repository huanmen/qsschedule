package cn.qztc.timetable.core.domain.schedule

import cn.qztc.timetable.core.domain.model.ActivityKind
import cn.qztc.timetable.core.domain.model.CourseMeeting
import cn.qztc.timetable.core.domain.model.ScheduleActivity
import java.time.LocalDate

data class ActivityConflictHit(
    val meeting: CourseMeeting,
    val courseName: String,
)

object ActivityConflict {

    fun overlappingMeetings(
        activity: ScheduleActivity,
        state: TimetableState,
        dateOf: (week: Int, weekday: Int) -> LocalDate,
    ): List<ActivityConflictHit> {
        val courses = state.courses.associateBy { it.id }
        return state.meetings.mapNotNull { meeting ->
            if (!overlaps(activity, meeting, dateOf)) return@mapNotNull null
            ActivityConflictHit(
                meeting = meeting,
                courseName = courses[meeting.courseId]?.name?.ifBlank { null } ?: "未命名课程",
            )
        }
    }

    internal fun overlaps(
        activity: ScheduleActivity,
        meeting: CourseMeeting,
        dateOf: (week: Int, weekday: Int) -> LocalDate,
    ): Boolean = if (activity.sessions.isNotEmpty()) {
        activity.sessions.any { session ->
            when (session.kind) {
                ActivityKind.PERIOD -> session.weekday == meeting.weekday &&
                    periodsOverlap(requireNotNull(session.periodStart), requireNotNull(session.periodEnd), meeting.periodStart, meeting.periodEnd) &&
                    meeting.weekRule.weeks.any { it in session.weeks }
                ActivityKind.DAY -> meeting.weekRule.weeks.any { dateOf(it, meeting.weekday) == session.date }
            }
        }
    } else when (activity.kind) {
        ActivityKind.PERIOD -> {
            val weekday = activity.weekday ?: return false
            val start = activity.periodStart ?: return false
            val end = activity.periodEnd ?: start
            weekday == meeting.weekday &&
                periodsOverlap(start, end, meeting.periodStart, meeting.periodEnd) &&
                meeting.weekRule.weeks.any { it in activity.weeks }
        }
        ActivityKind.DAY -> {
            val start = activity.startDate ?: return false
            val end = activity.endDate ?: start
            meeting.weekRule.weeks.any { week ->
                val date = dateOf(week, meeting.weekday)
                !date.isBefore(start) && !date.isAfter(end)
            }
        }
    }

    internal fun periodsOverlap(startA: Int, endA: Int, startB: Int, endB: Int): Boolean =
        startA <= endB && startB <= endA
}
