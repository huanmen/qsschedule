package cn.qztc.timetable.adskip

internal object AdSkipSafety {
    private val permissionPackages = setOf("com.android.permissioncontroller", "com.google.android.permissioncontroller",
        "com.android.systemui", "com.android.settings", "com.miui.securitycenter", "com.lbe.security.miui")
    private val consent = Regex("^(允许|始终允许|仅在使用中允许|不允许|拒绝|确定|取消|开启|去开启|立即开启|开启通知|打开通知|去设置|暂不开启|暂不打开|以后再说|暂不|同意|Allow|Don't allow)$", RegexOption.IGNORE_CASE)
    private fun prompt(value: String) = value.contains("通知") &&
        listOf("允许", "开启", "打开", "接收", "推送", "提醒").any(value::contains)

    fun systemPackage(pkg: String?) = pkg in permissionPackages
    fun onDisplay(target: AdSkipBounds?, display: AdSkipBounds) = target?.containedBy(display) == true
    fun unobscured(target: AdSkipBounds, systemWindows: List<AdSkipBounds>) = systemWindows.none {
        val x = target.left + target.width / 2
        val y = target.top + target.height / 2
        x >= it.left && x < it.right && y >= it.top && y < it.bottom
    }

    fun allowsTarget(nodes: List<AdSkipNode>, target: Int): Boolean {
        val node = nodes.getOrNull(target) ?: return false
        if (nodes.any { it.visible && it.packageName in permissionPackages }) return false
        // A downloaded rule must not turn a permission or notification consent into an ad action.
        if (listOfNotNull(node.text, node.description).any { consent.matches(it.trim()) || prompt(it) }) return false
        val screen = nodes.firstOrNull()?.bounds ?: return true
        fun under(i: Int, ancestor: Int): Boolean {
            var current: Int? = i
            repeat(nodes.size) {
                if (current == ancestor) return true
                current = current?.let(nodes::getOrNull)?.parent ?: return false
            }
            return false
        }
        var parent = node.parent
        var depth = 0
        while (parent != null && depth++ < nodes.size) {
            val containerIndex = parent
            val container = nodes.getOrNull(containerIndex) ?: return false
            val box = container.bounds
            if (box != null && box.width.toLong() * box.height < screen.width.toLong() * screen.height * .85) {
                val descendants = nodes.indices.filter { nodes[it].visible && under(it, containerIndex) }
                if (descendants.any { listOfNotNull(nodes[it].text, nodes[it].description).any(::prompt) } &&
                    descendants.any { listOfNotNull(nodes[it].text, nodes[it].description).any { label -> consent.matches(label.trim()) } }) return false
            }
            parent = container.parent
        }
        // A valid tree reaches its root; malformed cyclic parent chains fail closed.
        return parent == null
    }
}
