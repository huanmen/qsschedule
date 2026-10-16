package cn.qztc.timetable.qnu

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Check before leaving the calling page. No academic login is submitted here. */
@Composable
internal fun CampusNetworkDialog(onDismiss: () -> Unit, onReady: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalQztcColors.current
    var revision by remember { mutableIntStateOf(0) }
    var checking by remember { mutableStateOf(true) }
    var vpnMessage by remember { mutableStateOf("") }
    val ready by rememberUpdatedState(onReady)
    LaunchedEffect(revision) {
        checking = true
        val reachable = withContext(Dispatchers.IO) { QnuIntranetProbe.isReachable() }
        if (reachable) ready() else checking = false
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (!checking) revision++ }
    AlertDialog(onDismissRequest = onDismiss, containerColor = colors.solid,
        title = { Text(if (checking) "检查校园网" else "请连接校园网", color = colors.ink) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (checking) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.accent)
                Text("正在检查教务连接…", color = colors.mut)
            } else {
                Text("连接校园网或校园 VPN 后重试。", color = colors.mut)
                TextButton(onClick = {
                    if (EasyConnectBridge.isInstalled(context)) { EasyConnectBridge.open(context); vpnMessage = "连接后返回即可继续" }
                    else vpnMessage = "请先安装校园 VPN"
                }) { Text("打开校园 VPN") }
                if (vpnMessage.isNotBlank()) Text(vpnMessage, color = colors.mut)
            }
        } }, confirmButton = { if (!checking) TextButton(onClick = { revision++ }) { Text("重新检查") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
