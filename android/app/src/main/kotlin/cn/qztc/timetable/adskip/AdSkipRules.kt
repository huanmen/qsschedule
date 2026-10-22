package cn.qztc.timetable.adskip

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Data only: deliberately NOT a GKD selector interpreter or executable plugin loader. */
@Serializable
data class AdSkipRule(
    val id: String,
    val name: String,
    val packageName: String,
    val activityNames: List<String> = emptyList(),
    val targetId: String? = null,
    val textPrefix: String? = null,
    val ancestorId: String? = null,
    val clickParentId: String? = null,
    val successActivityNames: List<String> = emptyList(),
    val cooldownMs: Long = 6000,
    val matchWindowMs: Long = 10000,
    val appLabel: String? = null,
    val selectors: List<AdSkipSelector> = emptyList(),
    val allowGesture: Boolean = false,
    val clickableAncestor: Boolean = false,
    /** GKD action; kept separate so imports do not silently turn back/none into clicks. */
    val action: String = "click",
    val actionMaximum: Int = 0,
    val preKeys: List<Int> = emptyList(),
    val excludedActivityNames: List<String> = emptyList(),
    /** All groups must exist; the action target is still selected by selectors. */
    val requiredSelectors: List<List<AdSkipSelector>> = emptyList(),
    val adapterVersion: Int = 0,
    /** Recovery after service reconnect, gated by a complete structural fingerprint. */
    val allowUnknownActivity: Boolean = false,
)

/** Conditions within a selector are AND; selectors within a rule are OR. */
@Serializable
data class AdSkipSelector(
    val id: String? = null,
    val text: String? = null,
    val textPrefix: String? = null,
    val textContains: String? = null,
    val description: String? = null,
    val className: String? = null,
    val clickable: Boolean? = null,
    val ancestorId: String? = null,
    val skipLabel: Boolean = false,
    val textRegex: String? = null,
    val index: Int? = null,
    val childCount: Int? = null,
    val maxTextLength: Int? = null,
    val parentSelector: AdSkipSelector? = null,
    val previousSiblingSelector: AdSkipSelector? = null,
    val labelMatches: List<AdSkipLabelMatch> = emptyList(),
)

@Serializable
data class AdSkipLabelMatch(val value: String, val operation: String)

@Serializable
data class AdSkipRulePack(
    val schemaVersion: Int = 1,
    val version: Int,
    val name: String,
    val source: String,
    val rules: List<AdSkipRule>,
)

