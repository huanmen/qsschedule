package cn.qztc.timetable.update

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun AppUpdateDialog(
    manifest: AppUpdateClient.Manifest, currentVersion: String,
    downloading: Boolean, downloadedBytes: Long, ready: Boolean, clearing: Boolean,
    onHide: () -> Unit, onDownload: () -> Unit, onInstall: () -> Unit, onClear: () -> Unit,
) {
    val progress = (downloadedBytes.toFloat() / manifest.sizeBytes.coerceAtLeast(1)).coerceIn(0f, 1f)
    AlertDialog(
        onDismissRequest = { if (!clearing) onHide() },
        title = { Text("更新至 ${manifest.latestVersionName}") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("当前版本 $currentVersion")
            if (downloading) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text("${(progress * 100).toInt()}% · ${formatApkSize(downloadedBytes)} / ${formatApkSize(manifest.sizeBytes)}")
                Text(if (clearing) "正在停止下载并清理文件…" else if (progress >= 1f) "下载完成，正在校验安装包…" else "暂时收起后，可从“检查更新”返回。取消会清除已下载的文件。")
            } else if (ready) {
                Text("下载和校验已完成，可以安装更新。")
            }
            if (!downloading && manifest.notes.isNotEmpty()) Text(manifest.notes.joinToString("\n") { "• $it" })
            TextButton(onClick = onClear, enabled = !clearing) {
                Text(if (clearing) "正在清除…" else if (downloading) "取消并清除" else "清除安装包")
            }
        } },
        dismissButton = { TextButton(onClick = onHide, enabled = !clearing) { Text(if (downloading || ready) "暂时收起" else "稍后") } },
        confirmButton = {
            if (!downloading) Button(onClick = if (ready) onInstall else onDownload, enabled = !clearing) {
                Text(if (ready) "安装更新" else "下载更新")
            }
        },
    )
}

internal fun formatApkSize(bytes: Long): String = String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
