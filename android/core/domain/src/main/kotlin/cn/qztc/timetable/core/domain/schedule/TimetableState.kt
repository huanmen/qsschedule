package cn.qztc.timetable.core.domain.schedule

import cn.qztc.timetable.core.domain.model.Course
import cn.qztc.timetable.core.domain.model.CourseMeeting
import cn.qztc.timetable.core.domain.model.ScheduleException
import cn.qztc.timetable.core.domain.model.SourceType
import kotlinx.serialization.Serializable

/**
 * 一个学期课表的完整逻辑状态。差异计算、应用与快照恢复都以它为单位。
 */
@Serializable
data class TimetableState(
    val courses: List<Course> = emptyList(),
    val meetings: List<CourseMeeting> = emptyList(),
    val exceptions: List<ScheduleException> = emptyList(),
    val suppressedOccurrences: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = courses.isEmpty() && meetings.isEmpty() && exceptions.isEmpty()

    /** Last teaching week actually present in the imported education timetable. */
    val importedTeachingWeeks: Int?
        get() = meetings.asSequence().filter { it.sourceType == SourceType.QNU_EDU }
            .flatMap { it.weekRule.weeks.asSequence() }.filter { it > 0 }.maxOrNull()

    fun filterBySource(sourceType: SourceType) = TimetableState(
        courses = courses.filter { it.sourceType == sourceType },
        meetings = meetings.filter { it.sourceType == sourceType },
        exceptions = exceptions.filter { it.sourceType == sourceType },
    )

    companion object {
        val EMPTY = TimetableState()
    }
}
