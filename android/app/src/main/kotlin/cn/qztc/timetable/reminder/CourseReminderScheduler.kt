package cn.qztc.timetable.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.CoroutineWorker
import androidx.work.workDataOf
import cn.qztc.timetable.feature.timetable.ImportedSchedule
import cn.qztc.timetable.core.domain.model.OccurrenceState
import cn.qztc.timetable.core.domain.schedule.OccurrenceExpander
import cn.qztc.timetable.core.domain.calendar.AcademicCalendar
import cn.qztc.timetable.notifications.QztcNotifications
import cn.qztc.timetable.core.domain.model.ReminderDisplayMode
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Uses short-lived one-time jobs rather than exact alarms. Android may delay a reminder under
 * battery restrictions, so the app deliberately does not promise minute-perfect delivery.
 */
object CourseReminderScheduler {
    private const val TAG = "course-reminder"
    private const val KEY_TITLE = "title"
    private const val KEY_DETAIL = "detail"
    private const val KEY_NOTIFICATION_ID = "notificationId"
    private const val PREFS_NAME = "course-reminder-scheduler"
    private const val KEY_FINGERPRINT = "fingerprint"

    private data class ReminderSpec(
        val stableId: String,
        val reminderAt: LocalDateTime,
        val title: String,
        val detail: String,
        val notificationId: Int,
        val displayMode: ReminderDisplayMode,
        val courseDate: String,
    )

    @Synchronized
    fun reschedule(
        context: Context,
        schedule: ImportedSchedule?,
        enabled: Boolean,
        minutesBefore: Int,
        displayMode: ReminderDisplayMode = ReminderDisplayMode.SCREEN_POPUP,
        courseOverrides: Map<String, Int> = emptyMap(),
        courseMessages: Map<String, String> = emptyMap(),
        now: LocalDateTime = LocalDateTime.now(),
        remindOnHolidays: Boolean = false,
    ) {
        val workManager = WorkManager.getInstance(context)
        val fingerprint = fingerprint(schedule, enabled, minutesBefore, displayMode, courseOverrides, courseMessages, now, remindOnHolidays)
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (preferences.getString(KEY_FINGERPRINT, null) == fingerprint) return

        workManager.cancelAllWorkByTag(TAG)
        if (!enabled || schedule == null) {
            preferences.edit().putString(KEY_FINGERPRINT, fingerprint).apply()
            return
        }

        val specs = reminderSpecs(schedule, minutesBefore, displayMode, courseOverrides, courseMessages, now, remindOnHolidays)

        specs.forEach { spec ->
            val delay = Duration.between(now, spec.reminderAt).toMillis().coerceAtLeast(0)
            val request = OneTimeWorkRequestBuilder<CourseReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        KEY_TITLE to spec.title,
                        KEY_DETAIL to spec.detail,
                        KEY_NOTIFICATION_ID to spec.notificationId,
                        "displayMode" to spec.displayMode.name,
                        "courseDate" to spec.courseDate,
                    ),
                )
                .addTag(TAG)
                .build()
            workManager.enqueueUniqueWork("$TAG:${spec.stableId}", ExistingWorkPolicy.REPLACE, request)
        }
        preferences.edit().putString(KEY_FINGERPRINT, fingerprint).apply()
    }

    private fun reminderSpecs(
        schedule: ImportedSchedule,
        minutesBefore: Int,
        displayMode: ReminderDisplayMode,
        courseOverrides: Map<String, Int>,
        courseMessages: Map<String, String>,
        now: LocalDateTime,
        remindOnHolidays: Boolean,
    ): List<ReminderSpec> {
        val courses = schedule.state.courses.associateBy { it.id }
        val periods = schedule.periods.associateBy { it.no }
        val horizon = now.toLocalDate().plusDays(7)
        val calendar = AcademicCalendar(schedule.academicDays)
        return (1..schedule.semester.totalWeeks).flatMap {
            OccurrenceExpander.expandVisibleWeek(schedule.semester, schedule.state, it, calendar, includeHolidayCourses = remindOnHolidays)
        }
            .asSequence()
            .filter { it.state != OccurrenceState.CANCELLED && !it.date.isAfter(horizon) }
            .mapNotNull { occurrence ->
                val period = periods[occurrence.periodStart] ?: return@mapNotNull null
                val effectiveMinutes = courseOverrides[occurrence.courseId] ?: minutesBefore
                if (effectiveMinutes <= 0) return@mapNotNull null
                val reminderAt = LocalDateTime.of(occurrence.date, period.startTime)
                    .minusMinutes(effectiveMinutes.toLong())
                if (!reminderAt.isAfter(now)) return@mapNotNull null
                val course = courses[occurrence.courseId] ?: return@mapNotNull null
                val detail = formatCourseReminderDetail(
                    date = occurrence.date,
                    startTime = period.startTime,
                    room = occurrence.room,
                    teacher = occurrence.teacher,
                    note = course.note,
                    customMessage = courseMessages[course.id],
                    today = now.toLocalDate(),
                )
                val stableId = "${occurrence.courseId}:${occurrence.date}:${occurrence.periodStart}"
                ReminderSpec(
                    stableId = stableId,
                    reminderAt = reminderAt,
                    title = course.name,
                    detail = detail,
                    notificationId = stableId.hashCode(),
                    displayMode = displayMode,
                    courseDate = occurrence.date.toString(),
                )
            }
            .toList()
    }

    internal fun fingerprint(
        schedule: ImportedSchedule?,
        enabled: Boolean,
        minutesBefore: Int,
        displayMode: ReminderDisplayMode,
        courseOverrides: Map<String, Int>,
        courseMessages: Map<String, String>,
        now: LocalDateTime,
        remindOnHolidays: Boolean = false,
    ): String {
        if (!enabled) return "disabled"
        if (schedule == null) return "no-schedule"

        val horizon = now.toLocalDate().plusDays(7)
        val specs = reminderSpecs(schedule, minutesBefore, displayMode, courseOverrides, courseMessages, now, remindOnHolidays)
            .asSequence()
            .map { spec -> "${spec.stableId}|${spec.reminderAt}|${spec.title}|${spec.detail}" }
            .sorted()
            .joinToString("\n")
        return "route-v3|${ZoneId.systemDefault().id}|$horizon|${displayMode.name}|$remindOnHolidays|$specs"
    }

    internal fun reminderDelayMillis(reminderAt: LocalDateTime, now: LocalDateTime): Long =
        Duration.between(now, reminderAt).toMillis().coerceAtLeast(0)

    internal fun deviceZoneId(): ZoneId = ZoneId.systemDefault()
}

