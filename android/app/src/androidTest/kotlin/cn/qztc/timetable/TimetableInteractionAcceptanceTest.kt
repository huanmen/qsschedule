package cn.qztc.timetable

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cn.qztc.timetable.core.domain.calendar.AcademicDay
import cn.qztc.timetable.core.domain.calendar.AcademicDayKind
import cn.qztc.timetable.core.domain.model.*
import cn.qztc.timetable.core.domain.schedule.TimetableState
import cn.qztc.timetable.core.ui.theme.QztcTheme
import cn.qztc.timetable.feature.timetable.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TimetableInteractionAcceptanceTest {
    @get:Rule val compose=createAndroidComposeRule<FeatureAcceptanceActivity>()
    private val longName="大学英语综合应用与跨文化交流能力训练课程"
    private val room="综合教学楼东区四楼多媒体实验教室"
    private val teacher="示例教师甲、示例教师乙"
    private fun screenshot(name:String) {
        compose.waitForIdle()
        val image=AtomicReference<Bitmap>()
        val copied=AtomicInteger(PixelCopy.ERROR_UNKNOWN)
        val done=CountDownLatch(1)
        compose.runOnIdle {
            val view=compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            image.set(bitmap)
            PixelCopy.request(compose.activity.window,bitmap,{ result -> copied.set(result); done.countDown() },Handler(Looper.getMainLooper()))
        }
        assertTrue("Screenshot timed out",done.await(20,TimeUnit.SECONDS))
        assertEquals("PixelCopy failed",PixelCopy.SUCCESS,copied.get())
        val bitmap=image.get()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q) {
            val values=ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME,name)
                put(MediaStore.Images.Media.MIME_TYPE,"image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/QztcAcceptance")
                put(MediaStore.Images.Media.IS_PENDING,1)
            }
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)?.let { uri ->
                context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                values.clear(); values.put(MediaStore.Images.Media.IS_PENDING,0)
                context.contentResolver.update(uri,values,null,null)
            }
        }
        bitmap.recycle()
    }
    private fun state(): DemoScheduleState {
        val semester=Semester("ui-test","合成学期",2024,Term.AUTUMN,LocalDate.of(2024,9,2),20)
        val course=Course("course-test",semester.id,SourceType.QNU_EDU,"test",name=longName,credits="3.0",selectionAttribute="必修",assessmentMethod="考试")
        val meeting=CourseMeeting(CourseMeeting.stableId(course.id,1,1,2),course.id,1,1,2,WeekRule.of((1..16).toList()),teacher,room,SourceType.QNU_EDU)
        return DemoScheduleState().apply { loadImported(ImportedSchedule(semester,PeriodSchemeFactory.forSemester(semester.id).periods,TimetableState(listOf(course),listOf(meeting),emptyList()))) }
    }

    private fun changedDayState(): DemoScheduleState {
        val semester=Semester("today-changes","调休测试学期",2024,Term.AUTUMN,LocalDate.of(2024,9,2),20)
        fun course(id:String,name:String)=Course(id,semester.id,SourceType.QNU_EDU,id,name=name)
        fun meeting(courseId:String,start:Int,end:Int)=CourseMeeting(
            CourseMeeting.stableId(courseId,1,start,end),courseId,1,start,end,WeekRule.of(listOf(1)),
            "测试教师","测试教室",SourceType.QNU_EDU,
        )
        val courses=listOf(course("morning","早间课程"),course("cancelled","停课课程"),course("evening","晚间课程"))
        val meetings=listOf(meeting("morning",1,2),meeting("cancelled",7,8),meeting("evening",11,12))
        val cancellation=ScheduleException(
            id="cancelled-once",courseId="cancelled",type=ExceptionType.CANCEL,
            original=SlotRef(1,1,7,8),sourceType=SourceType.QNU_EDU,
        )
        val makeup=AcademicDay(
            id="makeup",semesterId=semester.id,date=semester.startDate,kind=AcademicDayKind.MAKEUP,
            name="国庆调休",useCurrentWeek=true,useCurrentWeekday=true,
        )
        return DemoScheduleState().apply {
            loadImported(
                ImportedSchedule(
                    semester,PeriodSchemeFactory.forSemester(semester.id).periods,
                    TimetableState(courses,meetings,listOf(cancellation)),academicDays=listOf(makeup),
                ),
            )
        }
    }

    private fun currentCourseState(): Pair<DemoScheduleState,LocalDateTime> {
        val date=LocalDate.now()
        val semesterStart=date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val semester=Semester("today-focus","焦点卡测试学期",date.year,Term.AUTUMN,semesterStart,20)
        val course=Course("focus-course",semester.id,SourceType.QNU_EDU,"focus-course",name="系统时间焦点课程")
        val meeting=CourseMeeting(
            CourseMeeting.stableId(course.id,date.dayOfWeek.value,1,2),course.id,date.dayOfWeek.value,1,2,
            WeekRule.of(listOf(1)),"焦点教师","焦点教室",SourceType.QNU_EDU,
        )
        val state=DemoScheduleState().apply {
            loadImported(ImportedSchedule(semester,PeriodSchemeFactory.forSemester(semester.id).periods,TimetableState(listOf(course),listOf(meeting))))
        }
        return state to LocalDateTime.of(date,LocalTime.of(9,30))
    }

    @Test fun longWeekTextFitsAtLargeFontScale() {
        val state=state()
        compose.setContent { QztcTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,1.35f)) {
                WeekScreen(state,{_,_->},weekendDisplayMode=WeekendDisplayMode.SHOW_ALL,modifier=Modifier.width(360.dp))
            }
        } }
        for(text in listOf(longName,teacher,room)) {
            val layouts=mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text,useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
            assertTrue("Text not measured: $text",layouts.isNotEmpty())
            assertTrue("Clipped text: $text",layouts.none { it.hasVisualOverflow })
        }
    }

    @Test fun todayInformationStopsAtLeftAndMenuRemainsUsable() {
        val state=state()
        val id=state.occurrences(state.currentWeek).single().id
        compose.setContent { QztcTheme { TodayScreen(state) } }
        val row=compose.onNodeWithTag("today-course-$id",useUnmergedTree=true)
        row.performScrollTo()
        row.performTouchInput { swipe(Offset(width*.87f,height*.5f),Offset(width*.18f,height*.5f),600) }
        compose.waitForIdle()
        val rowBounds=row.fetchSemanticsNode().boundsInRoot
        val info=compose.onNodeWithTag("today-info-$id",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue("Info escaped left edge",info.left>=rowBounds.left)
        assertTrue("Info did not stop at left inset",info.left-rowBounds.left<60)
        compose.onNodeWithText("详情").assertIsDisplayed().performClick()
        compose.onNodeWithText("课程详情").assertIsDisplayed()
        compose.onNodeWithText("选课属性").assertExists()
        compose.onNodeWithText("考核方式").assertExists()
    }

    @Test fun changePreviewDoesNotInsertTopHint() {
        compose.setContent { QztcTheme { WeekScreen(DemoScheduleState(),{_,_->}) } }
        val previewToggle = compose.onNodeWithContentDescription("变化预览", substring = true)
        previewToggle.performTouchInput {
            down(center)
            advanceEventTime(700)
        }
        compose.onNodeWithText("变化后预览中 · 松开恢复").assertDoesNotExist()
        compose.onNodeWithText("按住看变化后").assertDoesNotExist()
        compose.onNodeWithText("退出预览").assertDoesNotExist()
        previewToggle.performTouchInput { up() }
    }

    @Test fun scheduleChangeIconOnlyAppearsInChangedWeekAndLongPressPreviewsOriginalCourse() {
        val state = changedDayState()
        compose.setContent { QztcTheme { WeekScreen(state, { _, _ -> }, initialWeek = 1) } }

        val previewToggle = compose.onNodeWithContentDescription("变化预览", substring = true)
        previewToggle.assertIsDisplayed()
        val density = compose.activity.resources.displayMetrics.density
        assertTrue("Change icon is larger than 14dp", previewToggle.fetchSemanticsNode().boundsInRoot.width <= 14f * density + 1f)
        compose.onNodeWithText("停").assertExists()

        compose.mainClock.autoAdvance = false
        previewToggle.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(700)
        compose.waitForIdle()
        compose.onNodeWithText("停").assertDoesNotExist()
        compose.onNodeWithText("停课课程").assertExists()

        previewToggle.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText("停").assertExists()

        compose.onNodeWithContentDescription("下一周").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("变化预览", substring = true).assertDoesNotExist()
    }

    @Test fun todayReturnSitsAfterDateAndHidesEyeForUnchangedWeek() {
        val schedule = currentCourseState().first
        val anotherDay = if (schedule.referenceWeekday == 1) 2 else 1
        schedule.selectedDay = anotherDay
        compose.setContent { QztcTheme { TodayScreen(schedule) } }
        compose.onNodeWithContentDescription("显示停课").assertDoesNotExist()
        val label = "第${schedule.currentWeek}周 · 周${listOf("一","二","三","四","五","六","日")[anotherDay-1]}"
        val date = compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
        val back = compose.onNodeWithText("回今日").fetchSemanticsNode().boundsInRoot
        assertTrue(back.left >= date.right)
        compose.onNodeWithText("回今日").performClick()
        compose.runOnIdle { assertEquals(schedule.referenceWeekday, schedule.selectedDay) }
        compose.onNodeWithText("回今日").assertDoesNotExist()
    }

    @Test fun todayListCanRevealAndHideCancelledCoursesAndKeepsDayParts() {
        val state=changedDayState()
        compose.setContent { QztcTheme { TodayScreen(state) } }

        compose.onNodeWithContentDescription("显示停课").assertIsDisplayed()
        compose.onNodeWithText("停课课程").assertDoesNotExist()
        compose.onNodeWithContentDescription("显示停课").performClick()
        compose.onNodeWithText("停课课程").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("停课").assertExists()
        screenshot("today-cancelled-makeup.png")
        compose.onNodeWithText("早间").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("午后").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("晚间").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("停课课程").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("显示停课").performClick()
        compose.onNodeWithText("停课课程").assertDoesNotExist()
    }

    @Test fun focusCardUsesClockAndShowsRemainingCourseBar() {
        val (state,now)=currentCourseState()
        compose.setContent { QztcTheme { TodayScreen(state,nowProvider={ now }) } }

        compose.onNodeWithText("正在上课").assertDoesNotExist()
        compose.onNodeWithText("今日安排").assertDoesNotExist()
        compose.onAllNodesWithText("系统时间焦点课程").assertCountEquals(2)
        compose.onNodeWithTag("focus-course-remaining-progress").assertIsDisplayed()
        compose.onNodeWithTag("today-schedule-progress").assertIsDisplayed()
        compose.onNodeWithText("上课中", substring = true).assertDoesNotExist()
        screenshot("today-focus-progress.png")
    }

    @Test fun holidayViewKeepsOtherDaysAndMergesHeader() {
        val state=state()
        val current=state.importedSchedule!!
        val other=current.state.courses.single().copy(id="tuesday",name="周二保留课")
        val meeting=current.state.meetings.single().copy(id="tuesday-meeting",courseId=other.id,weekday=2)
        state.loadImported(current.copy(state=current.state.copy(courses=current.state.courses+other,meetings=current.state.meetings+meeting),
            academicDays=listOf(AcademicDay("holiday",current.semester.id,current.semester.startDate,AcademicDayKind.HOLIDAY,"中秋节"))))
        compose.setContent { QztcTheme { WeekScreen(state,{_,_->},weekendDisplayMode=WeekendDisplayMode.HIDE_EMPTY) } }
        compose.onNodeWithText("周二保留课").assertExists()
        compose.onNodeWithTag("week-book-open").assertIsDisplayed().performClick()
        compose.onNodeWithTag("week-book-closed").assertIsDisplayed()
        compose.onNodeWithText("周二保留课").assertExists()
        compose.onNodeWithText(longName).assertDoesNotExist()
        compose.onNodeWithText("中秋").assertExists()
        compose.onNodeWithText("周六").assertDoesNotExist()
        listOf(1,2,3,4,7,8,9,10,11,12,13).forEach { period ->
            compose.onNodeWithTag("week-empty-1-$period").assertDoesNotExist()
        }
        compose.onNodeWithTag("week-empty-2-3").assertExists()
        screenshot("holiday-merged-cells-book-closed.png")
        compose.onNodeWithTag("week-book-closed").performClick()
        compose.onNodeWithTag("week-book-open").assertIsDisplayed()
        compose.onNodeWithText(longName).assertExists()
        compose.onNodeWithTag("week-empty-1-3").assertExists()
    }

    @Test fun courseViewKeepsOriginalCellsUntilCalendarViewIsSelected() {
        val semester=Semester("makeup-view","调休视图",2026,Term.AUTUMN,LocalDate.of(2026,8,31),20)
        fun course(id:String,name:String)=Course(id,semester.id,SourceType.QNU_EDU,id,name=name)
        val monday=course("monday","周一原课程")
        val tuesday=course("tuesday","周二来源课程")
        fun meeting(course:Course,weekday:Int)=CourseMeeting(
            CourseMeeting.stableId(course.id,weekday,1,2),course.id,weekday,1,2,
            WeekRule.of(listOf(1)),"教师","教室",SourceType.QNU_EDU,
        )
        val makeup=AcademicDay(
            "makeup",semester.id,semester.startDate,AcademicDayKind.MAKEUP,"调休",
            sourceWeek=1,sourceWeekday=2,
        )
        val state=DemoScheduleState().apply { loadImported(ImportedSchedule(
            semester,PeriodSchemeFactory.forSemester(semester.id).periods,
            TimetableState(listOf(monday,tuesday),listOf(meeting(monday,1),meeting(tuesday,2))),
            academicDays=listOf(makeup),
        )) }
        compose.setContent { QztcTheme { WeekScreen(state,{_,_->},initialWeek=1,weekendDisplayMode=WeekendDisplayMode.SHOW_ALL) } }

        compose.onNodeWithText("周一原课程").assertExists()
        compose.onAllNodesWithText("周二来源课程").assertCountEquals(1)
        compose.onNodeWithTag("week-book-open").performClick()
        compose.onNodeWithText("周一原课程").assertDoesNotExist()
        compose.onAllNodesWithText("周二来源课程").assertCountEquals(2)
    }

    @Test fun holidayCoursesArePaintedAtTheirActualGridPositions() {
        val state = state()
        val original = state.importedSchedule!!
        val source = original.state.courses.single().copy(name="测试课")
        val courses = (1..3).map { source.copy(id="holiday-course-$it") }
        val meetings = courses.mapIndexed { index, course ->
            original.state.meetings.single().copy(id="holiday-meeting-$index",courseId=course.id,
                weekday=index+1,periodStart=if(index==1) 7 else 1,periodEnd=if(index==1) 8 else 2,
                teacher="",room="")
        }
        state.loadImported(original.copy(state=TimetableState(courses,meetings),
            academicDays=(0..2).map { offset -> AcademicDay("holiday-$offset",original.semester.id,
                original.semester.startDate.plusDays(offset.toLong()),AcademicDayKind.HOLIDAY,"国庆节") }))
        val backdrop = Color(0xFF224466)
        compose.setContent { QztcTheme { Box(Modifier.fillMaxSize().background(backdrop)) {
            WeekScreen(state,{_,_->},initialWeek=1,weekendDisplayMode=WeekendDisplayMode.SHOW_ALL)
        } } }
        fun assertPainted() {
            for(occurrence in state.occurrences(1,respectAcademicCalendar=false)) {
                val cell=compose.onNodeWithTag("week-course-${occurrence.id}")
                cell.assertIsDisplayed()
                val bounds=cell.fetchSemanticsNode().boundsInRoot
                val root=compose.onRoot().fetchSemanticsNode().boundsInRoot
                val pixels=compose.onRoot().captureToImage().toPixelMap()
                val x=(bounds.center.x-root.left).toInt()
                val y=(bounds.bottom-12f-root.top).toInt()
                val painted=pixels[x,y]
                assertTrue("Holiday course was clipped at ${occurrence.weekday}/${occurrence.periodStart}",
                    kotlin.math.abs(painted.red-backdrop.red)+kotlin.math.abs(painted.green-backdrop.green)+
                        kotlin.math.abs(painted.blue-backdrop.blue) > .08f)
            }
        }
        assertPainted()
        compose.onNodeWithTag("week-book-open").performClick()
        compose.onAllNodesWithText("测试课").assertCountEquals(0)
        compose.onNodeWithTag("week-book-closed").performClick()
        assertPainted()
        screenshot("holiday-course-pixels-restored.png")
    }

    @Test fun movedCourseReplacesOldTextBeforeTransparentCellsAreDrawn() {
        val state = state()
        val original = state.importedSchedule!!
        val base = original.state.meetings.single()
        val monday = base.copy(periodStart=7, periodEnd=10, teacher="旧教师", room="旧地点")
        val movedCourse = original.state.courses.single().copy(id="moved-course", name="调入课程")
        val tuesday = base.copy(id="moved-source", courseId=movedCourse.id, weekday=2, periodStart=7, periodEnd=9, teacher="来源教师")
        val move = ScheduleException("move-partial",movedCourse.id,ExceptionType.MOVE,
            original=SlotRef(1,2,7,9), replacement=SlotRef(1,1,7,9,teacher="新教师",room="新地点"),
            sourceType=SourceType.MANUAL)
        state.loadImported(original.copy(state=original.state.copy(courses=original.state.courses + movedCourse,
            meetings=listOf(monday,tuesday),exceptions=listOf(move))))
        compose.setContent { QztcTheme { WeekScreen(state,{_,_->},initialWeek=1,weekendDisplayMode=WeekendDisplayMode.SHOW_ALL) } }
        val trimmedId = "${base.courseId}-${base.id}-1-10-10"
        val newCell = compose.onNodeWithTag("week-course-${movedCourse.id}-exception-1-7-9")
        val oldCell = compose.onNodeWithTag("week-course-$trimmedId")
        val newBounds = newCell.getUnclippedBoundsInRoot()
        val oldBounds = oldCell.getUnclippedBoundsInRoot()
        assertTrue("Old cell must stay below the replacement cell: $oldBounds / $newBounds", oldBounds.top >= newBounds.bottom)
        newCell.assert(hasText("新教师"))
        oldCell.assert(hasText("旧教师"))
        compose.onNodeWithTag("week-course-${base.courseId}-${base.id}-1-7-10").assertDoesNotExist()
        newCell.performScrollTo().assertIsDisplayed()
        screenshot("week-replaced-content-no-overlap.png")
    }

    @Test fun shortWeekGridKeepsBothTimesWithoutTextOverflowAtLargeFontScale() {
        val state = state()
        val original = state.importedSchedule!!
        state.loadImported(original.copy(state=TimetableState.EMPTY))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.35f)) {
                QztcTheme { Box(Modifier.width(360.dp).height(570.dp)) {
                    WeekScreen(state,{_,_->},initialWeek=1,weekendDisplayMode=WeekendDisplayMode.HIDE_EMPTY)
                } }
            }
        }
        screenshot("short-week-all-period-times.png")
        for (period in listOf(1, 4, 7, 10, 13)) {
            val start = compose.onNodeWithTag("week-period-start-$period", useUnmergedTree=true)
            val end = compose.onNodeWithTag("week-period-end-$period", useUnmergedTree=true)
            start.assertIsDisplayed()
            end.assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            end.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("End time is clipped for period $period: ${layouts.map { "${it.size} ${it.layoutInput.style} width=${it.didOverflowWidth} height=${it.didOverflowHeight}" }}",
                layouts.isNotEmpty() && layouts.none { it.hasVisualOverflow })
            assertTrue(start.getUnclippedBoundsInRoot().bottom <= end.getUnclippedBoundsInRoot().top)
        }
        screenshot("short-week-all-period-times.png")
    }

    @Test fun partialActivityOwnsItsSlotsAndEmptyCellsReturnWhenRemoved() {
        val state=state()
        val original=state.importedSchedule!!
        val meeting=original.state.meetings.single().copy(periodStart=1,periodEnd=4)
        val activity=ScheduleActivity(
            id="calendar-event:slot-test",semesterId=original.semester.id,name="分段活动",note="活动备注",
            kind=ActivityKind.PERIOD,weekday=1,periodStart=2,periodEnd=3,weeks=listOf(1),
        )
        val schedule=original.copy(state=original.state.copy(meetings=listOf(meeting)),activities=listOf(activity))
        state.loadImported(schedule)
        compose.setContent { QztcTheme { WeekScreen(state,{_,_->},initialWeek=1,weekendDisplayMode=WeekendDisplayMode.SHOW_ALL) } }
        compose.onNodeWithTag("week-book-open").performClick()
        compose.onAllNodesWithText(longName).assertCountEquals(2)
        compose.onNodeWithText("分段活动").assertExists()
        (1..4).forEach { compose.onNodeWithTag("week-empty-1-$it").assertDoesNotExist() }
        compose.onNodeWithTag("week-empty-2-2").assertExists()
        compose.onNodeWithTag("week-empty-1-7").assertExists()
        screenshot("partial-activity-merged-cells.png")

        compose.runOnIdle { state.loadImported(schedule.copy(activities=emptyList(),state=schedule.state.copy(meetings=emptyList()))) }
        compose.onNodeWithText("分段活动").assertDoesNotExist()
        (1..4).forEach { compose.onNodeWithTag("week-empty-1-$it").assertExists() }
    }

    @Test fun timetableEditOpensCourseEditorOnly() {
        val state=state()
        compose.setContent { QztcTheme { WeekScreen(state,{_,_->}) } }
        compose.onNodeWithContentDescription("编辑课表").performClick()
        compose.onNodeWithText("添加课程").assertIsDisplayed()
        compose.onNodeWithText("添加活动").assertDoesNotExist()
        compose.onNodeWithText("设置调休").assertDoesNotExist()
        compose.onNodeWithText("编辑周课表").assertDoesNotExist()
    }

    @Test fun otherDayTitleOpensDatePickerInsteadOfReturningToday() {
        val state=state().apply { selectedDay=2 }
        compose.setContent { QztcTheme { TodayScreen(state) } }
        compose.onNodeWithText("第1周 · 周二").performClick()
        compose.onNodeWithText("选择日期",substring=true).assertExists()
        assertEquals(2,state.selectedDay)
    }
    @Test fun manualActivityIsAbsentAndSchoolActivityOpensCalendarOnItsDate() {
        val state = state()
        val original = state.importedSchedule!!
        val manual = ScheduleActivity("manual-test", original.semester.id, "manual calendar only", kind = ActivityKind.DAY,
            startDate = original.semester.startDate, endDate = original.semester.startDate)
        state.loadImported(original.copy(activities = listOf(manual)))
        var opened: LocalDate? = null
        compose.setContent { QztcTheme { WeekScreen(state, { _, _ -> }, initialWeek = 1,
            onOpenCalendar = { opened = it }) } }
        compose.onNodeWithText(manual.name).assertDoesNotExist()
        compose.onNodeWithTag("week-book-open").assertDoesNotExist()
        val school = manual.copy(id = "calendar-event:school-test", name = "school sports")
        compose.runOnIdle { state.loadImported(original.copy(activities = listOf(manual, school))) }
        compose.onNodeWithTag("week-book-open").performClick()
        compose.onNodeWithText(manual.name).assertDoesNotExist()
        compose.onNodeWithText(school.name).performClick()
        compose.runOnIdle { assertEquals(original.semester.startDate, opened) }
    }

}