object AdSkipRules {
    const val MAX_BYTES = 4 * 1024 * 1024
    val appNames = linkedMapOf(
        "com.yiban.app" to "易班",
        "com.chaoxing.mobile" to "学习通",
        "com.mygolbs.mybus" to "掌上公交",
        "com.zjwh.android_wh_physicalfitness" to "运动世界校园",
        "com.zzw.october" to "志愿汇",
        "com.cainiao.wireless" to "菜鸟",
        "com.zhishan.washer" to "悠洗",
        "com.realtech.xiaocan" to "小蚕惠生活",
        "com.tencent.mtt" to "QQ浏览器",
    )
    private val json = Json { prettyPrint = true }
    val bundled = AdSkipRulePack(
        schemaVersion = 2,
        version = 20261006,
        name = "校园广告辅助 · 基础规则",
        source = "本机历史控件与明确跳过文案规则；2026-10-05 引擎修订，实际效果待逐应用验证",
        rules = listOf(
            AdSkipRule(
                "yiban-splash", "开屏广告", "com.yiban.app",
                emptyList(), "btn_splash_ad",
                textPrefix = "跳过",
                selectors = listOf(AdSkipSelector(skipLabel = true)),
                allowGesture = true, clickableAncestor = true,
                successActivityNames = listOf("com.yiban.app.home.activity.HomeActivityV2"),
            ),
            AdSkipRule(
                "chaoxing-splash", "开屏广告", "com.chaoxing.mobile",
                emptyList(), "btn_jump",
                textPrefix = "跳过",
                selectors = listOf(AdSkipSelector(skipLabel = true)),
                allowGesture = true, clickableAncestor = true,
                successActivityNames = listOf("com.chaoxing.mobile.main.ui.MainTabActivity"),
            ),
            AdSkipRule(
                "mybus-route-banner", "线路文字横幅", "com.mygolbs.mybus",
                listOf("com.mygolbs.mybus.RTimeActivity"), "ll_textad_close",
                ancestorId = "textad_view", matchWindowMs = 0,
                allowGesture = true, clickableAncestor = true,
            ),
            AdSkipRule(
                id = "mybus-interstitial-close", name = "居中弹窗广告关闭",
                packageName = "com.mygolbs.mybus", appLabel = "掌上公交",
                activityNames = listOf("cj.mobile.fw.activity.PtgInteractionPortraitActivity"),
                targetId = "closeAdvertLayout", ancestorId = "ptgRootAdvertLayout",
                matchWindowMs = 0, cooldownMs = 6000,
            ),
            AdSkipRule(
                id = "mybus-native-feed-close", name = "信息流广告关闭",
                packageName = "com.mygolbs.mybus", appLabel = "掌上公交",
                targetId = "ad_close", ancestorId = "ad_frameLayout",
                matchWindowMs = 0, cooldownMs = 6000, allowGesture = true,
            ),
            // Observed Octopus card: image, feedback, close, shake panel, SDK marks.
            // The full fingerprint also handles reconnecting into an existing ad.
            AdSkipRule(
                id = "mybus-octopus-interstitial-close", name = "插屏广告关闭",
                packageName = "com.mygolbs.mybus", appLabel = "掌上公交",
                activityNames = listOf("com.octopus.ad.AdActivity"),
                allowUnknownActivity = true,
                matchWindowMs = 0, cooldownMs = 6000,
                selectors = listOf(AdSkipSelector(
                    className = "ImageView", clickable = true, index = 2, childCount = 0,
                    parentSelector = AdSkipSelector(className = "FrameLayout", childCount = 6),
                    previousSiblingSelector = AdSkipSelector(className = "TextView", text = "反馈", clickable = true),
                )),
                requiredSelectors = listOf(listOf(AdSkipSelector(
                    className = "TextView", textContains = "第三方应用或详情页",
                    parentSelector = AdSkipSelector(
                        className = "LinearLayout", index = 3,
                        parentSelector = AdSkipSelector(className = "FrameLayout", childCount = 6),
                        previousSiblingSelector = AdSkipSelector(
                            className = "ImageView", clickable = true, index = 2, childCount = 0,
                            previousSiblingSelector = AdSkipSelector(className = "TextView", text = "反馈"),
                        ),
                    ),
                ))),
                successActivityNames = listOf("com.mygolbs.mybus.NewHomePageActivity"),
            ),
            // The launch splash exposes a clickable container before the
            // drawn text child gets a stable accessibility node. Binding both
            // IDs makes the first action fast while retaining a strict scene
            // boundary.
            AdSkipRule(
                id = "mybus-splash-skip", name = "开屏跳过",
                packageName = "com.mygolbs.mybus", appLabel = "掌上公交",
                activityNames = listOf("com.mygolbs.mybus.LoginActivity"),
                targetId = "ptgSkipLayout", ancestorId = "ptgSplashSkipFl",
                successActivityNames = listOf("com.mygolbs.mybus.NewHomePageActivity"),
                matchWindowMs = 0, cooldownMs = 3000, allowGesture = true,
            ),
            AdSkipRule(
                id = "mybus-ms-splash-skip", name = "开屏跳过",
                packageName = "com.mygolbs.mybus", appLabel = "掌上公交",
                activityNames = listOf("com.mygolbs.mybus.LoginActivity"),
                targetId = "ms_skipView", ancestorId = "adsRl",
                successActivityNames = listOf("com.mygolbs.mybus.NewHomePageActivity"),
                matchWindowMs = 0, cooldownMs = 3000, allowGesture = true,
            ),
            // Observed on 2026-10-08: 运动世界校园's interstitial uses a
            // top-left `ad_close` control inside its `ad_container`.
            AdSkipRule(
                id = "sportworld-interstitial-close", name = "插屏广告关闭",
                packageName = "com.zjwh.android_wh_physicalfitness", appLabel = "运动世界校园",
                targetId = "ad_close", ancestorId = "ad_container",
                matchWindowMs = 0, cooldownMs = 6000,
            ),
            AdSkipRule(
                id = "sportworld-splash-skip", name = "开屏倒计时跳过",
                packageName = "com.zjwh.android_wh_physicalfitness", appLabel = "运动世界校园",
                activityNames = listOf("com.zjwh.android_wh_physicalfitness.mvi.splash.SplashActivity"),
                successActivityNames = listOf("com.zjwh.android_wh_physicalfitness.mvi.home.HomeActivity"),
                ancestorId = "fl_container", matchWindowMs = 0, allowGesture = true,
                selectors = listOf(AdSkipSelector(skipLabel = true, ancestorId = "fl_container")),
            ),
            AdSkipRule(
                id = "sportworld-floating-close", name = "首页浮动广告关闭",
                packageName = "com.zjwh.android_wh_physicalfitness", appLabel = "运动世界校园",
                activityNames = listOf("com.zjwh.android_wh_physicalfitness.mvi.home.HomeActivity"),
                targetId = "tianmu_suspend_iv_close", ancestorId = "tianmu_suspend_rl_container",
                matchWindowMs = 0, cooldownMs = 6000,
            ),
            // The home feed's inline native ad uses a different container from
            // the full-screen interstitial. Keep the close control bound to the
            // ad card so the nearby banner and landing link are never clicked.
            AdSkipRule(
                id = "sportworld-inline-close", name = "首页信息流广告关闭",
                packageName = "com.zjwh.android_wh_physicalfitness", appLabel = "运动世界校园",
                activityNames = listOf("com.zjwh.android_wh_physicalfitness.mvi.home.HomeActivity"),
                targetId = "ad_close", ancestorId = "inter_item_fill",
                matchWindowMs = 0, cooldownMs = 6000,
            ),
            AdSkipRule(
                id = AdSkipDrawnSplash.NATIVE_ID, name = "首页信息流广告关闭",
                packageName = "com.zjwh.android_wh_physicalfitness", appLabel = "运动世界校园",
                activityNames = listOf("com.zjwh.android_wh_physicalfitness.mvi.home.HomeActivity"),
                matchWindowMs = 0, allowGesture = true, allowUnknownActivity = true,
                requiredSelectors = listOf(
                    listOf(AdSkipSelector(description = "virtualNode", ancestorId = "fl_native")),
                    listOf(AdSkipSelector(text = "广告", ancestorId = "fl_native")),
                ),
                selectors = listOf(AdSkipSelector(className = "ImageView", ancestorId = "fl_native")),
            ),
            // gkd-kit/subscription 1.186.0, com.realtech.xiaocan group 1 / rule 0.
            // Preserve the complete sibling chain: the first ImageView is the ad.
            AdSkipRule(
                id = "xiaocan-home-popup", name = "首页弹窗广告",
                packageName = "com.realtech.xiaocan", appLabel = "小蚕惠生活",
                activityNames = listOf("com.realtech.xiaocan.MainActivity"),
                matchWindowMs = 0, allowGesture = true, adapterVersion = 2,
                selectors = listOf(AdSkipSelector(
                    className = "ImageView", clickable = true, childCount = 0,
                    previousSiblingSelector = AdSkipSelector(
                        className = "ImageView",
                        previousSiblingSelector = AdSkipSelector(
                            className = "View",
                            parentSelector = AdSkipSelector(className = "View", childCount = 3),
                        ),
                    ),
                )),
            ),
        ),
    )

