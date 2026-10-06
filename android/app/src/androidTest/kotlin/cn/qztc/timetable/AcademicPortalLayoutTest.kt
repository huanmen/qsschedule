package cn.qztc.timetable

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import cn.qztc.timetable.education.REPAIR_ACADEMIC_HOME_LAYOUT
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AcademicPortalLayoutTest {
    @get:Rule val activity = ActivityScenarioRule(FeatureAcceptanceActivity::class.java)
    private fun onMain(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)

    private fun fixture(url: String, check: (WebView) -> Unit) {
        lateinit var web: WebView
        val ready = CountDownLatch(1)
        activity.scenario.onActivity { host ->
            web = WebView(host)
            web.settings.javaScriptEnabled = true
            web.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) { ready.countDown() }
            }
            host.setContentView(web)
            web.loadDataWithBaseURL(url, """
                <!doctype html><html style="height:0"><body style="height:0;margin:0">
                <iframe src="/academic/frameset_index.jsp" srcdoc="&lt;h1&gt;合成教务页面&lt;/h1&gt;" width="100%" height="100%" style="height:0"></iframe>
                </body></html>
            """.trimIndent(), "text/html", "UTF-8", null)
        }
        try {
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            check(web)
        } finally { onMain { web.stopLoading(); web.destroy() } }
    }

    private fun evaluate(web: WebView, source: String): String {
        val done = CountDownLatch(1)
        var result = ""
        onMain { web.evaluateJavascript(source) { result = it; done.countDown() } }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        return result
    }

    @Test fun restoresCollapsedHomeWrapperAndRemainsIdempotent() {
        fixture("https://jw.qztc.edu.cn/academic/index_frame.jsp") { web ->
            assertEquals("true", evaluate(web, REPAIR_ACADEMIC_HOME_LAYOUT))
            assertEquals("true", evaluate(web, "document.querySelector('iframe').getBoundingClientRect().height >= innerHeight"))
            assertEquals("true", evaluate(web, REPAIR_ACADEMIC_HOME_LAYOUT))
            assertEquals("true", evaluate(web, "document.querySelector('iframe').contentDocument.querySelector('h1') !== null"))
        }
    }

    @Test fun neverRestylesLoginOrEvaluationForms() {
        fixture("https://jw.qztc.edu.cn/academic/evaluation/form.do") { web ->
            assertEquals("false", evaluate(web, REPAIR_ACADEMIC_HOME_LAYOUT))
            assertEquals("true", evaluate(web, "document.querySelector('iframe').style.height === '0px'"))
        }
    }
}
