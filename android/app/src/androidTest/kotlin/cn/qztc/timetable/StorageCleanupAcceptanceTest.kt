package cn.qztc.timetable

import androidx.test.platform.app.InstrumentationRegistry
import cn.qztc.timetable.storage.StorageInventoryReader
import cn.qztc.timetable.storage.StorageEntry
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class StorageCleanupAcceptanceTest {
    @Test fun cleanupPreservesActiveBackgroundAndPendingSnapshots() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val suffix=UUID.randomUUID().toString()
        val cache=File(context.cacheDir,"qa-$suffix")
        val active=File(context.filesDir,"backgrounds/background-$suffix.image")
        val pending=File(context.filesDir,"snapshots/pending-$suffix.png")
        val resolved=File(context.filesDir,"snapshots/resolved-$suffix.png")
        val generated=listOf(cache,active,pending,resolved)
        try {
            generated.forEach { it.parentFile!!.mkdirs();it.writeBytes(ByteArray(64)) }
            val reader=StorageInventoryReader(context,active.absolutePath)
            val entries=reader.read().entries
            assertFalse(entries.any { it.file?.canonicalFile==active.canonicalFile || it.file?.canonicalFile==pending.canonicalFile })
            val selected=entries.filter { it.file?.canonicalFile==cache.canonicalFile || it.file?.canonicalFile==resolved.canonicalFile }
            assertEquals(2,selected.size)
            val forged=StorageEntry(active.absolutePath,"active","",64,active)
            assertEquals(128L,reader.clear(selected+forged))
            assertTrue(active.isFile);assertTrue(pending.isFile)
            assertFalse(cache.exists());assertFalse(resolved.exists())
        } finally { generated.forEach { it.delete() } }
    }
}
