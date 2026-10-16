package cn.qztc.timetable.education

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class AcademicCalendarExportTest {
    @Test fun preservesShanghaiExamTimeAndAllDayEvents() {
        val date = LocalDate.of(2026, 12, 12)
        val ics = academicIcs(listOf(
            AcademicCalendarEntry("cet4", "四级", date, LocalTime.of(9, 0), "考试", false),
            AcademicCalendarEntry("holiday", "假期", date, null, "备注;含,标点\n第二行", false),
        ))
        assertTrue(ics.contains("DTSTART:20261212T010000Z\r\n"))
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20261212\r\n"))
        assertTrue(ics.contains("DESCRIPTION:备注\\;含\\,标点\\n第二行\r\n"))
        assertEquals(2, Regex("DTSTAMP:").findAll(ics).count())
    }
}
