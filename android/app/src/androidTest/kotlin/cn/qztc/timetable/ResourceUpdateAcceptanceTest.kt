package cn.qztc.timetable

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import cn.qztc.timetable.announcement.AnnouncementFeed
import cn.qztc.timetable.announcement.AnnouncementStore
import cn.qztc.timetable.update.ResourceContentStore
import cn.qztc.timetable.update.ResourceUpdateClient
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ResourceUpdateAcceptanceTest {
    @Test fun liveResourceAppliesWithoutPackageInstallationAndMatchesOriginal() = runBlocking {
        val real = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(real.cacheDir, "resource-qa-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(real) { override fun getFilesDir() = dir }
        try {
            val manifest = ResourceUpdateClient.check()
            val original = real.assets.open("content/elective-advice.txt").bufferedReader().use { it.readText() }
            assertNull(ResourceContentStore.content(isolated))
            ResourceUpdateClient.download(ResourceContentStore.directory(isolated), manifest, ResourceUpdateClient.Control(), {})
            ResourceContentStore.apply(isolated, manifest)
            assertEquals(manifest.version, ResourceContentStore.content(isolated)?.version)
            assertEquals(original, ResourceContentStore.electiveAdvice(isolated))
            assertFalse(ResourceUpdateClient.partial(ResourceContentStore.directory(isolated), manifest).exists())
            val reopened = object : ContextWrapper(real) { override fun getFilesDir() = dir }
            assertEquals(original, ResourceContentStore.electiveAdvice(reopened))
        } finally { dir.deleteRecursively() }
    }

    @Test fun sameAnnouncementDoesNotPopupAgainButNewRevisionDoes() {
        val real = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "announcements-qa-${UUID.randomUUID()}"
        val isolated = object : ContextWrapper(real) {
            override fun getSharedPreferences(ignored: String?, mode: Int) = real.getSharedPreferences(name, Context.MODE_PRIVATE)
        }
        val prefs = isolated.getSharedPreferences("announcements", 0)
        try {
            val text = """{"schemaVersion":1,"items":[{"id":"resource-qa","revision":1,"title":"资料更新","body":"原文","publishedAt":"2026-01-01T00:00:00Z","important":true,"action":"check_update"}]}"""
            prefs.edit().putString("feed", text).commit()
            val store = AnnouncementStore(isolated)
            store.dismiss(requireNotNull(store.important()))
            assertNull(AnnouncementStore(isolated).important())
            prefs.edit().putString("feed", text.replace("\"revision\":1", "\"revision\":2")).commit()
            assertEquals("resource-qa:2", AnnouncementStore(isolated).important()?.readKey)
            assertEquals(1, AnnouncementFeed.parse(text).items.size)
        } finally { prefs.edit().clear().commit() }
    }
}
