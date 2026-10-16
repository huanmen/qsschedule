package cn.qztc.timetable.education

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import org.json.JSONObject
import java.time.*

internal data class HomeworkReminderOptions(val enabled: Boolean = true, val once: LocalDateTime? = null,
    val daily: LocalTime? = null, val beforeMinutes: Int? = 60)

internal fun homeworkReminderTimes(options: HomeworkReminderOptions, item: HomeworkItem, now: LocalDateTime): List<Pair<String, LocalDateTime>> {
    if (!options.enabled || !item.needsSubmission(now)) return emptyList()
    return buildList {
        options.once?.takeIf { it.isAfter(now) }?.let { add("once" to it) }
        options.daily?.let { time ->
            val today = now.toLocalDate().atTime(time)
            add("daily" to if (today.isAfter(now)) today else today.plusDays(1))
        }
        options.beforeMinutes?.let { minutes -> item.deadlineDate?.minusMinutes(minutes.toLong())?.takeIf { it.isAfter(now) }?.let { add("before" to it) } }
    }.filter { (_, time) -> item.deadlineDate?.let { !time.isAfter(it) } != false }
}

/** Choices are separate from refreshed school data; deleted rows cannot reappear on refresh. */
internal class HomeworkChoices(context: Context, val account: String) {
    private val prefs = context.getSharedPreferences("homework-choices", Context.MODE_PRIVATE)
    fun hidden(): Set<String> = prefs.getStringSet("$account:hidden", emptySet()).orEmpty().toSet()
    fun hide(id: String, hidden: Boolean) {
        val ids = hidden().toMutableSet(); if (hidden) ids.add(id) else ids.remove(id)
        prefs.edit().putStringSet("$account:hidden", ids).apply()
    }
    fun options(id: String): HomeworkReminderOptions = runCatching {
        val root = JSONObject(prefs.getString("$account:reminder:$id", "{}").orEmpty())
        HomeworkReminderOptions(root.optBoolean("enabled", true), root.optString("once").takeIf(String::isNotBlank)?.let(LocalDateTime::parse),
            root.optString("daily").takeIf(String::isNotBlank)?.let(LocalTime::parse),
            if (root.has("before")) root.optInt("before").takeIf { it in 1..525600 } else 60)
    }.getOrDefault(HomeworkReminderOptions())
    fun save(id: String, options: HomeworkReminderOptions) {
        prefs.edit().putString("$account:reminder:$id", JSONObject().put("enabled", options.enabled)
            .put("once", options.once?.toString().orEmpty()).put("daily", options.daily?.toString().orEmpty())
            .put("before", options.beforeMinutes ?: 0).toString()).apply()
    }
}

internal object HomeworkReminderScheduler {
    fun notificationId(account: String, id: String) = "homework:$account:$id".hashCode()
    private fun prefs(context: Context) = context.getSharedPreferences("homework-alarms", Context.MODE_PRIVATE)
    fun activate(context: Context, account: String) { prefs(context).edit().putString("account", account).apply(); reschedule(context) }
    fun exactAllowed(context: Context): Boolean = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    private fun pending(context: Context, key: String): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, HomeworkReminderReceiver::class.java).setAction("cn.qztc.timetable.HOMEWORK_REMINDER")
            .setData(Uri.parse("qztc-homework://alarm/" + Uri.encode(key))), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    @Synchronized fun reschedule(context: Context) {
        val prefs = prefs(context)
        val alarm = context.getSystemService(AlarmManager::class.java)
        prefs.getStringSet("scheduled", emptySet()).orEmpty().forEach { alarm.cancel(pending(context, it)) }
        val account = prefs.getString("account", null) ?: return
        val root = runCatching { JSONObject(context.getSharedPreferences("homework-results", 0).getString(account, "{}").orEmpty()) }.getOrNull()
        val records = runCatching { root?.let(HomeworkPayload::read).orEmpty() }.getOrDefault(emptyList())
        val choices = HomeworkChoices(context, account)
        val hidden = choices.hidden()
        records.filter { it.id in hidden || !it.needsSubmission() || !choices.options(it.id).enabled }.forEach {
            context.getSystemService(android.app.NotificationManager::class.java).cancel(notificationId(account, it.id))
        }
        val times = linkedMapOf<String, Long>()
        records.filterNot { it.id in hidden }.forEach { item ->
            homeworkReminderTimes(choices.options(item.id), item, LocalDateTime.now()).forEach { (_, time) ->
                // Coalesce simultaneous rules into a single notification.
                val key = "$account|${item.id}|$time"
                times[key] = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        }
        times.forEach { (key, time) ->
            try {
                if (exactAllowed(context)) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending(context, key))
                else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending(context, key))
            } catch (_: SecurityException) { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending(context, key)) }
        }
        prefs.edit().putStringSet("scheduled", times.keys).apply()
    }

    fun fire(context: Context, key: String) {
        val parts = key.split('|'); if (parts.size != 3) return
        val (account, id, date) = parts
        val prefs = prefs(context)
        if (prefs.getString("account", null) != account || prefs.getString("sent:$account:$id", null) == date ||
            key !in prefs.getStringSet("scheduled", emptySet()).orEmpty()) return
        val choices = HomeworkChoices(context, account)
        val root = runCatching { JSONObject(context.getSharedPreferences("homework-results", 0).getString(account, "{}").orEmpty()) }.getOrNull()
        val item = runCatching { root?.let(HomeworkPayload::read)?.firstOrNull { it.id == id } }.getOrNull()
        if (item != null && id !in choices.hidden() && choices.options(id).enabled && item.needsSubmission()) {
            if (cn.qztc.timetable.notifications.QztcNotifications.notifyHomeworkReminder(context,
                    "待提交：${item.title}", "${item.courseName}\n截止 ${item.deadline.ifBlank { "未公布" }}", notificationId(account, id)))
                prefs.edit().putString("sent:$account:$id", date).apply()
        }
        reschedule(context)
    }
}

class HomeworkReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "cn.qztc.timetable.HOMEWORK_REMINDER") intent.data?.lastPathSegment?.let { HomeworkReminderScheduler.fire(context, it) }
        else HomeworkReminderScheduler.reschedule(context)
    }
}
