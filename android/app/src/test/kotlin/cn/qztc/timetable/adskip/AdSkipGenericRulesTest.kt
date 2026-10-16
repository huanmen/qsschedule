package cn.qztc.timetable.adskip

import org.junit.Assert.*
import org.junit.Test

class AdSkipGenericRulesTest {
    private val pkg = "example.shopping"
    private val rule = AdSkipGenericRules.rule(pkg)
    private val root = AdSkipNode(null, null, true, true, false, bounds = AdSkipBounds(0, 0, 1080, 2400), packageName = pkg)
    private fun target(text: String?, id: String? = null, description: String? = null) =
        AdSkipNode(id, text, true, true, true, 0, description, "android.widget.TextView", AdSkipBounds(860, 100, 1040, 180), pkg)

    @Test fun bilibiliPauseSheetNeedsExactCloseAndItsOwnAdMarker() {
        val bili=AdSkipGenericRules.rule("tv.danmaku.bili")
        val screen=root.copy(packageName=bili.packageName,bounds=AdSkipBounds(0,0,1280,2772))
        val sheet=screen.copy(parent=0,bounds=AdSkipBounds(0,872,1280,2720))
        val close=target(null,description="关闭暂停页").copy(parent=1,packageName=bili.packageName,
            clickable=false,bounds=AdSkipBounds(1163,949,1241,1027),className="android.view.View")
        val marker=target("广告").copy(parent=1,packageName=bili.packageName,bounds=AdSkipBounds(46,1902,98,1936))
        val nodes=listOf(screen,sheet,close,marker)
        assertEquals(2,AdSkipMatcher.findTarget(bili,nodes))
        assertNull(AdSkipMatcher.findTarget(bili,nodes.dropLast(1)))
        assertNull(AdSkipMatcher.findTarget(bili,nodes.toMutableList().apply {set(2,close.copy(description="关闭弹幕"))}))
        assertNull(AdSkipMatcher.findTarget(bili,nodes.toMutableList().apply {set(3,marker.copy(parent=0))}))
        assertNull(AdSkipMatcher.findTarget(bili,nodes.toMutableList().apply {set(1,sheet.copy(bounds=screen.bounds))}))
        assertNull(AdSkipMatcher.findTarget(bili,nodes+close))
        assertTrue(AdSkipSafety.allowsTarget(nodes,2))
    }

