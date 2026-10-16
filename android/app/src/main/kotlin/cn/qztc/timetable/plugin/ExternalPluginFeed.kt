package cn.qztc.timetable.plugin

import android.content.Context
import cn.qztc.timetable.BuildConfig
import cn.qztc.timetable.adskip.AdSkipRulePack
import cn.qztc.timetable.adskip.AdSkipRules
import cn.qztc.timetable.adskip.AdSkipStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

@Serializable
data class ExternalPlugin(
    val id: String,
    val name: String,
    val summary: String,
    val version: Int,
    val kind: String,
    val rulesUrl: String,
    val minAppVersion: Int = 1,
    val minAdapterVersion: Int = 1,
)

@Serializable
data class ExternalPluginFeed(val schemaVersion: Int, val plugins: List<ExternalPlugin>)

@Serializable
data class PluginSource(val url: String, val plugins: List<ExternalPlugin>)

@Serializable
private data class InstalledExternalPlugin(val version: Int, val ruleIds: List<String>)

/** Remote catalogs describe rule data only. Native code and APK installation are never accepted. */
object ExternalPluginFeeds {
    private const val MAX_FEED_BYTES = 64 * 1024
    private val json = Json { ignoreUnknownKeys = false }
    private val pluginId = Regex("[a-z][a-z0-9.-]{2,63}")

    fun parse(url: String, text: String): PluginSource {
        https(url)
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_FEED_BYTES) { "目录文件过大" }
        val feed = json.decodeFromString<ExternalPluginFeed>(text)
        require(feed.schemaVersion == 1 && feed.plugins.size <= 30) { "不支持的插件目录" }
        require(feed.plugins.map { it.id }.distinct().size == feed.plugins.size) { "插件 ID 重复" }
        feed.plugins.forEach {
            require(it.id.matches(pluginId) && it.name.length in 1..40 && it.summary.length <= 80 && it.version > 0) { "插件信息无效" }
            require(it.kind == "adskip-rule-pack") { "仅支持广告规则数据包" }
            require(it.minAppVersion in 1..BuildConfig.VERSION_CODE && it.minAdapterVersion in 1..3) {
                "此规则包需要更新的宿主或适配器"
            }
            https(it.rulesUrl)
        }
        return PluginSource(url, feed.plugins)
    }

    suspend fun fetch(url: String): PluginSource = withContext(Dispatchers.IO) {
        val target = https(url)
        val connection = (target.toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = false
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            require(connection.responseCode == 200) { "目录获取失败" }
            require(connection.contentLengthLong < 0 || connection.contentLengthLong <= MAX_FEED_BYTES) { "目录文件过大" }
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (output.size() <= MAX_FEED_BYTES) {
                    val n = input.read(buffer, 0, minOf(buffer.size, MAX_FEED_BYTES + 1 - output.size()))
                    if (n < 0) break
                    output.write(buffer, 0, n)
                }
                output.toByteArray()
            }
            require(bytes.size <= MAX_FEED_BYTES) { "目录文件过大" }
            parse(url, bytes.toString(Charsets.UTF_8))
        } finally { connection.disconnect() }
    }

    private fun https(value: String): URI {
        val uri = URI(value.trim())
        require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null &&
            uri.fragment == null && uri.port in listOf(-1, 443)) { "仅支持 HTTPS 直链" }
        return uri
    }

    private fun prefs(context: Context) = context.getSharedPreferences("external_plugin_feeds", Context.MODE_PRIVATE)
    fun sources(context: Context): List<PluginSource> = runCatching {
        json.decodeFromString<List<PluginSource>>(prefs(context).getString("sources", "[]") ?: "[]")
    }.getOrDefault(emptyList())

    fun save(context: Context, source: PluginSource) {
        require(source.plugins.none { entry -> PluginRegistry.plugins.any { it.descriptor.id == entry.id } }) {
            "插件 ID 与内置插件重复"
        }
        val existing = sources(context)
        require(source.plugins.none { entry -> existing.any { other ->
            other.url != source.url && other.plugins.any { it.id == entry.id }
        } }) {
            "插件 ID 已被另一个来源使用"
        }
        val previous = existing.firstOrNull { it.url == source.url }
        require(previous == null || previous.plugins.filter { installedVersion(context, it.id) > 0 }
            .all { old -> source.plugins.any { it.id == old.id } }) { "请先删除已安装的旧插件" }
        require(existing.any { it.url == source.url } || existing.size < 10) { "最多添加 10 个插件来源" }
        val updated = existing.filterNot { it.url == source.url } + source
        prefs(context).edit().putString("sources", json.encodeToString(updated)).apply()
    }

    fun removeSource(context: Context, url: String) {
        require(sources(context).filter { it.url == url }.flatMap { it.plugins }
            .none { installedVersion(context, it.id) > 0 }) { "请先删除此来源已安装的规则" }
        prefs(context).edit().putString("sources", json.encodeToString(sources(context).filterNot { it.url == url })).apply()
    }

    private fun installed(context: Context, id: String): InstalledExternalPlugin? = runCatching {
        json.decodeFromString<InstalledExternalPlugin>(prefs(context).getString("installed:$id", null) ?: return null)
    }.getOrNull()
    fun installedVersion(context: Context, id: String): Int = installed(context, id)?.version ?: 0

    suspend fun install(context: Context, entry: ExternalPlugin): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val preview = AdSkipStore.downloadPack(entry.rulesUrl)
            require(preview.issues.isEmpty()) { "有 ${preview.issues.size} 条规则无法完整适配，请先在广告辅助中检查原文件" }
            val pack: AdSkipRulePack = preview.pack ?: error("规则包没有兼容内容")
            AdSkipRules.validate(pack, allowExternalApps = true)
            val previous = installed(context, entry.id)?.ruleIds.orEmpty()
            val otherIds = AdSkipStore.rules(context).map { it.id }.toSet() - previous.toSet()
            require(pack.rules.none { it.id in otherIds }) { "规则 ID 与已有规则重复" }
            AdSkipStore.addRules(context, pack, pluginVersion = if (AdSkipStore.installed(context)) null else 2)
            previous.filterNot { old -> pack.rules.any { it.id == old } }.forEach { id ->
                AdSkipStore.localPack(context)?.rules?.firstOrNull { it.id == id }?.let { AdSkipStore.removeRule(context, it) }
            }
            prefs(context).edit().putString("installed:${entry.id}",
                json.encodeToString(InstalledExternalPlugin(entry.version, pack.rules.map { it.id }))).apply()
        }
    }

    fun uninstall(context: Context, id: String) {
        installed(context, id)?.ruleIds.orEmpty().forEach { ruleId ->
            AdSkipStore.localPack(context)?.rules?.firstOrNull { it.id == ruleId }?.let { AdSkipStore.removeRule(context, it) }
        }
        prefs(context).edit().remove("installed:$id").apply()
    }
}
