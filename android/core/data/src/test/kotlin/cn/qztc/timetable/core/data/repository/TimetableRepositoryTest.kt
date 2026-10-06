package cn.qztc.timetable.core.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cn.qztc.timetable.core.data.db.TimetableDatabase
import cn.qztc.timetable.core.domain.imports.ImportDiagnostic
import cn.qztc.timetable.core.domain.imports.ImportedTimetable
import cn.qztc.timetable.core.domain.model.Course
import cn.qztc.timetable.core.domain.model.CourseMeeting
import cn.qztc.timetable.core.domain.model.ExceptionType
import cn.qztc.timetable.core.domain.model.HomeView
import cn.qztc.timetable.core.domain.model.MeetingField
import cn.qztc.timetable.core.domain.model.ScheduleException
import cn.qztc.timetable.core.domain.model.Semester
import cn.qztc.timetable.core.domain.model.SlotRef
import cn.qztc.timetable.core.domain.model.SourceType
import cn.qztc.timetable.core.domain.model.Term
import cn.qztc.timetable.core.domain.model.UserSettings
import cn.qztc.timetable.core.domain.model.WeekRule
import cn.qztc.timetable.core.domain.schedule.TimetableState
import cn.qztc.timetable.core.domain.schedule.OccurrenceExpander
import cn.qztc.timetable.core.domain.model.OccurrenceState
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimetableRepositoryTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: TimetableDatabase
    private lateinit var repository: TimetableRepository

    private val semester = Semester(
        id = "s1",
        name = "2024 秋",
        schoolYear = 2024,
        term = Term.AUTUMN,
        startDate = LocalDate.of(2024, 9, 2),
        totalWeeks = 20,
    )

    @Before
    fun setUp() {
        context.deleteDatabase(TimetableDatabase.DEFAULT_NAME)
        db = TimetableDatabase.build(context, allowMainThreadQueries = true)
        repository = TimetableRepository(db)
        runBlocking { repository.upsertSemester(semester) }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun course(id: String, name: String = "课程$id") = Course(
        id = "qnu:$id", semesterId = "", sourceType = SourceType.QNU_EDU,
        sourceKey = "qnu:$id", name = name,
    )

    private fun meeting(
        courseId: String,
        weekday: Int = 1,
        start: Int = 1,
        end: Int = 2,
        teacher: String? = "老师A",
        room: String? = "教室A",
        weeks: List<Int> = (1..16).toList(),
    ) = CourseMeeting(
        id = CourseMeeting.stableId("qnu:$courseId", weekday, start, end),
        courseId = "qnu:$courseId",
        weekday = weekday,
        periodStart = start,
        periodEnd = end,
        weekRule = WeekRule.of(weeks, "1-16周"),
        teacher = teacher,
        room = room,
        sourceType = SourceType.QNU_EDU,
    )

    private fun makeup(courseId: String, week: Int): ScheduleException {
        val slot = SlotRef(week = week, weekday = 5, periodStart = 11, periodEnd = 12, room = "教室C")
        return ScheduleException(
            id = ScheduleException.stableId(ExceptionType.MAKEUP, "qnu:$courseId", slot),
            courseId = "qnu:$courseId",
            type = ExceptionType.MAKEUP,
            replacement = slot,
            sourceType = SourceType.QNU_EDU,
        )
    }

    private fun candidate(state: TimetableState, diagnostics: List<ImportDiagnostic> = emptyList()) =
        ImportedTimetable(semesterHint = null, periods = emptyList(), state = state, diagnostics = diagnostics)

    private val firstImport = TimetableState(
        courses = listOf(course("c1"), course("c2")),
        meetings = listOf(meeting("c1"), meeting("c2", weekday = 3)),
        exceptions = listOf(makeup("c1", 9)),
    )

    @Test
    fun `legacy qnu cancellation periods are repaired when shifted slot matches meeting`() = runBlocking {
        val legacyCourse = course("legacy")
        val legacyMeeting = meeting("legacy", weekday = 5, start = 7, end = 8)
        val legacySlot = SlotRef(week = 2, weekday = 5, periodStart = 5, periodEnd = 6)
        val legacyCancellation = ScheduleException(
            id = ScheduleException.stableId(ExceptionType.CANCEL, legacyCourse.id, legacySlot),
            courseId = legacyCourse.id,
            type = ExceptionType.CANCEL,
            original = legacySlot,
            sourceType = SourceType.QNU_EDU,
        )
        repository.importCandidate(
            semester,
            candidate(TimetableState(listOf(legacyCourse), listOf(legacyMeeting), listOf(legacyCancellation))),
        )

        val restored = repository.state(semester.id)
        assertEquals(7, restored.exceptions.single().original?.periodStart)
        assertEquals(8, restored.exceptions.single().original?.periodEnd)
        val occurrence = OccurrenceExpander.expand(semester, restored, 2).single()
        assertEquals(OccurrenceState.CANCELLED, occurrence.state)
    }

    @Test fun multiDayCoveragePreservesOtherWeeksAndRestoresWhenMoved() = runBlocking {
        val oldCourse = course("old").copy(semesterId = semester.id)
        val oldMeeting = meeting("old", weeks = listOf(1, 2, 3, 4))
        repository.updateCourseAndMeeting(oldCourse, null, oldMeeting)
        val added = course("added").copy(semesterId = semester.id)
        val oddMonday = meeting("added", weeks = listOf(1, 3))
        val evenThursday = meeting("added", weekday = 4, start = 11, end = 13, weeks = listOf(2, 4))
        repository.replaceCourseMeetings(added, listOf(oddMonday, evenThursday), overwriteConflicts = true)
        val saved = repository.state(semester.id)
        assertEquals(2, saved.meetings.count { it.courseId == added.id })
        assertEquals(listOf(2, 4), OccurrenceExpander.effective(semester, saved).filter { it.courseId == oldCourse.id }.map { it.week })
        assertEquals(oldMeeting, saved.meetings.first { it.courseId == oldCourse.id })
        // Reopening and saving unchanged must retain the user's accepted coverage.
        repository.replaceCourseMeetings(added, listOf(oddMonday, evenThursday))
        assertEquals(2, repository.hiddenCourses(semester.id).size)
        repository.replaceCourseMeetings(added, listOf(evenThursday))
        assertEquals(4, OccurrenceExpander.effective(semester, repository.state(semester.id)).count { it.courseId == oldCourse.id })
    }

    @Test fun conflictingSaveWithoutConsentRollsBackAndOppositeParityIsAllowed() = runBlocking {
        val a = course("a").copy(semesterId = semester.id)
        val b = course("b").copy(semesterId = semester.id)
        repository.updateCourseAndMeeting(a, null, meeting("a", weeks = listOf(1, 3)))
        repository.replaceCourseMeetings(b, listOf(meeting("b", weeks = listOf(2, 4))))
        val before = repository.state(semester.id)
        assertTrue(runCatching { repository.replaceCourseMeetings(b, listOf(meeting("b", weeks = listOf(1, 3)))) }.isFailure)
        assertEquals(before, repository.state(semester.id))
    }

    @Test fun legacyCoverageRepairsOnlySkippedDatesAndDoesNotUndoManualRestore() = runBlocking {
        val old = course("legacy-old").copy(semesterId = semester.id)
        val added = course("legacy-added").copy(semesterId = semester.id)
        repository.updateCourseAndMeeting(old, null, meeting("legacy-old", weeks = listOf(1, 2, 3)))
        repository.updateCourseAndMeeting(added, null, meeting("legacy-added", weeks = listOf(1, 2, 3)))
        repository.replaceAcademicDays(semester.id, listOf(cn.qztc.timetable.core.domain.calendar.AcademicDay(
            "legacy-holiday", semester.id, semester.startDate, cn.qztc.timetable.core.domain.calendar.AcademicDayKind.HOLIDAY, "合成假期")))
        val before = repository.state(semester.id)
        val key = OccurrenceExpander.key(OccurrenceExpander.effective(semester, before)
            .single { it.courseId == old.id && it.week == 2 })
        val owner = "course-coverage:${added.id}"
        db.timetableDao().upsertSuppressions(listOf(cn.qztc.timetable.core.data.db.ActivitySuppressionEntity(
            "$owner:$key", semester.id, owner, key, coverageVersion = 0)))
        val repaired = repository.state(semester.id)
        assertEquals(listOf(3), OccurrenceExpander.effective(semester, repaired).filter { it.courseId == old.id }.map { it.week })
        val restored = repository.hiddenCourses(semester.id).single { it.occurrenceKey.contains(semester.startDate.toString()) }
        repository.restoreHiddenCourse(restored.id)
        val reopened = TimetableRepository(db).state(semester.id)
        assertEquals(listOf(1, 3), OccurrenceExpander.effective(semester, reopened).filter { it.courseId == old.id }.map { it.week })
        assertTrue(repository.hiddenCourses(semester.id).all { it.coverageVersion == 1 })
    }

    @Test fun coverageReplacesHolidayPlanAndMakeupAndSurvivesActivityRemoval() = runBlocking {
        val old = course("holiday-old").copy(semesterId = semester.id)
        val added = course("holiday-new").copy(semesterId = semester.id)
        val oldMeeting = meeting("holiday-old", weeks = listOf(1, 2, 3, 4))
        repository.updateCourseAndMeeting(old, null, oldMeeting)
        val holiday = cn.qztc.timetable.core.domain.calendar.AcademicDay(
            "holiday", semester.id, semester.startDate, cn.qztc.timetable.core.domain.calendar.AcademicDayKind.HOLIDAY, "合成假期")
        val movedDay = holiday.copy(id = "makeup", date = semester.startDate.plusDays(5),
            kind = cn.qztc.timetable.core.domain.calendar.AcademicDayKind.MAKEUP, sourceWeek = 1, sourceWeekday = 1)
        repository.replaceAcademicDays(semester.id, listOf(holiday, movedDay))
        val activity = cn.qztc.timetable.core.domain.model.ScheduleActivity(
            "activity-test", semester.id, "合成活动", kind = cn.qztc.timetable.core.domain.model.ActivityKind.PERIOD,
            weekday = 1, periodStart = 1, periodEnd = 2, weeks = listOf(2))
        repository.saveActivityWithCoverage(activity, listOf(oldMeeting.id))
        val addedMeeting = meeting("holiday-new", weeks = listOf(1, 2, 3))
        repository.replaceCourseMeetings(added, listOf(addedMeeting), overwriteConflicts = true)
        repository.replaceCourseMeetings(added, listOf(addedMeeting))
        repository.deleteActivity(activity.id)
        val saved = repository.state(semester.id)
        val calendar = cn.qztc.timetable.core.domain.calendar.AcademicCalendar(listOf(holiday, movedDay))
        for (view in listOf(cn.qztc.timetable.core.domain.calendar.AcademicCalendar.EMPTY, calendar)) {
            val occurrences = OccurrenceExpander.effective(semester, saved, view)
            assertEquals(listOf(4), occurrences.filter { it.courseId == old.id }.map { it.week })
            assertTrue(OccurrenceExpander.findConflicts(occurrences).isEmpty())
        }
        repository.deleteCourse(added.id)
        assertEquals(4, OccurrenceExpander.effective(semester, repository.state(semester.id)).size)
    }

    @Test
    fun `first import applies immediately and is revertable`() = runBlocking {
        val outcome = repository.importCandidate(semester, candidate(firstImport))
        assertTrue(outcome is ImportOutcome.Applied)

        val state = repository.state("s1")
        assertEquals(2, state.courses.size)
        assertEquals(2, state.meetings.size)
        assertEquals(1, state.exceptions.size)
        assertTrue(state.courses.all { it.semesterId == "s1" })

        // 首次导入也有快照（空表），可以撤销。
        val changeSetId = (outcome as ImportOutcome.Applied).changeSetId
        repository.revertChangeSet(changeSetId)
        assertTrue(repository.state("s1").isEmpty)
    }

    @Test
    fun `data survives database reopen`() = runBlocking {
        repository.importCandidate(semester, candidate(firstImport))
        db.close()

        val reopened = TimetableDatabase.build(context, allowMainThreadQueries = true)
        try {
            val repo2 = TimetableRepository(reopened)
            val state = repo2.state("s1")
            assertEquals(2, state.courses.size)
            assertEquals(2, state.meetings.size)
            assertEquals(1, state.exceptions.size)
            val restored = state.meetings.single { it.courseId == "qnu:c1" }
            assertEquals((1..16).toList(), restored.weekRule.weeks)
            assertEquals("1-16周", restored.weekRule.rawText)
            assertEquals("s1", repo2.semester("s1")?.id)
            val scheme = repo2.periodScheme("s1")
            assertEquals(13, scheme?.periods?.size)
            assertEquals("第1节", scheme?.periods?.first()?.label)
        } finally {
            reopened.close()
        }
    }

    @Test
    fun `identical reimport reports no change`() = runBlocking {
        repository.importCandidate(semester, candidate(firstImport))
        val outcome = repository.importCandidate(semester, candidate(firstImport))
        assertTrue(outcome is ImportOutcome.NoChange)
        assertEquals(1, repository.changeSets("s1").size)
    }

    @Test fun `metadata reimport is reviewable persists and is reversible`() = runBlocking {
        repository.importCandidate(semester,candidate(firstImport))
        val enriched=firstImport.copy(courses=firstImport.courses.map {
            it.copy(credits="3.0",selectionAttribute="必修",assessmentMethod="考试")
        })
        val pending=repository.importCandidate(semester,candidate(enriched)) as ImportOutcome.Pending
        assertEquals(2,pending.diff.changedCourses.size)
        assertEquals(null,repository.state("s1").courses.first().credits)
        repository.applyChangeSet(pending.changeSetId)
        assertTrue(repository.state("s1").courses.all { it.credits=="3.0" && it.assessmentMethod=="考试" && it.selectionAttribute=="必修" })
        assertTrue(repository.importCandidate(semester,candidate(firstImport)) is ImportOutcome.NoChange)
        repository.revertChangeSet(pending.changeSetId)
        assertTrue(repository.state("s1").courses.all { it.credits==null && it.assessmentMethod==null })
    }

    @Test
    fun `changed reimport creates pending set then apply ignores overridden fields`() = runBlocking {
        repository.importCandidate(semester, candidate(firstImport))

        // 用户手动加课，并把 c1 的教室改掉（覆盖保护）。
        val manual = Course(
            id = "manual:m1", semesterId = "s1", sourceType = SourceType.MANUAL,
            sourceKey = "manual:m1", name = "手动课程",
        )
        db.timetableDao().insertCourses(listOf(cn.qztc.timetable.core.data.db.EntityMappers.toEntity(manual)))
        val overridden = meeting("c1", room = "用户教室").copy(overrideMask = setOf(MeetingField.ROOM))
        db.timetableDao().insertMeetings(
            listOf(cn.qztc.timetable.core.data.db.EntityMappers.toEntity(overridden, "s1")),
        )

        // 教务侧：c1 换教师换教室，c2 删除，新增 c3，补课取消。
        val second = TimetableState(
            courses = listOf(course("c1"), course("c3")),
            meetings = listOf(
                meeting("c1", teacher = "老师B", room = "教务教室"),
                meeting("c3", weekday = 5),
            ),
            exceptions = emptyList(),
        )
        val outcome = repository.importCandidate(semester, candidate(second))
        assertTrue(outcome is ImportOutcome.Pending)
        val pending = (outcome as ImportOutcome.Pending)
        assertEquals(2, pending.diff.addedCount) // c3 + 其安排
        assertEquals(1, pending.diff.changedCount)
        assertEquals(3, pending.diff.removedCount) // c2 + 其安排 + 补课

        // 未应用前课表不动。
        assertTrue(repository.state("s1").courses.any { it.id == "qnu:c2" })

        repository.applyChangeSet(pending.changeSetId)
        val state = repository.state("s1")
        assertEquals(setOf("qnu:c1", "qnu:c3", "manual:m1"), state.courses.map { it.id }.toSet())
        val c1 = state.meetings.single { it.courseId == "qnu:c1" }
        assertEquals("老师B", c1.teacher)
        assertEquals("用户教室", c1.room) // 覆盖字段保留
        assertEquals(setOf(MeetingField.ROOM), c1.overrideMask)
        assertTrue(state.exceptions.isEmpty())
    }

    @Test
    fun `applied change set can be reverted`() = runBlocking {
        repository.importCandidate(semester, candidate(firstImport))
        val second = firstImport.copy(courses = firstImport.courses + course("c9"))
        val pending = repository.importCandidate(semester, candidate(second)) as ImportOutcome.Pending
        repository.applyChangeSet(pending.changeSetId)
        assertTrue(repository.state("s1").courses.any { it.id == "qnu:c9" })

        repository.revertChangeSet(pending.changeSetId)
        val state = repository.state("s1")
        assertTrue(state.courses.none { it.id == "qnu:c9" })
        assertEquals(2, state.courses.size)
        assertEquals(ChangeSetStatus.REVERTED, repository.changeSet(pending.changeSetId)?.status)
    }

    @Test
    fun `ignored change set keeps timetable and can be applied later`() = runBlocking {
        repository.importCandidate(semester, candidate(firstImport))
        val second = firstImport.copy(courses = firstImport.courses + course("c9"))
        val pending = repository.importCandidate(semester, candidate(second)) as ImportOutcome.Pending

        repository.ignoreChangeSet(pending.changeSetId)
        assertTrue(repository.state("s1").courses.none { it.id == "qnu:c9" })
        assertEquals(ChangeSetStatus.IGNORED, repository.changeSet(pending.changeSetId)?.status)

        repository.applyChangeSet(pending.changeSetId)
        assertTrue(repository.state("s1").courses.any { it.id == "qnu:c9" })
    }

    @Test
    fun `new pending set supersedes older pending set`() = runBlocking {
        repository.importCandidate(semester, candidate(firstImport))
        val second = firstImport.copy(courses = firstImport.courses + course("c8"))
        val third = firstImport.copy(courses = firstImport.courses + course("c9"))
        val pending1 = repository.importCandidate(semester, candidate(second)) as ImportOutcome.Pending
        val pending2 = repository.importCandidate(semester, candidate(third)) as ImportOutcome.Pending

        assertEquals(ChangeSetStatus.SUPERSEDED, repository.changeSet(pending1.changeSetId)?.status)
        assertEquals(listOf(pending2.changeSetId), repository.pendingChangeSets("s1").map { it.id })
    }

    @Test
    fun `failed parse keeps last usable timetable`() = runBlocking {
        repository.importCandidate(semester, candidate(firstImport))
        val outcome = repository.importCandidate(
            semester,
            candidate(
                TimetableState.EMPTY,
                diagnostics = listOf(ImportDiagnostic.error("NO_TIMETABLE", "页面缺少主网格")),
            ),
        )
        assertTrue(outcome is ImportOutcome.Failed)
        assertEquals(2, repository.state("s1").courses.size)
        val failedRun = repository.syncRuns("s1").first()
        assertEquals(SyncRunStatus.FAILED.name, failedRun.status)
        assertTrue(failedRun.errorMessage!!.contains("NO_TIMETABLE"))
    }

    @Test
    fun `partially parsed candidate with errors is rejected as a whole`() = runBlocking {
        repository.importCandidate(semester, candidate(firstImport))
        val partial = TimetableState(
            courses = listOf(course("c1")),
            meetings = listOf(meeting("c1")),
            exceptions = emptyList(),
        )

        val outcome = repository.importCandidate(
            semester,
            candidate(
                partial,
                diagnostics = listOf(
                    ImportDiagnostic.error("WEEK_TEXT_UNRECOGNIZED", "另一门课程的周次无法识别"),
                ),
            ),
        )

        assertTrue(outcome is ImportOutcome.Failed)
        val kept = repository.state("s1")
        assertEquals(firstImport.courses.map { it.id }.toSet(), kept.courses.map { it.id }.toSet())
        assertEquals(firstImport.meetings.map { it.id }.toSet(), kept.meetings.map { it.id }.toSet())
        assertEquals(firstImport.exceptions.map { it.id }.toSet(), kept.exceptions.map { it.id }.toSet())
        assertEquals(1, repository.changeSets("s1").size)
    }

    @Test
    fun `period scheme is created once and not overwritten on reimport`() = runBlocking {
        repository.importCandidate(semester, candidate(firstImport))
        val original = checkNotNull(repository.periodScheme("s1"))
        assertEquals(13, original.periods.size)

        val edited = original.copy(
            name = "用户作息",
            periods = original.periods.map { period ->
                if (period.no == 1) period.copy(startTime = java.time.LocalTime.of(8, 0)) else period
            },
        )
        repository.upsertPeriodScheme(edited)

        val second = firstImport.copy(courses = firstImport.courses + course("c9"))
        repository.importCandidate(semester, candidate(second))
        val kept = checkNotNull(repository.periodScheme("s1"))
        assertEquals("用户作息", kept.name)
        assertEquals(java.time.LocalTime.of(8, 0), kept.periods.first().startTime)
    }

    @Test
    fun `settings persist across reopen`() = runBlocking {
        assertEquals(UserSettings(), repository.settings())
        repository.updateSettings(
            UserSettings(homeView = HomeView.WEEK, activeSemesterId = "s1", syncEnabled = true, syncIntervalHours = 4),
        )
        db.close()

        val reopened = TimetableDatabase.build(context, allowMainThreadQueries = true)
        try {
            val settings = TimetableRepository(reopened).settings()
            assertEquals(HomeView.WEEK, settings.homeView)
            assertEquals("s1", settings.activeSemesterId)
            assertTrue(settings.syncEnabled)
            assertEquals(4, settings.syncIntervalHours)
        } finally {
            reopened.close()
        }
    }

    @Test
    fun `manual activities survive qnu reimport`() = runBlocking {
        repository.importCandidate(semester, candidate(firstImport))
        val activity = cn.qztc.timetable.core.domain.model.ScheduleActivity(
            id = "activity-sports",
            semesterId = "s1",
            name = "运动会",
            note = "全校停课",
            timeText = "9月10日–9月12日",
            kind = cn.qztc.timetable.core.domain.model.ActivityKind.DAY,
            startDate = LocalDate.of(2024, 9, 10),
            endDate = LocalDate.of(2024, 9, 12),
        )
        repository.upsertActivity(activity)

        repository.importCandidate(semester, candidate(firstImport.copy(courses = firstImport.courses + course("c9"))))

        val kept = repository.activities("s1").single()
        assertEquals("运动会", kept.name)
        assertEquals(LocalDate.of(2024, 9, 10), kept.startDate)
        repository.deleteActivity(kept.id)
        assertTrue(repository.activities("s1").isEmpty())
    }

    @Test
    fun `calendar activities are hidden rather than deleted`() = runBlocking {
        val event = cn.qztc.timetable.core.domain.model.ScheduleActivity(
            id = "calendar-event:s1:event-1",
            semesterId = "s1",
            name = "正式上课",
            kind = cn.qztc.timetable.core.domain.model.ActivityKind.DAY,
            startDate = LocalDate.of(2024, 9, 2),
            endDate = LocalDate.of(2024, 9, 2),
        )
        repository.upsertActivity(event)

        repository.deleteActivity(event.id)

        assertTrue(repository.activities("s1").isEmpty())
        assertEquals(true, db.timetableDao().allActivities("s1").single().let {
            cn.qztc.timetable.core.data.db.EntityMappers.toDomain(it).hiddenFromSchedule
        })
        repository.upsertActivity(event)
        assertEquals("正式上课", repository.activities("s1").single().name)
    }

    @Test
    fun `deleteMeetings removes those rows and orphan courses`() = runBlocking {
        repository.importCandidate(semester, candidate(firstImport))
        val before = repository.state("s1")
        val keep = before.meetings.single { it.courseId == "qnu:c2" }
        val drop = before.meetings.single { it.courseId == "qnu:c1" }

        repository.deleteMeetings("s1", listOf(drop.id))

        val after = repository.state("s1")
        assertEquals(listOf(keep.id), after.meetings.map { it.id })
        assertTrue(after.courses.none { it.id == "qnu:c1" })
        assertTrue(after.courses.any { it.id == "qnu:c2" })
    }

    @Test
    fun `academic days persist and replace`() = runBlocking {
        val holiday = cn.qztc.timetable.core.domain.calendar.AcademicDay(
            id = "academic:s1:2024-10-01",
            semesterId = "s1",
            date = LocalDate.of(2024, 10, 1),
            kind = cn.qztc.timetable.core.domain.calendar.AcademicDayKind.HOLIDAY,
            name = "国庆节",
        )
        val makeup = holiday.copy(
            id = "academic:s1:2024-10-12",
            date = LocalDate.of(2024, 10, 12),
            kind = cn.qztc.timetable.core.domain.calendar.AcademicDayKind.MAKEUP,
            name = "国庆调休",
            sourceWeekday = 1,
            useCurrentWeek = true,
        )
        repository.replaceAcademicDays("s1", listOf(holiday, makeup))
        assertEquals(listOf("国庆节", "国庆调休"), repository.academicDays("s1").map { it.name })
        repository.replaceAcademicDays("s1", listOf(holiday))
        assertEquals(listOf("国庆节"), repository.academicDays("s1").map { it.name })
    }
}