    @Test fun bilibiliUsesExactSplashIdsAndNeverSearchOrVideoSkipText() {
        val bili = AdSkipGenericRules.rule("tv.danmaku.bili")
        val screen = root.copy(packageName=bili.packageName)
        val skip = target("跳过 5", "tv.danmaku.bili:id/count_down").copy(packageName=bili.packageName)
        assertEquals(1, AdSkipMatcher.findTarget(bili, listOf(screen, skip)))
        for (id in listOf(null, "tv.danmaku.bili:id/search_src_text", "tv.danmaku.bili:id/player_skip")) {
            assertNull(AdSkipMatcher.findTarget(bili, listOf(screen, skip.copy(id=id))))
        }
        val splash=screen.copy(parent=0, id="tv.danmaku.bili:id/splash_container")
        val drawn=skip.copy(parent=1,text=null)
        assertEquals(2, AdSkipMatcher.findTarget(bili, listOf(screen,splash,drawn)))
        assertNull(AdSkipMatcher.findTarget(bili, listOf(screen,splash.copy(id="tv.danmaku.bili:id/player"),drawn)))
        assertNull(AdSkipMatcher.findTarget(bili, listOf(screen,skip.copy(bounds=AdSkipBounds(400,1100,600,1200)))))
        assertNull(AdSkipMatcher.findTarget(bili, listOf(screen,skip,skip)))
    }
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
        assertNull(AdSkipMatcher.findTarget(rule, listOf(root, target("跳过"), target("广告"))))
    }
    @Test fun descriptionCanIdentifySkipWithoutText() {
        assertEquals(1, AdSkipMatcher.findTarget(rule, listOf(root, target(null, description = "跳过广告"))))
    }
    @Test fun nonClickableSkipNeverClicksFullScreenLandingParent() {
        val parent = root.copy(clickable = true)
        val skip = target("跳过 5s").copy(clickable = false)
        assertEquals(1, AdSkipMatcher.findTarget(rule, listOf(parent, skip)))
        assertNull(AdSkipMatcher.clickTarget(rule, listOf(parent, skip), 1))
        val small = parent.copy(bounds = AdSkipBounds(840, 90, 1060, 190))
        assertEquals(0, AdSkipMatcher.clickTarget(rule, listOf(small, skip), 1))
    }
    @Test fun separateCountdownRequiresEnclosingSplashContainer() {
        val splash = root.copy(id = pkg + ":id/splash_container", parent = 0)
        val countdown = root.copy(id = "countdown-root", parent = 1,
            bounds = AdSkipBounds(840, 90, 1060, 190))
        val skip = target("跳过").copy(parent = 2)
        assertEquals(3, AdSkipMatcher.findTarget(rule, listOf(root, splash, countdown, skip)))
        assertNull(AdSkipMatcher.findTarget(rule, listOf(root, splash.copy(id = pkg + ":id/login"), countdown, skip)))
        assertNull(AdSkipMatcher.findTarget(rule, listOf(root, splash, countdown.copy(id = null), skip)))
        assertNull(AdSkipMatcher.findTarget(rule, listOf(root, splash, countdown, skip.copy(text = "查看详情"))))
    }
    @Test fun renderedCountdownCanContinueOnlyWithItsOwnSplashSkipChild() {
        val splash = root.copy(id = pkg + ":id/splash_container", parent = 0)
        val before = target("跳过5", "countdown-root").copy(parent = 1,
            className = "android.view.View", bounds = AdSkipBounds(840, 120, 1070, 220))
        val countdown = before.copy(text = " ")
        val skip = target("跳过").copy(parent = 2, className = "android.view.View",
            bounds = AdSkipBounds(900, 140, 1000, 200))
        val nodes = listOf(root, splash, countdown, skip)
        assertTrue(AdSkipGenericRules.relocatedTarget(rule, before, nodes, 3))
        for ((index, changed) in listOf(
            1 to splash.copy(id = pkg + ":id/login"),
            2 to countdown.copy(bounds = AdSkipBounds(800, 120, 1070, 220)),
            3 to skip.copy(parent = 0),
            3 to skip.copy(text = "允许"),
            3 to skip.copy(bounds = AdSkipBounds(900, 300, 1000, 360)),
        )) assertFalse(AdSkipGenericRules.relocatedTarget(rule, before,
            nodes.toMutableList().apply { set(index, changed) }, 3))
        assertFalse(AdSkipGenericRules.relocatedTarget(rule, before, nodes + countdown, 3))
    }
    @Test fun sdkSkipWithDrawnTextRequiresSplashAncestry() {
        val splash = root.copy(id = pkg + ":id/ms_splash_click_bg", parent = 0)
        val skip = target(null, pkg + ":id/ms_skipView").copy(parent = 1, clickable = false)
        assertEquals(2, AdSkipMatcher.findTarget(rule, listOf(root, splash, skip)))
        assertNull(AdSkipMatcher.findTarget(rule, listOf(root, splash.copy(id = pkg + ":id/photo_editor"), skip)))
        assertNull(AdSkipMatcher.findTarget(rule, listOf(root, splash, skip.copy(parent = null))))
    }

    @Test fun knownVolunteerAdOverlayAllowsDrawnSdkSkip() {
        val volunteer = AdSkipGenericRules.rule("com.zzw.october")
        val screen = root.copy(packageName = volunteer.packageName)
        val llAd = screen.copy(id = "com.zzw.october:id/ll_ad", parent = 0)
        val outAd = screen.copy(id = "com.zzw.october:id/out_ad", parent = 1)
        val container = screen.copy(id = "com.zzw.october:id/ms_skipView_container", parent = 2,
            bounds = AdSkipBounds(900, 80, 1080, 280))
        val skip = target(null, "com.zzw.october:id/ms_skipView").copy(parent = 3, clickable = false, packageName = volunteer.packageName,
            bounds = AdSkipBounds(940, 100, 1040, 220))
        assertEquals(4, AdSkipMatcher.findTarget(volunteer, listOf(screen, llAd, outAd, container, skip)))
        assertNull(AdSkipMatcher.findTarget(volunteer, listOf(screen, screen.copy(id = "com.zzw.october:id/dialog", parent = 0), outAd.copy(parent = 1), container.copy(parent = 2), skip.copy(parent = 3))))
    }

    @Test fun sdkCountAndSkipContainerRequiresSplashAncestry() {
        val splash = root.copy(id = pkg + ":id/fl_third_splash_container", parent = 0)
        val countAndSkip = root.copy(id = pkg + ":id/fanti_ad_count_and_skip_container_ex", parent = 1,
            bounds = AdSkipBounds(840, 0, 1080, 300))
        val skip = target("跳过").copy(parent = 2)
        assertEquals(3, AdSkipMatcher.findTarget(rule, listOf(root, splash, countAndSkip, skip)))
        assertNull(AdSkipMatcher.findTarget(rule, listOf(root, root.copy(id = pkg + ":id/dialog", parent = 0), countAndSkip.copy(parent = 1), skip)))
    }

    @Test fun weiboDrawnSplashSkipIsLimitedToBottomRightSplashRule() {
        val weiboRule = AdSkipGenericRules.rule("com.sina.weibo")
        val screen = AdSkipNode(null, null, true, true, false,
            bounds = AdSkipBounds(0, 0, 1280, 2772), packageName = "com.sina.weibo")
        val skip = AdSkipNode(null, "跳过", true, true, false, 0,
            bounds = AdSkipBounds(1045, 2570, 1234, 2674), packageName = "com.sina.weibo")
        assertEquals(1, AdSkipMatcher.findTarget(weiboRule, listOf(screen, skip)))
        assertNull(AdSkipMatcher.findTarget(weiboRule, listOf(screen, skip.copy(bounds = AdSkipBounds(400, 1200, 600, 1300)))))
        assertNull(AdSkipMatcher.findTarget(AdSkipGenericRules.rule("com.example.other"), listOf(screen.copy(packageName = "com.example.other"), skip.copy(packageName = "com.example.other"))))
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
    @Test fun closesInterstitialSdkSkipOnlyInsideAdContainer() {
        val popup = AdSkipNode(pkg + ":id/tobid_interstitial_ad_detail", null, true, true, false, 0,
            bounds = AdSkipBounds(90, 420, 990, 1700), packageName = pkg)
        val closeArea = AdSkipNode(pkg + ":id/tobid_interstitial_skip_ll", null, true, true, true, 1,
            bounds = AdSkipBounds(850, 430, 970, 525), packageName = pkg)
        val close = AdSkipNode(pkg + ":id/tobid_interstitial_skip_text", "关闭", true, true, false, 2,
            bounds = AdSkipBounds(880, 445, 940, 505), packageName = pkg)
        val nodes = listOf(root, popup, closeArea, close)
        assertEquals(3, AdSkipMatcher.findTarget(rule, nodes))
        assertEquals(2, AdSkipMatcher.clickTarget(rule, nodes, 3))
        assertNull(AdSkipMatcher.findTarget(rule, listOf(root, popup.copy(id = pkg + ":id/dialog"), closeArea, close)))
        assertNull(AdSkipMatcher.findTarget(rule, listOf(root, popup, closeArea, close.copy(text = "查看详情"))))
        assertNull(AdSkipMatcher.findTarget(rule, listOf(root, popup, closeArea, close.copy(bounds = AdSkipBounds(200, 900, 260, 960)))))
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
    @Test fun nativeLookupStillRequiresRealAdAncestry() {
        val popup = root.copy(id = pkg + ":id/tobid_interstitial_ad_detail", parent = null,
            bounds = AdSkipBounds(90, 420, 990, 1700))
        val close = target("关闭", pkg + ":id/tobid_interstitial_skip_text").copy(parent = 1,
            bounds = AdSkipBounds(880, 445, 940, 505))
        assertEquals(2, AdSkipMatcher.findTarget(rule, listOf(root, popup, close)))
        assertNull(AdSkipMatcher.findTarget(rule, listOf(root, popup.copy(id = null), close)))
        assertNull(AdSkipMatcher.findTarget(rule, listOf(root, popup, close.copy(parent = null))))
    }
    @Test fun excludesForeignHiddenDisabledAndOversizedControls() {
        val valid = target("跳过广告")
        listOf(valid.copy(packageName = "other.app"), valid.copy(visible = false), valid.copy(enabled = false),
            valid.copy(bounds = AdSkipBounds(0, 0, 1080, 2400)), valid.copy(bounds = AdSkipBounds(850, 1000, 1050, 1100)),
            valid.copy(bounds = AdSkipBounds(1000, 100, 1200, 200))).forEach {
            assertNull(AdSkipMatcher.findTarget(rule, listOf(root, it)))
        }
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
