package cn.qztc.timetable.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.qztc.timetable.core.domain.model.ScheduleActivity
import cn.qztc.timetable.notifications.QztcNotifications
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal data class ActivityReminderMoment(val activity:ScheduleActivity,val at:LocalDateTime,val boundary:String,
    val occurrenceDate:LocalDate=at.toLocalDate())

internal fun activityReminderMoments(activities:List<ScheduleActivity>,today:LocalDate=LocalDate.now()):List<ActivityReminderMoment> = activities
    .filterNot {it.hiddenFromSchedule}.flatMap {activity ->
        val memo=activity.memoTime?.takeIf {it.remind} ?: return@flatMap emptyList()
        (if(memo.repeat!=null)memo.slots(today.minusDays(366),maxOf(today,memo.date?:today).plusDays(3650))else memo.slots()).flatMap {slot -> buildList {
            val options=memo.reminder
            if(options!=null) {
                val date=slot.date!!.minusDays(options.daysBefore.toLong())
                if(slot.date.toString() !in options.excludedOccurrenceDates&&slot.date!!.dayOfWeek.value !in options.excludedOccurrenceWeekdays&&date.dayOfWeek.value !in options.excludedWeekdays&&date.toString() !in options.excludedDates)
                    add(ActivityReminderMoment(activity,date.atTime(options.minute/60,options.minute%60),"",slot.date!!))
                return@buildList
            }
            fun at(date:java.time.LocalDate,minute:Int?)=date.atTime(minute?.let {LocalTime.of(it/60,it%60)}?:LocalTime.of(9,0))
            add(ActivityReminderMoment(activity,at(slot.date!!,slot.minute),if(slot.endDate!=null)"开始" else ""))
            slot.endDate?.let {add(ActivityReminderMoment(activity,at(it,slot.endMinute),"结束"))}
        } }
    }.distinctBy {it.activity.id to it.at}.sortedBy {it.at}

/** Only the nearest global alarm is registered, avoiding hundreds of simultaneous week alarms. */
object ActivityReminderScheduler {
    private val json=Json {ignoreUnknownKeys=true}
    private const val ACTION="cn.qztc.timetable.ACTIVITY_REMINDER"
    private fun prefs(context:Context)=context.getSharedPreferences("activity-reminders",Context.MODE_PRIVATE)
    private fun pending(context:Context)=PendingIntent.getBroadcast(context,0,
        Intent(context,ActivityReminderReceiver::class.java).setAction(ACTION),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun read(context:Context):List<ScheduleActivity> = runCatching {
        json.decodeFromString<List<ScheduleActivity>>(prefs(context).getString("activities","[]")!!)
    }.getOrDefault(emptyList())

    @Synchronized fun update(context:Context,activities:List<ScheduleActivity>) {
        val encoded=json.encodeToString(activities.filter {it.memoTime?.remind==true})
        if(prefs(context).getString("activities",null)!=encoded) {
            prefs(context).edit().putString("activities",encoded).apply()
            reschedule(context)
        }
    }
    @Synchronized fun reschedule(context:Context) {
        val alarm=context.getSystemService(AlarmManager::class.java)
        alarm.cancel(pending(context))
        val next=activityReminderMoments(read(context)).firstOrNull {it.at>LocalDateTime.now()}
        prefs(context).edit().putString("next",next?.at?.toString()).apply()
        if(next!=null) {
            val at=next.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            try {
                if(cn.qztc.timetable.education.HomeworkReminderScheduler.exactAllowed(context))
                    alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pending(context))
                else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pending(context))
            } catch (_:SecurityException) {alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pending(context))}
        }
    }
    @Synchronized fun fire(context:Context) {
        val prefs=prefs(context)
        val next=prefs.getString("next",null)?.let {runCatching {LocalDateTime.parse(it)}.getOrNull()}?:return
        if(next>LocalDateTime.now().plusSeconds(1))return
        // Claim before posting so a repeated delivery cannot produce duplicate reminders.
        prefs.edit().remove("next").commit()
        activityReminderMoments(read(context)).filter {it.at==next}.forEach {moment ->
            QztcNotifications.notifyActivityReminder(context,moment.activity.name,
                listOf(moment.activity.memoTime?.location.orEmpty(),moment.activity.note).filter(String::isNotBlank).joinToString(" · "),
                moment.occurrenceDate.toString(),("activity-reminder:${moment.activity.id}"+moment.at).hashCode(),
                moment.activity.memoTime?.reminder?.displayMode?:cn.qztc.timetable.core.domain.model.ReminderDisplayMode.NOTIFICATION_BAR)
        }
        reschedule(context)
    }
    internal fun isReminder(intent:Intent)=intent.action==ACTION
}

class ActivityReminderReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(ActivityReminderScheduler.isReminder(intent))ActivityReminderScheduler.fire(context)
        else ActivityReminderScheduler.reschedule(context)
    }
}
