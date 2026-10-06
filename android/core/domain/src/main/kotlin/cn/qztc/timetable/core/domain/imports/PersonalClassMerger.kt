package cn.qztc.timetable.core.domain.imports

import cn.qztc.timetable.core.domain.model.Course
import cn.qztc.timetable.core.domain.model.CourseMeeting
import cn.qztc.timetable.core.domain.schedule.TimetableState

/**
 * Merges a personal timetable with a class timetable.
 * Matching slots keep the personal row; class-only rows and extra teacher names
 * are returned for the user to accept or edit.
 */
object PersonalClassMerger {

    data class ExtraSlot(
        val course: Course,
        val meeting: CourseMeeting,
    )

    data class TeacherConflict(
        val courseName: String,
        val weekday: Int,
        val periodStart: Int,
        val periodEnd: Int,
        val personalTeacher: String?,
        val extraTeachers: List<String>,
        val meetingId: String,
    )

    data class OverlapConflict(
        val personalCourseName: String,
        val classCourseName: String,
        val weekday: Int,
        val personalPeriodStart: Int,
        val personalPeriodEnd: Int,
        val classPeriodStart: Int,
        val classPeriodEnd: Int,
        val weeks: List<Int>,
        val personalMeetingId: String,
        val classMeetingId: String,
    )

    data class Result(
        val preferred: TimetableState,
        val extras: List<ExtraSlot>,
        val teacherConflicts: List<TeacherConflict>,
        val overlapConflicts: List<OverlapConflict> = emptyList(),
    ) {
        val needsReview: Boolean get() = extras.isNotEmpty() || teacherConflicts.isNotEmpty() || overlapConflicts.isNotEmpty()
    }

    fun merge(personal: TimetableState, classTable: TimetableState): Result {
        val personalByKey = personal.meetings.groupBy { key(personal, it) }
        val extras = mutableListOf<ExtraSlot>()
        val teacherConflicts = linkedMapOf<String, TeacherConflict>()
        val overlapConflicts = linkedMapOf<String, OverlapConflict>()

        // Check every personal/class pair before importing class-only rows. A
        // same-course row is the expected representation of one class; only
        // different courses occupying the same weekday, period, and week need
        // explicit user review.
        personal.meetings.forEach { personalMeeting ->
            val personalCourse = personal.courses.firstOrNull { it.id == personalMeeting.courseId } ?: return@forEach
            classTable.meetings.forEach { classMeeting ->
                if (personalMeeting.weekday != classMeeting.weekday) return@forEach
                if (personalMeeting.periodEnd < classMeeting.periodStart || classMeeting.periodEnd < personalMeeting.periodStart) return@forEach
                val weeks = personalMeeting.weekRule.weeks.intersect(classMeeting.weekRule.weeks.toSet()).sorted()
                if (weeks.isEmpty()) return@forEach
                val classCourse = classTable.courses.firstOrNull { it.id == classMeeting.courseId } ?: return@forEach
                if (personalCourse.id == classCourse.id || sameCourse(personalCourse.name, classCourse.name)) return@forEach
                val id = "${personalMeeting.id}|${classMeeting.id}"
                overlapConflicts[id] = OverlapConflict(
                    personalCourseName = personalCourse.name,
                    classCourseName = classCourse.name,
                    weekday = personalMeeting.weekday,
                    personalPeriodStart = personalMeeting.periodStart,
                    personalPeriodEnd = personalMeeting.periodEnd,
                    classPeriodStart = classMeeting.periodStart,
                    classPeriodEnd = classMeeting.periodEnd,
                    weeks = weeks,
                    personalMeetingId = personalMeeting.id,
                    classMeetingId = classMeeting.id,
                )
            }
        }

        classTable.meetings.forEach { classMeeting ->
            val classCourse = classTable.courses.firstOrNull { it.id == classMeeting.courseId } ?: return@forEach
            val candidates = personalByKey[key(classTable, classMeeting)].orEmpty()
                .filter { p -> p.weekRule.weeks.any { it in classMeeting.weekRule.weeks } }
            val sequenceMatches = candidates.filter { p -> personal.courses.find { it.id == p.courseId }?.sequence == classCourse.sequence }
            val match = sequenceMatches.singleOrNull() ?: candidates.singleOrNull()
            if (match == null) {
                extras += ExtraSlot(classCourse, classMeeting)
                return@forEach
            }
            val extraTeachers = extraTeacherNames(match.teacher, classMeeting.teacher)
            if (extraTeachers.isNotEmpty()) {
                val previous = teacherConflicts[match.id]
                teacherConflicts[match.id] = TeacherConflict(
                    courseName = classCourse.name,
                    weekday = classMeeting.weekday,
                    periodStart = classMeeting.periodStart,
                    periodEnd = classMeeting.periodEnd,
                    personalTeacher = match.teacher,
                    extraTeachers = (previous?.extraTeachers.orEmpty() + extraTeachers).distinct(),
                    meetingId = match.id,
                )
            }
        }

        return Result(
            preferred = personal,
            extras = extras.distinctBy { it.meeting.id },
            teacherConflicts = teacherConflicts.values.toList(),
            overlapConflicts = overlapConflicts.values.toList(),
        )
    }

    fun apply(
        personal: TimetableState,
        selectedExtras: List<ExtraSlot>,
        teacherChoices: Map<String, String>,
    ): TimetableState {
        val courses = personal.courses.toMutableList()
        val meetings = personal.meetings.map { meeting ->
            val chosen = teacherChoices[meeting.id]
            if (chosen.isNullOrBlank()) meeting else meeting.copy(teacher = chosen)
        }.toMutableList()
        selectedExtras.forEach { extra ->
            val existing = courses.firstOrNull { it.name == extra.course.name }
            val course = existing ?: extra.course.also(courses::add)
            val meeting = extra.meeting.copy(
                id = CourseMeeting.stableId(course.id, extra.meeting.weekday, extra.meeting.periodStart, extra.meeting.periodEnd),
                courseId = course.id,
                teacher = teacherChoices[extra.meeting.id] ?: extra.meeting.teacher,
            )
            if (meetings.none { it.id == meeting.id }) meetings += meeting
        }
        return TimetableState(
            courses = courses,
            meetings = meetings,
            exceptions = personal.exceptions,
        )
    }

    fun teacherChoiceKey(courseName: String, weekday: Int, periodStart: Int, periodEnd: Int): String =
        "$courseName|$weekday|$periodStart-$periodEnd"

    internal fun key(state: TimetableState, meeting: CourseMeeting): String {
        val name = state.courses.firstOrNull { it.id == meeting.courseId }?.name.orEmpty().trim()
        return "$name|${meeting.weekday}|${meeting.periodStart}-${meeting.periodEnd}"
    }

    internal fun extraTeacherNames(personal: String?, classTeacher: String?): List<String> {
        val personalNames = splitTeachers(personal)
        return splitTeachers(classTeacher).filter { name -> name !in personalNames }
    }

    internal fun splitTeachers(raw: String?): List<String> =
        raw.orEmpty()
            .split(',', '，', '、', '/', ';', '；')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    private fun sameCourse(left: String, right: String): Boolean =
        left.replace(Regex("[\\s\\u00a0　]"), "") == right.replace(Regex("[\\s\\u00a0　]"), "")
}
