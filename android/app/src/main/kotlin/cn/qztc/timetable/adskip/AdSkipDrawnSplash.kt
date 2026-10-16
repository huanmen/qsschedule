package cn.qztc.timetable.adskip

/** Observed SDK virtual tree; never treats a plain image or a circle as a skip button. */
internal object AdSkipDrawnSplash {
    const val NATIVE_ID = "sportworld-native-drawn-close"

    fun findMyBusFeedbackTarget(nodes: List<AdSkipNode>): Int? {
        val pkg = "com.mygolbs.mybus"
        fun own(i: Int) = nodes[i].visible && nodes[i].enabled && nodes[i].packageName == pkg
        val labels = nodes.filter { it.visible && it.packageName == pkg }.mapNotNull { it.text }.toSet()
        if (!labels.containsAll(setOf("无法关闭", "不感兴趣", "举报广告", "点击复制广告ID"))) return null
        val label = nodes.indices.singleOrNull { own(it) && nodes[it].text == "不感兴趣" } ?: return null
        var parent = nodes[label].parent
        repeat(3) {
            val row = parent ?: return null
            if (!own(row)) return null
            if (nodes[row].clickable) {
                val box = nodes[row].bounds ?: return null
                val text = nodes[label].bounds ?: return null
                return row.takeIf { nodes[row].className?.substringAfterLast('.') == "LinearLayout" &&
                    text.containedBy(box) && box.height <= text.height * 3 && box.width <= text.width * 2 }
            }
            parent = nodes[row].parent
        }
        return null
    }

    fun findMyBusNativeTarget(rule: AdSkipRule, nodes: List<AdSkipNode>): Int? {
        if (rule.packageName != "com.mygolbs.mybus") return null
        findMyBusCardTarget(nodes)?.let { return it }
        val screen = nodes.firstOrNull()?.bounds ?: return null
        fun own(i: Int) = nodes[i].visible && nodes[i].enabled && nodes[i].packageName == rule.packageName
        fun under(i: Int, ancestor: Int): Boolean {
            var current: Int? = i
            repeat(nodes.size) {
                if (current == ancestor) return true
                current = current?.let(nodes::getOrNull)?.parent ?: return false
            }
            return false
        }
        fun type(i: Int, value: String) = nodes[i].className?.substringAfterLast('.') == value
        for (container in nodes.indices.filter { own(it) && nodes[it].id == "${rule.packageName}:id/ad_frameLayout" }) {
            val box = nodes[container].bounds ?: continue
            if (!box.containedBy(screen) || box.width < screen.width / 3 || box.height < screen.height / 12) continue
            val jad = nodes.indices.filter { i ->
                if (!own(i) || !under(i, container) || nodes[i].id != "${rule.packageName}:id/jad_feed_close" ||
                    !nodes[i].clickable || !type(i, "TextView")) return@filter false
                val b = nodes[i].bounds ?: return@filter false
                val parent = nodes[i].parent ?: return@filter false
                b.containedBy(box) && b.width in (screen.width * .02).toInt()..(screen.width * .08).toInt() &&
                    b.height in (screen.height * .01).toInt()..(screen.height * .05).toInt() &&
                    nodes[parent].childCount == 3 &&
                    nodes.indices.any { own(it) && under(it, parent) && nodes[it].id == "${rule.packageName}:id/jad_feed_image" } &&
                    nodes.indices.any { own(it) && under(it, parent) && nodes[it].id == "${rule.packageName}:id/jad_feed_source" }
            }
            jad.singleOrNull()?.let { return it }
            if (nodes.indices.none { own(it) && under(it, container) && nodes[it].text?.trim() == "广告" }) continue
            val candidates = nodes.indices.filter { i ->
                if (!own(i) || !under(i, container) || !type(i, "Image") || nodes[i].childCount != 0 ||
                    !nodes[i].id.isNullOrBlank() || !nodes[i].text.isNullOrBlank() || !nodes[i].description.isNullOrBlank()) return@filter false
                val b = nodes[i].bounds ?: return@filter false
                val parent = nodes[i].parent ?: return@filter false
                val siblings = nodes.indices.filter { nodes[it].parent == parent && own(it) }
                b.width in (screen.width * .02).toInt()..(screen.width * .06).toInt() &&
                    b.height in (screen.height * .01).toInt()..(screen.height * .03).toInt() &&
                    b.width.toDouble() / b.height in .8..1.2 &&
                    b.left >= box.left + box.width * .9 && b.top >= box.top + box.height * .7 &&
                    b.containedBy(box) && type(parent, "View") && nodes[parent].index == 3 && siblings == listOf(i) &&
                    nodes[parent].parent?.let { content ->
                        // WebView preserves its empty, zero-height subtitle slot.
                        // It is structural evidence even when not visible to accessibility.
                        nodes[content].childCount == 4 &&
                            nodes.filter { it.parent == content }.map { it.index }.toSet() == setOf(0, 1, 2, 3)
                    } == true &&
                    nodes.indices.any { type(it, "WebView") && under(i, it) && under(it, container) }
            }
            candidates.singleOrNull()?.let { return it }
        }
        return null
    }

