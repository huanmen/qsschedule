package cn.qztc.timetable.feature.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.domain.model.ActivityMemoTime
import cn.qztc.timetable.core.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth

internal data class ActivityPreviewDay(val occurs:Boolean=false,val repeats:Boolean=false,val start:Boolean=false,
    val end:Boolean=false,val reminder:Boolean=false,val excluded:Boolean=false,val occurrenceStart:Boolean=false)

internal fun activityPreviewRange(memo:ActivityMemoTime?,today:LocalDate=LocalDate.now()):Pair<LocalDate,LocalDate> {
    if(memo==null)return today to today
    val first=memo.recurrence?.from?:memo.date?:memo.recurrence?.slots()?.firstOrNull()?.date?:today
    val last=memo.recurrence?.until?:listOfNotNull(memo.repeat?.until,memo.endDate).minOrNull()?:
        memo.recurrence?.slots()?.lastOrNull()?.date?:if(memo.repeat!=null)maxOf(first,today).plusYears(2)else first
    val allLast=maxOf(last,memo.additional.maxOfOrNull {it.endDate?:it.date!!}?:last)
    val earliest=minOf(first,memo.additional.minOfOrNull {it.date!!}?:first)
    return earliest.minusDays(if(memo.remind)(memo.reminder?.daysBefore?:0).toLong()else 0) to maxOf(earliest,allLast)
}

internal fun activityPreviewDays(memo:ActivityMemoTime,month:YearMonth):Map<LocalDate,ActivityPreviewDay> {
    val first=month.atDay(1).minusDays((month.atDay(1).dayOfWeek.value-1).toLong())
    val last=first.plusDays(41)
    val policy=memo.reminder
    val slots=memo.slots(first,last.plusDays((policy?.daysBefore?:0).toLong()))
    val starts=slots.mapNotNull {it.date}.toSet()
    val occurs=slots.flatMap {s ->generateSequence(s.date!!) {if(it<(s.endDate?:s.date!!))it.plusDays(1)else null}.toList()}.toSet()
    val repeating=memo.repeat!=null||memo.recurrence!=null||memo.additional.isNotEmpty()
    val excluded=policy?.excludedOccurrenceDates.orEmpty().map(LocalDate::parse).toSet()
    val reminders=if(!memo.remind||policy==null)emptySet()else slots.filter {it.date !in excluded&&it.date!!.dayOfWeek.value !in policy.excludedOccurrenceWeekdays}.map {
        it.date!!.minusDays(policy.daysBefore.toLong())
    }.filter {it.dayOfWeek.value !in policy.excludedWeekdays&&it.toString() !in policy.excludedDates}.toSet()
    val start=memo.recurrence?.from?:memo.date
    val end=memo.recurrence?.until?:listOfNotNull(memo.endDate,memo.repeat?.until).minOrNull()
    val endpoints=if(!repeating)slots.mapNotNull {it.endDate}.toSet()else memo.additional.mapNotNull {it.endDate}.toSet()
    return (0L..41L).associate {n ->val date=first.plusDays(n);date to ActivityPreviewDay(
        date in occurs,repeating&&date in starts,date==start||memo.additional.any {it.date==date},date==end||date in endpoints,
        date in reminders,date in excluded,date in starts)}
}

