package cn.qztc.timetable.adskip

import android.app.NotificationManager
import android.content.Intent
import android.content.SharedPreferences
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
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            revision++
            prefs.edit().putLong("background_retry", android.os.SystemClock.uptimeMillis()).apply()
        } }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        owner.lifecycle.addObserver(observer)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener); owner.lifecycle.removeObserver(observer) }
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
    var message by remember { mutableStateOf<String?>(null) }
    val enabled = remember(revision) { AdSkipStore.enabled(context) }
    val installed = remember(revision) { AdSkipStore.installed(context) }
    val authorized = remember(revision) { AdSkipStore.serviceEnabled(context) }
    val unrestricted = remember(revision) { context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName) }
    val notifications = remember(revision) { context.getSystemService(NotificationManager::class.java).areNotificationsEnabled() }
    fun settings(intent: Intent) { runCatching { context.startActivity(intent) }.onFailure { message = "请在系统设置中手动开启" } }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun enable() {
        AdSkipStore.setEnabled(context, true)
        if (!authorized) settings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
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
                        if (enabled) AdSkipStore.setEnabled(context, false)
                        else if (AdSkipStore.prefs(context).getBoolean("consent_v2", false)) enable()
                        else disclosure = true
                    }, enabled = installed,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentDeep),
                ) { Text(if (enabled) "关闭服务" else "开启服务") }
                }
                if (enabled && !connected) TextButton(onClick = { settings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("开启无障碍权限") }
                if (!installed) Text("请先在插件列表安装", style = QztcTypography.meta, color = colors.mut)
            }
        }
        AdSkipCard {
            AdSkipRow("无障碍服务", Icons.Outlined.AccessibilityNew, if (authorized && connected) "已开启" else "去开启") { settings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            AdSkipDivider()
            AdSkipRow("电池优化", Icons.Outlined.BatteryChargingFull, if (unrestricted) "系统已豁免" else "去设置") { powerSettings = true }
            AdSkipDivider()
            AdSkipRow("自启动", Icons.Outlined.Settings, "去设置") { settings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))) }
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
    if (powerSettings) AlertDialog(onDismissRequest = { powerSettings = false }, title = { Text("后台运行设置") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("在应用信息中，将省电策略设为无限制，并开启自启动。系统电池豁免与厂商省电策略是两个设置。", style = QztcTypography.meta)
            TextButton(onClick = {
                powerSettings = false
                settings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)))
            }) { Text("应用省电策略与自启动") }
            TextButton(onClick = { powerSettings = false; settings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) {
                Text(if (unrestricted) "系统电池优化 · 已豁免" else "系统电池优化")
            }
        } }, confirmButton = { TextButton(onClick = { powerSettings = false }) { Text("完成") } })
}

@Composable
internal fun AdSkipPage(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalQztcColors.current
    Column(Modifier.fillMaxSize().background(colors.solid2)) {
        QztcPageTopBar(title = title, onBack = onBack, actions = actions)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(QztcSpacing.lg).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp), content = content)
    }
}

@Composable
internal fun AdSkipCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = LocalQztcColors.current.solid, shape = RoundedCornerShape(QztcCorners.content)) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
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
internal fun AdSkipToggle(title: String, icon: ImageVector, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalQztcColors.current
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = colors.mut)
        Text(title, Modifier.weight(1f).padding(start = 14.dp), style = QztcTypography.body, color = colors.ink)
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.semantics { contentDescription = title })
    }
}
