package cn.qztc.timetable.core.domain.schedule

import cn.qztc.timetable.core.domain.calendar.AcademicCalendar
import cn.qztc.timetable.core.domain.calendar.AcademicDaySchedule
import cn.qztc.timetable.core.domain.calendar.SemesterCalendar
import cn.qztc.timetable.core.domain.model.ClassOccurrence
import cn.qztc.timetable.core.domain.model.CourseMeeting
import cn.qztc.timetable.core.domain.model.ExceptionType
import cn.qztc.timetable.core.domain.model.OccurrenceState
import cn.qztc.timetable.core.domain.model.ScheduleException
import cn.qztc.timetable.core.domain.model.Semester
import cn.qztc.timetable.core.domain.model.SlotRef
import java.time.LocalDate

data class OccurrenceConflict(
    val date: LocalDate,
    val first: ClassOccurrence,
    val second: ClassOccurrence,
)

/**
 * 把长期安排与调停补异常展开成具体日期的课次，并检测时间冲突。
 */
object OccurrenceExpander {

    fun key(occurrence: ClassOccurrence): String = listOf(occurrence.courseId,
        occurrence.meetingId ?: occurrence.exceptionId.orEmpty(), occurrence.date.toString(),
        occurrence.periodStart.toString(), occurrence.periodEnd.toString()).joinToString("|")

    fun effective(semester: Semester, state: TimetableState, calendar: AcademicCalendar = AcademicCalendar.EMPTY): List<ClassOccurrence> =
        (1..semester.totalWeeks).flatMap { expandVisibleWeek(semester, state, it, calendar) }

    /** Match separately in the weekly plan and the actual calendar. Holidays must not
     * leave the old course visible in the plan after a confirmed replacement. */
    fun replacementTargets(
        semester: Semester, existing: TimetableState, replacement: TimetableState,
        calendar: AcademicCalendar = AcademicCalendar.EMPTY,
    ): List<ClassOccurrence> {
        val replacementIds = replacement.courses.map { it.id }.toSet()
        return listOf(AcademicCalendar.EMPTY, calendar).flatMap { viewCalendar ->
            val targets = effective(semester, replacement, viewCalendar).filter { it.isActive }.groupBy { it.date }
            effective(semester, existing, viewCalendar).filter { old ->
                old.courseId !in replacementIds && targets[old.date].orEmpty().any { it.overlaps(old) }
            }
        }.distinctBy(::key)
    }

    private fun visible(occurrences: List<ClassOccurrence>, state: TimetableState): List<ClassOccurrence> {
        if (state.suppressedOccurrences.isEmpty()) return occurrences
        val hidden = state.suppressedOccurrences.toHashSet()
        return occurrences.filterNot { key(it) in hidden }
    }

    fun expand(semester: Semester, state: TimetableState, week: Int? = null): List<ClassOccurrence> {
        val occurrences = mutableListOf<ClassOccurrence>()

        for (meeting in state.meetings) {
            for (meetingWeek in meeting.weekRule.weeks) {
                if (meetingWeek > semester.totalWeeks) continue
                if (week != null && meetingWeek != week) continue
                occurrences += ClassOccurrence(
                    courseId = meeting.courseId,
                    meetingId = meeting.id,
                    date = SemesterCalendar.dateOf(semester, meetingWeek, meeting.weekday),
                    week = meetingWeek,
                    weekday = meeting.weekday,
                    periodStart = meeting.periodStart,
                    periodEnd = meeting.periodEnd,
                    teacher = meeting.teacher,
                    room = meeting.room,
                    state = OccurrenceState.NORMAL,
                )
            }
        }

        // 先加补课和调入课次，再处理取消与代课，保证“补课后又停课”也能标记。
        for (exception in state.exceptions) {
            when (exception.type) {
                ExceptionType.MAKEUP -> exception.replacement?.let { slot ->
                    if (slot.inWeek(week)) {
                        occurrences += additionOccurrence(semester, exception, slot, OccurrenceState.MAKEUP, slot.teacher)
                    }
                }
                ExceptionType.MOVE -> exception.replacement?.let { slot ->
                    if (slot.inWeek(week)) {
                        val originalTeacher = teacherForOriginal(occurrences, state.meetings, exception.courseId, exception.original)
                        occurrences += additionOccurrence(semester, exception, slot, OccurrenceState.MOVED_IN, slot.teacher ?: originalTeacher)
                    }
                }
                else -> Unit
            }
        }

        for (exception in state.exceptions) {
            when (exception.type) {
                ExceptionType.MOVE, ExceptionType.CANCEL -> exception.original?.let { slot ->
                    if (slot.inWeek(week)) {
                        replaceMatching(occurrences, exception.courseId, slot) { occ ->
                            occ.copy(state = OccurrenceState.CANCELLED, exceptionId = exception.id)
                        }
                    }
                }
                ExceptionType.SUBSTITUTE -> exception.original?.let { slot ->
                    if (slot.inWeek(week)) {
                        replaceMatching(occurrences, exception.courseId, slot) { occ ->
                            occ.copy(
                                state = OccurrenceState.SUBSTITUTED,
                                teacher = exception.substituteTeacher ?: occ.teacher,
                                exceptionId = exception.id,
                            )
                        }
                    }
                }
                else -> Unit
            }
        }

        return occurrences.sortedWith(compareBy({ it.date }, { it.periodStart }, { it.courseId }))
    }

