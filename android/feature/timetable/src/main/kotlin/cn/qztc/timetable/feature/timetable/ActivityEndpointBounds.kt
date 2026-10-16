package cn.qztc.timetable.feature.timetable

import cn.qztc.timetable.core.domain.model.ActivityMemoTime
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.LocalDate

/** Inclusive picker bounds preserve the other endpoint; the user cannot cross it. */
internal fun memoEndpointBounds(slot:ActivityMemoTime,end:Boolean,allDay:Boolean,today:LocalDate?=null):Pair<LocalDateTime?,LocalDateTime?> {
    val start=slot.date?:return null to null
    return if(end) {
        val min=if(allDay)start.atStartOfDay()else start.atTime((slot.minute?:540)/60,(slot.minute?:540)%60).plusMinutes(1)
        min to start.plusDays(366).atTime(LocalTime.MAX)
    } else {
        val last=slot.endDate?:return today?.atStartOfDay() to null
        val max=if(allDay)last.atTime(LocalTime.MAX)else last.atTime((slot.endMinute?:600)/60,(slot.endMinute?:600)%60).minusMinutes(1)
        maxOf(last.minusDays(366).atStartOfDay(),today?.atStartOfDay()?:LocalDateTime.MIN) to max
    }
}
