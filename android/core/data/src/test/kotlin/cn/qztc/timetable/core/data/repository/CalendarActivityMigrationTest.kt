package cn.qztc.timetable.core.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cn.qztc.timetable.core.data.db.TimetableDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CalendarActivityMigrationTest {
    @Test fun migrationPreservesExistingActivityAndPersistsCalendarOnlyFlag() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "calendar-policy-${System.nanoTime()}.db"
        val schema = JSONObject(javaClass.classLoader!!.getResourceAsStream(
            "cn.qztc.timetable.core.data.db.TimetableDatabase/9.json",
        )!!.bufferedReader().use { it.readText() }).getJSONObject("database")
        context.openOrCreateDatabase(name, 0, null).use { sqlite ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                sqlite.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indexes = entity.optJSONArray("indices")
                if (indexes != null) for (j in 0 until indexes.length())
                    sqlite.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) sqlite.execSQL(setup.getString(i))
            sqlite.execSQL("INSERT INTO activities (id,semesterId,name,note,timeText,kind,weeksCsv,startDate,endDate,sessionsJson,hiddenFromSchedule) VALUES ('a','s','Saved activity','keep','','DAY','','2026-10-14','2026-10-14','[]',0)")
            sqlite.version = 9
        }
        try {
            val db = TimetableDatabase.build(context, name, true)
            try {
                val repository = TimetableRepository(db)
                val saved = repository.activities("s").single()
                assertEquals("Saved activity", saved.name)
                assertEquals("keep", saved.note)
                assertTrue(saved.calendarTimetable)
                assertNull(saved.memoTime)
                repository.upsertActivity(saved.copy(calendarTimetable = false,
                    memoTime = cn.qztc.timetable.core.domain.model.ActivityMemoTime(java.time.LocalDate.of(2026, 10, 14), 0)))
            } finally { db.close() }
            val reopened = TimetableDatabase.build(context, name, true)
            try {
                val saved = TimetableRepository(reopened).activities("s").single()
                assertFalse(saved.calendarTimetable)
                assertEquals("keep", saved.note)
                assertEquals(0, saved.memoTime?.minute)
                assertEquals(java.time.LocalDate.of(2026, 10, 14), saved.memoTime?.date)
                assertTrue(saved.effectiveSessions().isEmpty())
            } finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }
}
