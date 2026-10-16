package cn.qztc.timetable

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import cn.qztc.timetable.core.domain.calendar.*
import cn.qztc.timetable.core.domain.model.*
import cn.qztc.timetable.core.ui.theme.QztcTheme
import cn.qztc.timetable.feature.settings.ActivityManagementScreen
import cn.qztc.timetable.feature.settings.ReminderSettingsScreen
import cn.qztc.timetable.feature.settings.ReminderTestState
import cn.qztc.timetable.feature.timetable.*
import java.io.File
import java.time.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** No real activity, preferences or database are changed by these UI fixtures. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
class CalendarFollowupQaActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val mode=intent.getStringExtra("mode")?:"editor"
        if(mode=="adskip-notify") {
            val manager=getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(android.app.NotificationChannel("qa-navigation","跳转验收",android.app.NotificationManager.IMPORTANCE_LOW))
            val intent=cn.qztc.timetable.adskip.adSkipManagementIntent(this)
            val pending=android.app.PendingIntent.getActivity(this,86016,intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
            manager.notify(86016,androidx.core.app.NotificationCompat.Builder(this,"qa-navigation")
                .setSmallIcon(cn.qztc.timetable.R.drawable.ic_notification).setContentTitle("广告辅助通知跳转验收")
                .setContentText("使用后台通知的实际目标，不修改无障碍设置").setContentIntent(pending).setAutoCancel(true).build())
            finish();return
        }
        if(mode=="notify") {
            cn.qztc.timetable.notifications.QztcNotifications.notifyCourseReminder(this,"跳转验收课程","合成通知，不修改课表",86014,
                ReminderDisplayMode.NOTIFICATION_BAR,LocalDate.now().toString())
            finish();return
        }
        if(mode=="notification-cleanup") {
            getSystemService(android.app.NotificationManager::class.java).cancel(86014)
            getSystemService(android.app.NotificationManager::class.java).cancel(86016)
            finish();return
        }
        val term=Semester("qa","验收秋季",2026,Term.AUTUMN,LocalDate.of(2026,8,31),20)
        val date=LocalDate.now()
        val isolated=object:ContextWrapper(this) {
            override fun getApplicationContext():Context=this
            override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences("followup-qa-$name",mode)
        }
        setContent {QztcTheme {CompositionLocalProvider(LocalContext provides isolated) {Box(Modifier.fillMaxSize().semantics {testTagsAsResourceId=true}) {
            if(mode=="reminders") {
                var state by remember {mutableStateOf(CourseReminderUiState(enabled=true))}
                ReminderSettingsScreen(state,emptyList(),"隔离提醒验收",{finish()},
                    {state=state.copy(enabled=it)},{state=state.copy(minutes=it)},
                    {state=state.copy(displayMode=it)},ReminderTestState(),{},{},{_,_,_->},
                    onRemindOnHolidaysChange={state=state.copy(remindOnHolidays=it)})
            } else if(mode=="management") {
                var plans by remember {mutableStateOf(listOf(ScheduleActivity("fixture","calendar","合成活动",kind=ActivityKind.DAY,color=0,
                    memoTime=ActivityMemoTime(date,0,additional=listOf(ActivityMemoTime(date.plusWeeks(2),600)))),
                    ScheduleActivity("old-a","calendar","过期活动甲",kind=ActivityKind.DAY,memoTime=ActivityMemoTime(date.minusDays(1))),
                    ScheduleActivity("old-b","calendar","过期活动乙",kind=ActivityKind.DAY,memoTime=ActivityMemoTime(date.minusDays(2))),
                    ScheduleActivity("old-c","calendar","过期活动丙",kind=ActivityKind.DAY,memoTime=ActivityMemoTime(date.minusDays(3))),
                    ScheduleActivity("calendar-event:qa:sports","qa","校运会",kind=ActivityKind.DAY,startDate=date.plusDays(2),endDate=date.plusDays(3))))}
                val days=(1..7).map {n ->AcademicDay("qa-$n",if(n%2==0)"a" else "b",LocalDate.of(2026,10,n),
                    AcademicDayKind.HOLIDAY,"国庆节",sourceWeek=n)} +
                    AcademicDay("qa-makeup","qa",LocalDate.of(2026,10,10),AcademicDayKind.MAKEUP,"国庆调休",useCurrentWeek=true)
                ActivityManagementScreen(plans,term,listOf(term),days,{finish()},{},{},
                    activityColor={Color(android.graphics.Color.parseColor(activityColorCss(it)))},
                    presetColors=(0..8).toList(),onColor={id,value ->plans=plans.map {if(it.id==id)it.copy(color=value)else it}},
                    onDelete={ids ->
                        plans=plans.filterNot {it.id in ids}
                        File(filesDir,"followup-deleted-activities.json").writeText(Json.encodeToString(ids.toList()))
                    })
            } else {
                ActivityMemoEditor("添加活动","fixture",ActivityEditDraft(name="合成活动",kind=ActivityKind.DAY,
                    weekday=1,periodStart=1,periodEnd=2,weeks=emptyList(),startDate=date,endDate=date,color=0,
                    memoTime=if(mode=="bounded")ActivityMemoTime(date,600,date.plusDays(3),540,
                        repeat=ActivityRepeatRule(ActivityRepeatKind.DAILY,until=date.plusDays(3),bounded=true),
                        remind=true,reminder=ActivityMemoReminder(daysBefore=2))
                    else if(mode=="trim")ActivityMemoTime(date,600,
                        repeat=ActivityRepeatRule(ActivityRepeatKind.WEEKLY,weekdays=(1..7).toList(),until=date.plusDays(21),bounded=true),
                        remind=true,reminder=ActivityMemoReminder(daysBefore=2,excludedOccurrenceWeekdays=listOf(1,5),
                            excludedOccurrenceDates=listOf(date.plusDays(1).toString(),date.plusDays(10).toString())))
                    else if(mode=="weekly")ActivityMemoTime(date,600,endDate=date.plusDays(21),endMinute=600,
                        repeat=ActivityRepeatRule(ActivityRepeatKind.WEEKLY,weekdays=listOf(1,5),until=date.plusDays(21),bounded=true),
                        remind=true,reminder=ActivityMemoReminder(daysBefore=2))else null),
                    onBack={finish()},onSave={draft ->
                        File(filesDir,"followup-activity-plan.json").writeText(Json.encodeToString(draft.toScheduleActivity("calendar")))
                        finish()
                    },presetColors=(0..8).toList(),semester=term,
                    onPreviewReminder={popup,title,detail ->cn.qztc.timetable.reminder.activityReminderPreview(this@CalendarFollowupQaActivity,popup,title,detail)},
                    onSystemReminderSettings={popup ->cn.qztc.timetable.reminder.openActivityReminderSettings(this@CalendarFollowupQaActivity,popup)})
            }
        } } } }
    }
}
