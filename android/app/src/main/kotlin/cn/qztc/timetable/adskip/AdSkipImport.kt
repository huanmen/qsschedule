package cn.qztc.timetable.adskip

import de.marhali.json5.Json5
import de.marhali.json5.config.Json5Options
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.security.MessageDigest

@Serializable
data class AdSkipImportIssue(val app: String, val group: String, val reason: String)
data class AdSkipImportPreview(val pack: AdSkipRulePack?, val issues: List<AdSkipImportIssue>, val appCount: Int)

/** Adapter for the public GKD subscription format and LiTiaoTiao JSON exports. */
object AdSkipImport {
    private val json5 = Json5(Json5Options.builder().build())
    private val rootFields = setOf("id", "name", "version", "author", "checkUpdateUrl", "updateUrl", "supportUri", "categories", "globalGroups", "apps")

    fun parse(text: String, installedPackages: Map<String, String> = emptyMap()): AdSkipImportPreview {
        require(text.toByteArray(Charsets.UTF_8).size <= AdSkipRules.MAX_BYTES) { "文件超过 4 MB" }
        val element = runCatching { Json.parseToJsonElement(json5.serialize(json5.parse(text))) }.getOrElse {
            val arrays = splitTopLevelArrays(text)
            require(arrays.size > 1) { "规则 JSON5 无法解析" }
            JsonArray(arrays.flatMap { Json.parseToJsonElement(json5.serialize(json5.parse(it))).jsonArray })
        }
        return when (element) {
            is JsonArray -> parseLtt(element, installedPackages)
            is JsonObject -> if ("schemaVersion" in element || ("rules" in element && "apps" !in element)) {
                val pack = AdSkipRules.decode(element.toString(), allowExternalApps = true)
                AdSkipImportPreview(pack, emptyList(), pack.rules.map { it.packageName }.distinct().size)
            } else parseGkd(element)
            else -> error("规则根结构无效")
        }
    }

    private fun parseGkd(root: JsonObject): AdSkipImportPreview {
        val apps = root["apps"] as? JsonArray ?: error("缺少应用列表")
        require((root.keys - rootFields).isEmpty()) { "不支持的订阅字段" }
        val name = label(root.str("name") ?: "GKD 规则", 80)
        val version = root["version"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 } ?: error("缺少规则版本")
        val issues = mutableListOf<AdSkipImportIssue>(); val rules = mutableListOf<AdSkipRule>()
        for ((ai, appValue) in apps.withIndex()) {
            val app = appValue.jsonObject; val pkg = app.str("id") ?: error("缺少应用包名")
            val appName = label(app.str("name") ?: pkg, 80)
            for ((gi, groupValue) in (app["groups"] as? JsonArray ?: error("缺少规则组")).withIndex()) {
                val group = groupValue.jsonObject; val groupName = label(group.str("name") ?: "规则组", 60)
                for ((ri, value) in group["rules"].entries().withIndex()) {
                    val item = value as? JsonObject ?: buildJsonObject { put("matches", value) }
                    runCatching {
                        require(AdSkipRules.supportedPackage(pkg)) { "受保护或无效的应用包名" }
                        require(isAdGroup(groupName)) { "非广告规则不导入（权限、签到、购买等）" }
                        val allowed = setOf("key", "name", "desc", "enable", "fastQuery", "activityIds", "excludeActivityIds", "matches", "anyMatches", "action", "actionCd", "matchTime", "actionMaximum", "resetMatch", "snapshotUrls", "exampleUrls")
                        require(((group.keys + item.keys) - allowed - setOf("rules")).isEmpty()) { "尚不兼容的条件：" + ((group.keys + item.keys) - allowed - setOf("rules")).sorted().joinToString() }
                        val effective = JsonObject(group + item); val action = effective.str("action") ?: "click"
                        require(effective.str("resetMatch") in listOf(null, "app")) { "暂不支持此次数重置方式" }
                        require(action in setOf("click", "clickNode", "clickCenter", "back", "none")) { "不支持的动作：$action" }
                        val all = effective["matches"].strings().map { parseSelector(it, pkg) }
                        val any = effective["anyMatches"].strings().flatMap { parseSelector(it, pkg) }
                        require(all.size <= 8 && any.size <= 16) { "单规则选择器过多" }
                        val parsed = all.lastOrNull() ?: any
                        require(parsed.isNotEmpty()) { "关系/逻辑选择器暂不能安全执行" }
                        val activityIds = effective["activityIds"].strings().map { if (it.startsWith('.')) pkg + it else it }
                        val excludedActivityIds = effective["excludeActivityIds"].strings().map { if (it.startsWith('.')) pkg + it else it }
                        val rule = AdSkipRule(stableId("gkd|$name|$pkg|$ai|$gi|$ri"), label(item.str("name") ?: groupName, 60), pkg,
                            activityNames = activityIds, excludedActivityNames = excludedActivityIds, selectors = parsed,
                            requiredSelectors = all.dropLast(1) + if (all.isNotEmpty() && any.isNotEmpty()) listOf(any) else emptyList(),
                            adapterVersion = 2,
                            cooldownMs = effective["actionCd"]?.jsonPrimitive?.longOrNull ?: 6000L,
                            matchWindowMs = effective["matchTime"]?.jsonPrimitive?.longOrNull ?: 0L, appLabel = appName,
                            action = action, actionMaximum = effective["actionMaximum"]?.jsonPrimitive?.intOrNull ?: 0,
                            allowGesture = action == "click" || action == "clickCenter")
                        AdSkipRules.validate(AdSkipRulePack(2, version, name, "用户导入 GKD", listOf(rule)), true); rules += rule
                    }.onFailure { issues += AdSkipImportIssue(appName, "$groupName / $ri", label(it.message ?: "格式不兼容", 220)) }
                }
            }
        }
        return preview(name, version, apps.size, rules, issues, "用户导入 GKD")
    }

