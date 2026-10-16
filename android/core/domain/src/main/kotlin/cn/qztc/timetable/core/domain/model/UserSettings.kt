package cn.qztc.timetable.core.domain.model

import kotlinx.serialization.Serializable

enum class HomeView { TODAY, WEEK, MINE, CALENDAR }

/** 启动页偏好；LAST 表示打开上一次使用的今日或周课表。 */
enum class HomePreference { TODAY, WEEK, CALENDAR, LAST }

/** 周末无课时的周课表列显示策略；有周末课时始终恢复为完整七列。 */
enum class WeekendDisplayMode { SHOW_ALL, FOLD_EMPTY, HIDE_EMPTY }

/** 用户设置（V1 任务 02 需要持久化的最小集合，不含任何凭据）。 */
@Serializable
data class UserSettings(
    val homeView: HomeView = HomeView.TODAY,
    val activeSemesterId: String? = null,
    val syncEnabled: Boolean = false,
    val syncIntervalHours: Int = 2,
)
