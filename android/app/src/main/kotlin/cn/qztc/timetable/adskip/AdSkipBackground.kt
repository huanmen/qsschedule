package cn.qztc.timetable.adskip

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import cn.qztc.timetable.R

/** Promotes the system-bound accessibility service; never starts another watchdog process. */
internal class AdSkipBackground(private val service: Service) {
    private var running = false

    fun update() {
        if (!AdSkipRuntime.connected.value || !AdSkipStore.enabled(service) || !AdSkipStore.foregroundEnabled(service)) {
            stop()
            return
        }
        if (running) return
        runCatching {
            val manager = service.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "广告辅助后台运行", NotificationManager.IMPORTANCE_LOW).apply {
                description = "显示广告辅助的持续运行状态，可在插件设置中关闭"
                setShowBadge(false)
            })
            val open = PendingIntent.getActivity(service, 8121, adSkipManagementIntent(service),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(service, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("广告辅助正在运行")
                .setContentText("仅处理已开启的应用与规则 · 点此管理或暂停")
                .setContentIntent(open)
                .setOngoing(true).setSilent(true).setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build()
            if (Build.VERSION.SDK_INT >= 34) service.startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else service.startForeground(ID, notification)
            running = true
            AdSkipRuntime.setBackground("前台服务运行中")
        }.onFailure {
            // A vendor restriction must not crash or disable the accessibility service.
            AdSkipRuntime.setBackground("系统未允许前台运行，请打开本页重试")
        }
    }

    fun stop() {
        service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        running = false
        AdSkipRuntime.setBackground("未启动")
    }

    private companion object {
        const val CHANNEL = "ad_skip_background"
        const val ID = 8121
    }
}

internal fun adSkipManagementIntent(context:android.content.Context)=Intent(context,cn.qztc.timetable.MainActivity::class.java)
    .setAction(cn.qztc.timetable.MainActivity.EXTRA_OPEN_AD_SKIP)
    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    .putExtra(cn.qztc.timetable.MainActivity.EXTRA_OPEN_AD_SKIP,true)
