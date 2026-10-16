package cn.qztc.timetable.core.domain.model

import cn.qztc.timetable.core.domain.serialization.LocalDateSerializer
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.serialization.Serializable

@Serializable
enum class ActivityRepeatKind { DAILY, WEEKLY, MONTHLY, INTERVAL }

/** Calendar recurrence is expanded within a requested window, never an infinite list. */
@Serializable
data class ActivityRepeatRule(
    val kind:ActivityRepeatKind,
    val weekdays:List<Int> = emptyList(),
    val intervalDays:Int = 1,
    @Serializable(LocalDateSerializer::class) val until:LocalDate? = null,
    val bounded:Boolean = false,
) {
    init {
        require(intervalDays in 1..3650)
        require(weekdays.all {it in 1..7})
        require(kind!=ActivityRepeatKind.WEEKLY || weekdays.isNotEmpty())
    }
    fun dates(anchor:LocalDate,from:LocalDate,through:LocalDate):List<LocalDate> {
        val first=maxOf(anchor,from)
        val last=minOf(through,until?:through)
        if(first>last)return emptyList()
        require(ChronoUnit.DAYS.between(first,last)<=5000)
        return generateSequence(first) {if(it<last)it.plusDays(1)else null}.filter {day -> when(kind) {
            ActivityRepeatKind.DAILY -> true
            ActivityRepeatKind.WEEKLY -> day.dayOfWeek.value in weekdays
            ActivityRepeatKind.MONTHLY -> day.dayOfMonth==anchor.dayOfMonth
            ActivityRepeatKind.INTERVAL -> ChronoUnit.DAYS.between(anchor,day)%intervalDays==0L
        } }.toList()
    }
    fun label():String=when(kind) {
        ActivityRepeatKind.DAILY -> "每天"
        ActivityRepeatKind.MONTHLY -> "每月"
        ActivityRepeatKind.INTERVAL -> "每 $intervalDays 天"
        ActivityRepeatKind.WEEKLY -> weekdays.distinct().sorted().joinToString("、") {"周${"一二三四五六日"[it-1]}"}
    }
}

@Serializable
data class ActivityMemoReminder(
    val daysBefore:Int = 0,
    val minute:Int = 540,
    val excludedWeekdays:List<Int> = emptyList(),
    val excludedDates:List<String> = emptyList(),
    val displayMode:ReminderDisplayMode = ReminderDisplayMode.NOTIFICATION_BAR,
    val excludedOccurrenceDates:List<String> = emptyList(),
    val excludedOccurrenceWeekdays:List<Int> = emptyList(),
) {
    init {
        require(daysBefore in 0..365)
        require(minute in 0..1439)
        require(excludedWeekdays.all {it in 1..7})
        require(excludedOccurrenceWeekdays.all {it in 1..7})
        require(excludedDates.size<=366 && excludedDates.all {runCatching {LocalDate.parse(it)}.isSuccess})
        require(excludedOccurrenceDates.size<=366 && excludedOccurrenceDates.all {runCatching {LocalDate.parse(it)}.isSuccess})
    }
}
