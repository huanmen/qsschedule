package cn.qztc.timetable.adskip

import android.app.NotificationManager
import android.content.Intent
import android.content.SharedPreferences
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.qztc.timetable.core.ui.theme.*

@Composable
internal fun rememberAdSkipRevision(): Int {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(context, owner) {
        val prefs = AdSkipStore.prefs(context)
        val permissionObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { revision++ }
        }
        context.contentResolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, permissionObserver)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            revision++
            prefs.edit().putLong("background_retry", android.os.SystemClock.uptimeMillis()).apply()
        } }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        owner.lifecycle.addObserver(observer)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener); owner.lifecycle.removeObserver(observer); context.contentResolver.unregisterContentObserver(permissionObserver) }
    }
    return revision
}

@Composable
fun AdSkipScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val revision = rememberAdSkipRevision()
    val colors = LocalQztcColors.current
    val connected by AdSkipRuntime.connected.collectAsStateWithLifecycle()
    val background by AdSkipRuntime.background.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf("home") }
    var disclosure by rememberSaveable { mutableStateOf(false) }
    var powerSettings by rememberSaveable { mutableStateOf(false) }
    var feedbackSettings by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val enabled = remember(revision) { AdSkipStore.enabled(context) }
    val installed = remember(revision) { AdSkipStore.installed(context) }
    val authorized = remember(revision, connected) { AdSkipStore.serviceEnabled(context) }
    val unrestricted = remember(revision) { context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName) }
    val notifications = remember(revision) { context.getSystemService(NotificationManager::class.java).areNotificationsEnabled() }
    fun settings(intent: Intent) { runCatching { context.startActivity(intent) }.onFailure { message = "请在系统设置中手动开启" } }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) runCatching {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }
    }
    fun enable() {
        AdSkipStore.setEnabled(context, true)
        if (!authorized) settings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        else if (Build.VERSION.SDK_INT >= 33 && !notifications) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    BackHandler(page != "home") { page = "home" }
    when (page) {
        "logs" -> { AdSkipLogScreen { page = "home" }; return }
        "apps" -> { AdSkipApplicationsScreen { page = "home" }; return }
    }
    AdSkipPage("广告辅助", onBack) {
        AdSkipCard {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.VerifiedUser, null, Modifier.size(26.dp), tint = colors.accentDeep)
                Text(when { !installed -> "未安装"; !enabled -> "服务未开启"; connected -> "服务运行中"; !authorized -> "待开启无障碍"; else -> "等待服务连接" }, Modifier.weight(1f), style = QztcTypography.courseTitle, color = colors.ink)
                Button(
                    onClick = {
                        if (enabled && authorized) AdSkipStore.setEnabled(context, false)
                        else if (AdSkipStore.prefs(context).getBoolean("consent_v2", false)) enable()
                        else disclosure = true
                    }, enabled = installed,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentDeep),
                ) { Text(if (enabled && authorized) "关闭服务" else "开启服务") }
                }
                if (!installed) Text("请先在插件列表安装", style = QztcTypography.meta, color = colors.mut)
            }
        }
        AdSkipCard {
            AdSkipRow("无障碍服务", Icons.Outlined.AccessibilityNew, if (authorized && connected) "已开启" else "去开启") { settings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            AdSkipDivider()
            AdSkipRow("电池优化", Icons.Outlined.BatteryChargingFull, if (unrestricted) "系统已豁免" else "去设置") { powerSettings = true }
            AdSkipDivider()
            // Manufacturer autostart switches have no portable Android status API.
            // This is a navigation action, not an assertion that access is missing.
            AdSkipRow("自启动", Icons.Outlined.Settings, "查看设置") { powerSettings = true }
            AdSkipDivider()
            AdSkipRow("运行通知", Icons.Outlined.Notifications, if (notifications) "已开启" else "去开启") {
                if (Build.VERSION.SDK_INT >= 33 && !notifications) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                else settings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
        }
        AdSkipCard {
            val excluded = remember(revision) { AdSkipStore.prefs(context).all.count { it.key.startsWith("app:") && it.value == false } }
            AdSkipRow("应用范围", Icons.Outlined.Apps, if (excluded == 0) "全部应用" else "已排除 " + excluded + " 个") { page = "apps" }
            AdSkipDivider()
            AdSkipRow("运行日志", Icons.Outlined.History) { page = "logs" }
            AdSkipDivider()
            AdSkipToggle("底部提示", Icons.Outlined.Notifications, AdSkipStore.feedbackEnabled(context)) { AdSkipStore.setFeedback(context, it) }
            AdSkipDivider()
            AdSkipRow("成功提示语", Icons.Outlined.TextFields, AdSkipStore.feedbackTemplate(context)) {
                feedbackSettings = true
            }
            AdSkipDivider()
            AdSkipToggle("持续后台运行", Icons.Outlined.BatteryChargingFull, AdSkipStore.foregroundEnabled(context)) { AdSkipStore.setForeground(context, it) }
            AdSkipDivider()
            AdSkipToggle("仅处理开屏广告", Icons.Outlined.Timer, AdSkipStore.splashOnly(context)) { AdSkipStore.setSplashOnly(context, it) }
        }
        AdSkipRuleList()
        message?.let { Text(it, color = colors.mut, style = QztcTypography.meta) }
    }
    if (disclosure) AlertDialog(onDismissRequest = { disclosure = false }, title = { Text("开启服务") },
        text = { Text("读取应用控件并点击广告跳过按钮。页面内容不保存、不上传，可随时关闭。") },
        confirmButton = { TextButton(onClick = {
            disclosure = false
            AdSkipStore.prefs(context).edit().putBoolean("consent_v2", true).apply()
            enable()
        }) { Text("开启服务") } }, dismissButton = { TextButton(onClick = { disclosure = false }) { Text("取消") } })
    if (powerSettings) AlertDialog(
        onDismissRequest = { powerSettings = false },
        title = { Text("后台运行设置", style = QztcTypography.pageTitle) },
        text = {
            val explanationStyle = QztcTypography.body.copy(fontSize = 16.sp, lineHeight = 25.sp)
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("保持广告辅助在切换应用后继续运行，请检查以下设置：", style = explanationStyle, color = colors.ink)
                Text("1. 省电策略选择「无限制」。\n2. 允许本应用自启动。", style = explanationStyle, color = colors.ink)
                Text("若系统仍会暂停服务，再检查系统电池优化。它与手机厂商的省电策略是两个设置。", style = explanationStyle, color = colors.mut)
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    powerSettings = false
                    settings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)))
                }, modifier = Modifier.fillMaxWidth()) { Text("应用省电与自启动设置") }
                OutlinedButton(onClick = {
                    powerSettings = false
                    settings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (unrestricted) "查看系统电池优化 · 已豁免" else "打开系统电池优化设置")
                }
                TextButton(onClick = { powerSettings = false }, modifier = Modifier.align(Alignment.End)) { Text("关闭") }
            }
        },
    )
    if (feedbackSettings) FeedbackTemplateDialog { feedbackSettings = false }
}

