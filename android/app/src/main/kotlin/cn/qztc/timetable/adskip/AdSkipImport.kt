package cn.qztc.timetable.adskip

import de.marhali.json5.Json5
import de.marhali.json5.config.Json5Options
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class AdSkipImportIssue(val app: String, val group: String, val reason: String)
data class AdSkipImportPreview(val pack: AdSkipRulePack?, val issues: List<AdSkipImportIssue>, val appCount: Int)

/** Strict subset: never discard unknown matching constraints. */
object AdSkipImport {
    private val json5 = Json5(Json5Options.builder().build())
    private val supported = setOf("key", "name", "desc", "snapshotUrls", "excludeSnapshotUrls", "exampleUrls", "enable", "fastQuery", "activityIds", "matches", "action", "actionCd", "matchTime", "rules")

    fun parse(text: String): AdSkipImportPreview {
        require(text.toByteArray(Charsets.UTF_8).size <= AdSkipRules.MAX_BYTES) { "文件超过 4 MB" }
        val root = try {
            Json.parseToJsonElement(json5.serialize(json5.parse(text))).jsonObject
        } catch (_: StackOverflowError) { throw IllegalArgumentException("规则嵌套过深") }
        if ("schemaVersion" in root || ("rules" in root && "apps" !in root)) {
            val pack = AdSkipRules.decode(root.toString(), allowExternalApps = true)
            return AdSkipImportPreview(pack, emptyList(), pack.rules.map { it.packageName }.distinct().size)
        }
        val apps = root["apps"] as? JsonArray ?: error("缺少应用列表")
        require(apps.size <= 2500) { "应用数量过多" }
        val issues = mutableListOf<AdSkipImportIssue>()
        val rules = mutableListOf<AdSkipRule>()
        val name = label(root.string("name") ?: "第三方规则", 80)
        val version = root["version"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 } ?: error("缺少规则版本")
        val unknownRoot = root.keys - setOf("id", "name", "version", "author", "checkUpdateUrl", "updateUrl", "supportUri", "categories", "globalGroups", "apps")
        require(unknownRoot.isEmpty()) { "不支持的订阅字段" }
        if ((root["globalGroups"] as? JsonArray)?.isNotEmpty() == true)
            issues += AdSkipImportIssue("全局", "全局规则", "不支持全局匹配，仅执行按 App 限定的规则")
        for ((appIndex, appElement) in apps.withIndex()) {
            val app = appElement as? JsonObject ?: error("应用结构无效")
            val pkg = app.string("id") ?: error("缺少应用包名")
            val appName = label(app.string("name") ?: pkg, 80)
            val groups = app["groups"] as? JsonArray ?: error("缺少规则组")
            require(groups.size <= 300) { "单应用规则过多" }
            for ((groupIndex, groupElement) in groups.withIndex()) {
                val group = groupElement as? JsonObject ?: error("规则组结构无效")
                val groupName = label(group.string("name") ?: "未命名规则组", 60)
                val entries = when (val entry = group["rules"]) {
                    is JsonArray -> entry
                    null -> JsonArray(emptyList())
                    else -> JsonArray(listOf(entry))
                }
                require(entries.size <= 300 && issues.size + rules.size <= 15000) { "规则数量过多" }
                for ((ruleIndex, element) in entries.withIndex()) {
                    val result = runCatching {
                        require(AdSkipRules.supportedPackage(pkg)) { "受保护或无效的应用包名" }
                        require((app.keys - setOf("id", "name", "groups")).isEmpty()) { "应用级条件尚不兼容" }
                        require(groupName.contains("广告") || groupName.contains("跳过")) { "非广告规则不导入（权限、签到、购买等）" }
                        val item = if (element is JsonObject) element else buildJsonObject { put("matches", element) }
                        val unsupported = (group.keys + item.keys) - supported
                        require(unsupported.isEmpty()) { "尚不兼容的条件：" + unsupported.sorted().joinToString() }
                        val effective = JsonObject(group + item)
                        require(effective.string("action") in listOf(null, "clickNode")) { "仅支持原生节点点击" }
                        val activities = strings(effective["activityIds"]).map { if (it.startsWith('.')) pkg + it else it }
                        require(activities.isNotEmpty()) { "未限定具体页面" }
                        val selectors = strings(effective["matches"])
                        require(selectors.size == 1) { "多条件组合尚不兼容" }
                        val target = nativeSelector(selectors.single(), pkg)
                        val rule = AdSkipRule(
                            "gkd-$appIndex-$groupIndex-$ruleIndex", groupName, pkg, activities,
                            target.first, textPrefix = target.second,
                            cooldownMs = effective.long("actionCd", 6000).coerceAtLeast(3000),
                            matchWindowMs = effective.long("matchTime", 0), appLabel = appName,
                        )
                        AdSkipRules.validate(AdSkipRulePack(version = version, name = name, source = "用户导入", rules = listOf(rule)), allowExternalApps = true)
                        rule
                    }
                    result.onSuccess { rules += it }.onFailure { issues += AdSkipImportIssue(appName, "$groupName / $ruleIndex", label(it.message ?: "格式不兼容", 220)) }
                }
            }
        }
        require(rules.size <= 4000) { "可执行规则超过上限" }
        val pack = if (rules.isEmpty()) null else AdSkipRulePack(
            version = version, name = name, source = "用户导入 GKD 数据；仅原生 ID 子集，非完整兼容，未真机验证", rules = rules,
        )
        return AdSkipImportPreview(pack, issues, apps.size)
    }

    private fun nativeSelector(selector: String, pkg: String): Pair<String, String?> {
        var rest = selector.trim().removePrefix("@")
        val attributes = mutableListOf<String>()
        while (rest.startsWith('[')) {
            val end = rest.indexOf(']')
            require(end > 0) { "选择器格式无效" }
            attributes += rest.substring(1, end).trim()
            rest = rest.substring(end + 1).trim()
        }
        require(rest.isEmpty() && attributes.isNotEmpty()) { "复杂选择器尚不兼容" }
        var id: String? = null
        var prefix: String? = null
        for (attribute in attributes) {
            val idMatch = Regex("""(vid|id)\s*=\s*(["'])([A-Za-z0-9_./:]+)\2""").matchEntire(attribute)
            if (idMatch != null) {
                require(id == null) { "重复 ID 条件" }
                val value = idMatch.groupValues[3]
                id = if (idMatch.groupValues[1] == "vid") value else {
                    require(value.startsWith("$pkg:id/")) { "不是应用自身资源 ID" }
                    value.removePrefix("$pkg:id/")
                }
            } else when (attribute.replace(" ", "")) {
                "clickable=true", "visibleToUser=true", "enabled=true" -> Unit
                "text^=\"跳过\"", "text^='跳过'" -> prefix = "跳过"
                else -> error("选择器条件尚不兼容")
            }
        }
        return (id ?: error("必须有明确原生资源 ID")) to prefix
    }

    private fun strings(value: JsonElement?): List<String> = when (value) {
        null -> emptyList()
        is JsonArray -> value.map { require(it is JsonPrimitive && it.isString); it.content }
        is JsonPrimitive -> { require(value.isString); listOf(value.content) }
        else -> error("预期字符串或字符串数组")
    }
    private fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.long(key: String, default: Long): Long = get(key)?.let { it.jsonPrimitive.longOrNull ?: error("数值格式无效") } ?: default
    private fun label(value: String, max: Int) = value.filterNot { it.isISOControl() || it == '|' }.take(max).ifBlank { "未命名" }
}
