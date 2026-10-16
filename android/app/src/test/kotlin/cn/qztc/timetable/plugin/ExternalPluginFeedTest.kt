package cn.qztc.timetable.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ExternalPluginFeedTest {
    private val url = "https://example.org/plugins/catalog.json"
    private fun feed(kind: String = "adskip-rule-pack", rulesUrl: String = "https://example.org/rules.json") =
        """{"schemaVersion":1,"plugins":[{"id":"campus.ads","name":"规则包","summary":"测试", "version":2,"kind":"$kind","rulesUrl":"$rulesUrl"}]}"""

    @Test fun acceptsOnlyBoundedRuleData() {
        val parsed = ExternalPluginFeeds.parse(url, feed())
        assertEquals("campus.ads", parsed.plugins.single().id)
        assertEquals(2, parsed.plugins.single().version)
        assertThrows(IllegalArgumentException::class.java) { ExternalPluginFeeds.parse("http://example.org/catalog.json", feed()) }
        assertThrows(IllegalArgumentException::class.java) { ExternalPluginFeeds.parse(url, feed(kind = "apk")) }
        assertThrows(IllegalArgumentException::class.java) { ExternalPluginFeeds.parse(url, feed(rulesUrl = "http://example.org/rules.json")) }
        assertThrows(IllegalArgumentException::class.java) { ExternalPluginFeeds.parse(url, feed() + " ".repeat(65536)) }
    }

    @Test fun accessibilitySessionHasOneOwner() {
        AccessibilitySession.release("test-owner")
        assertTrue(AccessibilitySession.claim("test-owner"))
        assertFalse(AccessibilitySession.availableTo("adskip"))
        assertFalse(AccessibilitySession.claim("another-plugin"))
        AccessibilitySession.release("another-plugin")
        assertTrue(AccessibilitySession.occupiedBy("test-owner"))
        AccessibilitySession.release("test-owner")
        assertTrue(AccessibilitySession.availableTo("adskip"))
    }
}
