package cn.qztc.timetable.adskip

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcPageTopBar
import cn.qztc.timetable.core.ui.theme.QztcSpacing
import cn.qztc.timetable.core.ui.theme.QztcTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.ByteArrayOutputStream

@Composable
fun AdSkipScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = LocalQztcColors.current
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    val connected by AdSkipRuntime.connected.collectAsStateWithLifecycle()
    val enabled = remember(revision) { AdSkipStore.enabled(context) }
    val installed = remember(revision) { AdSkipStore.installed(context) }
    val authorized = remember(revision) { AdSkipStore.serviceEnabled(context) }
    val localPack = remember(revision) { AdSkipStore.localPack(context) }
    val pack = localPack ?: AdSkipRules.bundled
    val records = remember(revision) { AdSkipStore.records(context) }
    val unrestricted = remember(revision) { context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName) }
    var disclosure by remember { mutableStateOf(false) }
    var restore by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<AdSkipImportPreview?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var downloadUrl by rememberSaveable { mutableStateOf("") }
    fun openSettings(intent: Intent) {
        runCatching { context.startActivity(intent) }.onFailure { message = "此设备无法打开该设置，请在系统设置中手动查找泉师课程表。" }
    }
    DisposableEffect(lifecycleOwner, context) {
        val prefs = AdSkipStore.prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener); lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) { runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (output.size() <= AdSkipRules.MAX_BYTES) {
                        val count = stream.read(buffer, 0, minOf(buffer.size, AdSkipRules.MAX_BYTES + 1 - output.size()))
                        if (count <= 0) break
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                    ?: error("无法读取")
                require(bytes.size <= AdSkipRules.MAX_BYTES)
                AdSkipImport.parse(bytes.toString(Charsets.UTF_8))
            } }
            result.onSuccess { preview = it }.onFailure { message = "导入失败：${it.message ?: "文件格式无效"}。支持 4 MB 以内 JSON/JSON5，原插件未更改。" }
            busy = false
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) { runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(AdSkipRules.encode(AdSkipRules.bundled)) }
                    ?: error("无法写入")
            } }
            message = if (result.isSuccess) "已导出基础规则模板，不含屏幕或个人数据。" else "导出失败，请重新选择保存位置。"
            busy = false
        }
    }
    Column(Modifier.fillMaxSize().background(colors.solid2)) {
        QztcPageTopBar(title = "插件管理 · 广告辅助", onBack = onBack)
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(QztcSpacing.lg), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("按需开启，不影响课表", style = QztcTypography.courseTitle, color = colors.ink)
                    Text("无需 GKD、Shizuku 或 Root", style = QztcTypography.meta, color = colors.mut)
                }
                Switch(checked = enabled, enabled = installed, onCheckedChange = { if (it) disclosure = true else AdSkipStore.setEnabled(context, false) })
            }
            Surface(color = colors.solid, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(when { !installed -> "广告辅助未安装"; !enabled -> "广告辅助已暂停"; connected -> "服务已连接"; authorized -> "系统已授权，但服务未连接"; else -> "等待开启系统无障碍服务" }, style = QztcTypography.courseTitle, color = colors.ink)
                    Text("系统授权：${if (authorized) "已开启" else "未开启"} · 服务连接：${if (connected) "已连接" else "未连接"}", style = QztcTypography.meta, color = colors.mut)
                    Text("总开关关闭时不读取或点击目标应用。系统显示已授权，不代表服务正在运行。", style = QztcTypography.meta, color = colors.mut)
                    TextButton(onClick = { openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("打开无障碍设置") }
                }
            }
            Text("应用与规则", style = QztcTypography.section, color = colors.mut)
            AdSkipStore.appNames(context).forEach { (pkg, name) ->
                val rules = AdSkipStore.rules(context).filter { it.packageName == pkg }
                val appEnabled = remember(revision, pkg) { AdSkipStore.appEnabled(context, pkg) }
                Surface(color = colors.solid, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(name, style = QztcTypography.courseTitle, color = colors.ink)
                                Text(when { rules.isEmpty() -> "暂无可执行规则"; localPack != null -> "${rules.size} 项本地规则 · 未验证"; else -> "${rules.size} 项基础规则 · 有外部试用记录" }, style = QztcTypography.meta, color = colors.mut)
                            }
                            Switch(checked = appEnabled && rules.isNotEmpty(), enabled = enabled && rules.isNotEmpty(), onCheckedChange = { AdSkipStore.setAppEnabled(context, pkg, it) })
                        }
                        rules.forEach { rule ->
                            val checked = remember(revision, rule.id) { AdSkipStore.ruleEnabled(context, rule) }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(rule.name, Modifier.weight(1f), style = QztcTypography.body, color = colors.ink)
                                Switch(checked = checked, enabled = enabled && appEnabled, onCheckedChange = { AdSkipStore.setRuleEnabled(context, rule, it) })
                            }
                        }
                        val note = when (pkg) {
                            "com.mygolbs.mybus" -> "启动广告仍待适配；不自动点击 WebView 或屏幕坐标。"
                            "com.cainiao.wireless" -> "启动广告仍待适配。"
                            "com.zhishan.washer" -> "尚未遇到可验证广告，不使用可能误点报修的规则。"
                            else -> "本 App 的实际效果以当前设备复查结果为准。"
                        }
                        Text(note, style = QztcTypography.meta, color = colors.mut)
                    }
                }
            }
            Text("规则管理", style = QztcTypography.section, color = colors.mut)
            Text("${pack.name} · v${pack.version}", style = QztcTypography.body, color = colors.ink)
            Text(pack.source, style = QztcTypography.meta, color = colors.mut)
            Text("按需安装，也可随时删除。删除会停止广告辅助，清除规则、开关和诊断记录。下载或导入后先预览，安装后保持关闭。", style = QztcTypography.meta, color = colors.mut)
            OutlinedTextField(
                value = downloadUrl,
                onValueChange = { downloadUrl = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !busy,
                label = { Text("插件下载地址") },
                placeholder = { Text("https://example.com/adskip-v1.json") },
                supportingText = { Text("仅接受 HTTPS；下载后会先校验并预览，不会自动启用") },
            )
            OutlinedButton(
                enabled = !busy && downloadUrl.isNotBlank(),
                onClick = {
                    scope.launch {
                        busy = true
                        val result = withContext(Dispatchers.IO) { runCatching { AdSkipStore.downloadPack(downloadUrl) } }
                        result.onSuccess { preview = it; message = "下载完成，请确认规则来源后安装。" }
                            .onFailure { error -> message = "下载失败：${error.message ?: "地址或规则包无效"}" }
                        busy = false
                    }
                },
            ) { Text(if (busy) "正在下载…" else "下载插件") }
            OutlinedButton(enabled = !busy, onClick = { importer.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }) { Text(if (busy) "正在处理…" else "导入本地规则包") }
            TextButton(enabled = !busy, onClick = { exporter.launch("campus-adskip-v1.json") }) { Text("导出基础规则模板") }
            if (installed) TextButton(enabled = !busy, onClick = { restore = true }) { Text("删除已安装插件") }
            val issues = remember(revision) { AdSkipStore.importIssues(context) }
            if (issues.isNotEmpty()) Text("上次安装有 ${issues.size} 项不兼容：\n" + issues.take(8).joinToString("\n") { "${it.app} · ${it.group}：${it.reason}" }, style = QztcTypography.meta, color = colors.mut)
            message?.let { Text(it, style = QztcTypography.body, color = colors.accentDeep) }

            Text("后台运行", style = QztcTypography.section, color = colors.mut)
            Text(if (unrestricted) "系统电池优化：已豁免" else "系统电池优化：未豁免（不代表服务已停止）", style = QztcTypography.body, color = colors.ink)
            Text("如服务经常断开，可在系统设置允许自启动、调整电池限制，或手动锁定最近任务。强制停止会中断服务；本功能不隐藏进程，也不保证绕过系统清理。", style = QztcTypography.meta, color = colors.mut)
            TextButton(onClick = { openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("打开应用系统设置") }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("最近诊断", Modifier.weight(1f), style = QztcTypography.section, color = colors.mut)
                TextButton(enabled = records.isNotEmpty(), onClick = { AdSkipStore.clearRecords(context) }) { Text("清空记录") }
            }
            Text("点击请求被接受 ≠ 广告已关闭。复查只报告目标节点和页面状态，不保证广告消失由点击造成。", style = QztcTypography.meta, color = colors.mut)
            if (records.isEmpty()) Text("暂无记录。开启后，在已选应用的适配页面触发广告，再返回查看。", style = QztcTypography.body, color = colors.mut)
            val formatter = remember { SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault()) }
            records.take(15).forEach { record ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${record.app} · ${record.rule}", style = QztcTypography.body, color = colors.ink)
                    Text(record.result, style = QztcTypography.meta, color = colors.accentDeep)
                    Text(formatter.format(Date(record.timestamp)), style = QztcTypography.meta, color = colors.mut)
                }
            }
            Text("最多保存 60 条本地诊断，只含应用、规则、时间和结果。不保存屏幕内容，不上传使用记录。", style = QztcTypography.meta, color = colors.mut)
        }
    }
    if (disclosure) AlertDialog(
        onDismissRequest = { disclosure = false }, title = { Text("开启广告辅助？") },
        text = { Text("此功能会通过无障碍服务读取你选中应用的页面控件，按启用的规则点击广告关闭或跳过按钮。可能因应用更新而失效或误点，请只启用可信规则。页面内容不保存、不上传；可随时关闭总开关，或在系统设置撤销授权。") },
        confirmButton = { TextButton(onClick = { disclosure = false; AdSkipStore.setEnabled(context, true); if (!authorized) openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("同意并开启") } },
        dismissButton = { TextButton(onClick = { disclosure = false }) { Text("取消") } },
    )
    preview?.let { candidate -> AlertDialog(
        onDismissRequest = { preview = null }, title = { Text("安装广告辅助插件？") },
        text = { Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            candidate.pack?.let { Text("${it.name} · v${it.version}\n${it.rules.size} 项可安装规则\n来源（文件自述）：${it.source}") }
            Text("涉及 ${candidate.appCount} 个应用，${candidate.issues.size} 项不兼容。安装会替换现有规则包，总开关、应用与规则开关全部关闭。")
            candidate.issues.take(30).forEach { Text("${it.app} · ${it.group}：${it.reason}") }
            if (candidate.pack == null) Text("没有兼容规则，不能安装。")
        } },
        confirmButton = { TextButton(enabled = candidate.pack != null, onClick = { candidate.pack?.let { AdSkipStore.installPack(context, it, candidate.issues) }; preview = null; message = "已安装，所有开关均已关闭。请核对来源后逐项开启。" }) { Text("安装并保持关闭") } },
        dismissButton = { TextButton(onClick = { preview = null }) { Text("取消") } },
    ) }
    if (restore) AlertDialog(
        onDismissRequest = { restore = false }, title = { Text("删除已安装插件？") },
        text = { Text("将停止广告辅助并删除规则、开关和诊断记录。课表等数据不受影响；可重新安装基础包，或从链接下载。") },
        confirmButton = { TextButton(onClick = { AdSkipStore.removeDownloadedPlugin(context); restore = false; message = "插件已删除并停止运行，可重新安装。" }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { restore = false }) { Text("取消") } },
    )
}
