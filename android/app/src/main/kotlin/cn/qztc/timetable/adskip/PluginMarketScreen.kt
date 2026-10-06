package cn.qztc.timetable.adskip

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun PluginMarketScreen(onBack: () -> Unit, onManagePlugin: () -> Unit, onReviewPlugin: () -> Unit = {}) {
    val context = LocalContext.current
    val revision = rememberAdSkipRevision()
    val installed = remember(revision) { AdSkipStore.installed(context) }
    val colors = LocalQztcColors.current
    var delete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf<PluginDistribution.Release?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val reviewStore = remember(context) { cn.qztc.timetable.review.ReviewStore(context) }
    var reviewInstalled by remember { mutableStateOf(reviewStore.installed()) }
    var deleteReview by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { release = PluginDistribution.available() }
    val needsUpdate = remember(revision, release) { AdSkipStore.pluginNeedsUpdate(context, release?.version ?: 2) }
    AdSkipPage("插件市场", onBack) {
        AdSkipCard {
            Column(Modifier.clickable(enabled = installed, onClick = onManagePlugin).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.VerifiedUser, null, Modifier.size(32.dp), tint = colors.accentDeep)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text("广告辅助", style = QztcTypography.courseTitle, color = colors.ink)
                        Text("自动跳过广告", style = QztcTypography.meta, color = colors.mut)
                    }
                    TextButton(enabled = !busy, onClick = {
                        if (!installed || needsUpdate) scope.launch {
                            busy = true
                            error = PluginDistribution.install(context, release).exceptionOrNull()?.message
                            busy = false
                        } else delete = true
                    }) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.accentDeep)
                        else Text(when { !installed -> "下载"; needsUpdate -> "更新"; else -> "删除" })
                    }
                }
            }
        }
        AdSkipCard {
            Row(Modifier.fillMaxWidth().clickable(enabled = reviewInstalled, onClick = onReviewPlugin).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.EditNote, null, Modifier.size(32.dp), tint = colors.accentDeep)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text("AI 好评助手", style = QztcTypography.courseTitle, color = colors.ink)
                    Text("自然表达 · 字数控制", style = QztcTypography.meta, color = colors.mut)
                }
                TextButton(onClick = { if (reviewInstalled) deleteReview = true else { reviewStore.install(); reviewInstalled = true } }) { Text(if (reviewInstalled) "删除" else "下载") }
            }
        }
        error?.let { Text(it, color = colors.mut, style = QztcTypography.meta) }
    }
    if (deleteReview) AlertDialog(onDismissRequest = { deleteReview = false }, title = { Text("删除 AI 好评助手？") },
        text = { Text("删除插件草稿和模型配置，课表不受影响。") },
        confirmButton = { TextButton(onClick = { reviewStore.delete(); reviewInstalled = false; deleteReview = false }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { deleteReview = false }) { Text("取消") } })
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("删除广告辅助？") },
        text = { Text("停止服务并删除插件数据，课表不受影响。") },
        confirmButton = { TextButton(onClick = { AdSkipStore.removeDownloadedPlugin(context); delete = false }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { delete = false }) { Text("取消") } })
}
