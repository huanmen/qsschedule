package cn.qztc.timetable.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import cn.qztc.timetable.MainActivity
import cn.qztc.timetable.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/** Foreground data transfer continues while the App or update dialog is in the background. */
class ResourceDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var running = false
    private fun notification(state: ResourceUpdateState): Notification {
        val open = PendingIntent.getActivity(this, 904101,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_UPDATE, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val total = state.manifest?.sizeBytes ?: 0
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(if (state.stage == "applying") "正在校验并应用更新资源" else "正在下载更新资源")
            .setContentText("${formatResourceBytes(state.bytes)} / ${formatResourceBytes(total)}")
            .setProgress(1000, if (total > 0) (state.bytes * 1000 / total).toInt() else 0, total == 0L)
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS).build()
    }
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "更新下载", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(ID, notification(ResourceUpdateSession.state.value))
        if (!running) {
            running = true
            scope.launch {
                ResourceUpdateSession.state.collectLatest { state ->
                    if (state.stage in listOf("downloading", "applying")) {
                        getSystemService(NotificationManager::class.java).notify(ID, notification(state))
                        delay(250)
                    }
                }
            }
            ResourceUpdateSession.download(applicationContext, onFinished = { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() })
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object { private const val CHANNEL = "resource_updates"; private const val ID = 904100 }
}
