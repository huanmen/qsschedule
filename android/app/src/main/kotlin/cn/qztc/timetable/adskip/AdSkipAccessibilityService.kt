package cn.qztc.timetable.adskip

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AdSkipAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val lastAction = mutableMapOf<String, Long>()
    private val lastDiagnostic = mutableMapOf<String, Long>()
    private var activity = ""
    private var foregroundPackage = ""
    private var windowId = -1
    private var enteredAt = 0L
    private var latestEventAt = 0L
    private var scanScheduled = false
    private var pending: AdSkipRule? = null
    private val scanTask = Runnable { scanScheduled = false; scan() }
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != "records") updatePackageFilter()
    }

    override fun onCreate() {
        super.onCreate()
        AdSkipStore.prefs(this).registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    override fun onServiceConnected() { AdSkipRuntime.setConnected(true); updatePackageFilter() }

    private fun updatePackageFilter() {
        if (!AdSkipRuntime.connected.value) return
        if (!AdSkipStore.installed(this)) {
            disconnect()
            disableSelf()
            return
        }
        val info = serviceInfo ?: return
        // Empty/null packageNames means ALL apps on Android. Use a non-installed sentinel.
        info.packageNames = AdSkipStore.activePackages(this).ifEmpty { listOf("cn.qztc.adskip.disabled") }.toTypedArray()
        serviceInfo = info
        handler.removeCallbacksAndMessages(null)
        scanScheduled = false
        pending?.let { finish(it, AdSkipResult.UNCONFIRMED) }
        activity = ""
        lastAction.clear()
        lastDiagnostic.clear()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        AdSkipRuntime.setConnected(true)
        if (!AdSkipStore.enabled(this)) return
        val pkg = event.packageName?.toString() ?: return
        if (!AdSkipStore.appEnabled(this, pkg)) return
        val now = SystemClock.uptimeMillis()
        if (!AdSkipTiming.fresh(event.eventTime, now)) {
            activity = ""
            handler.removeCallbacks(scanTask)
            scanScheduled = false
            return
        }
        latestEventAt = event.eventTime
        if (pkg != foregroundPackage || event.windowId != windowId) {
            foregroundPackage = pkg
            windowId = event.windowId
            activity = ""
            enteredAt = now
        }
        // Use exact configured activity names, including HomeActivityV2, not widget classes.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val name = event.className?.toString().orEmpty()
            val known = AdSkipStore.rules(this).flatMap { it.activityNames + it.successActivityNames }
            if (name in known) {
                if (activity != name) enteredAt = now
                activity = name
            } else if (!name.startsWith("android.") && !name.startsWith("androidx.")) {
                activity = ""
            }
        }
        if (!scanScheduled && pending == null) {
            scanScheduled = true
            handler.postDelayed(scanTask, 100)
        }
    }

    private fun active(rule: AdSkipRule) = AdSkipRuntime.connected.value && AdSkipStore.enabled(this) &&
        AdSkipStore.appEnabled(this, rule.packageName) && AdSkipStore.ruleEnabled(this, rule) &&
        AdSkipStore.rules(this).any { it == rule }

    private fun scan() {
        if (!AdSkipStore.enabled(this) || pending != null) return
        if (!AdSkipTiming.fresh(latestEventAt, SystemClock.uptimeMillis())) return
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString()
        if (pkg != foregroundPackage || !AdSkipStore.appEnabled(this, pkg.orEmpty()) || root.windowId != windowId) {
            recycle(root); return
        }
        val now = SystemClock.uptimeMillis()
        val candidates = AdSkipStore.rules(this).filter { rule ->
            rule.packageName == pkg && active(rule) && AdSkipMatcher.matchesActivity(rule, activity) &&
                (rule.matchWindowMs == 0L || now - enteredAt <= rule.matchWindowMs) &&
                (lastAction[rule.id]?.let { now - it >= rule.cooldownMs } ?: true)
        }
        if (candidates.isEmpty()) { recycle(root); return }
        try {
            for (rule in candidates) {
                targetedSnapshot(root, rule).use { snapshot ->
                    val target = AdSkipMatcher.findTarget(rule, snapshot.nodes)
                    if (target == null) { diagnostic(rule, AdSkipResult.NO_MATCH); return@use }
                    val clickIndex = AdSkipMatcher.clickTarget(rule, snapshot.nodes, target)
                    if (clickIndex == null) { diagnostic(rule, AdSkipResult.TARGET_NOT_CLICKABLE); return@use }
                    if (!active(rule)) return@use
                    lastAction[rule.id] = now
                    val accepted = runCatching { snapshot.native[clickIndex].performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)
                    AdSkipStore.record(this, rule, if (accepted) AdSkipResult.CLICK_ACCEPTED else AdSkipResult.CLICK_REJECTED)
                    if (accepted) { pending = rule; handler.postDelayed({ verify(rule, 0) }, 400) }
                    return
                }
            }
        } finally { recycle(root) }
    }

    private fun verify(rule: AdSkipRule, attempt: Int) {
        if (pending != rule) return
        if (!AdSkipTiming.verificationTimely(lastAction[rule.id] ?: 0, SystemClock.uptimeMillis())) {
            finish(rule, AdSkipResult.UNCONFIRMED); return
        }
        if (!active(rule)) { finish(rule, AdSkipResult.UNCONFIRMED); return }
        val root = rootInActiveWindow
        if (root == null) { finish(rule, AdSkipResult.UNCONFIRMED); return }
        val pkg = root.packageName?.toString()
        if (pkg != rule.packageName) { recycle(root); finish(rule, AdSkipResult.UNCONFIRMED); return }
        val present = try { targetedSnapshot(root, rule).use {
            val found = AdSkipMatcher.findTarget(rule, it.nodes) != null
            if (found) true else if (it.complete) false else null
        } } finally { recycle(root) }
        if (present == true && attempt < 2) { handler.postDelayed({ verify(rule, attempt + 1) }, 600); return }
        finish(rule, AdSkipVerification.result(rule, pkg, activity, present))
    }

    private fun finish(rule: AdSkipRule, result: AdSkipResult) {
        AdSkipStore.record(this, rule, result)
        pending = null
    }

    private fun diagnostic(rule: AdSkipRule, result: AdSkipResult) {
        val key = "${rule.id}:$result"
        val now = SystemClock.uptimeMillis()
        if (lastDiagnostic[key]?.let { now - it < 30000 } == true) return
        lastDiagnostic[key] = now
        AdSkipStore.record(this, rule, result)
    }

    private fun disconnect() {
        handler.removeCallbacksAndMessages(null)
        scanScheduled = false
        pending?.let { finish(it, AdSkipResult.UNCONFIRMED) }
        activity = ""
        foregroundPackage = ""
        windowId = -1
        latestEventAt = 0L
        AdSkipRuntime.setConnected(false)
    }
    override fun onInterrupt() { disconnect() }
    override fun onUnbind(intent: Intent?): Boolean { disconnect(); return super.onUnbind(intent) }
    override fun onDestroy() {
        AdSkipStore.prefs(this).unregisterOnSharedPreferenceChangeListener(preferenceListener)
        disconnect(); super.onDestroy()
    }

    private fun targetedSnapshot(root: AccessibilityNodeInfo, rule: AdSkipRule): Snapshot {
        val native = mutableListOf<AccessibilityNodeInfo>()
        val nodes = mutableListOf<AdSkipNode>()
        // Query exact resource IDs instead of a prefix of the page tree. A long bus
        // stop list must not crowd the banner out of the budget. Read no list text.
        var complete = root.isVisibleToUser && root.childCount > 0
        try {
            val targets = root.findAccessibilityNodeInfosByViewId("${rule.packageName}:id/${rule.targetId}")
            if (targets.size > 8) complete = false
            targets.drop(8).forEach(::recycle)
            for (target in targets.take(8)) {
                var current: AccessibilityNodeInfo? = target
                var previous: Int? = null
                var depth = 0
                while (current != null && depth <= 24) {
                    val node = current
                    val index = nodes.size
                    native += node
                    nodes += AdSkipNode(node.viewIdResourceName, if (depth == 0) node.text?.toString() else null, node.isVisibleToUser, node.isEnabled, node.isClickable)
                    previous?.let { nodes[it] = nodes[it].copy(parent = index) }
                    val needsAncestor = rule.ancestorId != null && node.viewIdResourceName != "${rule.packageName}:id/${rule.ancestorId}"
                    val needsParent = rule.clickParentId != null && depth == 0
                    if (!needsAncestor && !needsParent) break
                    previous = index
                    current = node.parent
                    depth++
                }
                if (depth > 24 && current != null) { recycle(current); complete = false }
            }
        } catch (_: RuntimeException) {
            complete = false
        }
        return Snapshot(nodes, native, complete)
    }
    private class Snapshot(val nodes: List<AdSkipNode>, val native: List<AccessibilityNodeInfo>, val complete: Boolean) : AutoCloseable {
        @Suppress("DEPRECATION")
        override fun close() { native.forEach { it.recycle() } }
    }
    @Suppress("DEPRECATION")
    private fun recycle(node: AccessibilityNodeInfo) { node.recycle() }

}
