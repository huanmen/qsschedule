package cn.qztc.timetable.adskip

import org.junit.Assert.*
import org.junit.Test

class MyBusPopupTest {
    private val rule = AdSkipRules.bundled.rules.single { it.id == "mybus-interstitial-close" }
    private fun node(id: String, parent: Int? = null, clickable: Boolean = false) =
        AdSkipNode("com.mygolbs.mybus:id/$id", null, true, true, clickable, parent)
    @Test fun selectsCloseSiblingRatherThanAdvertOrCallToAction() {
        val nodes = listOf(node("ptgRootAdvertLayout"), node("advertRootLayout", 0),
            node("advertBtn", 1, true), node("closeAdvertLayout", 0, true))
        assertEquals(3, AdSkipMatcher.findTarget(rule, nodes))
        assertNull(AdSkipMatcher.findTarget(rule, nodes.take(3)))
        assertNull(AdSkipMatcher.findTarget(rule, listOf(node("closeAdvertLayout", clickable = true))))
        assertFalse(AdSkipMatcher.matchesActivity(rule, "com.mygolbs.mybus.MainActivity"))
    }
}
