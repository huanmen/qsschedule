package cn.qztc.timetable.storage

import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.Icons
import android.app.DownloadManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Process
import android.os.storage.StorageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.ui.theme.*
import cn.qztc.timetable.snapshot.SnapshotRecord
import cn.qztc.timetable.snapshot.SnapshotRow
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class StorageEntry(
    val id: String,
    val name: String,
    val category: String,
    val bytes: Long,
    val file: File? = null,
    val files: List<File> = emptyList(),
    val downloadId: Long? = null,
    val detail: String = "",
)
internal data class StorageInventory(val appBytes: Long, val dataBytes: Long, val entries: List<StorageEntry>, val cacheBytes: Long = 0, val measured: Boolean = true) {
    val totalBytes get() = appBytes + dataBytes
    val persistentBytes get() = (dataBytes-cacheBytes).coerceAtLeast(0)
}

internal class StorageInventoryReader(private val context: Context, private val activeBackground: String?) {
    private val webCaches = listOf("Default/HTTP Cache", "Default/Cache", "Default/Code Cache", "GPUCache")
        .map { File(context.dataDir,"app_webview/$it") }
    private fun within(file: File, root: File) = file.canonicalPath.startsWith(root.canonicalPath + File.separator)
    private fun eligible(file: File): Boolean {
        if (!file.isFile || java.nio.file.Files.isSymbolicLink(file.toPath()) || file.canonicalPath == activeBackground?.let { File(it).canonicalPath }) return false
        return within(file, context.cacheDir) || context.externalCacheDir?.let { within(file,it) } == true || webCaches.any { within(file,it) } ||
            (file.parentFile?.canonicalFile == File(context.filesDir,"snapshots").canonicalFile &&
                (file.name.startsWith("web-") || file.name.startsWith("pending-") || file.name.startsWith("resolved-"))) ||
            (file.parentFile?.canonicalFile == File(context.filesDir,"backgrounds").canonicalFile && file.name.startsWith("background-"))
    }
    fun read(): StorageInventory {
        val files = (listOfNotNull(context.cacheDir,context.externalCacheDir,File(context.filesDir,"snapshots"),File(context.filesDir,"backgrounds")) + webCaches)
            .flatMap { root -> root.canonicalFile.walkTopDown().onEnter { !java.nio.file.Files.isSymbolicLink(it.toPath()) }.filter { eligible(it) }.toList() }.distinctBy { it.canonicalPath }
        val rawEntries = files.map { file ->
            val isWebCache = webCaches.any { within(file,it) }
            val isMapCache = file.path.contains("offline-campus", true) || file.name.contains("map", true)
            val category = when {
                file.name.startsWith("school-calendar-") -> "校历与下载"
                file.parentFile?.name == "backgrounds" -> "图片与背景"
                file.name.startsWith("web-") || file.name.startsWith("pending-") || file.name.startsWith("resolved-") || file.path.contains("exports") -> "教务网页"
                isMapCache -> "地图"
                isWebCache || within(file, context.cacheDir) || context.externalCacheDir?.let { within(file,it) } == true -> "教务网页"
                else -> "其他临时数据"
            }
            val name = when {
                file.name.startsWith("school-calendar-") -> "校历原文件"
                file.parentFile?.name == "backgrounds" -> "备用背景图片"
                file.name.startsWith("web-") -> "教务课表截图"
                file.name.startsWith("pending-") || file.name.startsWith("resolved-") -> "历史临时快照"
                file.path.contains("exports") -> "分享课表临时文件"
                category == "地图" -> "地图运行缓存"
                category == "教务网页" -> "教务网页"
                else -> "应用临时缓存"
            }
            val detail = when {
                file.name.startsWith("school-calendar-") -> file.name.removePrefix("school-calendar-").removeSuffix(".pdf").let { "${it} 学年" }
                file.name.startsWith("web-") -> "保存的教务页面截图"
                file.name.startsWith("pending-") || file.name.startsWith("resolved-") -> "历史临时快照"
                else -> ""
            }
            StorageEntry(file.canonicalPath, name, category, file.length(), file = file, detail = detail)
        }
        val entries = rawEntries
            .groupBy { entry -> if (entry.category == "教务网页") "教务网页" else entry.id }
            .values
            .map { group ->
                if (group.first().category == "教务网页" && group.size > 1) {
                    StorageEntry(
                        id = "web-cache",
                        name = "教务网页",
                        category = "教务网页",
                        bytes = group.sumOf { it.bytes },
                        files = group.flatMap { listOfNotNull(it.file) + it.files },
                    )
                } else group.first()
            }
            .toMutableList()
        val downloads = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        runCatching { downloads.query(DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL))?.use { cursor ->
            while(cursor.moveToNext()) {
                val id=cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                val name=cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
                val size=cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).coerceAtLeast(0)
                entries += StorageEntry("download:$id", if(name.contains("校历")) "校历原文件" else "应用保存的下载文件", "校历与下载", size, downloadId=id, detail = name)
            }
        } }
        val stats = runCatching {
            val volume = (context.getSystemService(Context.STORAGE_SERVICE) as StorageManager).getUuidForPath(context.dataDir)
            (context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager)
                .queryStatsForPackage(volume, context.packageName, Process.myUserHandle())
        }.getOrNull()
        return StorageInventory(stats?.appBytes ?: File(context.applicationInfo.sourceDir).length(),stats?.dataBytes ?: 0,entries,stats?.cacheBytes ?: 0,stats != null)
    }
    fun clear(selected: List<StorageEntry>): Long {
        var removed=0L
        for(entry in selected) {
            val file=entry.file
            val targets = listOfNotNull(entry.file) + entry.files
            for (file in targets) if(eligible(file)) { val size=file.length(); if(file.delete()) removed+=size }
            entry.downloadId?.let { id ->
                if((context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(id)>0) removed+=entry.bytes
            }
        }
        return removed
    }
}

