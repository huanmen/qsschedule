package cn.qztc.timetable.plugin

import android.content.Context
import android.content.Intent
import android.net.Uri
import cn.qztc.timetable.core.data.db.TimetableDatabase
import cn.qztc.timetable.core.data.repository.TimetableRepository
import cn.qztc.timetable.core.domain.model.*
import cn.qztc.timetable.core.domain.schedule.TimetableState
import cn.qztc.timetable.reminder.DailyAgendaItem
import cn.qztc.timetable.reminder.DailyAgendaScheduler
import cn.qztc.timetable.notifications.QztcNotifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

enum class PluginCapability(val label: String) {
    READ_TIMETABLE("读取学期与课程安排"), READ_CALENDAR("读取已加入的日历与活动"),
    WRITE_ACTIVITIES("管理本插件创建的活动"), NOTIFICATIONS("发送本插件的通知"),
    OPEN_LINKS("打开公开网页"), PUBLIC_DATA("读取公开赛事与考证数据")
}

class PluginAccessStore(context: Context) {
    private val prefs = context.getSharedPreferences("plugin-host-access", Context.MODE_PRIVATE)
    fun grants(id: String): Set<PluginCapability> = prefs.getStringSet(id, emptySet()).orEmpty()
        .mapNotNull { runCatching { PluginCapability.valueOf(it) }.getOrNull() }.toSet()
    fun set(id: String, capabilities: Set<PluginCapability>) {
        prefs.edit().putStringSet(id, capabilities.map { it.name }.toSet()).apply()
    }
    fun revoke(id: String) { prefs.edit().remove(id).apply() }
}

/** Native SDK v1. Host data and credentials are never exposed as raw database/files. */
class PluginHostServices(private val context: Context, private val pluginId: String) {
    val apiVersion: Int get() = 1
    private val plugin get() = PluginRegistry.plugins.singleOrNull { it.descriptor.id == pluginId }
        ?: throw SecurityException("插件未注册")
    private fun requireCapability(capability: PluginCapability) {
        requirePluginCapability(plugin.installedVersion(context) > 0, plugin.descriptor.capabilities,
            PluginAccessStore(context).grants(pluginId), capability)
    }
    private suspend fun <T> repository(block: suspend (TimetableRepository) -> T): T = withContext(Dispatchers.IO) {
        val database = TimetableDatabase.build(context)
        try { block(TimetableRepository(database)) } finally { database.close() }
    }

    suspend fun semesters(): List<Semester> {
        requireCapability(PluginCapability.READ_TIMETABLE)
        return repository { it.semesters() }
    }
    suspend fun timetable(semesterId: String): TimetableState {
        requireCapability(PluginCapability.READ_TIMETABLE)
        return repository { it.state(semesterId) }
    }
    suspend fun periodScheme(semesterId: String): PeriodScheme? {
        requireCapability(PluginCapability.READ_TIMETABLE)
        return repository { it.periodScheme(semesterId) }
    }
    suspend fun activities(semesterId: String): List<ScheduleActivity> {
        requireCapability(PluginCapability.READ_CALENDAR)
        return repository { it.activities(semesterId) }
    }
    suspend fun agenda(date: LocalDate, includeCourses: Boolean = false): List<DailyAgendaItem> {
        requireCapability(PluginCapability.READ_CALENDAR)
        if (includeCourses) requireCapability(PluginCapability.READ_TIMETABLE)
        return withContext(Dispatchers.IO) {
            DailyAgendaScheduler.items(context, date, DailyAgendaSettings(includeCourses = includeCourses))
        }
    }
    suspend fun saveActivity(activity: ScheduleActivity) {
        requireCapability(PluginCapability.WRITE_ACTIVITIES)
        requirePluginActivityOwnership(pluginId, activity.id)
        require(activity.name.isNotBlank() && activity.name.length <= 160 && activity.note.length <= 2000)
        require(activity.sessions.size <= 500 && activity.weeks.size <= 60)
        require(activity.startDate == null || activity.endDate == null ||
            java.time.temporal.ChronoUnit.DAYS.between(activity.startDate, activity.endDate) in 0..366)
        repository { repo ->
            require(repo.semester(activity.semesterId) != null) { "学期不存在" }
            val owned = repo.activities(activity.semesterId).filter { it.id.startsWith("plugin:$pluginId:") }
            require(owned.size < 200 || owned.any { it.id == activity.id }) { "活动数量已达上限" }
            repo.saveActivityWithCoverage(activity, emptyList())
        }
    }
    suspend fun deleteActivity(id: String) {
        requireCapability(PluginCapability.WRITE_ACTIVITIES)
        requirePluginActivityOwnership(pluginId, id)
        repository { it.deleteActivity(id) }
    }
    fun notify(title: String, detail: String, mode: ReminderDisplayMode = ReminderDisplayMode.NOTIFICATION_BAR): Boolean {
        requireCapability(PluginCapability.NOTIFICATIONS)
        return QztcNotifications.notifyPlugin(context, pluginId, plugin.descriptor.name,
            title.take(160), detail.take(3500), mode)
    }
    fun openLink(url: String) {
        requireCapability(PluginCapability.OPEN_LINKS)
        val uri = Uri.parse(url)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null)
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    suspend fun publicSchedulesJson(): String {
        requireCapability(PluginCapability.PUBLIC_DATA)
        return withContext(Dispatchers.IO) { context.assets.open("academic-hub/community-feed.json").bufferedReader().use { it.readText() } }
    }
    fun readSetting(key: String): String? {
        require(plugin.installedVersion(context) > 0)
        require(key.matches(Regex("[A-Za-z0-9_.-]{1,80}")))
        return context.getSharedPreferences("plugin-sdk:$pluginId", Context.MODE_PRIVATE).getString(key, null)
    }
    fun writeSetting(key: String, value: String?) {
        require(plugin.installedVersion(context) > 0)
        require(key.matches(Regex("[A-Za-z0-9_.-]{1,80}")) && (value == null || value.length <= 16000))
        context.getSharedPreferences("plugin-sdk:$pluginId", Context.MODE_PRIVATE).edit().putString(key, value).apply()
    }
}

internal fun requirePluginCapability(installed: Boolean, declared: Set<PluginCapability>, granted: Set<PluginCapability>, requested: PluginCapability) {
    if (!installed || requested !in declared || requested !in granted) throw SecurityException("尚未授权：${requested.label}")
}

internal fun requirePluginActivityOwnership(pluginId: String, activityId: String) {
    require(pluginId.matches(Regex("[A-Za-z0-9_.-]{1,80}")))
    require(activityId.startsWith("plugin:$pluginId:") && activityId.length in (pluginId.length + 9)..160) { "只能修改本插件创建的活动" }
}
