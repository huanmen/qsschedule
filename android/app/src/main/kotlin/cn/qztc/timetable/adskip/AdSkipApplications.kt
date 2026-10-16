package cn.qztc.timetable.adskip

import android.content.Context
import android.content.Intent
import android.os.SystemClock

/** Launcher visibility only; no QUERY_ALL_PACKAGES and no access to system permission screens. */
internal object AdSkipApplications {
    private var cached: Map<String, String> = emptyMap()
    private var loadedAt = 0L
    @Synchronized fun labels(context: Context, refresh: Boolean = false): Map<String, String> {
        // PackageManager calls can block the accessibility worker on vendor ROMs.
        // Refresh explicitly on package broadcasts; never refresh from a scan just
        // because the cache is older than a timer threshold.
        if (refresh || loadedAt == 0L) {
            AdSkipStore.invalidateApplicationDefaults()
            val pm = context.packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            val entries = pm.queryIntentActivities(launcher, 0)
            @Suppress("DEPRECATION")
            val homes = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0).map { it.activityInfo.packageName }.toSet()
            val keyboards = context.getSystemService(android.view.inputmethod.InputMethodManager::class.java).inputMethodList.map { it.packageName }.toSet()
            cached = entries.mapNotNull { entry ->
                val info = entry.activityInfo.applicationInfo
                val pkg = info.packageName
                if (!AdSkipRules.supportedPackage(pkg) || pkg in homes || pkg in keyboards ||
                    pkg in setOf("com.miui.securitycenter", "com.miui.securitymanager", "com.miui.home", "com.android.launcher", "com.android.launcher3", "com.miui.packageinstaller") ||
                    pkg.startsWith("com.google.android.permission")) null
                else pkg to pm.getApplicationLabel(info).toString().replace('|', ' ').take(80)
            }.toMap()
            loadedAt = SystemClock.uptimeMillis()
        }
        return cached
    }
}
