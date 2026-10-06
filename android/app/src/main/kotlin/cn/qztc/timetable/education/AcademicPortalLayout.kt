package cn.qztc.timetable.education

import android.webkit.WebView
import cn.qztc.timetable.integration.qnu.android.QnuEndpointPolicy

/** The school's legacy outer iframe can inherit a zero-height document in WebView.
 * Only repair that exact home wrapper; never alter login, scoring or grade forms. */
internal const val REPAIR_ACADEMIC_HOME_LAYOUT = """
(() => {
  if (location.protocol !== 'https:' || location.hostname !== 'jw.qztc.edu.cn' ||
      location.pathname !== '/academic/index_frame.jsp') return false;
  const body = document.body;
  if (!body) return false;
  const frame = [...body.children].find(e => {
    if (e.tagName !== 'IFRAME') return false;
    try { const url = new URL(e.getAttribute('src'), document.baseURI);
      return url.origin === location.origin && url.pathname === '/academic/frameset_index.jsp';
    } catch (_) { return false; }
  });
  if (!frame) return false;
  // Some Android WebViews report a nonzero innerHeight while resolving vh to 0.
  // Use the measured CSS viewport; the native layout listener reruns on resize.
  const height = Math.max(window.innerHeight, window.visualViewport?.height || 0);
  if (!(height > 0)) return false;
  const pixels = Math.ceil(height) + 'px';
  document.documentElement.style.setProperty('height', pixels, 'important');
  document.documentElement.style.setProperty('min-height', pixels, 'important');
  body.style.setProperty('height', pixels, 'important');
  body.style.setProperty('min-height', pixels, 'important');
  body.style.setProperty('margin', '0', 'important');
  frame.style.setProperty('display', 'block', 'important');
  frame.style.setProperty('width', '100%', 'important');
  frame.style.setProperty('height', pixels, 'important');
  frame.style.setProperty('min-height', pixels, 'important');
  frame.style.setProperty('border', '0', 'important');
  return frame.getBoundingClientRect().height > 0;
})()
"""

internal fun repairAcademicHomeLayout(view: WebView) {
    if (QnuEndpointPolicy.isAcademicHomeUrl(view.url.orEmpty())) {
        view.evaluateJavascript(REPAIR_ACADEMIC_HOME_LAYOUT, null)
    }
}
