package cn.qztc.timetable.reminder

import android.content.Context
import cn.qztc.timetable.core.domain.model.ReminderDisplayMode
import cn.qztc.timetable.core.ui.theme.ReminderPreviewState
import cn.qztc.timetable.notifications.QztcNotifications

/** Uses the same channels and delivery diagnostics as course reminder previews. */
suspend fun activityReminderPreview(context:Context,popup:Boolean,title:String,detail:String):ReminderPreviewState {
    val mode=if(popup)ReminderDisplayMode.SCREEN_POPUP else ReminderDisplayMode.NOTIFICATION_BAR
    val result=QztcNotifications.testActivityReminder(context,mode,title,detail)
    return ReminderPreviewState(message=result.issue?:if(popup)"系统已接收测试通知，请确认顶部是否出现弹窗。"else"测试通知已发出，请在通知栏确认。",
        needsSystemSettings=result.issue!=null,needsConfirmation=result.posted)
}

fun openActivityReminderSettings(context:Context,popup:Boolean) {
    runCatching {context.startActivity(QztcNotifications.reminderSettingsIntent(context,
        if(popup)ReminderDisplayMode.SCREEN_POPUP else ReminderDisplayMode.NOTIFICATION_BAR))}
}
