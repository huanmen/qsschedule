package cn.qztc.timetable.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cn.qztc.timetable.MainActivity
import cn.qztc.timetable.core.domain.model.ReminderDisplayMode
import cn.qztc.timetable.qnu.EasyConnectBridge
import cn.qztc.timetable.R
import kotlinx.coroutines.delay

/** System notifications stay optional: a denied permission never blocks timetable data flows. */
object QztcNotifications {
    private const val PREVIEW_ID = 903010
    data class ReminderProbe(val issue: String? = null, val posted: Boolean = false)
    private const val REMINDER_CHANNEL = "course_reminders"
    private const val REMINDER_POPUP_CHANNEL = "course_reminders_popup"
    private const val CHANGE_CHANNEL = "schedule_changes"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(REMINDER_CHANNEL, "上课提醒", NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(REMINDER_POPUP_CHANNEL, "上课提醒（屏幕弹窗）", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "在屏幕顶部弹出上课提醒"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANGE_CHANNEL, "课表变化", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun reminderSettingsIntent(context: Context, mode: ReminderDisplayMode): Intent {
        ensureChannels(context)
        if (!canPost(context)) return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, if (mode == ReminderDisplayMode.SCREEN_POPUP) REMINDER_POPUP_CHANNEL else REMINDER_CHANNEL)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    suspend fun testCourseReminder(context: Context, mode: ReminderDisplayMode): ReminderProbe {
        return testReminder(context,mode) {notifyCourseReminder(context,"测试提醒",
            if(mode==ReminderDisplayMode.SCREEN_POPUP)"请确认屏幕顶部是否出现弹窗。"else"这是一条通知栏测试提醒。",PREVIEW_ID,mode)}
    }

    suspend fun testActivityReminder(context:Context,mode:ReminderDisplayMode,title:String,detail:String):ReminderProbe =
        testReminder(context,mode) {notifyActivityReminder(context,title,detail,java.time.LocalDate.now().toString(),PREVIEW_ID,mode)}

    private suspend fun testReminder(context:Context,mode:ReminderDisplayMode,send:()->Boolean):ReminderProbe {
        ensureChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val popup = mode == ReminderDisplayMode.SCREEN_POPUP
        val channel = manager.getNotificationChannel(if (popup) REMINDER_POPUP_CHANNEL else REMINDER_CHANNEL)
        val groupBlocked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            channel?.group?.let { manager.getNotificationChannelGroup(it)?.isBlocked } == true
        val dndMayBlock = manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN && channel?.canBypassDnd() != true
        val issue = reminderNotificationIssue(canPost(context), channel?.importance ?: 0, popup, groupBlocked, dndMayBlock)
        if (issue != null) return ReminderProbe(issue = issue)
        manager.cancel(PREVIEW_ID)
        if (!send()) {
            return ReminderProbe(issue = "系统未允许发送提醒，请检查通知权限。")
        }
        repeat(5) {
            delay(200)
            if (manager.activeNotifications.any { it.id == PREVIEW_ID }) return ReminderProbe(posted = true)
        }
        return ReminderProbe(issue = "没有检测到测试通知，请检查系统通知设置。")
    }

    fun notifyCourseReminder(
        context: Context,
        title: String,
        detail: String,
        notificationId: Int,
        displayMode: ReminderDisplayMode = ReminderDisplayMode.SCREEN_POPUP,
        courseDate: String? = null,
    ): Boolean {
        ensureChannels(context)
        val popup = displayMode == ReminderDisplayMode.SCREEN_POPUP
        val channelId = if (popup) REMINDER_POPUP_CHANNEL else REMINDER_CHANNEL
        return post(context, notificationId,
            builder(context, channelId, Intent(context, MainActivity::class.java).apply {
                if (courseDate == null || courseDate == java.time.LocalDate.now().toString()) {
                    action = MainActivity.EXTRA_OPEN_TODAY
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(MainActivity.EXTRA_OPEN_TODAY, true)
                }
            })
                .setContentTitle("即将上课：$title")
                .setContentText(detail)
                .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(if (popup) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .setDefaults(if (popup) NotificationCompat.DEFAULT_ALL else NotificationCompat.DEFAULT_SOUND)
                .build(),
        )
    }

    fun notifyActivityReminder(context: Context, title: String, detail: String, date: String, notificationId: Int,
        mode:ReminderDisplayMode=ReminderDisplayMode.NOTIFICATION_BAR): Boolean {
        ensureChannels(context)
        val intent = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.EXTRA_OPEN_CALENDAR)
            .setData(android.net.Uri.parse("qztc://calendar/$date"))
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra(MainActivity.EXTRA_OPEN_CALENDAR, date)
        val popup=mode==ReminderDisplayMode.SCREEN_POPUP
        return post(context, notificationId, builder(context, if(popup)REMINDER_POPUP_CHANNEL else REMINDER_CHANNEL, intent)
            .setPriority(if(popup)NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentTitle(title).setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail)).build())
    }

    fun notifyScheduleChange(context: Context, count: Int, notificationId: Int) {
        ensureChannels(context)
        val text = "检测到 $count 项课表变化，原课表已保留。"
        post(context, notificationId,
            builder(context, CHANGE_CHANNEL)
                .setContentTitle("发现课表变化")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .build(),
        )
    }

    fun notifyDailyAgenda(context: Context, title: String, detail: String, mode: ReminderDisplayMode): Boolean {
        ensureChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val popup = mode == ReminderDisplayMode.SCREEN_POPUP
        val channel = if (popup) "daily-agenda-popup" else "daily-agenda"
        manager.createNotificationChannel(NotificationChannel(channel, if (popup) "每日待办（弹窗）" else "每日待办",
            if (popup) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT))
        return post(context, 73001, builder(context, channel)
            .setContentTitle(title).setContentText(detail.lineSequence().firstOrNull())
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (popup) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT).build())
    }

