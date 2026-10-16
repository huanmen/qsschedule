package cn.qztc.timetable.feature.timetable

import java.time.LocalDate

data class RecognizedActivity(val title:String,val date:LocalDate?,val minute:Int?,val location:String,val note:String)

private const val ACTIVITY_NUMBER="[0-9０-９零〇○一二三四五六七八九十百千两兩壹贰貳叁參叄肆伍陆陸柒捌玖拾佰仟廿卅卌]{1,12}"

internal fun activityChineseNumber(raw:String):Int? {
    val expanded=raw.replace("廿","二十").replace("卅","三十").replace("卌","四十")
    fun digit(c:Char):Int?=c.digitToIntOrNull()?:when(c) {
        '零','〇','○'->0;'一','壹'->1;'二','两','兩','贰','貳'->2;'三','叁','參','叄'->3
        '四','肆'->4;'五','伍'->5;'六','陆','陸'->6;'七','柒'->7;'八','捌'->8;'九','玖'->9;else->null
    }
    if(expanded.all {digit(it)!=null})return expanded.map {digit(it)!!}.joinToString("").toIntOrNull()
    var total=0;var number=0;var previousUnit=10000
    for(c in expanded) {
        val d=digit(c)
        if(d!=null)number=number*10+d else {
            val unit=when(c) {'十','拾'->10;'百','佰'->100;'千','仟'->1000;else->return null}
            if(unit>=previousUnit)return null
            total+=(if(number==0)1 else number)*unit;number=0;previousUnit=unit
        }
    }
    return (total+number).takeIf {it in 0..9999}
}

/** Local deterministic recognition; ambiguous fragments remain in the title instead of being guessed. */
fun recognizeActivityText(text:String,today:LocalDate):RecognizedActivity {
    var remaining=text.trim()
    var date:LocalDate?=null
    val absolute=Regex("(?:($ACTIVITY_NUMBER)[年/-])?($ACTIVITY_NUMBER)[月/-]($ACTIVITY_NUMBER)[日号號]?").find(remaining)
    if(absolute!=null) {
        date=runCatching {LocalDate.of(if(absolute.groupValues[1].isBlank())today.year else requireNotNull(activityChineseNumber(absolute.groupValues[1])),
            requireNotNull(activityChineseNumber(absolute.groupValues[2])),requireNotNull(activityChineseNumber(absolute.groupValues[3])))}.getOrNull()
        if(date!=null)remaining=remaining.replaceRange(absolute.range,"")
    } else {
        val relative=listOf("大后天" to 3L,"后天" to 2L,"明天" to 1L,"今天" to 0L).firstOrNull {remaining.contains(it.first)}
        if(relative!=null) {date=today.plusDays(relative.second);remaining=remaining.replace(relative.first,"")}
        else Regex("(下[周週]|本[周週]|这周|這週|[周週]|星期|[礼禮]拜)($ACTIVITY_NUMBER|日|天)").find(remaining)?.let {m ->
            val day=if(m.groupValues[2] in listOf("日","天"))7 else activityChineseNumber(m.groupValues[2])?:0
            if(day !in 1..7)return@let
            var monday=today.minusDays((today.dayOfWeek.value-1).toLong())
            if(m.groupValues[1].startsWith("下"))monday=monday.plusWeeks(1)
            var chosen=monday.plusDays((day-1).toLong())
            if(m.groupValues[1] in listOf("周","週","星期","礼拜","禮拜")&&chosen<today)chosen=chosen.plusWeeks(1)
            date=chosen;remaining=remaining.replaceRange(m.range,"")
        }
    }
    var minute:Int?=null
    Regex("(上午|早上|中午|下午|晚上|晚间|晚間|凌晨)?\\s*($ACTIVITY_NUMBER)(?:[:：]($ACTIVITY_NUMBER)|[点點时時](?:($ACTIVITY_NUMBER)分?|半)?)").find(remaining)?.let {m ->
        var hour=activityChineseNumber(m.groupValues[2])?:return@let
        val part=m.groupValues[1]
        if(part in listOf("下午","晚上","晚间","晚間")&&hour<12)hour+=12
        if(part=="凌晨"&&hour==12)hour=0
        if(part=="中午"&&hour<11)hour+=12
        val minText=m.groupValues[3].ifBlank {m.groupValues[4]}
        val min=if(m.value.endsWith("半"))30 else if(minText.isBlank())0 else activityChineseNumber(minText)?:return@let
        if(hour in 0..23&&min in 0..59) {minute=hour*60+min;remaining=remaining.replaceRange(m.range,"")}
    }
    val note=Regex("(?:备注|内容)[:：]\\s*(.+)$").find(remaining)?.let {m ->remaining=remaining.replaceRange(m.range,"");m.groupValues[1]}.orEmpty()
    val location=Regex("(?:地点[:：]|在|@)\\s*([^，,；;\\s]+)").find(remaining)?.let {m ->remaining=remaining.replaceRange(m.range,"");m.groupValues[1]}.orEmpty()
    return RecognizedActivity(remaining.trim(' ','，',',','；',';'),date,minute,location,note)
}