internal fun formatCourseReminderDetail(
    date: LocalDate,
    startTime: LocalTime,
    room: String?,
    teacher: String?,
    note: String?,
    customMessage: String? = null,
    today: LocalDate = LocalDate.now(),
): String = buildString {
    customMessage?.trim()?.takeIf { it.isNotEmpty() }?.let { append("提醒：${it.take(240)}\n") }
    if(date != today) append("${date.monthValue}月${date.dayOfMonth}日 ")
    append(startTime)
    room?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
    teacher?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
    note?.trim()?.takeIf { it.isNotEmpty() }?.let { append("\n备注：$it") }
}

class CourseReminderWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val title = inputData.getString("title")?.takeIf { it.isNotBlank() } ?: return Result.success()
        val detail = inputData.getString("detail").orEmpty().let {text ->
            if(inputData.getString("courseDate")==LocalDate.now().toString())
                text.replace(Regex("(?m)^${LocalDate.now().monthValue}月${LocalDate.now().dayOfMonth}日 "),"")else text
        }
        QztcNotifications.notifyCourseReminder(
            applicationContext,
            title,
            detail,
            inputData.getInt("notificationId", title.hashCode()),
            runCatching { ReminderDisplayMode.valueOf(inputData.getString("displayMode").orEmpty()) }
                .getOrDefault(ReminderDisplayMode.NOTIFICATION_BAR),
            inputData.getString("courseDate"),
        )
        return Result.success()
    }
}
