package cn.qztc.timetable.plugin

import android.content.Context
import android.util.AtomicFile
import cn.qztc.timetable.distribution.DistributionClient
import cn.qztc.timetable.update.AppUpdateClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import androidx.compose.runtime.*

/** Downloaded JSON only: the rendering code is trusted host code. */
object AcademicRecordsPlugin : PluginController {
    const val ID = "academic-records"
    override val descriptor = PluginDescriptor(ID, "赛事与考证", "公开赛事资料、考证日程与关注更新", 1)
    private fun prefs(context: Context) = context.getSharedPreferences(ID, Context.MODE_PRIVATE)
    fun dataFile(context: Context) = File(context.filesDir, "plugins/$ID/records.json")
    override fun installedVersion(context: Context) = if (dataFile(context).isFile) prefs(context).getInt("version", 0) else 0
    private suspend fun manifest() = withContext(Dispatchers.IO) {
        val connection = AppUpdateClient.openTrustedConnection(DistributionClient.BASE_URL + "/api/plugins/$ID", "GET", "application/json")
        try {
            require(connection.responseCode == 200) { "赛事插件尚未发布或服务器暂不可用，请稍后重试" }
            val data = connection.inputStream.use { readLimited(it, 8192) }
            JSONObject(data.toString(Charsets.UTF_8)).also {
                require(it.getString("id") == ID && it.getInt("version") > 0)
                require(it.getInt("minAppVersion") <= cn.qztc.timetable.BuildConfig.VERSION_CODE)
            }
        } finally { connection.disconnect() }
    }
    override suspend fun availableVersion(): Int = try { manifest().getInt("version") }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { descriptor.version }
    override suspend fun install(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val release = manifest()
            val version = release.getInt("version")
            val url = DistributionClient.BASE_URL + "/downloads/qsschedule-$ID-$version.json"
            require(release.getString("dataUrl") == url)
            val connection = AppUpdateClient.openTrustedConnection(url, "GET", "application/json")
            val bytes = try {
                require(connection.responseCode == 200) { "插件资料下载失败" }
                connection.inputStream.use { readLimited(it, 4_000_000) }
            } finally { connection.disconnect() }
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            require(hash == release.getString("sha256")) { "插件资料校验失败，原版本已保留" }
            save(context, bytes, version)
            Result.success(Unit)
        } catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) { Result.failure(error) }
    }
    private fun save(context: Context, bytes: ByteArray, version: Int) {
        val data = JSONObject(bytes.toString(Charsets.UTF_8))
        require(data.getString("pluginId") == ID && data.getInt("pluginVersion") == version && version > 0)
        require(data.getInt("schemaVersion") == 1)
        require(data.getJSONObject("data").getJSONArray("records").length() in 1..2000)
        require(data.getJSONObject("data").getJSONObject("aliases").length() > 0)
        val file = dataFile(context)
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try { output.write(bytes); atomic.finishWrite(output) }
        catch (error: Exception) { atomic.failWrite(output); throw error }
        val revision = maxOf(System.currentTimeMillis(), prefs(context).getLong("revision", 0L) + 1L)
        prefs(context).edit().putInt("version", version).putLong("revision", revision).apply()
        PluginPins.set(context, ID, true)
    }
    fun script(context: Context): ByteArray? = if (installedVersion(context) == 0) null else runCatching {
        val data = JSONObject(dataFile(context).readText()).getJSONObject("data")
        ("window.AuditedRecordData=" + data.toString() + ";").toByteArray(Charsets.UTF_8)
    }.getOrNull()
    override fun uninstall(context: Context) {
        AtomicFile(dataFile(context)).delete()
        prefs(context).edit().clear().apply()
        PluginPins.set(context, ID, false)
        val work = androidx.work.WorkManager.getInstance(context)
        work.cancelUniqueWork("followed-competition-updates")
        work.cancelUniqueWork("followed-competition-updates-initial")
        androidx.core.app.NotificationManagerCompat.from(context).cancel(1301)
    }
    private fun readLimited(input: InputStream, maximum: Int): ByteArray {
        val output = ByteArrayOutputStream()
        AppUpdateClient.copyLimited(input, output, maximum.toLong())
        return output.toByteArray()
    }
}

@androidx.compose.runtime.Composable
fun rememberAcademicRecordsRevision(): Long {
    val context = androidx.compose.ui.platform.LocalContext.current
    fun current() = if (AcademicRecordsPlugin.installedVersion(context) > 0)
        context.getSharedPreferences(AcademicRecordsPlugin.ID, Context.MODE_PRIVATE).getLong("revision", 1L) else 0L
    var revision by remember { mutableLongStateOf(current()) }
    DisposableEffect(context) {
        val prefs = context.getSharedPreferences(AcademicRecordsPlugin.ID, Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            revision = current()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return revision
}

@Composable
fun rememberAcademicRecordsInstalled(): Boolean = rememberAcademicRecordsRevision() > 0L
