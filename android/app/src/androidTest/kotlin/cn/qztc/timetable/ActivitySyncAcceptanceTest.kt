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
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun addAndDeleteActivityPersistsFromCourseArrangement() {
        val label = "QA 活动同步验收"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = TimetableDatabase.build(context)
        val repository = TimetableRepository(database)
        var seededFixture = false
        val semester = runBlocking {
            val settings = repository.settings()
            val available = repository.semesters()
            val current = available.firstOrNull { it.id == settings.activeSemesterId } ?: available.firstOrNull()
            if (current != null) {
                current
            } else {
                val today = LocalDate.now()
                val fixture = Semester(
                    id = "qa-activity-sync-fixture",
                    name = "合成活动学期",
                    schoolYear = today.year,
                    term = Term.AUTUMN,
                    startDate = today,
                    totalWeeks = 20,
                )
                repository.upsertSemester(fixture)
                repository.upsertPeriodScheme(PeriodSchemeFactory.forSemester(fixture.id))
                val course = Course(
                    id = "qa-activity-sync-course",
                    semesterId = fixture.id,
                    sourceType = cn.qztc.timetable.core.domain.model.SourceType.MANUAL,
                    sourceKey = "qa-activity-sync-course",
                    name = "合成课程",
                )
                repository.updateCourseAndMeeting(
                    course,
                    previousMeeting = null,
                    meeting = CourseMeeting(
                        id = CourseMeeting.stableId(course.id, 1, 1, 2),
                        courseId = course.id,
                        weekday = 1,
                        periodStart = 1,
                        periodEnd = 2,
                        weekRule = cn.qztc.timetable.core.domain.model.WeekRule.of((1..20).toList()),
                        sourceType = cn.qztc.timetable.core.domain.model.SourceType.MANUAL,
                    ),
                )
                repository.updateSettings(settings.copy(activeSemesterId = fixture.id))
                seededFixture = true
                fixture
            }
        }
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf(
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_FINE_LOCATION",
        ).forEach { permission ->
            uiAutomation.executeShellCommand("pm grant ${context.packageName} $permission").close()
        }
        if (seededFixture) compose.activityRule.scenario.recreate()

        fun removeFixture() = runBlocking {
            repository.activities(semester.id)
                .filter { it.name == label }
                .forEach { repository.deleteActivity(it.id, restoreCourses = true) }
        }

        removeFixture()
        try {
            compose.waitUntil(60_000) {
                runCatching {
                    compose.onAllNodesWithText("我的").fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            compose.onNodeWithContentDescription("我的").performClick()
            compose.onNodeWithText("课程安排").performScrollTo().performClick()
            compose.waitUntil(20_000) {
                compose.onAllNodesWithText("活动安排").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithText("添加").performClick()
            compose.onNode(hasSetTextAction() and hasText("活动名称")).performTextInput(label)
            compose.onNodeWithText("完成").performClick()
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(runBlocking { repository.activities(semester.id).any { it.name == label } })
            compose.onNodeWithText(label).performClick()
            compose.onNodeWithText("删除活动").performClick()
            compose.onNodeWithText("删除").performClick()
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText(label).fetchSemanticsNodes().isEmpty()
            }
            assertTrue(runBlocking { repository.activities(semester.id).none { it.name == label } })
        } finally {
            removeFixture()
            database.close()
            if (seededFixture) context.deleteDatabase(TimetableDatabase.DEFAULT_NAME)
        }
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
            id = "qa-activity-pages:item",
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
