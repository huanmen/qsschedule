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
        assertEquals(14, AdSkipRules.bundled.rules.size)
        assertFalse(AdSkipRules.bundled.rules.any { it.targetId == "iv_close_homepage" })
        assertFalse(AdSkipRules.bundled.rules.any { it.packageName == "com.cainiao.wireless" || it.packageName == "com.zhishan.washer" })
    }
    @Test fun internalDrawnNativeRuleCannotBeReplacedByImportedSelectors() {
        val native = AdSkipRules.bundled.rules.single { it.id == AdSkipDrawnSplash.NATIVE_ID }
        reject(AdSkipRules.bundled.copy(rules = listOf(native.copy(
            selectors = listOf(AdSkipSelector(className = "ImageView"))
        ))))
        reject(AdSkipRules.bundled.copy(rules = listOf(native.copy(packageName = "com.realtech.xiaocan"))))
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
    @Test fun sportWorldClicksOnlyCloseInsideAdContainer() {
        val rule = AdSkipRules.bundled.rules.single { it.id == "sportworld-interstitial-close" }
        val root = node(rule, "ad_container", clickable = false).copy(parent = null,
            bounds = AdSkipBounds(64, 458, 1216, 2413))
        val close = node(rule, "ad_close", clickable = true, parent = 0).copy(
            description = "关闭", bounds = AdSkipBounds(109, 527, 158, 576))
        assertEquals(1, AdSkipMatcher.findTarget(rule, listOf(root, close)))
        assertEquals(listOf(1), AdSkipMatcher.clickCandidates(rule, listOf(root, close), 1))
        assertNull(AdSkipMatcher.findTarget(rule, listOf(root.copy(id = "${rule.packageName}:id/dialog"), close)))
    }
    @Test fun sportWorldHomeOnlyClicksFloatingAdClose() {
        val rule = AdSkipRules.bundled.rules.single { it.id == "sportworld-floating-close" }
        val container = node(rule, "tianmu_suspend_rl_container", clickable = true).copy(
            parent = null, bounds = AdSkipBounds(1020, 2234, 1280, 2494)
        )
        val close = node(rule, "tianmu_suspend_iv_close", clickable = true, parent = 0).copy(
            bounds = AdSkipBounds(1215, 2234, 1280, 2299)
        )
        assertEquals(1, AdSkipMatcher.findTarget(rule, listOf(container, close)))
        assertEquals(1, AdSkipMatcher.clickTarget(rule, listOf(container, close), 1))
        assertNull(AdSkipMatcher.findTarget(rule, listOf(container, close.copy(id = "${rule.packageName}:id/tianmu_suspend_iv_pic"))))
    }
    @Test fun sportWorldInlineAdOnlyClicksCloseInsideInlineCard() {
        val rule = AdSkipRules.bundled.rules.single { it.id == "sportworld-inline-close" }
        val card = node(rule, "inter_item_fill", clickable = true).copy(parent = null,
            bounds = AdSkipBounds(93, 1042, 1187, 1657))
        val close = node(rule, "ad_close", clickable = true, parent = 0).copy(
            description = "关闭", bounds = AdSkipBounds(1090, 1050, 1150, 1110))
        assertEquals(1, AdSkipMatcher.findTarget(rule, listOf(card, close)))
        assertNull(AdSkipMatcher.findTarget(rule, listOf(card.copy(id = "${rule.packageName}:id/feed_area"), close)))
    }
    @Test fun sportWorldSplashSkipIsBoundToSplashContainer() {
        val rule = AdSkipRules.bundled.rules.single { it.id == "sportworld-splash-skip" }
        val root = node(rule, "fl_container", clickable = false).copy(parent = null, bounds = AdSkipBounds(0, 0, 1280, 2772))
        val skip = AdSkipNode(null, "跳过5秒", true, true, false, 0,
            className = "android.view.View", packageName = rule.packageName,
            bounds = AdSkipBounds(1093, 542, 1171, 594))
        assertEquals(1, AdSkipMatcher.findTarget(rule, listOf(root, skip)))
        assertNull(AdSkipMatcher.findTarget(rule, listOf(root.copy(id = "${rule.packageName}:id/cl_root"), skip)))
    }
    @Test fun mybusSplashSkipIsBoundToSplashSkipFrame() {
        val rule = AdSkipRules.bundled.rules.single { it.id == "mybus-splash-skip" }
        val frame = node(rule, "ptgSplashSkipFl", clickable = false).copy(parent = null,
            bounds = AdSkipBounds(1054, 114, 1280, 231))
        val skip = node(rule, "ptgSkipLayout", clickable = true, parent = 0).copy(
            bounds = AdSkipBounds(1087, 114, 1247, 198))
        assertEquals(1, AdSkipMatcher.findTarget(rule, listOf(frame, skip)))
        assertNull(AdSkipMatcher.findTarget(rule, listOf(frame.copy(id = "${rule.packageName}:id/view_top"), skip)))
    }
    @Test fun mybusFeedCloseNeverMatchesAnnouncementOrLandingLink() {
        val rule = AdSkipRules.bundled.rules.single { it.id == "mybus-native-feed-close" }
        val card = node(rule, "ad_frameLayout", clickable = false)
        val close = node(rule, "ad_close", parent = 0)
        val announcement = node(rule, "iv_close_homepage", parent = 0)
        val link = node(rule, "ad_action", parent = 0)
        val tree = listOf(card, link, announcement, close)
        assertEquals(3, AdSkipMatcher.findTarget(rule, tree))
        assertEquals(listOf(3), AdSkipMatcher.clickCandidates(rule, tree, 3))
        assertNull(AdSkipMatcher.findTarget(rule, tree.dropLast(1)))
        assertNull(AdSkipMatcher.findTarget(rule, listOf(card.copy(id = "${rule.packageName}:id/new_view2"), close)))
    }
    @Test fun mybusMsDrawnSkipUsesOwnCenterInsideKnownAdOverlay() {
        val rule = AdSkipRules.bundled.rules.single { it.id == "mybus-ms-splash-skip" }
        val root = node(rule, "adsRl", clickable = false)
        val background = node(rule, "ms_splash_click_bg", clickable = true, parent = 0)
        val frame = node(rule, "ms_skipView_container", clickable = false, parent = 0)
        val skip = node(rule, "ms_skipView", clickable = false, parent = 2).copy(
            bounds = AdSkipBounds(1117, 128, 1234, 245))
        val tree = listOf(root, background, frame, skip)
        assertEquals(3, AdSkipMatcher.findTarget(rule, tree))
        assertTrue(AdSkipMatcher.clickCandidates(rule, tree, 3).isEmpty())
        assertTrue(rule.allowGesture)
        assertNull(AdSkipMatcher.findTarget(rule, tree.dropLast(1)))
        assertNull(AdSkipMatcher.findTarget(rule, tree.toMutableList().apply { set(0, root.copy(id = "${rule.packageName}:id/content")) }))
        assertFalse(AdSkipMatcher.matchesActivity(rule, "com.mygolbs.mybus.NewHomePageActivity"))
    }
    @Test fun mybusOctopusCloseRequiresCompleteObservedFingerprint() {
        val rule = AdSkipRules.bundled.rules.single { it.id == "mybus-octopus-interstitial-close" }
        val root = node(rule, "", clickable = false).copy(parent = null,
            className = "android.widget.FrameLayout", bounds = AdSkipBounds(0, 152, 1280, 2720))
        val card = node(rule, "", clickable = false).copy(parent = 0,
            className = "android.widget.FrameLayout", childCount = 6, bounds = AdSkipBounds(142, 593, 1137, 2278))
        val ad = node(rule, "", clickable = true, parent = 1).copy(
            className = "android.widget.ImageView", index = 0, childCount = 0)
        val feedback = node(rule, "", "反馈", parent = 1).copy(
            className = "android.widget.TextView", index = 1, childCount = 0)
        val close = node(rule, "", clickable = true, parent = 1).copy(
            className = "android.widget.ImageView", index = 2, childCount = 0,
            bounds = AdSkipBounds(1027, 664, 1095, 732))
        val shake = node(rule, "", clickable = false, parent = 1).copy(
            className = "android.widget.LinearLayout", index = 3, childCount = 2)
        val shakeText = node(rule, "", "摇一摇跳转至\n第三方应用或详情页", false, 5).copy(
            className = "android.widget.TextView", index = 1, childCount = 0)
        val tree = listOf(root, card, ad, feedback, close, shake, shakeText)
        assertEquals(4, AdSkipMatcher.findTarget(rule, tree))
        assertEquals(listOf(4), AdSkipMatcher.clickCandidates(rule, tree, 4))
        for ((index, changed) in listOf(
            1 to card.copy(childCount = 5), 3 to feedback.copy(text = "分享"),
            4 to close.copy(clickable = false), 4 to close.copy(index = 0),
            6 to shakeText.copy(text = "查看详情"), 6 to shakeText.copy(parent = 0),
        )) assertNull(AdSkipMatcher.findTarget(rule, tree.toMutableList().apply { set(index, changed) }))
        assertNull(AdSkipMatcher.findTarget(rule, tree.dropLast(1)))
        assertTrue(AdSkipMatcher.matchesActivity(rule, ""))
        assertTrue(AdSkipMatcher.matchesActivity(rule, "com.octopus.ad.AdActivity"))
        assertFalse(AdSkipMatcher.matchesActivity(rule, "com.mygolbs.mybus.NewHomePageActivity"))
        assertFalse(AdSkipMatcher.matchesActivity(rule.copy(allowUnknownActivity = false), ""))
        reject(AdSkipRules.bundled.copy(rules = listOf(rule.copy(requiredSelectors = emptyList()))))
        reject(AdSkipRules.bundled.copy(rules = listOf(rule.copy(activityNames = emptyList()))))
        reject(AdSkipRules.bundled.copy(rules = listOf(rule.copy(selectors = listOf(AdSkipSelector(className = "ImageView"))))))
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
    @Test fun rapidSplashPollingStopsAtHomeAndHasABoundedLaunchWindow() {
        assertTrue(AdSkipTiming.rapidSplash("com.yiban.app", "com.yiban.app.activity.WelcomeActivity", 1000, 8000))
        assertTrue(AdSkipTiming.rapidSplash("com.yiban.app", "", 1000, 1050))
        assertFalse(AdSkipTiming.rapidSplash("com.yiban.app", "com.yiban.app.home.activity.HomeActivityV2", 1000, 1100))
        assertFalse(AdSkipTiming.rapidSplash("com.yiban.app", "", 1000, 9000))
        assertFalse(AdSkipTiming.rapidSplash("com.cainiao.wireless", "", 1000, 1100))
        assertFalse(AdSkipTiming.rapidSplash("com.yiban.app", "", 1000, 999))
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
