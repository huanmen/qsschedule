package cn.qztc.timetable.adskip

import org.junit.Assert.*
import org.junit.Test

class AdSkipEngineTest {
    private val rule = AdSkipRules.bundled.rules.first()
    private fun node(text: String? = null, desc: String? = null, parent: Int? = null, clickable: Boolean = false,
                     bounds: AdSkipBounds = AdSkipBounds(800, 100, 1000, 180)) =
        AdSkipNode(null, text, true, true, clickable, parent, desc, "android.widget.TextView", bounds, rule.packageName)

    @Test fun skipLabelsSupportCountdownDescriptionAndMissingId() {
        for (label in listOf("跳过", "跳过广告", "跳过 3s", "5 秒 | 跳过", "Skip Ad", "Skip (3)", "关闭广告")) {
            assertTrue(label, AdSkipRules.isSkipLabel(label))
            assertEquals(label, 0, AdSkipMatcher.findTarget(rule, listOf(node(text = label))))
            assertEquals(label, 0, AdSkipMatcher.findTarget(rule, listOf(node(desc = label))))
        }
    }

    @Test fun genericClosePurchaseAndUnrelatedTextDoNotMatch() {
        for (label in listOf("关闭", "关闭通知", "不再显示", "立即购买", "跳过本次登录", "会员可跳过广告", "跳过支付", "5s", "X")) {
            assertFalse(label, AdSkipRules.isSkipLabel(label))
            assertNull(label, AdSkipMatcher.findTarget(rule.copy(targetId = null), listOf(node(text = label))))
        }
    }

    @Test fun packageVisibilityAndEnabledAreMandatory() {
        val n = node("跳过")
        for (invalid in listOf(n.copy(packageName = "com.android.settings"), n.copy(visible = false), n.copy(enabled = false))) {
            assertNull(AdSkipMatcher.findTarget(rule, listOf(invalid)))
        }
    }

    @Test fun missingActivityIsAllowedOnlyWhenRuleHasNoActivityRestriction() {
        assertTrue(AdSkipMatcher.matchesActivity(rule, ""))
        val scoped = rule.copy(activityNames = listOf("example.SplashActivity"))
        assertFalse(AdSkipMatcher.matchesActivity(scoped, ""))
        assertFalse(AdSkipMatcher.matchesActivity(scoped, "example.OtherActivity"))
        assertTrue(AdSkipMatcher.matchesActivity(scoped, "example.SplashActivity"))
    }

    @Test fun nonClickableCloseNeverClicksAnyAdvertisingAncestor() {
        val nodes = listOf(
            node(parent = null, clickable = true, bounds = AdSkipBounds(0, 0, 1080, 2400)),
            node(parent = 0, clickable = true, bounds = AdSkipBounds(780, 90, 1020, 190)),
            node("跳过 5s", parent = 1),
        )
        assertTrue(AdSkipMatcher.clickCandidates(rule, nodes, 2).isEmpty())
        assertTrue(AdSkipMatcher.clickCandidates(rule.copy(clickableAncestor = false), nodes, 2).isEmpty())
        val clickable = nodes.toMutableList().apply { set(2, nodes[2].copy(clickable = true)) }
        assertEquals(listOf(2), AdSkipMatcher.clickCandidates(rule, clickable, 2))
    }

    @Test fun selectorConditionsAreAndAndAlternativesAreOr() {
        val condition = AdSkipSelector(text = "跳过", className = "android.widget.Button", clickable = true)
        val restricted = rule.copy(targetId = null, selectors = listOf(condition))
        assertNull(AdSkipMatcher.findTarget(restricted, listOf(node("跳过", clickable = true))))
        assertEquals(0, AdSkipMatcher.findTarget(restricted, listOf(node("跳过", clickable = true).copy(className = "android.widget.Button"))))
        assertEquals(0, AdSkipMatcher.findTarget(restricted.copy(selectors = listOf(condition, AdSkipSelector(description = "关闭广告"))), listOf(node(desc = "关闭广告"))))
    }

    @Test fun unspecifiedTargetAndUnscopedCloseAreRejected() {
        for (s in listOf(AdSkipSelector(), AdSkipSelector(text = "关闭"), AdSkipSelector(id = "close"))) {
            assertThrows(IllegalArgumentException::class.java) {
                AdSkipRules.validate(AdSkipRules.bundled.copy(rules = listOf(rule.copy(targetId = null, selectors = listOf(s)))))
            }
        }
    }

    private fun importSelector(selector: String): AdSkipImportPreview {
        val escaped = selector.replace("\\", "\\\\").replace("\"", "\\\"")
        return AdSkipImport.parse("""{"name":"test","version":1,"apps":[{"id":"org.example.reader","groups":[{"name":"广告","rules":[{"matches":"$escaped"}]}]}]}""")
    }

    @Test fun importSupportsTextOnlyWithoutDiscardingClickability() {
        val imported = importSelector("[text=\"跳过\"][clickable=true]").pack!!.rules.single()
        assertNull(imported.targetId)
        assertTrue(imported.activityNames.isEmpty())
        assertNull(AdSkipMatcher.findTarget(imported, listOf(node("跳过").copy(packageName = imported.packageName))))
        assertEquals(0, AdSkipMatcher.findTarget(imported, listOf(node("跳过", clickable = true).copy(packageName = imported.packageName))))
    }

    @Test fun importRejectsRelationsAndContradictoryConditions() {
        for (selector in listOf("[text=\"跳过\"] < [vid=\"ad\"]", "[text=\"跳过\"][clickable=true][clickable=false]", "[text=\"立即购买\"]")) {
            val result = importSelector(selector)
            assertNull(selector, result.pack)
            assertEquals(1, result.issues.size)
        }
    }

    @Test fun absenceNeverClaimsCausalSuccess() {
        assertEquals(AdSkipResult.UNCONFIRMED, AdSkipVerification.result(rule, "launcher", null, false))
        assertEquals(AdSkipResult.UNCONFIRMED, AdSkipVerification.result(rule, rule.packageName, null, null))
        assertTrue(AdSkipVerification.result(rule, rule.packageName, null, false).label.contains("不能确认"))
        assertTrue(AdSkipVerification.result(rule, rule.packageName, rule.successActivityNames.first(), false).label.contains("不能确认"))
    }
}
