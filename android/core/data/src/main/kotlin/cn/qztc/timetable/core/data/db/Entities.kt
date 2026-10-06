package cn.qztc.timetable.core.data.db

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "semesters")
data class SemesterEntity(
    @PrimaryKey val id: String,
    val name: String,
    val schoolYear: Int,
    val term: String,
    val startDate: String,
    val totalWeeks: Int,
    val archived: Boolean,
)

@Entity(tableName = "courses", indices = [Index("semesterId")])
data class CourseEntity(
    @PrimaryKey val id: String,
    val semesterId: String,
    val sourceType: String,
    val sourceKey: String,
    val code: String?,
    val name: String,
    val sequence: String?,
    val color: Int?,
    val note: String?,
    val nature: String? = null,
    val credits: String? = null,
    val selectionAttribute: String? = null,
    val assessmentMethod: String? = null,
)

@Entity(tableName = "meetings", indices = [Index("semesterId"), Index("courseId")])
data class MeetingEntity(
    @PrimaryKey val id: String,
    val semesterId: String,
    val courseId: String,
    val weekday: Int,
    val periodStart: Int,
    val periodEnd: Int,
    val weeksCsv: String,
    val weekRaw: String,
    val teacher: String?,
    val room: String?,
    val sourceType: String,
    val overrideMaskCsv: String,
)

@Entity(tableName = "schedule_exceptions", indices = [Index("semesterId"), Index("courseId")])
data class ScheduleExceptionEntity(
    @PrimaryKey val id: String,
    val semesterId: String,
    val courseId: String,
    val type: String,
    val originalJson: String?,
    val replacementJson: String?,
    val substituteTeacher: String?,
    val note: String?,
    val sourceType: String,
)

@Entity(tableName = "sync_runs", indices = [Index("semesterId")])
data class SyncRunEntity(
    @PrimaryKey val id: String,
    val semesterId: String,
    val source: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val status: String,
    val errorMessage: String?,
    val changeSetId: String?,
)

@Entity(
    tableName = "change_sets",
    indices = [
        Index("semesterId"),
        Index(value = ["semesterId", "createdAt"]),
    ],
)
data class ChangeSetEntity(
    @PrimaryKey val id: String,
    val semesterId: String,
    val syncRunId: String?,
    val status: String,
    val createdAt: Long,
    val resolvedAt: Long?,
    val diffJson: String,
    val snapshotJson: String,
    val addedCount: Int,
    val changedCount: Int,
    val removedCount: Int,
    val conflictCount: Int,
)

@Entity(tableName = "period_schemes")
data class PeriodSchemeEntity(
    @PrimaryKey val semesterId: String,
    val id: String,
    val name: String,
    val editable: Boolean,
    val periodsJson: String,
)

@Entity(tableName = "activities", indices = [Index("semesterId")])
data class ActivityEntity(
    @PrimaryKey val id: String,
    val semesterId: String,
    val name: String,
    val note: String,
    val timeText: String,
    val kind: String,
    val color: Int?,
    val weekday: Int?,
    val periodStart: Int?,
    val periodEnd: Int?,
    val weeksCsv: String,
    val startDate: String?,
    val endDate: String?,
    @ColumnInfo(defaultValue = "'[]'") val sessionsJson: String = "[]",
    @ColumnInfo(defaultValue = "0") val hiddenFromSchedule: Boolean = false,
)

@Entity(tableName = "activity_suppressions", indices = [Index("semesterId"), Index("activityId")])
data class ActivitySuppressionEntity(
    @PrimaryKey val id: String,
    val semesterId: String,
    val activityId: String?,
    val occurrenceKey: String,
    @ColumnInfo(defaultValue = "0") val coverageVersion: Int = 1,
)

@Entity(tableName = "schedule_place_bindings", indices = [Index("semesterId")])
data class SchedulePlaceBindingEntity(
    @PrimaryKey val ownerId: String,
    val semesterId: String,
    val locationJson: String,
)

@Entity(
    tableName = "academic_days",
    indices = [
        Index("semesterId"),
        Index(value = ["semesterId", "date"], unique = true),
    ],
)
data class AcademicDayEntity(
    @PrimaryKey val id: String,
    val semesterId: String,
    val date: String,
    val kind: String,
    val name: String,
    val sourceWeek: Int?,
    val sourceWeekday: Int?,
    val useCurrentWeek: Boolean,
    val useCurrentWeekday: Boolean,
)

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val homeView: String,
    val activeSemesterId: String?,
    val syncEnabled: Boolean,
    val syncIntervalHours: Int,
)