@Composable
fun StorageSettingsScreen(
    activeBackground: String?,
    onBack: () -> Unit,
    onFilesChanged: () -> Unit = {},
    snapshots: List<SnapshotRecord> = emptyList(),
    onShareSnapshot: (SnapshotRecord) -> Unit = {},
    onSaveSnapshot: (SnapshotRecord) -> Unit = {},
    onDeleteSnapshot: (SnapshotRecord) -> Unit = {},
) {
    val context=LocalContext.current
    val colors=LocalQztcColors.current
    val reader=remember(activeBackground) { StorageInventoryReader(context,activeBackground) }
    val scope=rememberCoroutineScope()
    var inventory by remember { mutableStateOf<StorageInventory?>(null) }
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var released by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<StorageEntry?>(null) }
    LaunchedEffect(reader) { inventory=withContext(Dispatchers.IO) { reader.read() } }
    BackHandler(enabled=!busy,onBack=onBack)
    val entries=inventory?.entries.orEmpty()
    val chosen=entries.filter { it.id in selected }
    Column(Modifier.fillMaxSize().background(colors.solid2)) {
        QztcPageTopBar(title="数据与存储",onBack={ if(!busy) onBack() })
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {
                Text("本地快照", style = QztcTypography.section, color = colors.ink)
                Text("可打开查看、分享、保存到相册或删除", style = QztcTypography.meta, color = colors.mut)
            }
            if (snapshots.isEmpty()) item { Text("暂无本地快照", style = QztcTypography.meta, color = colors.mut) }
            items(snapshots, key = { "snapshot-${it.file.absolutePath}" }) { record ->
                SnapshotRow(record, onShareSnapshot, onSaveSnapshot, onDeleteSnapshot)
            }
            item { HorizontalDivider(); Text("清理缓存", style = QztcTypography.section, color = colors.ink) }
            inventory?.let { stats ->
                item {
                    Text("总占用 ${storageSize(stats.totalBytes)}",style=QztcTypography.pageTitle,color=colors.ink)
                    val segments=listOf(Triple("程序",stats.appBytes,colors.accent),Triple("数据",stats.persistentBytes,colors.warn),Triple("缓存",stats.cacheBytes,colors.danger))
                    Row(Modifier.fillMaxWidth().height(12.dp).padding(top=4.dp)) {
                        segments.filter { it.second>0 }.forEach { (_,bytes,color) ->
                            Box(Modifier.weight(bytes.toFloat()).fillMaxHeight().background(color))
                        }
                    }
                    segments.forEach { (name,bytes,color) ->
                        Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                            Text(name,color=color); Text(storageSize(bytes),color=colors.ink)
                        }
                    }
                    if (!stats.measured) Text("系统统计暂不可用，仅显示可读取的安装文件",
                        style=QztcTypography.meta,color=colors.mut,modifier=Modifier.padding(top=8.dp))
                }
            }
            item {
                Text(
                    "程序空间由 Android 管理；下方只列出不会影响课表、账号、地图编辑和当前背景的可清理文件",
                    color = colors.mut,
                )
            }
            entries.groupBy { it.category }.forEach { (category,group) ->
                item(key=category) { Row(verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={expanded=if(category in expanded) expanded-category else expanded+category}) {
                        Icon(if(category in expanded) androidx.compose.material.icons.Icons.Outlined.ExpandLess else androidx.compose.material.icons.Icons.Outlined.ExpandMore,
                            if(category in expanded) "收起$category" else "展开$category")
                    }
                    Checkbox(group.all { it.id in selected },enabled=!busy,onCheckedChange={ check -> selected=if(check) selected+group.map { it.id } else selected-group.map { it.id }.toSet() })
                    Text("$category · ${storageSize(group.sumOf { it.bytes })}",color=colors.ink)
                } }
                if(category in expanded) items(group,key={it.id}) { entry ->
                    Row(Modifier.fillMaxWidth().clickable(enabled=!busy) { selected=if(entry.id in selected) selected-entry.id else selected+entry.id },verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(entry.id in selected,onCheckedChange=null)
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, style=QztcTypography.meta, color=colors.ink)
                            if (entry.detail.isNotBlank()) Text(entry.detail, style=QztcTypography.meta, color=colors.mut)
                        }
                        IconButton(
                            onClick = { preview = entry },
                            enabled = !busy,
                            modifier = Modifier.testTag("storage-preview:${entry.id}"),
                        ) {
                            Icon(Icons.Outlined.Info, contentDescription = "预览${entry.name}", tint = colors.mut)
                        }
                        Text(storageSize(entry.bytes),Modifier.padding(start=8.dp),style=QztcTypography.meta,color=colors.mut)
                    }
                }
                item(key="$category-divider") { HorizontalDivider() }
            }
            message?.let { item { Text(it,color=colors.mut) } }
            if(inventory != null && entries.isEmpty()) item { Text("暂无可清理文件",color=colors.mut) }
        }
        Button(enabled=chosen.isNotEmpty()&&!busy,onClick={confirm=true},modifier=Modifier.padding(20.dp).fillMaxWidth()) { Text("清除所选 · ${storageSize(chosen.sumOf { it.bytes })}") }
    }
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("清除所选文件？")},text={Text("共 ${chosen.size} 项，${storageSize(chosen.sumOf { it.bytes })}。已保存文件删除后不可恢复。")},
        confirmButton={TextButton(onClick={ confirm=false;busy=true;scope.launch {
            runCatching { withContext(Dispatchers.IO) { reader.clear(chosen) } }.onSuccess { released=storageSize(it) }.onFailure { message="部分文件无法清理，请刷新后重试" }
            inventory=withContext(Dispatchers.IO){reader.read()};selected=emptySet();busy=false;onFilesChanged()
        } }) {Text("清除")}},dismissButton={TextButton(onClick={confirm=false}){Text("取消")}})
    released?.let { size ->
        AlertDialog(
            onDismissRequest = { released = null },
            title = { Text("清理完成") },
            text = { Text("已释放 $size") },
            confirmButton = { TextButton(onClick = { released = null }) { Text("知道了") } },
        )
    }
    preview?.let { entry ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(entry.name) },
            text = { Text(listOf(entry.detail, "占用空间：${storageSize(entry.bytes)}").filter { it.isNotBlank() }.joinToString("\n")) },
            confirmButton = { TextButton(onClick = { preview = null }) { Text("关闭") } },
        )
    }
}


internal fun storageSize(bytes: Long): String = if(bytes < 1_000_000) "%.1f KB".format(bytes/1000.0) else "%.1f MB".format(bytes/1_000_000.0)
