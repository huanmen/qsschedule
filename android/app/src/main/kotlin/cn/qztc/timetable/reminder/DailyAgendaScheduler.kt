package cn.qztc.timetable.reminder

import android.content.Context
import androidx.work.*
import cn.qztc.timetable.AppPreferencesStore
import cn.qztc.timetable.qztcDataStore
import cn.qztc.timetable.core.data.db.TimetableDatabase
import cn.qztc.timetable.core.data.repository.TimetableRepository
import cn.qztc.timetable.core.domain.model.*
import cn.qztc.timetable.education.AcademicAgendaStore
import cn.qztc.timetable.feature.timetable.ImportedSchedule
import cn.qztc.timetable.notifications.QztcNotifications
import kotlinx.coroutines.flow.first
import java.time.*
import java.util.concurrent.TimeUnit

object DailyAgendaScheduler {
    private const val TAG = "daily-agenda"
    private fun prefs(context: Context) = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)

    @Synchronized
    fun reschedule(context: Context, settings: DailyAgendaSettings) {
        val sources = cn.qztc.timetable.plugin.PluginRegistry.plugins.filter {
            it.installedVersion(context) > 0 && it.descriptor.reminderKinds.isNotEmpty()
        }
        val fingerprint = settings.normalized().toString() + "|" + ZoneId.systemDefault().id + sources.map { it.descriptor.id }
        val changed = prefs(context).getString("settings", null) != fingerprint
        if (changed) WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        sources.forEach { enqueueNext(context, settings.pluginAgenda(it.descriptor.id), LocalDateTime.now(), replace = changed, pluginId = it.descriptor.id) }
        prefs(context).edit().putString("settings", fingerprint).apply()
    }

    internal fun enqueueNext(context: Context, settings: DailyAgendaSettings, now: LocalDateTime, replace: Boolean = false, pluginId: String = "academic-records") {
        val at = nextDailyAgendaRun(settings, now) ?: return
        val zone = ZoneId.systemDefault()
        val delay = Duration.between(now.atZone(zone).toInstant(), at.atZone(zone).toInstant()).toMillis().coerceAtLeast(0)
        WorkManager.getInstance(context).enqueueUniqueWork("$TAG:$pluginId:${at.toLocalDate()}", if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<DailyAgendaWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("date" to at.toLocalDate().toString(), "pluginId" to pluginId)).addTag(TAG).addTag("$TAG:$pluginId").build())
    }

    internal suspend fun items(context: Context, date: LocalDate, settings: DailyAgendaSettings): List<DailyAgendaItem> {
        val database = TimetableDatabase.build(context)
        try {
            val repository = TimetableRepository(database)
            val schedules = repository.semesters().map { semester -> ImportedSchedule(semester,
                repository.periodScheme(semester.id)?.periods ?: PeriodSchemeFactory.forSemester(semester.id).periods,
                if (settings.includeCourses) repository.state(semester.id) else cn.qztc.timetable.core.domain.schedule.TimetableState.EMPTY,
                repository.activities(semester.id), if (settings.includeCourses) repository.academicDays(semester.id) else emptyList()) }
            val options = if (cn.qztc.timetable.plugin.AcademicRecordsPlugin.installedVersion(context) > 0) settings
                else settings.copy(includeCompetitions = false, includeExams = false)
            return buildDailyAgenda(date, options, schedules, AcademicAgendaStore(context).read(), repository.activities("calendar"))
        } finally { database.close() }
    }

    suspend fun preview(context: Context, settings: DailyAgendaSettings): Boolean {
        val options = settings.pluginAgenda("academic-records")
        val items = pluginItems(context, LocalDate.now(), "academic-records", settings)
        return QztcNotifications.notifyDailyAgenda(context, "其他事项预览 · ${items.size} 项",
            dailyAgendaDetail(items, items.size.coerceAtLeast(1)), options.displayMode)
    }

    internal suspend fun pluginItems(context: Context, date: LocalDate, id: String, settings: DailyAgendaSettings): List<DailyAgendaItem> {
        val options = settings.forPlugin(id)
        val list = items(context, date, settings.pluginAgenda(id)).filter {
            it.kind in options.kinds && (id == "academic-records" && it.kind in setOf("competition", "exam") || it.id.startsWith("plugin:$id:"))
        }.toMutableList()
        if (id == "academic-records" && "updates" in options.kinds) {
            cn.qztc.timetable.education.CompetitionUpdates.pending(context).forEach {
                list += DailyAgendaItem("update:$it", it, null, "关注赛事的时间、地点或资料已更新", "updates")
            }
        }
        return list
    }

    internal fun lastSent(context: Context, id: String) = prefs(context).getString("lastSent:$id", null)
    internal fun markSent(context: Context, date: LocalDate, id: String) { prefs(context).edit().putString("lastSent:$id", date.toString()).apply() }
}

class DailyAgendaWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("pluginId") ?: return Result.success()
        val plugin = cn.qztc.timetable.plugin.PluginRegistry.plugins.firstOrNull { it.descriptor.id == id }
            ?.takeIf { it.installedVersion(applicationContext) > 0 } ?: return Result.success()
        val allSettings = AppPreferencesStore(applicationContext.qztcDataStore).preferences.first().dailyAgenda
        val settings = allSettings.pluginAgenda(id)
        if (!settings.enabled) return Result.success()
        val today = LocalDate.now()
        val expected = inputData.getString("date")
        try {
            // A job delayed across midnight must not send yesterday's agenda.
            if (expected == today.toString() && today.dayOfWeek.value in settings.weekdays &&
                DailyAgendaScheduler.lastSent(applicationContext, id) != expected) {
                val items = DailyAgendaScheduler.pluginItems(applicationContext, today, id, allSettings)
                if (items.isNotEmpty()) {
                    if (QztcNotifications.notifyPlugin(applicationContext, id, plugin.descriptor.name, "${plugin.descriptor.name} · ${items.size} 项",
                            dailyAgendaDetail(items, items.size), settings.displayMode)) {
                        DailyAgendaScheduler.markSent(applicationContext, today, id)
                        if (id == "academic-records") cn.qztc.timetable.education.CompetitionUpdates.consume(applicationContext,
                            items.filter { it.kind == "updates" }.map { it.title }.toSet())
                    }
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return Result.retry()
        }
        DailyAgendaScheduler.enqueueNext(applicationContext,
            AppPreferencesStore(applicationContext.qztcDataStore).preferences.first().dailyAgenda.pluginAgenda(id),
            today.atTime(LocalTime.MAX), pluginId = id)
        return Result.success()
    }
}
