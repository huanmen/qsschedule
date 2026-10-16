package cn.qztc.timetable.feature.settings

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarDefaultInclusionTest {
    private val today = LocalDate.of(2026, 9, 29)
    private fun event(type: SchoolCalendarEventType, name: String) = SchoolCalendarEvent(
        id = name,
        schoolYear = "2026-2027",
        semesterLabel = "2026 秋",
        type = type,
        name = name,
        startDate = today,
    )

    @Test fun timetableOnlyIncludesBreakBoundariesAndSports() {
        assertFalse(CalendarDefaultInclusion.includes(event(SchoolCalendarEventType.TEACHING_START, "正式上课"), 2026, today))
        assertFalse(CalendarDefaultInclusion.includes(event(SchoolCalendarEventType.REPORTING, "新生报到"), 2026, today))
        assertFalse(CalendarDefaultInclusion.includes(event(SchoolCalendarEventType.REPORTING, "学生报到"), 2026, today))
        assertFalse(CalendarDefaultInclusion.includes(event(SchoolCalendarEventType.REPORTING, "学生报到"), 2025, today))
        assertFalse(CalendarDefaultInclusion.includes(event(SchoolCalendarEventType.REPORTING, "新生报到"), 2025, today))
        assertTrue(CalendarDefaultInclusion.includes(event(SchoolCalendarEventType.BREAK, "寒假"), 2026, today))
        assertTrue(CalendarDefaultInclusion.includes(event(SchoolCalendarEventType.SPORTS_MEETING, "校运动会"), 2026, today))
        assertFalse(CalendarDefaultInclusion.includes(event(SchoolCalendarEventType.EXAM, "补考"), 2026, today))
    }
}
