package cn.qztc.timetable.feature.timetable

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** -1 follows the default; 0 disables only this course. */
@Composable
fun ReminderMinuteChoices(value: Int, onChange: (Int) -> Unit, allowDefault: Boolean = false) {
    var showCustom by rememberSaveable { mutableStateOf(false) }
    var customText by rememberSaveable { mutableStateOf("") }
    val presets = listOf(5, 10, 15)
    Column {
        if (allowDefault) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(value == -1, { onChange(-1) }, label = { Text("跟随默认") })
            FilterChip(value == 0, { onChange(0) }, label = { Text("本课不提醒") })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            presets.forEach { minutes ->
                FilterChip(value == minutes, { onChange(minutes) }, modifier = Modifier.weight(1f),
                    label = { Text("$minutes 分钟", style = MaterialTheme.typography.labelSmall, maxLines = 1) })
            }
            FilterChip(value > 0 && value !in presets, {
                customText = value.takeIf { it > 0 }?.toString().orEmpty()
                showCustom = true
            }, modifier = Modifier.weight(1f), label = {
                Text("自定义", style = MaterialTheme.typography.labelSmall, maxLines = 1)
            })
        }
        if (value > 0 && value !in presets) Text("自定义：提前 $value 分钟", style = MaterialTheme.typography.bodySmall)
    }
    if (showCustom) {
        val minutes = customText.toIntOrNull()
        val valid = minutes != null && minutes in 1..60
        AlertDialog(
            onDismissRequest = { showCustom = false }, title = { Text("自定义提前时间") },
            text = { OutlinedTextField(customText, { customText = it.filter(Char::isDigit).take(3) },
                label = { Text("提前多少分钟") }, suffix = { Text("分钟") },
                supportingText = { Text("请输入 1–60 分钟") },
                isError = customText.isNotEmpty() && !valid, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) },
            confirmButton = { TextButton(enabled = valid, onClick = {
                minutes?.takeIf { it in 1..60 }?.let(onChange)
                showCustom = false
            }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { showCustom = false }) { Text("取消") } },
        )
    }
}
