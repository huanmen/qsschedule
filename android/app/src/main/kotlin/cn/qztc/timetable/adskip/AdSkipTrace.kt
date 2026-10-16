package cn.qztc.timetable.adskip

import android.util.Log
import cn.qztc.timetable.BuildConfig

/** Debug-only metadata. Never logs arbitrary page text or persists a UI tree. */
internal object AdSkipTrace {
    private val packages = setOf("com.yiban.app", "com.mygolbs.mybus", "com.cainiao.wireless", "com.sina.weibo", "com.netease.cloudmusic", "com.realtech.xiaocan", "tv.danmaku.bili", "com.zmzx.college.search", "com.zjwh.android_wh_physicalfitness", "com.zzw.october")
    private val recent = java.util.ArrayDeque<String>()
    private var lastStructureAt = 0L
    @Synchronized fun state(message: String) {
        if (!BuildConfig.DEBUG) return
        Log.i("AdSkipTrace", message)
        recent.addLast("${android.os.SystemClock.uptimeMillis()} $message")
        while (recent.size > 60) recent.removeFirst()
    }
    @Synchronized fun dump(): List<String> = if (BuildConfig.DEBUG) recent.toList() else emptyList()
    fun verification(rule: AdSkipRule, stage: String) {
        if (BuildConfig.DEBUG && rule.packageName in packages) state("verify pkg=${rule.packageName} $stage")
    }
    fun matched(rule: AdSkipRule, node: AdSkipNode, window: Int) {
        if (!BuildConfig.DEBUG || rule.packageName !in packages) return
        state("matched pkg=${rule.packageName} rule=${rule.id} window=$window id=${node.id} bounds=${node.bounds} clickable=${node.clickable}")
    }
    fun action(rule: AdSkipRule, stage: String) {
        if (BuildConfig.DEBUG && rule.packageName in packages) state("action pkg=${rule.packageName} $stage")
    }
    fun tree(pkg: String, activity: String, nodes: List<AdSkipNode>, complete: Boolean, candidates: Int, elapsedMs: Long) {
        if (!BuildConfig.DEBUG || pkg !in packages) return
        val controls = nodes.filter {
            AdSkipRules.isSkipLabel(it.text) || AdSkipRules.isSkipLabel(it.description) ||
                it.id?.contains(Regex("(?i)(skip|splash|countdown)")) == true
        }.take(12).joinToString {
            val ancestors = mutableListOf<String>()
            var parent = it.parent
            repeat(10) {
                val ancestor = parent?.let(nodes::getOrNull) ?: return@repeat
                ancestors += ancestor.id.orEmpty()
                parent = ancestor.parent
            }
            "id=" + it.id + " text=" + it.text + " desc=" + it.description + " skipLabel=" + (AdSkipRules.isSkipLabel(it.text) || AdSkipRules.isSkipLabel(it.description)) +
                " visible=" + it.visible + " enabled=" + it.enabled + " clickable=" + it.clickable + " bounds=" + it.bounds + " ancestorIds=" + ancestors
        }
        state("tree pkg=$pkg activity=$activity count=${nodes.size} complete=$complete scanMs=$elapsedMs candidates=$candidates controls=[$controls]")
        val nativeContainers = nodes.indices.filter {
            nodes[it].id in setOf("$pkg:id/fl_native", "$pkg:id/fl_banner_native", "$pkg:id/ad_frameLayout")
        }
        val smallBusWindow = (pkg == "com.mygolbs.mybus" && nodes.size <= 60) ||
            (pkg == "com.zmzx.college.search" && activity.endsWith("InitActivity") && nodes.size in 20..80)
        val now = android.os.SystemClock.uptimeMillis()
        if ((nativeContainers.isNotEmpty() || smallBusWindow) && now - lastStructureAt >= 3000) {
            lastStructureAt = now
            nodes.forEachIndexed { index, node ->
                var current: Int? = index
                var inside = false
                repeat(nodes.size) {
                    if (current in nativeContainers) inside = true
                    if (current == null) return@repeat
                    current = current?.let(nodes::getOrNull)?.parent
                }
                if (inside || smallBusWindow) {
                    val marker = listOfNotNull(node.text, node.description).firstOrNull {
                        it in setOf("广告", "美团广告", "关闭", "关闭广告", "×", "X", "立即进入", "立即打开", "上滑/点击了解更多内容", "点击到落地页或三方APP", "点击跳转至详情页或第三方应用")
                    }
                    val disclosure = node.text?.let { text ->
                        if (text.contains("应用名称") && text.contains("开发者") && text.contains("权限详情") && text.contains("隐私")) "app-disclosure" else null
                    }
                    val sdk = node.description?.takeIf { it == "virtualNode" || it.startsWith("root:") }
                    state("native pkg=$pkg n=$index parent=${node.parent} i=${node.index} children=${node.childCount} " +
                        "id=${node.id} type=${node.className} marker=$marker disclosure=$disclosure sdk=$sdk visible=${node.visible} enabled=${node.enabled} click=${node.clickable} bounds=${node.bounds}")
                }
            }
        }
    }
}
