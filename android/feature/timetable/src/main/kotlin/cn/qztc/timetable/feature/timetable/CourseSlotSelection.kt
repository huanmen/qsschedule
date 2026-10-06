package cn.qztc.timetable.feature.timetable

/** Work on individual periods so cancelling the middle of a range preserves its neighbours. */
internal fun removeCoursePeriod(slots: List<WeeklySlotDraft>, day: Int, period: Int): List<WeeklySlotDraft> =
    slots.flatMap { slot ->
        if (slot.weekday != day || period !in slot.periodStart..slot.periodEnd) listOf(slot)
        else listOfNotNull(
            slot.takeIf { it.periodStart < period }?.copy(periodEnd = period - 1),
            slot.takeIf { it.periodEnd > period }?.copy(periodStart = period + 1),
        )
    }

internal fun addCoursePeriods(slots: List<WeeklySlotDraft>, day: Int, range: IntRange, weeks: List<Int>): List<WeeklySlotDraft> {
    val occupied = slots.filter { it.weekday == day }.flatMap { it.periodStart..it.periodEnd }.toSet()
    val next = slots + range.filterNot { it in occupied }.map { WeeklySlotDraft(day, it, it, weeks) }
    val result = mutableListOf<WeeklySlotDraft>()
    next.sortedWith(compareBy({ it.weekday }, { it.periodStart })).forEach { slot ->
        val last = result.lastOrNull()
        if (last != null && last.weekday == slot.weekday && last.periodEnd + 1 == slot.periodStart &&
            last.weeks == slot.weeks && last.sourceMeetingId == slot.sourceMeetingId) {
            result[result.lastIndex] = last.copy(periodEnd = slot.periodEnd)
        } else result += slot
    }
    return result
}
