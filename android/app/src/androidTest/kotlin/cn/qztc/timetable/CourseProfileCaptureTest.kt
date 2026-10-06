package cn.qztc.timetable

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cn.qztc.timetable.qnu.readCourseProfiles
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CourseProfileCaptureTest {
    @get:Rule val compose=createAndroidComposeRule<FeatureAcceptanceActivity>()
    @Test fun captureWaitsForCourseListInFrameInsteadOfUnrelatedTable() = runBlocking {
        lateinit var web: WebView
        val loaded=CountDownLatch(1)
        compose.runOnUiThread {
            web=WebView(compose.activity)
            web.settings.javaScriptEnabled=true
            web.webViewClient=object:WebViewClient(){override fun onPageFinished(view:WebView,url:String){loaded.countDown()}}
            compose.activity.setContentView(web)
            web.loadDataWithBaseURL("https://jw.qztc.edu.cn/", """
              <table class="infolist_hr"><tr><td>无关表格</td></tr></table>
              <iframe id="details"></iframe><script>
              setTimeout(()=>document.querySelector('#details').srcdoc='<table><tr><th>课程名</th><th>课序号</th><th>学分</th><th>选课属性</th><th>考核方式</th></tr><tr><td>合成课程</td><td>02</td><td>2.5</td><td>选修</td><td>考查</td></tr></table>',600);
              </script>
            """.trimIndent(),"text/html","UTF-8",null)
        }
        try {
            assertTrue("WebView fixture did not load",loaded.await(10,TimeUnit.SECONDS))
            val profiles=readCourseProfiles(web)
            assertEquals(1,profiles.size)
            assertEquals("2.5",profiles.single().credits)
            assertEquals("选修",profiles.single().selectionAttribute)
            assertEquals("考查",profiles.single().assessmentMethod)
        } finally { compose.runOnUiThread { web.stopLoading();web.destroy() } }
    }
}
