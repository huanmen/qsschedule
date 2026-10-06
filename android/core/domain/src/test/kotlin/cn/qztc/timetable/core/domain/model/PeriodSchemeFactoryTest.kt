package cn.qztc.timetable.core.domain.model

import cn.qztc.timetable.core.domain.imports.ImportedPeriod
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class PeriodSchemeFactoryTest {

    @Test
    fun `defaults used when import has no periods`() {
        val scheme = PeriodSchemeFactory.forSemester("s1")
        assertEquals("s1:periods", scheme.id)
        assertEquals(13, scheme.periods.size)
        assertEquals(LocalTime.of(8, 20), scheme.periods.first().startTime)
        assertEquals(DaySegment.NOON, scheme.periods[4].segment)
        assertEquals(LocalTime.of(20, 50), scheme.periods.last().startTime)
    }

    @Test
    fun `imported labels and times override defaults`() {
        val scheme = PeriodSchemeFactory.forSemester(
            semesterId = "s1",
            imported = listOf(
                ImportedPeriod(1, "第一节", "08:30", "09:10"),
                ImportedPeriod(5, "午间课", "12:00", "12:45"),
            ),
        )
        assertEquals("第一节", scheme.periods[0].label)
        assertEquals(LocalTime.of(8, 30), scheme.periods[0].startTime)
        assertEquals(LocalTime.of(9, 10), scheme.periods[0].endTime)
        assertEquals("午间课", scheme.periods[4].label)
        assertEquals(LocalTime.of(12, 0), scheme.periods[4].startTime)
        assertEquals("第2节", scheme.periods[1].label)
    }
}
