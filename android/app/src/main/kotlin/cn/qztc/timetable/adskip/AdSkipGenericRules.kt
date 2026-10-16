package cn.qztc.timetable.adskip

/** Host-owned rules, expanded per package. External files cannot replace these IDs. */
object AdSkipGenericRules {
    const val ID = "builtin-global-splash"
    // Observed SDK controls; lookup alone never qualifies a button for a click.
    val lookupIds = listOf("fanti_ad_txt_skip", "tobid_splash_skip_text", "tobid_interstitial_skip_text", "ms_skipView")
    fun lookupIdsFor(packageName: String): List<String> = when (packageName) {
        "tv.danmaku.bili" -> listOf("count_down", "skip")
        "com.zmzx.college.search" -> listOf("id_skip_layout") + lookupIds
        else -> lookupIds
    }
    fun rule(packageName: String, label: String? = null) = AdSkipRule(
        id = ID, name = "通用广告跳过", packageName = packageName, appLabel = label,
        selectors = listOf(AdSkipSelector(skipLabel = true)),
        allowGesture = true, clickableAncestor = true, matchWindowMs = 0,
        activityNames = if (packageName == "com.sina.weibo") listOf("com.sina.weibo.SplashActivity") else emptyList(),
        successActivityNames = when (packageName) {
            "com.cainiao.wireless" -> listOf("com.cainiao.wireless.homepage.view.activity.HomePageActivity")
            "com.zjwh.android_wh_physicalfitness" -> listOf("com.zjwh.android_wh_physicalfitness.mvi.home.HomeActivity")
            else -> emptyList()
        },
    )

    fun findTarget(rule: AdSkipRule, nodes: List<AdSkipNode>): Int? {
        val screen = nodes.firstOrNull()?.bounds ?: return null
        if (screen.width <= 0 || screen.height <= 0) return null
        if (rule.packageName == "tv.danmaku.bili") {
            bilibiliPauseClose(nodes, screen)?.let { return it }
        }
        // GKD's Bilibili subscription uses these exact IDs because skip text
        // also appears in search suggestions and video pages.
        if (rule.packageName == "tv.danmaku.bili") return nodes.indices.singleOrNull { index ->
            val node = nodes[index]
            val box = node.bounds ?: return@singleOrNull false
            node.visible && node.enabled && node.packageName == rule.packageName &&
                node.id in setOf("tv.danmaku.bili:id/count_down", "tv.danmaku.bili:id/skip") &&
                box.width > 0 && box.height > 0 && box.containedBy(screen) &&
                box.width <= screen.width * .4 && box.height <= screen.height * .15 &&
                (box.top < screen.top + screen.height * .25 || box.bottom > screen.bottom - screen.height * .2) &&
                (listOfNotNull(node.text, node.description).any(AdSkipRules::isSkipLabel) || hasSplashAncestor(nodes, index))
        }
        val labelled = nodes.indices.firstOrNull { index ->
            val node = nodes[index]
            val b = node.bounds ?: return@firstOrNull false
            if (!node.visible || !node.enabled || node.packageName != rule.packageName || !b.containedBy(screen)) return@firstOrNull false
            if (b.width > screen.width * .4 || b.height > screen.height * .15) return@firstOrNull false
            if (isAdClose(nodes, index, screen)) return@firstOrNull true
            // Splash controls live near the screen edge.
            val edge = b.top < screen.top + screen.height * .25 || b.bottom > screen.bottom - screen.height * .2
            if (!edge) return@firstOrNull false
            if (node.id == "${rule.packageName}:id/ms_skipView" &&
                (hasSplashAncestor(nodes, index) || hasKnownAdOverlayAncestor(nodes, index))) return@firstOrNull true
            listOfNotNull(node.text, node.description).any { raw ->
                val label = raw.trim()
                if (!AdSkipRules.isSkipLabel(label)) false
                else label.contains("广告") || label.contains("ad", ignoreCase = true) ||
                    label.any(Char::isDigit) || advertisingSkipId.containsMatchIn(node.id.orEmpty()) ||
                    inSplashCountdown(nodes, index) || knownSplashContainer(nodes, index) ||
                    weiboSplashSkip(rule, node, b, screen)
            }
        }
        if (labelled != null) return labelled
        return AdSkipDrawnSplash.findTarget(rule, nodes) ?: AdSkipDrawnSplash.findNativeTarget(rule, nodes)
    }

