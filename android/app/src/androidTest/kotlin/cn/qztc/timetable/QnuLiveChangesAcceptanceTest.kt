package cn.qztc.timetable

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cn.qztc.timetable.core.data.db.TimetableDatabase
import cn.qztc.timetable.core.data.repository.TimetableRepository
import cn.qztc.timetable.core.domain.imports.ImportedTimetable
import cn.qztc.timetable.core.domain.model.*
import cn.qztc.timetable.core.domain.schedule.*
import cn.qztc.timetable.core.ui.theme.QztcTheme
import cn.qztc.timetable.qnu.QnuCredentialStore
import cn.qztc.timetable.qnu.QnuImportScreen
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Opt-in, normal school WebView login. Results stay in memory; no timetable/account writes. */
class QnuLiveChangesAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<FeatureAcceptanceActivity>()

    @Test fun selectedSemesterReadsBothNoticeSourcesWithoutChangingStoredTimetables(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("liveSchoolCheck") == "true") { "Live school check must be explicitly selected" }
        val credentials = QnuCredentialStore(context).load()
        assertNotNull("Configure an account in the App first", credentials)
        val database = TimetableDatabase.build(context)
        try {
            val repository = TimetableRepository(database)
            val terms = repository.semesters().filter { repository.state(it.id).courses.isNotEmpty() }
                .sortedWith(compareBy({ it.schoolYear }, { it.term.ordinal }))
            android.util.Log.i("QnuLiveCheck", "available=" + terms.joinToString { "${it.schoolYear}-${it.term}" })
            val target = if (args.getString("schoolSemester") == "previous") terms.dropLast(1).lastOrNull() else terms.lastOrNull()
            checkNotNull(target) { "No matching imported semester" }
            val originalSettings = repository.settings()
            val before = terms.associate { it.id to repository.state(it.id) }
            val candidate = AtomicReference<ImportedTimetable?>()
            val failure = AtomicReference<String?>()
            compose.setContent { QztcTheme {
                QnuImportScreen(credentials, onBack = {}, onImported = { candidate.set(it) },
                    automaticCheck = true, syncOnly = true, checkSemester = target,
                    onCheckFailed = { failure.compareAndSet(null, it) })
            } }
            compose.waitUntil(125_000) { candidate.get() != null || failure.get() != null }
            assertNull("School check failed: ${failure.get()}", failure.get())
            val incoming = checkNotNull(candidate.get())
            assertEquals(target.schoolYear, incoming.semesterHint?.year)
            assertEquals(target.term, incoming.semesterHint?.term)
            assertTrue(incoming.state.meetings.isEmpty())
            val merged = UpcomingScheduleChanges.merge(target, before.getValue(target.id), incoming.state,
                repository.periodScheme(target.id)?.periods.orEmpty(), LocalDateTime.now(), includeExpired = true)
            val recognized = merged.exceptions.filter { notice -> incoming.state.exceptions.any { school ->
                notice.type == school.type && notice.original == school.original && notice.replacement == school.replacement
            } }
            assertTrue(incoming.state.exceptions.all { notice -> listOfNotNull(notice.original, notice.replacement).all { it.week > 0 && it.weekday in 1..7 } })
            if (args.getString("expectScreenshotNotice") == "true") {
                // The specific notice supplied in the user's screenshot.
                val course = incoming.state.courses.singleOrNull { it.code == "0701417" }
                assertNotNull("Expected school cancellation was not returned", course)
                val cancellation = incoming.state.exceptions.single { it.courseId == course!!.id && it.type == ExceptionType.CANCEL }
                assertEquals(6, cancellation.original?.week)
                assertEquals(4, cancellation.original?.weekday)
                assertEquals(3, cancellation.original?.periodStart)
                assertEquals(4, cancellation.original?.periodEnd)
                assertTrue("Notice must match the student's imported course", recognized.any { it.original == cancellation.original })
            }
            assertEquals(originalSettings, repository.settings())
            for (term in terms) assertEquals("Stored timetable changed", before.getValue(term.id), repository.state(term.id))
            // Deliberately omit names, codes, accounts, HTML and session data.
            android.util.Log.i("QnuLiveCheck", "PASS year=${target.schoolYear} term=${target.term} notices=${incoming.state.exceptions.size} matched=${recognized.size} storedTimetablesUnchanged=true")
            Unit
        } finally { database.close() }
    }
}
