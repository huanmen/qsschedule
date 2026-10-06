package cn.qztc.timetable.core.domain.schedule

import cn.qztc.timetable.core.domain.model.ClassOccurrence
import cn.qztc.timetable.core.domain.model.CourseMeeting
import cn.qztc.timetable.core.domain.model.Semester

data class MeetingConflictHit(
    val meeting: CourseMeeting?,
    val courseId: String,
    val courseName: String,
    val weekday: Int,
    val periodStart: Int,
    val periodEnd: Int,
    val weeks: List<Int>,
)

object MeetingConflict {

    /**
     * 按展开后的课次判断占用。停课、调走后的原位置不算有课，可以覆盖；
     * 调入、补课仍占用时间。
     */
    fun overlapping(
        weekday: Int,
        periodStart: Int,
        periodEnd: Int,
        weeks: Collection<Int>,
        state: TimetableState,
        semester: Semester,
        ignoreMeetingId: String? = null,
        calendar: cn.qztc.timetable.core.domain.calendar.AcademicCalendar = cn.qztc.timetable.core.domain.calendar.AcademicCalendar.EMPTY,
    ): List<MeetingConflictHit> {
        val weekSet = weeks.toSet()
        if (weekSet.isEmpty()) return emptyList()
        val courses = state.courses.associateBy { it.id }
        val meetings = state.meetings.associateBy { it.id }
        val grouped = LinkedHashMap<String, MutableList<ClassOccurrence>>()
        val candidateCourse = cn.qztc.timetable.core.domain.model.Course(
            id = "conflict-candidate", semesterId = semester.id,
            sourceType = cn.qztc.timetable.core.domain.model.SourceType.MANUAL,
            sourceKey = "conflict-candidate", name = "待保存课程",
        )
        val candidateMeeting = CourseMeeting(
            id = "conflict-candidate", courseId = candidateCourse.id, weekday = weekday,
            periodStart = periodStart, periodEnd = periodEnd,
            weekRule = cn.qztc.timetable.core.domain.model.WeekRule.of(weekSet.toList()),
            sourceType = cn.qztc.timetable.core.domain.model.SourceType.MANUAL,
        )
        val targets = OccurrenceExpander.replacementTargets(semester, state,
            TimetableState(listOf(candidateCourse), listOf(candidateMeeting)), calendar)
        for (occ in targets) {
            if (!occ.isActive) continue
            if (ignoreMeetingId != null && occ.meetingId == ignoreMeetingId) continue
            val key = "${occ.meetingId ?: occ.exceptionId ?: occ.courseId}:${occ.weekday}:${occ.periodStart}-${occ.periodEnd}"
            grouped.getOrPut(key) { mutableListOf() } += occ
        }
        return grouped.values.map { occs ->
            val first = occs.first()
            MeetingConflictHit(
                meeting = first.meetingId?.let { meetings[it] },
                courseId = first.courseId,
                courseName = courses[first.courseId]?.name?.ifBlank { null } ?: "未命名课程",
                weekday = first.weekday,
                periodStart = occs.minOf { it.periodStart },
                periodEnd = occs.maxOf { it.periodEnd },
                weeks = occs.map { it.week }.distinct().sorted(),
            )
        }
    }
}