    /** Observed six-branch popup: creative, animation, three CTA layers, close. */
    private fun findMyBusCardTarget(nodes: List<AdSkipNode>): Int? {
        val pkg = "com.mygolbs.mybus"
        val screen = nodes.firstOrNull()?.bounds ?: return null
        fun own(i: Int) = nodes[i].visible && nodes[i].enabled && nodes[i].packageName == pkg
        fun type(i: Int, name: String) = nodes[i].className?.substringAfterLast('.') == name
        fun children(i: Int) = nodes.indices.filter { nodes[it].parent == i && own(it) }
        fun under(i: Int, ancestor: Int): Boolean {
            var current: Int? = i
            repeat(nodes.size) {
                if (current == ancestor) return true
                current = current?.let(nodes::getOrNull)?.parent ?: return false
            }
            return false
        }
        val targets = mutableListOf<Int>()
        for (card in nodes.indices.filter { own(it) && type(it, "FrameLayout") && nodes[it].childCount in setOf(2, 6) }) {
            val direct = children(card)
            val alternate = nodes[card].childCount == 2
            if (direct.size != nodes[card].childCount) continue
            val content = if (alternate) direct.singleOrNull { nodes[it].index == 0 &&
                type(it, "FrameLayout") && nodes[it].childCount == 5 } ?: continue else card
            val branches = children(content)
            val count = if (alternate) 5 else 6
            if (branches.size != count || branches.map { nodes[it].index }.toSet() != (0 until count).toSet()) continue
            fun branch(index: Int) = branches.single { nodes[it].index == index }
            val box = nodes[card].bounds ?: continue
            if (!box.containedBy(screen) || box.width < screen.width * .85 ||
                box.height !in (screen.height * .55).toInt()..(screen.height * .9).toInt()) continue
            val creative = branch(if (alternate) 1 else 0)
            val animation = branch(if (alternate) 0 else 1)
            val cta = branch(4)
            val wrapper = if (alternate) direct.singleOrNull { nodes[it].index == 1 && type(it, "FrameLayout") } ?: continue else branch(5)
            val creativeValid = if (alternate) type(creative, "LinearLayout") && nodes[creative].childCount == 2 &&
                children(creative).any { type(it, "ImageView") } else
                type(creative, "ImageView") && nodes[creative].childCount == 0 && nodes[creative].bounds == box
            if (!creativeValid ||
                !type(animation, "FrameLayout") || nodes[animation].childCount != 1 ||
                (2 until count).any { !type(branch(it), "FrameLayout") }) continue
            val ctaBox = nodes[cta].bounds ?: continue
            if (nodes[branch(2)].bounds != ctaBox || nodes[branch(3)].bounds != ctaBox ||
                !ctaBox.containedBy(box) || ctaBox.width < box.width * .8 || ctaBox.height > box.height * .2 ||
                ctaBox.top < box.top + box.height * .75 ||
                nodes.indices.none { own(it) && under(it, cta) && nodes[it].text == "立即打开" } ||
                nodes.indices.none { own(it) && under(it, cta) && nodes[it].text == "上滑/点击了解更多内容" }) continue
            val target = children(wrapper).singleOrNull() ?: continue
            val node = nodes[target]
            val close = node.bounds ?: continue
            if (!type(target, "ImageView") || node.childCount != 0 || !node.id.isNullOrBlank() ||
                !node.text.isNullOrBlank() || !node.description.isNullOrBlank() || nodes[wrapper].bounds != close ||
                !close.containedBy(box) || close.width !in (screen.width * .03).toInt()..(screen.width * .08).toInt() ||
                close.width.toDouble() / close.height !in .85..1.15 ||
                close.right > box.left + box.width * .15 || close.top <= ctaBox.bottom ||
                close.bottom < box.top + box.height * .94) continue
            val parent = nodes[card].parent ?: continue
            val siblings = children(parent)
            val footer = siblings.singleOrNull { nodes[it].index == 0 && it != card } ?: continue
            if (nodes[card].index != 1 || nodes[parent].childCount != 2 || !type(footer, "FrameLayout") ||
                nodes[footer].childCount != 2 || children(footer).none { type(it, "ImageView") }) continue
            targets += target
        }
        return targets.singleOrNull()
    }

