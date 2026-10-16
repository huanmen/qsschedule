package cn.qztc.timetable.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

internal fun dateTimeAllowed(value:LocalDateTime,minimum:LocalDateTime?,maximum:LocalDateTime?)=
    (minimum==null||value>=minimum)&&(maximum==null||value<=maximum)

@Composable
fun QztcDateTimePicker(title:String,date:LocalDate,minute:Int?,minimum:LocalDateTime?=null,maximum:LocalDateTime?=null,
    onDismiss:()->Unit,onChoose:(LocalDate,Int?)->Unit) {
    val colors=LocalQztcColors.current
    fun clamp(value:LocalDateTime):LocalDateTime {
        val lower=if(minimum!=null&&value<minimum)minimum else value
        return if(maximum!=null&&lower>maximum)maximum else lower
    }
    val initial=clamp(date.atTime((minute?:0)/60,(minute?:0)%60))
    var selected by remember {mutableStateOf(initial.toLocalDate())}
    var clock by remember {mutableIntStateOf(initial.hour*60+initial.minute)}
    var month by remember {mutableStateOf(YearMonth.from(selected))}
    var hourText by remember(selected) {mutableStateOf((clock/60).toString().padStart(2,'0'))}
    var minuteText by remember(selected) {mutableStateOf((clock%60).toString().padStart(2,'0'))}
    val entered=hourText.toIntOrNull()?.takeIf {it in 0..23}?.let {h ->minuteText.toIntOrNull()?.takeIf {it in 0..59}?.let {h*60+it}}
    fun minuteAllowed(value:Int)=dateTimeAllowed(selected.atTime(value/60,value%60),minimum,maximum)
    QztcCalendarSheet(title,onDismiss,onConfirm={onChoose(selected,if(minute==null)null else entered)},
        confirmEnabled=minute==null||entered?.let(::minuteAllowed)==true,
        onToday={
            val current=entered?:clock
            val value=clamp(LocalDate.now().atTime(current/60,current%60))
            selected=value.toLocalDate();clock=value.hour*60+value.minute;month=YearMonth.from(selected)
            hourText=(clock/60).toString().padStart(2,'0');minuteText=(clock%60).toString().padStart(2,'0')
        }) {
        if(minute!=null)Row(Modifier.fillMaxWidth().padding(vertical=4.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
            CompactClockInput(hourText,{hourText=it},"时","datetime-hour-input")
            Text(":",Modifier.padding(horizontal=8.dp))
            CompactClockInput(minuteText,{minuteText=it},"分","datetime-minute-input")
        }
        QztcCalendarMonth(month,selected,selected,{month=it},{day ->
            selected=day
            if(minute!=null) {
                val current=entered?:clock
                val adjusted=clamp(day.atTime(current/60,current%60))
                clock=adjusted.hour*60+adjusted.minute
                hourText=(clock/60).toString().padStart(2,'0');minuteText=(clock%60).toString().padStart(2,'0')
            }
        },isDateEnabled={day ->(minimum==null||day>=minimum.toLocalDate())&&(maximum==null||day<=maximum.toLocalDate())},
            minimumMonth=minimum?.let {YearMonth.from(it)},maximumMonth=maximum?.let {YearMonth.from(it)})
    }
}

@Composable
private fun CompactClockInput(value:String,onChange:(String)->Unit,label:String,tag:String) {
    val colors=LocalQztcColors.current
    BasicTextField(value,{onChange(it.filter(Char::isDigit).take(2))},singleLine=true,
        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),cursorBrush=SolidColor(colors.accent),
        textStyle=QztcTypography.body.copy(textAlign=TextAlign.Center,color=colors.ink),
        modifier=Modifier.width(64.dp).height(32.dp).border(1.dp,colors.accentDeep,RoundedCornerShape(6.dp)).testTag(tag),
        decorationBox={inner ->Row(Modifier.fillMaxSize().padding(horizontal=6.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.weight(1f),contentAlignment=Alignment.Center) {inner()}
            Text(label,style=QztcTypography.meta,color=colors.mut)
        }})
}
