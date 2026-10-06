package cn.qztc.timetable.adskip

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.HttpURLConnection
import java.net.URL

object AdSkipRuntime {
    private val state = MutableStateFlow(false)
    val connected = state.asStateFlow()
    internal fun setConnected(value: Boolean) { state.value = value }
    private val backgroundState = MutableStateFlow("未启动")
    val background = backgroundState.asStateFlow()
    internal fun setBackground(value: String) { backgroundState.value = value }
}

data class AdSkipRecord(val timestamp: Long, val app: String, val rule: String, val result: String)

object AdSkipStore {
    private val browserPackages = setOf("com.android.chrome", "com.android.browser", "com.quark.browser")
    private val defaultDisabledPackages = setOf(
        "com.tencent.mobileqq", "com.tencent.qqmail", "com.tencent.mm",
        "com.sankuai.meituan", "com.sankuai.meituan.takeoutnew", "com.jingdong.app.mall",
        "com.taobao.taobao", "com.taobao.shopping", "com.baidu.searchbox",
        "com.google.android.googlequicksearchbox", "com.ss.android.ugc.aweme.lite",
        "com.deepseek.chat", "com.alibaba.qwen", "com.ss.android.ugc.aweme",
        "com.alipay.android.app", "com.wanmei.wmclass", "com.dousheng.app"
    )
    private const val NAME = "ad_skip"
    private const val MAX_RECORDS = 60
    private const val PACK = "local_pack_v1"
    private var cachedText: String? = null
    private var cachedPack: AdSkipRulePack? = null
    fun prefs(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    fun installed(context: Context): Boolean = prefs(context).getBoolean("installed", prefs(context).getBoolean("enabled", false) || localPack(context) != null)
    fun enabled(context: Context): Boolean = installed(context) && prefs(context).getBoolean("enabled", false)
    fun feedbackEnabled(context: Context) = prefs(context).getBoolean("feedback", true)
    fun foregroundEnabled(context: Context) = prefs(context).getBoolean("foreground", true)
    fun splashOnly(context: Context) = prefs(context).getBoolean("splash_only", true)
    fun setSplashOnly(context: Context, value: Boolean) { prefs(context).edit().putBoolean("splash_only", value).apply() }
    fun pluginNeedsUpdate(context: Context, latest: Int = 2) = installed(context) && prefs(context).getInt("plugin_version", 1) < latest
    fun updatePlugin(context: Context, version: Int = 2) { prefs(context).edit().putInt("plugin_version", version).apply() }
    fun applicationExcluded(context: Context, packageName: String) = !prefs(context).getBoolean("app:" + packageName, !defaultDisabled(context, packageName))
    fun setFeedback(context: Context, value: Boolean) { prefs(context).edit().putBoolean("feedback", value).apply() }
    fun setForeground(context: Context, value: Boolean) { prefs(context).edit().putBoolean("foreground", value).apply() }
    fun setEnabled(context: Context, value: Boolean) {
        require(!value || installed(context)) { "请先安装广告辅助插件" }
        prefs(context).edit().putBoolean("enabled", value).apply()
    }

    fun genericEnabled(context: Context) = installed(context) && prefs(context).getBoolean("rule:" + AdSkipGenericRules.ID, true)
    fun appEnabled(context: Context, packageName: String): Boolean = installed(context) && packageName !in browserPackages && AdSkipRules.supportedPackage(packageName) &&
        (rules(context).any { it.packageName == packageName } || (genericEnabled(context) && packageName in AdSkipApplications.labels(context))) &&
        prefs(context).getBoolean("app:$packageName", !defaultDisabled(context, packageName))
    fun setAppEnabled(context: Context, packageName: String, value: Boolean) {
        require(AdSkipRules.supportedPackage(packageName))
        prefs(context).edit().putBoolean("app:$packageName", value).apply()
    }
    private fun defaultDisabled(context: Context, packageName: String): Boolean {
        if (packageName in defaultDisabledPackages || packageName.startsWith("com.android.") ||
            packageName.startsWith("com.google.android.") || packageName.startsWith("com.miui.") ||
            packageName.startsWith("com.xiaomi.")) return true
        val info = runCatching { context.packageManager.getApplicationInfo(packageName, 0) }.getOrNull() ?: return false
        if (android.content.pm.ApplicationInfo.CATEGORY_GAME == info.category) return true
        val label = runCatching { context.packageManager.getApplicationLabel(info).toString() }.getOrDefault("")
        return label.contains(Regex("(?i)deepseek|qwen|千问|通义|豆包|ai|搜索|浏览器"))
    }
    @Synchronized
    fun localPack(context: Context): AdSkipRulePack? {
        val raw = prefs(context).getString(PACK, null)
        if (raw != cachedText) {
            cachedPack = raw?.let { runCatching { AdSkipRules.decode(it, allowExternalApps = true) }.getOrNull() }
            cachedText = raw
        }
        return cachedPack
    }
    fun rules(context: Context): List<AdSkipRule> = if (!installed(context)) emptyList() else
        (AdSkipRules.bundled.rules + localPack(context)?.rules.orEmpty()).distinctBy { it.id }
    fun rulesFor(context: Context, packageName: String): List<AdSkipRule> {
        if (!appEnabled(context, packageName)) return emptyList()
        val specific = rules(context).filter { it.packageName == packageName }
        val generic = if (genericEnabled(context) && packageName in AdSkipApplications.labels(context))
            listOf(AdSkipGenericRules.rule(packageName, AdSkipApplications.labels(context)[packageName])) else emptyList()
        return specific + generic
    }
    fun appNames(context: Context): Map<String, String> = AdSkipRules.appNames + rules(context).associate {
        it.packageName to (AdSkipRules.appNames[it.packageName] ?: it.appLabel ?: it.packageName)
    }
    fun activePackages(context: Context): List<String> = if (!enabled(context)) emptyList() else
        (rules(context).filter { appEnabled(context, it.packageName) && ruleEnabled(context, it) }.map { it.packageName } +
            if (genericEnabled(context)) AdSkipApplications.labels(context).keys.filter { appEnabled(context, it) } else emptyList()).distinct()
    fun ruleEnabled(context: Context, rule: AdSkipRule): Boolean =
        !requiresReimport(rule) && prefs(context).getBoolean("rule:${rule.id}", rule.id == AdSkipGenericRules.ID || AdSkipRules.bundled.rules.any { it == rule })
    fun requiresReimport(rule: AdSkipRule) = rule.id.startsWith("import-") && rule.adapterVersion < 2
    fun setRuleEnabled(context: Context, rule: AdSkipRule, value: Boolean) {
        require(!value || !requiresReimport(rule)) { "请重新导入原规则文件，以恢复完整的匹配条件" }
        prefs(context).edit().putBoolean("rule:${rule.id}", value).apply()
    }
    /** Validate everything before atomic replacement. Imports never auto-enable any rule. */
    fun installPack(context: Context, pack: AdSkipRulePack, issues: List<AdSkipImportIssue> = emptyList()) {
        AdSkipRules.validate(pack, allowExternalApps = true)
        val editor = prefs(context).edit()
        prefs(context).all.keys.filter { it.startsWith("rule:") || it.startsWith("app:") }.forEach(editor::remove)
        pack.rules.forEach { editor.putBoolean("rule:${it.id}", false) }
        pack.rules.map { it.packageName }.distinct().forEach { editor.putBoolean("app:$it", false) }
        editor.putBoolean("installed", true).putBoolean("enabled", false)
        editor.putBoolean("rule:" + AdSkipGenericRules.ID, false)
        editor.putString("import_issues", Json.encodeToString(issues))
        editor.putString(PACK, AdSkipRules.encode(pack)).apply()
    }
    fun importIssues(context: Context): List<AdSkipImportIssue> = runCatching {
        Json.decodeFromString<List<AdSkipImportIssue>>(prefs(context).getString("import_issues", "[]") ?: "[]")
    }.getOrDefault(emptyList()) + localPack(context)?.rules.orEmpty().filter(::requiresReimport).map {
        AdSkipImportIssue(it.appLabel ?: it.packageName, it.name, "旧适配器可能遗漏条件，已暂停；请重新导入原文件")
    }
    /** Additions preserve built-ins and existing choices. New or changed rules start disabled. */
    fun addRules(context: Context, pack: AdSkipRulePack, issues: List<AdSkipImportIssue> = emptyList(), pluginVersion: Int? = null) {
        require(installed(context) || pluginVersion != null)
        require(pluginVersion == null || pluginVersion > 0)
        AdSkipRules.validate(pack, allowExternalApps = true)
        val builtin = AdSkipRules.bundled.rules.associateBy { it.id }
        pack.rules.forEach { require(it.id !in builtin || builtin[it.id] == it) { "规则 ID 与内置规则重复" } }
        val additions = pack.rules.filter { it.id !in builtin }
        val old = localPack(context)?.rules.orEmpty().filter { it.id !in builtin }
        val merged = pack.copy(name = "我的规则", rules = (additions + old).distinctBy { it.id })
        if (merged.rules.isNotEmpty()) AdSkipRules.validate(merged, allowExternalApps = true)
        val editor = prefs(context).edit()
        if (pluginVersion != null) {
            if (!installed(context)) editor.putBoolean("enabled", false)
            editor.putBoolean("installed", true).putInt("plugin_version", pluginVersion)
        }
        additions.filter { rule -> old.none { it == rule } }.forEach { editor.putBoolean("rule:" + it.id, false) }
        if (merged.rules.isNotEmpty()) editor.putString(PACK, AdSkipRules.encode(merged))
        editor.putString("import_issues", Json.encodeToString(issues)).apply()
    }
    fun removeRule(context: Context, rule: AdSkipRule) {
        require(AdSkipRules.bundled.rules.none { it.id == rule.id } && rule.id != AdSkipGenericRules.ID)
        val pack = localPack(context) ?: return
        val remaining = pack.rules.filter { it.id != rule.id }
        val edit = prefs(context).edit().remove("rule:" + rule.id)
        if (remaining.isEmpty()) edit.remove(PACK) else edit.putString(PACK, AdSkipRules.encode(pack.copy(rules = remaining)))
        edit.apply()
    }
    fun updateRule(context: Context, old: AdSkipRule, changed: AdSkipRule) {
        require(old.id == changed.id && old.packageName == changed.packageName) { "规则所属应用和标识不能更改" }
        require(AdSkipRules.bundled.rules.none { it.id == old.id }) { "内置规则不能修改" }
        val pack = localPack(context) ?: error("规则不存在")
        require(pack.rules.any { it.id == old.id }) { "规则不存在" }
        val updated = pack.copy(rules = pack.rules.map { if (it.id == old.id) changed else it })
        AdSkipRules.validate(updated, allowExternalApps = true)
        prefs(context).edit().putString(PACK, AdSkipRules.encode(updated)).putBoolean("rule:" + old.id, false).apply()
    }
    fun restoreBundled(context: Context) {
        val editor = prefs(context).edit().remove(PACK).remove("import_issues")
        prefs(context).all.keys.filter { it.startsWith("rule:") || it.startsWith("app:") }.forEach(editor::remove)
        editor.putBoolean("installed", true).putBoolean("enabled", false)
        editor.putInt("plugin_version", 2)
        editor.apply()
    }

    /** Remove all installed plugin data. The host engine stays in the APK but cannot run. */
    fun removeDownloadedPlugin(context: Context) {
        prefs(context).edit().clear().putBoolean("installed", false).putBoolean("enabled", false).apply()
        cachedText = null
        cachedPack = null
    }

    /** Download a bounded v1 rule pack. Installation remains a separate, user-confirmed step. */
    fun downloadPack(url: String, installedPackages: Map<String, String> = emptyMap(), open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }): AdSkipImportPreview {
        val target = URL(url.trim())
        require(target.protocol.equals("https", ignoreCase = true)) { "插件下载地址必须使用 HTTPS" }
        require(target.host.isNotBlank() && target.userInfo == null) { "下载地址无效" }
        val connection = open(target).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            instanceFollowRedirects = false
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "QztcTimetable-PluginStore/1")
        }
        try {
            require(connection.responseCode !in 300..399) { "请使用插件文件的 HTTPS 直链，暂不接受跳转地址" }
            require(connection.responseCode in 200..299) { "下载失败：HTTP ${connection.responseCode}" }
            val declared = connection.contentLengthLong
            require(declared <= AdSkipRules.MAX_BYTES || declared < 0) { "插件文件超过 4 MB" }
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (output.size() <= AdSkipRules.MAX_BYTES) {
                    val count = input.read(buffer, 0, minOf(buffer.size, AdSkipRules.MAX_BYTES + 1 - output.size()))
                    if (count <= 0) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            require(bytes.size <= AdSkipRules.MAX_BYTES) { "插件文件超过 4 MB" }
            return AdSkipImport.parse(bytes.toString(Charsets.UTF_8), installedPackages)
        } finally {
            connection.disconnect()
        }
    }
    fun serviceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val component = ComponentName(context, AdSkipAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
    }
    @Synchronized
    fun record(context: Context, rule: AdSkipRule, result: AdSkipResult) {
        if (!installed(context)) return
        val app = AdSkipRules.appNames[rule.packageName] ?: rule.appLabel ?: rule.packageName
        val entry = "${System.currentTimeMillis()}|$app|${rule.name}|${result.label}"
        val old = prefs(context).getString("records", "").orEmpty().lineSequence().filter(String::isNotBlank)
        prefs(context).edit().putString("records", (sequenceOf(entry) + old).take(MAX_RECORDS).joinToString("\n")).apply()
    }
    fun records(context: Context): List<AdSkipRecord> = prefs(context).getString("records", "").orEmpty()
        .lineSequence().mapNotNull { line ->
            val fields = line.split('|')
            if (fields.size != 4) null else fields[0].toLongOrNull()?.let { AdSkipRecord(it, fields[1], fields[2], fields[3]) }
        }.toList()
    fun clearRecords(context: Context) { prefs(context).edit().remove("records").apply() }
    fun recordOutcome(context: Context, rule: AdSkipRule, result: AdSkipResult) {
        if (!installed(context)) return
        val outcome = AdSkipOutcome.label(result) ?: return
        val app = AdSkipRules.appNames[rule.packageName] ?: rule.appLabel ?: rule.packageName
        val entry = System.currentTimeMillis().toString() + "|" + app + "|" + rule.name + "|" + outcome
        val old = prefs(context).getString("outcomes", "").orEmpty().lineSequence().filter(String::isNotBlank)
        prefs(context).edit().putString("outcomes", (sequenceOf(entry) + old).take(MAX_RECORDS).joinToString("\n")).apply()
    }
    fun outcomes(context: Context): List<AdSkipRecord> = prefs(context).getString("outcomes", "").orEmpty().lineSequence().mapNotNull {
        val f = it.split('|')
        if (f.size != 4 || f[3] !in setOf("成功", "失败")) null else f[0].toLongOrNull()?.let { time -> AdSkipRecord(time, f[1], f[2], f[3]) }
    }.toList()
    fun clearOutcomes(context: Context) { prefs(context).edit().remove("outcomes").apply() }
    fun migrateDiagnostics(context: Context) {
        if (prefs(context).getInt("diagnostics_version", 0) < 2) {
            prefs(context).edit().remove("records").putInt("diagnostics_version", 2).apply()
        }
    }
}
