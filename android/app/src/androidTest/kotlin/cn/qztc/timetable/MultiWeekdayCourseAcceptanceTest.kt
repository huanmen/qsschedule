package cn.qztc.timetable

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cn.qztc.timetable.core.domain.model.*
import cn.qztc.timetable.core.domain.schedule.TimetableState
import cn.qztc.timetable.core.ui.theme.QztcTheme
import cn.qztc.timetable.feature.timetable.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MultiWeekdayCourseAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<FeatureAcceptanceActivity>()

    @Test fun addingMultipleDaysCanCancelPeriodAndReviewConflicts() {
        val semester = Semester("ui-multi", "合成学期", 2024, Term.AUTUMN, LocalDate.of(2024,9,2), 4)
        val course = Course("synthetic-existing", semester.id, SourceType.MANUAL, "synthetic", name = "冲突示例课")
        val meeting = CourseMeeting("m", course.id, 1, 1, 2, WeekRule.of(listOf(1,3)), sourceType = SourceType.MANUAL)
        val schedule = DemoScheduleState().apply { loadImported(ImportedSchedule(
            semester, PeriodSchemeFactory.forSemester(semester.id).periods,
            TimetableState(listOf(course),listOf(meeting)))) }
        var saved: CourseEditDraft? = null
        compose.setContent { QztcTheme { WeekScreen(schedule, { _, _ -> }, initialWeek = 1, onAddCourse = { saved = it }) } }
        compose.onNodeWithContentDescription("编辑课表").performClick()
        compose.onNodeWithText("添加课程").performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("多星期示例")
        compose.activityRule.scenario.onActivity { activity ->
            val keyboard = activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            keyboard.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
        // The default Monday range is 1–2. Cancel only period 2, then add Wednesday.
        compose.onNodeWithTag("course-period-2").performScrollTo().performClick()
        compose.onNodeWithTag("course-day-3").performScrollTo().performClick()
        compose.onNodeWithTag("course-period-7").performScrollTo().performClick()
        compose.onNodeWithTag("course-period-8").performScrollTo().performClick()
        compose.onNodeWithContentDescription("保存").performScrollTo().performClick()
        compose.onNodeWithText("这些课程时间重叠").assertIsDisplayed()
        compose.onNodeWithText("重新选择").performClick()
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithContentDescription("保存").performScrollTo().performClick()
        compose.onNodeWithText("覆盖并保存").performClick()
        compose.runOnIdle {
            assertNotNull(saved)
            assertTrue(saved!!.overwriteConflicts)
            assertEquals(setOf(1,3), saved!!.weeklySlots.map { it.weekday }.toSet())
            assertEquals(1, saved!!.weeklySlots.single { it.weekday==1 }.periodEnd)
            assertEquals(7, saved!!.weeklySlots.single { it.weekday==3 }.periodStart)
            assertEquals(8, saved!!.weeklySlots.single { it.weekday==3 }.periodEnd)
        }
    }
}
