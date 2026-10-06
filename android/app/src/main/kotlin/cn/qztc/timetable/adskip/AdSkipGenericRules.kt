package cn.qztc.timetable.adskip

/** Host-owned rules, expanded per package. External files cannot replace these IDs. */
object AdSkipGenericRules {
    const val ID = "builtin-global-splash"
    fun rule(packageName: String, label: String? = null) = AdSkipRule(
        id = ID, name = "通用广告跳过", packageName = packageName, appLabel = label,
        selectors = listOf(AdSkipSelector(skipLabel = true)),
        allowGesture = true, clickableAncestor = true, matchWindowMs = 0,
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
        val labelled = nodes.indices.firstOrNull { index ->
            val node = nodes[index]
            val b = node.bounds ?: return@firstOrNull false
            if (!node.visible || !node.enabled || node.packageName != rule.packageName || !b.containedBy(screen)) return@firstOrNull false
            if (b.width > screen.width * .4 || b.height > screen.height * .15) return@firstOrNull false
            if (isAdClose(nodes, index, screen)) return@firstOrNull true
            // Splash controls live near the screen edge.
            val edge = b.top < screen.top + screen.height * .25 || b.bottom > screen.bottom - screen.height * .2
            if (!edge) return@firstOrNull false
            listOfNotNull(node.text, node.description).any { raw ->
                val label = raw.trim()
                if (!AdSkipRules.isSkipLabel(label)) false
                else label.contains("广告") || label.contains("ad", ignoreCase = true) ||
                    label.any(Char::isDigit) || adMarker || advertisingSkipId.containsMatchIn(node.id.orEmpty())
            }
        }
        if (labelled != null) return labelled
        // Some splash SDKs expose only the ad container and draw the skip
        // control on a canvas. The service will use a constrained top-right
        // gesture for this explicit container, never for a generic window.
        return nodes.indices.firstOrNull { index ->
            val node = nodes[index]
            val id = node.id?.substringAfter(":id/").orEmpty().lowercase()
            val b = node.bounds ?: return@firstOrNull false
            node.visible && node.enabled && node.packageName == rule.packageName &&
                id in setOf("splash_ad_container", "splash_container") &&
                b.width >= screen.width * .7 && b.height >= screen.height * .45
        }
    }

    fun relocatedTarget(rule: AdSkipRule, before: AdSkipNode, nodes: List<AdSkipNode>, index: Int): Boolean {
        val after = nodes.getOrNull(index) ?: return false
        if (before.packageName != after.packageName || before.className != after.className ||
            before.id != after.id || before.text != after.text || before.description != after.description) return false
        return nodes.count { it.visible && it.enabled && it.packageName == before.packageName &&
            it.className == before.className && it.id == before.id && it.text == before.text &&
            it.description == before.description } == 1 && findTarget(rule, nodes) == index
    }

    /** An unlabelled X is safe only when its own bounded container is marked as an ad. */
    private fun isAdClose(nodes: List<AdSkipNode>, index: Int, screen: AdSkipBounds): Boolean {
        val node = nodes[index]
        val b = node.bounds ?: return false
        val labels = listOfNotNull(node.text, node.description).map(String::trim)
        if (labels.any { it.equals("关闭广告", true) || it.equals("close ad", true) || it.equals("close advertisement", true) }) return true
        val closeId = node.id?.substringAfter(":id/")?.lowercase().orEmpty()
        val looksLikeClose = labels.any { it in setOf("×", "✕", "✖", "X", "x", "关闭") } ||
            closeId.contains("close") || closeId.contains("dismiss")
        // Some SDKs expose the drawn X as an ImageView and put the click action
        // on a small clickable parent. Require a large image ad above it and a
        // compact control in the lower-middle overlay area.
        if (!looksLikeClose && node.className?.substringAfterLast('.') == "ImageView" &&
            node.parent?.let(nodes::getOrNull)?.clickable == true &&
            b.top > screen.top + screen.height * .52 && b.bottom < screen.bottom - screen.height * .03 &&
            nodes.any { candidate -> candidate.className?.substringAfterLast('.') == "ImageView" &&
                candidate.bounds?.let { it.width > screen.width * .55 && it.height > screen.height * .25 && it.top < screen.top + screen.height * .3 } == true }) {
            return true
        }
        if (!looksLikeClose || b.width > screen.width * .16 || b.height > screen.height * .08) return false
        var parent = node.parent
        repeat(6) {
            val container = parent?.let(nodes::getOrNull) ?: return false
            val box = container.bounds ?: return false
            val children = nodes.indices.filter { child -> isDescendant(nodes, child, parent!!) }
            val marked = listOfNotNull(container.text, container.description).any(::adMarker) ||
                children.any { child -> listOfNotNull(nodes[child].text, nodes[child].description).any(::adMarker) }
            val adId = container.id?.substringAfter(":id/")?.lowercase().orEmpty()
            val adContainer = adContainerId.containsMatchIn(adId)
            if ((marked || adContainer) && box.width in (screen.width / 4)..screen.width &&
                box.height in (screen.height / 10)..(screen.height * 4 / 5) &&
                b.left >= box.left + box.width * 2 / 3 && b.top <= box.top + box.height / 4 &&
                b.containedBy(box)) return true
            parent = container.parent
        }
        return false
    }

    private fun adMarker(value: String): Boolean = value.trim().lowercase() in
        setOf("广告", "广告内容", "advertisement", "advertising", "sponsored", "ad")

    private fun isDescendant(nodes: List<AdSkipNode>, child: Int, ancestor: Int): Boolean {
        var parent = nodes[child].parent
        repeat(12) {
            if (parent == ancestor) return true
            parent = parent?.let(nodes::getOrNull)?.parent ?: return false
        }
        return false
    }

    private val advertisingSkipId = Regex("(?i)(splash.*skip|skip.*splash|ksad.*skip|gdt.*skip|tt_splash.*skip|ad[_-]skip|skip[_-]ad)")
    private val adContainerId = Regex("(^|[_-])(ad|ads|advert|splash)([_-]|$)")
}
