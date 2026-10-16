package cn.qztc.timetable.feature.timetable

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.domain.model.*
import cn.qztc.timetable.core.domain.calendar.SemesterCalendar
import cn.qztc.timetable.core.ui.theme.*
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityMemoEditor(title:String,editorKey:String,initial:ActivityEditDraft,onBack:()->Unit,onSave:(ActivityEditDraft)->Unit,
    onDelete:(()->Unit)?=null,presetColors:List<Int> = CoursePalette.indices.toList(),semester:Semester?=null,
    onPreviewReminder:suspend (Boolean,String,String)->ReminderPreviewState={_,_,_->ReminderPreviewState(message="提醒预览在主应用中可用")},
    onSystemReminderSettings:(Boolean)->Unit={}) {
    val colors=LocalQztcColors.current
    val original=initial.memoTime
    var page by rememberSaveable(editorKey) {mutableStateOf("main")}
    var input by rememberSaveable(editorKey) {mutableStateOf("")}
    var showRecognition by rememberSaveable(editorKey) {mutableStateOf(true)}
    var name by rememberSaveable(editorKey) {mutableStateOf(initial.name)}
    var note by rememberSaveable(editorKey) {mutableStateOf(initial.note)}
    var location by rememberSaveable(editorKey) {mutableStateOf(original?.location.orEmpty())}
    var color by rememberSaveable(editorKey) {mutableIntStateOf(initial.color)}
    val recognition=remember(input) {input.takeIf(String::isNotBlank)?.let {recognizeActivityText(it,LocalDate.now())}}
    var keepLegacy by rememberSaveable(editorKey) {mutableStateOf(original==null&&!initial.id.isNullOrBlank())}
    val base=original?.let {it.takeIf {it.date!=null}?.copy(additional=emptyList(),recurrence=null,repeat=null,reminder=null)}
        ?:original?.recurrence?.let {rule ->
            val start=rule.from?:rule.slots().firstOrNull()?.date?:LocalDate.now()
            val end=rule.until?.takeIf {it>start||it==start&&(rule.minute==null||(rule.untilMinute?:1439)>(rule.minute?:0))}
            ActivityMemoTime(start,rule.minute,end,end?.let {if(rule.minute==null)null else rule.untilMinute?:1439})
        }?:ActivityMemoTime(maxOf(initial.startDate,LocalDate.now()))
    var encoded by rememberSaveable(editorKey) {mutableStateOf((listOf(base)+original?.additional.orEmpty()).map {Json.encodeToString(it)})}
    val slots=encoded.map {Json.decodeFromString<ActivityMemoTime>(it)}
    var allDay by rememberSaveable(editorKey) {mutableStateOf(original?.let {it.minute==null&&it.recurrence?.minute==null}?:true)}
    var undated by rememberSaveable(editorKey) {mutableStateOf(original!=null&&original.date==null&&original.recurrence==null)}
    var mode by rememberSaveable(editorKey) {mutableStateOf(when {
        original?.recurrence!=null -> "term"
        original?.repeat!=null -> original.repeat!!.kind.name.lowercase()
        original?.additional?.isNotEmpty()==true -> "points"
        else -> "none"
    })}
    var weeks by rememberSaveable(editorKey) {mutableStateOf(original?.recurrence?.weeks.orEmpty())}
    var weekdays by rememberSaveable(editorKey) {mutableStateOf(original?.recurrence?.weekdays?:original?.repeat?.weekdays.orEmpty())}
    var interval by rememberSaveable(editorKey) {mutableStateOf((original?.repeat?.intervalDays?:2).toString())}
    var repeatUntil by rememberSaveable(editorKey) {mutableStateOf((original?.repeat?.until?:original?.recurrence?.until)?.toString())}
    var remind by rememberSaveable(editorKey) {mutableStateOf(original?.remind?:false)}
    var daysBefore by rememberSaveable(editorKey) {mutableStateOf((original?.reminder?.daysBefore?:0).toString())}
    var reminderMinute by rememberSaveable(editorKey) {mutableIntStateOf(original?.reminder?.minute?:original?.minute?:540)}
    var skippedDays by rememberSaveable(editorKey) {mutableStateOf(original?.reminder?.excludedOccurrenceWeekdays.orEmpty())}
    var skippedDates by rememberSaveable(editorKey) {mutableStateOf(original?.reminder?.excludedDates.orEmpty())}
    var skippedOccurrences by rememberSaveable(editorKey) {mutableStateOf(original?.reminder?.excludedOccurrenceDates.orEmpty())}
    var reminderMode by rememberSaveable(editorKey) {mutableStateOf((original?.reminder?.displayMode?:ReminderDisplayMode.NOTIFICATION_BAR).name)}
    var choosingDate by remember {mutableStateOf<Pair<Int,Boolean>?>(null)}
    var choosingRepeatEnd by remember {mutableStateOf(false)}
    var choosingReminderTime by remember {mutableStateOf(false)}
    var deleting by remember {mutableStateOf(false)}
    var previewState by remember {mutableStateOf(ReminderPreviewState())}
    val scope=rememberCoroutineScope()
    fun preview(popup:Boolean=reminderMode==ReminderDisplayMode.SCREEN_POPUP.name) {
        if(previewState.running)return
        previewState=ReminderPreviewState(running=true)
        scope.launch {previewState=try {onPreviewReminder(popup,name.ifBlank {"活动提醒预览"},
            listOf(location,note).filter(String::isNotBlank).joinToString(" · ").ifBlank {"这是当前安排的测试提醒"})}
            catch(cancel:kotlinx.coroutines.CancellationException) {throw cancel}
            catch(_:Exception) {ReminderPreviewState(message="未能发送预览，请检查系统通知设置",needsSystemSettings=true)}}
    }
    val termId=original?.recurrence?.semesterId?:semester?.id
    val termName=original?.recurrence?.semesterName?:semester?.name.orEmpty()
    val anchor=original?.recurrence?.firstMonday?:semester?.let {SemesterCalendar.firstMonday(it.startDate)}
    val totalWeeks=maxOf(semester?.takeIf {it.id==termId}?.totalWeeks?:20,weeks.maxOrNull()?:1)
    fun repeatDayAllowed(date:LocalDate):Boolean {
        val first=slots.first()
        val until=listOfNotNull(first.endDate,repeatUntil?.let(LocalDate::parse)).minOrNull()
        return date>=(first.date?:LocalDate.now())&&(until==null||date<until||
            (date==until&&(allDay||first.endDate!=until||(first.minute?:540)<=(first.endMinute?:1439))))
    }
    fun weekAllowed(week:Int)=anchor?.let {monday ->(1..7).any {repeatDayAllowed(monday.plusDays(((week-1)*7+it-1).toLong()))}}==true
    fun weekdayAllowed(day:Int):Boolean {
        if(mode=="term")return (weeks.ifEmpty {(1..totalWeeks).toList()}).any {week ->
            anchor?.let {repeatDayAllowed(it.plusDays(((week-1)*7+day-1).toLong()))}==true}
        val first=slots.first().date?:LocalDate.now()
        return (0L..6L).any {offset ->val date=first.plusDays(offset);date.dayOfWeek.value==day&&repeatDayAllowed(date)}
    }
    fun trimSelections(entries:List<ActivityMemoTime>,until:LocalDate?=repeatUntil?.let(LocalDate::parse)) {
        val clipped=trimActivityEditorSelection(entries,mode,weeks,weekdays,until,anchor,allDay,interval.toIntOrNull()?:1,
            daysBefore.toIntOrNull()?:0,skippedDays,skippedDates,skippedOccurrences)
        weeks=clipped.weeks;weekdays=clipped.weekdays;repeatUntil=clipped.until?.toString()
        skippedDays=clipped.excludedWeekdays;skippedDates=clipped.excludedDates;skippedOccurrences=clipped.excludedOccurrences
    }
    fun updateSlot(index:Int,value:ActivityMemoTime) {
        val entries=slots.mapIndexed {i,old ->if(i==index)value else old}
        trimSelections(entries)
        encoded=entries.map {Json.encodeToString(it)}
    }
    fun builtMemo():ActivityMemoTime {
        if(undated)return ActivityMemoTime(location=location)
        val first=slots.first()
        val prepared=if(mode=="points")listOf(first)+slots.drop(1)else listOf(first)
        val dayOnly=allDay
        val normalized=prepared.map {it.copy(minute=if(dayOnly)null else it.minute?:540,
            endMinute=if(dayOnly||it.endDate==null)null else it.endMinute?:600,additional=emptyList(),recurrence=null,repeat=null,reminder=null)}
        val until=listOfNotNull(first.endDate,repeatUntil?.let(LocalDate::parse)).minOrNull()
        val repeat=when(mode) {
            "daily" ->ActivityRepeatRule(ActivityRepeatKind.DAILY,until=until,bounded=true)
            "weekly" ->ActivityRepeatRule(ActivityRepeatKind.WEEKLY,weekdays=weekdays,until=until,bounded=true)
            "monthly" ->ActivityRepeatRule(ActivityRepeatKind.MONTHLY,until=until,bounded=true)
            "interval" ->ActivityRepeatRule(ActivityRepeatKind.INTERVAL,intervalDays=interval.toInt(),until=until,bounded=true)
            else ->null
        }
        require(repeatUntil==null||LocalDate.parse(repeatUntil)>=first.date!!)
        val weekly=if(mode=="term")ActivityWeekRecurrence(requireNotNull(termId),termName,requireNotNull(anchor),weeks,weekdays,
            if(dayOnly)null else first.minute?:540,until,first.date,if(dayOnly||until!=first.endDate)null else first.endMinute)else null
        require(weekly==null||weekly.slots().isNotEmpty())
        return (if(weekly!=null)ActivityMemoTime(recurrence=weekly)else normalized.first().copy(additional=normalized.drop(1),repeat=repeat))
            .copy(remind=remind,location=location.trim(),reminder=ActivityMemoReminder(daysBefore.toInt(),reminderMinute,
                original?.reminder?.excludedWeekdays.orEmpty(),skippedDates,ReminderDisplayMode.valueOf(reminderMode),skippedOccurrences,skippedDays))
    }
    val memo=runCatching {builtMemo()}
    val valid=name.isNotBlank()&&(keepLegacy||memo.isSuccess)
    fun leave() {if(page=="main")onBack()else page="main"}
    BackHandler {leave()}
    Column(Modifier.fillMaxSize().background(colors.solid2).imePadding()) {
        QztcPageTopBar(when(page) {"repeat"->"重复";"reminder"->"提醒";else->title},onBack={leave()},actions={
            if(page=="main")QztcTopIconButton(Icons.Outlined.Check,"保存活动",enabled=valid,onClick={
                onSave(initial.copy(name=name.trim(),note=note.trim(),color=color,
                    memoTime=if(keepLegacy)null else builtMemo(),sessions=if(keepLegacy)initial.sessions else emptyList(),
                    kind=if(keepLegacy)initial.kind else ActivityKind.DAY))
            })
        })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=12.dp),
            verticalArrangement=Arrangement.spacedBy(14.dp)) {
            when(page) {
                "main" -> {
                    MemoPanel {
                        if(showRecognition) {
                            OutlinedTextField(input,{input=it},placeholder={Text("明天下午2点 在图书馆 开会")},
                                label={Text("自动识别安排")},modifier=Modifier.fillMaxWidth(),minLines=1)
                            recognition?.let {r ->
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                        if(r.title.isNotBlank())Text(r.title,style=QztcTypography.body)
                                        Text(listOfNotNull(r.date?.toString(),r.minute?.let(::clockLabel),
                                            r.location.takeIf(String::isNotBlank),r.note.takeIf(String::isNotBlank)).joinToString(" "),
                                            style=QztcTypography.meta,color=colors.mut)
                                    }
                                    TextButton(onClick={
                                        if(r.title.isNotBlank())name=r.title
                                        if(r.date!=null||r.minute!=null) {
                                            updateSlot(0,ActivityMemoTime(maxOf(r.date?:slots.first().date?:LocalDate.now(),LocalDate.now()),r.minute))
                                            undated=false;allDay=r.minute==null
                                        }
                                        if(r.location.isNotBlank())location=r.location
                                        if(r.note.isNotBlank())note=r.note
                                        input="";showRecognition=false
                                    }) {Text("应用")}
                                }
                            }
                        } else TextButton(onClick={showRecognition=true}) {Text("自动识别安排")}
                        OutlinedTextField(name,{name=it},label={Text("标题")},modifier=Modifier.fillMaxWidth())
                        OutlinedTextField(location,{location=it},label={Text("地点（可选）")},modifier=Modifier.fillMaxWidth())
                        OutlinedTextField(note,{note=it},label={Text("内容 / 备注（可选）")},modifier=Modifier.fillMaxWidth(),minLines=2)
                    }
                    if(keepLegacy)MemoPanel {
                        Text("现有时间安排",style=QztcTypography.section)
                        initial.sessions.forEach {Text(cn.qztc.timetable.core.domain.schedule.ActivityWeekLayout.sessionTime(it))}
                        TextButton(onClick={keepLegacy=false}) {Text("重新设置时间")}
                    } else MemoPanel {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text("全天事件",Modifier.weight(1f),style=QztcTypography.body)
                            Switch(allDay,{value ->
                                allDay=value
                                encoded=slots.map {s ->Json.encodeToString(s.copy(minute=if(value)null else s.minute?:540,
                                    endMinute=if(value||s.endDate==null)null else s.endMinute?:600))}
                            })
                        }
                        MemoSlotTimes(slots.first(),allDay,undated,{choosingDate=0 to it}) {
                            updateSlot(0,slots.first().copy(endDate=null,endMinute=null))
                        }
                    }
                    if(!keepLegacy&&!undated)MemoPanel {
                        MemoAction("重复",repeatLabel(mode,slots.size,interval)) {page="repeat"}
                        MemoRepeatSummary(mode,weeks,weekdays,slots,allDay,interval)
                        MemoAction("提醒",if(!remind)"不提醒" else "${if(daysBefore=="0")"当天" else "提前 $daysBefore 天"} ${clockLabel(reminderMinute)}") {page="reminder"}
                    }
                    MemoPanel {
                        Text("安排背景色",style=QztcTypography.section)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            presetColors.forEach {value ->
                                Box(Modifier.size(36.dp).clip(CircleShape).background(coursePresetColor(value)).clickable {color=value},
                                    contentAlignment=Alignment.Center) {if(value==color)Text("✓",color=colors.ink)}
                            }
                        }
                        if(presetColors.isEmpty())Text("可到显示与外观设置添加颜色",style=QztcTypography.meta)
                    }
                    if(!keepLegacy)MemoPanel {ActivitySchedulePreview(memo.getOrNull())}
                    onDelete?.let {TextButton(onClick={deleting=true}) {Text("删除活动",color=colors.danger)}}
                }
                "repeat" -> {
                    MemoPanel {
                        listOf("none" to "不重复","points" to "自定义多时间点","term" to "按周次","daily" to "每天",
                            "weekly" to "每周 / 选择星期","monthly" to "每月","interval" to "自定义间隔").forEach {(key,label) ->
                            Row(Modifier.fillMaxWidth().clickable(enabled=key!="term"||anchor!=null) {
                                mode=key;if(key=="weekly"&&weekdays.isEmpty())weekdays=listOf(slots.first().date!!.dayOfWeek.value)
                            }.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                                Text(label,Modifier.weight(1f),color=if(key=="term"&&anchor==null)colors.mut else colors.ink)
                                RadioButton(mode==key,{mode=key;if(key=="weekly"&&weekdays.isEmpty())weekdays=listOf(slots.first().date!!.dayOfWeek.value)},
                                    enabled=key!="term"||anchor!=null)
                            }
                        }
                    }
                    if(mode=="term"||mode=="weekly")MemoPanel {
                        if(mode=="term") {
                            Text("选择周次",style=QztcTypography.section)
                            Row {
                            TextButton(onClick={weeks=(1..totalWeeks).filter(::weekAllowed)}) {Text("全部周")}
                            TextButton(onClick={weeks=(1..totalWeeks).filter {it%2==1&&weekAllowed(it)}}) {Text("单周")}
                            TextButton(onClick={weeks=(1..totalWeeks).filter {it%2==0&&weekAllowed(it)}}) {Text("双周")}
                                TextButton(onClick={weeks=emptyList()}) {Text("清空")}
                            }
                            Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
                                QztcWeekGrid(weeks.toSet(),totalWeeks,columns=6,isWeekEnabled=::weekAllowed) {w ->weeks=if(w in weeks)weeks-w else (weeks+w).sorted()}
                            }
                        }
                        Text("选择星期",style=QztcTypography.section)
                        Row {
                            TextButton(onClick={weekdays=(1..5).filter(::weekdayAllowed)}) {Text("周一至周五")}
                            TextButton(onClick={weekdays=(1..7).filter(::weekdayAllowed)}) {Text("每天")}
                            TextButton(onClick={weekdays=emptyList()}) {Text("清空")}
                        }
                        WeekdayChoices(weekdays,isEnabled=::weekdayAllowed) {weekdays=it}
                        Text("可点选单独星期，也可组合连续星期",style=QztcTypography.meta,color=colors.mut)
                    }
                    if(mode=="interval")MemoPanel {
                        OutlinedTextField(interval,{interval=it.filter(Char::isDigit).take(4)},label={Text("每隔几天")},suffix={Text("天")},
                            singleLine=true,modifier=Modifier.fillMaxWidth())
                    }
                    if(mode=="points") {
                        slots.forEachIndexed {index,slot ->
                            MemoPanel {
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    Text("时间点 ${index+1}",Modifier.weight(1f),style=QztcTypography.section)
                                    if(index>0)TextButton(onClick={encoded=encoded.filterIndexed {i,_ ->i!=index}}) {Text("移除")}
                                }
                                if(index==0) {
                                    fun label(date:LocalDate?,minute:Int?)=date?.toString()?.let {it+if(allDay)""else" "+clockLabel(minute?:540)}?:"未设置"
                                    Text("开始时间：${label(slot.date,slot.minute)}",style=QztcTypography.body)
                                    Text("结束时间：${label(slot.endDate,slot.endMinute)}",style=QztcTypography.body)
                                } else MemoSlotTimes(slot,allDay,false,{choosingDate=index to it}) {
                                    updateSlot(index,slot.copy(endDate=null,endMinute=null))
                                }
                            }
                        }
                        OutlinedButton(enabled=slots.size<50,onClick={
                            val day=(slots.last().endDate?:slots.last().date!!).plusDays(1)
                            encoded=encoded+Json.encodeToString(ActivityMemoTime(day,if(allDay)null else 540))
                        },modifier=Modifier.fillMaxWidth()) {Text("添加时间点")}
                    }
                    if(mode !in listOf("none","points"))MemoPanel {
                        if(slots.first().endDate!=null)Text("重复安排不超出开始至结束时间范围",style=QztcTypography.meta,color=colors.mut)
                        MemoAction("重复结束日期",repeatUntil?:"不设置") {choosingRepeatEnd=true}
                        if(repeatUntil!=null)TextButton(onClick={repeatUntil=null}) {Text("留空")}
                    }
                }
                "reminder" -> {
                    if(mode!="none"||!allDay)MemoPanel {MemoRepeatSummary(mode,weeks,weekdays,slots,allDay,interval)}
                    MemoPanel {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text("启用提醒",Modifier.weight(1f),style=QztcTypography.body);Switch(remind,{remind=it})
                        }
                        if(remind) {
                            OutlinedTextField(daysBefore,{daysBefore=it.filter(Char::isDigit).take(3)},label={Text("提前天数（0 为当天）")},
                                suffix={Text("天")},singleLine=true,modifier=Modifier.fillMaxWidth())
                            MemoAction("提醒时分",clockLabel(reminderMinute)) {choosingReminderTime=true}
                            Text("每次重复安排都会按此规则提醒",style=QztcTypography.meta,color=colors.mut)
                        }
                    }
                    if(remind) {
                        MemoPanel {
                            Text("提醒方式",style=QztcTypography.section)
                            QztcReminderModeOptions(reminderMode==ReminderDisplayMode.SCREEN_POPUP.name,{popup ->
                                reminderMode=(if(popup)ReminderDisplayMode.SCREEN_POPUP else ReminderDisplayMode.NOTIFICATION_BAR).name
                                preview(popup)
                            },previewState,{preview()},{onSystemReminderSettings(reminderMode==ReminderDisplayMode.SCREEN_POPUP.name)})
                        }
                        MemoPanel {
                            Text("这些星期不提醒",style=QztcTypography.section)
                            val reminderWeekdays=if(mode in listOf("weekly","term"))weekdays.distinct().sorted()else (1..7).toList()
                            WeekdayChoices(skippedDays,visibleDays=reminderWeekdays) {skippedDays=it}
                            Text("按安排发生的星期选择，提前提醒也会一并排除",style=QztcTypography.meta,color=colors.mut)
                            ActivitySchedulePreview(memo.getOrNull(),selectExclusions=true) {date ->
                                val text=date.toString()
                                skippedOccurrences=if(text in skippedOccurrences)skippedOccurrences-text else if(skippedOccurrences.size<366)(skippedOccurrences+text).sorted()else skippedOccurrences
                            }
                            if(skippedOccurrences.isNotEmpty())TextButton(onClick={skippedOccurrences=emptyList()}) {Text("清空不提醒的安排日期")}
                            skippedDates.forEach {date ->Row(verticalAlignment=Alignment.CenterVertically) {
                                Text(date,Modifier.weight(1f));TextButton(onClick={skippedDates=skippedDates-date}) {Text("移除")}
                            }}
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    choosingDate?.let {(index,end) ->
        val slot=slots[index]
        val start=slot.date?:LocalDate.now()
        val bounds=memoEndpointBounds(slot,end,allDay,LocalDate.now())
        val impossible=bounds.first!=null&&bounds.second!=null&&bounds.first!!>bounds.second!!
        QztcDateTimePicker(if(end)"结束时间"else"开始时间",
            if(end)slot.endDate?:start else start,
            if(allDay)null else (if(end)slot.endMinute?:((slot.minute?:540)+60).coerceAtMost(1439)else slot.minute?:540),
            bounds.first,if(impossible)null else bounds.second,onDismiss={choosingDate=null}) {date,minute ->
            undated=false
            updateSlot(index,if(end)slot.copy(endDate=date,endMinute=minute)else if(impossible)slot.copy(date=date,minute=minute,endDate=null,endMinute=null)else slot.copy(date=date,minute=minute))
            choosingDate=null
        }
    }
    if(choosingRepeatEnd)QztcDateTimePicker("重复结束日期",repeatUntil?.let(LocalDate::parse)?:slots.first().endDate?:slots.first().date!!,null,
        slots.first().date!!.atStartOfDay(),slots.first().endDate?.atTime(LocalTime.MAX),onDismiss={choosingRepeatEnd=false}) {date,_ ->
        trimSelections(slots,date);choosingRepeatEnd=false
    }
    if(choosingReminderTime)QztcTimePicker(LocalTime.of(reminderMinute/60,reminderMinute%60),onDismiss={choosingReminderTime=false}) {
        reminderMinute=it.hour*60+it.minute;choosingReminderTime=false
    }
    if(deleting)AlertDialog(onDismissRequest={deleting=false},title={Text("删除活动？")},
        confirmButton={TextButton(onClick={deleting=false;onDelete?.invoke()}) {Text("删除")}},
        dismissButton={TextButton(onClick={deleting=false}) {Text("取消")}})
}

@Composable
private fun MemoPanel(content:@Composable ColumnScope.()->Unit) {
    val colors=LocalQztcColors.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.solid).padding(16.dp),
        verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
}
@Composable
private fun MemoAction(title:String,value:String,onClick:()->Unit) {
    val colors=LocalQztcColors.current
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(title,Modifier.weight(1f),style=QztcTypography.body,color=colors.ink)
        Text(value,style=QztcTypography.meta,color=colors.mut,modifier=Modifier.widthIn(max=200.dp))
        Icon(Icons.Outlined.ChevronRight,null,tint=colors.mut,modifier=Modifier.size(18.dp))
    }
}
@Composable
private fun WeekdayChoices(selected:List<Int>,isEnabled:(Int)->Boolean={true},visibleDays:List<Int> = (1..7).toList(),onChange:(List<Int>)->Unit) {
    val colors=LocalQztcColors.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val cell=(maxWidth-6.dp*6)/7
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        visibleDays.forEach {day ->
            Box(Modifier.width(cell).height(40.dp).testTag("memo-weekday-$day").clip(RoundedCornerShape(8.dp))
                .background(if(day in selected)colors.accent else colors.solid2)
                .clickable(enabled=isEnabled(day)||day in selected) {onChange(if(day in selected)selected-day else (selected+day).sorted())},contentAlignment=Alignment.Center) {
                Text("周${"一二三四五六日"[day-1]}",style=QztcTypography.meta,
                    color=if(!isEnabled(day))colors.faint else if(day in selected)colors.onAccent else colors.ink,maxLines=1)
            }
        }
    }
    }
}

