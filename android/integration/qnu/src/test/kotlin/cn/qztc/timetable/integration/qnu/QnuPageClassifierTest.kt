package cn.qztc.timetable.integration.qnu

import org.junit.Assert.assertEquals
import org.junit.Test

class QnuPageClassifierTest {

    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.classLoader.getResourceAsStream("fixtures/$name")).readBytes()

    @Test
    fun `fixture is recognized as timetable without exposing content`() {
        assertEquals(
            QnuPageKind.TIMETABLE,
            QnuPageClassifier.classify(fixture("qnu-timetable-standard.gbk.html")),
        )
    }

    @Test
    fun `login form is recognized as authentication page`() {
        val html = """
            <html><head><title>统一身份认证登录</title></head><body>
            <form action="/academic/login.do"><input type="password" name="password"></form>
            </body></html>
        """.trimIndent().toByteArray()

        assertEquals(QnuPageKind.AUTHENTICATION, QnuPageClassifier.classify(html))
    }

    @Test
    fun `unrelated page remains unknown`() {
        assertEquals(QnuPageKind.UNKNOWN, QnuPageClassifier.classify("<html>ok</html>".toByteArray()))
    }
}
