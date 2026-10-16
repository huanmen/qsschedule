package cn.qztc.timetable.core.domain.model

import cn.qztc.timetable.core.domain.serialization.LocalDateSerializer
import java.time.LocalDate
import kotlinx.serialization.Serializable

/** Null date means an undated memo; a null end means a single point. */
@Serializable
data class ActivityMemoTime(
    @Serializable(LocalDateSerializer::class) val date: LocalDate? = null,
    val minute: Int? = null,
    @Serializable(LocalDateSerializer::class) val endDate: LocalDate? = null,
    val endMinute: Int? = null,
    val additional: List<ActivityMemoTime> = emptyList(),
    val recurrence: ActivityWeekRecurrence? = null,
    val remind: Boolean = false,
    val repeat: ActivityRepeatRule? = null,
    val reminder: ActivityMemoReminder? = null,
    val location: String = "",
) {
    init {
        require(minute == null || minute in 0..1439)
        require(endMinute == null || endMinute in 0..1439)
        require(date != null || (minute == null && endDate == null && endMinute == null))
        require(endDate != null || endMinute == null)
        require(endDate == null || !endDate.isBefore(date))
        require(endDate == null || (minute == null) == (endMinute == null))
        require(endDate != date || minute == null || endMinute!! > minute)
        require(endDate == null || java.time.temporal.ChronoUnit.DAYS.between(date, endDate) <= 366)
        require(additional.size <= 50 && additional.all { it.date != null && it.additional.isEmpty() && it.recurrence == null })
    }

    fun slots(from:LocalDate? = null, through:LocalDate? = null): List<ActivityMemoTime> {
        val base=listOfNotNull(takeIf {date!=null}?.copy(additional=emptyList(),recurrence=null,repeat=null))
        val repeating=repeat?.let {rule -> base.flatMap {template -> rule.dates(template.date!!,
            (from?:LocalDate.now().minusYears(1)).minusDays(366),minOf(through?:maxOf(template.date!!,LocalDate.now()).plusYears(2),
                template.endDate?.takeIf {rule.bounded}?:LocalDate.MAX)).map {day ->
            val span=template.endDate?.let {java.time.temporal.ChronoUnit.DAYS.between(template.date,it)}
            if(rule.bounded)template.copy(date=day,endDate=null,endMinute=null)
            else template.copy(date=day,endDate=span?.let {day.plusDays(it)})
        }.filter {slot ->!rule.bounded||template.endDate==null||slot.date!!<template.endDate||
            (slot.date==template.endDate&&(slot.minute?:0)<=(template.endMinute?:1439))} } } ?: base
        return (repeating+additional+recurrence?.slots().orEmpty()).filter {
            (from==null || (it.endDate?:it.date!!)>=from) && (through==null || it.date!!<=through)
        }.distinctBy {listOf(it.date,it.minute,it.endDate,it.endMinute)}
    }

    fun dates(): List<LocalDate> = slots().flatMap { it.singleDates() }.distinct().sorted()

    private fun singleDates(): List<LocalDate> = date?.let { first ->
        generateSequence(first) { if (it < (endDate ?: first)) it.plusDays(1) else null }.toList()
    }.orEmpty()

    fun onDate(date: LocalDate): List<ActivityMemoTime> = slots(date,date).filter { date in it.singleDates() }

    fun label(): String {
        if (repeat != null) return "${date ?: ""} · ${repeat.label()}" + (repeat.until?.let {" · 至 $it"} ?: "")
        if (recurrence != null) return recurrence.label()
        if (additional.isNotEmpty()) return slots().joinToString("；") { it.label() }
        fun clock(value: Int?) = value?.let { " %02d:%02d".format(it / 60, it % 60) }.orEmpty()
        return date?.let { "$it${clock(minute)}" + (endDate?.let { end -> " 至 $end${clock(endMinute)}" }.orEmpty()) } ?: "未设置时间"
    }
}

/** The semester anchor is stored with the rule so changing the viewed term never moves a memo. */
@Serializable
data class ActivityWeekRecurrence(
    val semesterId: String,
    val semesterName: String,
    @Serializable(LocalDateSerializer::class) val firstMonday: LocalDate,
    val weeks: List<Int>,
    val weekdays: List<Int>,
    val minute: Int? = null,
    @Serializable(LocalDateSerializer::class) val until:LocalDate? = null,
    @Serializable(LocalDateSerializer::class) val from:LocalDate? = null,
    val untilMinute:Int? = null,
) {
    init {
        require(weeks.isNotEmpty() && weeks.all { it in 1..60 })
        require(weekdays.isNotEmpty() && weekdays.all { it in 1..7 })
        require(minute == null || minute in 0..1439)
        require(untilMinute==null||untilMinute in 0..1439)
    }
    fun slots(): List<ActivityMemoTime> = weeks.distinct().sorted().flatMap { week -> weekdays.distinct().sorted().map { day ->
        ActivityMemoTime(firstMonday.plusDays(((week-1)*7+day-1).toLong()), minute)
    } }.filter {(from==null||it.date!!>=from)&&(until==null||it.date!!<until||
        (it.date==until&&(it.minute?:0)<=(untilMinute?:1439)))}
    fun label(): String = "$semesterName · 第 ${weeks.distinct().sorted().joinToString("、")} 周 · " +
        weekdays.distinct().sorted().joinToString("、") { "周${listOf("一","二","三","四","五","六","日")[it-1]}" } +
        (minute?.let { " %02d:%02d".format(it/60,it%60) } ?: "")
}