    private fun parseLtt(root: JsonArray, installedPackages: Map<String, String>): AdSkipImportPreview {
        require(installedPackages.isNotEmpty()) { "李跳跳规则需根据已安装应用包名解析" }
        val hashes = installedPackages.keys.associateBy(::javaHash); val issues = mutableListOf<AdSkipImportIssue>(); val rules = mutableListOf<AdSkipRule>()
        root.forEachIndexed { index, value ->
            if (value !is JsonObject) return@forEachIndexed
            val key = value.keys.singleOrNull() ?: return@forEachIndexed; val pkg = hashes[key] ?: return@forEachIndexed; val appName = installedPackages[pkg] ?: pkg
            val config = runCatching { Json.parseToJsonElement(json5.serialize(json5.parse(value.getValue(key).jsonPrimitive.content))).jsonObject }.getOrElse { issues += AdSkipImportIssue(appName, "配置", "内层 JSON 无法解析"); return@forEachIndexed }
            config["popup_rules"].asArrayOrNull()?.forEachIndexed { ri, item -> runCatching {
                require((config.keys - setOf("popup_rules")).isEmpty()) { "应用配置含尚不支持的动作或限制条件" }
                val obj = item.jsonObject; val action = obj.str("action") ?: error("缺少动作")
                require(action != "GLOBAL_ACTION_BACK" && !action.contains(',') && isAdText(action + obj.str("id"))) { "不是安全的广告跳过规则" }
                val selector = lttSelector(action); val max = obj["times"]?.jsonPrimitive?.intOrNull ?: 0; require(max in 0..20)
                require((obj.keys - setOf("id", "action", "times")).isEmpty()) { "规则含尚不支持的限制条件" }
                val rule = AdSkipRule(stableId("ltt|$key|$ri|$action"), label(obj.str("id") ?: "广告关闭", 60), pkg, selectors = selector, appLabel = appName, actionMaximum = max, allowGesture = true, adapterVersion = 2)
                AdSkipRules.validate(AdSkipRulePack(2, 1, "李跳跳导入", "用户导入", listOf(rule)), true)
                rule
            }.onSuccess { rules += it }.onFailure { issues += AdSkipImportIssue(appName, "规则 $ri", label(it.message ?: "不兼容", 220)) } }
        }
        return preview("李跳跳导入", 1, installedPackages.size, rules, issues, "用户导入李跳跳文本规则")
    }

