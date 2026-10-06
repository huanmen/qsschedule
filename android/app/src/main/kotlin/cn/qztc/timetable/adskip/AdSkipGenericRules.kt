package cn.qztc.timetable.adskip

/** Host-owned rules, expanded per package. External files cannot replace these IDs. */
object AdSkipGenericRules {
    const val ID = "builtin-global-splash"
    fun rule(packageName: String, label: String? = null) = AdSkipRule(
        id = ID, name = "通用广告跳过", packageName = packageName, appLabel = label,
        selectors = listOf(AdSkipSelector(skipLabel = true)),
        allowGesture = true, clickableAncestor = true, matchWindowMs = 12000,
        successActivityNames = when (packageName) {
            "com.cainiao.wireless" -> listOf("com.cainiao.wireless.homepage.view.activity.HomePageActivity")
            else -> emptyList()
        },
    )

    fun findTarget(rule: AdSkipRule, nodes: List<AdSkipNode>): Int? {
        val screen = nodes.firstOrNull()?.bounds ?: return null
        if (screen.width <= 0 || screen.height <= 0) return null
        val adMarker = nodes.any { it.visible && it.packageName == rule.packageName &&
            (it.text?.trim() == "广告" || it.description?.trim() == "广告") }
        return nodes.indices.firstOrNull { index ->
            val node = nodes[index]
            val b = node.bounds ?: return@firstOrNull false
            if (!node.visible || !node.enabled || node.packageName != rule.packageName || !b.containedBy(screen)) return@firstOrNull false
            // Small controls near the screen edge; never a full-screen advertising container.
            if (b.width > screen.width * .4 || b.height > screen.height * .15) return@firstOrNull false
            val edge = b.top < screen.top + screen.height * .25 || b.bottom > screen.bottom - screen.height * .2
            if (!edge) return@firstOrNull false
            listOfNotNull(node.text, node.description).any { raw ->
                val label = raw.trim()
                if (!AdSkipRules.isSkipLabel(label)) false
                else label.contains("广告") || label.contains("ad", ignoreCase = true) ||
                    label.any(Char::isDigit) || adMarker || advertisingSkipId.containsMatchIn(node.id.orEmpty())
            }
        }
    }

    private val advertisingSkipId = Regex("(?i)(splash.*skip|skip.*splash|ksad.*skip|gdt.*skip|tt_splash.*skip|ad[_-]skip|skip[_-]ad)")
}
