package cn.qztc.timetable.core.domain.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PublicHolidayEventParserTest {
    @Test fun `distinguishes time off adjustment from makeup work`() {
        assertEquals(AcademicDayKind.HOLIDAY, PublicHolidayEventParser.classify("国庆节放假调休"))
        assertEquals(AcademicDayKind.HOLIDAY, PublicHolidayEventParser.classify("春节（休）"))
        assertEquals(AcademicDayKind.HOLIDAY, PublicHolidayEventParser.classify("不上班"))
        assertEquals(AcademicDayKind.MAKEUP, PublicHolidayEventParser.classify("班", "中国节假日"))
        assertEquals(AcademicDayKind.MAKEUP, PublicHolidayEventParser.classify("国庆节（班）"))
        assertEquals(AcademicDayKind.MAKEUP, PublicHolidayEventParser.classify("Workday", "Chinese holidays"))
    }
    @Test fun `uses only explicit teaching weekday and never date weekday`() {
        assertEquals(4, PublicHolidayEventParser.sourceWeekday("国庆调休，补周四的课"))
        assertEquals(1, PublicHolidayEventParser.sourceWeekday("调休上星期一课"))
        assertNull(PublicHolidayEventParser.sourceWeekday("10月10日（周六）调休上班"))
    }
    @Test
    fun `classifies national holidays and makeup days from titles`() {
        assertEquals(AcademicDayKind.HOLIDAY, PublicHolidayEventParser.classify("国庆节"))
        assertEquals(AcademicDayKind.HOLIDAY, PublicHolidayEventParser.classify("中秋放假"))
        assertEquals(AcademicDayKind.MAKEUP, PublicHolidayEventParser.classify("国庆调休"))
        assertEquals(AcademicDayKind.MAKEUP, PublicHolidayEventParser.classify("补班"))
    }

    @Test
    fun `accepts unnamed all-day events on a holiday calendar`() {
        assertEquals(
            AcademicDayKind.HOLIDAY,
            PublicHolidayEventParser.classify("休", "中国节假日"),
        )
        assertEquals(
            AcademicDayKind.HOLIDAY,
            PublicHolidayEventParser.classify("Holiday", "Holidays in China"),
        )
    }

    @Test
    fun `ignores personal calendar noise`() {
        assertNull(PublicHolidayEventParser.classify("妈妈生日"))
        assertNull(PublicHolidayEventParser.classify("项目会议", "节假日"))
        assertNull(PublicHolidayEventParser.classify("加班", "节假日"))
    }
}