    fun findNativeTarget(rule: AdSkipRule, nodes: List<AdSkipNode>): Int? {
        if (rule.packageName != "com.zjwh.android_wh_physicalfitness") return null
        if (rule.id == NATIVE_ID) findSportBannerTarget(nodes)?.let { return it }
        val screen = nodes.firstOrNull()?.bounds ?: return null
        fun own(i: Int) = nodes[i].visible && nodes[i].enabled && nodes[i].packageName == rule.packageName
        fun children(i: Int) = nodes.indices.filter { nodes[it].parent == i && own(it) }
        fun type(i: Int, value: String) = nodes[i].className?.substringAfterLast('.') == value
        fun under(i: Int, ancestor: Int): Boolean {
            var current: Int? = i
            repeat(nodes.size) {
                if (current == ancestor) return true
                current = current?.let(nodes::getOrNull)?.parent ?: return false
            }
            return false
        }
        for (virtual in nodes.indices.filter { own(it) && type(it, "ViewGroup") &&
            nodes[it].description == "virtualNode" }) {
            if (nodes.indices.none { nodes[it].id == rule.packageName + ":id/fl_native" && under(virtual, it) }) continue
            val content = children(virtual).singleOrNull() ?: continue
            if (!type(content, "ViewGroup") || nodes[content].childCount != 2) continue
            val body = children(content).singleOrNull { nodes[it].index == 1 } ?: continue
            val layout = children(body).singleOrNull() ?: continue
            val branches = children(layout)
            if (!type(layout, "ViewGroup") || nodes[layout].childCount != 3 || branches.size != 3 ||
                branches.map { nodes[it].index }.toSet() != setOf(0, 1, 2)) continue
            val creative = branches.single { nodes[it].index == 0 }
            val wrapper = branches.single { nodes[it].index == 1 }
            val marker = branches.single { nodes[it].index == 2 }
            if (nodes.indices.none { own(it) && under(it, marker) && nodes[it].text == "广告" } ||
                nodes.indices.none { own(it) && under(it, creative) && type(it, "ImageView") }) continue
            val target = children(wrapper).singleOrNull() ?: continue
            val node = nodes[target]
            val box = node.bounds ?: continue
            val container = nodes[layout].bounds ?: continue
            if (!type(wrapper, "ViewGroup") || !type(target, "ImageView") || node.childCount != 0 ||
                !node.id.isNullOrBlank() || !node.text.isNullOrBlank() ||
                nodes[wrapper].bounds != box || !box.containedBy(container) || !container.containedBy(screen) ||
                container.width < screen.width * .5 || container.height > screen.height * .8 ||
                box.width !in (screen.width * .02).toInt()..(screen.width * .09).toInt() ||
                box.height !in (screen.height * .01).toInt()..(screen.height * .06).toInt() ||
                box.width.toDouble() / box.height !in .85..1.15 ||
                box.left < container.left + container.width * .8 ||
                box.top > container.top + container.height * .15) continue
            return target
        }
        return null
    }

    fun sportBannerPresent(nodes: List<AdSkipNode>): Boolean = nodes.indices.any { container ->
        nodes[container].id == "com.zjwh.android_wh_physicalfitness:id/fl_banner_native" &&
            nodes.indices.any { i -> nodes[i].parent == container && nodes[i].childCount in 4..5 &&
                nodes[i].className?.endsWith("RelativeLayout") == true }
    }

