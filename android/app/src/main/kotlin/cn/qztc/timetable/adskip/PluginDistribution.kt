package cn.qztc.timetable.adskip

import android.content.Context
import cn.qztc.timetable.distribution.DistributionClient
import cn.qztc.timetable.update.AppUpdateClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream

object PluginDistribution {
    @Serializable data class Release(val version: Int, val rulesUrl: String, val minAppVersion: Int = 1)
    @Serializable data class NativeRelease(val id: String, val version: Int, val minAppVersion: Int = 1)
    suspend fun available(): Release? = withContext(Dispatchers.IO) {
        try {
            val connection = AppUpdateClient.openTrustedConnection(DistributionClient.BASE_URL + "/api/plugins/adskip", "GET", "application/json")
            try {
                require(connection.responseCode == 200)
                val out = ByteArrayOutputStream()
                connection.inputStream.use { AppUpdateClient.copyLimited(it, out, 8192) }
                Json.decodeFromString<Release>(out.toString("UTF-8")).also {
                    require(it.version > 0 && it.minAppVersion <= cn.qztc.timetable.BuildConfig.VERSION_CODE)
                    require(it.rulesUrl == DistributionClient.BASE_URL + "/api/rules/adskip")
                }
            } finally { connection.disconnect() }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { null }
    }
    suspend fun install(context: Context, release: Release?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val pack = if (release != null) AdSkipStore.downloadPack(release.rulesUrl).pack ?: error("规则包无效") else AdSkipRules.bundled
            AdSkipStore.addRules(context, pack, pluginVersion = release?.version ?: 2)
            DistributionClient.completed(context, "adskip", release?.version ?: 2)
            Result.success(Unit)
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { Result.failure(IllegalStateException("下载失败，请重试")) }
    }

    /** Native feature plugins still execute host code, while their install and
     * version gate are controlled by the same first-party server catalog. */
    suspend fun nativeAvailable(id: String): Int? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = AppUpdateClient.openTrustedConnection(
                DistributionClient.BASE_URL + "/api/plugins/" + id, "GET", "application/json")
            try {
                require(connection.responseCode == 200)
                val release = connection.inputStream.use { input ->
                    Json.decodeFromString<NativeRelease>(input.bufferedReader().readText())
                }
                require(release.id == id && release.version > 0 && release.minAppVersion <= cn.qztc.timetable.BuildConfig.VERSION_CODE)
                release.version
            } finally { connection.disconnect() }
        }.getOrNull()
    }

    suspend fun installNative(context: Context, id: String, fallbackVersion: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val version = nativeAvailable(id) ?: error("插件服务器暂时不可用")
            require(version >= fallbackVersion) { "插件版本过旧" }
            context.getSharedPreferences("builtin_plugins", Context.MODE_PRIVATE).edit().putInt(id, version).apply()
            DistributionClient.completed(context, id, version)
        }
    }
}
