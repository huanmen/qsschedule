package cn.qztc.timetable.adskip

import org.junit.Assert.*
import org.junit.Test

class AdSkipSafetyTest {
    @Test fun animatedTargetsOutsidePhysicalDisplayCannotConsumeAnAttempt() {
        val display = AdSkipBounds(0, 0, 1280, 2772)
        assertFalse(AdSkipSafety.onDisplay(AdSkipBounds(1403, 2367, 1508, 2472), display))
        assertFalse(AdSkipSafety.onDisplay(AdSkipBounds(-40, 2367, 65, 2472), display))
        assertFalse(AdSkipSafety.onDisplay(null, display))
        assertTrue(AdSkipSafety.onDisplay(AdSkipBounds(65, 2476, 182, 2593), display))
    }
    private val pkg = "example.app"
    private val root = AdSkipNode(null, null, true, true, false, bounds = AdSkipBounds(0, 0, 1080, 2400), packageName = pkg)
    private val dialog = root.copy(parent = 0, bounds = AdSkipBounds(80, 700, 1000, 1500))
    private val title = root.copy(parent = 1, text = "开启通知，不错过消息提醒")
    private val allow = root.copy(parent = 1, text = "立即开启", clickable = true)
    private val close = root.copy(parent = 1, id = "$pkg:id/ad_close", clickable = true,
        bounds = AdSkipBounds(900, 730, 960, 790))
    @Test fun appNotificationPromptAndFakeConsentCannotBeClicked() {
        val tree = listOf(root, dialog, title, allow, close)
        assertFalse(AdSkipSafety.allowsTarget(tree, 3))
        assertFalse(AdSkipSafety.allowsTarget(tree, 4))
        for (text in listOf("允许", "去设置", "暂不开启", "取消"))
            assertFalse(AdSkipSafety.allowsTarget(listOf(root, allow.copy(text = text)), 1))
    }
    @Test fun systemPermissionWindowProtectsEvenUnderlyingAd() {
        val tree = listOf(root, close.copy(parent = 0), title.copy(packageName = "com.android.permissioncontroller"))
        assertFalse(AdSkipSafety.allowsTarget(tree, 1))
    }
    @Test fun fakeNotificationInsideCreativeDoesNotBlockIndependentSdkClose() {
        val sdkClose = close.copy(parent = 0, bounds = AdSkipBounds(900, 80, 960, 140))
        assertTrue(AdSkipSafety.allowsTarget(listOf(root, dialog, title, allow, close, sdkClose), 5))
        assertTrue(AdSkipSafety.allowsTarget(listOf(root, dialog, title.copy(text = "广告"), allow.copy(text = "查看详情"), close), 4))
    }
    @Test fun headsUpNotificationBlocksCoveredTopSkipButNotBottomSkip() {
        val notification = AdSkipBounds(0, 80, 1080, 450)
        assertFalse(AdSkipSafety.unobscured(AdSkipBounds(900, 100, 1020, 180), listOf(notification)))
        assertTrue(AdSkipSafety.unobscured(AdSkipBounds(60, 2150, 150, 2240), listOf(notification)))
        assertTrue(AdSkipSafety.unobscured(AdSkipBounds(900, 100, 1020, 180), emptyList()))
    }
    @Test fun statusBarOverlappingOnlySixPixelsDoesNotBlockActualTap() {
        val button = AdSkipBounds(896, 146, 1182, 244)
        assertTrue(AdSkipSafety.unobscured(button, listOf(AdSkipBounds(0, 0, 1280, 152))))
        assertFalse(AdSkipSafety.unobscured(button, listOf(AdSkipBounds(0, 0, 1280, 250))))
    }

    @Test fun deeplyNestedAdCloseIsNotRejectedWhenNoConsentExists() {
        val nodes = (0..30).map { index ->
            AdSkipNode(
                id = if (index == 30) "example.app:id/ad_close" else null,
                text = null, visible = true, enabled = true, clickable = index == 30,
                parent = if (index == 0) null else index - 1,
                bounds = if (index == 30) AdSkipBounds(900, 2100, 960, 2160) else AdSkipBounds(0, 0, 1080, 2400),
                packageName = pkg,
            )
        }
        assertTrue(AdSkipSafety.allowsTarget(nodes, 30))
        val consentDialog = nodes.toMutableList().apply {
            set(29, nodes[29].copy(bounds = AdSkipBounds(80, 700, 1000, 2300)))
            add(title.copy(parent = 29))
            add(allow.copy(parent = 29))
        }
        assertFalse(AdSkipSafety.allowsTarget(consentDialog, 30))
        assertFalse(AdSkipSafety.allowsTarget(nodes.toMutableList().apply {
            set(1, nodes[1].copy(parent = 30))
        }, 30))
    }
}
