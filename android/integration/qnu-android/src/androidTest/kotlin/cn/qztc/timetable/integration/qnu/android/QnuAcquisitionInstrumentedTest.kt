package cn.qztc.timetable.integration.qnu.android

import android.webkit.CookieManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QnuAcquisitionInstrumentedTest {

    private val timetableUrl =
        "https://jw.qztc.edu.cn/academic/manager/coursearrange/showTimetable.do?id=synthetic"

    @Test
    fun syntheticWebViewCookieCanBeCapturedWithoutAppearingInDiagnostics() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setCookie(timetableUrl, "SESSION=synthetic; Path=/academic; Secure")
        cookieManager.flush()

        try {
            val result = QnuWebSessionCapture().capture(timetableUrl)
            assertTrue(result is QnuSessionCaptureResult.Captured)
            val rendered = (result as QnuSessionCaptureResult.Captured).session.toString()
            assertEquals("QnuSessionSnapshot(redacted)", rendered)
        } finally {
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
        }
    }

    @Test
    fun syntheticGbkPageCrossesAndroidAcquisitionBoundary() = runTest {
        val bytes = """
            <html><head><meta http-equiv="Content-Type" content="text/html; charset=gbk"/></head><body>
            <div id="title">2026 秋 学生课表</div>
            <table id="timetable"><tbody>
            <tr><th></th><th>周一</th></tr>
            <tr><th>第1节<br/>08:20<br/>┆<br/>09:05</th>
            <td id="1-1">&lt;&lt;模拟课程&gt;&gt;;1<br/>模拟教室<br/>模拟教师<br/>1-16周<br/>讲课学时</td></tr>
            </tbody></table></body></html>
        """.trimIndent().toByteArray(charset("GBK"))
        val coordinator = QnuAcquisitionCoordinator { QnuFetchResult.Page(bytes, "text/html; charset=GBK") }
        val session = QnuSessionSnapshot(
            timetableUrl,
            "SESSION=synthetic",
        )

        val result = coordinator.acquire(session, "device-simulation")

        assertTrue(result is QnuAcquisitionResult.Ready)
        val ready = result as QnuAcquisitionResult.Ready
        assertEquals(1, ready.timetable.state.courses.size)
        assertEquals(1, ready.timetable.state.meetings.size)
    }
}