    fun notifyHomeworkReminder(context: Context, title: String, detail: String, notificationId: Int): Boolean {
        val channel = "homework-reminders"
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channel, "作业提醒", NotificationManager.IMPORTANCE_HIGH))
        return post(context, notificationId, builder(context, channel, Intent(context, MainActivity::class.java)
            .setAction(MainActivity.EXTRA_OPEN_HOMEWORK)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra(MainActivity.EXTRA_OPEN_HOMEWORK, true))
            .setContentTitle(title).setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH).build())
    }

    fun notifyPlugin(context: Context, pluginId: String, name: String, title: String, detail: String, mode: ReminderDisplayMode): Boolean {
        val popup = mode == ReminderDisplayMode.SCREEN_POPUP
        val channel = "plugin:$pluginId:${if (popup) "popup" else "normal"}"
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channel, "$name · 通知", if (popup) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT))
        return post(context, "plugin:$pluginId:$title".hashCode(), builder(context, channel)
            .setContentTitle(title).setContentText(detail.lineSequence().firstOrNull())
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail)).build())
    }

    fun notifySchoolVpnRequired(context: Context, notificationId: Int) {
        val text = "学校内网不可达，点击后打开 EasyConnect，连接成功后返回 App。"
        val intent = if (EasyConnectBridge.isInstalled(context)) {
            EasyConnectBridge.connectionIntent(context)
        } else {
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        post(context, notificationId,
            builder(context, CHANGE_CHANNEL, intent)
                .setContentTitle("需要连接学校 VPN")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .build(),
        )
    }

    /** Permission can be revoked between the check and notify; keep notifications optional. */
    private fun post(context: Context, notificationId: Int, notification: android.app.Notification): Boolean {
        if (!canPost(context)) return false
        return try {
            NotificationManagerCompat.from(context).notify(
                notificationId,
                notification,
            )
            true
        } catch (_: SecurityException) {
            // A permission change can race this call. The in-app state remains authoritative.
            false
        }
    }

    private fun builder(
        context: Context,
        channelId: String,
        contentIntent: Intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
    ): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    contentIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
}

/** A posted notification cannot prove that the OEM displayed a heads-up banner. */
internal fun reminderNotificationIssue(
    allowed: Boolean, importance: Int, popup: Boolean, groupBlocked: Boolean, dndMayBlock: Boolean,
): String? = when {
    !allowed -> "应用通知权限未开启，请在系统设置中允许通知。"
    groupBlocked || importance == NotificationManager.IMPORTANCE_NONE -> "这类提醒已被系统关闭，请开启对应的通知类别。"
    popup && importance < NotificationManager.IMPORTANCE_HIGH -> "屏幕弹窗未达到所需通知级别，请开启悬浮通知或横幅。"
    dndMayBlock -> "当前勿扰模式可能拦截提醒，请检查系统通知与勿扰设置。"
    else -> null
}
