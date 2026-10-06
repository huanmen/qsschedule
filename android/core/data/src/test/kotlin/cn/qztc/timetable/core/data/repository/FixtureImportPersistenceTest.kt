package cn.qztc.timetable.core.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cn.qztc.timetable.core.data.db.TimetableDatabase
import cn.qztc.timetable.core.domain.model.Semester
import cn.qztc.timetable.core.domain.model.Term
import cn.qztc.timetable.integration.qnu.QnuTimetableImporter
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
class FixtureImportPersistenceTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: TimetableDatabase
    private lateinit var repository: TimetableRepository

    @Before
    fun setUp() {
        context.deleteDatabase("fixture-import.db")
        db = TimetableDatabase.build(context, name = "fixture-import.db", allowMainThreadQueries = true)
        repository = TimetableRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.classLoader!!.getResourceAsStream("fixtures/$name")) {
            "missing fixture $name"
        }.readBytes()

    @Test
    fun `standard gbk fixture imports, survives reopen, then change can be applied and reverted`() = runBlocking {
        val semester = Semester(
            id = "s-standard",
            name = "2024 秋",
            schoolYear = 2024,
            term = Term.AUTUMN,
            startDate = LocalDate.of(2024, 9, 2),
        )
        repository.upsertSemester(semester)

        val imported = QnuTimetableImporter.import(fixture("qnu-timetable-standard.gbk.html"), semester.id)
        assertTrue(!imported.hasErrors)
        val first = repository.importCandidate(semester, imported)
        assertTrue(first is ImportOutcome.Applied)

        val afterFirst = repository.state(semester.id)
        assertTrue(afterFirst.courses.isNotEmpty())
        assertTrue(afterFirst.meetings.isNotEmpty())
        val scheme = checkNotNull(repository.periodScheme(semester.id))
        assertEquals(13, scheme.periods.size)
        assertEquals("08:20", scheme.periods.first().startTime.toString().substring(0, 5))

        db.close()
        val reopened = TimetableDatabase.build(context, name = "fixture-import.db", allowMainThreadQueries = true)
        try {
            val repo2 = TimetableRepository(reopened)
            val restored = repo2.state(semester.id)
            assertEquals(afterFirst.courses.size, restored.courses.size)
            assertEquals(afterFirst.meetings.size, restored.meetings.size)
            assertEquals(afterFirst.exceptions.size, restored.exceptions.size)

            val secondImport = QnuTimetableImporter.import(
                fixture("qnu-timetable-changes.gbk.html"),
                semester.id,
            )
            assertTrue(!secondImport.hasErrors)
            val pending = repo2.importCandidate(semester, secondImport)
            assertTrue(pending is ImportOutcome.Pending)
            val changeSetId = (pending as ImportOutcome.Pending).changeSetId
            assertTrue(pending.diff.addedCount + pending.diff.changedCount + pending.diff.removedCount > 0)

            repo2.applyChangeSet(changeSetId)
            val applied = repo2.state(semester.id)
            assertTrue(applied.courses.isNotEmpty())
            assertTrue(applied.courses.map { it.id }.toSet() != restored.courses.map { it.id }.toSet())

            repo2.revertChangeSet(changeSetId)
            val reverted = repo2.state(semester.id)
            assertEquals(restored.courses.map { it.id }.toSet(), reverted.courses.map { it.id }.toSet())
            assertEquals(restored.meetings.map { it.id }.toSet(), reverted.meetings.map { it.id }.toSet())
        } finally {
            reopened.close()
        }
    }
}
