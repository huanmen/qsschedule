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
        val fingerprint = settings.normalized().toString() + "|" + ZoneId.systemDefault().id
        val changed = prefs(context).getString("settings", null) != fingerprint
        if (changed) WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        enqueueNext(context, settings, LocalDateTime.now(), replace = changed)
        prefs(context).edit().putString("settings", fingerprint).apply()
    }

    internal fun enqueueNext(context: Context, settings: DailyAgendaSettings, now: LocalDateTime, replace: Boolean = false) {
        val at = nextDailyAgendaRun(settings, now) ?: return
        val zone = ZoneId.systemDefault()
        val delay = Duration.between(now.atZone(zone).toInstant(), at.atZone(zone).toInstant()).toMillis().coerceAtLeast(0)
        WorkManager.getInstance(context).enqueueUniqueWork("$TAG:${at.toLocalDate()}", if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<DailyAgendaWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("date" to at.toLocalDate().toString())).addTag(TAG).build())
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
        val items = items(context, LocalDate.now(), settings)
        return QztcNotifications.notifyDailyAgenda(context, "今日待办预览 · ${items.size} 项",
            dailyAgendaDetail(items, settings.maximumItems), settings.displayMode)
    }

    internal fun lastSent(context: Context) = prefs(context).getString("lastSent", null)
    internal fun markSent(context: Context, date: LocalDate) { prefs(context).edit().putString("lastSent", date.toString()).apply() }
}

class DailyAgendaWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val settings = AppPreferencesStore(applicationContext.qztcDataStore).preferences.first().dailyAgenda
        if (!settings.enabled) return Result.success()
        val today = LocalDate.now()
        val expected = inputData.getString("date")
        try {
            // A job delayed across midnight must not send yesterday's agenda.
            if (expected == today.toString() && today.dayOfWeek.value in settings.weekdays &&
                DailyAgendaScheduler.lastSent(applicationContext) != expected) {
                val items = DailyAgendaScheduler.items(applicationContext, today, settings)
                if (items.isNotEmpty() || settings.notifyEmpty) {
                    if (QztcNotifications.notifyDailyAgenda(applicationContext, "今日待办 · ${items.size} 项",
                            dailyAgendaDetail(items, settings.maximumItems), settings.displayMode)) {
                        DailyAgendaScheduler.markSent(applicationContext, today)
                    }
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return Result.retry()
        }
        DailyAgendaScheduler.enqueueNext(applicationContext,
            AppPreferencesStore(applicationContext.qztcDataStore).preferences.first().dailyAgenda,
            today.atTime(LocalTime.MAX))
        return Result.success()
    }
}
