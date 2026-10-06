package cn.qztc.timetable.core.domain.schedule

import cn.qztc.timetable.core.domain.model.ActivityKind
import cn.qztc.timetable.core.domain.model.ScheduleActivity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityWeekLayoutTest {

    private val monday = LocalDate.of(2026, 9, 7)

    @Test
    fun `period activity occupies consecutive course cells on one weekday`() {
        val activity = periodActivity(
            weekday = 2,
            periodStart = 3,
            periodEnd = 4,
            weeks = listOf(2, 3),
        )
        val week2 = ActivityWeekLayout.blocksForWeek(
            activities = listOf(activity),
            week = 2,
            visibleDays = (1..7).toList(),
            dateOf = ::dateOf,
        )
        val week1 = ActivityWeekLayout.blocksForWeek(
            activities = listOf(activity),
            week = 1,
            visibleDays = (1..7).toList(),
            dateOf = ::dateOf,
        )

        assertEquals(1, week2.size)
        assertEquals(2, week2.single().startWeekday)
        assertEquals(2, week2.single().endWeekday)
        assertEquals(3, week2.single().periodStart)
        assertEquals(4, week2.single().periodEnd)
        assertTrue(week1.isEmpty())
    }

    @Test
    fun `day activity splits around lunch and evening rest bands`() {
        val activity = dayActivity(
            start = LocalDate.of(2026, 9, 8),
            end = LocalDate.of(2026, 9, 10),
        )
        val withoutLunch = ActivityWeekLayout.blocksForWeek(
            activities = listOf(activity),
            week = 1,
            visibleDays = (1..5).toList(),
            dateOf = ::dateOf,
            includeLunchPeriods = false,
        )
        val withLunch = ActivityWeekLayout.blocksForWeek(
            activities = listOf(activity),
            week = 1,
            visibleDays = (1..5).toList(),
            dateOf = ::dateOf,
            includeLunchPeriods = true,
        )

        assertEquals(listOf(2 to 4), withoutLunch.map { it.startWeekday to it.endWeekday }.distinct())
        assertEquals(listOf(1 to 4, 7 to 10, 11 to 13), withoutLunch.map { it.periodStart to it.periodEnd }.distinct())
        assertEquals(listOf(1 to 4, 5 to 6, 7 to 10, 11 to 13), withLunch.map { it.periodStart to it.periodEnd }.distinct())
    }

    @Test
    fun `period activity spanning lunch and evening is split into grid bands`() {
        val activity = periodActivity(weekday = 1, periodStart = 4, periodEnd = 12, weeks = listOf(1))
        val blocks = ActivityWeekLayout.blocksForWeek(
            activities = listOf(activity),
            week = 1,
            visibleDays = (1..5).toList(),
            dateOf = ::dateOf,
            includeLunchPeriods = true,
        )
        assertEquals(listOf(4 to 4, 5 to 6, 7 to 10, 11 to 12), blocks.map { it.periodStart to it.periodEnd })
    }

    @Test
    fun `hidden weekend splits a day activity into weekday ranges`() {
        val activity = dayActivity(
            start = LocalDate.of(2026, 9, 7),
            end = LocalDate.of(2026, 9, 13),
        )
        val visible = listOf(1, 2, 3, 4, 5)
        val blocks = ActivityWeekLayout.blocksForWeek(
            activities = listOf(activity),
            week = 1,
            visibleDays = visible,
            dateOf = ::dateOf,
            includeLunchPeriods = false,
        )

        assertEquals(listOf(1 to 5), blocks.map { it.startWeekday to it.endWeekday }.distinct())
        assertEquals(listOf(1 to 2, 5 to 5), ActivityWeekLayout.contiguousRanges(visible, listOf(1, 2, 5)))
    }

    @Test
    fun `splitAroundBreaks skips folded lunch band`() {
        assertEquals(
            listOf(1..4, 7..10, 11..13),
            ActivityWeekLayout.splitAroundBreaks(1, 13, includeLunch = false),
        )
        assertEquals(
            listOf(1..4, 5..6, 7..10, 11..13),
            ActivityWeekLayout.splitAroundBreaks(1, 13, includeLunch = true),
        )
    }

    private fun dateOf(weekday: Int): LocalDate = monday.plusDays((weekday - 1).toLong())

    @Test fun `migrated consecutive dates retain legacy layout without bridging gaps`() {
        val old = dayActivity(dateOf(2), dateOf(4))
        val migrated = old.copy(sessions = old.effectiveSessions())
        fun layout(activity: ScheduleActivity) = ActivityWeekLayout.blocksForWeek(listOf(activity), 1, (1..7).toList(), ::dateOf, false)
        val expected = layout(old).map { listOf(it.startWeekday, it.endWeekday, it.periodStart, it.periodEnd) }
        assertEquals(expected, layout(migrated).map { listOf(it.startWeekday, it.endWeekday, it.periodStart, it.periodEnd) })
        val gap = migrated.copy(sessions = migrated.sessions.filter { it.date != dateOf(3) })
        assertEquals(listOf(2 to 2, 4 to 4), layout(gap).map { it.startWeekday to it.endWeekday }.distinct())
    }

    @Test fun `timed date sessions keep separate morning and afternoon blocks`() {
        val periods = cn.qztc.timetable.core.domain.model.PeriodSchemeFactory.forSemester("sem").periods
        val sessions = (2..3).flatMap { day -> listOf(
            cn.qztc.timetable.core.domain.model.ActivitySession("morning-$day", ActivityKind.DAY, date = dateOf(day), startMinute = 480, endMinute = 570),
            cn.qztc.timetable.core.domain.model.ActivitySession("afternoon-$day", ActivityKind.DAY, date = dateOf(day), startMinute = 840, endMinute = 930)) }
        val activity = dayActivity(dateOf(2),dateOf(3)).copy(sessions = sessions)
        val blocks = ActivityWeekLayout.blocksForWeek(listOf(activity),1,(1..7).toList(),::dateOf,false,periods)
        assertTrue(blocks.all { it.startWeekday == 2 && it.endWeekday == 3 })
        assertTrue(blocks.none { it.periodStart == 1 && it.periodEnd == 13 })
        assertTrue(blocks.any { it.periodEnd < 7 })
        assertTrue(blocks.any { it.periodStart >= 7 })
    }

    private fun periodActivity(
        weekday: Int,
        periodStart: Int,
        periodEnd: Int,
        weeks: List<Int>,
    ) = ScheduleActivity(
        id = "period",
        semesterId = "sem",
        name = "讲座",
        kind = ActivityKind.PERIOD,
        weekday = weekday,
        periodStart = periodStart,
        periodEnd = periodEnd,
        weeks = weeks,
    )

    private fun dayActivity(start: LocalDate, end: LocalDate) = ScheduleActivity(
        id = "day",
        semesterId = "sem",
        name = "运动会",
        kind = ActivityKind.DAY,
        startDate = start,
        endDate = end,
    )
}
