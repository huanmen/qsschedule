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
class CourseMetadataMigrationTest {
    @Test fun migrateSixAndPersistCourseDetails() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="migration-metadata-${System.nanoTime()}.db"
        val schema=JSONObject(javaClass.classLoader!!.getResourceAsStream(
            "cn.qztc.timetable.core.data.db.TimetableDatabase/6.json",
        )!!.bufferedReader().use { it.readText() }).getJSONObject("database")
        context.openOrCreateDatabase(name,0,null).use { sqlite ->
            val entities=schema.getJSONArray("entities")
            for(i in 0 until entities.length()) {
                val entity=entities.getJSONObject(i)
                sqlite.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",entity.getString("tableName")))
                val indexes=entity.optJSONArray("indices")
                if(indexes!=null)for(j in 0 until indexes.length())sqlite.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",entity.getString("tableName")))
            }
            val setup=schema.getJSONArray("setupQueries")
            for(i in 0 until setup.length())sqlite.execSQL(setup.getString(i))
            sqlite.execSQL("INSERT INTO courses (id,semesterId,sourceType,sourceKey,name,credits,note) VALUES ('c','s','QNU_EDU','c','Synthetic course','3.0','keep')")
            sqlite.version=6
        }
        try {
            val db=TimetableDatabase.build(context,name,true)
            try {
                val repository=TimetableRepository(db)
                val course=repository.state("s").courses.single()
                assertEquals("3.0",course.credits)
                assertEquals("keep",course.note)
                assertNull(course.assessmentMethod)
                repository.updateCourseAndMeeting(course.copy(selectionAttribute="必修",assessmentMethod="考试"),null,null)
            } finally { db.close() }
            val reopened=TimetableDatabase.build(context,name,true)
            try {
                val saved=TimetableRepository(reopened).state("s").courses.single()
                assertEquals("必修",saved.selectionAttribute)
                assertEquals("考试",saved.assessmentMethod)
            } finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }
}