    fun encode(pack: AdSkipRulePack): String = json.encodeToString(pack)

    fun decode(text: String, allowExternalApps: Boolean = false): AdSkipRulePack {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "规则包超过 4 MB" }
        val pack = json.decodeFromString<AdSkipRulePack>(text)
        validate(pack, allowExternalApps)
        return pack
    }

    fun validate(pack: AdSkipRulePack, allowExternalApps: Boolean = false) {
        require(pack.schemaVersion in 1..2 && pack.version > 0) { "不支持的规则格式或版本" }
        require(pack.name.safeLabel(80) && pack.source.safeLabel(300)) { "名称或来源无效" }
        require(pack.rules.size in 1..4000 && pack.rules.map { it.id }.distinct().size == pack.rules.size) { "规则数量无效或 ID 重复" }
        pack.rules.forEach { rule ->
            val drawnNative = rule.id == AdSkipDrawnSplash.NATIVE_ID
            if (drawnNative) require(rule == bundled.rules.single { it.id == AdSkipDrawnSplash.NATIVE_ID }) {
                "不能修改内置广告结构规则"
            }
            require(rule.id != AdSkipGenericRules.ID) { "不能覆盖内置通用规则" }
            require(rule.id.matches(Regex("[a-z0-9-]{1,64}")) && rule.name.safeLabel(60)) { "规则标识或名称无效" }
            require(supportedPackage(rule.packageName) && (allowExternalApps || rule.packageName in appNames)) { "无效或不支持的应用包名" }
            require(rule.appLabel == null || rule.appLabel.safeLabel(80)) { "应用名称无效" }
            require(rule.activityNames.size <= 8 && rule.excludedActivityNames.size <= 8 && rule.successActivityNames.size <= 8) { "页面限定过多" }
            (rule.activityNames + rule.excludedActivityNames + rule.successActivityNames).forEach { activity ->
                require(activity.length in 3..180 && activity.matches(Regex("[A-Za-z0-9_.$]+")) && activity.contains('.')) { "页面名称必须完整且不能使用通配符" }
            }
            listOfNotNull(rule.targetId, rule.ancestorId, rule.clickParentId).forEach { id ->
                require(id.matches(Regex("[A-Za-z_][A-Za-z0-9_]{0,99}"))) { "只支持应用自身的原生控件 ID" }
            }
            require(rule.textPrefix == null || rule.textPrefix == "跳过") { "旧格式只支持明确的跳过文案" }
            require(rule.targetId != null || rule.selectors.isNotEmpty()) { "缺少目标条件" }
            require(drawnNative || !rule.allowUnknownActivity || (rule.activityNames.isNotEmpty() && rule.targetId == null &&
                rule.requiredSelectors.isNotEmpty() && rule.selectors.all {
                    it.index != null && it.childCount != null && it.clickable == true &&
                        it.parentSelector != null && it.previousSiblingSelector != null
                })) { "页面未知时必须限定完整的广告节点结构" }
            require(rule.action in setOf("click", "clickNode", "clickCenter", "splashEdge", "back", "none")) { "不支持的动作" }
            require(rule.actionMaximum in 0..20 && rule.preKeys.isEmpty()) { "不支持前置动作，不能忽略后执行" }
            if (rule.targetId != null) require(rule.textPrefix != null || rule.ancestorId != null) { "关闭按钮必须限定广告文案或广告容器" }
            require(rule.selectors.size <= 16 && rule.requiredSelectors.size <= 8 && rule.requiredSelectors.all { it.size in 1..16 }) { "候选条件过多" }
            fun validateSelector(selector: AdSkipSelector, depth: Int = 0) {
                require(depth <= 12) { "选择器关系过深" }
                require(selector.index == null || selector.index in 0..2000) { "节点索引无效" }
                require(selector.childCount == null || selector.childCount in 0..2000) { "子节点数无效" }
                require(selector.maxTextLength == null || selector.maxTextLength in 0..160) { "文本长度无效" }
                require(selector.labelMatches.size <= 8 && selector.labelMatches.all {
                    it.value.safeLabel(100) && it.operation in setOf("exact", "prefix", "suffix", "contains")
                }) { "文本组合条件无效" }
                selector.parentSelector?.let { validateSelector(it, depth + 1) }
                selector.previousSiblingSelector?.let { validateSelector(it, depth + 1) }
                listOfNotNull(selector.id, selector.ancestorId).forEach { id ->
                    require(id.matches(Regex("[A-Za-z_][A-Za-z0-9_]{0,99}"))) { "控件 ID 无效" }
                }
                listOfNotNull(selector.text, selector.textPrefix, selector.textContains, selector.description, selector.className).forEach {
                    require(it.safeLabel(160)) { "匹配文本无效" }
                }
            }
            (rule.selectors + rule.requiredSelectors.flatten()).forEach { validateSelector(it) }
            rule.selectors.forEach { selector ->
                val explicitAd = listOfNotNull(selector.text, selector.description).any { isSkipLabel(it) || it in setOf("关闭广告", "Close ad") } ||
                listOfNotNull(selector.textPrefix, selector.textContains).any { it.contains("跳过", true) || it.contains("skip", true) || it in setOf("关闭广告", "Skip ad") }
                val structuralClose = rule.activityNames.isNotEmpty() && selector.className != null && selector.clickable == true &&
                    (selector.parentSelector != null || selector.previousSiblingSelector != null)
                val explicitImportedLabel = selector.labelMatches.any {
                    AdSkipRules.isSkipLabel(it.value) || it.value in setOf("关闭广告", "close ad")
                }
                val importedAdContext = rule.requiredSelectors.flatten().any { required ->
                    (listOfNotNull(required.id, required.text, required.textPrefix, required.textContains, required.description) +
                        required.labelMatches.map { it.value }).any { value ->
                        listOf("广告", "推广", "开屏", "弹窗", "banner", "splash", "interstitial", "native", "ksad", "gdt", "ad_", "_ad", "advert", "popup").any { marker -> value.contains(marker, true) }
                    }
                }
                require(drawnNative || selector.skipLabel || explicitAd || explicitImportedLabel || structuralClose ||
                    (rule.adapterVersion == 3 && importedAdContext && (
                        selector.id?.contains(Regex("(?i)(close|dismiss|skip|cancel|ignore)")) == true ||
                        selector.labelMatches.any { it.value.contains(Regex("(?i)(close|dismiss|skip|cancel|ignore|关闭|跳过|取消|不感兴趣)")) }
                    )) ||
                    (selector.id != null && (selector.ancestorId != null || rule.activityNames.isNotEmpty())) || rule.action == "back") { "匹配须限定跳过文案或具体广告控件上下文" }
            }
            require(rule.cooldownMs in 3000..60000 && (rule.matchWindowMs == 0L || rule.matchWindowMs in 1000..30000)) { "触发时间范围无效" }
        }
    }

    fun supportedPackage(value: String): Boolean = value.length <= 180 &&
        value.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")) &&
        value != "cn.qztc.timetable" && !value.startsWith("cn.qztc.timetable.") &&
        value !in setOf("com.android.settings", "com.android.systemui", "com.android.permissioncontroller", "com.google.android.permissioncontroller", "com.android.packageinstaller", "com.google.android.packageinstaller")

    private fun String.safeLabel(max: Int) = isNotBlank() && length <= max && none { it.isISOControl() || it == '|' }

    private val skipLabelPattern = Regex("""(?i)^(?:(?:[0-9]{1,2}\s*(?:s|秒)?\s*[|｜·]?\s*)?(?:跳过(?:广告)?|skip(?:\s+ad)?)(?:\s*[|｜·(（]?\s*[0-9]{1,2}\s*(?:s|秒)?[)）]?)?|关闭广告|close\s+ad)$""")
    fun isSkipLabel(value: String?) = value != null && value.length <= 40 && skipLabelPattern.matches(value.trim())
}

