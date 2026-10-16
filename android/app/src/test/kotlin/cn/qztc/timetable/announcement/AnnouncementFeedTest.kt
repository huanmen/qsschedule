package cn.qztc.timetable.announcement

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AnnouncementFeedTest {
    private val now = Instant.parse("2026-10-06T00:00:00Z")
    private val item = Announcement("release-14", title = "新版本", body = "说明", publishedAt = "2026-10-05T10:00:00Z")
    @Test fun filtersFutureExpiredAndUnrelatedVersions() {
        val feed = AnnouncementFeed(items = listOf(item, item.copy(id = "future", publishedAt = "2026-10-07T10:00:00Z"),
            item.copy(id = "expired", expiresAt = "2026-10-05T11:00:00Z"), item.copy(id = "newer", minVersionCode = 14), item.copy(id = "older", maxVersionCode = 12)))
        assertEquals(listOf(item), feed.visible(13, now))
    }
    @Test fun validatesBeforeCacheReplacement() {
        val text = Json.encodeToString(AnnouncementFeed(items = listOf(item.copy(action = "check_update"))))
        assertEquals("check_update", AnnouncementFeed.parse(text).items.single().action)
        assertThrows(IllegalArgumentException::class.java) { AnnouncementFeed.parse(text.replace("check_update", "open_arbitrary_url")) }
        assertThrows(IllegalArgumentException::class.java) { AnnouncementFeed.parse(Json.encodeToString(AnnouncementFeed(items = listOf(item, item)))) }
        assertThrows(IllegalArgumentException::class.java) { AnnouncementFeed.parse(Json.encodeToString(AnnouncementFeed(schemaVersion = 2, items = emptyList()))) }
    }
    @Test fun revisionChangesUnreadIdentityWithoutChangingId() {
        assertNotEquals(item.readKey, item.copy(revision = 2).readKey)
        assertEquals(item.readKey, item.copy(body = "说明").readKey)
    }
}