@Composable
private fun FeedbackTemplateDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalQztcColors.current
    var draft by remember { mutableStateOf(TextFieldValue(AdSkipStore.feedbackTemplate(context))) }
    fun insert(tag: String) {
        val start = draft.selection.min
        val end = draft.selection.max
        val text = draft.text.replaceRange(start, end, tag)
        if (text.length <= 120) draft = TextFieldValue(text, TextRange(start + tag.length))
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("成功提示语") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(draft, { if (it.text.length <= 120 && it.text.none(Char::isISOControl)) draft = it },
                Modifier.fillMaxWidth(), label = { Text("提示语") }, maxLines = 3)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { insert(AdSkipFeedbackTemplate.APP) }) { Text("插入应用名") }
                TextButton(onClick = { insert(AdSkipFeedbackTemplate.ICON) }) { Text("插入应用图标") }
            }
            Text("预览", style = QztcTypography.meta, color = colors.mut)
            AndroidView(factory = { feedbackLabel(it) }, update = { it.renderFeedback(draft.text, "菜鸟") }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { draft = TextFieldValue(AdSkipFeedbackTemplate.DEFAULT) }) { Text("恢复默认") }
        }
    }, confirmButton = {
        TextButton(enabled = draft.text.isNotBlank(), onClick = { AdSkipStore.setFeedbackTemplate(context, draft.text); onDismiss() }) { Text("保存") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
internal fun AdSkipPage(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    cn.qztc.timetable.core.ui.plugin.PluginPage(title, onBack, actions, content)
}

@Composable
internal fun AdSkipCard(content: @Composable ColumnScope.() -> Unit) {
    cn.qztc.timetable.core.ui.plugin.PluginCard(content)
}

@Composable
internal fun AdSkipDivider() { HorizontalDivider(Modifier.padding(start = 54.dp), color = LocalQztcColors.current.line) }

@Composable
internal fun AdSkipRow(title: String, icon: ImageVector, value: String = "", onClick: () -> Unit) {
    val colors = LocalQztcColors.current
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = colors.mut)
        Text(title, Modifier.weight(1f).padding(start = 14.dp), style = QztcTypography.body, color = colors.ink)
        Text(value, style = QztcTypography.meta, color = colors.mut)
        Icon(Icons.Outlined.ChevronRight, null, tint = colors.faint)
    }
}

@Composable
internal fun AdSkipToggle(title: String, icon: ImageVector?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalQztcColors.current
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Icon(it, null, Modifier.size(22.dp), tint = colors.mut) }
        Text(title, Modifier.weight(1f).padding(start = if (icon == null) 0.dp else 14.dp), style = QztcTypography.body, color = colors.ink)
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.semantics { contentDescription = title })
    }
}
