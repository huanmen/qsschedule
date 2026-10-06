package cn.qztc.timetable.adskip

import org.junit.Assert.*
import org.junit.Test

class AdSkipRulesTest {
    @Test fun frozenBacklogAndDelayedVerificationAreNotCurrentEvidence() {
        assertTrue(AdSkipTiming.fresh(1000, 1100))
        assertFalse(AdSkipTiming.fresh(1000, 5000))
        assertFalse(AdSkipTiming.fresh(0, 1000))
        assertFalse(AdSkipTiming.fresh(2000, 1000))
        assertTrue(AdSkipTiming.verificationTimely(1000, 2600))
        assertFalse(AdSkipTiming.verificationTimely(1000, 5000))
    }
    @Test fun targetFirstSnapshotRetainsExactAncestorChain() {
        // Targeted queries append the target first and ancestors later, unlike BFS.
        val nodes = listOf(node(bus, "ll_textad_close", parent = 1), node(bus, "ll_textad", parent = 2), node(bus, "textad_view"))
        assertEquals(0, AdSkipMatcher.findTarget(bus, nodes))
        assertEquals(0, AdSkipMatcher.clickTarget(bus, nodes, 0))
    }
    private val yiban = AdSkipRules.bundled.rules[0].copy(activityNames = listOf("com.yiban.app.activity.WelcomeActivity"), selectors = emptyList(), allowGesture = false, clickableAncestor = false)
    private val chaoxing = AdSkipRules.bundled.rules[1].copy(activityNames = listOf("com.chaoxing.mobile.activity.SplashActivity"), selectors = emptyList(), clickParentId = "jump_view", allowGesture = false, clickableAncestor = false)
    private val bus = AdSkipRules.bundled.rules[2]
    private fun node(rule: AdSkipRule, id: String, text: String? = null, clickable: Boolean = true, parent: Int? = null, visible: Boolean = true) =
        AdSkipNode("${rule.packageName}:id/$id", text, visible, true, clickable, parent)
    private fun reject(pack: AdSkipRulePack) {
        assertThrows(IllegalArgumentException::class.java) { AdSkipRules.validate(pack) }
    }
    @Test fun bundledRoundTripPreservesExistingRulesAndAddsExplicitPopupRule() {
        assertEquals(AdSkipRules.bundled, AdSkipRules.decode(AdSkipRules.encode(AdSkipRules.bundled)))
        assertEquals(5, AdSkipRules.bundled.rules.size)
        assertFalse(AdSkipRules.bundled.rules.any { it.packageName == "com.cainiao.wireless" || it.packageName == "com.zhishan.washer" })
    }
    @Test fun genericRuleCooldownIsSeparateForEachApplication() {
        assertNotEquals(AdSkipGenericRules.rule("com.realtech.xiaocan").runtimeKey(),
            AdSkipGenericRules.rule("tv.danmaku.bili").runtimeKey())
    }
    @Test fun bundledPopupExactlyMatchesImportedSourceAndNeverClicksAdImage() {
        val bundled = AdSkipRules.bundled.rules.single { it.id == "xiaocan-home-popup" }
        val imported = AdSkipImport.parse("""
            {name:'source',version:1,apps:[{id:'com.realtech.xiaocan',groups:[{name:'弹窗广告',
            rules:[{activityIds:'com.realtech.xiaocan.MainActivity',
            matches:'View[childCount=3] > View + ImageView + ImageView[clickable=true][childCount=0]'}]}]}]}
        """).pack!!.rules.single()
        assertEquals(imported.selectors, bundled.selectors)
        fun view(type: String, parent: Int?, index: Int?, children: Int, clickable: Boolean = false) =
            AdSkipNode(null, null, true, true, clickable, parent, className = "android.view.$type",
                packageName = bundled.packageName, index = index, childCount = children)
        val tree = listOf(view("View", null, null, 3), view("View", 0, 0, 0),
            view("ImageView", 0, 1, 0, true), view("ImageView", 0, 2, 0, true))
        assertEquals(3, AdSkipMatcher.findTarget(bundled, tree))
        assertEquals(listOf(3), AdSkipMatcher.clickCandidates(bundled, tree, 3))
        assertNull(AdSkipMatcher.findTarget(bundled, tree.dropLast(1)))
    }
    @Test fun unknownAppsAreRejected() { reject(AdSkipRules.bundled.copy(rules = listOf(yiban.copy(packageName = "com.bank.app")))) }
    @Test fun explicitSkipMayOmitActivityButGenericButtonIsRejected() {
        AdSkipRules.validate(AdSkipRules.bundled.copy(rules = listOf(yiban.copy(activityNames = emptyList()))))
        reject(AdSkipRules.bundled.copy(rules = listOf(yiban.copy(textPrefix = null))))
    }
    @Test fun arbitraryScriptAndUnknownFieldsAreRejected() {
        val text = AdSkipRules.encode(AdSkipRules.bundled).replaceFirst("{", "{ \"script\": \"tap(1,2)\",")
        assertThrows(IllegalArgumentException::class.java) { AdSkipRules.decode(text) }
    }
    @Test fun sizeVersionDuplicateAndControlCharactersAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { AdSkipRules.decode("x".repeat(AdSkipRules.MAX_BYTES + 1)) }
        reject(AdSkipRules.bundled.copy(schemaVersion = 99))
        reject(AdSkipRules.bundled.copy(rules = listOf(yiban, yiban)))
        reject(AdSkipRules.bundled.copy(name = "fake|result"))
    }
    @Test fun activitiesUseExactMatchingAndVersionedHomeName() {
        assertTrue(AdSkipMatcher.matchesActivity(yiban, "com.yiban.app.activity.WelcomeActivity"))
        assertFalse(AdSkipMatcher.matchesActivity(yiban, "com.yiban.app.activity.OtherWelcomeActivity"))
        assertTrue(yiban.successActivityNames.contains("com.yiban.app.home.activity.HomeActivityV2"))
    }
    @Test fun yibanRequiresVisibleSkipTextAndOwnResourceId() {
        assertEquals(0, AdSkipMatcher.findTarget(yiban, listOf(node(yiban, "btn_splash_ad", "跳过3"))))
        assertNull(AdSkipMatcher.findTarget(yiban, listOf(node(yiban, "btn_splash_ad", "立即购买"))))
        assertNull(AdSkipMatcher.findTarget(yiban, listOf(node(yiban, "btn_splash_ad", "跳过3", visible = false))))
        assertNull(AdSkipMatcher.findTarget(yiban, listOf(node(bus, "btn_splash_ad", "跳过3"))))
    }
    @Test fun chaoxingClicksOnlyTheExactParent() {
        val nodes = listOf(node(chaoxing, "jump_view").copy(bounds = AdSkipBounds(0, 0, 110, 45)),
            node(chaoxing, "btn_jump", "跳过1s", clickable = false, parent = 0).copy(bounds = AdSkipBounds(5, 5, 100, 40)))
        assertEquals(1, AdSkipMatcher.findTarget(chaoxing, nodes))
        assertEquals(0, AdSkipMatcher.clickTarget(chaoxing, nodes, 1))
        assertNull(AdSkipMatcher.clickTarget(chaoxing, nodes.toMutableList().apply { set(0, node(chaoxing, "buy_button")) }, 1))
    }
    @Test fun busContainerMustBeAncestorNotSomewhereElseOnPage() {
        val nodes = listOf(node(bus, "textad_view"), node(bus, "ll_textad_close", parent = 0))
        assertEquals(1, AdSkipMatcher.findTarget(bus, nodes))
        assertNull(AdSkipMatcher.findTarget(bus, nodes.toMutableList().apply { set(1, node(bus, "ll_textad_close")) }))
    }
    @Test fun nonClickableNodeDoesNotFallBackToGestureOrOtherAncestor() {
        val nodes = listOf(node(yiban, "btn_splash_ad", "跳过3", clickable = false))
        assertEquals(0, AdSkipMatcher.findTarget(yiban, nodes))
        assertNull(AdSkipMatcher.clickTarget(yiban, nodes, 0))
    }
    @Test fun leavingAppOrIncompleteTreeIsNotAdClosed() {
        assertEquals(AdSkipResult.UNCONFIRMED, AdSkipVerification.result(yiban, "launcher", null, false))
        assertEquals(AdSkipResult.UNCONFIRMED, AdSkipVerification.result(yiban, yiban.packageName, null, null))
    }
    @Test fun verificationSeparatesTargetGoneNormalPageAndStillPresent() {
        val home = yiban.successActivityNames.first()
        assertEquals(AdSkipResult.TARGET_PRESENT, AdSkipVerification.result(yiban, yiban.packageName, home, true))
        assertEquals(AdSkipResult.NORMAL_PAGE, AdSkipVerification.result(yiban, yiban.packageName, home, false))
        assertEquals(AdSkipResult.TARGET_GONE, AdSkipVerification.result(yiban, yiban.packageName, null, false))
    }
}