    fun isSportBannerTarget(nodes: List<AdSkipNode>, target: Int): Boolean {
        var current: Int? = target
        repeat(nodes.size) {
            val node = current?.let(nodes::getOrNull) ?: return false
            if (node.id == "com.zjwh.android_wh_physicalfitness:id/fl_banner_native") return true
            current = node.parent
        }
        return false
    }

    private fun findSportBannerTarget(nodes: List<AdSkipNode>): Int? {
        val pkg = "com.zjwh.android_wh_physicalfitness"
        val screen = nodes.firstOrNull()?.bounds ?: return null
        fun own(i: Int) = nodes[i].visible && nodes[i].enabled && nodes[i].packageName == pkg
        fun type(i: Int, name: String) = nodes[i].className?.substringAfterLast('.') == name
        fun children(i: Int) = nodes.indices.filter { nodes[it].parent == i }
        val targets = mutableListOf<Int>()
        for (container in nodes.indices.filter { own(it) && nodes[it].id == "$pkg:id/fl_banner_native" }) {
            val box = nodes[container].bounds ?: continue
            if (!box.containedBy(screen) || box.width < screen.width * .9 || box.height > screen.height * .2 ||
                box.left < screen.left + screen.width * .02 || box.right > screen.right - screen.width * .02) continue
            val layout = children(container).singleOrNull() ?: continue
            val branches = children(layout)
            // Older carousel builds omit the marker branch and expose four
            // siblings: provider title, SDK controls, feedback hit-area and
            // creative. The close remains an independent ImageView under the
            // controls branch, so it can be handled without touching the ad.
            if (own(layout) && type(layout, "RelativeLayout") && nodes[layout].childCount == 4 &&
                branches.size == 4 && branches.map { nodes[it].index }.toSet() == setOf(1, 2, 3, 4)) {
                val title = branches.single { nodes[it].index == 1 }
                val controls = branches.single { nodes[it].index == 2 }
                val feedback = branches.single { nodes[it].index == 3 }
                val creative = branches.single { nodes[it].index == 4 }
                val controlChildren = children(controls)
                val creativeChildren = children(creative)
                if (type(title, "TextView") && !nodes[title].text.isNullOrBlank() &&
                    type(controls, "FrameLayout") && nodes[controls].childCount == 3 &&
                    controlChildren.size == 3 && controlChildren.map { nodes[it].index }.toSet() == setOf(0, 1, 2) &&
                    type(feedback, "View") && nodes[feedback].clickable &&
                    type(creative, "FrameLayout") && nodes[creative].childCount == 2 &&
                    creativeChildren.size == 2 && creativeChildren.all { type(it, "ImageView") && !nodes[it].clickable }) {
                    val target = controlChildren.single { nodes[it].index == 2 }
                    val node = nodes[target]
                    val close = node.bounds
                    if (type(target, "ImageView") && node.clickable && node.childCount == 0 &&
                        node.id.isNullOrBlank() && node.text.isNullOrBlank() && node.description.isNullOrBlank() &&
                        close != null && close.containedBy(box) &&
                        close.width in (screen.width * .03).toInt()..(screen.width * .09).toInt() &&
                        close.width.toDouble() / close.height in .85..1.15 &&
                        close.top >= box.top + box.height * .45 && close.right <= box.left + box.width * .7) {
                        targets += target
                    }
                }
            }
            if (!own(layout) || !type(layout, "RelativeLayout") || nodes[layout].childCount != 5 ||
                branches.size != 5 || branches.map { nodes[it].index }.toSet() != (0..4).toSet()) continue
            val mark = branches.single { nodes[it].index == 2 }
            val creative = branches.single { nodes[it].index == 4 }
            val controls = children(mark)
            val images = children(creative)
            if (!type(mark, "FrameLayout") || nodes[mark].childCount != 3 || controls.size != 3 ||
                controls.map { nodes[it].index }.toSet() != setOf(0, 1, 2) ||
                !type(creative, "FrameLayout") || nodes[creative].childCount != 2 || images.size != 2 ||
                images.any { !type(it, "ImageView") || nodes[it].clickable } ||
                !type(controls.single { nodes[it].index == 0 }, "ImageView") ||
                !type(controls.single { nodes[it].index == 1 }, "FrameLayout")) continue
            val target = controls.single { nodes[it].index == 2 }
            val node = nodes[target]
            val close = node.bounds ?: continue
            if (!own(target) || !type(target, "ImageView") || !node.clickable || node.childCount != 0 ||
                !node.id.isNullOrBlank() || !node.text.isNullOrBlank() || !node.description.isNullOrBlank() ||
                !close.containedBy(box) || close.width !in (screen.width * .03).toInt()..(screen.width * .09).toInt() ||
                close.width.toDouble() / close.height !in .85..1.15 || close.top < box.top + box.height * .5 ||
                close.right > box.left + box.width * .7) continue
            targets += target
        }
        return targets.singleOrNull()
    }

