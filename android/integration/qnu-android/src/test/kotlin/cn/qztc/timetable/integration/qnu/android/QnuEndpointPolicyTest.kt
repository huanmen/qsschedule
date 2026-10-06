package cn.qztc.timetable.integration.qnu.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QnuEndpointPolicyTest {

    @Test
    fun `only exact HTTPS academic host is trusted`() {
        assertTrue(QnuEndpointPolicy.isTrustedAcademicUrl("https://jw.qztc.edu.cn/academic/index_frame.jsp"))
        assertFalse(QnuEndpointPolicy.isTrustedAcademicUrl("http://jw.qztc.edu.cn/academic/index_frame.jsp"))
        assertFalse(QnuEndpointPolicy.isTrustedAcademicUrl("https://jw.qztc.edu.cn.example.com/academic/index_frame.jsp"))
    }

    @Test
    fun `personal timetable path is recognized without fixed query parameters`() {
        assertTrue(
            QnuEndpointPolicy.isTimetableUrl(
                "https://jw.qztc.edu.cn/academic/manager/coursearrange/showTimetable.do?id=synthetic&yearid=1",
            ),
        )
        assertFalse(QnuEndpointPolicy.isTimetableUrl("https://jw.qztc.edu.cn/academic/index_frame.jsp"))
        assertTrue(
            QnuEndpointPolicy.isSnapshotCaptureUrl(
                "https://jw.qztc.edu.cn/academic/student/currcourse/currcourse.jsdo",
            ),
        )
        assertFalse(QnuEndpointPolicy.isSnapshotCaptureUrl("https://id.qztc.edu.cn/portal/login.html"))
    }

    @Test
    fun `course selection accepts only numeric school identifiers`() {
        assertTrue(
            QnuEndpointPolicy.isCourseSelectionUrl(
                "https://jw.qztc.edu.cn/academic/student/currcourse/currcourse.jsdo?year=45&term=3",
            ),
        )
        assertTrue(
            QnuEndpointPolicy.courseSelectionUrl("45", "3") ==
                "https://jw.qztc.edu.cn/academic/student/currcourse/currcourse.jsdo?year=45&term=3",
        )
        assertTrue(QnuEndpointPolicy.courseSelectionUrl("45&redirect=evil", "3") == null)
    }

    @Test
    fun `identity and academic login pages require authentication`() {
        assertTrue(QnuEndpointPolicy.isAuthenticationUrl("https://id.qztc.edu.cn/portal/login.html"))
        assertTrue(QnuEndpointPolicy.isAuthenticationUrl("https://jw.qztc.edu.cn/academic/login/qztc/login.jsp"))
        assertFalse(QnuEndpointPolicy.isAuthenticationUrl("https://id.qztc.edu.cn/portal/main.html"))
        assertFalse(QnuEndpointPolicy.isAuthenticationUrl("https://id.qztc.edu.cn/portal/app/main.html"))
        assertFalse(QnuEndpointPolicy.isAuthenticationUrl("https://jw.qztc.edu.cn/academic/index_frame.jsp"))
    }

    @Test
    fun `interactive webview navigation stays inside school hosts`() {
        assertTrue(QnuEndpointPolicy.isAllowedWebViewUrl(QnuEndpointPolicy.INITIAL_WEBVIEW_URL))
        assertTrue(QnuEndpointPolicy.isAllowedWebViewUrl(QnuEndpointPolicy.CURRENT_TIMETABLE_ENTRY_URL))
        assertTrue(QnuEndpointPolicy.isAllowedWebViewUrl("https://id.qztc.edu.cn/portal/login.html"))
        assertFalse(QnuEndpointPolicy.isAllowedWebViewUrl("http://jw.qztc.edu.cn/academic/index_frame.jsp"))
        assertFalse(QnuEndpointPolicy.isAllowedWebViewUrl("https://example.com/"))
        assertFalse(QnuEndpointPolicy.isAllowedWebViewUrl("https://jw.qztc.edu.cn.example.com/"))
    }

    @Test
    fun `only the insecure academic CAS callback is upgraded to HTTPS`() {
        val callback =
            "http://jw.qztc.edu.cn/academic/login/qztc/loginIds6Valid.jsp?ticket=synthetic"

        assertTrue(
            QnuEndpointPolicy.upgradeInsecureAcademicCasCallback(callback) ==
                "https://jw.qztc.edu.cn/academic/login/qztc/loginIds6Valid.jsp?ticket=synthetic",
        )
        assertTrue(
            QnuEndpointPolicy.upgradeInsecureAcademicCasCallback(
                "https://jw.qztc.edu.cn/academic/login/qztc/loginIds6Valid.jsp",
            ) == null,
        )
        assertTrue(
            QnuEndpointPolicy.upgradeInsecureAcademicCasCallback(
                "http://jw.qztc.edu.cn/academic/index_frame.jsp",
            ) == null,
        )
        assertTrue(
            QnuEndpointPolicy.upgradeInsecureAcademicCasCallback(
                "http://jw.qztc.edu.cn.example.com/academic/login/qztc/loginIds6Valid.jsp",
            ) == null,
        )
    }

    @Test
    fun `portal and academic home pages are recognized`() {
        assertTrue(QnuEndpointPolicy.isPortalMainUrl("https://id.qztc.edu.cn/portal/main.html?r=1"))
        assertFalse(QnuEndpointPolicy.isPortalMainUrl("https://id.qztc.edu.cn/portal/login.html"))
        assertTrue(QnuEndpointPolicy.isAcademicHomeUrl("https://jw.qztc.edu.cn/academic/index_frame.jsp"))
        assertTrue(QnuEndpointPolicy.isAcademicHomeUrl("https://jw.qztc.edu.cn/academic/frameset_index.jsp"))
        assertFalse(QnuEndpointPolicy.isAcademicHomeUrl("https://jw.qztc.edu.cn/academic/footer.jsp"))
    }
}
