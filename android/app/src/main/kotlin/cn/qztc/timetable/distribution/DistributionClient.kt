package cn.qztc.timetable.distribution

import android.content.Context
import cn.qztc.timetable.update.AppUpdateClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

object DistributionClient {
    const val BASE_URL = "https://huanmen.com.cn/qsschedule"
    @Serializable private data class Completion(val installationId: String, val kind: String, val version: Int)
    /** Only a random installation ID and release number, after verified completion. */
    suspend fun completed(context: Context, kind: String, version: Int) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("distribution", Context.MODE_PRIVATE)
        val key = kind + ":" + version
        if (prefs.getBoolean(key, false)) return@withContext
        val id = synchronized(this@DistributionClient) {
            prefs.getString("installation_id", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("installation_id", it).commit() }
        }
        try {
            val connection = java.net.URL(BASE_URL + "/api/download-complete").openConnection() as java.net.HttpURLConnection
            try {
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.instanceFollowRedirects = false
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                val bytes = Json.encodeToString(Completion(id, kind, version)).toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
                if (connection.responseCode in 200..299) prefs.edit().putBoolean(key, true).apply()
            } finally { connection.disconnect() }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { /* Analytics never prevents installation. */ }
    }
}
