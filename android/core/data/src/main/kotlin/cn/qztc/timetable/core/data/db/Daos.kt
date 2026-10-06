package cn.qztc.timetable.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface TimetableDao {

    @Upsert
    suspend fun upsertSemester(semester: SemesterEntity)

    @Query("SELECT * FROM semesters WHERE id = :id")
    suspend fun semester(id: String): SemesterEntity?

    @Query("SELECT * FROM semesters ORDER BY startDate DESC")
    suspend fun semesters(): List<SemesterEntity>

    @Query("SELECT * FROM courses WHERE semesterId = :semesterId")
    suspend fun courses(semesterId: String): List<CourseEntity>

    @Query("SELECT * FROM meetings WHERE semesterId = :semesterId")
    suspend fun meetings(semesterId: String): List<MeetingEntity>

    @Query("SELECT * FROM schedule_exceptions WHERE semesterId = :semesterId")
    suspend fun exceptions(semesterId: String): List<ScheduleExceptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourses(courses: List<CourseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeetings(meetings: List<MeetingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExceptions(exceptions: List<ScheduleExceptionEntity>)

    @Upsert
    suspend fun upsertCourse(course: CourseEntity)

    @Upsert
    suspend fun upsertMeeting(meeting: MeetingEntity)

    @Query("DELETE FROM meetings WHERE id = :id")
    suspend fun deleteMeeting(id: String)

    @Query("DELETE FROM meetings WHERE courseId = :courseId")
    suspend fun deleteMeetingsByCourse(courseId: String)

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteCourse(id: String)

    @Query("DELETE FROM courses WHERE semesterId = :semesterId AND sourceType = :sourceType")
    suspend fun deleteCoursesBySource(semesterId: String, sourceType: String)

    @Query("DELETE FROM meetings WHERE semesterId = :semesterId AND sourceType = :sourceType")
    suspend fun deleteMeetingsBySource(semesterId: String, sourceType: String)

    @Query("DELETE FROM schedule_exceptions WHERE semesterId = :semesterId AND sourceType = :sourceType")
    suspend fun deleteExceptionsBySource(semesterId: String, sourceType: String)

    @Upsert
    suspend fun upsertPeriodScheme(scheme: PeriodSchemeEntity)

    @Query("SELECT * FROM period_schemes WHERE semesterId = :semesterId")
    suspend fun periodScheme(semesterId: String): PeriodSchemeEntity?

    @Query("SELECT * FROM activities WHERE semesterId = :semesterId AND hiddenFromSchedule = 0")
    suspend fun activities(semesterId: String): List<ActivityEntity>

    @Query("SELECT * FROM activities WHERE semesterId = :semesterId")
    suspend fun allActivities(semesterId: String): List<ActivityEntity>

    @Upsert
    suspend fun upsertActivity(activity: ActivityEntity)

    @Query("DELETE FROM activities WHERE id = :id")
    suspend fun deleteActivity(id: String)

    @Query("UPDATE activities SET hiddenFromSchedule = 1 WHERE id = :id")
    suspend fun hideActivity(id: String)

    @Query("SELECT * FROM activity_suppressions WHERE semesterId = :semesterId")
    suspend fun suppressions(semesterId: String): List<ActivitySuppressionEntity>

    @Upsert
    suspend fun upsertSuppressions(records: List<ActivitySuppressionEntity>)

    @Query("DELETE FROM activity_suppressions WHERE activityId = :activityId")
    suspend fun clearActivitySuppressions(activityId: String)

    @Query("UPDATE activity_suppressions SET activityId = NULL WHERE activityId = :activityId")
    suspend fun detachActivitySuppressions(activityId: String)

    @Query("DELETE FROM activity_suppressions WHERE id = :id")
    suspend fun restoreSuppression(id: String)

    @Query("SELECT * FROM schedule_place_bindings WHERE semesterId = :semesterId")
    suspend fun placeBindings(semesterId: String): List<SchedulePlaceBindingEntity>

    @Upsert
    suspend fun upsertPlaceBinding(binding: SchedulePlaceBindingEntity)

    @Query("DELETE FROM schedule_place_bindings WHERE ownerId = :ownerId")
    suspend fun deletePlaceBinding(ownerId: String)

    @Query("SELECT * FROM academic_days WHERE semesterId = :semesterId ORDER BY date")
    suspend fun academicDays(semesterId: String): List<AcademicDayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAcademicDays(days: List<AcademicDayEntity>)

    @Query("DELETE FROM academic_days WHERE semesterId = :semesterId")
    suspend fun deleteAcademicDays(semesterId: String)
}

@Dao
interface SyncDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncRun(run: SyncRunEntity)

    @Query("SELECT * FROM sync_runs WHERE semesterId = :semesterId ORDER BY startedAt DESC")
    suspend fun syncRuns(semesterId: String): List<SyncRunEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChangeSet(changeSet: ChangeSetEntity)

    @Query("SELECT * FROM change_sets WHERE id = :id")
    suspend fun changeSet(id: String): ChangeSetEntity?

    @Query("SELECT * FROM change_sets WHERE semesterId = :semesterId ORDER BY createdAt DESC")
    suspend fun changeSets(semesterId: String): List<ChangeSetEntity>

    @Query("SELECT * FROM change_sets WHERE semesterId = :semesterId AND status = :status ORDER BY createdAt DESC")
    suspend fun changeSetsByStatus(semesterId: String, status: String): List<ChangeSetEntity>

    @Query("UPDATE change_sets SET status = :status, resolvedAt = :resolvedAt WHERE id = :id")
    suspend fun updateChangeSetStatus(id: String, status: String, resolvedAt: Long?)
}

@Dao
interface SettingsDao {

    @Query("SELECT * FROM user_settings WHERE id = 1")
    suspend fun settings(): UserSettingsEntity?

    @Upsert
    suspend fun upsertSettings(settings: UserSettingsEntity)
}
