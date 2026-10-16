package cn.qztc.timetable.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.domain.calendar.*
import cn.qztc.timetable.core.domain.model.*
import cn.qztc.timetable.core.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private data class ManagedActivity(val id:String,val title:String,val detail:String,val dates:Set<LocalDate>,
    val school:Boolean=false,val color:Int?=null,val last:LocalDate?=dates.maxOrNull(),val endless:Boolean=false)
private data class ManagedSection(val key:String,val label:String,val rows:List<ManagedActivity>)

@Composable
fun ActivityManagementScreen(activities:List<ScheduleActivity>,semester:Semester?,semesterOptions:List<Semester>,
    academicDays:List<AcademicDay>,onBack:()->Unit,onEdit:(String)->Unit,onAdd:(LocalDate)->Unit,
    activityColor:(Int)->Color,onDelete:(Set<String>)->Unit = {},presetColors:List<Int> = emptyList(),
    onColor:(String,Int)->Unit = {_,_->}) {
    val colors=LocalQztcColors.current
    val context=LocalContext.current
    val prefs=remember(context) {context.getSharedPreferences("activity-management",0)}
    var removedSchool by remember {mutableStateOf(prefs.getStringSet("removed-school",emptySet()).orEmpty().toSet())}
    var mode by rememberSaveable {mutableStateOf("date")}
    var modeMenu by remember {mutableStateOf(false)}
    var today by remember {mutableStateOf(LocalDate.now())}
    LaunchedEffect(Unit) {while(true) {delay(30_000);today=LocalDate.now()}}
    val term=semester?:semesterOptions.firstOrNull()
    var showSchool by rememberSaveable {mutableStateOf(false)}
    var deleting by rememberSaveable {mutableStateOf(false)}
    var selected by remember {mutableStateOf(emptySet<String>())}
    var filterStart by rememberSaveable(mode) {mutableStateOf<String?>(null)}
    var filterEnd by rememberSaveable(mode) {mutableStateOf<String?>(null)}
    var choosing by remember {mutableStateOf<Int?>(null)}
    var colorId by remember {mutableStateOf<String?>(null)}
    var colorOverrides by remember {mutableStateOf(emptyMap<String,Int>())}
    var sharedColors by remember {mutableStateOf(CalendarCardColors.read(context))}
    DisposableEffect(context) {
        val prefs=CalendarCardColors.preferences(context)
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener {_,_->sharedColors=CalendarCardColors.read(context)}
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    fun colorKey(row:ManagedActivity)=if(row.id.startsWith("school-day:"))"school:${row.title}:${row.dates.minOrNull()?.year}"
        else row.id.removePrefix("school:").takeIf {row.id.startsWith("school:calendar-event:")}?:row.id
    fun deleteIds(ids:Set<String>) {
        val school=ids.filter {it.startsWith("school:")||it.startsWith("school-day:")}.toSet()
        if(school.isNotEmpty()) {
            removedSchool=removedSchool+school
            prefs.edit().putStringSet("removed-school",removedSchool).apply()
        }
        onDelete(ids-school)
        selected=emptySet();deleting=false
    }
    BackHandler(deleting) {deleting=false;selected=emptySet()}
    val school=remember(context,term?.id) {SchoolCalendarApi.cached(context,
        term?.startDate?.let {val y=it.year-if(it.monthValue<9)1 else 0;"$y-${y+1}"}?:CourseArrangementProfile.schoolYear(),"activity-management")}
    val allRows=remember(activities,term,school,academicDays,today) {buildList {
        activities.filterNot {it.hiddenFromSchedule||it.id.startsWith("academic:")||it.id.startsWith("calendar-event:")||
            (it.memoTime==null&&term!=null&&it.semesterId!=term.id)}.forEach {a ->
            val memo=a.memoTime
            add(ManagedActivity(a.id,a.name,listOf(memo?.location.orEmpty(),a.note).filter(String::isNotBlank).joinToString("\n"),
                managedActivityDates(a,term).toSet(),color=a.color,last=managedActivityEnd(a,term)?.toLocalDate(),
                endless=memo?.repeat?.let {it.until==null&&(!it.bounded||memo.endDate==null)}==true))
        }
        val events=calendarImportEvents(school?.events.orEmpty())
        events.forEach {e ->
            val dates=generateSequence(e.startDate) {if(it<(e.endDate?:e.startDate))it.plusDays(1)else null}.toSet()
            add(ManagedActivity("school:${e.id}",e.name,e.detail,dates,true))
        }
        activities.filter {it.id.startsWith("calendar-event:")&&!it.hiddenFromSchedule}.forEach {a ->
            if(events.none {a.id.endsWith(":${it.id}")||a.id.endsWith(":${it.id}:end")})
                add(ManagedActivity("school:${a.id}",a.name,a.note,managedActivityDates(a,term).toSet(),true,a.color))
        }
        val normalized=managedSchoolDayRanges((school?.days.orEmpty()+academicDays).distinctBy {"${it.date}:${it.kind}"}).flatMap {it.days}
        buildCalendarHolidaySchedules(normalized).forEach {s ->
            val dates=calendarScheduleDays(s).map {it.date}.toSet()
            val name=s.holiday?.name?:s.makeup.first().name.let {if(it.contains("调休"))it else "${holidayNameKey(it)}调休"}
            val detail=listOfNotNull(s.holiday?.let {"放假："+calendarDateRangeLabel(it.start,it.end)},
                s.makeup.takeIf {it.isNotEmpty()}?.joinToString("、",prefix="调休：") {calendarDateRangeLabel(it.start,it.end)}).joinToString("\n")
            add(ManagedActivity("school-day:${dates.minOrNull()}:$name",name,detail,dates,true))
        }
    }.distinctBy {it.id}.sortedWith(compareBy<ManagedActivity> {it.dates.minOrNull()?:LocalDate.MAX}.thenBy {it.title})}
    val visible=allRows.filter {it.id !in removedSchool&&(!it.school||showSchool)}
    val marked=visible.flatMap {it.dates}.toSet()
    val markedWeeks=term?.let {t ->marked.mapNotNull {SemesterCalendar.weekOf(t,it)}.toSet()}.orEmpty()
    val rows=visible.filter {val expired=!it.endless&&it.last?.isBefore(today)==true;expired==(mode=="expired")}
    val sections=remember(rows,mode,filterStart,filterEnd,term,today) {
        val first=filterStart?.let(LocalDate::parse)
        val last=filterEnd?.let(LocalDate::parse)?:first
        val dated=linkedMapOf<LocalDate,MutableList<ManagedActivity>>()
        val undated=mutableListOf<ManagedActivity>()
        if(first==null&&(mode=="date"||mode=="week"))dated[if(mode=="week")today.minusDays((today.dayOfWeek.value-1).toLong())else today]=mutableListOf()
        rows.forEach {row ->
            if(row.dates.isEmpty())undated+=row
            else if(mode=="semester"||mode=="expired") {
                if(mode=="expired"||term==null||row.dates.any {SemesterCalendar.weekOf(term,it)!=null})
                    dated.getOrPut(row.dates.minOrNull()!!) {mutableListOf()}.add(row)
            } else row.dates.filter {day ->
                val lower=first?:if(last!=null)LocalDate.MIN else if(mode=="week")today.minusDays((today.dayOfWeek.value-1).toLong())else today
                day>=lower&&(last==null||day<=if(mode=="week")last.plusDays(6)else last)
            }.groupBy {day ->if(mode=="week")day.minusDays((day.dayOfWeek.value-1).toLong())else day}
                .forEach {(day,_) ->dated.getOrPut(day) {mutableListOf()}.add(row)}
        }
        buildList {
            dated.toSortedMap().forEach {(date,list) ->
                val label=if(mode=="week") {
                    val week=term?.let {SemesterCalendar.weekOf(it,date)}
                    "${week?.let {"第 $it 周 - "}?: ""}${date.format(DateTimeFormatter.ofPattern("M月d日"))}"
                } else date.format(DateTimeFormatter.ofPattern("M月d日"))+" · 周${"一二三四五六日"[date.dayOfWeek.value-1]}"
                add(ManagedSection(date.toString(),label,list.distinctBy {it.id}))
            }
            if(undated.isNotEmpty()&&first==null)add(ManagedSection("undated","未定日期",undated))
        }
    }
    Column(Modifier.fillMaxSize().background(colors.solid2)) {
        run {
            val title=mapOf("date" to "按日期","week" to "按周次","semester" to "整学期","expired" to "已过期")[mode]!!
            QztcPageTopBar("活动安排",onBack=onBack,centerContent={
                Box {
                    Text("$title ▾",Modifier.clickable {modeMenu=true}.padding(horizontal=8.dp,vertical=10.dp),style=QztcTypography.body,
                        color=cn.qztc.timetable.core.ui.glass.LocalGlassForeground.current?:colors.ink)
                    DropdownMenu(modeMenu,{modeMenu=false}) {
                        listOf("date" to "按日期","week" to "按周次","semester" to "整学期","expired" to "已过期").forEach {(key,label) ->
                            DropdownMenuItem(text={Text(label)},enabled=key!="week"||term!=null,onClick={
                                mode=key;modeMenu=false;deleting=false;selected=emptySet();filterStart=null;filterEnd=null
                            })
                        }
                    }
                }
            },actions={
                if(mode=="expired")QztcTopIconButton(Icons.Outlined.DeleteOutline,"删除过期安排",enabled=!deleting,onClick={deleting=true})
                else QztcTopIconButton(if(showSchool)Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                    if(showSchool)"隐藏校历节点" else "显示校历节点",onClick={showSchool=!showSchool})
                QztcTopIconButton(Icons.Outlined.Add,"添加活动",onClick={onAdd(today)})
            })
        }
        if(mode=="date"||mode=="week") {
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically) {
                repeat(2) {index ->
                    val value=if(index==0)filterStart else filterEnd
                    if(index==1)Text("–",Modifier.padding(horizontal=6.dp),color=colors.mut)
                    Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically) {
                        OutlinedButton(onClick={choosing=index},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=12.dp,vertical=0.dp)) {
                            Text(value?.let {d ->val date=LocalDate.parse(d)
                                if(mode=="week")"第 ${term?.let {SemesterCalendar.weekOf(it,date)}?:1} 周 - ${date.format(DateTimeFormatter.ofPattern("M月d日"))}"
                                else date.format(DateTimeFormatter.ofPattern("M月d日"))
                            }?:if(mode=="week")(if(index==0)"开始周次"else"结束周次")else(if(index==0)"开始日期"else"结束日期"),Modifier.weight(1f),style=QztcTypography.meta)
                            if(value!=null)IconButton(onClick={if(index==0)filterStart=null else filterEnd=null},modifier=Modifier.size(32.dp)) {
                                Icon(Icons.Outlined.Close,"清空${if(index==0)"开始" else "结束"}筛选",Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.End) {
            if(mode=="expired") {
                if(deleting) {
                    Text("已选择 ${selected.size} 项",Modifier.weight(1f),style=QztcTypography.meta,color=colors.mut)
                    val selectable=sections.flatMap {it.rows}.map {it.id}.toSet()
                    TextButton(enabled=selectable.isNotEmpty(),onClick={selected=selectable}) {Text("全选")}
                    TextButton(onClick={deleting=false;selected=emptySet()}) {Text("取消")}
                    TextButton(enabled=selected.isNotEmpty(),onClick={deleteIds(selected)}) {Text("确认")}
                }
            }
        }
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            sections.forEach {section ->
                item("header:${section.key}") {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(section.label,Modifier.weight(1f),style=QztcTypography.section,color=colors.mut)
                    Text("${section.rows.size} 项",style=QztcTypography.meta,color=colors.mut)
                }}
                if(section.rows.isEmpty())item("empty:${section.key}") {Text(if(mode=="week")"本周暂无安排"else"今天暂无安排",style=QztcTypography.meta,color=colors.mut)}
                items(section.rows,key={"${section.key}:${it.id}"}) {row ->
                    val shared=sharedColors[colorKey(row)]?.let {Color(android.graphics.Color.parseColor(it))}
                    val baseColor=colorOverrides[row.id]?.let(activityColor)?:shared?:row.color?.let(activityColor)
                    val bg=if(row.school)Color.White else baseColor?.copy(alpha=.22f)?.compositeOver(colors.solid)?:colors.solid
                    val ink=if(bg.luminance()>.179f)Color(0xFF172126)else Color.White
                    ManagedSwipeDelete(row.id,mode=="expired"&&!deleting,{deleteIds(setOf(row.id))}) {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(bg)
                            .clickable(enabled=deleting) {selected=if(row.id in selected)selected-row.id else selected+row.id}
                            .padding(horizontal=12.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically) {
                            if(deleting)Checkbox(row.id in selected,{checked ->selected=if(checked)selected+row.id else selected-row.id},Modifier.size(36.dp))
                            Column(Modifier.weight(1f).padding(horizontal=4.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                Text(row.title,style=QztcTypography.courseTitle,color=ink)
                                val memo=activities.firstOrNull {it.id==row.id}?.memoTime
                                val sectionDate=runCatching {LocalDate.parse(section.key)}.getOrNull()
                                val times=if(memo!=null&&sectionDate!=null) {
                                    if(mode=="week")(0L..6L).flatMap {n ->val day=sectionDate.plusDays(n);memo.onDate(day).map {s ->
                                        day.format(DateTimeFormatter.ofPattern("M月d日"))+s.minute?.let {" %02d:%02d".format(it/60,it%60)}.orEmpty()
                                    }}.distinct().joinToString("、")
                                    else if(mode=="date")memo.onDate(sectionDate).mapNotNull {s ->s.minute?.let {"%02d:%02d".format(it/60,it%60)}}.distinct().joinToString("、")
                                    else memo.label()
                                } else ""
                                if(times.isNotBlank())Text(times,style=QztcTypography.meta,color=ink.copy(alpha=.8f))
                                if(row.detail.isNotBlank())Text(row.detail,style=QztcTypography.meta,color=ink.copy(alpha=.73f))
                            }
                            if(!deleting&&!row.school&&mode!="expired") {
                                TextButton(onClick={onEdit(row.id)},modifier=Modifier.testTag("management-edit-${row.id}")) {Text("编辑",color=ink)}
                            }
                        }
                    }
                }
            }
            if(sections.isEmpty())item {Text(if(mode=="expired")"暂无已过期安排"else"暂无活动安排",color=colors.mut)}
        }
    }
    choosing?.let {index ->
        val selectedDate=(if(index==0)filterStart else filterEnd)?.let(LocalDate::parse)?:today
        fun choose(day:LocalDate) {
            if(index==0) {filterStart=day.toString();if(filterEnd?.let {LocalDate.parse(it)<day}==true)filterEnd=day.toString()}
            else {filterEnd=day.toString();if(filterStart?.let {LocalDate.parse(it)>day}==true)filterStart=day.toString()}
            choosing=null
        }
        if(mode=="week"&&term!=null)QztcWeekPicker((SemesterCalendar.weekOf(term,selectedDate)?:1).coerceIn(1,term.totalWeeks),
            term.totalWeeks,markedWeeks,onDismiss={choosing=null}) {choose(SemesterCalendar.dateOf(term,it,1))}
        else QztcDatePicker(selectedDate,onDismiss={choosing=null},markedDates=marked,referenceDates=buildSet {
            filterStart?.let {add(LocalDate.parse(it))}
            filterEnd?.let {add(LocalDate.parse(it))}
        }) {choose(it)}
    }
    colorId?.let {id ->
        AlertDialog(onDismissRequest={colorId=null},title={Text("编辑活动")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(allRows.firstOrNull {it.id==id}?.title.orEmpty(),style=QztcTypography.body)
            Text("安排背景色",style=QztcTypography.meta)
            FlowPalette(presetColors,colorOverrides[id]?:activities.firstOrNull {it.id==id}?.color,activityColor) {value ->
                colorOverrides=colorOverrides+(id to value)
                val row=allRows.firstOrNull {it.id==id}
                if(row?.school==true) {
                    CalendarCardColors.set(context,colorKey(row),"#%06x".format(activityColor(value).toArgb() and 0xffffff))
                    sharedColors=CalendarCardColors.read(context)
                } else onColor(id,value)
                colorId=null
            }
        }},confirmButton={TextButton(onClick={colorId=null}) {Text("关闭")}})
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowPalette(presets:List<Int>,selected:Int?,color:(Int)->Color,onChoose:(Int)->Unit) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        presets.forEach {value ->
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(21.dp)).background(color(value)).clickable {onChoose(value)},contentAlignment=Alignment.Center) {
                if(value==selected)Text("✓",color=if(color(value).luminance()>.179f)Color(0xFF172126)else Color.White)
            }
        }
    }
}

@Composable
private fun ManagedSwipeDelete(id:String,enabled:Boolean,onDelete:()->Unit,content:@Composable ()->Unit) {
    var drag by remember(id) {mutableFloatStateOf(0f)}
    val offset by animateFloatAsState(drag,label="activity-delete")
    val threshold=with(LocalDensity.current) {48.dp.toPx()}
    val latest by rememberUpdatedState(onDelete)
    val colors=LocalQztcColors.current
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).pointerInput(id,enabled) {
        if(enabled)detectHorizontalDragGestures(onHorizontalDrag={change,amount ->
            change.consume();drag=(drag+amount).coerceIn(0f,threshold*1.25f)
        },onDragEnd={if(drag>=threshold)latest();drag=0f},onDragCancel={drag=0f})
    }) {
        if(offset>0)Box(Modifier.matchParentSize().background(colors.danger).padding(start=18.dp),contentAlignment=Alignment.CenterStart) {
            Text("删除",color=Color.White,style=QztcTypography.body)
        }
        Box(Modifier.offset {IntOffset(offset.roundToInt(),0)}) {content()}
    }
}
