package cn.qztc.timetable.feature.timetable

import cn.qztc.timetable.core.domain.model.Course
import cn.qztc.timetable.core.domain.model.SourceType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekLayoutTest {
    @Test
    fun `month title uses compact year-month`() {
        val currentWeek = listOf(
            LocalDate.of(2026, 9, 7),
            LocalDate.of(2026, 9, 13),
        )
        val historicWeek = listOf(
            LocalDate.of(2025, 9, 1),
            LocalDate.of(2025, 9, 7),
        )
        val spanning = listOf(
            LocalDate.of(2025, 12, 29),
            LocalDate.of(2026, 1, 4),
        )

        assertEquals("26.9", weekMonthTitle(currentWeek))
        assertEquals("25.9", weekMonthTitle(historicWeek))
        assertEquals("25.12", weekMonthTitle(spanning))
        assertEquals("1", scheduleDayNumber(LocalDate.of(2025, 9, 1)))
    }

    @Test
    fun `week header marks the first date of a new month`() {
        val septemberWeekStart = LocalDate.of(2026, 9, 28)
        assertEquals("30", scheduleDayLabel(LocalDate.of(2026, 9, 30), septemberWeekStart))
        assertEquals("10.1", scheduleDayLabel(LocalDate.of(2026, 10, 1), septemberWeekStart))
        assertEquals("10.2", scheduleDayLabel(LocalDate.of(2026, 10, 2), septemberWeekStart))
        assertEquals("1.1", scheduleDayLabel(LocalDate.of(2027, 1, 1), LocalDate.of(2026, 12, 28)))
    }

    @Test
    fun `period height follows the densest course instead of a fixed stretched row`() {
        val short = course("短", "A", "一")
        val dense = course("信息安全技术与管理实践", "朝阳楼510室", "张老师")
        val shortOnly = weekPeriodHeightDp(listOf(short), unitWidthDp = 42f, visibleDays = (1..7).toList(), compactWeekend = false)
        val wide = weekPeriodHeightDp(listOf(dense), unitWidthDp = 88f, visibleDays = (1..5).toList(), compactWeekend = false)
        val narrow = weekPeriodHeightDp(listOf(dense), unitWidthDp = 42f, visibleDays = (1..7).toList(), compactWeekend = false)
        val mixed = weekPeriodHeightDp(listOf(short, dense), unitWidthDp = 42f, visibleDays = (1..7).toList(), compactWeekend = false)

        assertTrue("short courses should not inherit the old stretched weekend row", shortOnly < 64f)
        assertTrue(narrow > wide)
        assertEquals(narrow, mixed, 0.001f)
        assertTrue(wide >= weekMinPeriodHeightDp)
    }

    @Test
    fun `fitted period height fills the remaining space so eleven rows sit above the dock`() {
        val fitted = weekFittedPeriodHeightDp(
            availableHeightDp = 11 * 42f + weekLunchFoldedHeightDp + weekEveningBreakHeightDp + 12 * weekGridGapDp,
            hasLunchClass = false,
        )
        assertEquals(42f, fitted, 0.01f)
        val roomy = weekFittedPeriodHeightDp(
            availableHeightDp = 11 * 52f + weekLunchFoldedHeightDp + weekEveningBreakHeightDp + 12 * weekGridGapDp,
            hasLunchClass = false,
        )
        assertEquals(52f, roomy, 0.01f)
    }

    @Test
    fun `evening rows start after morning noon afternoon and the evening break`() {
        val period = 40f
        val lunch = weekLunchFoldedHeightDp
        val evening = weekEveningBreakHeightDp
        val gap = weekGridGapDp
        val period11 = weekGridRowOffsetDp(
            periodNo = 11,
            periodHeight = period,
            lunchHeight = lunch,
            hasLunchClass = false,
            eveningBreakHeight = evening,
        )
        val expected = 8 * (period + gap) + lunch + evening + 2 * gap
        assertEquals(expected, period11, 0.01f)
        val period10Bottom = weekGridRowOffsetDp(10, period, lunch, false, evening) + period
        assertEquals(period10Bottom + gap + evening + gap, period11, 0.01f)
    }

    @Test
    fun `evening rows stay aligned when lunch periods occupy two teaching rows`() {
        val period = 41
        val gap = 2
        val evening = 20
        val lunch = period * 2 + gap
        val period11 = weekGridRowOffsetPx(
            periodNo = 11,
            periodHeightPx = period,
            lunchHeightPx = lunch,
            hasLunchClass = true,
            eveningBreakHeightPx = evening,
            gridGapPx = gap,
        )
        assertEquals(10 * (period + gap) + evening + gap, period11)
    }

    private fun course(name: String, room: String, teacher: String): DemoOccurrence = DemoOccurrence(
        course = Course(
            id = name,
            semesterId = "semester",
            sourceType = SourceType.QNU_EDU,
            sourceKey = name,
            name = name,
        ),
        paletteIndex = 0,
        weekday = 1,
        periodStart = 1,
        periodEnd = 1,
        room = room,
        teacher = teacher,
    )
}
