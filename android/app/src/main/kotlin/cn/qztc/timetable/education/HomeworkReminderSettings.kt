package cn.qztc.timetable.education

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.domain.model.ReminderDisplayMode
import cn.qztc.timetable.core.ui.theme.*
import cn.qztc.timetable.notifications.QztcNotifications
import java.time.LocalTime

internal data class HomeworkReminderDefaults(val enabled: Boolean = true, val beforeMinutes: Int = 60,
    val daily: LocalTime? = null, val displayMode: ReminderDisplayMode = ReminderDisplayMode.NOTIFICATION_BAR)

internal class HomeworkReminderSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("homework-reminder-defaults", 0)
    fun load() = HomeworkReminderDefaults(prefs.getBoolean("enabled", true), prefs.getInt("before", 60).coerceIn(1, 525600),
        prefs.getString("daily", null)?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
        runCatching { ReminderDisplayMode.valueOf(prefs.getString("mode", "NOTIFICATION_BAR")!!) }.getOrDefault(ReminderDisplayMode.NOTIFICATION_BAR))
    fun save(value: HomeworkReminderDefaults) { prefs.edit().putBoolean("enabled", value.enabled).putInt("before", value.beforeMinutes)
        .putString("daily", value.daily?.toString()).putString("mode", value.displayMode.name).apply() }
}

@Composable
internal fun HomeworkReminderSettings() {
    val context = LocalContext.current
    val colors = LocalQztcColors.current
    val store = remember { HomeworkReminderSettingsStore(context) }
    var value by remember { mutableStateOf(store.load()) }
    var picker by remember { mutableStateOf("") }
    var test by remember { mutableStateOf(ReminderPreviewState()) }
    fun save(next: HomeworkReminderDefaults) { store.save(next); value = next; HomeworkReminderScheduler.reschedule(context) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) save(value.copy(enabled = true)) }
    fun preview(mode: ReminderDisplayMode = value.displayMode) {
        val sent = QztcNotifications.notifyHomeworkReminder(context, "作业提醒预览", "待提交作业\n截止前 ${value.beforeMinutes} 分钟提醒", 73901, mode)
        test = ReminderPreviewState(message = if (sent) "预览提醒已发送" else "请先允许通知权限", needsConfirmation = sent, needsSystemSettings = !sent)
    }
    Column(Modifier.fillMaxWidth().padding(bottom = QztcSpacing.md).background(colors.solid, RoundedCornerShape(QztcCorners.content))
        .padding(QztcSpacing.lg), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("作业提醒", style = QztcTypography.section)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (value.enabled) "已开启" else "已关闭", Modifier.weight(1f))
            Switch(value.enabled, { enable ->
                if (enable && Build.VERSION.SDK_INT >= 33 && !QztcNotifications.canPost(context)) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else save(value.copy(enabled = enable))
            }, Modifier.testTag("homework-reminder-enabled"))
        }
        Column(Modifier.alpha(if (value.enabled) 1f else .45f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = value.enabled, onClick = { picker = "before" }) {
                Text("默认在截止前 ${value.beforeMinutes / 60} 小时 ${value.beforeMinutes % 60} 分钟提醒")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("每天提醒待提交作业", Modifier.weight(1f))
                Switch(value.daily != null, { save(value.copy(daily = if (it) LocalTime.of(20, 0) else null)) }, enabled = value.enabled)
            }
            value.daily?.let { time -> TextButton(enabled = value.enabled, onClick = { picker = "daily" }) { Text("每日时间  $time") } }
            QztcReminderModeOptions(value.displayMode == ReminderDisplayMode.SCREEN_POPUP, {
                val mode = if (it) ReminderDisplayMode.SCREEN_POPUP else ReminderDisplayMode.NOTIFICATION_BAR
                save(value.copy(displayMode = mode)); preview(mode)
            }, test, { preview() }, {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }, enabled = value.enabled)
        }
    }
    if (value.enabled && picker == "daily") QztcTimePicker(value.daily ?: LocalTime.of(20, 0), { picker = "" }) {
        save(value.copy(daily = it)); picker = ""
    }
    if (value.enabled && picker == "before") QztcHourMinutePicker("截止前提醒", value.beforeMinutes / 60, value.beforeMinutes % 60, 8760,
        { picker = "" }) { hour, minute -> save(value.copy(beforeMinutes = (hour * 60 + minute).coerceAtLeast(1))); picker = "" }
}
