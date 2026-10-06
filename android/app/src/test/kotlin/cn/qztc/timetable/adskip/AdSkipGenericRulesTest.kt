package cn.qztc.timetable.adskip

import org.junit.Assert.*
import org.junit.Test

class AdSkipGenericRulesTest {
    private val pkg = "example.shopping"
    private val rule = AdSkipGenericRules.rule(pkg)
    private val root = AdSkipNode(null, null, true, true, false, bounds = AdSkipBounds(0, 0, 1080, 2400), packageName = pkg)
    private fun target(text: String?, id: String? = null, description: String? = null) =
        AdSkipNode(id, text, true, true, true, 0, description, "android.widget.TextView", AdSkipBounds(860, 100, 1040, 180), pkg)
    @Test fun supportsUnlistedPackageAndCountdownLabels() {
        listOf("跳过 3s", "5 秒 | 跳过", "Skip (3)", "跳过广告", "关闭广告", "Skip Ad").forEach {
            assertEquals(it, 1, AdSkipMatcher.findTarget(rule, listOf(root, target(it))))
        }
    }
    @Test fun neverTreatsOnboardingOrGenericCloseAsAdvertisement() {
        listOf("跳过", "Skip", "关闭", "X", "跳过登录", "跳过本题", "购买", "继续").forEach {
            assertNull(it, AdSkipMatcher.findTarget(rule, listOf(root, target(it))))
        }
    }
    @Test fun plainSkipNeedsAdvertisingContext() {
        assertEquals(1, AdSkipMatcher.findTarget(rule, listOf(root, target("跳过", pkg + ":id/ksad_splash_skip"))))
        assertEquals(1, AdSkipMatcher.findTarget(rule, listOf(root, target("跳过"), target("广告"))))
    }
    @Test fun descriptionCanIdentifySkipWithoutText() {
        assertEquals(1, AdSkipMatcher.findTarget(rule, listOf(root, target(null, description = "跳过广告"))))
    }
    @Test fun closesMarkedPopupAndAccessibleWebAd() {
        val popup = AdSkipNode(pkg + ":id/ad_popup", null, true, true, false, 0,
            bounds = AdSkipBounds(90, 420, 990, 1700), packageName = pkg)
        val ad = AdSkipNode(null, "广告", true, true, false, 1,
            bounds = AdSkipBounds(850, 1610, 940, 1650), packageName = pkg)
        val close = AdSkipNode(null, "×", true, true, true, 1,
            bounds = AdSkipBounds(880, 445, 940, 505), packageName = pkg)
        assertEquals(3, AdSkipMatcher.findTarget(rule, listOf(root, popup, ad, close)))
        val web = popup.copy(id = null, className = "android.webkit.WebView")
        assertEquals(3, AdSkipMatcher.findTarget(rule, listOf(root, web, ad, close)))
        assertEquals(1, AdSkipMatcher.findTarget(rule, listOf(root,
            target(null, description = "Close advertisement").copy(bounds = AdSkipBounds(700, 900, 800, 980)))))
    }
    @Test fun doesNotCloseOrdinaryDialogOrAdLandingLink() {
        val dialog = AdSkipNode(pkg + ":id/header", null, true, true, false, 0,
            bounds = AdSkipBounds(90, 420, 990, 1700), packageName = pkg)
        val close = AdSkipNode(null, "×", true, true, true, 1,
            bounds = AdSkipBounds(880, 445, 940, 505), packageName = pkg)
        assertNull(AdSkipMatcher.findTarget(rule, listOf(root, dialog, close)))
        assertNull(AdSkipMatcher.findTarget(rule, listOf(root, dialog, close.copy(text = "立即查看"),
            AdSkipNode(null, "广告", true, true, false, 1, bounds = AdSkipBounds(850, 1610, 940, 1650), packageName = pkg))))
        assertNull(AdSkipMatcher.findTarget(rule, listOf(root, dialog, close.copy(bounds = AdSkipBounds(200, 900, 260, 960)),
            AdSkipNode(null, "广告", true, true, false, 1, bounds = AdSkipBounds(850, 1610, 940, 1650), packageName = pkg))))
    }
    @Test fun movingCloseMustRemainUniqueAndInsideMarkedAd() {
        val popup = AdSkipNode(pkg + ":id/ad_popup", null, true, true, false, 0,
            bounds = AdSkipBounds(90, 420, 990, 1700), packageName = pkg)
        val close = AdSkipNode(null, "×", true, true, true, 1,
            bounds = AdSkipBounds(880, 445, 940, 505), packageName = pkg)
        val marker = AdSkipNode(null, "广告", true, true, false, 1,
            bounds = AdSkipBounds(850, 1610, 940, 1650), packageName = pkg)
        val moved = close.copy(bounds = AdSkipBounds(840, 445, 900, 505))
        assertTrue(AdSkipGenericRules.relocatedTarget(rule, close, listOf(root, popup, marker, moved), 3))
        assertFalse(AdSkipGenericRules.relocatedTarget(rule, close, listOf(root, popup, marker, moved, close), 3))
        assertFalse(AdSkipGenericRules.relocatedTarget(rule, close, listOf(root, popup.copy(id = null), moved), 2))
    }
    @Test fun excludesForeignHiddenDisabledAndOversizedControls() {
        val valid = target("跳过广告")
        listOf(valid.copy(packageName = "other.app"), valid.copy(visible = false), valid.copy(enabled = false),
            valid.copy(bounds = AdSkipBounds(0, 0, 1080, 2400)), valid.copy(bounds = AdSkipBounds(850, 1000, 1050, 1100)),
            valid.copy(bounds = AdSkipBounds(1000, 100, 1200, 200))).forEach {
            assertNull(AdSkipMatcher.findTarget(rule, listOf(root, it)))
        }
    }
    @Test fun supportsImageCloseWithClickableParentForInterstitial() {
        val ad = AdSkipNode(null, null, true, true, false, 0,
            className = "android.widget.ImageView", bounds = AdSkipBounds(120, 420, 960, 1500), packageName = pkg)
        val parent = AdSkipNode(null, null, true, true, true, 0,
            className = "android.widget.FrameLayout", bounds = AdSkipBounds(500, 1600, 630, 1730), packageName = pkg)
        val close = AdSkipNode(null, null, true, true, false, 2,
            className = "android.widget.ImageView", bounds = AdSkipBounds(500, 1600, 630, 1730), packageName = pkg)
        assertEquals(3, AdSkipMatcher.findTarget(rule, listOf(root, ad, parent, close)))
        assertEquals(listOf(2), AdSkipMatcher.clickCandidates(rule, listOf(root, ad, parent, close), 3))
    }
    @Test fun supportsCanvasSplashContainerAsLastResortTarget() {
        val splash = AdSkipNode(pkg + ":id/splash_ad_container", null, true, true, false, 0,
            className = "android.widget.FrameLayout", bounds = AdSkipBounds(0, 0, 1080, 2200), packageName = pkg)
        assertEquals(1, AdSkipMatcher.findTarget(rule, listOf(root, splash)))
    }
    @Test fun untrustedFilesCannotOverrideGlobalRule() {
        assertThrows(IllegalArgumentException::class.java) {
            AdSkipRules.validate(AdSkipRulePack(2, 1, "test", "test", listOf(rule)), true)
        }
    }
    @Test fun logsOnlyTerminalOutcomesAndNeverClickAcceptance() {
        assertNull(AdSkipOutcome.label(AdSkipResult.CLICK_ACCEPTED))
        assertNull(AdSkipOutcome.label(AdSkipResult.GESTURE_COMPLETED))
        assertNull(AdSkipOutcome.label(AdSkipResult.NO_MATCH))
        assertEquals("成功", AdSkipOutcome.label(AdSkipResult.TARGET_GONE))
        assertEquals("失败", AdSkipOutcome.label(AdSkipResult.UNCONFIRMED))
        assertEquals("失败", AdSkipOutcome.label(AdSkipResult.TARGET_PRESENT))
    }
}
