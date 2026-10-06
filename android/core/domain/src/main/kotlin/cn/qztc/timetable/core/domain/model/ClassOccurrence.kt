package cn.qztc.timetable.core.domain.model

import java.time.LocalDate

enum class OccurrenceState { NORMAL, MAKEUP, MOVED_IN, CANCELLED, SUBSTITUTED }

/**
 * 某个日期实际发生（或被取消）的课次。由安排与异常展开计算，不直接持久化。
 */
data class ClassOccurrence(
    val courseId: String,
    val meetingId: String?,
    val date: LocalDate,
    val week: Int,
    val weekday: Int,
    val periodStart: Int,
    val periodEnd: Int,
    val teacher: String?,
    val room: String?,
    val state: OccurrenceState,
    val exceptionId: String? = null,
) {
    val isActive: Boolean get() = state != OccurrenceState.CANCELLED

    fun overlaps(other: ClassOccurrence): Boolean =
        date == other.date &&
            periodStart <= other.periodEnd &&
            other.periodStart <= periodEnd
}
