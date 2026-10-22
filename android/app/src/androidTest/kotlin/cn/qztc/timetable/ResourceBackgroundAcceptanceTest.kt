package cn.qztc.timetable

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import cn.qztc.timetable.update.*
import kotlinx.coroutines.delay
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Session test with a controlled slow transfer. No production data or installation changes. */
class ResourceBackgroundAcceptanceTest {
    @Test fun pausedSessionKeepsBytesAcrossObserversAndCancelDeletesOnlyPartial() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val real = instrumentation.targetContext
        val folder = File(real.cacheDir, "background-qa-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(real) {
            override fun getFilesDir() = folder
            override fun getApplicationContext(): Context = this
        }
        fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
        fun await(condition: () -> Boolean) {
            val deadline = System.currentTimeMillis() + 30_000
            while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(25)
            assertTrue(condition())
        }
        try {
            main { ResourceUpdateSession.reset(); ResourceUpdateSession.check(isolated) { error("Expected fresh isolated content") } }
            await { ResourceUpdateSession.state.value.stage == "available" }
            val m = requireNotNull(ResourceUpdateSession.state.value.manifest)
            var stopped = false
            main {
                ResourceUpdateSession.download(isolated, { stopped = true }) { app, manifest, control, progress ->
                    val dir = ResourceContentStore.directory(app).apply { mkdirs() }
                    val part = ResourceUpdateClient.partial(dir, manifest)
                    part.writeBytes(ByteArray(128))
                    progress(128)
                    while (true) { control.check(); delay(25) }
                }
            }
            await { ResourceUpdateSession.state.value.bytes == 128L }
            // Opening a second UI observer/check must retain the existing transfer, not check/reset.
            main { ResourceUpdateSession.check(isolated) { error("Must preserve active session") } }
            assertEquals(128L, ResourceUpdateSession.state.value.bytes)
            main { ResourceUpdateSession.stop(isolated, false) }
            await { ResourceUpdateSession.state.value.stage == "paused" }
            assertTrue(stopped)
            assertEquals(128L, ResourceUpdateSession.state.value.bytes)
            main { ResourceUpdateSession.check(isolated) { error("Must preserve paused session") } }
            assertEquals("paused", ResourceUpdateSession.state.value.stage)
            main { ResourceUpdateSession.stop(isolated, true) }
            await { ResourceUpdateSession.state.value.stage == "idle" }
            assertFalse(ResourceUpdateClient.partial(ResourceContentStore.directory(isolated), m).exists())
        } finally { main { ResourceUpdateSession.reset() }; folder.deleteRecursively() }
    }
}
