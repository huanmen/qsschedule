package cn.qztc.timetable.core.domain.model

import kotlinx.serialization.Serializable

enum class ExceptionType { MAKEUP, MOVE, CANCEL, SUBSTITUTE }

/**
 * 某个具体课次位置：第 [week] 周星期 [weekday] 的第 [periodStart]..[periodEnd] 节。
 * [dateText] 保留教务页面的原始日期文本（如 `12-27`），仅用于展示核对。
 */
@Serializable
data class SlotRef(
    val week: Int,
    val weekday: Int,
    val periodStart: Int,
    val periodEnd: Int,
    val room: String? = null,
    val teacher: String? = null,
    val dateText: String? = null,
)

/**
 * 停课、调课、补课、代课。
 * CANCEL 只有 [original]；MAKEUP 只有 [replacement]；MOVE 两者都有；SUBSTITUTE 用 [substituteTeacher]。
 */
@Serializable
data class ScheduleException(
    val id: String,
    val courseId: String,
    val type: ExceptionType,
    val original: SlotRef? = null,
    val replacement: SlotRef? = null,
    val substituteTeacher: String? = null,
    val sourceType: SourceType,
    val note: String? = null,
    /** Full school course name for change notices that are kept outside the timetable. */
    val courseName: String? = null,
) {
    companion object {
        fun stableId(type: ExceptionType, courseId: String, slot: SlotRef): String =
            "$courseId:${type.name.lowercase()}:w${slot.week}d${slot.weekday}p${slot.periodStart}-${slot.periodEnd}"
    }
}