/** Ephemeral metadata; never persisted or logged. Indices refer to the same bounded snapshot. */
data class AdSkipNode(
    val id: String?, val text: String?, val visible: Boolean, val enabled: Boolean,
    val clickable: Boolean, val parent: Int? = null,
    val description: String? = null, val className: String? = null,
    val bounds: AdSkipBounds? = null, val packageName: String? = null,
    val index: Int? = null, val childCount: Int? = null,
)

/** A generic rule uses one ID across apps, but must not share their cooldown. */
internal fun AdSkipRule.runtimeKey(): String = packageName + "/" + id

data class AdSkipBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
    fun containedBy(other: AdSkipBounds) = width > 0 && height > 0 && left >= other.left && top >= other.top && right <= other.right && bottom <= other.bottom
}

object AdSkipMatcher {
    /** Preserve tree indices/ancestry while skipping stale off-display matches. */
    fun findTarget(rule: AdSkipRule, nodes: List<AdSkipNode>, display: AdSkipBounds,
        accepts: (Int) -> Boolean = { true }): Int? {
        var eligible = nodes
        repeat(nodes.size) {
            val target = findTarget(rule, eligible) ?: return null
            if (AdSkipSafety.onDisplay(nodes[target].bounds, display) && accepts(target)) return target
            eligible = eligible.mapIndexed { index, node -> if (index == target) node.copy(visible = false) else node }
        }
        return null
    }

