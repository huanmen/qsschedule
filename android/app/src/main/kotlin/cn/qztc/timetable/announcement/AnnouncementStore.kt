package cn.qztc.timetable.announcement

import android.content.Context
import cn.qztc.timetable.BuildConfig
import cn.qztc.timetable.update.AppUpdateClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.Instant

class AnnouncementStore(context: Context) {
    private val prefs = context.getSharedPreferences("announcements", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val changed = MutableStateFlow(0)
    val revision = changed.asStateFlow()
    private var attemptedAt = 0L
    fun items(now: Instant = Instant.now()): List<Announcement> = runCatching {
        AnnouncementFeed.parse(prefs.getString("feed", null) ?: return emptyList()).visible(BuildConfig.VERSION_CODE, now)
    }.getOrDefault(emptyList())
    fun read(item: Announcement) = prefs.getStringSet("read", emptySet()).orEmpty().contains(item.readKey)
    fun markRead(item: Announcement) {
        prefs.edit().putStringSet("read", (prefs.getStringSet("read", emptySet()).orEmpty() + item.readKey).takeLastBounded()).apply()
        changed.value++
    }
    fun important(): Announcement? = items().firstOrNull { it.important && !read(it) && it.readKey !in prefs.getStringSet("shown", emptySet()).orEmpty() }
    fun dismiss(item: Announcement) {
        prefs.edit().putStringSet("shown", (prefs.getStringSet("shown", emptySet()).orEmpty() + item.readKey).takeLastBounded()).apply()
        changed.value++
    }
    suspend fun refresh(force: Boolean = false): Result<Unit> = mutex.withLock {
        val now = System.currentTimeMillis()
        if (!force && attemptedAt != 0L && now - attemptedAt < 15 * 60_000) return@withLock Result.success(Unit)
        attemptedAt = now
        withContext(Dispatchers.IO) {
            try {
                val connection = AppUpdateClient.openTrustedConnection(URL, "GET", "application/json")
                val text = try {
                    require(connection.responseCode in 200..299) { "暂时无法获取公告" }
                    require(connection.contentLengthLong <= AnnouncementFeed.MAX_BYTES) { "公告文件过大" }
                    val output = ByteArrayOutputStream()
                    connection.inputStream.use { AppUpdateClient.copyLimited(it, output, AnnouncementFeed.MAX_BYTES.toLong()) }
                    output.toString("UTF-8")
                } finally { connection.disconnect() }
                AnnouncementFeed.parse(text)
                prefs.edit().putString("feed", text).putLong("last_success", now).apply()
                changed.value++
                Result.success(Unit)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { Result.failure(IllegalStateException("暂时无法获取公告，已保留上次内容")) }
        }
    }
    private fun Set<String>.takeLastBounded(): Set<String> = toList().takeLast(300).toSet()
    companion object { const val URL = cn.qztc.timetable.distribution.DistributionClient.BASE_URL + "/api/announcements-v2" }
}
