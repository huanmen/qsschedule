package cn.qztc.timetable.qnu

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject

/** Diagnostics deliberately exclude URLs, exception messages, HTML and timetable contents. */
class QnuImportDiagnostics(private val context: Context) {
    private val preferences = context.getSharedPreferences("qnu-import-diagnostics", Context.MODE_PRIVATE)
    private var last = SystemClock.elapsedRealtime()
    @Synchronized fun record(stage: String, error: Throwable? = null, rendererCrashed: Boolean? = null) {
        runCatching {
            val now = SystemClock.elapsedRealtime()
            val old = JSONArray(preferences.getString("events", "[]"))
            val events = JSONArray()
            for (i in maxOf(0,old.length()-79) until old.length()) events.put(old.getJSONObject(i))
            val runtime = Runtime.getRuntime()
            val event = JSONObject().put("stage",stage).put("elapsedMs",now-last)
                .put("time",System.currentTimeMillis()).put("heapBytes",runtime.totalMemory()-runtime.freeMemory())
            error?.let { event.put("exception",it.javaClass.name).put("frames",JSONArray(it.stackTrace.filter { frame -> frame.className.startsWith("cn.qztc.") }.take(12).map { frame -> "${frame.className}.${frame.methodName}:${frame.lineNumber}" })) }
            rendererCrashed?.let { event.put("rendererCrashed",it) }
            events.put(event);preferences.edit().putString("events",events.toString()).apply();last=now
        }
    }

    fun exportText(): String = JSONObject().apply {
        put("format","qztc-import-diagnostics/v1")
        put("androidSdk",Build.VERSION.SDK_INT);put("device",Build.MANUFACTURER+" "+Build.MODEL)
        put("webViewVersion",WebView.getCurrentWebViewPackage()?.versionName)
        put("events",JSONArray(preferences.getString("events","[]")))
        if(Build.VERSION.SDK_INT>=30) {
            val manager=context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            put("exits",JSONArray(manager.getHistoricalProcessExitReasons(context.packageName,0,5).map {
                JSONObject().put("reason",it.reason).put("status",it.status).put("time",it.timestamp).put("pssKb",it.pss).put("rssKb",it.rss)
            }))
        }
    }.toString(2)
}
