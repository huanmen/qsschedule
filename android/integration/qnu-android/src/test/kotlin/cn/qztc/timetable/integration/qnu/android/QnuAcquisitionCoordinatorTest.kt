package cn.qztc.timetable.integration.qnu.android

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QnuAcquisitionCoordinatorTest {

    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")).readBytes()

    private val session = QnuSessionSnapshot(
        timetableUrl = "https://jw.qztc.edu.cn/academic/manager/coursearrange/showTimetable.do?id=synthetic",
        cookieHeader = "SESSION=synthetic",
    )

    @Test
    fun `valid fetched bytes become imported timetable`() = runTest {
        val coordinator = QnuAcquisitionCoordinator {
            QnuFetchResult.Page(fixture("qnu-timetable-standard.gbk.html"), "text/html; charset=GBK")
        }

        val result = coordinator.acquire(session, "semester-simulated")

        assertTrue(result is QnuAcquisitionResult.Ready)
        val timetable = (result as QnuAcquisitionResult.Ready).timetable
        assertTrue(timetable.state.courses.isNotEmpty())
        assertEquals(13, timetable.periods.size)
    }

    @Test
    fun `login HTML is returned as reauthentication instead of empty timetable`() = runTest {
        val login = """
            <html><head><title>统一身份认证登录</title></head>
            <body><form action="/login.do"><input type="password"></form></body></html>
        """.trimIndent().toByteArray()
        val coordinator = QnuAcquisitionCoordinator { QnuFetchResult.Page(login, "text/html") }

        assertEquals(QnuAcquisitionResult.ReauthenticationRequired, coordinator.acquire(session, "s1"))
    }

    @Test
    fun `unknown HTML is rejected without attempting an empty import`() = runTest {
        val coordinator = QnuAcquisitionCoordinator {
            QnuFetchResult.Page("<html>not a timetable</html>".toByteArray(), "text/html")
        }

        assertEquals(
            QnuAcquisitionResult.Failed(QnuAcquisitionFailure.INVALID_PAGE),
            coordinator.acquire(session, "s1"),
        )
    }

    @Test
    fun `empty semester is reported separately without clearing data`() = runTest {
        val emptySemester = """
            <html><head><meta charset="GBK"></head><body>
            <div id="title">2026 秋 学生课表</div>
            <table id="timetable"><tr><th></th><th>周一</th></tr></table>
            </body></html>
        """.trimIndent().toByteArray(charset("GBK"))
        val coordinator = QnuAcquisitionCoordinator {
            QnuFetchResult.Page(emptySemester, "text/html; charset=GBK")
        }

        assertEquals(
            QnuAcquisitionResult.Failed(QnuAcquisitionFailure.EMPTY_TIMETABLE),
            coordinator.acquire(session, "qnu-2026-autumn"),
        )
    }

    @Test
    fun `session string never exposes URL or cookie`() {
        val rendered = session.toString()
        assertEquals("QnuSessionSnapshot(redacted)", rendered)
        assertTrue(!rendered.contains("SESSION") && !rendered.contains("showTimetable"))
    }
}
