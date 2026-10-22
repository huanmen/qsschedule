package cn.qztc.timetable.announcement

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

@Serializable
data class AnnouncementUpdate(val kind: String, val versionCode: Int, val sha256: String)

@Serializable
data class Announcement(
    val id: String,
    val revision: Int = 1,
    val title: String,
    val body: String,
    val publishedAt: String,
    val expiresAt: String? = null,
    val important: Boolean = false,
    val minVersionCode: Int = 1,
    val maxVersionCode: Int? = null,
    val action: String? = null,
    val update: AnnouncementUpdate? = null,
) {
    val readKey get() = id + ":" + revision
    val isUpdateNotice get() = action == "check_update" && update?.kind == "apk"
    fun hasUpdate(currentVersionCode: Int): Boolean =
        action == "check_update" && update?.kind == "apk" && update.versionCode > currentVersionCode
}

@Serializable
data class AnnouncementFeed(val schemaVersion: Int = 1, val items: List<Announcement>) {
    fun visible(version: Int, now: Instant): List<Announcement> = items.filter {
        version >= it.minVersionCode && (it.maxVersionCode == null || version <= it.maxVersionCode) &&
            !Instant.parse(it.publishedAt).isAfter(now) && (it.expiresAt == null || Instant.parse(it.expiresAt).isAfter(now))
    }.sortedByDescending { Instant.parse(it.publishedAt) }

    companion object {
        const val MAX_BYTES = 128 * 1024
        private val json = Json { ignoreUnknownKeys = false }
        fun parse(text: String): AnnouncementFeed {
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "公告文件过大" }
            val feed = json.decodeFromString<AnnouncementFeed>(text.removePrefix("\uFEFF"))
            require(feed.schemaVersion == 1 && feed.items.size <= 100) { "公告格式不支持" }
            require(feed.items.map { it.id }.distinct().size == feed.items.size) { "公告标识重复" }
            feed.items.forEach {
                require(it.id.matches(Regex("[a-zA-Z0-9_-]{1,64}")) && it.revision > 0)
                require(it.title.isNotBlank() && it.title.length <= 100 && it.body.isNotBlank() && it.body.length <= 12_000)
                require(it.minVersionCode >= 1 && (it.maxVersionCode == null || it.maxVersionCode >= it.minVersionCode))
                require(it.action == null || it.action == "check_update")
                it.update?.let { target ->
                    require(it.action == "check_update" && target.kind == "apk" && target.versionCode > 0)
                    require(target.sha256.matches(Regex("[a-f0-9]{64}")))
                }
                val start = Instant.parse(it.publishedAt)
                require(it.expiresAt == null || Instant.parse(it.expiresAt).isAfter(start))
            }
            return feed
        }
    }
}