    /**
     * 按课表上的第 [week] 周各列展开。放假列不排课；调休日改用指定周次、星期的课。
     */
    fun expandVisibleWeek(
        semester: Semester,
        state: TimetableState,
        week: Int,
        calendar: AcademicCalendar = AcademicCalendar.EMPTY,
        includeHolidayCourses: Boolean = false,
    ): List<ClassOccurrence> {
        if (calendar.isEmpty) return visible(expand(semester, state, week), state)
        val teaching = expand(semester, state)
        val bySlot = teaching.groupBy { it.week to it.weekday }
        val result = mutableListOf<ClassOccurrence>()
        for (displayWeekday in 1..7) {
            val date = SemesterCalendar.dateOf(semester, week, displayWeekday)
            when (val resolved = calendar.resolve(semester, date)) {
                is AcademicDaySchedule.Holiday -> {
                    if (includeHolidayCourses) {
                        result += bySlot[week to displayWeekday].orEmpty().map { occurrence ->
                            occurrence.copy(date = date, week = week, weekday = displayWeekday)
                        }
                    }
                }
                is AcademicDaySchedule.Teaching -> {
                    val source = bySlot[resolved.week to resolved.weekday].orEmpty()
                    result += source.map { occ ->
                        occ.copy(date = date, week = week, weekday = displayWeekday)
                    }
                }
            }
        }
        return visible(result.sortedWith(compareBy({ it.date }, { it.periodStart }, { it.courseId })), state)
    }

    fun findConflicts(occurrences: List<ClassOccurrence>): List<OccurrenceConflict> {
        val conflicts = mutableListOf<OccurrenceConflict>()
        val byDate = occurrences.filter { it.isActive }.groupBy { it.date }
        for ((date, sameDay) in byDate) {
            for (i in sameDay.indices) {
                for (j in i + 1 until sameDay.size) {
                    val a = sameDay[i]
                    val b = sameDay[j]
                    if (a.overlaps(b)) conflicts += OccurrenceConflict(date, a, b)
                }
            }
        }
        return conflicts
    }

    private fun additionOccurrence(
        semester: Semester,
        exception: ScheduleException,
        slot: SlotRef,
        state: OccurrenceState,
        teacher: String?,
    ) = ClassOccurrence(
        courseId = exception.courseId,
        meetingId = null,
        date = SemesterCalendar.dateOf(semester, slot.week, slot.weekday),
        week = slot.week,
        weekday = slot.weekday,
        periodStart = slot.periodStart,
        periodEnd = slot.periodEnd,
        teacher = teacher,
        room = slot.room,
        state = state,
        exceptionId = exception.id,
    )

    private fun SlotRef.inWeek(week: Int?): Boolean = week == null || this.week == week

    private fun teacherForOriginal(
        occurrences: List<ClassOccurrence>,
        meetings: List<CourseMeeting>,
        courseId: String,
        original: SlotRef?,
    ): String? {
        if (original == null) return null
        occurrences.firstOrNull { matches(it, courseId, original) }?.teacher?.let { return it }
        return meetings.firstOrNull { meeting ->
            meeting.courseId == courseId &&
                meeting.weekday == original.weekday &&
                meeting.periodStart <= original.periodEnd &&
                original.periodStart <= meeting.periodEnd &&
                original.week in meeting.weekRule
        }?.teacher
    }

    private fun matches(occ: ClassOccurrence, courseId: String, slot: SlotRef): Boolean =
        occ.courseId == courseId &&
            occ.week == slot.week &&
            occ.weekday == slot.weekday &&
            occ.periodStart <= slot.periodEnd &&
            slot.periodStart <= occ.periodEnd

    private fun replaceMatching(
        occurrences: MutableList<ClassOccurrence>,
        courseId: String,
        slot: SlotRef,
        transform: (ClassOccurrence) -> ClassOccurrence,
    ) {
        val updated = mutableListOf<ClassOccurrence>()
        for (occ in occurrences) {
            if (occ.state != OccurrenceState.CANCELLED && matches(occ, courseId, slot)) {
                val start = maxOf(occ.periodStart, slot.periodStart)
                val end = minOf(occ.periodEnd, slot.periodEnd)
                if (occ.periodStart < start) updated += occ.copy(periodEnd = start - 1)
                updated += transform(occ.copy(periodStart = start, periodEnd = end))
                if (end < occ.periodEnd) updated += occ.copy(periodStart = end + 1)
            } else updated += occ
        }
        occurrences.clear()
        occurrences.addAll(updated)
    }
}
