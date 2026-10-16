package cn.qztc.timetable.review

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ReviewClientTest {
    private val config = ReviewConnection("https://example.org/v1", "test-model", "synthetic-key")
    private val draft = ReviewDraft(facts = "面条偏软，汤很清淡", minimum = 10, maximum = 20)

    @Test fun baseAddressesNormalizeAndCustomRoutesRemainUnchanged() {
        assertEquals("https://example.org/v1/chat/completions", config.requestEndpoint())
        assertEquals("https://example.org/v1/chat/completions", config.copy(endpoint = " https://example.org/ " ).requestEndpoint())
        assertEquals("https://example.org/custom/chat", config.copy(endpoint = "https://example.org/custom/chat").requestEndpoint())
        assertFalse(config.toString().contains(config.apiKey))
    }

    @Test fun firstValidResultStopsRequests() = runBlocking {
        var calls = 0
        val result = ReviewClient.generateWith(draft, config) { _, _ -> calls++; "汤很清淡，面条稍微软了一点。" }
        assertEquals(1, calls)
        assertTrue(result.withinRange)
    }

    @Test fun lengthRetryPreservesFactsAndIncludesPreviousAnswer() = runBlocking {
        val history = mutableListOf<List<ReviewClient.Message>>()
        val result = ReviewClient.generateWith(draft, config) { _, messages ->
            history += messages
            if (history.size == 1) "清淡" else "汤很清淡，面条稍微软了一点。"
        }
        assertEquals(2, history.size)
        assertEquals(2, history[0].size)
        assertEquals(4, history[1].size)
        assertTrue(history[1][1].content.contains(draft.facts))
        assertEquals("清淡", history[1][2].content)
        assertTrue(result.withinRange)
    }

    @Test fun exhaustedRetriesReturnClosestCompleteDraftWithoutTruncation() = runBlocking {
        val replies = listOf("好".repeat(21), "好".repeat(50), "好".repeat(25))
        var calls = 0
        val result = ReviewClient.generateWith(draft, config) { _, _ -> replies[calls++] }
        assertEquals(3, calls)
        assertFalse(result.withinRange)
        assertEquals(replies[0], result.text)
    }

    @Test fun responseParsingRejectsMalformedPayloadWithoutEchoingIt() {
        assertEquals("清淡", ReviewClient.parseResponse("{\"choices\":[{\"message\":{\"content\":\"清淡\"}}]}"))
        listOf("synthetic-private-error", "{}", "{\"choices\":[{\"message\":{\"content\":42}}]}").forEach { payload ->
            val error = assertThrows(IllegalStateException::class.java) { ReviewClient.parseResponse(payload) }
            assertEquals("模型返回格式不兼容", error.message)
        }
    }

    @Test fun invalidDraftNeverContactsModel() = runBlocking {
        var calls = 0
        try {
            ReviewClient.generateWith(draft.copy(facts = ""), config) { _, _ -> calls++; "" }
            fail("Expected validation failure")
        } catch (_: IllegalArgumentException) { assertEquals(0, calls) }
    }
}