    fun matchesActivity(rule: AdSkipRule, activity: String) = activity !in rule.excludedActivityNames &&
        (rule.activityNames.isEmpty() || activity in rule.activityNames ||
            (activity.isEmpty() && rule.allowUnknownActivity && rule.requiredSelectors.isNotEmpty()))

    fun findTarget(rule: AdSkipRule, nodes: List<AdSkipNode>): Int? {
        if (rule.id == AdSkipGenericRules.ID) return AdSkipGenericRules.findTarget(rule, nodes)
        if (rule.id == AdSkipDrawnSplash.NATIVE_ID) return AdSkipDrawnSplash.findNativeTarget(rule, nodes)
        if (rule.id == "mybus-native-feed-close") {
            AdSkipDrawnSplash.findMyBusFeedbackTarget(nodes)?.let { return it }
            AdSkipDrawnSplash.findMyBusNativeTarget(rule, nodes)?.let { return it }
        }
        if (rule.requiredSelectors.any { alternatives -> nodes.indices.none { i -> alternatives.any { matchesSelector(rule, it, nodes, i) } } }) return null
        fun full(id: String) = "${rule.packageName}:id/$id"
        return nodes.indices.firstOrNull { index ->
            val node = nodes[index]
            if (!node.visible || !node.enabled || (node.packageName != null && node.packageName != rule.packageName)) return@firstOrNull false
            fun hasAncestor(id: String?): Boolean {
                if (id == null) return true
                var parent = node.parent
                var depth = 0
                while (parent != null && depth++ < 24) {
                    val ancestor = nodes.getOrNull(parent) ?: break
                    if (ancestor.id == full(id) && ancestor.visible) return true
                    parent = ancestor.parent
                }
                return false
            }
            val legacy = rule.targetId != null && node.id == full(rule.targetId) &&
                (rule.textPrefix == null || node.text?.startsWith(rule.textPrefix) == true) && hasAncestor(rule.ancestorId)
            legacy || rule.selectors.any { matchesSelector(rule, it, nodes, index) }
        }
    }

