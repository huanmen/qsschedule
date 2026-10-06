package cn.qztc.timetable.core.domain.diff

import cn.qztc.timetable.core.domain.model.Course
import cn.qztc.timetable.core.domain.model.CourseMeeting
import cn.qztc.timetable.core.domain.model.MeetingField
import cn.qztc.timetable.core.domain.model.ScheduleException
import cn.qztc.timetable.core.domain.schedule.TimetableState
import kotlinx.serialization.Serializable

/**
 * 一个字段级变化。[skippedByOverride] 为 true 时该字段被用户覆盖保护，应用差异时保留本地值。
 */
@Serializable
data class FieldChange(
    val field: MeetingField,
    val oldValue: String?,
    val newValue: String?,
    val skippedByOverride: Boolean = false,
)

@Serializable
data class MeetingChange(
    val old: CourseMeeting,
    val new: CourseMeeting,
    val fields: List<FieldChange>,
)

@Serializable
data class CourseChange(
    val old: Course,
    val new: Course,
)

/**
 * 新旧课表（同一来源范围内）的完整差异。
 * 节次或星期变化表现为一对删除+新增，字段变化表现为 [changedMeetings]。
 */
@Serializable
data class TimetableDiff(
    val addedCourses: List<Course> = emptyList(),
    val removedCourses: List<Course> = emptyList(),
    val addedMeetings: List<CourseMeeting> = emptyList(),
    val removedMeetings: List<CourseMeeting> = emptyList(),
    val changedCourses: List<CourseChange> = emptyList(),
    val changedMeetings: List<MeetingChange> = emptyList(),
    val addedExceptions: List<ScheduleException> = emptyList(),
    val removedExceptions: List<ScheduleException> = emptyList(),
) {
    val isEmpty: Boolean
        get() = addedCourses.isEmpty() && removedCourses.isEmpty() &&
            addedMeetings.isEmpty() && removedMeetings.isEmpty() &&
            changedCourses.isEmpty() &&
            changedMeetings.isEmpty() &&
            addedExceptions.isEmpty() && removedExceptions.isEmpty()

    val addedCount: Int get() = addedCourses.size + addedMeetings.size + addedExceptions.size
    val changedCount: Int get() = changedCourses.size + changedMeetings.size
    val removedCount: Int get() = removedCourses.size + removedMeetings.size + removedExceptions.size
}

/**
 * 以确定性 ID 为锚点比较两份同来源课表状态。
 * [current] 与 [candidate] 都应只包含同一来源（通常是 QNU_EDU）的数据。
 */
object TimetableDiffer {

    fun diff(current: TimetableState, candidate: TimetableState): TimetableDiff {
        val currentCourses = current.courses.associateBy { it.id }
        val candidateCourses = candidate.courses.associateBy { it.id }
        val currentMeetings = current.meetings.associateBy { it.id }
        val candidateMeetings = candidate.meetings.associateBy { it.id }
        val currentExceptions = current.exceptions.associateBy { it.id }
        val candidateExceptions = candidate.exceptions.associateBy { it.id }

        val changedMeetings = mutableListOf<MeetingChange>()
        for ((id, old) in currentMeetings) {
            val new = candidateMeetings[id] ?: continue
            val fields = compareMeetingFields(old, new)
            if (fields.isNotEmpty()) changedMeetings += MeetingChange(old, new, fields)
        }

        val changedCourses = currentCourses.mapNotNull { (id, old) ->
            val candidateCourse = candidateCourses[id] ?: return@mapNotNull null
            // Background timetable pages can omit the separate detail table.
            val new = candidateCourse.copy(
                code=candidateCourse.code?:old.code,
                nature=candidateCourse.nature?:old.nature,
                credits=candidateCourse.credits?:old.credits,
                selectionAttribute=candidateCourse.selectionAttribute?:old.selectionAttribute,
                assessmentMethod=candidateCourse.assessmentMethod?:old.assessmentMethod,
            )
            if (courseManagedFieldsChanged(old, new)) CourseChange(old, new) else null
        }

        return TimetableDiff(
            addedCourses = candidateCourses.values.filter { it.id !in currentCourses },
            removedCourses = currentCourses.values.filter { it.id !in candidateCourses },
            addedMeetings = candidateMeetings.values.filter { it.id !in currentMeetings },
            removedMeetings = currentMeetings.values.filter { it.id !in candidateMeetings },
            changedCourses = changedCourses,
            changedMeetings = changedMeetings,
            addedExceptions = candidateExceptions.values.filter { it.id !in currentExceptions },
            removedExceptions = currentExceptions.values.filter { it.id !in candidateExceptions },
        )
    }

    /** Compare only fields owned by the school source; local color and note stay local. */
    private fun courseManagedFieldsChanged(old: Course, new: Course): Boolean =
        old.code != new.code || old.name != new.name || old.sequence != new.sequence ||
            old.nature != new.nature || old.credits != new.credits ||
            old.selectionAttribute != new.selectionAttribute || old.assessmentMethod != new.assessmentMethod

    private fun compareMeetingFields(old: CourseMeeting, new: CourseMeeting): List<FieldChange> {
        val fields = mutableListOf<FieldChange>()
        if (old.teacher != new.teacher) {
            fields += FieldChange(
                MeetingField.TEACHER, old.teacher, new.teacher,
                skippedByOverride = MeetingField.TEACHER in old.overrideMask,
            )
        }
        if (old.room != new.room) {
            fields += FieldChange(
                MeetingField.ROOM, old.room, new.room,
                skippedByOverride = MeetingField.ROOM in old.overrideMask,
            )
        }
        if (old.weekRule.weeks != new.weekRule.weeks) {
            fields += FieldChange(
                MeetingField.WEEKS, old.weekRule.weeks.toString(), new.weekRule.weeks.toString(),
                skippedByOverride = MeetingField.WEEKS in old.overrideMask,
            )
        }
        return fields
    }
}

/**
 * 把差异应用到当前状态。只按 ID 增删改差异中出现的行，
 * 手动数据不在差异范围内因此保持不变；被覆盖保护的字段保留本地值。
 */
object TimetableDiffApplier {

    fun apply(current: TimetableState, diff: TimetableDiff): TimetableState {
        val removedCourseIds = diff.removedCourses.map { it.id }.toSet()
        val removedMeetingIds = diff.removedMeetings.map { it.id }.toSet()
        val removedExceptionIds = diff.removedExceptions.map { it.id }.toSet()
        val courseChanges = diff.changedCourses.associateBy { it.old.id }
        val changesById = diff.changedMeetings.associateBy { it.old.id }

        val courses = current.courses
            .filter { it.id !in removedCourseIds }
            .map { course ->
                val change = courseChanges[course.id] ?: return@map course
                change.new.copy(color = course.color, note = course.note)
            } + diff.addedCourses
        val exceptions = current.exceptions.filter { it.id !in removedExceptionIds } + diff.addedExceptions
        val meetings = current.meetings
            .filter { it.id !in removedMeetingIds }
            .map { meeting -> changesById[meeting.id]?.let { applyChange(meeting, it) } ?: meeting } +
            diff.addedMeetings

        return TimetableState(courses, meetings, exceptions)
    }

    private fun applyChange(local: CourseMeeting, change: MeetingChange): CourseMeeting {
        var result = local
        for (field in change.fields) {
            if (field.skippedByOverride) continue
            result = when (field.field) {
                MeetingField.TEACHER -> result.copy(teacher = change.new.teacher)
                MeetingField.ROOM -> result.copy(room = change.new.room)
                MeetingField.WEEKS -> result.copy(weekRule = change.new.weekRule)
            }
        }
        return result
    }
}
