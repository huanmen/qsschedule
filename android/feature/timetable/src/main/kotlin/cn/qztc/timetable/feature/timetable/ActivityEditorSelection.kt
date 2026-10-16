package cn.qztc.timetable.feature.timetable

import cn.qztc.timetable.core.domain.model.*
import java.time.LocalDate

internal data class ActivityEditorSelection(val weeks:List<Int>,val weekdays:List<Int>,val until:LocalDate?,
    val excludedWeekdays:List<Int>,val excludedDates:List<String>,val excludedOccurrences:List<String>)

/** Keep stored choices in the same window as the actual recurrence, including the last day's clock. */
internal fun trimActivityEditorSelection(slots:List<ActivityMemoTime>,mode:String,weeks:List<Int>,weekdays:List<Int>,
    until:LocalDate?,anchor:LocalDate?,allDay:Boolean,interval:Int,daysBefore:Int,
    excludedWeekdays:List<Int>,excludedDates:List<String>,excludedOccurrences:List<String>):ActivityEditorSelection {
    val first=slots.first();val start=first.date?:return ActivityEditorSelection(weeks,weekdays,until,excludedWeekdays,excludedDates,excludedOccurrences)
    val clippedUntil=until?.coerceAtLeast(start)?.let {minOf(it,first.endDate?:it)}
    val last=listOfNotNull(first.endDate,clippedUntil).minOrNull()?:start.plusYears(2)
    fun allowed(day:LocalDate)=day>=start&&day<=last&&(day!=first.endDate||allDay||(first.minute?:540)<=(first.endMinute?:1439))
    val termDates=if(mode=="term"&&anchor!=null)weeks.flatMap {week ->weekdays.map {day ->
        week to anchor.plusDays(((week-1)*7+day-1).toLong())
    }}.filter {allowed(it.second)}else emptyList()
    val occurrences=when(mode) {
        "term" ->termDates.map {it.second}
        "weekly","daily","monthly","interval" -> {
            if(mode=="weekly"&&weekdays.isEmpty())emptyList()else ActivityRepeatRule(when(mode) {
                "weekly"->ActivityRepeatKind.WEEKLY;"monthly"->ActivityRepeatKind.MONTHLY
                "interval"->ActivityRepeatKind.INTERVAL;else->ActivityRepeatKind.DAILY
            },weekdays,interval.coerceIn(1,3650),last,true).dates(start,start,last).filter(::allowed)
        }
        "points" ->slots.mapNotNull {it.date}
        else ->listOf(start)
    }.toSet()
    val actualDays=occurrences.map {it.dayOfWeek.value}.toSet()
    val sendingDays=occurrences.map {it.minusDays(daysBefore.coerceIn(0,365).toLong()).toString()}.toSet()
    return ActivityEditorSelection(if(mode=="term")weeks.filter {w ->termDates.any {it.first==w}}else weeks,
        if(mode in listOf("term","weekly"))weekdays.filter {it in actualDays}else weekdays,clippedUntil,
        excludedWeekdays.filter {it in actualDays},excludedDates.filter {it in sendingDays},
        excludedOccurrences.filter {runCatching {LocalDate.parse(it) in occurrences}.getOrDefault(false)})
}

internal fun activityRepeatSummary(mode:String,weeks:List<Int>,weekdays:List<Int>,slots:List<ActivityMemoTime>,
    allDay:Boolean,interval:String):List<String> = buildList {
    // Point labels already contain their dates. Other recurrence labels alone do not identify the start date.
    if(mode!="points")slots.first().date?.let {add("开始日期：$it")}
    when(mode) {
        "term" ->if(weeks.isNotEmpty())add("第 ${weeks.sorted().joinToString("、")} 周")
        "daily" ->add("每天")
        "monthly" ->add("每月 ${slots.first().date?.dayOfMonth} 日")
        "interval" ->add("每 $interval 天")
        "points" ->slots.forEach {add(it.label())}
    }
    if(mode in listOf("term","weekly")&&weekdays.isNotEmpty())add("星期"+weekdays.sorted().joinToString("、") {"一二三四五六日"[it-1].toString()})
    if(mode!="points"&&!allDay)slots.first().minute?.let {add("%02d:%02d".format(it/60,it%60))}
}
