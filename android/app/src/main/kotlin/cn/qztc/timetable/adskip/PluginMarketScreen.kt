package cn.qztc.timetable.adskip

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.ui.plugin.PluginCard
import cn.qztc.timetable.core.ui.plugin.PluginPage
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcTypography
import cn.qztc.timetable.plugin.PluginController
import androidx.compose.material3.Checkbox
import cn.qztc.timetable.plugin.ExternalPluginFeeds
import cn.qztc.timetable.plugin.PluginRegistry
import cn.qztc.timetable.plugin.PluginSource
import cn.qztc.timetable.plugin.OfficialPluginCatalog
import kotlinx.coroutines.launch

@Composable
fun PluginMarketScreen(onBack: () -> Unit, onManagePlugin: () -> Unit,
    onReviewPlugin: () -> Unit = {},
    onScheduleQaPlugin: () -> Unit = {}, onExamSchedulePlugin: () -> Unit = {}, onAcademicRecordsPlugin: () -> Unit = {}) {
    val context = LocalContext.current
    val colors = LocalQztcColors.current
    val scope = rememberCoroutineScope()
    val latest = remember { mutableStateMapOf<String, Int>() }
    var revision by remember { mutableIntStateOf(0) }
    val adRevision = rememberAdSkipRevision()
    val serviceConnected by AdSkipRuntime.connected.collectAsState()
    var busyId by remember { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var addSource by remember { mutableStateOf(false) }
    var sourceUrl by remember { mutableStateOf("") }
    var sourceBusy by remember { mutableStateOf(false) }
    var sourceRevision by remember { mutableIntStateOf(0) }
    val sources = remember(sourceRevision) { ExternalPluginFeeds.sources(context) }
    var deleteExternal by remember { mutableStateOf<String?>(null) }
    var permissionId by remember { mutableStateOf<String?>(null) }
    var installAfterPermission by remember { mutableStateOf(false) }
    var official by remember { mutableStateOf(OfficialPluginCatalog.cached(context)) }
    var marketLoading by remember { mutableStateOf(false) }
    suspend fun refreshMarket() {
        marketLoading = true
        try {
            official = OfficialPluginCatalog.refresh(context)
            PluginRegistry.plugins.forEach { plugin -> latest[plugin.descriptor.id] = plugin.availableVersion() }
        } finally { marketLoading = false }
    }

    LaunchedEffect(Unit) {
        refreshMarket()
    }
    PluginPage("插件市场", onBack, actions = {
        IconButton(enabled = !marketLoading, onClick = { scope.launch { refreshMarket() } }) {
            Icon(Icons.Outlined.Refresh, "刷新插件市场", tint = colors.ink)
        }
        IconButton(onClick = { addSource = true }) { Icon(Icons.Outlined.Add, "添加插件来源", tint = colors.ink) }
    }) {
        PluginRegistry.plugins.forEach { plugin ->
            val id = plugin.descriptor.id
            val installed = remember(revision, adRevision) { plugin.installedVersion(context) }
            val remote = official?.plugins?.firstOrNull { it.id == id }
            val listed = official == null || remote != null
            val compatible = remote == null || remote.minAppVersion <= cn.qztc.timetable.BuildConfig.VERSION_CODE
            if (!listed && installed == 0) return@forEach
            val update = listed && compatible && installed > 0 && installed < (remote?.version ?: latest[id] ?: plugin.descriptor.version)
            PluginCard {
                Row(Modifier.fillMaxWidth().clickable(enabled = installed > 0) {
                    when (id) {
                        "academic-records" -> onAcademicRecordsPlugin()
                        "adskip" -> onManagePlugin()
                        "review" -> onReviewPlugin()
                        "schedule-qa" -> onScheduleQaPlugin()
                        "exam-schedule" -> onExamSchedulePlugin()
                    }
                }.padding(horizontal=14.dp,vertical=10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(when (id) {
                        "review" -> Icons.Outlined.EditNote
                        "schedule-qa" -> Icons.Outlined.EditNote
                        "exam-schedule" -> Icons.Outlined.Refresh
                        "academic-records" -> Icons.Outlined.EmojiEvents
                        else -> Icons.Outlined.VerifiedUser
                    }, null, Modifier.size(30.dp), tint = colors.accentDeep)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(remote?.name ?: plugin.descriptor.name, style = QztcTypography.courseTitle, color = colors.ink)
                        Text(if(id=="adskip")"自动跳过其他软件的广告，需开启无障碍服务"else remote?.summary ?: plugin.descriptor.summary,
                            style = QztcTypography.meta, color = colors.mut, maxLines=1, overflow=TextOverflow.Ellipsis)
                        if (!listed) Text("已下架 · 保留已安装版本", style = QztcTypography.meta, color = colors.mut)
                        if (!compatible) Text("需要更新 App", style = QztcTypography.meta, color = colors.mut)
                        if (installed > 0) {
                            val missing = PluginRegistry.missingDependencies(context, plugin.descriptor, serviceConnected)
                            if (missing.isNotEmpty()) Text(missing.joinToString(" · ") { "需${it.label}" },
                                style = QztcTypography.meta, color = colors.mut)
                            if (plugin.descriptor.capabilities.isNotEmpty()) Text("接口权限",Modifier.clickable {
                                installAfterPermission = false; permissionId = id
                            }.padding(vertical=4.dp),style=QztcTypography.meta,color=colors.accentDeep)
                        }
                    }
                    if (installed > 0) {
                        val pinned = remember(revision) { cn.qztc.timetable.plugin.PluginPins.contains(context, id) }
                        IconButton(onClick = { cn.qztc.timetable.plugin.PluginPins.set(context, id, !pinned); revision++ }) {
                            Icon(Icons.Outlined.PushPin, if (pinned) "从个人页取消置顶" else "显示到个人页",
                                modifier = Modifier.rotate(if (pinned) 0f else 45f),
                                tint = if (pinned) colors.accentDeep else colors.mut)
                        }
                    }
                    TextButton(enabled = busyId == null && !marketLoading && (installed > 0 || (listed && compatible)), onClick = {
                        if (installed > 0 && !update) deleteId = id
                        else if (plugin.descriptor.capabilities.isNotEmpty()) {
                            installAfterPermission = true; permissionId = id
                        }
                        else scope.launch {
                            busyId = id
                            error = plugin.install(context).exceptionOrNull()?.message
                            revision++
                            busyId = null
                        }
                    }) {
                        if (busyId == id) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(when { installed == 0 -> "下载"; update -> "更新"; else -> "删除" })
                    }
                }
            }
        }
        val officialSource = official?.let { catalog -> PluginSource(OfficialPluginCatalog.RULES_URL,
            catalog.rules.filter { entry -> sources.none { source -> source.plugins.any { it.id == entry.id } } }) }
        (listOfNotNull(officialSource) + sources.filterNot { it.url == OfficialPluginCatalog.RULES_URL }).forEach { source ->
            val isOfficial = source.url == OfficialPluginCatalog.RULES_URL
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (isOfficial) "官方规则插件" else source.url.substringAfter("://").substringBefore('/'), Modifier.weight(1f),
                    style = QztcTypography.meta, color = colors.mut)
                IconButton(onClick = {
                    scope.launch {
                        sourceBusy = true
                        runCatching {
                            if (isOfficial) official = OfficialPluginCatalog.refresh(context)
                            else ExternalPluginFeeds.save(context, ExternalPluginFeeds.fetch(source.url))
                        }.onSuccess { sourceRevision++ }
                            .onFailure { error = it.message ?: "更新来源失败" }
                        sourceBusy = false
                    }
                }, enabled = !sourceBusy) {
                    Icon(Icons.Outlined.Refresh, "更新插件来源", tint = colors.mut)
                }
                if (!isOfficial) IconButton(onClick = {
                    ExternalPluginFeeds.removeSource(context, source.url)
                    sourceRevision++
                }, enabled = source.plugins.none { ExternalPluginFeeds.installedVersion(context, it.id) > 0 }) {
                    Icon(Icons.Outlined.DeleteOutline, "移除插件来源", tint = colors.mut)
                }
            }
            source.plugins.forEach { entry ->
                val installed = ExternalPluginFeeds.installedVersion(context, entry.id)
                val listed = !isOfficial || entry.id in official!!.activeRules
                if (!listed && installed == 0) return@forEach
                PluginCard {
                    Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Extension, null, Modifier.size(30.dp), tint = colors.accentDeep)
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(entry.name, style = QztcTypography.courseTitle, color = colors.ink)
                            Text(entry.summary, style = QztcTypography.meta, color = colors.mut,maxLines=1,overflow=TextOverflow.Ellipsis)
                            if (!listed) Text("已下架 · 保留已安装版本", style = QztcTypography.meta, color = colors.mut)
                        }
                        TextButton(enabled = busyId == null, onClick = {
                            if (installed > 0 && (!listed || installed >= entry.version)) deleteExternal = entry.id
                            else scope.launch {
                                busyId = entry.id
                                error = ExternalPluginFeeds.install(context, entry).exceptionOrNull()?.message
                                sourceRevision++
                                revision++
                                busyId = null
                            }
                        }) {
                            if (busyId == entry.id) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text(when { installed == 0 -> "下载"; !listed -> "删除"; installed < entry.version -> "更新"; else -> "删除" })
                        }
                    }
                }
            }
        }
        error?.let { Text(it, style = QztcTypography.meta, color = colors.mut) }
    }
    val deleting: PluginController? = PluginRegistry.plugins.firstOrNull { it.descriptor.id == deleteId }
    if (deleting != null) AlertDialog(onDismissRequest = { deleteId = null },
        title = { Text("删除${deleting.descriptor.name}？") },
        text = { Text("仅清除该插件的数据，课表不受影响。") },
        confirmButton = { TextButton(onClick = {
            deleting.uninstall(context)
            cn.qztc.timetable.plugin.PluginAccessStore(context).revoke(deleting.descriptor.id)
            context.getSharedPreferences("plugin-sdk:${deleting.descriptor.id}", android.content.Context.MODE_PRIVATE).edit().clear().apply()
            revision++
            deleteId = null
        }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text("取消") } })
    PluginRegistry.plugins.firstOrNull { it.descriptor.id == permissionId }?.let { plugin ->
        val access = remember(context) { cn.qztc.timetable.plugin.PluginAccessStore(context) }
        var selected by remember(plugin.descriptor.id) { mutableStateOf(access.grants(plugin.descriptor.id)) }
        AlertDialog(onDismissRequest = { permissionId = null }, title = { Text("${plugin.descriptor.name} · 接口权限") },
            text = { Column { plugin.descriptor.capabilities.forEach { capability ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(capability in selected, { checked -> selected = if (checked) selected + capability else selected - capability })
                    Text(capability.label)
                }
            } } },
            confirmButton = { TextButton(onClick = {
                if (installAfterPermission) scope.launch {
                    busyId = plugin.descriptor.id
                    val result = plugin.install(context)
                    error = result.exceptionOrNull()?.message
                    if (result.isSuccess) access.set(plugin.descriptor.id, selected)
                    revision++; busyId = null
                } else access.set(plugin.descriptor.id, selected)
                permissionId = null
            }) { Text(if (installAfterPermission) "安装" else "保存") } },
            dismissButton = { TextButton(onClick = { permissionId = null }) { Text("取消") } })
    }
    if (deleteExternal != null) AlertDialog(onDismissRequest = { deleteExternal = null },
        title = { Text("删除规则插件？") },
        text = { Text("会移除这个来源加入的规则；内置规则和课表不受影响。") },
        confirmButton = { TextButton(onClick = {
            ExternalPluginFeeds.uninstall(context, deleteExternal!!)
            sourceRevision++
            revision++
            deleteExternal = null
        }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { deleteExternal = null }) { Text("取消") } })
    if (addSource) AlertDialog(onDismissRequest = { if (!sourceBusy) addSource = false },
        title = { Text("添加插件来源") },
        text = {
            Column {
                OutlinedTextField(sourceUrl, { sourceUrl = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, enabled = !sourceBusy, label = { Text("HTTPS 目录地址") })
                error?.let { Text(it, style = QztcTypography.meta, color = colors.mut) }
            }
        },
        confirmButton = { TextButton(enabled = !sourceBusy && sourceUrl.isNotBlank(), onClick = {
            scope.launch {
                sourceBusy = true
                val result = runCatching {
                    val source: PluginSource = ExternalPluginFeeds.fetch(sourceUrl)
                    ExternalPluginFeeds.save(context, source)
                }
                result.onSuccess {
                    sourceRevision++
                    addSource = false
                    error = null
                }.onFailure { error = it.message ?: "插件来源不可用" }
                sourceBusy = false
            }
        }) { Text(if (sourceBusy) "获取中…" else "添加") } },
        dismissButton = { TextButton(enabled = !sourceBusy, onClick = { addSource = false }) { Text("取消") } })
}
