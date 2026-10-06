package cn.qztc.timetable.feature.settings

import java.time.LocalDate
import cn.qztc.timetable.core.domain.model.Term
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolCalendarWebFetcherTest {
    @Test
    fun `pdf text keeps both semester ranges in the requested school year`() {
        val result = SchoolCalendarWebFetcher.parseCalendarText(
            text = "2026—2027 学年校历 第一学期（2026.8.31—2027.1.15） 第二学期（2027.2.22—2027.7.2）",
            articleUrl = "https://www.qztc.edu.cn/jwc/2026/0608/c1044a293737/page.htm",
            viewerUrl = "https://www.qztc.edu.cn/_js/_portletPlugs/swfPlayer/pdfjs22228/web/viewer.html?file=calendar.pdf",
            semesterId = "semester",
            requestedSchoolYear = "2026-2027",
        )

        assertEquals("2026-2027", result.schoolYear)
        assertEquals(LocalDate.of(2026, 8, 31), result.firstSemesterStart)
        assertEquals(LocalDate.of(2027, 2, 22), result.secondSemesterStart)
        assertEquals(LocalDate.of(2027, 1, 15), result.firstSemesterEnd)
        assertEquals(LocalDate.of(2027, 7, 2), result.secondSemesterEnd)
        assertTrue(result.days.isEmpty())
    }

    @Test
    fun `pdf event text cannot create a holiday on the semester start date`() {
        val result = SchoolCalendarWebFetcher.parseCalendarText(
            text = "2026—2027 学年校历 第一学期（2026.8.31—2027.1.15）中秋节放假安排另行通知，寒假：1月16日至2月20日。",
            articleUrl = "https://www.qztc.edu.cn/jwc/calendar.htm",
            viewerUrl = "https://www.qztc.edu.cn/pdf/calendar.pdf",
            semesterId = "semester",
            requestedSchoolYear = "2026-2027",
        )

        assertTrue(result.days.isEmpty())
        assertTrue(result.events.any { it.name == "寒假" })
    }

    @Test
    fun `pdf text extracts dated calendar events from both semesters`() {
        val result = SchoolCalendarWebFetcher.parseCalendarText(
            text = """
                2026—2027 学年校历 第一学期（2026.8.31—2027.1.15）
                1.教职工：8 月 29 日报到。2.学生：8 月 29、30 日报到，8 月 31 日正式上课；
                新生：9 月 5 日报到。
                4.校运动会暂定 11 月 4-6 日。6.寒假：1 月 16 日至 2 月 20 日。
                第二学期（2027.2.22—2027.7.2）
                1.教职工：2 月 21 日报到。2.学生：2 月 21 日报到，2 月 22 日正式上课。
                3.公共课、专业课补考 3 月 7 日前完成。5.暑假：7 月 3 日至 8 月 27 日。
            """.trimIndent(),
            articleUrl = "https://www.qztc.edu.cn/jwc/2026/0608/c1044a293737/page.htm",
            viewerUrl = "https://www.qztc.edu.cn/pdf/calendar.pdf",
            semesterId = "semester",
            requestedSchoolYear = "2026-2027",
        )

        assertEquals(LocalDate.of(2027, 1, 16), result.events.first { it.name == "寒假" }.startDate)
        assertEquals(LocalDate.of(2027, 2, 20), result.events.first { it.name == "寒假" }.endDate)
        assertEquals(LocalDate.of(2026, 11, 4), result.events.first { it.name == "校运动会" }.startDate)
        assertEquals(LocalDate.of(2026, 11, 6), result.events.first { it.name == "校运动会" }.endDate)
        assertTrue(result.events.any {
            it.name == "学生报到" && it.startDate == LocalDate.of(2026, 8, 29) &&
                it.endDate == LocalDate.of(2026, 8, 30)
        })
        assertTrue(result.events.any { it.name == "教职工报到" && it.semesterLabel == "第二学期" && it.startDate == LocalDate.of(2027, 2, 21) })
        assertTrue(result.events.any { it.name == "新生报到" && it.startDate == LocalDate.of(2026, 9, 5) })
        assertEquals(LocalDate.of(2027, 2, 22), result.events.first { it.name == "正式上课" && it.semesterLabel == "第二学期" }.startDate)
        assertTrue(result.events.any { it.name == "暑假" && it.startDate == LocalDate.of(2027, 7, 3) && it.endDate == LocalDate.of(2027, 8, 27) })
        assertTrue(result.events.any { it.name == "公共课、专业课补考截止" && it.startDate == LocalDate.of(2027, 3, 7) })
    }

    @Test
    fun `website result cannot silently switch to another school year`() {
        val result = SchoolCalendarWebFetcher.parseCalendarText(
            text = "2025—2026 学年校历 第一学期（2025.9.1—2026.1.17）",
            articleUrl = "https://www.qztc.edu.cn/jwc/2025/0605/c1044a285630/page.htm",
            viewerUrl = "https://www.qztc.edu.cn/pdf/old.pdf",
            semesterId = "semester",
            requestedSchoolYear = "2026-2027",
        )

        assertEquals("2025-2026", result.schoolYear)
        assertEquals(LocalDate.of(2025, 9, 1), result.firstSemesterStart)
    }

    @Test
    fun `official ranges identify the current semester without month guessing`() {
        val result = SchoolCalendarWebFetcher.parseCalendarText(
            text = "2026—2027 学年校历 第一学期（2026.8.31—2027.1.15） 第二学期（2027.2.22—2027.7.2）",
            articleUrl = "https://www.qztc.edu.cn/jwc/2026/0608/c1044a293737/page.htm",
            viewerUrl = "https://www.qztc.edu.cn/pdf/calendar.pdf",
            semesterId = "semester",
            requestedSchoolYear = "2026-2027",
        )

        assertEquals(Term.AUTUMN, result.semesterFor(LocalDate.of(2026, 9, 15))?.term)
        assertEquals("第一学期", result.semesterFor(LocalDate.of(2027, 1, 10))?.label)
        assertEquals(Term.SPRING, result.semesterFor(LocalDate.of(2027, 3, 1))?.term)
        assertEquals("2026-2027 第二学期（2027年2月22日 至 2027年7月2日）",
            result.semesterFor(LocalDate.of(2027, 3, 1))?.displayLabel())
    }
}