    private fun lttSelector(action: String): List<AdSkipSelector> = listOf(AdSkipSelector(labelMatches = action.split('&').map { part ->
        require(part.isNotBlank() && part.length <= 100)
        val prefix = part.firstOrNull(); val text = if (prefix in listOf('=', '+', '-')) part.drop(1) else part
        AdSkipLabelMatch(text, when (prefix) { '=' -> "exact"; '+' -> "prefix"; '-' -> "suffix"; else -> "contains" })
    }))

    private fun parseSelector(source: String, pkg: String): List<AdSkipSelector> {
        require(source.length <= 1000 && !source.contains('\\')) { "选择器过长或包含尚不支持的转义" }
        // Tokenize outside quoted attributes. Unparsed syntax is rejected, never discarded.
        var quote = '\u0000'; var bracket = 0; var start = 0
        var previous: List<AdSkipSelector>? = null; var relation: Char? = null
        fun append(part: String) {
            val current = parseSimpleSelector(part.trim(), pkg)
            previous = previous?.let { before -> current.flatMap { right -> before.map { left ->
                when (relation) {
                    '>' -> right.copy(parentSelector = left)
                    '+' -> right.copy(previousSiblingSelector = left)
                    else -> error("不支持的关系")
                }
            } } } ?: current
            require(previous!!.size <= 16) { "选择器分支过多" }
        }
        source.forEachIndexed { i, c ->
            if (quote != '\u0000') { if (c == quote) quote = '\u0000' }
            else when {
                c == '\'' || c == '"' -> quote = c
                c == '[' -> bracket++
                c == ']' -> bracket--
                bracket == 0 && (c == '>' || c == '+') -> { append(source.substring(start, i)); relation = c; start = i + 1 }
            }
            require(bracket in 0..1) { "无效属性括号" }
        }
        require(quote == '\u0000' && bracket == 0) { "选择器不完整" }
        append(source.substring(start))
        return previous!!
    }

    private fun parseSimpleSelector(source: String, pkg: String): List<AdSkipSelector> {
        require(source.count { it == '[' } == source.count { it == ']' })
        val attributePattern = Regex("\\[([^]]*)]")
        val attrs = attributePattern.findAll(source).map { it.groupValues[1] }.toList()
        val className = source.substringBefore('[', source).trim()
        require(className.isEmpty() || className.matches(Regex("[A-Za-z_][A-Za-z0-9_.$]*"))) { "不支持的关系或目标标记" }
        require(attributePattern.replace(source.removePrefix(className), "").isBlank()) { "存在未解析的选择器条件" }
        require(className.isNotEmpty() || attrs.isNotEmpty()) { "空选择器" }
        var results = listOf(AdSkipSelector(className = className.takeIf { it.isNotEmpty() }))
        attrs.forEach { attr ->
            val branches = splitOr(attr).map { branch ->
            Regex("\\s*text\\.length\\s*<=\\s*(\\d+)\\s*").matchEntire(branch)?.let {
                return@map AdSkipSelector(maxTextLength = it.groupValues[1].toInt())
            }
            val m = Regex("""\s*(vid|id|text|desc|name|clickable|visibleToUser|enabled|childCount|index)\s*(\^=|\*=|=)\s*(?:"([^"]*)"|'([^']*)'|([A-Za-z0-9_.-]+))\s*""").matchEntire(branch) ?: error("不支持的属性表达式")
            val key = m.groupValues[1]; val op = m.groupValues[2]; val raw = listOf(3, 4, 5).firstNotNullOf { m.groupValues[it].takeIf(String::isNotEmpty) }
            require(key == "text" || op == "=") { "此属性运算符尚不支持" }
            when (key) {
                "vid" -> AdSkipSelector(id = raw)
                "id" -> { require(raw.startsWith("$pkg:id/")); AdSkipSelector(id = raw.substringAfter(":id/")) }
                "text" -> when (op) { "=" -> AdSkipSelector(text = raw); "^=" -> AdSkipSelector(textPrefix = raw); else -> AdSkipSelector(textContains = raw) }
                "desc" -> AdSkipSelector(description = raw)
                "name" -> AdSkipSelector(className = raw)
                "clickable" -> { require(raw in setOf("true", "false")); AdSkipSelector(clickable = raw == "true") }
                "visibleToUser", "enabled" -> { require(raw == "true"); AdSkipSelector() }
                "childCount" -> AdSkipSelector(childCount = raw.toInt())
                "index" -> AdSkipSelector(index = raw.toInt())
                else -> error("不支持的属性")
            }
            }
            results = results.flatMap { left -> branches.map { right -> merge(left, right) } }
            require(results.size <= 16) { "选择器分支过多" }
        }
        return results
    }

