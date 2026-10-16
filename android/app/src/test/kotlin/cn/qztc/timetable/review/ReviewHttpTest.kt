package cn.qztc.timetable.review

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ReviewHttpTest {
    private val config = ReviewConnection("https://example.org/v1", "sample-model", "synthetic-key")
    private class FakeConnection(private val status: Int, private val body: String) : HttpURLConnection(URL("https://example.org")) {
        val sent = ByteArrayOutputStream()
        var disconnected = false
        override fun connect() {}
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getOutputStream() = sent
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        override fun getResponseCode() = status
    }
    @Test fun sendsExpectedCompatibleRequestAndDisablesCredentialRedirects() {
        val connection = FakeConnection(200, "{\"choices\":[{\"message\":{\"content\":\"清淡\"}}]}")
        var address = ""
        val output = ReviewClient.requestWith(config, listOf(ReviewClient.Message("user", "合成素材"))) { address = it; connection }
        assertEquals("清淡", output)
        assertEquals("https://example.org/v1/chat/completions", address)
        assertEquals("POST", connection.requestMethod)
        assertEquals("Bearer synthetic-key", connection.getRequestProperty("Authorization"))
        assertFalse(connection.instanceFollowRedirects)
        assertTrue(connection.connectTimeout > 0 && connection.readTimeout > 0)
        val json = Json.parseToJsonElement(connection.sent.toString("UTF-8")).jsonObject
        assertEquals("sample-model", json["model"]!!.jsonPrimitive.content)
        assertEquals(false, json["stream"]!!.jsonPrimitive.boolean)
        assertEquals("合成素材", json["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonPrimitive.content)
        assertFalse(connection.sent.toString("UTF-8").contains(config.apiKey))
        assertTrue(connection.disconnected)
    }
    @Test fun failuresAreSanitizedAndConnectionsAlwaysClose() {
        listOf(302, 401, 403, 429, 500).forEach { status ->
            val connection = FakeConnection(status, "synthetic-private-error")
            val error = assertThrows(IllegalStateException::class.java) {
                ReviewClient.requestWith(config, emptyList()) { connection }
            }
            assertFalse(error.message.orEmpty().contains("synthetic-private-error"))
            assertTrue(connection.disconnected)
        }
    }
    @Test fun oversizedResponseIsRejectedAndClosed() {
        val connection = FakeConnection(200, "x".repeat(128 * 1024 + 1))
        assertThrows(Exception::class.java) { ReviewClient.requestWith(config, emptyList()) { connection } }
        assertTrue(connection.disconnected)
    }
    @Test fun insecureEndpointNeverOpensConnection() {
        var opened = false
        assertThrows(IllegalArgumentException::class.java) {
            ReviewClient.requestWith(config.copy(endpoint = "http://example.org/v1"), emptyList()) { opened = true; FakeConnection(200, "") }
        }
        assertFalse(opened)
    }
}