    private fun matchesSelector(rule: AdSkipRule, s: AdSkipSelector, nodes: List<AdSkipNode>, index: Int, depth: Int = 0): Boolean {
        if (depth > 12) return false
        val node = nodes.getOrNull(index) ?: return false
        if (!node.visible || !node.enabled || (node.packageName != null && node.packageName != rule.packageName)) return false
        fun full(id: String) = "${rule.packageName}:id/$id"
        fun hasAncestor(id: String?): Boolean {
            if (id == null) return true
            var parent = node.parent
            repeat(24) {
                val ancestor = parent?.let(nodes::getOrNull) ?: return false
                if (ancestor.id == full(id) && ancestor.visible) return true
                parent = ancestor.parent
            }
            return false
        }
        return (s.id == null || node.id == full(s.id)) &&
                (s.text == null || node.text == s.text) &&
                (s.textPrefix == null || node.text?.startsWith(s.textPrefix) == true) &&
                (s.textContains == null || node.text?.contains(s.textContains) == true) &&
                (s.description == null || node.description == s.description) &&
                (s.className == null || node.className == s.className || (!s.className.contains('.') && node.className?.substringAfterLast('.') == s.className)) &&
                (s.clickable == null || node.clickable == s.clickable) && hasAncestor(s.ancestorId) &&
                (s.index == null || node.index == s.index) &&
                (s.childCount == null || node.childCount == s.childCount) &&
                (s.maxTextLength == null || (node.text?.length ?: 0) <= s.maxTextLength) &&
                s.labelMatches.all { condition -> listOfNotNull(node.text, node.description, node.id).any { value ->
                    when (condition.operation) {
                        "exact" -> value == condition.value
                        "prefix" -> value.startsWith(condition.value)
                        "suffix" -> value.endsWith(condition.value)
                        "contains" -> value.contains(condition.value)
                        else -> false
                    }
                } } &&
                (s.parentSelector == null || node.parent?.let { matchesSelector(rule, s.parentSelector, nodes, it, depth + 1) } == true) &&
                (s.previousSiblingSelector == null || (node.parent != null && node.index != null && nodes.indices.any {
                    nodes[it].parent == node.parent && nodes[it].index == node.index - 1 && matchesSelector(rule, s.previousSiblingSelector, nodes, it, depth + 1)
                })) &&
                (s.textRegex == null || runCatching { Regex(s.textRegex).containsMatchIn(node.text.orEmpty()) }.getOrDefault(false)) &&
                (!s.skipLabel || AdSkipRules.isSkipLabel(node.text) || AdSkipRules.isSkipLabel(node.description))
    }

