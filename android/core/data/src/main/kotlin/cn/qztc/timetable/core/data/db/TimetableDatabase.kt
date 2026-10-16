package cn.qztc.timetable.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SemesterEntity::class,
        CourseEntity::class,
        MeetingEntity::class,
        ScheduleExceptionEntity::class,
        PeriodSchemeEntity::class,
        SyncRunEntity::class,
        ChangeSetEntity::class,
        ActivityEntity::class,
        AcademicDayEntity::class,
        UserSettingsEntity::class,
        ActivitySuppressionEntity::class,
        SchedulePlaceBindingEntity::class,
    ],
    version = 11,
    exportSchema = true,
)
abstract class TimetableDatabase : RoomDatabase() {

    abstract fun timetableDao(): TimetableDao
    abstract fun syncDao(): SyncDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        const val DEFAULT_NAME = "timetable.db"
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `activities` ADD COLUMN `memoTimeJson` TEXT")
            }
        }

        fun build(
            context: Context,
            name: String = DEFAULT_NAME,
            allowMainThreadQueries: Boolean = false,
        ): TimetableDatabase {
            val builder = Room.databaseBuilder(
                context.applicationContext,
                TimetableDatabase::class.java,
                name,
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
            if (allowMainThreadQueries) builder.allowMainThreadQueries()
            return builder.build()
        }

        /** Adds the ordering index without rewriting any timetable rows. */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_change_sets_semesterId_createdAt` " +
                        "ON `change_sets` (`semesterId`, `createdAt`)"
                )
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `activities` (" +
                        "`id` TEXT NOT NULL, " +
                        "`semesterId` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`timeText` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, " +
                        "`color` INTEGER, " +
                        "`weekday` INTEGER, " +
                        "`periodStart` INTEGER, " +
                        "`periodEnd` INTEGER, " +
                        "`weeksCsv` TEXT NOT NULL, " +
                        "`startDate` TEXT, " +
                        "`endDate` TEXT, " +
                        "PRIMARY KEY(`id`))"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_activities_semesterId` ON `activities` (`semesterId`)"
                )
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `courses` ADD COLUMN `nature` TEXT")
                database.execSQL("ALTER TABLE `courses` ADD COLUMN `credits` TEXT")
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `academic_days` (" +
                        "`id` TEXT NOT NULL, " +
                        "`semesterId` TEXT NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`sourceWeek` INTEGER, " +
                        "`sourceWeekday` INTEGER, " +
                        "`useCurrentWeek` INTEGER NOT NULL, " +
                        "`useCurrentWeekday` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_academic_days_semesterId` " +
                        "ON `academic_days` (`semesterId`)",
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_academic_days_semesterId_date` " +
                        "ON `academic_days` (`semesterId`, `date`)",
                )
            }
        }

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `activities` ADD COLUMN `sessionsJson` TEXT NOT NULL DEFAULT '[]'")
                database.execSQL("CREATE TABLE IF NOT EXISTS `activity_suppressions` (`id` TEXT NOT NULL, `semesterId` TEXT NOT NULL, `activityId` TEXT, `occurrenceKey` TEXT NOT NULL, PRIMARY KEY(`id`))")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_activity_suppressions_semesterId` ON `activity_suppressions` (`semesterId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_activity_suppressions_activityId` ON `activity_suppressions` (`activityId`)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `schedule_place_bindings` (`ownerId` TEXT NOT NULL, `semesterId` TEXT NOT NULL, `locationJson` TEXT NOT NULL, PRIMARY KEY(`ownerId`))")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_place_bindings_semesterId` ON `schedule_place_bindings` (`semesterId`)")
                // Use the same legacy decoder as normal reads, without requiring SQLite JSON extensions.
                database.query("SELECT * FROM activities").use { cursor ->
                    fun text(column: String) = cursor.getString(cursor.getColumnIndexOrThrow(column))
                    fun number(column: String): Int? = cursor.getColumnIndexOrThrow(column).let { if (cursor.isNull(it)) null else cursor.getInt(it) }
                    while (cursor.moveToNext()) {
                        val entity = ActivityEntity(text("id"), text("semesterId"), text("name"), text("note"), text("timeText"),
                            text("kind"), number("color"), number("weekday"), number("periodStart"), number("periodEnd"),
                            text("weeksCsv"), text("startDate"), text("endDate"))
                        val migrated = EntityMappers.toEntity(EntityMappers.toDomain(entity))
                        database.execSQL("UPDATE activities SET sessionsJson = ? WHERE id = ?", arrayOf(migrated.sessionsJson, entity.id))
                    }
                }
            }
        }

        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `courses` ADD COLUMN `selectionAttribute` TEXT")
                database.execSQL("ALTER TABLE `courses` ADD COLUMN `assessmentMethod` TEXT")
            }
        }

        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `activities` ADD COLUMN `hiddenFromSchedule` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `activity_suppressions` ADD COLUMN `coverageVersion` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_9_10: Migration = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `activities` ADD COLUMN `calendarTimetable` INTEGER NOT NULL DEFAULT 1")
            }
        }
    }
}
