package cn.qztc.timetable.plugin

import android.content.Context
import cn.qztc.timetable.BuildConfig
import cn.qztc.timetable.distribution.DistributionClient
import cn.qztc.timetable.update.AppUpdateClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream

@Serializable
data class MarketPlugin(val id: String, val name: String, val summary: String, val version: Int, val minAppVersion: Int)
data class OfficialCatalog(val plugins: List<MarketPlugin>, val rules: List<ExternalPlugin>, val activeRules: Set<String>)
@Serializable
private data class CatalogWire(val schemaVersion: Int, val plugins: List<MarketPlugin>, val rulePlugins: List<ExternalPlugin>)

/** A catalog only controls already supported host features and rule data. */
object OfficialPluginCatalog {
    const val RULES_URL = DistributionClient.BASE_URL + "/api/plugin-rules-catalog"
    private val json = Json { ignoreUnknownKeys = true }
    private fun prefs(context: Context) = context.getSharedPreferences("official-plugin-catalog", Context.MODE_PRIVATE)

    internal fun parse(raw: String): OfficialCatalog {
        require(raw.toByteArray(Charsets.UTF_8).size <= 65536)
        val value = json.decodeFromString<CatalogWire>(raw)
        require(value.schemaVersion == 1 && value.plugins.size <= 30)
        val plugins = value.plugins
        plugins.forEach {
            require(it.id.matches(Regex("[a-z][a-z0-9.-]{2,63}")) && it.name.length in 1..40 && it.summary.length <= 80 && it.version > 0 && it.minAppVersion > 0)
        }
        require(plugins.map { it.id }.distinct().size == plugins.size)
        val rules = value.rulePlugins
        require(rules.size <= 30)
        // Unsupported releases are omitted individually; one future plugin must not hide the catalog.
        require(rules.map { it.id }.distinct().size == rules.size)
        val compatible = rules.filter { it.minAppVersion <= BuildConfig.VERSION_CODE && it.minAdapterVersion <= 3 }
        val feed = ExternalPluginFeeds.parse(RULES_URL, json.encodeToString(ExternalPluginFeed(1, compatible)))
        require(feed.plugins.none { entry -> PluginRegistry.plugins.any { it.descriptor.id == entry.id } })
        return OfficialCatalog(plugins, feed.plugins, feed.plugins.map { it.id }.toSet())
    }

    fun cached(context: Context): OfficialCatalog? = runCatching {
        parse(prefs(context).getString("catalog", null) ?: return null)
    }.getOrNull()?.let { current ->
        val archive = runCatching { ExternalPluginFeeds.parse(RULES_URL,
            prefs(context).getString("archive", "") ?: "").plugins }.getOrDefault(emptyList())
        current.copy(rules = current.rules + archive.filter { old -> old.id !in current.activeRules && ExternalPluginFeeds.installedVersion(context, old.id) > 0 })
    }

    suspend fun refresh(context: Context): OfficialCatalog? = withContext(Dispatchers.IO) {
        try {
            val connection = AppUpdateClient.openTrustedConnection(DistributionClient.BASE_URL + "/api/plugins", "GET", "application/json")
            val raw = try {
                require(connection.responseCode == 200)
                val out = ByteArrayOutputStream()
                connection.inputStream.use { AppUpdateClient.copyLimited(it, out, 65536) }
                out.toString("UTF-8")
            } finally { connection.disconnect() }
            val result = parse(raw)
            val old = cached(context)?.rules.orEmpty()
            val archive = result.rules + old.filter { it.id !in result.activeRules && ExternalPluginFeeds.installedVersion(context, it.id) > 0 }
            prefs(context).edit().putString("catalog", raw).putString("archive", json.encodeToString(ExternalPluginFeed(1, archive))).apply()
            cached(context)
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { cached(context) }
    }
}