    /** Observed pause-ad sheet: exact close description and its own ad-marked subtree. */
    private fun bilibiliPauseClose(nodes: List<AdSkipNode>, screen: AdSkipBounds): Int? {
        val candidates = nodes.indices.filter { index ->
            val node = nodes[index]
            val box = node.bounds ?: return@filter false
            node.visible && node.enabled && node.packageName == "tv.danmaku.bili" &&
                node.description == "关闭暂停页" && box.containedBy(screen) &&
                box.width in 1..(screen.width / 8) && box.height in 1..(screen.height / 12)
        }
        return candidates.singleOrNull()?.takeIf { index ->
            val target = nodes[index].bounds!!
            var parent = nodes[index].parent
            repeat(8) {
                val ancestorIndex = parent ?: return@takeIf false
                val ancestor = nodes.getOrNull(ancestorIndex) ?: return@takeIf false
                val box = ancestor.bounds ?: return@takeIf false
                if (ancestor.visible && ancestor.packageName == "tv.danmaku.bili" &&
                    box.width >= screen.width * .8 && box.height in (screen.height / 3)..(screen.height * 4 / 5) &&
                    box.top >= screen.top + screen.height / 5 && target.containedBy(box) &&
                    target.left >= box.left + box.width * 4 / 5 && target.top < box.top + box.height / 5) {
                    val ad = nodes.indices.any { child ->
                        nodes[child].visible && nodes[child].packageName == "tv.danmaku.bili" &&
                            nodes[child].bounds?.containedBy(box) == true && isDescendant(nodes, child, ancestorIndex) &&
                            listOfNotNull(nodes[child].text, nodes[child].description).any(::adMarker)
                    }
                    if (ad) return@takeIf true
                }
                parent = ancestor.parent
            }
            false
        }
    }

    /** Weibo's splash SDK exposes a drawn, id-less plain "跳过" at the
     * lower-right edge. The activity restriction is part of the rule so this
     * never applies to feed, composer, picker, or dialog windows. */
    private fun weiboSplashSkip(rule: AdSkipRule, node: AdSkipNode, bounds: AdSkipBounds,
        screen: AdSkipBounds): Boolean = rule.packageName == "com.sina.weibo" &&
        rule.activityNames == listOf("com.sina.weibo.SplashActivity") && node.id == null &&
        bounds.bottom > screen.bottom - screen.height * 0.12 &&
        bounds.left > screen.left + screen.width * 0.72

    fun relocatedTarget(rule: AdSkipRule, before: AdSkipNode, nodes: List<AdSkipNode>, index: Int): Boolean {
        val after = nodes.getOrNull(index) ?: return false
        if (countdownChildTarget(rule, before, nodes, index)) return true
        if (before.packageName != after.packageName || before.className != after.className ||
            before.id != after.id || before.text != after.text || before.description != after.description) return false
        return nodes.count { it.visible && it.enabled && it.packageName == before.packageName &&
            it.className == before.className && it.id == before.id && it.text == before.text &&
            it.description == before.description } == 1 && findTarget(rule, nodes) == index
    }

