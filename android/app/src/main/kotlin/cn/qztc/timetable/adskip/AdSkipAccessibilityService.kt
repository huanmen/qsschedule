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
import android.widget.Toast
import cn.qztc.timetable.BuildConfig

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
    private var receivedEvents = 0L
    private var probeCount = 0L
    private var lastProbeAt = 0L
    private var lastRootStatus = "not-read"
    private val background by lazy { AdSkipBackground(this) }
    private var pending: Attempt? = null
    private val windowProbe = object : Runnable {
        override fun run() {
            probeCount++
            lastProbeAt = SystemClock.uptimeMillis()
            if (!AdSkipRuntime.connected.value || !AdSkipStore.enabled(this@AdSkipAccessibilityService)) return
            if (pending == null && getSystemService(PowerManager::class.java).isInteractive) runCatching {
                if (Build.VERSION.SDK_INT >= 33) clearCache()
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
                        if (pkg != foregroundPackage) actionCounts.clear()
                        foregroundPackage = pkg; foregroundWindow = window; activity = ""; enteredAt = SystemClock.uptimeMillis()
                        if (AdSkipStore.appEnabled(this@AdSkipAccessibilityService, pkg)) startBurst()
                    }
                }
            }
            handler.postDelayed(this, 1200)
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
        if (pending == null && scanStep < RETRIES.size) scheduleScan(RETRIES[scanStep++])
    }
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in setOf("enabled", "installed", "foreground", "background_retry")) background.update()
        if (key == "feedback" && !AdSkipStore.feedbackEnabled(this)) feedbackToast?.cancel()
        if (key == null || key in setOf("enabled", "installed", "local_pack_v1") || key.startsWith("app:") || key.startsWith("rule:")) updatePackageFilter()
    }

    override fun onCreate() {
        super.onCreate()
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
        updatePackageFilter()
    }

    private fun updatePackageFilter() {
        if (Looper.myLooper() != worker.looper) { handler.post { updatePackageFilter() }; return }
        if (!AdSkipRuntime.connected.value) return
        runCatching {
            val info = serviceInfo ?: return
            // Empty packageNames means ALL apps, so retain the disabled sentinel.
            info.packageNames = AdSkipStore.activePackages(this).ifEmpty { listOf("cn.qztc.adskip.disabled") }.toTypedArray()
            AdSkipTrace.state("filter count=${info.packageNames?.size} cainiao=${info.packageNames?.contains("com.cainiao.wireless")} weibo=${info.packageNames?.contains("com.sina.weibo")}")
            serviceInfo = info
            cancelWork()
            foregroundPackage = ""
            foregroundWindow = -1
            activity = ""
            lastAction.clear()
            actionCounts.clear()
            lastDiagnostic.clear()
            startBurst()
            if (AdSkipStore.enabled(this)) handler.postDelayed(windowProbe, 1200)
        }.onFailure { Log.e(TAG, "Unable to update accessibility package filter", it) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        receivedEvents++
        if (event == null || !AdSkipStore.enabled(this) || event.eventType !in EVENT_TYPES) return
        val pkg = event.packageName?.toString()
        val eventType = event.eventType
        val windowId = event.windowId
        val eventTime = event.eventTime
        val className = event.className?.toString().orEmpty()
        // Framework recycles events after return. Copy only metadata; never retain event/source.
        handler.post { handleEvent(pkg, eventType, windowId, className, eventTime) }
    }

    private fun handleEvent(pkg: String?, eventType: Int, windowId: Int, className: String, eventTime: Long) {
        if (pkg in setOf("com.cainiao.wireless", "com.sina.weibo") && eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
            AdSkipTrace.state("event pkg=$pkg class=$className delay=${SystemClock.uptimeMillis() - eventTime} allowed=${AdSkipStore.appEnabled(this, pkg!!)}")
        if (!AdSkipStore.enabled(this)) return
        if (pkg != null && !AdSkipStore.appEnabled(this, pkg)) return
        if (BuildConfig.DEBUG && eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            Log.d(TAG, "Window event: package=" + pkg + ", window=" + windowId)
        }
        // Delayed events only trigger a fresh root read; their old source is never clicked.
        val now = SystemClock.uptimeMillis()
        if (pkg != null && (pkg != foregroundPackage || windowId != foregroundWindow)) {
            if (pkg != foregroundPackage) actionCounts.clear()
            foregroundPackage = pkg
            foregroundWindow = windowId
            activity = ""
            enteredAt = now
        }
        if (pkg != null && eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val name = className
            val configured = AdSkipStore.rulesFor(this, pkg).flatMap { it.activityNames + it.successActivityNames }
            if (name in configured || name.endsWith("Activity")) {
                if (activity != name) enteredAt = now
                activity = name
                activityWindow = windowId
                // Queue delivery time is not event time: an old queued home event must
                // never become fresh post-click evidence merely because a scan was slow.
                activityObservedAt = eventTime
            }
        }
        if (pending != null) return
        if (!scanScheduled) startBurst()
        else if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || now - burstStartedAt > 1000) {
            // Bring a scan forward without allowing content storms to postpone it.
            handler.removeCallbacks(scanTask)
            scanScheduled = false
            startBurst()
        }
    }

    private fun startBurst() {
        if (!AdSkipStore.enabled(this) || pending != null) return
        scanStep = 0
        burstStartedAt = SystemClock.uptimeMillis()
        scheduleScan((80L - (burstStartedAt - lastScanAt)).coerceAtLeast(0))
    }

    private fun scheduleScan(delay: Long) {
        if (scanScheduled || !AdSkipStore.enabled(this)) return
        scanScheduled = true
        handler.postDelayed(scanTask, delay)
    }

    private fun active(rule: AdSkipRule) = AdSkipRuntime.connected.value && AdSkipStore.enabled(this) &&
        AdSkipStore.appEnabled(this, rule.packageName) && AdSkipStore.ruleEnabled(this, rule) &&
        AdSkipStore.rulesFor(this, rule.packageName).any { it == rule }

    private fun currentRoot(): AccessibilityNodeInfo? {
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) return null
        runCatching { rootInActiveWindow }.getOrNull()?.let { return it }
        val currentWindows = runCatching { windows }.getOrNull().orEmpty()
        return try {
            currentWindows.firstOrNull { it.isActive && it.isFocused && it.type == AccessibilityWindowInfo.TYPE_APPLICATION }?.root
        } finally {
            @Suppress("DEPRECATION")
            currentWindows.forEach { it.recycle() }
        }
    }

    private fun scan() {
        if (!AdSkipStore.enabled(this) || pending != null) return
        // Splash controls may appear without a content event. Do not repeatedly
        // traverse a cached loading tree throughout the short ad window.
        if (Build.VERSION.SDK_INT >= 33) clearCache()
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
        val now = SystemClock.uptimeMillis()
        if (foregroundPackage != pkg || foregroundWindow != root.windowId) {
            if (foregroundPackage != pkg) actionCounts.clear()
            foregroundPackage = pkg; foregroundWindow = root.windowId; activity = ""; enteredAt = now
        }
        val candidates = AdSkipStore.rulesFor(this, pkg).filter { rule ->
            rule.packageName == pkg && active(rule) && AdSkipMatcher.matchesActivity(rule, activity) &&
                (rule.id != AdSkipGenericRules.ID || !AdSkipStore.splashOnly(this) || now - enteredAt <= 15000L) &&
                (rule.matchWindowMs == 0L || now - enteredAt <= rule.matchWindowMs) &&
                (lastAction[rule.runtimeKey()]?.let { now - it >= rule.cooldownMs } ?: true)
                && (rule.actionMaximum == 0 || (actionCounts[rule.runtimeKey()] ?: 0) < rule.actionMaximum)
        }
        if (BuildConfig.DEBUG && scanStep == 0) Log.d(TAG, "Scan: package=" + pkg + ", candidates=" + candidates.size)
        if (candidates.isEmpty()) { recycle(root); return }
        // Query explicit skip labels before walking a potentially large feed. This
        // sparse lookup can find a target, but MUST NEVER prove target absence.
        val generic = candidates.firstOrNull { it.id == AdSkipGenericRules.ID }
        if (generic != null && candidates.size == 1) {
            val found = runCatching { textTarget(root, generic) }.getOrNull()
            if (found != null) {
                recycle(root)
                found.use { tree -> beginAttempt(generic, tree, 1) }
                return
            }
        }
        // Structural popup rules need the complete sibling context in large feeds.
        snapshot(root, budgetMs = if (candidates.any { it.id != AdSkipGenericRules.ID }) 600 else 180).use { tree ->
            if (scanStep == 0) AdSkipTrace.tree(pkg, activity, tree.nodes, tree.complete, candidates.size, tree.elapsedMs)
            for (rule in candidates) {
                val target = AdSkipMatcher.findTarget(rule, tree.nodes)
                if (target == null) { diagnostic(rule, if (tree.complete) AdSkipResult.NO_MATCH else AdSkipResult.INCOMPLETE_TREE); continue }
                if (!active(rule)) return
                beginAttempt(rule, tree, target)
                return
            }
        }
    }

    private fun beginAttempt(rule: AdSkipRule, tree: Snapshot, target: Int) {
        if (!active(rule)) return
        AdSkipTrace.matched(rule, tree.nodes[target], tree.windowId)
        val attempt = Attempt(rule, SystemClock.uptimeMillis(), tree.windowId, tree.nodes[target])
        pending = attempt
        lastAction[rule.runtimeKey()] = attempt.startedAt
        handler.removeCallbacks(scanTask)
        scanScheduled = false
        if (rule.action == "clickCenter") {
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
            AdSkipStore.record(this, rule, AdSkipResult.CLICK_ACCEPTED)
            verifyLater(attempt, 300)
        } else if (rule.action != "click" || !gesture(attempt)) finish(attempt, AdSkipResult.CLICK_REJECTED)
    }

    /** Only explicit ad labels/countdowns/IDs qualify without a complete context tree. */
    private fun textTarget(root: AccessibilityNodeInfo, rule: AdSkipRule): Snapshot? {
        val started = SystemClock.uptimeMillis()
        val screen = describe(root, null)
        for (label in listOf("跳过", "skip")) {
            if (SystemClock.uptimeMillis() - started > 180) break
            val matches = root.findAccessibilityNodeInfosByText(label)
            var selected: Snapshot? = null
            try {
                for (node in matches.take(24)) {
                    if (node.windowId != root.windowId) continue
                    val nodes = listOf(screen, describe(node, null))
                    if (AdSkipMatcher.findTarget(rule, nodes) != 1) continue
                    // Keep separately owned handles and no invented parent relationship.
                    @Suppress("DEPRECATION")
                    val native = listOf(AccessibilityNodeInfo.obtain(root), AccessibilityNodeInfo.obtain(node))
                    selected = Snapshot(nodes, native, false, root.windowId, SystemClock.uptimeMillis() - started)
                    AdSkipTrace.verification(rule, "lookup=text scanMs=${selected.elapsedMs}")
                    break
                }
            } finally { matches.forEach(::recycle) }
            if (selected != null) return selected
        }
        return null
    }

    private fun describe(node: AccessibilityNodeInfo, parent: Int?, index: Int? = null): AdSkipNode {
        val rect = Rect().also(node::getBoundsInScreen)
        return AdSkipNode(node.viewIdResourceName, node.text?.toString()?.take(160), node.isVisibleToUser, node.isEnabled, node.isClickable, parent,
            node.contentDescription?.toString()?.take(160), node.className?.toString(), AdSkipBounds(rect.left, rect.top, rect.right, rect.bottom), node.packageName?.toString(), index, node.childCount)
    }

    private fun gesture(attempt: Attempt): Boolean {
        if (pending !== attempt || !active(attempt.rule) || !attempt.rule.allowGesture ||
            attempt.rule.action !in setOf("click", "clickCenter") || attempt.gestureTried) return false
        attempt.gestureTried = true
        val root = currentRoot() ?: return false
        if (root.packageName?.toString() != attempt.rule.packageName || root.windowId != attempt.windowId) { recycle(root); return false }
        // ACTION_CLICK may have changed the screen. Match again, never reuse old coordinates.
        val bounds = snapshot(root, budgetMs = 600).use { tree ->
            val targetIndex = AdSkipMatcher.findTarget(attempt.rule, tree.nodes) ?: return false
            val target = tree.nodes[targetIndex]
            if (!sameTarget(attempt.target, target) && !relocatedAdClose(attempt, tree.nodes, targetIndex)) return false
            val b = target.bounds ?: return false
            val screen = tree.nodes.firstOrNull()?.bounds ?: return false
            if (!b.containedBy(screen)) return false
            if (attempt.rule.id == AdSkipGenericRules.ID && target.id?.substringAfter(":id/") in setOf("splash_ad_container", "splash_container")) {
                AdSkipBounds(screen.left + screen.width * 4 / 5, screen.top + screen.height / 20,
                    screen.left + screen.width * 19 / 20, screen.top + screen.height / 7)
            } else {
                if (b.width > screen.width / 2 || b.height > screen.height / 3) return false
                b
            }
        }
        val path = Path().apply { moveTo((bounds.left + bounds.right) / 2f, (bounds.top + bounds.bottom) / 2f) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 40)).build()
        AdSkipStore.record(this, attempt.rule, AdSkipResult.GESTURE_SENT)
        val submitted = runCatching { dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (pending !== attempt) return
                if (!attempt.counted) {
                    actionCounts[attempt.rule.runtimeKey()] = (actionCounts[attempt.rule.runtimeKey()] ?: 0) + 1
                    attempt.counted = true
                }
                AdSkipStore.record(this@AdSkipAccessibilityService, attempt.rule, AdSkipResult.GESTURE_COMPLETED)
                verifyLater(attempt)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) { finish(attempt, AdSkipResult.GESTURE_CANCELLED) }
        }, handler) }.getOrDefault(false)
        if (!submitted) finish(attempt, AdSkipResult.GESTURE_CANCELLED)
        // A vendor missing the callback must not leave the engine stuck forever.
        if (submitted) handler.postDelayed({ if (pending === attempt) finish(attempt, AdSkipResult.UNCONFIRMED) }, 2400)
        return submitted
    }

    private fun verify(attempt: Attempt) {
        if (pending !== attempt) return
        if (!active(attempt.rule) || !AdSkipTiming.verificationTimely(attempt.startedAt, SystemClock.uptimeMillis())) {
            finish(attempt, AdSkipResult.UNCONFIRMED); return
        }
        if (Build.VERSION.SDK_INT >= 33) clearCache()
        val root = currentRoot()
        if (root == null) {
            attempt.absence.reset(); attempt.homeChecks = 0
            AdSkipTrace.verification(attempt.rule, "root=null"); verifyLater(attempt); return
        }
        val pkg = root.packageName?.toString()
        if (pkg != attempt.rule.packageName) { AdSkipTrace.verification(attempt.rule, "foreground-changed"); recycle(root); finish(attempt, AdSkipResult.UNCONFIRMED); return }
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
        val directPresent = queryOriginalTarget(root, attempt.target)
        if (directPresent != null) {
            val window = root.windowId
            recycle(root)
            AdSkipTrace.verification(attempt.rule, "lookup=original present=$directPresent window=$window")
            if (attempt.absence.observe(window, true, directPresent)) {
                finish(attempt, AdSkipResult.TARGET_GONE)
            } else if (directPresent) {
                if (!attempt.gestureTried && gesture(attempt)) return
                if (++attempt.presentChecks >= 3) finish(attempt, AdSkipResult.TARGET_PRESENT) else verifyLater(attempt)
            } else verifyLater(attempt, 150)
            return
        }
        snapshot(root, budgetMs = 600).use { tree ->
            if (!AdSkipTiming.verificationTimely(attempt.startedAt, SystemClock.uptimeMillis())) {
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
                if (!attempt.gestureTried && (sameTarget(attempt.target, tree.nodes[target]) ||
                    relocatedAdClose(attempt, tree.nodes, target)) && gesture(attempt)) return
                if (++attempt.presentChecks < 3) { verifyLater(attempt); return }
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
        val id = target.id?.takeIf { it.startsWith("${target.packageName}:id/") }
        val explicit = listOfNotNull(target.text, target.description).firstOrNull(AdSkipRules::isSkipLabel)
        val text = when {
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
        if (AdSkipOutcome.label(result) == "成功") announce(attempt.rule, "跳过成功")
        startBurst()
    }

    private fun announce(rule: AdSkipRule, message: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) { mainHandler.post { announce(rule, message) }; return }
        if (!AdSkipStore.enabled(this) || !AdSkipStore.feedbackEnabled(this)) return
        val app = (AdSkipRules.appNames[rule.packageName] ?: rule.appLabel ?: "广告辅助").take(10)
        // Replace the in-progress message so throttling cannot swallow the final result.
        feedbackToast?.cancel()
        feedbackToast = Toast.makeText(this, app + "：" + message, Toast.LENGTH_SHORT).also { it.show() }
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
        mainHandler.post { feedbackToast?.cancel(); feedbackToast = null }
        scanScheduled = false
        previous?.let {
            AdSkipStore.record(this, it.rule, AdSkipResult.UNCONFIRMED)
            AdSkipStore.recordOutcome(this, it.rule, AdSkipResult.UNCONFIRMED)
        }
    }

    override fun onInterrupt() { handler.post { cancelWork(); activity = ""; foregroundPackage = ""; foregroundWindow = -1; if (AdSkipStore.enabled(this)) handler.postDelayed(windowProbe, 1200) } }
    override fun onUnbind(intent: Intent?): Boolean { handler.post { cancelWork() }; background.stop(); AdSkipRuntime.setConnected(false); return super.onUnbind(intent) }
    override fun onDestroy() {
        AdSkipStore.prefs(this).unregisterOnSharedPreferenceChangeListener(preferenceListener)
        unregisterReceiver(packagesChanged)
        handler.post { cancelWork() }
        // Quit outside the queue: an earlier cancelWork may remove queued callbacks.
        worker.quitSafely()
        background.stop()
        AdSkipRuntime.setConnected(false)
        super.onDestroy()
    }

    override fun dump(fd: java.io.FileDescriptor, writer: java.io.PrintWriter, args: Array<out String>) {
        if (!BuildConfig.DEBUG) return
        writer.println("AdSkipDebug connected=${AdSkipRuntime.connected.value} enabled=${AdSkipStore.enabled(this)} events=$receivedEvents")
        val ready = java.util.concurrent.CountDownLatch(1)
        var workerState = "worker-response-timeout"
        handler.post {
            workerState = "probes=$probeCount probeAge=${SystemClock.uptimeMillis() - lastProbeAt} root=$lastRootStatus pending=${pending != null} scheduled=$scanScheduled step=$scanStep"
            ready.countDown()
        }
        ready.await(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
        writer.println(workerState)
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
        var counted = false
        var homeChecks = 0
        var gestureTried = false
        val absence = AdSkipAbsenceEvidence()
        var presentChecks = 0
    }
    @Suppress("DEPRECATION")
    private fun recycle(node: AccessibilityNodeInfo) { node.recycle() }
    private companion object {
        const val TAG = "AdSkipAccessibility"
        val RETRIES = longArrayOf(100, 200, 300, 400, 800, 1200, 2000)
        val EVENT_TYPES = setOf(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, AccessibilityEvent.TYPE_WINDOWS_CHANGED)
    }
}
