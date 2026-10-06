package cn.qztc.timetable.feature.timetable

import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.max

internal const val weekMinPeriodHeightDp = 40f
internal const val weekHardMinPeriodHeightDp = 36f
internal const val weekLunchFoldedHeightDp = 20f
internal const val weekLunchExpandedHeightDp = 44f
internal const val weekEveningBreakHeightDp = 20f
internal const val weekCompactLineHeightDp = 13f
internal const val weekCompactCharWidthDp = 10.5f
internal const val weekCourseHorizontalPaddingDp = 8f
internal const val weekCourseVerticalPaddingDp = 6f
internal const val weekCourseBlockGapDp = 4f
internal const val weekGridGapDp = 2f

internal fun compactYearMonth(date: LocalDate): String =
    "${(date.year % 100).toString().padStart(2, '0')}.${date.monthValue}"

internal fun weekMonthTitle(dates: List<LocalDate>): String {
    if (dates.isEmpty()) return ""
    return compactYearMonth(dates.min())
}

internal fun scheduleDayNumber(date: LocalDate): String = date.dayOfMonth.toString()

internal fun scheduleDayLabel(date: LocalDate, weekStartDate: LocalDate?): String =
    if (weekStartDate != null && (date.year != weekStartDate.year || date.month != weekStartDate.month)) {
        "${date.monthValue}.${date.dayOfMonth}"
    } else {
        scheduleDayNumber(date)
    }

internal fun weekPeriodHeightDp(
    occurrences: List<DemoOccurrence>,
    unitWidthDp: Float,
    visibleDays: List<Int>,
    compactWeekend: Boolean,
): Float {
    val fromText = occurrences
        .filter { !it.ghost && it.weekday in visibleDays }
        .maxOfOrNull { occurrence ->
            val cellWidth = unitWidthDp * dayColumnWeight(occurrence.weekday, compactWeekend)
            neededPeriodHeightDp(occurrence, cellWidth)
        }
        ?: weekMinPeriodHeightDp
    return max(weekMinPeriodHeightDp, fromText)
}

internal fun neededPeriodHeightDp(occurrence: DemoOccurrence, cellWidthDp: Float): Float {
    val periodCount = (occurrence.layoutPeriodEnd - occurrence.layoutPeriodStart + 1).coerceAtLeast(1)
    val textWidth = (cellWidthDp - weekCourseHorizontalPaddingDp).coerceAtLeast(weekCompactCharWidthDp)
    val titleMaxLines = if (periodCount == 1) 4 else 7
    val metaMaxLines = if (periodCount == 1) 3 else 5
    val titleLines = textLineCount(occurrence.course.name, textWidth, titleMaxLines)
    val teacherLines = textLineCount(occurrence.teacher, textWidth, metaMaxLines)
    val roomLines = textLineCount(occurrence.room, textWidth, metaMaxLines)
    val contentHeight = weekCourseVerticalPaddingDp +
        titleLines * weekCompactLineHeightDp +
        weekCourseBlockGapDp +
        (teacherLines + roomLines) * weekCompactLineHeightDp
    val gapsInsideBlock = weekGridGapDp * (periodCount - 1)
    return ((contentHeight - gapsInsideBlock) / periodCount).coerceAtLeast(weekMinPeriodHeightDp)
}

internal fun textLineCount(text: String, maxWidthDp: Float, maxLines: Int): Int {
    if (text.isBlank() || maxLines <= 0) return 0
    val units = text.sumOf { character -> if (character.code > 0x7F) 1.0 else 0.55 }
    val perLine = (maxWidthDp / weekCompactCharWidthDp).coerceAtLeast(1f)
    return ceil(units / perLine).toInt().coerceIn(1, maxLines)
}

internal fun dayColumnWeight(day: Int, compactWeekend: Boolean): Float =
    if (compactWeekend && day >= 6) .55f else 1f

internal fun weekGridRowOffsetPx(
    periodNo: Int,
    periodHeightPx: Int,
    lunchHeightPx: Int,
    hasLunchClass: Boolean,
    eveningBreakHeightPx: Int,
    gridGapPx: Int = weekGridGapDp.toInt(),
): Int = when (periodNo) {
    in 1..4 -> (periodHeightPx + gridGapPx) * (periodNo - 1)
    in 5..6 -> (periodHeightPx + gridGapPx) * 4 + (periodHeightPx + gridGapPx) * (periodNo - 5)
    in 7..10 -> {
        val afternoonStart = if (hasLunchClass) {
            (periodHeightPx + gridGapPx) * 6
        } else {
            (periodHeightPx + gridGapPx) * 4 + lunchHeightPx + gridGapPx
        }
        afternoonStart + (periodHeightPx + gridGapPx) * (periodNo - 7)
    }
    in 11..13 -> weekGridRowOffsetPx(
        periodNo = 10,
        periodHeightPx = periodHeightPx,
        lunchHeightPx = lunchHeightPx,
        hasLunchClass = hasLunchClass,
        eveningBreakHeightPx = eveningBreakHeightPx,
        gridGapPx = gridGapPx,
    ) + periodHeightPx + gridGapPx + eveningBreakHeightPx + gridGapPx +
        (periodHeightPx + gridGapPx) * (periodNo - 11)
    else -> error("Unsupported QNU period: $periodNo")
}

internal fun weekGridRowOffsetDp(
    periodNo: Int,
    periodHeight: Float,
    lunchHeight: Float,
    hasLunchClass: Boolean,
    eveningBreakHeight: Float,
    gridGap: Float = weekGridGapDp,
): Float = when (periodNo) {
    in 1..4 -> (periodHeight + gridGap) * (periodNo - 1)
    in 5..6 -> (periodHeight + gridGap) * 4 + (periodHeight + gridGap) * (periodNo - 5)
    in 7..10 -> {
        val afternoonStart = if (hasLunchClass) {
            (periodHeight + gridGap) * 6
        } else {
            (periodHeight + gridGap) * 4 + lunchHeight + gridGap
        }
        afternoonStart + (periodHeight + gridGap) * (periodNo - 7)
    }
    in 11..13 -> {
        val eveningStart = weekGridRowOffsetDp(
            periodNo = 10,
            periodHeight = periodHeight,
            lunchHeight = lunchHeight,
            hasLunchClass = hasLunchClass,
            eveningBreakHeight = eveningBreakHeight,
            gridGap = gridGap,
        ) + periodHeight + gridGap + eveningBreakHeight + gridGap
        eveningStart + (periodHeight + gridGap) * (periodNo - 11)
    }
    else -> error("Unsupported QNU period: $periodNo")
}

internal fun weekFittedPeriodHeightDp(
    availableHeightDp: Float,
    hasLunchClass: Boolean,
): Float {
    val periodRows = if (hasLunchClass) 13 else 11
    val extraBands = if (hasLunchClass) {
        weekEveningBreakHeightDp
    } else {
        weekLunchFoldedHeightDp + weekEveningBreakHeightDp
    }
    val gaps = if (hasLunchClass) 13 else 12
    return ((availableHeightDp - extraBands - gaps * weekGridGapDp) / periodRows)
        .coerceAtLeast(weekHardMinPeriodHeightDp)
}
