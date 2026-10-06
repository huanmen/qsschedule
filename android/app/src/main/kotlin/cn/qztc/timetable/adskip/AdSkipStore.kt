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
}

data class AdSkipRecord(val timestamp: Long, val app: String, val rule: String, val result: String)

object AdSkipStore {
    private const val NAME = "ad_skip"
    private const val MAX_RECORDS = 60
    private const val PACK = "local_pack_v1"
    private var cachedText: String? = null
    private var cachedPack: AdSkipRulePack? = null
    fun prefs(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    fun installed(context: Context): Boolean = prefs(context).getBoolean("installed", prefs(context).getBoolean("enabled", false) || localPack(context) != null)
    fun enabled(context: Context): Boolean = installed(context) && prefs(context).getBoolean("enabled", false)
    fun setEnabled(context: Context, value: Boolean) {
        require(!value || installed(context)) { "请先安装广告辅助插件" }
        prefs(context).edit().putBoolean("enabled", value).apply()
    }

    fun appEnabled(context: Context, packageName: String): Boolean = AdSkipRules.supportedPackage(packageName) && rules(context).any { it.packageName == packageName } &&
        prefs(context).getBoolean("app:$packageName", AdSkipRules.bundled.rules.any { it.packageName == packageName })
    fun setAppEnabled(context: Context, packageName: String, value: Boolean) {
        require(AdSkipRules.supportedPackage(packageName))
        prefs(context).edit().putBoolean("app:$packageName", value).apply()
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
        localPack(context)?.rules ?: AdSkipRules.bundled.rules
    fun appNames(context: Context): Map<String, String> = AdSkipRules.appNames + rules(context).associate {
        it.packageName to (AdSkipRules.appNames[it.packageName] ?: it.appLabel ?: it.packageName)
    }
    fun activePackages(context: Context): List<String> = if (!enabled(context)) emptyList() else
        rules(context).filter { appEnabled(context, it.packageName) && ruleEnabled(context, it) }.map { it.packageName }.distinct()
    fun ruleEnabled(context: Context, rule: AdSkipRule): Boolean =
        prefs(context).getBoolean("rule:${rule.id}", AdSkipRules.bundled.rules.any { it == rule })
    fun setRuleEnabled(context: Context, rule: AdSkipRule, value: Boolean) {
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
        editor.putString("import_issues", Json.encodeToString(issues))
        editor.putString(PACK, AdSkipRules.encode(pack)).apply()
    }
    fun importIssues(context: Context): List<AdSkipImportIssue> = runCatching {
        Json.decodeFromString<List<AdSkipImportIssue>>(prefs(context).getString("import_issues", "[]") ?: "[]")
    }.getOrDefault(emptyList())
    fun restoreBundled(context: Context) {
        val editor = prefs(context).edit().remove(PACK).remove("import_issues")
        prefs(context).all.keys.filter { it.startsWith("rule:") || it.startsWith("app:") }.forEach(editor::remove)
        editor.putBoolean("installed", true).putBoolean("enabled", false)
        editor.apply()
    }

    /** Remove all installed plugin data. The host engine stays in the APK but cannot run. */
    fun removeDownloadedPlugin(context: Context) {
        prefs(context).edit().clear().putBoolean("installed", false).putBoolean("enabled", false).apply()
        cachedText = null
        cachedPack = null
    }

    /** Download a bounded v1 rule pack. Installation remains a separate, user-confirmed step. */
    fun downloadPack(url: String, open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }): AdSkipImportPreview {
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
            return AdSkipImport.parse(bytes.toString(Charsets.UTF_8))
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
}
