package cn.qztc.timetable.qnu

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable

/** Check before opening a query, so an unconfigured account never starts a request. */
@Composable
internal fun rememberEducationAccountGate(
    configured: Boolean,
    onConfigure: () -> Unit,
): (() -> Unit) -> Unit {
    var required by rememberSaveable { mutableStateOf(false) }
    if (required) AlertDialog(
        onDismissRequest = { required = false },
        title = { Text("请先配置教务账号") },
        confirmButton = { TextButton(onClick = { required = false; onConfigure() }) { Text("去配置") } },
        dismissButton = { TextButton(onClick = { required = false }) { Text("取消") } },
    )
    return { action -> if (configured) action() else required = true }
}
