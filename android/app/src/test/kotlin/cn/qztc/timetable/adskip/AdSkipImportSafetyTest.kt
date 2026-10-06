package cn.qztc.timetable.adskip

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AdSkipImportSafetyTest {
    private val pkg = "org.example.reader"
    private fun parse(vararg selectors: String, extra: String = "") = AdSkipImport.parse("""
        {name:'test',version:1,apps:[{id:'$pkg',groups:[{name:'弹窗广告',activityIds:'.MainActivity',
        rules:[{matches:${JsonArray(selectors.map(::JsonPrimitive))}$extra}]}]}]}
    """)
    private fun n(clazz: String, parent: Int? = null, index: Int? = null, count: Int = 0, clickable: Boolean = false, text: String? = null) =
        AdSkipNode(null, text, true, true, clickable, parent, className = "android.view.$clazz",
            packageName = pkg, index = index, childCount = count)

    @Test fun popupAdImageIsNotTheAdjacentCloseImage() {
        val preview = parse("View[childCount=3] > View + ImageView + ImageView[clickable=true][childCount=0]")
        assertEquals(emptyList<AdSkipImportIssue>(), preview.issues)
        val rule = preview.pack!!.rules.single()
        val nodes = listOf(n("View", count = 3), n("View", 0, 0), n("ImageView", 0, 1, clickable = true), n("ImageView", 0, 2, clickable = true))
        assertEquals(3, AdSkipMatcher.findTarget(rule, nodes))
        assertNull(AdSkipMatcher.findTarget(rule, nodes.dropLast(1)))
        assertNull(AdSkipMatcher.findTarget(rule, nodes.toMutableList().apply { set(0, nodes[0].copy(childCount = 4)) }))
        assertNull(AdSkipMatcher.findTarget(rule, nodes.toMutableList().apply { set(3, nodes[3].copy(childCount = 1)) }))
    }

    @Test fun matchesAreAllRequiredAndTheLastSelectorReceivesClick() {
        val rule = parse("[text=\"广告\"]", "[text=\"跳过\"]").pack!!.rules.single()
        assertNull(AdSkipMatcher.findTarget(rule, listOf(n("TextView", text = "跳过"))))
        assertEquals(1, AdSkipMatcher.findTarget(rule, listOf(n("TextView", text = "广告"), n("TextView", text = "跳过"))))
    }

    @Test fun preservesIndicesLengthsAndFullClassNames() {
        val rule = parse("android.view.TextView[text^=\"跳过\"][text.length<=10][index=2][childCount=0]").pack!!.rules.single()
        val nodes = listOf(n("TextView", index = 2, text = "跳过 3s"))
        assertEquals(0, AdSkipMatcher.findTarget(rule, nodes))
        assertNull(AdSkipMatcher.findTarget(rule, listOf(nodes[0].copy(index = 1))))
        assertNull(AdSkipMatcher.findTarget(rule, listOf(nodes[0].copy(text = "跳过这段文字就能领取优惠券"))))
    }

    @Test fun refusesUnsupportedSyntaxInsteadOfDroppingIt() {
        for (selector in listOf("@[text=\"跳过\"]<<n [vid=\"ad\"]", "[text=\"跳过\"][width<100]", "[text=\"跳过\"][desc*=\"广告\"]")) {
            assertNull(selector, parse(selector).pack)
        }
        assertNull(parse("[text=\"跳过\"]", extra = ",matchDelay:800").pack)
        assertNull(parse("[text=\"跳过\"]", extra = ",preKeys:[1]").pack)
    }

    @Test fun anyMatchesDoesNotReplaceRequiredMatches() {
        val rule = parse("[text=\"跳过\"]", extra = ",anyMatches:['[text=\"广告\"]','[text=\"赞助\"]']").pack!!.rules.single()
        assertNull(AdSkipMatcher.findTarget(rule, listOf(n("TextView", text = "跳过"))))
        assertEquals(1, AdSkipMatcher.findTarget(rule, listOf(n("TextView", text = "赞助"), n("TextView", text = "跳过"))))
    }

    private fun ltt(action: String): AdSkipRule {
        val config = buildJsonObject { put("popup_rules", buildJsonArray { add(buildJsonObject {
            put("id", "广告关闭"); put("action", action)
        }) }) }
        val source = buildJsonArray { add(buildJsonObject { put(pkg.hashCode().toString(), config.toString()) }) }
        return AdSkipImport.parse(source.toString(), mapOf(pkg to "示例应用")).pack!!.rules.single()
    }

    @Test fun lttAmpersandRequiresBothTermsOnTheSameNode() {
        val rule = ltt("+跳过&-广告")
        assertNull(AdSkipMatcher.findTarget(rule, listOf(n("TextView", text = "跳过"), n("TextView", text = "广告"))))
        assertEquals(0, AdSkipMatcher.findTarget(rule, listOf(n("TextView", text = "跳过此广告"))))
        assertNull(AdSkipMatcher.findTarget(rule, listOf(n("TextView", text = "广告在这里跳过"))))
    }

    @Test fun lttLabelsCanMatchDescriptionsWithoutWeakeningEquality() {
        val rule = ltt("=跳过广告")
        assertEquals(0, AdSkipMatcher.findTarget(rule, listOf(n("ImageView").copy(description = "跳过广告"))))
        assertNull(AdSkipMatcher.findTarget(rule, listOf(n("ImageView").copy(description = "购买会员跳过广告"))))
    }
}