    fun findTarget(rule: AdSkipRule, nodes: List<AdSkipNode>): Int? {
        findCollegeInsetSplash(rule, nodes)?.let { return it }
        val screen = nodes.firstOrNull()?.bounds ?: return null
        if (screen.width <= 0 || screen.height <= 0) return null
        fun own(i: Int) = nodes[i].visible && nodes[i].enabled && nodes[i].packageName == rule.packageName
        fun type(i: Int, name: String) = nodes[i].className?.substringAfterLast('.') == name
        fun children(i: Int) = nodes.indices.filter { nodes[it].parent == i && own(it) }
        fun fullScreen(i: Int) = nodes[i].bounds?.let {
            it.containedBy(screen) && it.width >= screen.width * .95 && it.height >= screen.height * .95
        } == true
        fun under(i: Int, ancestor: Int): Boolean {
            var parent = nodes[i].parent
            repeat(24) {
                if (parent == ancestor) return true
                parent = parent?.let(nodes::getOrNull)?.parent ?: return false
            }
            return false
        }
        // Another observed SDK renders its skip as a View sibling of the
        // creative, rather than inside virtualNode.
        for (container in nodes.indices.filter { own(it) && type(it, "FrameLayout") &&
            nodes[it].childCount == 4 && fullScreen(it) }) {
            val branches = children(container)
            if (branches.size != 4 || branches.map { nodes[it].index }.toSet() != setOf(0, 1, 2, 3)) continue
            var parent = nodes[container].parent
            var splash = false
            repeat(24) {
                val ancestor = parent?.let(nodes::getOrNull) ?: return@repeat
                val id = ancestor.id?.substringAfter(":id/").orEmpty()
                if (ancestor.packageName == rule.packageName && ancestor.visible &&
                    (id.contains("splash", true) || (rule.packageName == "com.zjwh.android_wh_physicalfitness" &&
                        id == "fl_container"))) splash = true
                parent = ancestor.parent
            }
            if (!splash) continue
            val creative = branches.single { nodes[it].index == 1 }
            val target = branches.single { nodes[it].index == 2 }
            val mark = branches.single { nodes[it].index == 3 }
            val sdkRoot = nodes.indices.singleOrNull { own(it) && under(it, creative) &&
                nodes[it].description == "root:root:0:0:0" } ?: continue
            if (nodes[sdkRoot].description != "root:root:0:0:0" || !fullScreen(sdkRoot) ||
                nodes.indices.none { own(it) && under(it, sdkRoot) &&
                    nodes[it].description?.startsWith("image-long:") == true && fullScreen(it) } ||
                nodes.indices.none { own(it) && under(it, sdkRoot) &&
                    nodes[it].description?.startsWith("fillButton:") == true } ||
                nodes.indices.none { own(it) && under(it, sdkRoot) &&
                    nodes[it].text == "点击跳转至详情页或第三方应用" } ||
                nodes[mark].text != "美团广告") continue
            val node = nodes[target]
            val box = node.bounds ?: continue
            if (!type(target, "View") || !node.clickable || node.childCount != 0 ||
                !node.id.isNullOrBlank() || !node.text.isNullOrBlank() || !node.description.isNullOrBlank() ||
                !box.containedBy(screen) || box.width < screen.width * .04 || box.width > screen.width * .12 ||
                box.height < screen.height * .02 || box.height > screen.height * .07 ||
                box.width.toDouble() / box.height !in .85..1.15 ||
                box.left < screen.left + screen.width * .02 || box.right > screen.left + screen.width * .22 ||
                box.top < screen.top + screen.height * .85 || box.bottom > screen.top + screen.height * .97) continue
            return target
        }
        for (virtual in nodes.indices.filter { own(it) && type(it, "ViewGroup") &&
            nodes[it].description == "virtualNode" && fullScreen(it) }) {
            var parent = nodes[virtual].parent
            var splash = false
            repeat(24) {
                val ancestor = parent?.let(nodes::getOrNull) ?: return@repeat
                val id = ancestor.id?.substringAfter(":id/").orEmpty()
                if (ancestor.packageName == rule.packageName && ancestor.visible &&
                    (id.contains("splash", true) || (rule.packageName == "com.zjwh.android_wh_physicalfitness" && id == "fl_container"))) splash = true
                parent = ancestor.parent
            }
            if (!splash) continue
            val content = children(virtual).singleOrNull()?.takeIf { type(it, "ViewGroup") && fullScreen(it) } ?: continue
            if (nodes[content].childCount != 4) continue
            val branches = children(content)
            if (branches.size != 4 || branches.map { nodes[it].index }.toSet() != setOf(0, 1, 2, 3)) continue
            val creative = branches.single { nodes[it].index == 0 }
            val skipBranch = branches.single { nodes[it].index == 1 }
            val landing = branches.single { nodes[it].index == 2 }
            val mark = branches.single { nodes[it].index == 3 }
            if (!fullScreen(creative) || !fullScreen(skipBranch) ||
                nodes.indices.none { own(it) && under(it, landing) && nodes[it].text == "立即进入" } ||
                nodes.indices.none { own(it) && under(it, landing) && nodes[it].text == "点击到落地页或三方APP" } ||
                nodes.indices.none { own(it) && under(it, mark) && nodes[it].text == "广告" }) continue
            val wrapper = children(skipBranch).singleOrNull()?.takeIf { type(it, "ViewGroup") && nodes[it].index == 0 } ?: continue
            val target = children(wrapper).singleOrNull()?.takeIf { type(it, "ImageView") && nodes[it].index == 0 && nodes[it].childCount == 0 } ?: continue
            val node = nodes[target]
            val box = node.bounds ?: continue
            if (!node.id.isNullOrBlank() || !node.text.isNullOrBlank() || !node.description.isNullOrBlank() ||
                nodes[wrapper].bounds != box || !box.containedBy(screen) ||
                box.width < screen.width * .04 || box.width > screen.width * .12 ||
                box.height < screen.height * .02 || box.height > screen.height * .07 ||
                box.width.toDouble() / box.height !in .85..1.15 ||
                box.left < screen.left + screen.width * .02 || box.right > screen.left + screen.width * .22 ||
                box.top < screen.top + screen.height * .85 || box.bottom > screen.top + screen.height * .97) continue
            return target
        }
        return null
    }

