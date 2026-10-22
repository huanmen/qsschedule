package cn.qztc.timetable.update

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.notifications.QztcNotifications

@Composable
fun ResourceUpdateHost(visible: Boolean, onDismiss: () -> Unit, onNoUpdate: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val state by ResourceUpdateSession.state.collectAsStateWithLifecycle()
    val colors = LocalQztcColors.current
    val currentNoUpdate by rememberUpdatedState(onNoUpdate)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        ResourceUpdateSession.requestDownload(context)
    }
    fun download() {
        if (Build.VERSION.SDK_INT >= 33 && !QztcNotifications.canPost(context)) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else ResourceUpdateSession.requestDownload(context)
    }
    fun hide() { if (state.stage == "done") ResourceUpdateSession.reset(); onDismiss() }
    LaunchedEffect(visible) { if (visible) ResourceUpdateSession.check(context) { currentNoUpdate() } }
    LaunchedEffect(state.stage, visible) {
        if (visible && state.stage == "error" && state.manifest == null) {
            android.widget.Toast.makeText(context, state.error ?: "暂时无法检查更新", android.widget.Toast.LENGTH_SHORT).show()
            ResourceUpdateSession.reset()
            onDismiss()
        }
    }
    if (!visible) return
    val stage = state.stage
    // A check is not an available update. Never show an empty/checking dialog.
    if (stage == "idle" || stage == "checking" || (stage == "error" && state.manifest == null)) return
    val m = state.manifest
    val busy = stage in listOf("checking", "pausing", "clearing", "applying")
    AlertDialog(
        onDismissRequest = { if (!busy) hide() },
        title = { Text("更新资源") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(when (stage) {
                "checking", "idle" -> "正在检查更新资源…"
                "downloading" -> "正在下载更新资源…"
                "pausing" -> "正在暂停下载…"
                "clearing" -> "正在取消下载并删除文件…"
                "paused" -> "下载已暂停，可继续下载"
                "applying" -> "正在校验并应用更新资源…"
                "done" -> "更新资源已应用，无需安装新包"
                "error" -> "更新未完成"
                else -> "发现新的更新资源"
            })
            if (m != null && stage != "done") {
                Text("${formatResourceBytes(state.bytes)} / ${formatResourceBytes(m.sizeBytes)}")
                LinearProgressIndicator(progress = { (state.bytes.toFloat() / m.sizeBytes).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            } else if (stage == "checking") LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it) }
            if (m != null && stage in listOf("available", "paused", "done")) m.notes.forEach { Text(it) }
        } },
        confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                @Composable fun action(label: String, enabled: Boolean = true, onClick: () -> Unit) {
                    OutlinedButton(onClick, enabled = enabled, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                        border = BorderStroke(1.dp, if (enabled) colors.accentDeep else colors.mut.copy(alpha = .3f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accentDeep)) {
                        Text(label, maxLines = 1, softWrap = false)
                    }
                }
                if (m != null && stage != "done") action("取消下载", !busy) { ResourceUpdateSession.stop(context, true, onDismiss) }
                action(if (stage == "done") "关闭" else "后台更新", !busy) { hide() }
                when (stage) {
                    "downloading" -> action("暂停下载") { ResourceUpdateSession.stop(context, false) }
                    "available" -> action("下载更新") { download() }
                    "paused" -> action("继续下载") { download() }
                    "error" -> action(if (m == null) "重新检查" else "继续下载") {
                        if (m != null) download() else { ResourceUpdateSession.reset(); ResourceUpdateSession.check(context) { currentNoUpdate() } }
                    }
                }
            }
        },
    )
}
