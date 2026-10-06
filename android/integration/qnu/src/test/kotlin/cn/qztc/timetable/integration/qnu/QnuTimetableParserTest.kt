package cn.qztc.timetable.integration.qnu

import cn.qztc.timetable.core.domain.imports.DiagnosticSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QnuTimetableParserTest {

    @Test fun `row spans spaced headings and numbering column retain profile alignment`() {
        val html="""<table><tr><th>序号</th><th>课程名</th><th>课序号</th><th>学<br>分</th><th>选课 属性</th><th>考核<br>方式</th><th>教室</th></tr>
          <tr><td rowspan="2">1</td><td rowspan="2">英语A(3)</td><td rowspan="2">02</td><td rowspan="2">3.0</td><td rowspan="2">必修</td><td rowspan="2">考试</td><td>A101</td></tr>
          <tr><td>B202</td></tr></table>"""
        val profiles=QnuTimetableParser.parseCourseProfilesHtml(html)
        assertEquals(1,profiles.size)
        assertEquals("02",profiles.single().sequence)
        assertEquals("考试",profiles.single().assessmentMethod)
        assertEquals("3.0",QnuTimetableParser.matchCourseProfile("英语A（3）","2",profiles)?.credits)
        assertNull(QnuTimetableParser.matchCourseProfile("英语A(1)","2",profiles))
    }

    @Test fun `matching does not borrow metadata from another section or ambiguous name`() {
        val profiles=listOf(QnuCourseProfile("算法","01",credits="3"),QnuCourseProfile("算法","02",credits="4"))
        assertEquals("4",QnuTimetableParser.matchCourseProfile("算法","2",profiles)?.credits)
        assertNull(QnuTimetableParser.matchCourseProfile("算法","3",profiles))
        assertNull(QnuTimetableParser.matchCourseProfile("算法",null,profiles))
    }

    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.classLoader.getResourceAsStream("fixtures/$name")) {
            "missing fixture $name"
        }.readBytes()

    private val standard by lazy { QnuTimetableParser.parse(fixture("qnu-timetable-standard.gbk.html")) }
    private val changes by lazy { QnuTimetableParser.parse(fixture("qnu-timetable-changes.gbk.html")) }

    @Test
    fun `gbk charset detected and chinese decoded`() {
        assertEquals("GBK", standard.charset?.uppercase())
        assertTrue(standard.gridEntries.any { it.courseName == "示例课程01" })
        assertTrue(standard.gridEntries.any { it.teacher == "示例教师02" })
    }

    @Test
    fun `semester header parsed`() {
        assertEquals("2024", standard.yearText)
        assertEquals("秋", standard.termText)
        assertEquals("2023", changes.yearText)
        assertEquals("示例班级", standard.className)
    }

    @Test
    fun `thirteen period rows with times`() {
        assertEquals(13, standard.periods.size)
        val first = standard.periods.first()
        assertEquals("第1节", first.label)
        assertEquals("08:20", first.startText)
        assertEquals("09:05", first.endText)
        val noon = standard.periods[4]
        assertEquals("午间1节", noon.label)
        assertEquals("12:20", noon.startText)
        val last = standard.periods.last()
        assertEquals("第11节", last.label)
        assertEquals("21:35", last.endText)
    }

    @Test
    fun `standard grid cell parsed into five segments`() {
        val entry = standard.gridEntries.single { it.weekday == 1 && it.periodRow == 1 }
        assertEquals("示例课程01", entry.courseName)
        assertEquals("2", entry.sequence)
        assertEquals("示例教室02", entry.room)
        assertEquals("示例教师02", entry.teacher)
        assertEquals("1-16周", entry.weekText)
        assertEquals("讲课学时", entry.typeText)
        assertTrue(entry.extraStatuses.isEmpty())
    }

    @Test
    fun `multiple entries in one cell`() {
        val cell = standard.gridEntries.filter { it.weekday == 5 && it.periodRow == 1 }
        assertEquals(2, cell.size)
        assertEquals("1-16双", cell[0].weekText)
        assertEquals("1-16单", cell[1].weekText)
    }

    @Test
    fun `makeup entries in grid`() {
        val makeups = standard.gridEntries.filter { it.weekday == 5 && it.periodRow == 11 }
        assertEquals(2, makeups.size)
        assertTrue(makeups.all { it.typeText == "补课" })
        assertEquals(listOf("第9周", "第17周"), makeups.map { it.weekText })
    }

    @Test
    fun `combined status tokens parsed`() {
        val entry = changes.gridEntries.single { it.weekday == 6 && it.periodRow == 1 && it.courseName == "示例课程07" }
        assertEquals(setOf("补课", "停课"), entry.statusTokens)
    }

    @Test
    fun `trailing status line after five segments`() {
        val entry = changes.gridEntries.single { it.courseName == "示例课程56" }
        assertEquals("讲课学时", entry.typeText)
        assertEquals(listOf("停课"), entry.extraStatuses)
        assertEquals(setOf("讲课学时", "停课"), entry.statusTokens)
    }

    @Test
    fun `standard change detail is makeup only`() {
        assertEquals(1, standard.changeDetails.size)
        val detail = standard.changeDetails.single()
        assertEquals("补课", detail.typeText.trim())
        assertEquals("QNU0001", detail.courseCode)
        assertEquals("示例变更课程01", detail.courseName)
        assertNull(detail.original)
        val makeup = checkNotNull(detail.makeup)
        assertEquals(17, makeup.week)
        assertEquals(5, makeup.weekday)
        assertEquals(9, makeup.periodStart)
        assertEquals(10, makeup.periodEnd)
        assertEquals("示例教室02", makeup.room)
        assertEquals("12-27", makeup.dateText)
    }

    @Test
    fun `continuation rows inherit course context`() {
        val qnu0002 = changes.changeDetails.filter { it.courseCode == "QNU0002" }
        assertEquals(3, qnu0002.size)
        assertEquals(listOf(false, true, true), qnu0002.map { it.isContinuation })
        assertTrue(qnu0002.all { it.courseName == "示例变更课程02" })
        assertEquals(listOf(7, 8, 9), qnu0002.map { it.makeup?.week })
        assertTrue(qnu0002.all { it.makeup?.room == "示例教室03" })
    }

    @Test
    fun `move detail keeps original and replacement`() {
        val qnu0004 = changes.changeDetails.filter { it.courseCode == "QNU0004" }
        assertEquals(3, qnu0004.size)
        assertTrue(qnu0004.all { it.typeText.trim() == "调课" })
        val first = qnu0004.first()
        assertEquals(15, first.original?.week)
        assertEquals(6, first.original?.weekday)
        assertEquals(1, first.original?.periodStart)
        assertEquals(4, first.original?.periodEnd)
        assertEquals(12, first.makeup?.week)
        assertEquals(5, first.makeup?.periodStart)
        assertEquals(8, first.makeup?.periodEnd)
        assertEquals("示例教室05", first.makeup?.room)
    }

    @Test
    fun `fallback timetable structure uses semantic cell coordinates and document title`() {
        val html = """
            <html>
            <head><title>2025 秋季课程表</title></head>
            <body>
              <table class="schedule">
                <tr><th>节次</th><th>周一</th><th>周二</th></tr>
                <tr>
                  <th>第1节</th>
                  <td>&lt;&lt;兼容课程&gt;&gt;1<br>教学楼A101<br>示例教师<br>1-16周<br>讲课学时</td>
                  <td></td>
                </tr>
              </table>
            </body>
            </html>
        """.trimIndent().toByteArray()

        val parsed = QnuTimetableParser.parse(html)

        assertEquals("2025", parsed.yearText)
        assertEquals("秋", parsed.termText)
        assertTrue(parsed.diagnostics.none { it.severity == DiagnosticSeverity.ERROR })
        val entry = parsed.gridEntries.single()
        assertEquals(1, entry.weekday)
        assertEquals(1, entry.periodRow)
        assertEquals("兼容课程", entry.courseName)
        assertEquals("教学楼A101", entry.room)
    }

    @Test
    fun `fixtures parse without error diagnostics`() {
        assertTrue(standard.diagnostics.none { it.severity == DiagnosticSeverity.ERROR })
        assertTrue(changes.diagnostics.none { it.severity == DiagnosticSeverity.ERROR })
    }

    @Test
    fun `empty input reports error`() {
        val result = QnuTimetableParser.parse(ByteArray(0))
        assertTrue(result.diagnostics.any { it.severity == DiagnosticSeverity.ERROR })
    }

    @Test
    fun `page without timetable reports error not exception`() {
        val result = QnuTimetableParser.parse("<html><body>登录超时</body></html>".toByteArray())
        assertTrue(result.diagnostics.any { it.code == "NO_TIMETABLE" })
        assertTrue(result.gridEntries.isEmpty())
    }

    @Test
    fun `empty timetable grid is rejected instead of clearing existing data`() {
        val html = """
            <html><head><meta charset="GBK"></head><body>
            <div id="title">2026 秋 学生课表</div>
            <table id="timetable"><tr><th></th><th>周一</th></tr></table>
            </body></html>
        """.trimIndent().toByteArray(charset("GBK"))

        val result = QnuTimetableParser.parse(html)

        assertTrue(
            result.diagnostics.any {
                it.code == "EMPTY_GRID" && it.severity == DiagnosticSeverity.ERROR
            },
        )
    }

    @Test
    fun `unicode html from webview parses without treating gbk meta as bytes`() {
        val html = String(fixture("qnu-timetable-standard.gbk.html"), charset("GBK"))
        val page = QnuTimetableParser.parseHtml(html)
        val again = QnuTimetableImporter.importHtml(html, "s1")

        assertTrue(page.gridEntries.any { it.courseName == "示例课程01" })
        assertEquals(standard.gridEntries.size, page.gridEntries.size)
        assertTrue(again.state.meetings.isNotEmpty())
        assertEquals("示例课程01", again.state.courses.first().name)
    }

    @Test
    fun `course arrangement table supplies course metadata`() {
        val html = """
            <html><body>
            <table id="timetable">
              <tr><th>节次</th><th>星期一</th></tr>
              <tr><td>第1节<br>08:20-09:05</td><td></td></tr>
            </table>
            <table>
              <tr><th>课程名</th><th>课程性质</th><th>学分</th><th>选课属性</th><th>考核方式</th></tr>
              <tr><td>高等数学</td><td>数学类</td><td>3.0</td><td>专业必修</td><td>考试</td></tr>
            </table>
            </body></html>
        """.trimIndent()
        val page = QnuTimetableParser.parseHtml(html)
        val profile = page.courseProfiles.single()
        assertEquals("高等数学", profile.courseName)
        assertEquals("数学类", profile.nature)
        assertEquals("3.0", profile.credits)
        assertEquals("专业必修", profile.selectionAttribute)
        assertEquals("考试", profile.assessmentMethod)
    }

    @Test
    fun `course arrangement table with td headers and 课程属性 is parsed without a grid`() {
        val html = """
            <html><body>
            <table>
              <tr><td>课程号</td><td>课序号</td><td>课程名</td><td>课程属性</td><td>学分</td><td>考核方式</td></tr>
              <tr><td>1001</td><td>01</td><td>高等数学</td><td>专业必修</td><td>3.0</td><td>考查</td></tr>
            </table>
            </body></html>
        """.trimIndent()
        val page = QnuTimetableParser.parseHtml(html)
        val profile = page.courseProfiles.single()
        assertEquals("高等数学", profile.courseName)
        assertEquals("专业必修", profile.nature)
        assertEquals("3.0", profile.credits)
        assertEquals("专业必修", profile.selectionAttribute)
        assertEquals("考查", profile.assessmentMethod)
        val matched = QnuTimetableParser.matchCourseProfile("高等数学（一班）", "01", page.courseProfiles)
        assertEquals("专业必修", matched?.nature)
    }

    @Test
    fun `teaching class suffix still matches complete course metadata`() {
        val profiles = listOf(
            QnuCourseProfile(
                courseName = "大学英语A(3)",
                sequence = "2-1",
                credits = "3.0",
                selectionAttribute = "专业必修",
                assessmentMethod = "考试",
            ),
        )

        val matched = QnuTimetableParser.matchCourseProfile("大学英语A(3)-2-1", null, profiles)

        assertEquals("3.0", matched?.credits)
        assertEquals("专业必修", matched?.selectionAttribute)
        assertEquals("考试", matched?.assessmentMethod)
    }
}
