package cn.qztc.timetable

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.runtime.mutableStateOf
import androidx.test.platform.app.InstrumentationRegistry
import cn.qztc.timetable.core.data.db.TimetableDatabase
import cn.qztc.timetable.core.data.repository.TimetableRepository
import cn.qztc.timetable.core.domain.model.ActivityKind
import cn.qztc.timetable.core.domain.model.Course
import cn.qztc.timetable.core.domain.model.CourseMeeting
import cn.qztc.timetable.core.domain.model.PeriodSchemeFactory
import cn.qztc.timetable.core.domain.model.ScheduleActivity
import cn.qztc.timetable.core.domain.model.Semester
import cn.qztc.timetable.core.domain.model.Term
import cn.qztc.timetable.core.domain.schedule.TimetableState
import cn.qztc.timetable.core.ui.theme.QztcTheme
import cn.qztc.timetable.feature.timetable.DemoScheduleState
import cn.qztc.timetable.feature.timetable.ImportedSchedule
import cn.qztc.timetable.feature.timetable.TodayScreen
import cn.qztc.timetable.feature.timetable.WeekScreen
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ActivitySyncAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<FeatureAcceptanceActivity>()

    @Test fun courseArrangementRoutesActivityManagementToCalendar() {
        val today = LocalDate.now()
        val semester = Semester("qa-calendar-routing", "test", today.year, Term.AUTUMN, today, 20)
        var opened: LocalDate? = null
        compose.setContent { QztcTheme {
            cn.qztc.timetable.feature.settings.SemesterScheduleSettingsScreen(
                semester = semester, periodScheme = PeriodSchemeFactory.forSemester(semester.id),
                onSave = { _, _, _ -> }, onBack = {}, onOpenCalendar = { opened = it },
            )
        } }
        compose.onNodeWithText("在日历中管理").performScrollTo().performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(today, opened) }
        compose.onNodeWithText("添加活动").assertDoesNotExist()
    }
}

class ActivityPresentationAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<FeatureAcceptanceActivity>()

    @Test fun activityStateAppearsAndDisappearsOnTodayAndWeek() {
        val label = "QA 活动同步验收"
        val today = LocalDate.now()
        val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val semester = Semester("qa-activity-pages", "合成活动学期", today.year, Term.AUTUMN, start, 20)
        val activity = ScheduleActivity(
            id = "calendar-event:qa-activity-pages:item",
            semesterId = semester.id,
            name = label,
            kind = ActivityKind.DAY,
            startDate = today,
            endDate = today,
        )
        val periods = PeriodSchemeFactory.forSemester(semester.id).periods
        val withActivity = ImportedSchedule(semester, periods, TimetableState(), activities = listOf(activity))
        val withoutActivity = withActivity.copy(activities = emptyList())
        val state = DemoScheduleState().apply { loadImported(withActivity) }
        val showWeek = mutableStateOf(false)

        compose.setContent { QztcTheme { if (showWeek.value) WeekScreen(state, { _, _ -> }) else TodayScreen(state) } }
        compose.onNodeWithText(label).assertIsDisplayed()
        compose.runOnIdle { state.loadImported(withoutActivity) }
        compose.onNodeWithText(label).assertDoesNotExist()

        compose.runOnIdle { state.loadImported(withActivity); showWeek.value = true }
        compose.onNodeWithContentDescription("切换活动视图", substring = true).performClick()
        compose.onAllNodesWithText(label).onFirst().assertIsDisplayed()
        compose.runOnIdle { state.loadImported(withoutActivity) }
        compose.onNodeWithText(label).assertDoesNotExist()
    }
}