    /** Web splash rendering can split one labelled countdown into separate children. */
    private fun countdownChildTarget(rule: AdSkipRule, before: AdSkipNode, nodes: List<AdSkipNode>, index: Int): Boolean {
        val after = nodes[index]
        val box = before.bounds ?: return false
        if (before.id != "countdown-root" || before.packageName != rule.packageName ||
            !AdSkipRules.isSkipLabel(before.text) || after.packageName != before.packageName ||
            !AdSkipRules.isSkipLabel(after.text) || after.bounds?.containedBy(box) != true ||
            findTarget(rule, nodes) != index) return false
        var parent = after.parent
        repeat(12) {
            val ancestor = parent?.let(nodes::getOrNull) ?: return false
            if (ancestor.id == before.id) return ancestor.visible && ancestor.enabled &&
                ancestor.packageName == before.packageName && ancestor.className == before.className &&
                ancestor.bounds == box && inSplashCountdown(nodes, index) &&
                nodes.count { it.visible && it.id == before.id && it.packageName == before.packageName } == 1
            parent = ancestor.parent
        }
        return false
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
        val explicitInterstitialSkip = closeId.contains("interstitial") && closeId.contains("skip") &&
            labels.any { it == "关闭" || it.equals("skip", true) }
        // A close glyph is accepted only inside a bounded container that is
        // explicitly marked as advertising.
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
            if ((marked || (adContainer && (explicitInterstitialSkip || labels.any {
                it.equals("关闭广告", true) || it.equals("close ad", true)
            }))) && box.width in (screen.width / 4)..screen.width &&
                box.height in (screen.height / 10)..(screen.height * 4 / 5) &&
                b.left >= box.left + box.width * 2 / 3 && b.top <= box.top + box.height / 4 &&
                b.containedBy(box)) return true
            parent = container.parent
        }
        return false
    }

    private fun adMarker(value: String): Boolean = value.trim().lowercase() in
        setOf("广告", "广告内容", "advertisement", "advertising", "sponsored", "ad")

    /** Some SDKs put the countdown and plain skip text in separate child nodes. */
    private fun inSplashCountdown(nodes: List<AdSkipNode>, index: Int): Boolean {
        var parent = nodes[index].parent
        var countdown = false
        repeat(12) {
            val ancestor = parent?.let(nodes::getOrNull) ?: return false
            if (!ancestor.visible || ancestor.packageName != nodes[index].packageName) return false
            val id = ancestor.id?.substringAfter(":id/").orEmpty()
            // Several splash SDKs expose the countdown and the plain skip
            // label as siblings under a `*_count_and_skip_*` container rather
            // than using the word countdown. Keep this structural marker
            // limited to an enclosing splash tree.
            if (id == "countdown-root" || id.contains("countdown", true) ||
                id.contains("count_and_skip", true)) countdown = true
            if (countdown && id.contains("splash", true)) return true
            parent = ancestor.parent
        }
        return false
    }

    private fun hasSplashAncestor(nodes: List<AdSkipNode>, index: Int): Boolean {
        var parent = nodes[index].parent
        repeat(12) {
            val ancestor = parent?.let(nodes::getOrNull) ?: return false
            if (!ancestor.visible || ancestor.packageName != nodes[index].packageName) return false
            if (ancestor.id?.substringAfter(":id/")?.contains("splash", true) == true) return true
            parent = ancestor.parent
        }
        return false
    }

    /** Plain white circular “跳过” buttons often have no ID or countdown
     * digits. Accept them only inside a named full-screen splash container;
     * this keeps ordinary in-app buttons out of the generic rule. */
    private fun knownSplashContainer(nodes: List<AdSkipNode>, index: Int): Boolean {
        var parent = nodes[index].parent
        var splash = false
        repeat(16) {
            val ancestor = parent?.let(nodes::getOrNull) ?: return false
            val id = ancestor.id?.substringAfter(":id/").orEmpty().lowercase()
            if (id in setOf("splash_container", "splashroot", "splash_root") || id.contains("splash")) splash = true
            if (splash && (id.contains("countdown") || id.contains("count_and_skip"))) return true
            parent = ancestor.parent
        }
        return splash && nodes.any { node ->
            node.id?.contains(Regex("(?i)(countdown|count_and_skip)")) == true ||
                node.text?.contains(Regex("(?i)\\b[0-9]{1,2}\\s*(s|秒)\\b")) == true
        }
    }

    /** The 志愿汇 build uses `ll_ad/out_ad` instead of a splash-named root. */
    private fun hasKnownAdOverlayAncestor(nodes: List<AdSkipNode>, index: Int): Boolean {
        var parent = nodes[index].parent
        var hasOutAd = false
        var hasLlAd = false
        repeat(12) {
            val ancestor = parent?.let(nodes::getOrNull) ?: return false
            if (!ancestor.visible || ancestor.packageName != nodes[index].packageName) return false
            val id = ancestor.id?.substringAfter(":id/").orEmpty()
            if (id == "out_ad") hasOutAd = true
            if (id == "ll_ad") hasLlAd = true
            if (hasOutAd && hasLlAd) return true
            parent = ancestor.parent
        }
        return false
    }

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
