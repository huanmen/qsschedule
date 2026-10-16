package cn.qztc.timetable

import android.app.Application
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class QztcApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var renderResources: AppRenderResources
        private set

    override fun onCreate() {
        super.onCreate()
        renderResources = AppRenderResources(qztcDataStore, applicationScope)
        // Retire the removed helper without reading its saved credentials.
        val retired = getSharedPreferences("retired-plugin-cleanup", MODE_PRIVATE)
        if (!retired.getBoolean("campus-vpn", false)) {
            getSharedPreferences("campus_vpn_plugin", MODE_PRIVATE).edit().clear().apply()
            runCatching { cn.qztc.timetable.plugin.SecretStore("qztc_campus_vpn_v1").delete() }
            cn.qztc.timetable.plugin.PluginPins.set(this, "campus-vpn", false)
            retired.edit().putBoolean("campus-vpn", true).apply()
        }
        cn.qztc.timetable.widget.WidgetClock.register(this)
        WebView.enableSlowWholeDocumentDraw()
        applicationScope.launch {
            cn.qztc.timetable.education.HomeworkReminderScheduler.reschedule(this@QztcApplication)
            cn.qztc.timetable.reminder.ActivityReminderScheduler.reschedule(this@QztcApplication)
        }
    }
}
