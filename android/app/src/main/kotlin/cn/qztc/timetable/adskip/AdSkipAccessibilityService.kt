package cn.qztc.timetable.adskip

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import android.widget.TextView
import cn.qztc.timetable.BuildConfig
import cn.qztc.timetable.plugin.AccessibilitySession

class AdSkipAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = HandlerThread("ad-skip-scan").apply { start() }
    private val handler = Handler(worker.looper)
    private val lastAction = mutableMapOf<String, Long>()
    private val actionCounts = mutableMapOf<String, Int>()
    private val lastDiagnostic = mutableMapOf<String, Long>()
    private var activity = ""
    private var activityWindow = -1
    private var activityObservedAt = 0L
    private var foregroundPackage = ""
    private var foregroundWindow = -1
    private var enteredAt = 0L
    private var scanScheduled = false
    private var scanStep = 0
    private var lastScanAt = 0L
    private var burstStartedAt = 0L
    private var feedbackToast: Toast? = null
    private var feedbackOverlay: View? = null
    private var debugSampler: java.util.Timer? = null
    @Volatile private var mainHeartbeatAt = SystemClock.uptimeMillis()
    private var receivedEvents = 0L
    @Volatile private var probeCount = 0L
    @Volatile private var lastProbeAt = 0L
    @Volatile private var lastRootStatus = "not-read"
    private val background by lazy { AdSkipBackground(this) }
    private var pending: Attempt? = null
    private val detectionRound = AdSkipDetectionRound()
    private var lastCacheClearAt = 0L
    private val windowProbe = object : Runnable {
        override fun run() {
            probeCount++
            lastProbeAt = SystemClock.uptimeMillis()
            if (!AdSkipRuntime.connected.value || !AdSkipStore.enabled(this@AdSkipAccessibilityService) ||
                !AccessibilitySession.availableTo("adskip")) return
            if (pending == null && getSystemService(PowerManager::class.java).isInteractive) runCatching {
                val root = currentRoot()
                lastRootStatus = if (root == null) "null" else when (root.packageName?.toString()) {
                    "com.cainiao.wireless" -> "cainiao"
                    "com.sina.weibo" -> "weibo"
                    else -> "other"
                }
                if (root != null) {
                    val pkg = root.packageName?.toString().orEmpty()
                    val window = root.windowId
                    recycle(root)
                    if (pkg != foregroundPackage || window != foregroundWindow) {
                        observeForeground(pkg, window)
                        if (AdSkipStore.appEnabled(this@AdSkipAccessibilityService, pkg)) startBurst()
                    } else if (!scanScheduled && SystemClock.uptimeMillis() - lastScanAt >= 500 &&
                        AdSkipStore.appEnabled(this@AdSkipAccessibilityService, pkg)) {
                        startBurst()
                    }
                }
            }
            // Keep the first few seconds of a splash responsive. A 1.2 s
            // probe interval can miss a short-lived skip control entirely.
            handler.postDelayed(this, 300)
        }
    }
    private val packagesChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            handler.post { AdSkipApplications.labels(this@AdSkipAccessibilityService, refresh = true); updatePackageFilter() }
        }
    }
    private val scanTask = Runnable {
        scanScheduled = false
        lastScanAt = SystemClock.uptimeMillis()
        runCatching { scan() }.onFailure {
            pending?.let { attempt -> finish(attempt, AdSkipResult.UNCONFIRMED) }
            Log.w(TAG, "Window scan unavailable")
        }
        if (pending == null) {
            if (AdSkipTiming.rapidSplash(foregroundPackage, activity, enteredAt, SystemClock.uptimeMillis())) {
                scheduleScan(50)
            } else if (scanStep < RETRIES.size) scheduleScan(RETRIES[scanStep++])
        }
    }
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in setOf("enabled", "installed", "foreground", "background_retry")) background.update()
        if (key == "feedback" && !AdSkipStore.feedbackEnabled(this)) clearFeedback()
        if (key == "enabled" && !AdSkipStore.enabled(this)) clearFeedback()
        if (key == null || key in setOf("enabled", "installed", "local_pack_v1") || key.startsWith("app:") || key.startsWith("rule:")) updatePackageFilter()
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            debugSampler = java.util.Timer("ad-skip-debug", true).apply {
                scheduleAtFixedRate(object : java.util.TimerTask() {
                    override fun run() {
                        val now = SystemClock.uptimeMillis()
                        if (now - mainHeartbeatAt > 15_000) {
                            Log.w(TAG, "Main thread stalled: " + Looper.getMainLooper().thread.stackTrace.take(16).joinToString(" <- "))
                            Log.w(TAG, "Scan thread: " + worker.stackTrace.take(16).joinToString(" <- "))
                        }
                        mainHandler.post { mainHeartbeatAt = SystemClock.uptimeMillis() }
                    }
                }, 10_000, 10_000)
            }
        }
        AdSkipStore.prefs(this).registerOnSharedPreferenceChangeListener(preferenceListener)
        val filter = IntentFilter().apply { addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addDataScheme("package") }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(packagesChanged, filter, Context.RECEIVER_NOT_EXPORTED)
        else { @Suppress("DEPRECATION") registerReceiver(packagesChanged, filter) }
    }

    override fun onServiceConnected() {
        AdSkipTrace.state("connected")
        AdSkipRuntime.setConnected(true)
        AdSkipStore.migrateDiagnostics(this)
        background.update()
        AccessibilitySession.onChanged = {
            refreshPackageFilter()
        }
        updatePackageFilter()
    }

    internal fun refreshPackageFilter() {
        handler.post { updatePackageFilter() }
    }

    private fun updatePackageFilter() {
        if (Looper.myLooper() != worker.looper) { handler.post { updatePackageFilter() }; return }
        if (!AdSkipRuntime.connected.value) return
        runCatching {
            val info = serviceInfo ?: return
            // Empty packageNames means ALL apps, so retain the disabled sentinel.
            val packages = if (AccessibilitySession.availableTo("adskip")) AdSkipStore.activePackages(this) else emptyList()
            info.packageNames = packages
                .ifEmpty { listOf("cn.qztc.adskip.disabled") }.toTypedArray()
            AdSkipTrace.state("filter count=${info.packageNames?.size} cainiao=${info.packageNames?.contains("com.cainiao.wireless")} weibo=${info.packageNames?.contains("com.sina.weibo")}")
            serviceInfo = info
            cancelWork()
            foregroundPackage = ""
            foregroundWindow = -1
            activity = ""
            lastAction.clear()
            actionCounts.clear()
            lastDiagnostic.clear()
            detectionRound.reset()
            startBurst()
            if (AdSkipStore.enabled(this)) handler.postDelayed(windowProbe, 300)
        }.onFailure { Log.e(TAG, "Unable to update accessibility package filter", it) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        receivedEvents++
        if (event == null || !AdSkipStore.enabled(this) || !AccessibilitySession.availableTo("adskip") || event.eventType !in EVENT_TYPES) return
        val pkg = event.packageName?.toString()
        val eventType = event.eventType
        val windowId = event.windowId
        val eventTime = event.eventTime
        val className = event.className?.toString().orEmpty()
        // Framework recycles events after return. Copy only metadata; never retain event/source.
        handler.post { handleEvent(pkg, eventType, windowId, className, eventTime) }
    }

    private fun handleEvent(pkg: String?, eventType: Int, windowId: Int, className: String, eventTime: Long) {
        if (pkg in setOf("com.yiban.app", "com.cainiao.wireless", "com.sina.weibo") && eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
            AdSkipTrace.state("event pkg=$pkg class=$className delay=${SystemClock.uptimeMillis() - eventTime} allowed=${AdSkipStore.appEnabled(this, pkg!!)}")
        if (!AdSkipStore.enabled(this) || !AccessibilitySession.availableTo("adskip")) return
        if (pkg != null && !AdSkipStore.appEnabled(this, pkg)) return
        if (BuildConfig.DEBUG && eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            Log.d(TAG, "Window event: package=" + pkg + ", window=" + windowId)
        }
        // Delayed events only trigger a fresh root read; their old source is never clicked.
        val now = SystemClock.uptimeMillis()
        if (pkg != null && (pkg != foregroundPackage || windowId != foregroundWindow)) {
            observeForeground(pkg, windowId)
        }
        if (pkg != null && eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val name = className
            val configured = AdSkipStore.rulesFor(this, pkg).flatMap { it.activityNames + it.successActivityNames }
            if (name in configured || name.endsWith("Activity")) {
                if (activity != name) {
                    enteredAt = now
                    detectionRound.reset()
                }
                activity = name
                activityWindow = windowId
                // Queue delivery time is not event time: an old queued home event must
                // never become fresh post-click evidence merely because a scan was slow.
                activityObservedAt = eventTime
            }
        }
        if (pending != null) return
        if (!scanScheduled) startBurst()
        else if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || now - lastScanAt > 150) {
            // Bring a scan forward without allowing content storms to postpone it.
            handler.removeCallbacks(scanTask)
            scanScheduled = false
            startBurst()
        }
    }

    private fun startBurst() {
        if (!AdSkipStore.enabled(this) || !AccessibilitySession.availableTo("adskip") || pending != null || scanScheduled) return
        scanStep = 0
        burstStartedAt = SystemClock.uptimeMillis()
        scheduleScan((80L - (burstStartedAt - lastScanAt)).coerceAtLeast(0))
    }

    private fun scheduleScan(delay: Long) {
        if (scanScheduled || !AdSkipStore.enabled(this) || !AccessibilitySession.availableTo("adskip")) return
        scanScheduled = true
        handler.postDelayed(scanTask, delay)
    }

    private fun active(rule: AdSkipRule) = AdSkipRuntime.connected.value && AdSkipStore.enabled(this) && AccessibilitySession.availableTo("adskip") &&
        AdSkipStore.appEnabled(this, rule.packageName) && AdSkipStore.ruleEnabled(this, rule) &&
        AdSkipStore.rulesFor(this, rule.packageName).any { it == rule }

    private fun observeForeground(pkg: String, window: Int) {
        if (foregroundPackage != pkg || foregroundWindow != window) {
            actionCounts.clear()
            activity = ""
            activityWindow = -1
            activityObservedAt = 0L
            enteredAt = SystemClock.uptimeMillis()
            detectionRound.reset()
        }
        foregroundPackage = pkg
        foregroundWindow = window
    }

    private fun currentRoot(): AccessibilityNodeInfo? {
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) return null
        val activeRoot = runCatching { rootInActiveWindow }.getOrNull()
        val activePackage = activeRoot?.packageName?.toString()
        if (activeRoot != null && activePackage != null && !AdSkipStore.appEnabled(this, activePackage)) return activeRoot
        val currentWindows = runCatching { windows }.getOrNull().orEmpty()
        return try {
            if (activePackage != null && AdSkipStore.appEnabled(this, activePackage)) {
                val activeLayer = currentWindows.firstOrNull { it.id == activeRoot.windowId }?.layer
                if (activeLayer != null) {
                    for (window in currentWindows.filter {
                        it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.layer > activeLayer
                    }.sortedByDescending { it.layer }) {
                        val overlay = runCatching { window.root }.getOrNull() ?: continue
                        if (overlay.packageName?.toString() == activePackage && overlay.isVisibleToUser) {
                            recycle(activeRoot)
                            return overlay
                        }
                        recycle(overlay)
                    }
                }
            }
            activeRoot ?: currentWindows.firstOrNull {
                it.isActive && it.isFocused && it.type == AccessibilityWindowInfo.TYPE_APPLICATION
            }?.root
        } finally {
            @Suppress("DEPRECATION")
            currentWindows.forEach { it.recycle() }
        }
    }

    /** Avoid clearing the Android accessibility cache on every 50–150 ms retry. */
    private fun maybeClearAccessibilityCache(force: Boolean = false) {
        if (Build.VERSION.SDK_INT < 33) return
        val now = SystemClock.uptimeMillis()
        if (force || now - lastCacheClearAt >= 180L) {
            runCatching { clearCache() }
            lastCacheClearAt = now
        }
    }

    private fun scan() {
        if (!AdSkipStore.enabled(this) || !AccessibilitySession.availableTo("adskip") || pending != null) return
        // Splash controls may appear without a content event. Do not repeatedly
        // traverse a cached loading tree throughout the short ad window.
        val root = currentRoot() ?: run {
            AdSkipStore.rulesFor(this, foregroundPackage).filter { active(it) }.forEach { diagnostic(it, AdSkipResult.NO_WINDOW) }
            return
        }
        val pkg = root.packageName?.toString().orEmpty()
        // Check the package BEFORE traversing any content from the root.
        if (!AdSkipStore.appEnabled(this, pkg)) {
            // During app launch the event may precede the active-window transition.
            // Keep bounded retries, but never traverse or click this other app's tree.
            recycle(root); return
        }
        maybeClearAccessibilityCache(force = scanStep == 0)
        if (!root.refresh()) { recycle(root); return }
        val now = SystemClock.uptimeMillis()
        if (foregroundPackage != pkg || foregroundWindow != root.windowId) {
            observeForeground(pkg, root.windowId)
        }
        val candidates = AdSkipStore.rulesFor(this, pkg).filter { rule ->
            rule.packageName == pkg && active(rule) && AdSkipMatcher.matchesActivity(rule, activity) &&
                (rule.id != AdSkipGenericRules.ID || !AdSkipStore.splashOnly(this) || now - enteredAt <= 15000L) &&
                (rule.matchWindowMs == 0L || now - enteredAt <= rule.matchWindowMs) &&
                (lastAction[rule.runtimeKey()]?.let { now - it >= rule.cooldownMs } ?: true)
                && (rule.actionMaximum == 0 || (actionCounts[rule.runtimeKey()] ?: 0) < rule.actionMaximum)
        }
        if (BuildConfig.DEBUG && scanStep == 0) Log.d(TAG, "Scan: package=" + pkg + ", candidates=" + candidates.size)
        if (candidates.isEmpty()) {
            // No active rule is not absence evidence: the scene may be in a
            // cooldown or outside its activity scope. Only a complete tree
            // with active candidates can re-arm a detection round.
            recycle(root)
            return
        }
        // Qingtiao's planner checks explicit resource IDs before broad text
        // lookup. Native IDs are both faster and less likely to match an
        // ordinary in-app button with a similar label.
        for (rule in candidates) {
            if (rule.id == AdSkipGenericRules.ID || rule.targetId == null) continue
            val found = runCatching { directRuleTarget(root, rule) }.getOrNull() ?: continue
            found.use { tree ->
                AdSkipMatcher.findTarget(rule, tree.nodes)?.let { target ->
                    detectionRound.presence(true, now)
                    if (tryBeginAttempt(rule, tree, target, now)) { recycle(root); return }
                }
            }
        }
        // Query explicit skip labels only after known IDs. This sparse lookup
        // can find a target, but MUST NEVER prove target absence.
        val generic = candidates.firstOrNull { it.id == AdSkipGenericRules.ID }
        if (generic != null && (activity.endsWith("SplashActivity") ||
                (pkg == "com.zmzx.college.search" && activity.endsWith("InitActivity")))) {
            @Suppress("DEPRECATION")
            val splashRoot = AccessibilityNodeInfo.obtain(root)
            snapshot(splashRoot, budgetMs = 100).use { tree ->
                if (tree.complete) {
                    AdSkipDrawnSplash.findTarget(generic, tree.nodes)?.let { target ->
                        detectionRound.presence(true, now)
                        if (tryBeginAttempt(generic, tree, target, now)) { recycle(root); return }
                    }
                }
            }
        }
        if (generic != null) {
            val found = runCatching { directTarget(root, generic) }.getOrNull()
            if (found != null) {
                found.use { tree ->
                    AdSkipMatcher.findTarget(generic, tree.nodes)?.let { target ->
                        detectionRound.presence(true, now)
                        if (tryBeginAttempt(generic, tree, target, now)) { recycle(root); return }
                    }
                }
            }
        }
        // Structural popup rules need the complete sibling context in large feeds.
        snapshot(root, budgetMs = if (pkg == "tv.danmaku.bili" || candidates.any { it.id != AdSkipGenericRules.ID }) 600 else 180).use { tree ->
            if (scanStep == 0) AdSkipTrace.tree(pkg, activity, tree.nodes, tree.complete, candidates.size, tree.elapsedMs)
            var observedTarget = false
            for (rule in candidates) {
                if (activity.isEmpty() && rule.allowUnknownActivity && !tree.complete) continue
                val target = AdSkipMatcher.findTarget(rule, tree.nodes)
                if (target == null) { diagnostic(rule, if (tree.complete) AdSkipResult.NO_MATCH else AdSkipResult.INCOMPLETE_TREE); continue }
                observedTarget = true
                detectionRound.presence(true, now)
                if (tryBeginAttempt(rule, tree, target, now)) return
            }
            if (!observedTarget) detectionRound.presence(false, now, tree.complete)
        }
    }

    /** Select and mark one unique target before dispatching any action. */
    private fun tryBeginAttempt(rule: AdSkipRule, tree: Snapshot, target: Int, now: Long): Boolean {
        if (!active(rule) || detectionRound.finished()) return false
        val node = tree.nodes.getOrNull(target) ?: return false
        if (!AdSkipSafety.onDisplay(node.bounds, displayBounds())) {
            AdSkipTrace.verification(rule, "blocked=display bounds=${node.bounds} display=${displayBounds()}"); return false
        }
        if (!AdSkipSafety.allowsTarget(tree.nodes, target)) {
            AdSkipTrace.verification(rule, "blocked=consent"); return false
        }
        if (node.bounds?.let { systemWindowCovers(it, tree.windowId) } != false) {
            AdSkipTrace.verification(rule, "blocked=system-window"); return false
        }
        if (rule.id in setOf(AdSkipGenericRules.ID, AdSkipDrawnSplash.NATIVE_ID) && node.id.isNullOrBlank() && node.text.isNullOrBlank() &&
            node.description.isNullOrBlank() && !tree.complete) return false
        val key = buildCandidateKey(rule, node)
        if (!detectionRound.allows(key, now)) return false
        detectionRound.attempted(key, now)
        beginAttempt(rule, tree, target)
        return true
    }

    private fun buildCandidateKey(rule: AdSkipRule, node: AdSkipNode): String {
        val label = listOfNotNull(node.text, node.description).firstOrNull { it.isNotBlank() }.orEmpty()
            .trim().lowercase().replace(Regex("\\s+"), " ")
        val bounds = node.bounds
        val position = if (node.id == null && bounds != null) {
            ":${bounds.left / 48}:${bounds.top / 48}:${bounds.right / 48}:${bounds.bottom / 48}"
        } else ""
        return rule.runtimeKey() + "|node=" + (node.id ?: "") + "|label=" + label + position
    }

    private fun beginAttempt(rule: AdSkipRule, tree: Snapshot, target: Int) {
        if (!active(rule)) return
        AdSkipTrace.matched(rule, tree.nodes[target], tree.windowId)
        val attempt = Attempt(rule, SystemClock.uptimeMillis(), tree.windowId, tree.nodes[target])
        attempt.sportBanner = AdSkipDrawnSplash.isSportBannerTarget(tree.nodes, target)
        pending = attempt
        lastAction[rule.runtimeKey()] = attempt.startedAt
        handler.removeCallbacks(scanTask)
        scanScheduled = false
        if (rule.action == "clickCenter" || attempt.sportBanner) {
            if (!gesture(attempt)) finish(attempt, AdSkipResult.CLICK_REJECTED)
            return
        }
        val accepted = when (rule.action) {
            "none" -> true
            "back" -> runCatching { performGlobalAction(GLOBAL_ACTION_BACK) }.getOrDefault(false)
            else -> AdSkipMatcher.clickCandidates(rule, tree.nodes, target).any { i ->
                runCatching { tree.native[i].performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)
            }
        }
        if (accepted) {
            attempt.counted = true
            actionCounts[rule.runtimeKey()] = (actionCounts[rule.runtimeKey()] ?: 0) + 1
            AdSkipTrace.action(rule, "accessibility_click_accepted")
            AdSkipStore.record(this, rule, AdSkipResult.CLICK_ACCEPTED)
            verifyLater(attempt, 300)
        } else if (rule.action != "click" || !gesture(attempt)) finish(attempt, AdSkipResult.CLICK_REJECTED)
    }

    /** Only explicit ad labels/countdowns/IDs qualify without a complete context tree. */
    private fun textTarget(root: AccessibilityNodeInfo, rule: AdSkipRule): Snapshot? {
        val started = SystemClock.uptimeMillis()
        for (label in listOf("跳过", "skip")) {
            if (SystemClock.uptimeMillis() - started > 180) break
            val matches = root.findAccessibilityNodeInfosByText(label)
            var selected: Snapshot? = null
            try {
                for (node in matches.take(24)) {
                    if (node.windowId != root.windowId) continue
                    val tree = ancestorSnapshot(root, node)
                    if (AdSkipMatcher.findTarget(rule, tree.nodes) == null) { tree.close(); continue }
                    selected = tree
                    AdSkipTrace.verification(rule, "lookup=text scanMs=${selected.elapsedMs}")
                    break
                }
            } finally { matches.forEach(::recycle) }
            if (selected != null) return selected
        }
        return null
    }

    /** Native ID lookup plus the real parent chain avoids walking an entire feed. */
    private fun directTarget(root: AccessibilityNodeInfo, rule: AdSkipRule): Snapshot? {
        if (rule.packageName == "tv.danmaku.bili") {
            val pauseClose = root.findAccessibilityNodeInfosByText("关闭暂停页")
            try {
                if (pauseClose.any { it.isVisibleToUser && it.contentDescription?.toString() == "关闭暂停页" }) {
                    @Suppress("DEPRECATION")
                    val tree = snapshot(AccessibilityNodeInfo.obtain(root), budgetMs = 600)
                    if (tree.complete && AdSkipMatcher.findTarget(rule, tree.nodes) != null) return tree
                    tree.close()
                }
            } finally { pauseClose.forEach(::recycle) }
        }
        for (id in AdSkipGenericRules.lookupIdsFor(rule.packageName)) {
            val matches = root.findAccessibilityNodeInfosByViewId("${rule.packageName}:id/$id")
            try {
                for (node in matches.take(8)) {
                    if (node.windowId != root.windowId || !node.isVisibleToUser) continue
                    // The Tobid interstitial SDK places the skip control in a
                    // sibling overlay while the ad marker lives in
                    // `tobid_interstitial_ad_detail`. An ancestor-only
                    // snapshot cannot prove that relationship, so use one
                    // bounded window snapshot for this explicit SDK id.
                    if (id == "tobid_interstitial_skip_text") {
                        // snapshot owns and recycles its root handle. Keep it
                        // separate from the caller-owned root, which is
                        // recycled below after directTarget returns.
                        @Suppress("DEPRECATION")
                        val windowRoot = AccessibilityNodeInfo.obtain(root)
                        snapshot(windowRoot, budgetMs = 600).let { tree ->
                            if (AdSkipMatcher.findTarget(rule, tree.nodes) != null) return tree
                            tree.close()
                        }
                    }
                    val tree = ancestorSnapshot(root, node)
                    if (AdSkipMatcher.findTarget(rule, tree.nodes) != null) return tree
                    tree.close()
                }
            } finally { matches.forEach(::recycle) }
        }
        return textTarget(root, rule)
    }

    /** Bounded native lookup for a rule with an explicit target resource ID. */
    private fun directRuleTarget(root: AccessibilityNodeInfo, rule: AdSkipRule): Snapshot? {
        val id = rule.targetId ?: return null
        val matches = root.findAccessibilityNodeInfosByViewId("${rule.packageName}:id/$id")
        try {
            for (node in matches.take(8)) {
                if (node.windowId != root.windowId || !node.isVisibleToUser || !node.isEnabled) continue
                val tree = ancestorSnapshot(root, node)
                if (AdSkipMatcher.findTarget(rule, tree.nodes) != null) return tree
                tree.close()
            }
        } finally { matches.forEach(::recycle) }
        return null
    }

    private fun ancestorSnapshot(root: AccessibilityNodeInfo, target: AccessibilityNodeInfo): Snapshot {
        val started = SystemClock.uptimeMillis()
        @Suppress("DEPRECATION")
        val native = mutableListOf(AccessibilityNodeInfo.obtain(target))
        try {
            for (depth in 0 until 12) {
                val parent = native.last().parent ?: break
                native += parent
            }
            native.reverse()
            val ancestors = native.mapIndexed { i, node -> describe(node, if (i == 0) null else i) }
            // The first ancestor has no invented relationship to the screen node.
            @Suppress("DEPRECATION")
            native.add(0, AccessibilityNodeInfo.obtain(root))
            val nodes = listOf(describe(root, null)) + ancestors
            return Snapshot(nodes, native, false, root.windowId, SystemClock.uptimeMillis() - started)
        } catch (error: RuntimeException) {
            native.forEach(::recycle)
            throw error
        }
    }

    private fun describe(node: AccessibilityNodeInfo, parent: Int?, index: Int? = null): AdSkipNode {
        val rect = Rect().also(node::getBoundsInScreen)
        return AdSkipNode(node.viewIdResourceName, node.text?.toString()?.take(160), node.isVisibleToUser, node.isEnabled, node.isClickable, parent,
            node.contentDescription?.toString()?.take(160), node.className?.toString(), AdSkipBounds(rect.left, rect.top, rect.right, rect.bottom), node.packageName?.toString(), index, node.childCount)
    }

    private fun gesture(attempt: Attempt): Boolean {
        if (pending !== attempt || !active(attempt.rule) || !attempt.rule.allowGesture ||
            attempt.rule.action !in setOf("click", "clickCenter") || attempt.gestureAttempts >= 3) return false
        attempt.gestureTried = true
        val root = currentRoot() ?: return false
        if (root.packageName?.toString() != attempt.rule.packageName || root.windowId != attempt.windowId) { recycle(root); return false }
        // ACTION_CLICK may have changed the screen. Match again, never reuse old coordinates.
        val labelled = attempt.target.id != null || AdSkipRules.isSkipLabel(attempt.target.text) ||
            AdSkipRules.isSkipLabel(attempt.target.description)
        val fresh = if (attempt.rule.id == AdSkipGenericRules.ID && labelled) directTarget(root, attempt.rule) else null
        val tree = if (fresh != null) { recycle(root); fresh } else snapshot(root, budgetMs = 600)
        val bounds = tree.use { tree ->
            val targetIndex = AdSkipMatcher.findTarget(attempt.rule, tree.nodes) ?: return false
            val target = tree.nodes[targetIndex]
            if (!AdSkipSafety.allowsTarget(tree.nodes, targetIndex)) return false
            if (!sameTarget(attempt.target, target) && !relocatedAdClose(attempt, tree.nodes, targetIndex)) return false
            val b = target.bounds ?: return false
            if (!AdSkipSafety.onDisplay(b, displayBounds())) return false
            if (systemWindowCovers(b, tree.windowId)) return false
            val screen = tree.nodes.firstOrNull()?.bounds ?: return false
            if (!b.containedBy(screen)) return false
            if (b.width > screen.width / 2 || b.height > screen.height / 3) return false
            b
        }
        val path = Path().apply { moveTo((bounds.left + bounds.right) / 2f, (bounds.top + bounds.bottom) / 2f) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 100)).build()
        val sequence = ++attempt.gestureAttempts
        attempt.gestureCallbackReceived = false
        attempt.lastGestureAt = SystemClock.uptimeMillis()
        AdSkipStore.record(this, attempt.rule, AdSkipResult.GESTURE_SENT)
        val submitted = runCatching { dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (pending !== attempt) return
                attempt.gestureCallbackReceived = true
                if (!attempt.counted) {
                    actionCounts[attempt.rule.runtimeKey()] = (actionCounts[attempt.rule.runtimeKey()] ?: 0) + 1
                    attempt.counted = true
                }
                AdSkipTrace.action(attempt.rule, "gesture_completed")
                AdSkipStore.record(this@AdSkipAccessibilityService, attempt.rule, AdSkipResult.GESTURE_COMPLETED)
                verifyLater(attempt, 100)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) { finish(attempt, AdSkipResult.GESTURE_CANCELLED) }
        }, handler) }.getOrDefault(false)
        if (!submitted) finish(attempt, AdSkipResult.GESTURE_CANCELLED)
        // A vendor missing the callback must not leave the engine stuck forever.
        if (submitted) handler.postDelayed({
            if (pending === attempt && attempt.gestureAttempts == sequence && !attempt.gestureCallbackReceived)
                finish(attempt, AdSkipResult.UNCONFIRMED)
        }, 2400)
        return submitted
    }

    private fun displayBounds(): AdSkipBounds {
        val manager = getSystemService(WindowManager::class.java)
        val rect = if (Build.VERSION.SDK_INT >= 30) manager.maximumWindowMetrics.bounds else {
            @Suppress("DEPRECATION")
            val metrics = android.util.DisplayMetrics().also { manager.defaultDisplay.getRealMetrics(it) }
            Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
        return AdSkipBounds(rect.left, rect.top, rect.right, rect.bottom)
    }

    private fun systemWindowCovers(target: AdSkipBounds, appWindow: Int): Boolean {
        val visibleWindows = runCatching { windows }.getOrNull() ?: return true
        return try {
            val appLayer = visibleWindows.firstOrNull { it.id == appWindow }?.layer ?: return true
            val blockers = visibleWindows.filter { it.layer > appLayer && it.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY }
                .mapNotNull { window ->
                    val root = runCatching { window.root }.getOrNull()
                    val system = window.type == AccessibilityWindowInfo.TYPE_SYSTEM || AdSkipSafety.systemPackage(root?.packageName?.toString())
                    root?.let(::recycle)
                    if (!system) null else Rect().also(window::getBoundsInScreen).let {
                        val box = AdSkipBounds(it.left, it.top, it.right, it.bottom)
                        if (!AdSkipSafety.unobscured(target, listOf(box))) AdSkipTrace.state("blocker type=${window.type} title=${window.title} bounds=$box")
                        box
                    }
                }
            !AdSkipSafety.unobscured(target, blockers)
        } finally {
            @Suppress("DEPRECATION")
            visibleWindows.forEach { it.recycle() }
        }
    }

    private fun verify(attempt: Attempt) {
        if (pending !== attempt) return
        // Some SDKs remove a close control only after an animation/network
        // callback. Keep the action bound to this attempt for a short,
        // bounded confirmation window instead of declaring failure while the
        // same ad is visibly disappearing.
        if (!active(attempt.rule) || !AdSkipTiming.verificationTimely(attempt.startedAt, SystemClock.uptimeMillis(), 5000)) {
            finish(attempt, AdSkipResult.UNCONFIRMED); return
        }
        maybeClearAccessibilityCache(force = scanStep == 0)
        val root = currentRoot()
        if (root == null) {
            attempt.absence.reset(); attempt.homeChecks = 0
            AdSkipTrace.verification(attempt.rule, "root=null"); verifyLater(attempt); return
        }
        val pkg = root.packageName?.toString()
        if (pkg != attempt.rule.packageName) { AdSkipTrace.verification(attempt.rule, "foreground-changed"); recycle(root); finish(attempt, AdSkipResult.UNCONFIRMED); return }
        if (attempt.sportBanner) {
            snapshot(root, budgetMs = 600).use { tree ->
                val present = AdSkipDrawnSplash.sportBannerPresent(tree.nodes)
                if (attempt.absence.observe(tree.windowId, tree.complete, present)) {
                    finish(attempt, AdSkipResult.TARGET_GONE)
                } else if (present) {
                    // A carousel can hide the X without dismissing its advertising item.
                    val target = if (tree.complete) AdSkipMatcher.findTarget(attempt.rule, tree.nodes) else null
                    if (target != null && sameTarget(attempt.target, tree.nodes[target]) &&
                        SystemClock.uptimeMillis() - attempt.lastGestureAt >= 150 && gesture(attempt)) return
                    if (++attempt.presentChecks >= 8) finish(attempt, AdSkipResult.TARGET_PRESENT)
                    else verifyLater(attempt, 150)
                } else verifyLater(attempt, 150)
            }
            return
        }
        if (attempt.rule.id == "mybus-native-feed-close") {
            @Suppress("DEPRECATION")
            snapshot(AccessibilityNodeInfo.obtain(root), budgetMs = 180).use { menu ->
                val dismiss = if (menu.complete) AdSkipDrawnSplash.findMyBusFeedbackTarget(menu.nodes) else null
                if (dismiss != null) {
                    val accepted = runCatching { menu.native[dismiss].performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)
                    AdSkipTrace.action(attempt.rule, "feedback_dismiss accepted=$accepted")
                    recycle(root)
                    attempt.absence.reset()
                    if (accepted) verifyLater(attempt, 150) else finish(attempt, AdSkipResult.CLICK_REJECTED)
                    return
                }
            }
        }
        // A fresh exact home activity and matching active window is independent evidence,
        // even when a large home feed cannot be traversed within the tree budget.
        if (AdSkipVerification.confirmedHome(attempt.rule, pkg, activity, attempt.windowId, root.windowId,
                activityWindow, activityObservedAt, attempt.startedAt, SystemClock.uptimeMillis())) {
            recycle(root)
            if (++attempt.homeChecks >= 2) finish(attempt, AdSkipResult.NORMAL_PAGE) else verifyLater(attempt, 120)
            return
        }
        attempt.homeChecks = 0
        // Platform ID/text queries return the complete result for that predicate.
        // A valid fresh root and two consistent queries avoid requiring traversal
        // of an entire feed just to verify that one close button disappeared.
        // A dismissed dialog can reveal a second ad reusing the same resource
        // ID. Verify the original window before querying the newly visible one.
        val originalWindowGone = if (root.windowId != attempt.windowId) {
            val liveWindows = runCatching { windows }.getOrNull()
            try {
                liveWindows?.takeIf { list -> list.any { it.id == root.windowId } }
                    ?.none { it.id == attempt.windowId }
            } finally {
                @Suppress("DEPRECATION")
                liveWindows?.forEach { it.recycle() }
            }
        } else false
        val directPresent = when (originalWindowGone) {
            true -> false
            null -> null
            false -> if (root.windowId == attempt.windowId) queryOriginalTarget(root, attempt.target) else null
        }
        if (root.windowId != attempt.windowId && originalWindowGone != true) {
            recycle(root); attempt.absence.reset(); verifyLater(attempt); return
        }
        if (directPresent != null) {
            val window = root.windowId
            recycle(root)
            AdSkipTrace.verification(attempt.rule, "lookup=original present=$directPresent window=$window")
            if (attempt.absence.observe(window, true, directPresent)) {
                finish(attempt, AdSkipResult.TARGET_GONE)
            } else if (directPresent) {
                if (SystemClock.uptimeMillis() - attempt.lastGestureAt >= 150 && gesture(attempt)) return
                if (++attempt.presentChecks >= 8) finish(attempt, AdSkipResult.TARGET_PRESENT) else verifyLater(attempt)
            } else verifyLater(attempt, 150)
            return
        }
        snapshot(root, budgetMs = 600).use { tree ->
            if (!AdSkipTiming.verificationTimely(attempt.startedAt, SystemClock.uptimeMillis(), 5000)) {
                finish(attempt, AdSkipResult.UNCONFIRMED); return
            }
            val target = if (attempt.rule.id == AdSkipGenericRules.ID)
                AdSkipMatcher.findTarget(attempt.rule, tree.nodes)?.takeIf { i ->
                    sameTarget(attempt.target, tree.nodes[i]) || relocatedAdClose(attempt, tree.nodes, i)
                }
            else tree.nodes.indices.firstOrNull { tree.nodes[it].visible && sameControl(attempt.target, tree.nodes[it]) }
            AdSkipTrace.verification(attempt.rule, "window=${tree.windowId} count=${tree.nodes.size} complete=${tree.complete} target=${target != null} scanMs=${tree.elapsedMs} activity=$activity elapsed=${SystemClock.uptimeMillis() - attempt.startedAt}")
            val confirmedAbsent = attempt.absence.observe(tree.windowId, tree.complete, target != null)
            if (target != null) {
                if (SystemClock.uptimeMillis() - attempt.lastGestureAt >= 150 && (sameTarget(attempt.target, tree.nodes[target]) ||
                    relocatedAdClose(attempt, tree.nodes, target)) && gesture(attempt)) return
                if (++attempt.presentChecks < 8) { verifyLater(attempt); return }
                finish(attempt, AdSkipResult.TARGET_PRESENT)
            } else if (!confirmedAbsent) verifyLater(attempt)
            else {
                // Repeated absence is evidence, not proof of ad dismissal or causality.
                finish(attempt, AdSkipVerification.result(attempt.rule, pkg, activity, false))
            }
        }
    }

    private fun verifyLater(attempt: Attempt, delay: Long = 250) {
        handler.postDelayed({ runCatching { verify(attempt) }.onFailure { finish(attempt, AdSkipResult.UNCONFIRMED) } }, delay)
    }

    private fun sameTarget(before: AdSkipNode, after: AdSkipNode) = before.id == after.id &&
        before.className == after.className && before.bounds == after.bounds && before.packageName == after.packageName

    private fun relocatedAdClose(attempt: Attempt, nodes: List<AdSkipNode>, index: Int): Boolean {
        if (attempt.rule.id != AdSkipGenericRules.ID) return false
        // A unique fresh match keeps a moving close control tied to an ad container.
        return AdSkipGenericRules.relocatedTarget(attempt.rule, attempt.target, nodes, index)
    }

    private fun sameControl(before: AdSkipNode, after: AdSkipNode): Boolean = before.packageName == after.packageName &&
        before.className == after.className && (if (before.id != null) before.id == after.id else
        ((AdSkipRules.isSkipLabel(before.text) || AdSkipRules.isSkipLabel(before.description)) &&
            (AdSkipRules.isSkipLabel(after.text) || AdSkipRules.isSkipLabel(after.description))) || sameTarget(before, after))

    private fun queryOriginalTarget(root: AccessibilityNodeInfo, target: AdSkipNode): Boolean? = runCatching {
        if (!root.refresh() || !root.isVisibleToUser || root.childCount == 0 || root.packageName?.toString() != target.packageName) return null
        // This web control can split its text into children during rendering.
        // Verify the complete relationship instead of treating renamed text as absence.
        if (target.id == "countdown-root") return null
        val id = target.id?.takeIf { it.startsWith("${target.packageName}:id/") }
        val explicit = listOfNotNull(target.text, target.description).firstOrNull(AdSkipRules::isSkipLabel)
        val text = when {
            target.packageName == "tv.danmaku.bili" && target.description == "关闭暂停页" -> "关闭暂停页"
            explicit == null -> null
            explicit.contains("跳过") -> "跳过"
            explicit.contains("skip", true) -> "skip"
            explicit.contains("关闭广告") -> "关闭广告"
            else -> "close"
        }
        if (id == null && text == null) return null
        val matches = if (id != null) root.findAccessibilityNodeInfosByViewId(id) else root.findAccessibilityNodeInfosByText(text!!)
        try { matches.any { it.windowId == root.windowId && it.isVisibleToUser && sameControl(target, describe(it, null)) } }
        finally { matches.forEach(::recycle) }
    }.getOrNull()

    private fun finish(attempt: Attempt, result: AdSkipResult) {
        if (pending !== attempt) return
        pending = null
        AdSkipTrace.verification(attempt.rule, "result=${result.name} elapsed=${SystemClock.uptimeMillis() - attempt.startedAt}")
        AdSkipStore.record(this, attempt.rule, result)
        AdSkipStore.recordOutcome(this, attempt.rule, result)
        // Only confirmed dismissals get user-facing feedback. Failed or
        // unconfirmed probes stay in the in-app log without interrupting use.
        if (AdSkipOutcome.label(result) == "成功") {
            lastAction.remove(attempt.rule.runtimeKey())
            detectionRound.reset()
            announce(attempt.rule, "跳过成功")
        }
        startBurst()
    }

    private fun announce(rule: AdSkipRule, message: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) { mainHandler.post { announce(rule, message) }; return }
        if (!AdSkipStore.enabled(this) || !AdSkipStore.feedbackEnabled(this)) return
        val app = (AdSkipRules.appNames[rule.packageName] ?: rule.appLabel ?: "广告辅助").take(10)
        // The confirmation belongs to this course-table service, so keep its
        // own app icon while naming the target application in the text.
        val icon = runCatching { applicationInfo.loadIcon(packageManager) }.getOrNull()
        val template = AdSkipStore.feedbackTemplate(this)
        val feedbackText = AdSkipFeedbackTemplate.plain(template, app)
        // Replace the in-progress message so throttling cannot swallow the final result.
        feedbackToast?.cancel()
        removeFeedbackOverlay()
        AdSkipTrace.state("feedback shown pkg=${rule.packageName} result=$message")
        // A text Toast issued by a background accessibility service is hidden by
        // some vendor builds. Prefer an accessibility overlay at the bottom;
        // retain Toast only as a fallback when the vendor rejects the overlay.
        val density = resources.displayMetrics.density
        val label = feedbackLabel(this).apply { renderFeedback(template, app) }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (64 * density).toInt()
        }
        runCatching {
            getSystemService(WindowManager::class.java).addView(label, params)
            feedbackOverlay = label
            mainHandler.postDelayed({ removeFeedbackOverlay() }, 2800)
        }.onFailure {
            // Window transitions can interrupt the accessibility service after
            // a successful click; keep a platform fallback for older builds.
            feedbackToast = Toast.makeText(this, feedbackText, Toast.LENGTH_LONG).also { toast ->
                icon?.let { toast.view?.findViewById<android.widget.ImageView>(android.R.id.icon)?.setImageDrawable(it) }
                toast.show()
            }
        }
    }

    private fun diagnostic(rule: AdSkipRule, result: AdSkipResult) {
        val key = rule.id + ":" + result.name
        val now = SystemClock.uptimeMillis()
        if (lastDiagnostic[key]?.let { now - it < 30000 } == true) return
        lastDiagnostic[key] = now
        AdSkipStore.record(this, rule, result)
    }

    private fun cancelWork() {
        val previous = pending
        pending = null
        handler.removeCallbacksAndMessages(null)
        scanScheduled = false
        previous?.let {
            AdSkipStore.record(this, it.rule, AdSkipResult.UNCONFIRMED)
            AdSkipStore.recordOutcome(this, it.rule, AdSkipResult.UNCONFIRMED)
        }
    }

    private fun clearFeedback() {
        mainHandler.post { feedbackToast?.cancel(); feedbackToast = null; removeFeedbackOverlay() }
    }

    private fun removeFeedbackOverlay() {
        val view = feedbackOverlay ?: return
        feedbackOverlay = null
        runCatching { getSystemService(WindowManager::class.java).removeView(view) }
    }

    override fun onInterrupt() { handler.post { cancelWork(); activity = ""; foregroundPackage = ""; foregroundWindow = -1; if (AdSkipStore.enabled(this)) handler.postDelayed(windowProbe, 300) } }
    override fun onUnbind(intent: Intent?): Boolean {
        AccessibilitySession.onChanged = null
        handler.post { cancelWork() }
        background.stop()
        clearFeedback()
        AdSkipRuntime.setConnected(false)
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
        debugSampler?.cancel()
        AccessibilitySession.onChanged = null
        AdSkipStore.prefs(this).unregisterOnSharedPreferenceChangeListener(preferenceListener)
        unregisterReceiver(packagesChanged)
        handler.post { cancelWork() }
        // Quit outside the queue: an earlier cancelWork may remove queued callbacks.
        worker.quitSafely()
        background.stop()
        clearFeedback()
        AdSkipRuntime.setConnected(false)
        super.onDestroy()
    }

    override fun dump(fd: java.io.FileDescriptor, writer: java.io.PrintWriter, args: Array<out String>) {
        if (!BuildConfig.DEBUG) return
        writer.println("AdSkipDebug connected=${AdSkipRuntime.connected.value} enabled=${AdSkipStore.enabled(this)} events=$receivedEvents")
        // Accessibility query results are delivered through the main thread.
        // Waiting for the worker here delays those results and perturbs diagnostics.
        writer.println("probes=$probeCount probeAge=${SystemClock.uptimeMillis() - lastProbeAt} root=$lastRootStatus")
        worker.stackTrace.take(12).forEach { writer.println("  $it") }
        AdSkipTrace.dump().forEach(writer::println)
    }

    /** Bounded tree traversal: an incomplete scan can match, but cannot prove absence. */
    private fun snapshot(root: AccessibilityNodeInfo, budgetMs: Long = 180): Snapshot {
        val native = mutableListOf<AccessibilityNodeInfo>()
        val nodes = mutableListOf<AdSkipNode>()
        data class Pending(val node: AccessibilityNodeInfo, val parent: Int?, val childIndex: Int?)
        val queue = java.util.ArrayDeque<Pending>()
        val windowId = root.windowId
        queue.add(Pending(root, null, null))
        val started = SystemClock.uptimeMillis()
        var complete = true
        try {
            traversal@ while (queue.isNotEmpty()) {
                if (nodes.size >= 2000 || SystemClock.uptimeMillis() - started > budgetMs) { complete = false; break }
                val current = queue.removeFirst()
                val node = current.node
                val parent = current.parent
                val index = nodes.size
                native += node
                nodes += describe(node, parent, current.childIndex)
                val count = node.childCount
                for (i in 0 until count) {
                    if (queue.size + native.size >= 2000) { complete = false; break }
                    // A single wide container must not perform thousands of Binder reads
                    // after exhausting the scan budget. In-flight platform calls cannot
                    // be cancelled, but no additional child read is started afterwards.
                    if (SystemClock.uptimeMillis() - started > budgetMs) { complete = false; break@traversal }
                    val child = node.getChild(i)
                    if (child != null) queue.add(Pending(child, index, i)) else complete = false
                }
            }
        } catch (_: RuntimeException) { complete = false }
        finally { queue.forEach { recycle(it.node) } }
        return Snapshot(nodes, native, complete && nodes.size > 1 && nodes.first().visible, windowId, SystemClock.uptimeMillis() - started)
    }

    private class Snapshot(val nodes: List<AdSkipNode>, val native: List<AccessibilityNodeInfo>, val complete: Boolean, val windowId: Int, val elapsedMs: Long) : AutoCloseable {
        @Suppress("DEPRECATION")
        override fun close() { native.forEach { it.recycle() } }
    }
    private class Attempt(val rule: AdSkipRule, val startedAt: Long, val windowId: Int, val target: AdSkipNode) {
        var sportBanner = false
        var counted = false
        var homeChecks = 0
        var gestureTried = false
        var gestureAttempts = 0
        var lastGestureAt = -10_000L
        var gestureCallbackReceived = false
        val absence = AdSkipAbsenceEvidence()
        var presentChecks = 0
    }
    @Suppress("DEPRECATION")
    private fun recycle(node: AccessibilityNodeInfo) { node.recycle() }
    private companion object {
        const val TAG = "AdSkipAccessibility"
        val RETRIES = longArrayOf(50, 100, 150, 250, 400, 700, 1200)
        val EVENT_TYPES = setOf(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, AccessibilityEvent.TYPE_WINDOWS_CHANGED)
    }
}
