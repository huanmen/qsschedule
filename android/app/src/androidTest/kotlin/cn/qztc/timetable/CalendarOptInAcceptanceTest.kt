package cn.qztc.timetable

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.widget.DatePicker
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAction
import androidx.test.espresso.UiController
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.platform.app.InstrumentationRegistry
import cn.qztc.timetable.core.domain.calendar.AcademicDay
import cn.qztc.timetable.core.domain.model.*
import cn.qztc.timetable.core.ui.theme.QztcTheme
import cn.qztc.timetable.feature.settings.*
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CalendarOptInAcceptanceTest {
    @get:Rule val compose=createAndroidComposeRule<FeatureAcceptanceActivity>()
    private val events=CopyOnWriteArrayList<SchoolCalendarEvent>()
    private val removals=CopyOnWriteArrayList<SchoolCalendarEvent>()
    private val holidayChanges=CopyOnWriteArrayList<Pair<List<AcademicDay>,List<AcademicDay>>>()
    private val detectedSemesters=CopyOnWriteArrayList<Pair<DetectedSchoolSemester,Boolean>>()
    private val visible=mutableStateOf(true)
    private val eventId="qa-sports-${UUID.randomUUID()}"
    private val semester=Semester("qa-calendar-${UUID.randomUUID()}","2026 秋",2026,Term.AUTUMN,LocalDate.of(2026,8,31),20)

    private fun fixture(test: () -> Unit) {
        val context=compose.activity
        val files=listOf("school-calendar","calendar-event-edits","calendar-inclusion","calendar-candidates","calendar-refresh")
        val backups=files.associateWith { context.getSharedPreferences(it,0).all.toMap() }
        try {
            val json=org.json.JSONObject(context.assets.open("school-calendar-2026-2027.json").bufferedReader().use { it.readText() })
            json.put("events",org.json.JSONArray().put(org.json.JSONObject()
                .put("id",eventId).put("schoolYear","2026-2027").put("semesterLabel","第一学期")
                .put("type","SPORTS_MEETING").put("name","测试运动会").put("startDate","2026-09-21").put("endDate","2026-09-22")))
            context.getSharedPreferences("school-calendar",0).edit().putString("year:2026-2027",json.toString()).commit()
            context.getSharedPreferences("calendar-candidates",0).edit().clear().commit()
            context.getSharedPreferences("calendar-inclusion",0).edit().clear().commit()
            context.getSharedPreferences("calendar-refresh",0).edit().putString(semester.id,LocalDate.now().toString()).commit()
            compose.setContent { if(visible.value) QztcTheme {
                SemesterScheduleSettingsScreen(semester,PeriodSchemeFactory.forSemester(semester.id),onSave={_,_,_->},onBack={},
                    onCalendarEventsChange={events.addAll(it)},onCalendarEventRemove={removals.add(it)},
                    onAcademicDaysChange={_,_ -> fail("Opening or editing a draft must not update the timetable")},
                    onTimelineDaysChange={old,next -> holidayChanges.add(old to next)},
                    onCalendarSemesterDetected={detected,activate -> detectedSemesters.add(detected to activate)})
            } }
            openCalendar()
            test()
        } finally {
            compose.runOnIdle { visible.value=false }
            compose.waitForIdle()
            backups.forEach { (file,values) ->
                val editor=context.getSharedPreferences(file,0).edit().clear()
                values.forEach { (key,value) -> restore(editor,key,value) }
                editor.commit()
            }
        }
    }

    private fun restore(editor: SharedPreferences.Editor, key: String, value: Any?) {
        when(value) {
            is String -> editor.putString(key,value)
            is Boolean -> editor.putBoolean(key,value)
            is Int -> editor.putInt(key,value)
            is Long -> editor.putLong(key,value)
            is Float -> editor.putFloat(key,value)
            is Set<*> -> editor.putStringSet(key,value.filterIsInstance<String>().toSet())
        }
    }
    private fun openCalendar() {
        compose.waitUntil(15_000) { compose.onAllNodesWithText("查看校历").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("查看校历").performClick()
        compose.waitForIdle()
    }
    private fun date(label: String, day: Int) {
        compose.onNodeWithTag("calendar-date-$label").performClick()
        onView(isAssignableFrom(DatePicker::class.java)).inRoot(isDialog()).perform(object:ViewAction {
            override fun getConstraints()=isAssignableFrom(DatePicker::class.java)
            override fun getDescription()="Choose a synthetic calendar date"
            override fun perform(ui:UiController,view:android.view.View) { (view as DatePicker).updateDate(2026,8,day);ui.loopMainThreadUntilIdle() }
        })
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
    }
    private fun screenshot(name:String) {
        compose.waitForIdle()
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        java.io.File(compose.activity.getExternalFilesDir(null),name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }

    @Test fun bottomSheetAutoSavesDraftAndRightSwipeIncludesThenUnlocksIt()=fixture {
        val tag="calendar-swipe-event:$eventId"
        assertTrue(events.isEmpty());assertTrue(holidayChanges.isEmpty())
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.onNodeWithTag("calendar-event-editor").assertIsDisplayed()
        compose.onNodeWithText("保存并同步").assertDoesNotExist()
        date("开始日期",20)
        screenshot("calendar-event-sheet.png")
        compose.onNodeWithContentDescription("收起编辑").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("calendar-event-editor").fetchSemanticsNodes().isEmpty() }
        assertTrue(events.isEmpty())
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.onNodeWithText("2026/09/20").assertIsDisplayed()
        compose.onNodeWithContentDescription("收起编辑").performClick()
        compose.onNodeWithTag(tag).performScrollTo().performTouchInput { swipeRight() }
        compose.waitUntil(10_000) { events.size==1 }
        assertTrue(detectedSemesters.any { (detected,activate) -> activate && detected.term==Term.AUTUMN })
        assertEquals(LocalDate.of(2026,9,20),events.single().startDate)
        compose.onNodeWithTag(tag).performClick()
        date("结束日期",23)
        compose.onNodeWithContentDescription("收起编辑").performClick()
        compose.waitUntil(10_000) { events.size==2 }
        assertEquals(LocalDate.of(2026,9,23),events.last().endDate)
        compose.runOnIdle { visible.value=false };compose.waitForIdle()
        compose.runOnIdle { visible.value=true };openCalendar()
        assertEquals(2,events.size)
        compose.onNodeWithTag(tag).performScrollTo().performTouchInput { swipeRight() }
        compose.waitUntil(10_000) { removals.size==1 }
        compose.onNodeWithTag(tag).assertExists()
    }

    @Test fun holidayAndMakeupStayTogetherAndJoiningIsExplicit()=fixture {
        val tag="calendar-swipe-holiday:2026:中秋:2026-09-25"
        compose.onNodeWithTag(tag).performScrollTo().performTouchInput { swipeLeft() }
        assertTrue(holidayChanges.isEmpty())
        compose.onNodeWithTag(tag).assertExists()
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.onNodeWithTag("holiday-date-editor").assertIsDisplayed()
        compose.onNodeWithText("保存日期").assertDoesNotExist()
        date("结束日期",28)
        compose.onNodeWithContentDescription("收起编辑").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("holiday-date-editor").fetchSemanticsNodes().isEmpty() }
        assertTrue(holidayChanges.isEmpty())
        compose.onNodeWithTag(tag).performScrollTo().performTouchInput { swipeRight() }
        compose.waitUntil(10_000) { holidayChanges.size==1 }
        assertTrue(detectedSemesters.any { (detected,activate) -> activate && detected.term==Term.AUTUMN })
        assertTrue(holidayChanges.single().first.isEmpty())
        assertEquals(4,holidayChanges.single().second.size)
        screenshot("calendar-timeline-opt-in.png")
        compose.onNodeWithTag(tag).performTouchInput { swipeRight() }
        compose.waitUntil(10_000) { holidayChanges.size==2 }
        assertTrue(holidayChanges.last().second.isEmpty())
        compose.onNodeWithTag(tag).assertExists()
    }
}