    private fun splitOr(value: String): List<String> {
        var quote = '\u0000'; var start = 0; val out = mutableListOf<String>(); var i = 0
        while (i < value.length) { val c = value[i]; if (quote != '\u0000') { if (c == quote && (i == 0 || value[i - 1] != '\\')) quote = '\u0000' } else if (c == '\'' || c == '"') quote = c else if (value.startsWith("||", i)) { out += value.substring(start, i); i += 2; start = i; continue }; i++ }
        out += value.substring(start); return out
    }

    private fun merge(a: AdSkipSelector, b: AdSkipSelector) = AdSkipSelector(
        id = a.id?.also { require(b.id == null || b.id == it) } ?: b.id,
        text = a.text?.also { require(b.text == null || b.text == it) } ?: b.text,
        textPrefix = a.textPrefix?.also { require(b.textPrefix == null || b.textPrefix == it) } ?: b.textPrefix,
        textContains = a.textContains?.also { require(b.textContains == null || b.textContains == it) } ?: b.textContains,
        description = a.description?.also { require(b.description == null || b.description == it) } ?: b.description,
        className = a.className?.also { require(b.className == null || b.className == it) } ?: b.className,
        clickable = a.clickable?.also { require(b.clickable == null || b.clickable == it) } ?: b.clickable,
        index = a.index?.also { require(b.index == null || b.index == it) } ?: b.index,
        childCount = a.childCount?.also { require(b.childCount == null || b.childCount == it) } ?: b.childCount,
        maxTextLength = listOfNotNull(a.maxTextLength, b.maxTextLength).minOrNull(),
    )

    private fun preview(name: String, version: Int, count: Int, rules: List<AdSkipRule>, issues: List<AdSkipImportIssue>, source: String) = AdSkipImportPreview(rules.takeIf { it.isNotEmpty() }?.let { AdSkipRulePack(2, version, label(name,80), source, it.distinctBy(AdSkipRule::id)) }, issues, count)
    private fun isAdGroup(name: String) = listOf("广告", "开屏", "弹窗", "卡片", "banner", "splash").any { name.contains(it, true) }
    private fun isAdText(text: String) = listOf("广告", "跳过", "关闭", "不感兴趣", "skip", "close", "dismiss").any { text.contains(it, true) }
    private fun stableId(value: String) = "import-" + MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).take(15).joinToString("") { "%02x".format(it) }
    private fun javaHash(value: String): String { var n = 0; value.forEach { n = 31 * n + it.code }; return n.toString() }
    private fun label(value: String, max: Int) = value.filterNot { it.isISOControl() || it == '|' }.take(max).ifBlank { "未命名" }
    private fun JsonObject.str(key: String) = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonElement?.strings() = when (this) { null -> emptyList(); is JsonArray -> map { it.jsonPrimitive.content }; is JsonPrimitive -> if (isString) listOf(content) else emptyList(); else -> error("预期字符串或数组") }
    private fun JsonElement?.entries() = when (this) { null -> emptyList(); is JsonArray -> this; else -> listOf(this) }
    private fun JsonElement?.asArrayOrNull() = this as? JsonArray

    /** Some LiTiaoTiao exports concatenate two valid arrays; accept that known export shape. */
    private fun splitTopLevelArrays(text: String): List<String> {
        val result = mutableListOf<String>(); var depth = 0; var quote = '\u0000'; var start = -1; var i = 0
        while (i < text.length) {
            val c = text[i]
            if (quote != '\u0000') { if (c == quote && !escaped(text, i)) quote = '\u0000' }
            else when (c) {
                '\'', '"' -> quote = c
                '[' -> { if (depth == 0) start = i; depth++ }
                ']' -> { depth--; if (depth == 0 && start >= 0) { result += text.substring(start, i + 1); start = -1 } }
            }
            i++
        }
        return result
    }

    private fun escaped(text: String, index: Int): Boolean {
        var slashes = 0; var i = index - 1
        while (i >= 0 && text[i] == '\\') { slashes++; i-- }
        return slashes % 2 == 1
    }
}
