package cn.qztc.timetable.feature.timetable

import org.junit.Assert.*
import org.junit.Test

class CourseSlotSelectionTest {
    @Test fun cancellingMiddlePreservesOtherPeriodsAndOtherDays() {
        val slots = listOf(WeeklySlotDraft(1, 1, 4, listOf(1, 3), "m1"), WeeklySlotDraft(3, 7, 8, listOf(2, 4), "m2"))
        val next = removeCoursePeriod(slots, 1, 2)
        assertEquals(listOf(1..1, 3..4), next.filter { it.weekday == 1 }.map { it.periodStart..it.periodEnd })
        assertEquals(slots[1], next.last())
        assertTrue(next.take(2).all { it.weeks == listOf(1, 3) && it.sourceMeetingId == "m1" })
    }

    @Test fun addingAnotherDayDoesNotMoveExistingDay() {
        val monday = WeeklySlotDraft(1, 1, 2, listOf(1, 3, 5))
        val next = addCoursePeriods(listOf(monday), 4, 11..13, listOf(2, 4, 6))
        assertEquals(monday, next.first())
        assertEquals(WeeklySlotDraft(4, 11, 13, listOf(2, 4, 6)), next.last())
        assertEquals(emptyList<WeeklySlotDraft>(), removeCoursePeriod(listOf(monday.copy(periodEnd = 1)), 1, 1))
    }
}
