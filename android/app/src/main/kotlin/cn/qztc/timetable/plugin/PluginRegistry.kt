package cn.qztc.timetable.plugin

import android.content.Context
import cn.qztc.timetable.adskip.AdSkipRuntime
import cn.qztc.timetable.adskip.AdSkipStore
import cn.qztc.timetable.adskip.PluginDistribution
import cn.qztc.timetable.review.ReviewStore

enum class PluginDependency(val label: String) {
    ACCESSIBILITY("无障碍服务")
}

data class PluginDescriptor(
    val id: String,
    val name: String,
    val summary: String,
    val version: Int,
    val dependencies: Set<PluginDependency> = emptySet(),
    val capabilities: Set<PluginCapability> = emptySet(),
    val reminderKinds: Map<String, String> = emptyMap(),
)

/** Native plugins provide their own screen but share the same market lifecycle. */
interface PluginController {
    val descriptor: PluginDescriptor
    fun installedVersion(context: Context): Int
    suspend fun availableVersion(): Int = descriptor.version
    suspend fun install(context: Context): Result<Unit>
    fun uninstall(context: Context)
}

object PluginRegistry {
    val plugins: List<PluginController> = listOf(AdSkip, AcademicRecordsPlugin)

    fun missingDependencies(context: Context, descriptor: PluginDescriptor,
        accessibilityConnected: Boolean = AdSkipRuntime.connected.value): List<PluginDependency> =
        descriptor.dependencies.filter { dependency ->
            when (dependency) {
                PluginDependency.ACCESSIBILITY -> !accessibilityConnected
            }
        }

    private object AdSkip : PluginController {
        override val descriptor = PluginDescriptor("adskip", "广告辅助", "自动跳过广告", 2,
            setOf(PluginDependency.ACCESSIBILITY))
        override fun installedVersion(context: Context) = if (AdSkipStore.installed(context))
            AdSkipStore.prefs(context).getInt("plugin_version", 1) else 0
        override suspend fun availableVersion() = PluginDistribution.available()?.version ?: descriptor.version
        override suspend fun install(context: Context) = PluginDistribution.install(context, PluginDistribution.available())
        override fun uninstall(context: Context) = AdSkipStore.removeDownloadedPlugin(context)
    }

    private object Review : PluginController {
        override val descriptor = PluginDescriptor("review", "AI 好评助手", "自然表达 · 字数控制", 1)
        override fun installedVersion(context: Context) = if (ReviewStore(context).installed()) 1 else 0
        override suspend fun install(context: Context): Result<Unit> = runCatching { ReviewStore(context).install() }
        override fun uninstall(context: Context) = ReviewStore(context).delete()
    }

    private abstract class BuiltinFeature(
        override val descriptor: PluginDescriptor,
        private val key: String,
    ) : PluginController {
        override fun installedVersion(context: Context) = context.getSharedPreferences("builtin_plugins", Context.MODE_PRIVATE)
            .getInt(key, 0)
        override suspend fun availableVersion() = cn.qztc.timetable.adskip.PluginDistribution.nativeAvailable(descriptor.id) ?: descriptor.version
        override suspend fun install(context: Context): Result<Unit> =
            cn.qztc.timetable.adskip.PluginDistribution.installNative(context, descriptor.id, descriptor.version)
        override fun uninstall(context: Context) {
            context.getSharedPreferences("builtin_plugins", Context.MODE_PRIVATE).edit().remove(key).apply()
        }
    }

    private object ScheduleQa : BuiltinFeature(
        PluginDescriptor("schedule-qa", "AI 课表问答", "按课程、星期、节次和地点快速查询", version = 1), "schedule-qa")

    private object ExamSchedule : BuiltinFeature(
        PluginDescriptor("exam-schedule", "考试日程 & 倒计时", "记录考试安排并查看剩余时间", version = 1), "exam-schedule")
}
