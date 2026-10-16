package cn.qztc.timetable.feature.timetable

import cn.qztc.timetable.core.domain.model.ReminderDisplayMode

data class CourseReminderUiState(
    val enabled: Boolean = false,
    val minutes: Int = 15,
    val displayMode: ReminderDisplayMode = ReminderDisplayMode.SCREEN_POPUP,
    val overrides: Map<String, Int> = emptyMap(),
    val messages: Map<String, String> = emptyMap(),
    val remindOnHolidays: Boolean = false,
) {
    val displayLabel: String
        get() = if (displayMode == ReminderDisplayMode.SCREEN_POPUP) "弹窗提醒" else "仅通知栏"

    fun summary(courseId: String): String {
        if (!enabled) return "全局提醒已关闭"
        val effectiveMinutes = overrides[courseId] ?: minutes
        if (effectiveMinutes == 0) return "本课不提醒"
        return "提前 $effectiveMinutes 分钟"
    }
}
