package cn.qztc.timetable.feature.timetable

/** A transparent cell must never sit on top of obsolete course text. Trim the old
 * content before composition; retain cancelled portions only where no live class exists. */
internal fun replaceWeekCellContents(occurrences: List<DemoOccurrence>): List<DemoOccurrence> {
    val active = occurrences.filterNot { it.cancelled || it.ghost }
    return occurrences.flatMap { occurrence ->
        val replacements = active.filter { other ->
            other !== occurrence && other.weekday == occurrence.weekday &&
                (occurrence.cancelled || occurrence.ghost ||
                    (!occurrence.changed && other.changed && other.course.id == occurrence.course.id))
        }
        val remaining = (occurrence.periodStart..occurrence.periodEnd).filter { period ->
            replacements.none { period in it.periodStart..it.periodEnd }
        }
        val runs = mutableListOf<IntRange>()
        remaining.forEach { period ->
            val last = runs.lastOrNull()
            if (last != null && last.last + 1 == period) runs[runs.lastIndex] = last.first..period
            else runs += period..period
        }
        runs.map { occurrence.copy(periodStart = it.first, periodEnd = it.last,
            layoutPeriodStart = it.first, layoutPeriodEnd = it.last) }
    }
}
