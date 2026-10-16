package cn.qztc.timetable.review
import org.junit.Assert.*
import org.junit.Test
class ReviewDraftTest {
    @Test fun countIncludesPunctuationAndEmojiButNoWhitespace() {
        assertEquals(5, ReviewText.count("好吃， 🙂\n好 "))
        assertEquals(2, ReviewText.count("中　文"))
    }
    @Test fun invalidRangesAndMissingFactsAreRejected() {
        listOf(ReviewDraft(), ReviewDraft(facts = "好吃", minimum = 100, maximum = 20),
            ReviewDraft(facts = "好吃", maximum = 600)).forEach {
            assertThrows(IllegalArgumentException::class.java) { it.validate() }
        }
        ReviewDraft(facts = "面条口感偏软，汤比较清淡").validate()
    }
    @Test fun allScenesHaveEditableTemplatesAndPromptsRetainFacts() {
        ReviewDraft.scenes.forEach { assertTrue(ReviewText.starter(it).contains("【")) }
        val draft = ReviewDraft(facts = "味道不错但有些偏甜", minimum = 50, maximum = 80)
        assertTrue(draft.prompt().contains(draft.facts))
        assertTrue(draft.prompt().contains("50–80"))
        assertTrue(ReviewDraft.SYSTEM.contains("不编造"))
    }
    @Test fun boundaryCountsAreExact() {
        val draft = ReviewDraft(facts = "实际体验", minimum = 10, maximum = 20)
        assertFalse(ReviewText.inRange("好".repeat(9), draft))
        assertTrue(ReviewText.inRange("好".repeat(10), draft))
        assertTrue(ReviewText.inRange("好".repeat(20), draft))
        assertFalse(ReviewText.inRange("好".repeat(21), draft))
    }
    @Test fun customCredentialsRequireHttpsAndRejectUrlCredentials() {
        ReviewConnection("https://api.example.org/v1/chat/completions", "test", "test-key").validate()
        listOf("http://api.example.org/v1/chat/completions", "https://user:pass@example.org/path", "https://example.org/path?key=secret").forEach {
            assertThrows(IllegalArgumentException::class.java) { ReviewConnection(it, "test", "key").validate() }
        }
    }
}
