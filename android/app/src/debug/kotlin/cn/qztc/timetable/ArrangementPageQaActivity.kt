package cn.qztc.timetable

import android.app.Activity
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import cn.qztc.timetable.qnu.isCourseArrangementPage
import kotlinx.coroutines.*

/** Exercises the real WebView detector with synthetic pages only. */
class ArrangementPageQaActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var web: WebView
    private var sample = 0
    private val headers = "<table><tr><th>课程名称</th><th>学分</th><th>上课时间、地点</th></tr></table>"
    private val cases by lazy { listOf(
        "<h1>2026秋 课程安排</h1>$headers" to true,
        "<h1>2026秋 课程安排</h1>$headers<p>本学期没有课程</p>" to true,
        "<a href='#'>课程安排</a><p>学分信息</p>" to false,
        "<h1>个人课表</h1><table><tr><td>星期一</td></tr></table>" to false,
        "<iframe srcdoc='<h1>课程安排</h1>$headers'></iframe>" to true,
    ) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    scope.launch {
                        runCatching {
                            check(isCourseArrangementPage(view) == cases[sample].second)
                        }.onFailure {
                            android.util.Log.e("ArrangementQA", "FAIL synthetic case $sample", it)
                            finish()
                            return@launch
                        }
                        sample++
                        if (sample == cases.size) {
                            android.util.Log.i("ArrangementQA", "PASS 5 synthetic arrangement-page cases")
                            finish()
                        } else loadSample()
                    }
                }
            }
        }
        setContentView(web)
        loadSample()
    }
    private fun loadSample() = web.loadDataWithBaseURL("https://example.invalid/", cases[sample].first, "text/html", "UTF-8", null)
    override fun onDestroy() {
        scope.cancel()
        web.destroy()
        super.onDestroy()
    }
}
