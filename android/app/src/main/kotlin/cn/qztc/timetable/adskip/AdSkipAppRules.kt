package cn.qztc.timetable.adskip

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.ui.theme.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable
internal fun AdSkipApplicationsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val revision = rememberAdSkipRevision()
    val apps = remember(revision) { AdSkipApplications.labels(context).toList().sortedBy { it.second } }
    val colors = LocalQztcColors.current
    var query by rememberSaveable { mutableStateOf("") }
    AdSkipPage("应用范围", onBack) {
        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("搜索应用") }, leadingIcon = { Icon(Icons.Outlined.Search, null) })
        Text("默认全部应用；关闭不需要处理的应用。", color = colors.mut, style = QztcTypography.meta)
        AdSkipCard {
            apps.filter { (pkg, name) -> query.isBlank() || name.contains(query, true) || pkg.contains(query, true) }.forEachIndexed { index, (pkg, name) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.weight(1f), color = colors.ink, style = QztcTypography.body)
                    Switch(checked = !AdSkipStore.applicationExcluded(context, pkg), onCheckedChange = { AdSkipStore.setAppEnabled(context, pkg, it) })
                }
                if (index < apps.lastIndex) AdSkipDivider()
            }
        }
    }
}

@Composable
internal fun AdSkipRuleList() {
    val context = LocalContext.current
    val revision = rememberAdSkipRevision()
    val colors = LocalQztcColors.current
    val rules = remember(revision) { AdSkipStore.rules(context) }
    var importing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AdSkipRule?>(null) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("规则", Modifier.weight(1f), style = QztcTypography.section, color = colors.mut)
        TextButton(onClick = { importing = true }) { Icon(Icons.Outlined.Add, null); Text("添加") }
    }
    AdSkipCard {
        AdSkipToggle("通用广告跳过", Icons.Outlined.AutoAwesome, AdSkipStore.genericEnabled(context)) {
            AdSkipStore.setRuleEnabled(context, AdSkipGenericRules.rule("example.app"), it)
        }
        if (rules.isNotEmpty()) AdSkipDivider()
        rules.forEachIndexed { index, rule ->
            val builtIn = AdSkipRules.bundled.rules.any { it.id == rule.id }
            val title = (AdSkipRules.appNames[rule.packageName] ?: rule.appLabel ?: rule.packageName) + " · " + rule.name
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = QztcTypography.body, color = colors.ink)
                    Text(if (AdSkipStore.requiresReimport(rule)) "请重新导入原规则" else if (builtIn) "内置" else "自定义", style = QztcTypography.meta, color = colors.mut)
                }
                if (!builtIn) {
                    IconButton(onClick = { editing = rule }) { Icon(Icons.Outlined.Edit, "编辑规则", tint = colors.mut) }
                    IconButton(onClick = { AdSkipStore.removeRule(context, rule) }) { Icon(Icons.Outlined.DeleteOutline, "删除规则", tint = colors.mut) }
                }
                Switch(checked = AdSkipStore.ruleEnabled(context, rule), enabled = !AdSkipStore.requiresReimport(rule), onCheckedChange = { AdSkipStore.setRuleEnabled(context, rule, it) })
            }
            if (index < rules.lastIndex) AdSkipDivider()
        }
    }
    if (importing) AdSkipRuleImportDialog(onDismiss = { importing = false }) { pack, issues -> AdSkipStore.addRules(context, pack, issues) }
    editing?.let { rule ->
        var name by remember(rule) { mutableStateOf(rule.name) }
        var label by remember(rule) { mutableStateOf(rule.appLabel.orEmpty()) }
        var advanced by remember(rule) { mutableStateOf(false) }
        var definition by remember(rule) { mutableStateOf(Json { prettyPrint = true }.encodeToString(rule)) }
        var error by remember(rule) { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text("编辑规则") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("规则名称") }, singleLine = true)
                OutlinedTextField(label, { label = it }, label = { Text("应用名称") }, singleLine = true)
                TextButton(onClick = { advanced = !advanced }) { Text("匹配条件") }
                if (advanced) OutlinedTextField(definition, { if (it.length <= 32_000) definition = it }, modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp), label = { Text("规则 JSON") })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } }, confirmButton = { TextButton(onClick = {
                runCatching {
                    val changed = Json.decodeFromString<AdSkipRule>(definition).copy(name = name.trim(), appLabel = label.trim().ifBlank { null })
                    AdSkipStore.updateRule(context, rule, changed)
                }
                    .onSuccess { editing = null }.onFailure { error = it.message ?: "保存失败" }
            }) { Text("保存") } }, dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } })
    }
}
