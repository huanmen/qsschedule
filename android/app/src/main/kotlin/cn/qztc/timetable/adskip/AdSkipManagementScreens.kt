package cn.qztc.timetable.adskip

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun AdSkipLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val revision = rememberAdSkipRevision()
    val records = remember(revision) { AdSkipStore.outcomes(context) }
    val colors = LocalQztcColors.current
    val format = remember { SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault()) }
    AdSkipPage("运行日志", onBack, actions = {
        IconButton(onClick = { AdSkipStore.clearOutcomes(context) }, enabled = records.isNotEmpty()) { Icon(Icons.Outlined.DeleteOutline, "清空日志", tint = colors.mut) }
    }) {
        if (records.isEmpty()) Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) { Text("暂无日志", color = colors.mut) }
        else AdSkipCard {
            records.forEachIndexed { index, record ->
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(record.app, color = colors.ink, style = QztcTypography.body)
                        Text(format.format(Date(record.timestamp)), color = colors.mut, style = QztcTypography.meta)
                    }
                    Text(record.result, color = if (record.result == "成功") colors.accentDeep else colors.mut, style = QztcTypography.body)
                }
                if (index < records.lastIndex) AdSkipDivider()
            }
        }
    }
}

/** Rule files add to built-ins. Download UI appears only when explicitly opened. */
@Composable
internal fun AdSkipRuleImportDialog(onDismiss: () -> Unit, onImport: (AdSkipRulePack, List<AdSkipImportIssue>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<AdSkipImportPreview?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var showIssues by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            withContext(Dispatchers.IO) { runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytesBounded() } ?: error("无法读取文件")
                AdSkipImport.parse(bytes.toString(Charsets.UTF_8), AdSkipApplications.labels(context))
            } }.onSuccess { preview = it }.onFailure { message = it.message ?: "导入失败" }
            busy = false
        }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("添加规则") },
        text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (preview == null) {
                OutlinedTextField(url, { url = it }, singleLine = true, enabled = !busy, label = { Text("规则文件链接") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !busy && url.isNotBlank(), onClick = { scope.launch {
                        busy = true
                        withContext(Dispatchers.IO) { runCatching { AdSkipStore.downloadPack(url, AdSkipApplications.labels(context)) } }.onSuccess { preview = it }.onFailure { message = it.message ?: "下载失败" }
                        busy = false
                    } }) { Text(if (busy) "处理中…" else "下载") }
                    TextButton(enabled = !busy, onClick = { picker.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }) { Text("选择文件") }
                }
            } else {
                val candidate = preview!!
                Text(candidate.pack?.name ?: "没有兼容的规则")
                Text((candidate.pack?.rules?.size ?: 0).toString() + " 条可添加 · " + candidate.issues.size + " 条不兼容")
                if (candidate.issues.isNotEmpty()) TextButton(onClick = { showIssues = !showIssues }) { Text("查看不兼容项") }
                if (showIssues) candidate.issues.take(40).forEach { Text(it.app + " · " + it.group + "：" + it.reason, style = QztcTypography.meta) }
            }
            message?.let { Text(it) }
        } },
        confirmButton = { TextButton(enabled = !busy && preview?.pack != null, onClick = {
            val p = preview ?: return@TextButton
            runCatching { onImport(p.pack!!, p.issues) }.onSuccess { onDismiss() }.onFailure { message = it.message ?: "添加失败" }
        }) { Text("添加") } }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("取消") } })
}

private fun java.io.InputStream.readBytesBounded(): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (out.size() <= AdSkipRules.MAX_BYTES) {
        val count = read(buffer, 0, minOf(buffer.size, AdSkipRules.MAX_BYTES + 1 - out.size()))
        if (count < 0) break
        out.write(buffer, 0, count)
    }
    require(out.size() <= AdSkipRules.MAX_BYTES) { "文件超过 4 MB" }
    return out.toByteArray()
}
