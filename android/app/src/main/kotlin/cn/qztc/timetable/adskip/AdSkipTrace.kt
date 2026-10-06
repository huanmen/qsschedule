package cn.qztc.timetable.adskip

import android.util.Log
import cn.qztc.timetable.BuildConfig

/** Debug-only metadata. Never logs arbitrary page text or persists a UI tree. */
internal object AdSkipTrace {
    private val packages = setOf("com.cainiao.wireless", "com.sina.weibo", "com.netease.cloudmusic", "com.realtech.xiaocan", "tv.danmaku.bili", "com.zmzx.college.search")
    private val recent = java.util.ArrayDeque<String>()
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
    fun tree(pkg: String, activity: String, nodes: List<AdSkipNode>, complete: Boolean, candidates: Int, elapsedMs: Long) {
        if (!BuildConfig.DEBUG || pkg !in packages) return
        val controls = nodes.filter {
            AdSkipRules.isSkipLabel(it.text) || AdSkipRules.isSkipLabel(it.description) ||
                it.id?.contains(Regex("(?i)(skip|splash|countdown)")) == true
        }.take(12).joinToString {
            "id=" + it.id + " skipLabel=" + (AdSkipRules.isSkipLabel(it.text) || AdSkipRules.isSkipLabel(it.description)) +
                " visible=" + it.visible + " enabled=" + it.enabled + " clickable=" + it.clickable + " bounds=" + it.bounds
        }
        state("tree pkg=$pkg activity=$activity count=${nodes.size} complete=$complete scanMs=$elapsedMs candidates=$candidates controls=[$controls]")
    }
}
