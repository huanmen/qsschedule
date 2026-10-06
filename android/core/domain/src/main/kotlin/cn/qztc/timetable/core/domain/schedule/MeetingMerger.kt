package cn.qztc.timetable.core.domain.schedule

import cn.qztc.timetable.core.domain.model.CourseMeeting

/**
 * 连堂归并：教务网格把同一门课的连续节次重复放在相邻单元格，
 * 这里把同课程、同星期、同教师、同教室、同周次且节次相邻的安排合并为一条。
 */
object MeetingMerger {

    fun mergeConsecutive(meetings: List<CourseMeeting>): List<CourseMeeting> {
        val grouped = meetings.groupBy { meeting ->
            GroupKey(meeting.courseId, meeting.weekday, meeting.teacher, meeting.room, meeting.weekRule.weeks)
        }
        return grouped.flatMap { (_, group) -> mergeGroup(group) }
            .sortedWith(compareBy({ it.courseId }, { it.weekday }, { it.periodStart }))
    }

    private fun mergeGroup(group: List<CourseMeeting>): List<CourseMeeting> {
        val sorted = group.sortedBy { it.periodStart }
        val merged = mutableListOf<CourseMeeting>()
        var current = sorted.first()
        for (next in sorted.drop(1)) {
            current = if (next.periodStart <= current.periodEnd + 1) {
                current.copy(periodEnd = maxOf(current.periodEnd, next.periodEnd))
            } else {
                merged += current.withStableId()
                next
            }
        }
        merged += current.withStableId()
        return merged
    }

    private fun CourseMeeting.withStableId(): CourseMeeting =
        copy(id = CourseMeeting.stableId(courseId, weekday, periodStart, periodEnd))

    private data class GroupKey(
        val courseId: String,
        val weekday: Int,
        val teacher: String?,
        val room: String?,
        val weeks: List<Int>,
    )
}
