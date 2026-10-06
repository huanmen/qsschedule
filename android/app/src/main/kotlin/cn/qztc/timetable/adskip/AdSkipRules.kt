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
    val activityNames: List<String>,
    val targetId: String,
    val textPrefix: String? = null,
    val ancestorId: String? = null,
    val clickParentId: String? = null,
    val successActivityNames: List<String> = emptyList(),
    val cooldownMs: Long = 6000,
    val matchWindowMs: Long = 10000,
    val appLabel: String? = null,
)

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
        "com.cainiao.wireless" to "菜鸟",
        "com.zhishan.washer" to "悠洗",
    )
    private val json = Json { prettyPrint = true }
    val bundled = AdSkipRulePack(
        version = 20260926,
        name = "校园广告辅助 · 基础规则",
        source = "2026-09-25 本机控件与 GKD 外部试用记录；本引擎效果需另行验收",
        rules = listOf(
            AdSkipRule(
                "yiban-splash", "开屏广告", "com.yiban.app",
                listOf("com.yiban.app.activity.WelcomeActivity"), "btn_splash_ad",
                textPrefix = "跳过",
                successActivityNames = listOf("com.yiban.app.home.activity.HomeActivityV2"),
            ),
            AdSkipRule(
                "chaoxing-splash", "开屏广告", "com.chaoxing.mobile",
                listOf("com.chaoxing.mobile.activity.SplashActivity"), "btn_jump",
                textPrefix = "跳过", clickParentId = "jump_view",
                successActivityNames = listOf("com.chaoxing.mobile.main.ui.MainTabActivity"),
            ),
            AdSkipRule(
                "mybus-route-banner", "线路文字横幅", "com.mygolbs.mybus",
                listOf("com.mygolbs.mybus.RTimeActivity"), "ll_textad_close",
                ancestorId = "textad_view", matchWindowMs = 0,
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
        require(pack.schemaVersion == 1 && pack.version > 0) { "不支持的规则格式或版本" }
        require(pack.name.safeLabel(80) && pack.source.safeLabel(300)) { "名称或来源无效" }
        require(pack.rules.size in 1..4000 && pack.rules.map { it.id }.distinct().size == pack.rules.size) { "规则数量无效或 ID 重复" }
        pack.rules.forEach { rule ->
            require(rule.id.matches(Regex("[a-z0-9-]{1,64}")) && rule.name.safeLabel(60)) { "规则标识或名称无效" }
            require(supportedPackage(rule.packageName) && (allowExternalApps || rule.packageName in appNames)) { "无效或不支持的应用包名" }
            require(rule.appLabel == null || rule.appLabel.safeLabel(80)) { "应用名称无效" }
            require(rule.activityNames.size in 1..8 && rule.successActivityNames.size <= 8) { "必须限定具体页面" }
            (rule.activityNames + rule.successActivityNames).forEach { activity ->
                require(activity.length in 3..180 && activity.matches(Regex("[A-Za-z0-9_.$]+")) && activity.contains('.')) { "页面名称必须完整且不能使用通配符" }
            }
            listOfNotNull(rule.targetId, rule.ancestorId, rule.clickParentId).forEach { id ->
                require(id.matches(Regex("[A-Za-z_][A-Za-z0-9_]{0,99}"))) { "只支持应用自身的原生控件 ID" }
            }
            require(rule.textPrefix == null || rule.textPrefix == "跳过") { "只支持明确的跳过文案" }
            require(rule.textPrefix != null || rule.ancestorId != null) { "关闭按钮必须限定广告文案或广告容器" }
            require(rule.cooldownMs in 3000..60000 && (rule.matchWindowMs == 0L || rule.matchWindowMs in 1000..30000)) { "触发时间范围无效" }
        }
    }

    fun supportedPackage(value: String): Boolean = value.length <= 180 &&
        value.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")) &&
        value != "cn.qztc.timetable" && !value.startsWith("cn.qztc.timetable.") &&
        value !in setOf("com.android.settings", "com.android.systemui", "com.android.permissioncontroller", "com.google.android.permissioncontroller", "com.android.packageinstaller", "com.google.android.packageinstaller")

    private fun String.safeLabel(max: Int) = isNotBlank() && length <= max && none { it.isISOControl() || it == '|' }
}

/** Ephemeral metadata; never persisted or logged. Indices refer to the same bounded snapshot. */
data class AdSkipNode(
    val id: String?, val text: String?, val visible: Boolean, val enabled: Boolean,
    val clickable: Boolean, val parent: Int? = null,
)

object AdSkipMatcher {
    fun matchesActivity(rule: AdSkipRule, activity: String) = activity in rule.activityNames

    fun findTarget(rule: AdSkipRule, nodes: List<AdSkipNode>): Int? {
        fun full(id: String) = "${rule.packageName}:id/$id"
        return nodes.indices.firstOrNull { index ->
            val node = nodes[index]
            if (!node.visible || !node.enabled || node.id != full(rule.targetId)) return@firstOrNull false
            if (rule.textPrefix != null && node.text?.startsWith(rule.textPrefix) != true) return@firstOrNull false
            if (rule.ancestorId != null) {
                var parent = node.parent
                var found = false
                var depth = 0
                while (parent != null && depth++ < 24) {
                    val ancestor = nodes.getOrNull(parent) ?: break
                    if (ancestor.id == full(rule.ancestorId) && ancestor.visible) { found = true; break }
                    parent = ancestor.parent
                }
                if (!found) return@firstOrNull false
            }
            true
        }
    }

    fun clickTarget(rule: AdSkipRule, nodes: List<AdSkipNode>, target: Int): Int? {
        val index = if (rule.clickParentId == null) target else nodes.getOrNull(target)?.parent ?: return null
        val node = nodes.getOrNull(index) ?: return null
        if (rule.clickParentId != null && node.id != "${rule.packageName}:id/${rule.clickParentId}") return null
        return index.takeIf { node.visible && node.enabled && node.clickable }
    }
}

enum class AdSkipResult(val label: String) {
    NO_MATCH("未找到匹配节点"), TARGET_NOT_CLICKABLE("命中节点，但目标不可点击"),
    CLICK_REJECTED("命中节点，点击请求被拒绝"), CLICK_ACCEPTED("点击请求已接受，等待复查"),
    TARGET_GONE("复查：广告目标节点已消失"), NORMAL_PAGE("复查：已进入规则指定正常页"),
    TARGET_PRESENT("复查：广告目标仍存在"), UNCONFIRMED("页面切换或服务暂停，结果未确认"),
}

object AdSkipVerification {
    fun result(rule: AdSkipRule, foregroundPackage: String?, activity: String?, targetPresent: Boolean?): AdSkipResult = when {
        foregroundPackage != rule.packageName || targetPresent == null -> AdSkipResult.UNCONFIRMED
        targetPresent -> AdSkipResult.TARGET_PRESENT
        activity in rule.successActivityNames -> AdSkipResult.NORMAL_PAGE
        else -> AdSkipResult.TARGET_GONE
    }
}

object AdSkipTiming {
    fun fresh(eventTime: Long, now: Long): Boolean = eventTime > 0 && now >= eventTime && now - eventTime <= 2000
    fun verificationTimely(actionTime: Long, now: Long): Boolean = now >= actionTime && now - actionTime <= 3000
}
