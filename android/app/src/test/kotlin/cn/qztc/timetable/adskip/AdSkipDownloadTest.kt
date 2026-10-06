package cn.qztc.timetable.adskip

import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class AdSkipDownloadTest {
    private class Response(val body: ByteArray, val status: Int = 200, val declared: Long = -1) : HttpURLConnection(URL("https://example.org/pack.json")) {
        var closed = false
        override fun connect() = Unit
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getContentLengthLong() = declared
        override fun getInputStream() = body.inputStream()
    }
    @Test fun downloadValidatesBeforeReturningAndClosesConnection() {
        val response = Response(AdSkipRules.encode(AdSkipRules.bundled).toByteArray())
        assertEquals(AdSkipRules.bundled, AdSkipStore.downloadPack("https://example.org/pack.json") { response }.pack)
        assertTrue(response.closed)
    }
    @Test fun rejectsHttpBeforeConnecting() {
        assertThrows(IllegalArgumentException::class.java) {
            AdSkipStore.downloadPack("http://example.org/pack.json") { error("must not connect") }
        }
    }
    @Test fun rejectsErrorsRedirectsOversizedAndMalformedDownloads() {
        listOf(Response(byteArrayOf(), 404), Response(byteArrayOf(), 302),
            Response(byteArrayOf(), declared = AdSkipRules.MAX_BYTES + 1L),
            Response(ByteArray(AdSkipRules.MAX_BYTES + 1)), Response("<html>Error</html>".toByteArray())
        ).forEach { response ->
            assertThrows(Exception::class.java) { AdSkipStore.downloadPack("https://example.org/pack.json") { response } }
            assertTrue(response.closed)
        }
    }
    @Test fun importedExternalAppSurvivesSerialization() {
        val pack = AdSkipRules.bundled.copy(rules = listOf(AdSkipRules.bundled.rules.first().copy(
            id = "external", packageName = "org.example.campus", activityNames = listOf("org.example.campus.Splash"),
            successActivityNames = emptyList(), appLabel = "测试应用")))
        assertEquals(pack, AdSkipImport.parse(AdSkipRules.encode(pack)).pack)
        assertThrows(IllegalArgumentException::class.java) { AdSkipRules.validate(pack.copy(rules = listOf(pack.rules.first().copy(packageName = "com.android.settings"))), true) }
    }
}
