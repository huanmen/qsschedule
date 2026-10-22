package cn.qztc.timetable.update

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class ResourceUpdateClientTest {
    @get:Rule val folder = TemporaryFolder()
    private val payload = Json.encodeToString(ResourceUpdateClient.Content(version = 1, electiveAdvice = "资料".repeat(8000))).toByteArray()
    private val hash = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
    private val manifest = ResourceUpdateClient.Manifest(version = 1, url = "https://huanmen.com.cn/qsschedule/downloads/content-$hash.json", sha256 = hash, sizeBytes = payload.size.toLong())
    private fun response(offset: Long, code: Int = if (offset > 0) 206 else 200, data: ByteArray = payload): HttpURLConnection = object : HttpURLConnection(URL(manifest.url)) {
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy() = false
        override fun getResponseCode() = code
        override fun getHeaderField(name: String?) = if (name == "Content-Range" && code == 206) "bytes $offset-${data.size - 1}/${data.size}" else null
        override fun getContentLengthLong() = data.size - (if (code == 206) offset else 0)
        override fun getInputStream() = data.copyOfRange(if (code == 206) offset.toInt() else 0, data.size).inputStream()
    }
    @Test fun pausedTransferSurvivesNewControlAndContinuesAtSavedOffset() = runBlocking {
        val directory = folder.newFolder()
        val control = ResourceUpdateClient.Control()
        val first = runCatching { ResourceUpdateClient.download(directory, manifest, control, { if (it >= 8192) control.stop() }) { _, _, offset -> response(offset) } }
        assertTrue(first.isFailure)
        val saved = ResourceUpdateClient.downloaded(directory, manifest)
        assertTrue(saved in 8192 until payload.size.toLong())
        var resumedAt = -1L
        val content = ResourceUpdateClient.download(directory, manifest, ResourceUpdateClient.Control(), {}) { _, _, offset -> resumedAt = offset; response(offset) }
        assertEquals(saved, resumedAt)
        assertEquals("资料".repeat(8000), content.electiveAdvice)
    }
    @Test fun serverIgnoringRangeRestartsWithoutDuplicatingBytes() = runBlocking {
        val dir = folder.newFolder()
        ResourceUpdateClient.partial(dir, manifest).writeBytes(payload.take(500).toByteArray())
        ResourceUpdateClient.download(dir, manifest, ResourceUpdateClient.Control(), {}) { _, _, offset -> response(offset, 200) }
        assertArrayEquals(payload, ResourceUpdateClient.partial(dir, manifest).readBytes())
    }
    @Test fun wrongHashDeletesCorruptPartialForRetry() = runBlocking {
        val dir = folder.newFolder()
        val changed = payload.clone().also { it[0] = 32 }
        assertTrue(runCatching { ResourceUpdateClient.download(dir, manifest, ResourceUpdateClient.Control(), {}) { _, _, offset -> response(offset, data = changed) } }.isFailure)
        assertFalse(ResourceUpdateClient.partial(dir, manifest).exists())
    }
    @Test fun rejectsIncorrectResumeRangesAndRedirects() {
        listOf(206 to "bytes 2-99/100", 302 to null, 416 to null).forEach { (code, range) ->
            assertTrue(runCatching { ResourceUpdateClient.validateResponse(code, range, 1, 100) }.isFailure)
        }
        assertTrue(ResourceUpdateClient.validateResponse(206, "bytes 1-99/100", 1, 100))
        assertFalse(ResourceUpdateClient.validateResponse(200, null, 1, 100))
    }
    @Test fun rejectsOversizeForeignUrlsAndExecutablePayloads() {
        listOf(manifest.copy(sizeBytes = ResourceUpdateClient.MAX_BYTES + 1), manifest.copy(url = "https://example.com/file.json"), manifest.copy(url = manifest.url + "?x=1")).forEach {
            assertTrue(runCatching { ResourceUpdateClient.parseManifest(Json.encodeToString(it)) }.isFailure)
        }
        assertTrue(runCatching { ResourceUpdateClient.parseContent("{\"schemaVersion\":1,\"version\":1,\"electiveAdvice\":\"a\",\"code\":\"run\"}") }.isFailure)
    }
    @Test fun formatsByteUnits() {
        assertEquals("512 B", formatResourceBytes(512))
        assertEquals("1.00 KB", formatResourceBytes(1024))
        assertEquals("1.00 MB", formatResourceBytes(1024 * 1024))
        assertEquals("1.00 GB", formatResourceBytes(1024L * 1024 * 1024))
    }
    @Test fun cancellingDeletesPartialWithoutDeletingInstalledContent() {
        val dir = folder.newFolder()
        val installed = java.io.File(dir, "installed.json").apply { writeText("existing content") }
        ResourceUpdateClient.partial(dir, manifest).writeBytes(payload.take(100).toByteArray())
        ResourceUpdateClient.clearDownload(dir, manifest)
        assertEquals(0L, ResourceUpdateClient.downloaded(dir, manifest))
        assertEquals("existing content", installed.readText())
        ResourceUpdateClient.clearDownload(dir, manifest)
    }
}
