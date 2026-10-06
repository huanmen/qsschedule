package cn.qztc.timetable.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat

/** Minute ticks while awake; never wake the phone or hold a foreground service for decoration. */
internal object WidgetClock {
    fun register(context: Context) {
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) cancel(context)
                else TodayWidgetProvider.requestUpdate(context)
            }
        }, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun pending(context: Context) = PendingIntent.getBroadcast(context, 9091,
        Intent(context, TodayWidgetProvider::class.java).setAction(TodayWidgetProvider.ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(context: Context) {
        val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
        if (ids.isEmpty() || !context.getSystemService(PowerManager::class.java).isInteractive) return cancel(context)
        val nextMinute = (System.currentTimeMillis() / 60_000 + 1) * 60_000
        // A non-wakeup fallback if the app process is reclaimed. Android may defer this in power-saving mode.
        context.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC, nextMinute, pending(context))
    }

    fun cancel(context: Context) { context.getSystemService(AlarmManager::class.java).cancel(pending(context)) }
}