    fun clickCandidates(rule: AdSkipRule, nodes: List<AdSkipNode>, target: Int): List<Int> {
        // A clickable ancestor may be the ad's landing link. Never climb an
        // arbitrary parent chain; non-clickable close controls use their own center.
        return listOfNotNull(clickTarget(rule, nodes, target))
    }

    fun clickTarget(rule: AdSkipRule, nodes: List<AdSkipNode>, target: Int): Int? {
        val index = if (rule.clickParentId != null) nodes.getOrNull(target)?.parent ?: return null
            else if (rule.id == AdSkipGenericRules.ID && nodes.getOrNull(target)?.clickable != true &&
                nodes.getOrNull(target)?.parent?.let(nodes::getOrNull)?.clickable == true) nodes[target].parent!!
            else target
        val node = nodes.getOrNull(index) ?: return null
        if (rule.clickParentId != null && node.id != "${rule.packageName}:id/${rule.clickParentId}") return null
        if (index != target) {
            val targetBounds = nodes[target].bounds ?: return null
            val parentBounds = node.bounds ?: return null
            if (!targetBounds.containedBy(parentBounds) || parentBounds.width > targetBounds.width * 2 ||
                parentBounds.height > targetBounds.height * 2) return null
        }
        return index.takeIf { node.visible && node.enabled && node.clickable }
    }
}

enum class AdSkipResult(val label: String) {
    NO_MATCH("未找到匹配节点"), TARGET_NOT_CLICKABLE("命中节点，但目标不可点击"),
    CLICK_REJECTED("命中节点，点击请求被拒绝"), CLICK_ACCEPTED("点击请求已接受，等待复查"),
    TARGET_GONE("复查：点击后目标消失，尚不能确认广告已跳过"), NORMAL_PAGE("复查：页面已切换，尚不能确认由点击造成"),
    TARGET_PRESENT("复查：广告目标仍存在"), UNCONFIRMED("页面切换或服务暂停，结果未确认"),
    GESTURE_SENT("已提交目标位置手势，等待回调"), GESTURE_COMPLETED("手势已完成，等待复查"),
    GESTURE_CANCELLED("手势取消或提交失败"), NO_WINDOW("暂时无法读取当前窗口，继续重试"),
    INCOMPLETE_TREE("节点树读取不完整，未把未找到当作消失"),
}

object AdSkipVerification {
    fun confirmedHome(rule: AdSkipRule, pkg: String?, activity: String, originalWindow: Int, currentWindow: Int,
        eventWindow: Int, eventTime: Long, actionTime: Long, now: Long): Boolean =
        pkg == rule.packageName && activity in rule.successActivityNames && originalWindow >= 0 && currentWindow >= 0 && currentWindow != originalWindow &&
            currentWindow == eventWindow && eventTime >= actionTime && AdSkipTiming.fresh(eventTime, now)

    fun result(rule: AdSkipRule, foregroundPackage: String?, activity: String?, targetPresent: Boolean?): AdSkipResult = when {
        foregroundPackage != rule.packageName || targetPresent == null -> AdSkipResult.UNCONFIRMED
        targetPresent -> AdSkipResult.TARGET_PRESENT
        activity in rule.successActivityNames -> AdSkipResult.NORMAL_PAGE
        else -> AdSkipResult.TARGET_GONE
    }
}

object AdSkipTiming {
    fun rapidSplash(packageName: String, activity: String, enteredAt: Long, now: Long): Boolean =
        AdSkipRules.supportedPackage(packageName) &&
            (packageName != "com.yiban.app" || activity.isEmpty() || activity == "com.yiban.app.activity.WelcomeActivity") &&
            now >= enteredAt && now - enteredAt < 8000

    fun fresh(eventTime: Long, now: Long): Boolean = eventTime > 0 && now >= eventTime && now - eventTime <= 2000
    fun verificationTimely(actionTime: Long, now: Long, windowMs: Long = 3000): Boolean =
        now >= actionTime && now - actionTime <= windowMs
}

/** Unknown/incomplete snapshots and window transitions break consecutive absence. */
internal class AdSkipAbsenceEvidence {
    private var window = -1
    private var count = 0
    fun observe(windowId: Int, complete: Boolean, targetPresent: Boolean): Boolean {
        if (!complete || targetPresent || windowId < 0) { reset(); return false }
        count = if (window == windowId) count + 1 else 1
        window = windowId
        return count >= 2
    }
    fun reset() { window = -1; count = 0 }
}