    /** Observed inset SDK splash leaves the host logo below its drawn skip. */
    private fun findCollegeInsetSplash(rule: AdSkipRule, nodes: List<AdSkipNode>): Int? {
        val pkg = "com.zmzx.college.search"
        if (rule.packageName != pkg) return null
        val screen = nodes.firstOrNull()?.bounds ?: return null
        fun own(i: Int) = nodes[i].visible && nodes[i].enabled && nodes[i].packageName == pkg
        fun type(i: Int, name: String) = nodes[i].className?.substringAfterLast('.') == name
        fun children(i: Int) = nodes.indices.filter { nodes[it].parent == i }
        fun under(i: Int, ancestor: Int): Boolean {
            var current: Int? = i
            repeat(nodes.size) {
                if (current == ancestor) return true
                current = current?.let(nodes::getOrNull)?.parent ?: return false
            }
            return false
        }
        val backgrounds = nodes.indices.filter { own(it) && nodes[it].id == "$pkg:id/app_splash_background" }
        val logos = nodes.indices.filter { own(it) && nodes[it].id == "$pkg:id/id_splash_bottom_logo" }
        if (backgrounds.size != 1 || logos.size != 1) return null
        val logo = nodes[logos.single()].bounds ?: return null
        val targets = mutableListOf<Int>()
        for (container in nodes.indices.filter { own(it) && type(it, "RelativeLayout") && nodes[it].childCount == 9 }) {
            val branches = children(container)
            val box = nodes[container].bounds ?: continue
            if (branches.size != 9 || branches.map { nodes[it].index }.toSet() != (0..8).toSet() ||
                !box.containedBy(screen) || box.left != screen.left || box.top != screen.top ||
                box.width < screen.width * .95 || box.height !in (screen.height * .75).toInt()..(screen.height * .9).toInt() ||
                logo.top < box.bottom) continue
            fun branch(i: Int) = branches.single { nodes[it].index == i }
            val target = branch(3)
            val creative = branch(1)
            val cta = branch(5)
            val node = nodes[target]
            val close = node.bounds ?: continue
            if (!own(target) || !type(target, "TextView") || !node.clickable || node.text?.trim() != "跳过" ||
                node.childCount != 0 || !node.id.isNullOrBlank() || !node.description.isNullOrBlank() ||
                !close.containedBy(box) || close.left != box.left || close.bottom != box.bottom ||
                close.width !in (box.width * .2).toInt()..(box.width * .4).toInt() || close.height > box.height * .15 ||
                !type(creative, "RelativeLayout") || nodes[creative].bounds != box ||
                nodes.indices.none { own(it) && under(it, creative) && type(it, "ImageView") } ||
                !type(cta, "RelativeLayout") || !nodes[cta].clickable || nodes[cta].childCount != 1 ||
                !type(branch(2), "FrameLayout") || nodes[branch(2)].childCount != 2 ||
                (7..8).any { !own(branch(it)) || !type(branch(it), "ImageView") || !nodes[branch(it)].clickable }) continue
            targets += target
        }
        for (container in nodes.indices.filter { own(it) && type(it, "FrameLayout") && nodes[it].childCount == 4 }) {
            val branches = children(container)
            if (branches.size != 4 || branches.map { nodes[it].index }.toSet() != (0..3).toSet()) continue
            fun branch(i: Int) = branches.single { nodes[it].index == i }
            val box = nodes[container].bounds ?: continue
            if (!box.containedBy(screen) || box.left != screen.left || box.top != screen.top ||
                box.width < screen.width * .95 || box.height !in (screen.height * .75).toInt()..(screen.height * .9).toInt() ||
                logo.top < box.bottom || logo.width < screen.width * .2) continue
            val creative = branch(1)
            val target = branch(2)
            val marker = branch(3)
            if (!own(creative) || !type(creative, "FrameLayout") || nodes[creative].bounds != box ||
                !own(marker) || !type(marker, "TextView") || nodes[marker].childCount != 0) continue
            val virtual = nodes.indices.singleOrNull { own(it) && under(it, creative) &&
                type(it, "ViewGroup") && nodes[it].description == "virtualNode" } ?: continue
            val virtualBox = nodes[virtual].bounds ?: continue
            val content = children(virtual).singleOrNull() ?: continue
            val parts = children(content)
            if (!own(content) || !type(content, "ViewGroup") || nodes[content].bounds != virtualBox ||
                !virtualBox.containedBy(box) || virtualBox.width != box.width || virtualBox.height < box.height * .9 ||
                nodes[content].childCount != 3 || parts.size != 3 || parts.map { nodes[it].index }.toSet() != (0..2).toSet()) continue
            val landing = parts.single { nodes[it].index == 2 }
            val swipe = parts.single { nodes[it].index == 1 }
            fun labelBelow(parent: Int, text: String) = nodes.indices.any { own(it) && under(it, parent) && nodes[it].text == text }
            if (!labelBelow(landing, "立即进入") || !labelBelow(landing, "点击到落地页或三方APP") ||
                !labelBelow(swipe, "向上滑动") || !labelBelow(swipe, "跳转至详情页或第三方应用")) continue
            val node = nodes[target]
            val close = node.bounds ?: continue
            val mark = nodes[marker].bounds ?: continue
            if (!own(target) || !type(target, "View") || !node.clickable || node.childCount != 0 ||
                !node.id.isNullOrBlank() || !node.text.isNullOrBlank() || !node.description.isNullOrBlank() ||
                !close.containedBy(box) || close.width !in (screen.width * .04).toInt()..(screen.width * .12).toInt() ||
                close.width.toDouble() / close.height !in .85..1.15 || close.left < box.left + box.width * .02 ||
                close.right > box.left + box.width * .2 || close.top < box.top + box.height * .8 ||
                close.bottom > box.top + box.height * .95 || !mark.containedBy(box) ||
                mark.top < close.bottom || mark.bottom > box.bottom || mark.width > close.width * 1.5 ||
                mark.left < close.left || mark.right > close.right) continue
            targets += target
        }
        return targets.singleOrNull()
    }
}