@Composable
private fun MemoSlotTimes(slot:ActivityMemoTime,allDay:Boolean,undated:Boolean,onChoose:(Boolean)->Unit,onClear:()->Unit) {
    fun label(date:LocalDate?,minute:Int?)=date?.toString()?.let {it+if(allDay)""else" "+clockLabel(minute?:540)}?:"未设置"
    MemoAction("开始时间",if(undated)"未设置"else label(slot.date,slot.minute)) {onChoose(false)}
    MemoAction("结束时间",label(slot.endDate,slot.endMinute)) {onChoose(true)}
    TextButton(enabled=slot.endDate!=null,onClick=onClear) {Text("不设置结束时间")}
}

@Composable
private fun MemoRepeatSummary(mode:String,weeks:List<Int>,weekdays:List<Int>,slots:List<ActivityMemoTime>,allDay:Boolean,interval:String) {
    val colors=LocalQztcColors.current
    val lines=activityRepeatSummary(mode,weeks,weekdays,slots,allDay,interval)
    lines.forEach {Text(it,style=QztcTypography.meta,color=colors.mut)}
}
private fun clockLabel(value:Int)="%02d:%02d".format(value/60,value%60)
private fun repeatLabel(mode:String,count:Int,interval:String)=when(mode) {
    "points"->"自定义多时间点 · $count 个";"term"->"按周次";"daily"->"每天";"weekly"->"每周 / 指定星期";"monthly"->"每月";"interval"->"每 $interval 天";else->"不重复"
}

/** Crossing an endpoint moves its counterpart to the adjacent day. */
fun adjustMemoEndpoint(entry:ActivityMemoTime,end:Boolean,date:LocalDate,minute:Int?):ActivityMemoTime {
    if(entry.endDate==null)return entry.copy(date=date,minute=minute)
    val first=if(end)entry.date!! else date
    val last=if(end)date else entry.endDate!!
    val firstMinute=if(end)entry.minute else minute
    val lastMinute=if(end)minute else entry.endMinute
    val crossed=last<first||(last==first&&firstMinute!=null&&lastMinute!!<=firstMinute)
    val tooLong=java.time.temporal.ChronoUnit.DAYS.between(first,last)>366
    return entry.copy(date=if(crossed&&end)last.minusDays(1) else if(tooLong&&end)last.minusDays(366) else first,
        endDate=if(crossed&&!end)first.plusDays(1) else if(tooLong&&!end)first.plusDays(366) else last,minute=firstMinute,endMinute=lastMinute)
}
fun activityColorCss(value:Int):String="#%06x".format(coursePresetColor(value).toArgb() and 0xffffff)
