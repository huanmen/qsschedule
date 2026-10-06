package cn.qztc.timetable.qnu

import android.webkit.WebView
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

// Return only a boolean: no account, URL or course content leaves the WebView.
internal const val IS_COURSE_ARRANGEMENT_SCRIPT = """
(() => {
  const docs = [];
  function visit(doc) {
    if (!doc || docs.includes(doc) || docs.length >= 32) return;
    docs.push(doc);
    for (const frame of doc.querySelectorAll('iframe,frame')) {
      try { visit(frame.contentDocument); } catch (_) {}
    }
  }
  visit(document);
  return docs.some(doc => {
    const text = (doc.body?.innerText || '').replace(/[\s\u00a0\u3000]+/g, '');
    // A menu link alone is not the destination. Require the arrangement table
    // headers; an empty semester still has these headers.
    return /课程安排|教学安排/.test(text) &&
      Array.from(doc.querySelectorAll('table')).some(table => {
        const t = (table.innerText || '').replace(/[\s\u00a0\u3000]+/g, '');
        return /课程名/.test(t) && /学分/.test(t) && /上课时间/.test(t);
      });
  });
})();
"""

internal suspend fun isCourseArrangementPage(view: WebView): Boolean = withTimeoutOrNull(3_000) {
    suspendCancellableCoroutine { continuation ->
        view.post {
            if (continuation.isActive) view.evaluateJavascript(IS_COURSE_ARRANGEMENT_SCRIPT) { result ->
                if (continuation.isActive) continuation.resume(result == "true")
            }
        }
    }
} ?: false

internal suspend fun waitForCourseArrangement(view: WebView): Boolean = withTimeoutOrNull(15_000) {
    while (!isCourseArrangementPage(view)) delay(250)
    true
} ?: false
