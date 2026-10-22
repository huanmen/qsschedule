package cn.qztc.timetable.integration.qnu

import cn.qztc.timetable.core.domain.imports.DiagnosticSeverity
import cn.qztc.timetable.core.domain.model.ExceptionType
import cn.qztc.timetable.core.domain.model.OccurrenceState
import cn.qztc.timetable.core.domain.model.Semester
import cn.qztc.timetable.core.domain.model.SourceType
import cn.qztc.timetable.core.domain.model.Term
import cn.qztc.timetable.core.domain.schedule.OccurrenceExpander
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QnuTimetableImporterTest {
    @Test fun `notification import ignores grid and handles no notifications`() {
        val html = String(changesBytes, charset("GBK"))
        val result = QnuTimetableImporter.importChangesHtml(html, "s1")
        assertTrue(result.state.meetings.isEmpty())
        // This fixture has cancellation badges only in the grid. They must be ignored.
        assertTrue(result.state.exceptions.none { it.type == ExceptionType.CANCEL })
        assertTrue(result.state.exceptions.any { it.type == ExceptionType.MOVE })
        assertTrue(!result.hasErrors)
        val cancellation = QnuTimetableImporter.importChangesHtml("""
            <div id="title">2026 秋 学生课表</div><table id="timetable"></table>
            <table><tr><th>调停课通知</th></tr><tr>
            <td>停课</td><td>TEST01</td><td>测试课程</td><td>1</td><td>教师</td><td></td><td>2</td>
            <td>10-12</td><td>7</td><td>一</td><td>1-2</td><td>A</td>
            <td></td><td></td><td></td><td></td><td></td></tr></table>
        """.trimIndent(), "s1")
        assertTrue(!cancellation.hasErrors)
        assertEquals(ExceptionType.CANCEL, cancellation.state.exceptions.single().type)
        assertEquals(7, cancellation.state.exceptions.single().original?.week)
        assertTrue(cancellation.state.meetings.isEmpty())
        val empty = QnuTimetableImporter.importChangesHtml("<div id='title'>2026 秋 学生课表</div><table id='timetable'><td id='1-1'>bad grid</td></table>", "s1")
        assertTrue(!empty.hasErrors)
        assertTrue(empty.state.exceptions.isEmpty())
        assertTrue(empty.state.courses.isEmpty())
    }
    @Test fun `ambiguous cancellation in main type never cancels a whole term`() {
        val page=QnuParsedPage(gridEntries=listOf(QnuGridEntry(1,1,"测试课","1","A","教师","1-16周","停课")))
        val result=QnuTimetableImporter.map(page)
        assertEquals(16,result.state.meetings.single().weekRule.weeks.size)
        assertTrue(result.state.exceptions.isEmpty())
        assertTrue(result.diagnostics.any { it.code=="CANCEL_SCOPE_UNCONFIRMED" })
        assertTrue(result.diagnostics.any { it.code=="CANCEL_SCOPE_UNCONFIRMED" && it.message.contains("测试课") })
    }
    @Test fun `cell cancellation with dated detail cancels only that week`() {
        val page=QnuParsedPage(gridEntries=listOf(QnuGridEntry(1,1,"测试课程","1","教室","教师","1-16周","讲课学时",listOf("停课"))),
            changeDetails=listOf(QnuChangeDetail("停课",null,"测试课程","1",null,null,null,
                QnuTimePlace(null,3,1,1,1,null),null,false)))
        val imported=QnuTimetableImporter.map(page,"test")
        val cancellations=imported.state.exceptions.filter { it.type==ExceptionType.CANCEL }
        assertEquals(1,cancellations.size)
        assertEquals(3,cancellations.single().original!!.week)
        assertEquals(16,imported.state.meetings.single().weekRule.weeks.size)
    }

    @Test fun `afternoon cancellation detail maps around internal lunch slots`() {
        val page = QnuParsedPage(
            gridEntries = listOf(
                QnuGridEntry(5, 7, "下午课程", "1", "教室", "教师", "1-16周", "讲课学时"),
                QnuGridEntry(5, 8, "下午课程", "1", "教室", "教师", "1-16周", "讲课学时"),
            ),
            changeDetails = listOf(
                QnuChangeDetail(
                    "停课", null, "下午课程", "1", null, null, null,
                    QnuTimePlace(null, 2, 5, 5, 6, null), null, false,
                ),
            ),
        )
        val imported = QnuTimetableImporter.map(page, "test")
        val cancellation = imported.state.exceptions.single()
        assertEquals(7, cancellation.original?.periodStart)
        assertEquals(8, cancellation.original?.periodEnd)

        val semester = Semester("test", "测试学期", 2025, Term.AUTUMN, LocalDate.of(2025, 9, 1), 16)
        val occurrence = OccurrenceExpander.expand(semester, imported.state, 2).single()
        assertEquals(OccurrenceState.CANCELLED, occurrence.state)
    }

    @Test
    fun `change detail course code binds truncated name to grid course`() {
        val page = QnuParsedPage(
            gridEntries = listOf(
                QnuGridEntry(1, 1, "形势与政策（专题一）", "01", "教室", "教师", "1-17周", "讲课学时", listOf("停课")),
            ),
            courseProfiles = listOf(
                QnuCourseProfile("形势与政策（专题一）", "01", "XSC001"),
            ),
            changeDetails = listOf(
                QnuChangeDetail(
                    "调课", "XSC001", "形势与政策", "1", null, null, null,
                    QnuTimePlace(null, 16, 1, 1, 1, "教室"),
                    QnuTimePlace(null, 16, 3, 3, 3, "新教室"), false,
                ),
            ),
        )
        val imported = QnuTimetableImporter.map(page, "test")
        assertEquals(1, imported.state.courses.size)
        assertEquals(1, imported.state.exceptions.size)
        val exception = imported.state.exceptions.single()
        assertEquals(imported.state.courses.single().id, exception.courseId)
        assertTrue(imported.diagnostics.none { it.code == "CANCEL_SCOPE_UNCONFIRMED" })
        val week = OccurrenceExpander.expand(
            Semester("test", "测试", 2025, Term.AUTUMN, LocalDate.of(2025, 9, 1), 17),
            imported.state,
            16,
        )
        assertTrue(week.none { it.weekday == 1 && it.state == OccurrenceState.NORMAL })
        assertTrue(week.any { it.weekday == 3 && it.state == OccurrenceState.MOVED_IN })
    }

    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.classLoader.getResourceAsStream("fixtures/$name")) {
            "missing fixture $name"
        }.readBytes()

    private val standardBytes by lazy { fixture("qnu-timetable-standard.gbk.html") }
    private val changesBytes by lazy { fixture("qnu-timetable-changes.gbk.html") }
    private val standard by lazy { QnuTimetableImporter.import(standardBytes, "s1") }
    private val changes by lazy { QnuTimetableImporter.import(changesBytes, "s1") }

    @Test
    fun `semester hint recognized`() {
        assertEquals(2024, standard.semesterHint?.year)
        assertEquals(Term.AUTUMN, standard.semesterHint?.term)
        assertEquals(2023, changes.semesterHint?.year)
    }

    @Test
    fun `standard fixture produces stable non-empty state`() {
        assertTrue(standard.state.courses.isNotEmpty())
        assertTrue(standard.state.meetings.isNotEmpty())
        assertTrue(standard.state.courses.all { it.sourceType == SourceType.QNU_EDU })
        assertTrue(standard.state.courses.all { it.semesterId == "s1" })

        // 同一字节重复导入必须得到完全一致的状态（确定性 ID）。
        val again = QnuTimetableImporter.import(standardBytes, "s1")
        assertEquals(standard.state, again.state)
    }

    @Test
    fun `week rules resolved to concrete weeks`() {
        val evenMeeting = standard.state.meetings.single {
            it.courseId == "qnu:s1:示例课程04#5"
        }
        assertEquals(listOf(2, 4, 6, 8, 10, 12, 14, 16), evenMeeting.weekRule.weeks)

        val singleWeek = standard.state.meetings.single { it.courseId == "qnu:s1:示例课程24#7" }
        assertEquals(listOf(7), singleWeek.weekRule.weeks)
    }

    @Test
    fun `consecutive periods of the same course are merged`() {
        val html = """
            <html><head><meta http-equiv="Content-Type" content="text/html; charset=gbk"/></head><body>
            <div id="title">2024 秋 学生课表</div>
            <table id="timetable" class="infolist_hr"><tbody>
            <tr><th></th><th>周一</th></tr>
            <tr><th>第1节<br/>08:20<br/>┆<br/>09:05</th>
            <td id="1-1">&lt;&lt;连堂课&gt;&gt;;1<br/>教室A<br/>教师A<br/>1-16周<br/>讲课学时</td></tr>
            <tr><th>第2节<br/>09:15<br/>┆<br/>10:00</th>
            <td id="1-2">&lt;&lt;连堂课&gt;&gt;;1<br/>教室A<br/>教师A<br/>1-16周<br/>讲课学时</td></tr>
            </tbody></table></body></html>
        """.trimIndent().toByteArray(charset("GBK"))
        val result = QnuTimetableImporter.import(html)
        val meeting = result.state.meetings.single()
        assertEquals(1, meeting.periodStart)
        assertEquals(2, meeting.periodEnd)
        assertEquals("连堂课", result.state.courses.single().name)
    }

    @Test
    fun `regular meeting mapped with teacher and room`() {
        val meeting = standard.state.meetings.single { it.courseId == "qnu:s1:示例课程01#2" }
        assertEquals(1, meeting.weekday)
        assertEquals(1, meeting.periodStart)
        assertEquals("示例教师02", meeting.teacher)
        assertEquals("示例教室02", meeting.room)
    }

    @Test
    fun `grid makeup becomes exception not meeting`() {
        val makeupCourse = standard.state.courses.single { it.name == "示例课程35" }
        assertTrue(standard.state.meetings.none { it.courseId == makeupCourse.id })
        val exception = standard.state.exceptions.single { it.courseId == makeupCourse.id }
        assertEquals(ExceptionType.MAKEUP, exception.type)
        assertEquals(9, exception.replacement?.week)
        assertEquals(5, exception.replacement?.weekday)
        assertEquals(11, exception.replacement?.periodStart)
        assertEquals("示例教师04", exception.replacement?.teacher)
    }

    @Test
    fun `detail makeup mapped with course code`() {
        val course = standard.state.courses.single { it.code == "QNU0001" }
        assertEquals("示例变更课程01", course.name)
        val exception = standard.state.exceptions.single { it.courseId == course.id }
        assertEquals(ExceptionType.MAKEUP, exception.type)
        assertEquals(17, exception.replacement?.week)
        assertEquals(11, exception.replacement?.periodStart)
        assertEquals(12, exception.replacement?.periodEnd)
        assertEquals("12-27", exception.replacement?.dateText)
    }

    @Test
    fun `continuation rows produce one exception each`() {
        val course = changes.state.courses.single { it.code == "QNU0002" }
        val makeups = changes.state.exceptions.filter { it.courseId == course.id }
        assertEquals(3, makeups.size)
        assertEquals(listOf(7, 8, 9), makeups.mapNotNull { it.replacement?.week }.sorted())
    }

    @Test
    fun `move detail becomes move exception`() {
        val course = changes.state.courses.single { it.code == "QNU0004" }
        val moves = changes.state.exceptions.filter { it.courseId == course.id }
        assertEquals(3, moves.size)
        assertTrue(moves.all { it.type == ExceptionType.MOVE })
        val week15 = moves.single { it.original?.week == 15 }
        assertEquals(1, week15.original?.periodStart)
        assertEquals(4, week15.original?.periodEnd)
        assertEquals(12, week15.replacement?.week)
        assertEquals(7, week15.replacement?.periodStart)
        assertEquals("示例教室05", week15.replacement?.room)
    }

    @Test
    fun `lecture with cancel status keeps meeting and adds cancel`() {
        val course = changes.state.courses.single { it.name == "示例课程56" }
        val meeting = changes.state.meetings.single { it.courseId == course.id }
        assertEquals(listOf(11), meeting.weekRule.weeks)
        val cancel = changes.state.exceptions.single { it.courseId == course.id }
        assertEquals(ExceptionType.CANCEL, cancel.type)
        assertEquals(11, cancel.original?.week)
    }

    @Test
    fun `makeup with cancel status produces both exceptions`() {
        val course = changes.state.courses.single { it.name == "示例课程07" }
        val exceptions = changes.state.exceptions.filter { it.courseId == course.id }
        assertEquals(setOf(ExceptionType.MAKEUP, ExceptionType.CANCEL), exceptions.map { it.type }.toSet())
        assertTrue(exceptions.all { (it.original ?: it.replacement)?.week == 15 })
    }

    @Test
    fun `fixtures import without error diagnostics`() {
        assertTrue(standard.diagnostics.none { it.severity == DiagnosticSeverity.ERROR })
        assertTrue(changes.diagnostics.none { it.severity == DiagnosticSeverity.ERROR })
        assertTrue(!standard.hasErrors && !changes.hasErrors)
    }

    @Test
    fun `periods carried for scheme creation`() {
        assertEquals(13, standard.periods.size)
        assertEquals("第1节", standard.periods.first().label)
        assertEquals("08:20", standard.periods.first().startText)
    }

    @Test
    fun `course identifiers are isolated between semesters`() {
        val first = QnuTimetableImporter.import(standardBytes, "qnu-2024-autumn")
        val second = QnuTimetableImporter.import(standardBytes, "qnu-2025-autumn")

        assertTrue(first.state.courses.map { it.id }.toSet().intersect(second.state.courses.map { it.id }.toSet()).isEmpty())
    }

    @Test
    fun `arrangement metadata is applied to imported courses`() {
        val timetable = """
            <html><body>
            <table id="timetable">
              <tr><th>节次</th><th>星期一</th></tr>
              <tr><th>第1节</th><td>&lt;&lt;高等数学&gt;&gt;;01<br/>教室A<br/>教师A<br/>1-16周<br/>讲课学时</td></tr>
            </table>
            <table>
              <tr><th>课程号</th><th>课序号</th><th>课程名</th><th>课程性质</th><th>学分</th><th>选课属性</th><th>考核方式</th></tr>
              <tr><td>1001</td><td>01</td><td>高等数学</td><td>数学类</td><td>3.0</td><td>专业必修</td><td>考试</td></tr>
            </table>
            </body></html>
        """.trimIndent()
        val imported = QnuTimetableImporter.importHtml(timetable, "s1")
        val course = imported.state.courses.single()
        assertEquals("数学类", course.nature)
        assertEquals("3.0", course.credits)
        assertEquals("专业必修", course.selectionAttribute)
        assertEquals("考试", course.assessmentMethod)
    }

    @Test
    fun `unknown week text becomes error diagnostic and entry skipped`() {
        val html = """
            <html><head><meta http-equiv="Content-Type" content="text/html; charset=gbk"/></head><body>
            <div id="title">2024 秋 学生课表</div>
            <table id="timetable" class="infolist_hr"><tbody>
            <tr><th></th><th>周一</th></tr>
            <tr><th>第1节<br/>08:20<br/>┆<br/>09:05</th>
            <td id="1-1">&lt;&lt;某课程&gt;&gt;;1<br/>教室X<br/>教师X<br/>未知周次格式<br/>讲课学时</td></tr>
            </tbody></table></body></html>
        """.trimIndent().toByteArray(charset("GBK"))
        val result = QnuTimetableImporter.import(html)
        assertTrue(result.diagnostics.any { it.code == "WEEK_TEXT_UNRECOGNIZED" && it.severity == DiagnosticSeverity.ERROR })
        assertTrue(result.state.meetings.isEmpty())
    }

    @Test
    fun `broken page yields error state not exception`() {
        val result = QnuTimetableImporter.import("完全不是课表页面".toByteArray(charset("GBK")))
        assertTrue(result.hasErrors)
        assertTrue(result.state.isEmpty)
    }
}
