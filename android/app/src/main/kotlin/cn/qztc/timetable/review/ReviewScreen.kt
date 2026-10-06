package cn.qztc.timetable.review

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.adskip.AdSkipPage
import cn.qztc.timetable.adskip.AdSkipCard
import cn.qztc.timetable.core.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ReviewScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { ReviewStore(context) }
    val initial = remember { store.draft() }
    var scene by rememberSaveable { mutableStateOf(initial.scene) }
    var subject by rememberSaveable { mutableStateOf(initial.subject) }
    var facts by rememberSaveable { mutableStateOf(initial.facts) }
    var tone by rememberSaveable { mutableStateOf(initial.tone) }
    var minimum by rememberSaveable { mutableStateOf(initial.minimum.toString()) }
    var maximum by rememberSaveable { mutableStateOf(initial.maximum.toString()) }
    var requirements by rememberSaveable { mutableStateOf(initial.requirements) }
    var output by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var configuring by remember { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var template by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val colors = LocalQztcColors.current
    val clipboard = LocalClipboardManager.current
    fun draft() = ReviewDraft(scene, subject.trim(), facts.trim(), tone, minimum.toIntOrNull() ?: 0, maximum.toIntOrNull() ?: 0, requirements.trim())
    val currentDraft by rememberUpdatedState(draft())
    DisposableEffect(store) {
        onDispose { if (store.installed()) store.saveDraft(currentDraft) }
    }
    AdSkipPage("AI 好评助手", onBack, actions = {
        IconButton(onClick = { configuring = true }, enabled = !busy) { Icon(Icons.Outlined.Settings, "模型设置", tint = colors.mut) }
    }) {
        if (!store.installed()) Text("请先在插件市场下载", color = colors.mut)
        AdSkipCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReviewDraft.scenes.forEach { value -> FilterChip(selected = scene == value, onClick = { scene = value }, label = { Text(value) }, enabled = !busy) }
                }
                OutlinedTextField(subject, { if (it.length <= 100) subject = it }, modifier = Modifier.fillMaxWidth(), label = { Text("店名 / 商品（可选）") }, singleLine = true, enabled = !busy)
                OutlinedTextField(facts, { if (it.length <= 2000) facts = it }, modifier = Modifier.fillMaxWidth(), label = { Text("实际体验") }, placeholder = { Text("写两三点自己的感受，满意或不足都可以") }, minLines = 3, maxLines = 6, enabled = !busy)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { output = ReviewText.starter(scene); template = true; message = "替换【】中的内容后即可使用" }, enabled = !busy) { Text("通用模板") }
                    TextButton(onClick = { advanced = !advanced }, enabled = !busy) { Text(if (advanced) "收起定制" else "更多定制") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(minimum, { if (it.length <= 3 && it.all(Char::isDigit)) minimum = it }, Modifier.weight(1f), label = { Text("最少字数") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !busy)
                    OutlinedTextField(maximum, { if (it.length <= 3 && it.all(Char::isDigit)) maximum = it }, Modifier.weight(1f), label = { Text("最多字数") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !busy)
                }
                if (advanced) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReviewDraft.tones.forEach { value -> FilterChip(tone == value, { tone = value }, label = { Text(value) }, enabled = !busy) }
                    }
                    OutlinedTextField(requirements, { if (it.length <= 600) requirements = it }, Modifier.fillMaxWidth(), label = { Text("额外要求（可选）") }, placeholder = { Text("例如：不写店名，保留口味偏甜的感受") }, maxLines = 4, enabled = !busy)
                }
                Text("只发送本页素材到配置的模型；生成后自行确认、复制。", color = colors.mut, style = QztcTypography.meta)
                Button(onClick = {
                    val value = draft()
                    val config = store.connection()
                    val issue = runCatching { value.validate(); config.validate() }.exceptionOrNull()
                    if (issue != null) { message = issue.message; if (config.endpoint.isBlank()) configuring = true }
                    else scope.launch {
                        busy = true; message = null; store.saveDraft(value)
                        try {
                            val result = ReviewClient.generate(value, config)
                            output = result.text; template = false
                            if (!result.withinRange) message = "重写后仍未达到字数范围，请调整素材或编辑正文"
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (error: Exception) { message = if (error is IllegalArgumentException || error is IllegalStateException) error.message else "连接模型失败，请检查网络与模型设置" }
                        finally { busy = false }
                    }
                }, enabled = !busy && store.installed(), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = colors.accentDeep)) {
                    if (busy) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.solid); Spacer(Modifier.width(8.dp)); Text("生成并校准字数中") }
                    else Text(if (output.isBlank() || template) "生成评价" else "换一种写法")
                }
            }
        }
        message?.let { Text(it, style = QztcTypography.meta, color = colors.mut) }
        if (output.isNotEmpty()) AdSkipCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (template) "通用模板 · 请替换占位内容" else "评价草稿", style = QztcTypography.courseTitle, color = colors.ink)
                OutlinedTextField(output, { if (it.length <= 5000) output = it }, Modifier.fillMaxWidth(), minLines = 4, maxLines = 12, enabled = !busy)
                val count = ReviewText.count(output)
                val inRange = minimum.toIntOrNull()?.let { lo -> maximum.toIntOrNull()?.let { hi -> count in lo..hi } } == true
                Text("$count 字 · 含标点、不含空白" + if (inRange || template) "" else " · 超出范围", style = QztcTypography.meta, color = colors.mut)
                TextButton(onClick = { clipboard.setText(AnnotatedString(output)); message = "已复制" }, enabled = !busy) { Text("复制") }
            }
        }
    }
    if (configuring) ReviewSettingsDialog(store, onDismiss = { configuring = false }) { configuring = false; message = "模型设置已保存" }
}

@Composable
private fun ReviewSettingsDialog(store: ReviewStore, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val config = remember { store.connection() }
    var endpoint by remember { mutableStateOf(config.endpoint) }
    var model by remember { mutableStateOf(config.model) }
    var secret by remember { mutableStateOf(config.apiKey) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("模型设置") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("支持 Chat Completions 兼容接口。填写基础地址或完整聊天地址，密钥加密保存在本机。", style = QztcTypography.meta)
            OutlinedTextField(endpoint, { if (it.length <= 600) endpoint = it }, label = { Text("API 地址") }, placeholder = { Text("https://…/v1") }, maxLines = 2, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            OutlinedTextField(model, { if (it.length <= 100) model = it }, label = { Text("模型名称") }, singleLine = true)
            OutlinedTextField(secret, { if (it.length <= 4096) secret = it }, label = { Text("API 密钥") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        runCatching { store.saveConnection(ReviewConnection(endpoint.trim(), model.trim(), secret.trim())) }.onSuccess { onSaved() }.onFailure { error = it.message ?: "保存失败" }
    }) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
