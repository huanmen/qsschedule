package cn.qztc.timetable.core.domain.model

import kotlinx.serialization.Serializable

/** 用户手动改过、同步默认不覆盖的排课字段。 */
enum class MeetingField { TEACHER, ROOM, WEEKS }

/**
 * 一条长期重复的上课安排：星期 [weekday]（1=周一..7=周日）的第 [periodStart]..[periodEnd] 节，
 * 按 [weekRule] 的周次重复。
 */
@Serializable
data class CourseMeeting(
    val id: String,
    val courseId: String,
    val weekday: Int,
    val periodStart: Int,
    val periodEnd: Int,
    val weekRule: WeekRule,
    val teacher: String? = null,
    val room: String? = null,
    val sourceType: SourceType,
    val overrideMask: Set<MeetingField> = emptySet(),
) {
    init {
        require(weekday in 1..7) { "weekday must be 1..7" }
        require(periodStart in 1..MAX_PERIOD && periodEnd in periodStart..MAX_PERIOD) {
            "invalid period range $periodStart..$periodEnd"
        }
    }

    companion object {
        const val MAX_PERIOD = 13

        /** 确定性 ID，保证同一来源反复导入得到稳定标识。 */
        fun stableId(courseId: String, weekday: Int, periodStart: Int, periodEnd: Int): String =
            "$courseId:d$weekday:p$periodStart-$periodEnd"
    }
}
