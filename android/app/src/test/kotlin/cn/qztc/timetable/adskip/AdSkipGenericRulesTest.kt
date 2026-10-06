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
