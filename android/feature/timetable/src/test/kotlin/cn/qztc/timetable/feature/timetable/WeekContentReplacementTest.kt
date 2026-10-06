package cn.qztc.timetable.feature.timetable

import cn.qztc.timetable.core.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class WeekContentReplacementTest {
    private fun occurrence(id: String, start: Int, end: Int) = DemoOccurrence(
        Course(id, "s", SourceType.MANUAL, id, name = id), 0, 3, start, end, "room", "teacher")

    @Test fun changedCellReplacesOldTeacherAndRoomWithoutPaintingBoth() {
        val old = occurrence("lab", 7, 10)
        val moved = old.copy(periodStart = 7, periodEnd = 9, layoutPeriodStart = 7, layoutPeriodEnd = 9,
            teacher = "new teacher", room = "new room", moved = true, changed = true)
        val rendered = replaceWeekCellContents(listOf(old, moved))
        assertEquals(listOf(10), rendered.filter { !it.changed }.map { it.periodStart })
        assertEquals(listOf("new teacher"), rendered.filter { 8 in it.periodStart..it.periodEnd }.map { it.teacher })
    }

    @Test fun activeClassReplacesCancelledIntersectionAndKeepsUnoccupiedCancelledPortions() {
        val old = occurrence("cancelled", 1, 4).copy(cancelled = true)
        val live = occurrence("replacement", 2, 3)
        val rendered = replaceWeekCellContents(listOf(old, live))
        assertEquals(listOf(1..1, 4..4), rendered.filter { it.cancelled }.map { it.periodStart..it.periodEnd })
        assertEquals(1, rendered.count { 2 in it.periodStart..it.periodEnd })
        assertEquals(listOf(old), replaceWeekCellContents(listOf(old)))
    }
}