/** Marks actual generated occurrences, not guessed weekday dots. */
@Composable
internal fun ActivitySchedulePreview(memo:ActivityMemoTime?,selectExclusions:Boolean=false,onToggle:(LocalDate)->Unit={}) {
    val colors=LocalQztcColors.current
    val markerColors=listOf(Color(0xFF0072B2),Color(0xFFD55E00),Color(0xFF8B4BB3),Color(0xFF009E73))
    val initial=memo?.recurrence?.from?:memo?.date?:LocalDate.now()
    var monthText by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf(YearMonth.from(initial).toString())}
    val range=activityPreviewRange(memo)
    val minMonth=YearMonth.from(range.first);val maxMonth=YearMonth.from(range.second)
    val month=YearMonth.parse(monthText).coerceIn(minMonth,maxMonth)
    var selectedText by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf<String?>(null)}
    val days=remember(memo,month) {memo?.let {activityPreviewDays(it,month)}.orEmpty()}
    val start=month.atDay(1).minusDays((month.atDay(1).dayOfWeek.value-1).toLong())
    Column(Modifier.fillMaxWidth().calendarMonthSwipe(month,minMonth,maxMonth) {monthText=it.toString()}
        .testTag(if(selectExclusions)"occurrence-exclusion-calendar"else"activity-preview-calendar"),
        verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(if(selectExclusions)"选择不提醒的安排日期"else"安排预览",Modifier.weight(1f),style=QztcTypography.section,color=colors.ink)
            TextButton(onClick={monthText=YearMonth.from(LocalDate.now().coerceIn(range.first,range.second)).toString()}) {Text("回今日")}
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick={monthText=month.minusMonths(1).toString()},enabled=month>minMonth) {Icon(Icons.Outlined.ChevronLeft,"预览上个月")}
            Text("${month.year}年${month.monthValue}月",Modifier.weight(1f),textAlign=TextAlign.Center,color=colors.ink)
            IconButton(onClick={monthText=month.plusMonths(1).toString()},enabled=month<maxMonth) {Icon(Icons.Outlined.ChevronRight,"预览下个月")}
        }
        Row {"一二三四五六日".forEach {Text(it.toString(),Modifier.weight(1f),textAlign=TextAlign.Center,style=QztcTypography.meta,color=colors.mut)}}
        repeat(6) {week ->Row(Modifier.fillMaxWidth()) {repeat(7) {day ->
            val date=start.plusDays((week*7+day).toLong())
            val flags=days[date]?:ActivityPreviewDay()
            val selectable=!selectExclusions||flags.occurrenceStart
            val fill=when {flags.excluded->colors.dangerSoft;flags.start||flags.end->colors.accentSoft;flags.occurs->colors.solid2;else->Color.Transparent}
            Column(Modifier.weight(1f).padding(2.dp).height(48.dp).clip(RoundedCornerShape(8.dp)).background(fill)
                .clickable(enabled=selectable) {selectedText=date.toString();if(selectExclusions)onToggle(date)}
                .testTag("${if(selectExclusions)"exclude"else"preview"}-date-$date"),horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.Center) {
                Text(if(flags.excluded)"×"else date.dayOfMonth.toString(),style=QztcTypography.compact,color=when {
                    flags.excluded->colors.danger;!selectable||date.month!=month.month->colors.faint;else->colors.ink})
                Row(horizontalArrangement=Arrangement.spacedBy(2.dp)) {
                    listOf(flags.start,flags.end,flags.repeats,flags.reminder).zip(markerColors).forEachIndexed {i,(shown,tint) ->
                        if(shown)Box(Modifier.size(4.dp).background(tint,CircleShape).testTag("preview-mark-$i-$date"))
                    }
                }
            }
        }}}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            listOf("开始","结束","重复","提醒").zip(markerColors).forEach {(label,tint) ->
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(5.dp).background(tint,CircleShape));Text(label,style=QztcTypography.meta,color=colors.mut)
                }
            }
        }
        if(selectExclusions)Text("仅可点选实际安排日期；× 表示该次不提醒，再点一次恢复。",style=QztcTypography.meta,color=colors.mut)
        selectedText?.let {date ->val flags=days[LocalDate.parse(date)]
            Text(date+"  "+listOfNotNull("开始".takeIf {flags?.start==true},"结束".takeIf {flags?.end==true},
                "重复安排".takeIf {flags?.repeats==true},"发送提醒".takeIf {flags?.reminder==true},
                "该次不提醒".takeIf {flags?.excluded==true}).joinToString(" · ").ifBlank {if(flags?.occurs==true)"活动安排"else"无安排"},
                style=QztcTypography.meta,color=colors.mut)
        }
        if(memo==null)Text("补全时间与重复条件后显示预览",style=QztcTypography.meta,color=colors.mut)
    }
}
