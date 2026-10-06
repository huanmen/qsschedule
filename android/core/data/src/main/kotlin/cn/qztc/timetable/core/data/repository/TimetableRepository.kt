package cn.qztc.timetable.core.data.repository

import androidx.room.withTransaction
import cn.qztc.timetable.core.data.db.ActivitySuppressionEntity
import cn.qztc.timetable.core.data.db.SchedulePlaceBindingEntity
import cn.qztc.timetable.core.domain.model.ScheduleLocation
import cn.qztc.timetable.core.domain.calendar.AcademicCalendar
import cn.qztc.timetable.core.data.db.ChangeSetEntity
import cn.qztc.timetable.core.data.db.EntityMappers
import cn.qztc.timetable.core.data.db.SyncRunEntity
import cn.qztc.timetable.core.data.db.TimetableDatabase
import cn.qztc.timetable.core.domain.diff.TimetableDiff
import cn.qztc.timetable.core.domain.diff.TimetableDiffApplier
import cn.qztc.timetable.core.domain.diff.TimetableDiffer
import cn.qztc.timetable.core.domain.imports.ImportDiagnostic
import cn.qztc.timetable.core.domain.calendar.AcademicDay
import cn.qztc.timetable.core.domain.imports.ImportedTimetable
import cn.qztc.timetable.core.domain.model.Course
import cn.qztc.timetable.core.domain.model.CourseMeeting
import cn.qztc.timetable.core.domain.model.MeetingField
import cn.qztc.timetable.core.domain.model.PeriodScheme
import cn.qztc.timetable.core.domain.model.PeriodSchemeFactory
import cn.qztc.timetable.core.domain.model.ScheduleActivity
import cn.qztc.timetable.core.domain.model.ScheduleException
import cn.qztc.timetable.core.domain.model.Semester
import cn.qztc.timetable.core.domain.model.SlotRef
import cn.qztc.timetable.core.domain.model.SourceType
import cn.qztc.timetable.core.domain.model.UserSettings
import cn.qztc.timetable.core.domain.schedule.OccurrenceExpander
import cn.qztc.timetable.core.domain.schedule.TimetableState
import java.util.UUID
import kotlinx.serialization.json.Json

enum class ChangeSetStatus { PENDING, APPLIED, IGNORED, REVERTED, SUPERSEDED }

enum class SyncRunStatus { SUCCESS_NO_CHANGE, SUCCESS_PENDING, SUCCESS_APPLIED, FAILED }

data class ChangeSetRecord(
    val id: String,
    val semesterId: String,
    val syncRunId: String?,
    val status: ChangeSetStatus,
    val createdAt: Long,
    val resolvedAt: Long?,
    val diff: TimetableDiff,
    val snapshot: TimetableState,
    val addedCount: Int,
    val changedCount: Int,
    val removedCount: Int,
    val conflictCount: Int,
)

sealed class ImportOutcome {
    /** 解析存在错误：拒绝整批候选数据，保留最后一份课表，只记录失败。 */
    data class Failed(val syncRunId: String, val diagnostics: List<ImportDiagnostic>) : ImportOutcome()

    data class NoChange(val syncRunId: String) : ImportOutcome()

    /** 本地还没有教务课表，首次导入直接生效；快照为空表，仍可撤销。 */
    data class Applied(val syncRunId: String, val changeSetId: String, val diff: TimetableDiff) : ImportOutcome()

    /** 已有课表出现变化：生成待确认变更集，等用户应用或忽略。 */
    data class Pending(val syncRunId: String, val changeSetId: String, val diff: TimetableDiff) : ImportOutcome()
}

/**
 * 课表持久化仓库：导入候选数据 → 差异 → 变更集（应用 / 忽略 / 恢复，全部可逆）。
 * 同步来源只覆盖 QNU_EDU 行，手动数据永不被导入触碰。
 */
class TimetableRepository(private val db: TimetableDatabase) {

    private val json = Json { ignoreUnknownKeys = true }
    private val timetableDao get() = db.timetableDao()
    private val syncDao get() = db.syncDao()
    private val settingsDao get() = db.settingsDao()

    suspend fun upsertSemester(semester: Semester) =
        timetableDao.upsertSemester(EntityMappers.toEntity(semester))

    suspend fun semester(id: String): Semester? =
        timetableDao.semester(id)?.let(EntityMappers::toDomain)

    suspend fun semesters(): List<Semester> =
        timetableDao.semesters().map(EntityMappers::toDomain)

    /** 学期全部课表数据（所有来源）。 */
    suspend fun state(semesterId: String): TimetableState = db.withTransaction {
        val records = timetableDao.suppressions(semesterId)
        val state = repairLegacyQnuDetailPeriods(TimetableState(
            courses = timetableDao.courses(semesterId).map(EntityMappers::toDomain),
            meetings = timetableDao.meetings(semesterId).map(EntityMappers::toDomain),
            exceptions = timetableDao.exceptions(semesterId).map(EntityMappers::toDomain),
            suppressedOccurrences = records.map { it.occurrenceKey }.distinct(),
        ))
        repairLegacyCourseCoverage(semesterId, state, records)
    }

    /** Complete only previously accepted replacements on dates skipped by the old
     * holiday/activity expansion. Version each record so a later manual restore sticks. */
    private suspend fun repairLegacyCourseCoverage(
        semesterId: String, state: TimetableState, records: List<ActivitySuppressionEntity>,
    ): TimetableState {
        val legacy = records.filter { it.coverageVersion == 0 }
        if (legacy.isEmpty()) return state
        val semester = semester(semesterId) ?: return state
        val calendar = AcademicCalendar(academicDays(semesterId))
        val unhidden = state.copy(suppressedOccurrences = emptyList())
        val missing = mutableListOf<ActivitySuppressionEntity>()
        legacy.filter { it.activityId?.startsWith("course-coverage:") == true }
            .groupBy { it.activityId!! }.forEach { (owner, accepted) ->
                val courseId = owner.removePrefix("course-coverage:")
                val course = state.courses.find { it.id == courseId } ?: return@forEach
                val replacement = TimetableState(listOf(course),
                    state.meetings.filter { it.courseId == courseId },
                    state.exceptions.filter { it.courseId == courseId })
                val acceptedMeetings = accepted.map { it.occurrenceKey.split('|').take(2) }.toSet()
                val owned = records.filter { it.activityId == owner }.map { it.occurrenceKey }.toSet()
                OccurrenceExpander.replacementTargets(semester, unhidden, replacement, calendar).forEach { target ->
                    val key = OccurrenceExpander.key(target)
                    val skippedDate = calendar.dayOn(target.date) != null ||
                        records.any { it.activityId?.startsWith("course-coverage:") == false && it.occurrenceKey == key }
                    if (key !in owned && key.split('|').take(2) in acceptedMeetings && skippedDate) {
                        missing += ActivitySuppressionEntity("$owner:$key", semesterId, owner, key)
                    }
                }
            }
        timetableDao.upsertSuppressions(legacy.map { it.copy(coverageVersion = 1) } + missing)
        return state.copy(suppressedOccurrences = (state.suppressedOccurrences + missing.map { it.occurrenceKey }).distinct())
    }

    /**
     * 旧版曾把教务变更明细的第 5 节以后直接保存，漏掉内部午间两格。
     * 只有原位置匹配不到而 +2 后能匹配同一课次时才修复，避免移动合法数据。
     */
    private fun repairLegacyQnuDetailPeriods(state: TimetableState): TimetableState {
        fun matchesMeeting(exception: ScheduleException, slot: SlotRef): Boolean =
            state.meetings.any { meeting ->
                meeting.courseId == exception.courseId &&
                    meeting.weekday == slot.weekday &&
                    slot.week in meeting.weekRule &&
                    meeting.periodStart <= slot.periodEnd &&
                    slot.periodStart <= meeting.periodEnd
            }

        fun shifted(slot: SlotRef): SlotRef? =
            if (slot.periodStart in 5..11 && slot.periodEnd in 5..11) {
                slot.copy(periodStart = slot.periodStart + 2, periodEnd = slot.periodEnd + 2)
            } else null

        val repaired = state.exceptions.map { exception ->
            val original = exception.original
            val correctedOriginal = original
                ?.takeIf { exception.sourceType == SourceType.QNU_EDU && !matchesMeeting(exception, it) }
                ?.let(::shifted)
                ?.takeIf { matchesMeeting(exception, it) }
                ?: return@map exception
            val correctedReplacement = exception.replacement?.let(::shifted) ?: exception.replacement
            exception.copy(
                id = ScheduleException.stableId(exception.type, exception.courseId, correctedOriginal),
                original = correctedOriginal,
                replacement = correctedReplacement,
            )
        }
        return if (repaired == state.exceptions) state else state.copy(exceptions = repaired)
    }

    suspend fun periodScheme(semesterId: String): PeriodScheme? =
        timetableDao.periodScheme(semesterId)?.let(EntityMappers::toDomain)

    /** Apply one reviewed exception without replacing courses or other exceptions. */
    suspend fun applyScheduleException(semesterId: String, exception: ScheduleException) {
        db.withTransaction {
            val semester = requireNotNull(semester(semesterId))
            require(state(semesterId).courses.any { it.id == exception.courseId })
            require(exception.type in setOf(cn.qztc.timetable.core.domain.model.ExceptionType.CANCEL, cn.qztc.timetable.core.domain.model.ExceptionType.MOVE))
            require(exception.original != null)
            require(exception.type != cn.qztc.timetable.core.domain.model.ExceptionType.MOVE || exception.replacement != null)
            listOfNotNull(exception.original, exception.replacement).forEach { slot ->
                require(slot.week in 1..semester.totalWeeks && slot.weekday in 1..7 && slot.periodStart in 1..13 && slot.periodEnd in slot.periodStart..13)
            }
            timetableDao.insertExceptions(listOf(EntityMappers.toEntity(exception, semesterId)))
        }
    }

    suspend fun upsertPeriodScheme(scheme: PeriodScheme) =
        timetableDao.upsertPeriodScheme(EntityMappers.toEntity(scheme))

    suspend fun importCandidate(
        semester: Semester,
        candidate: ImportedTimetable,
        now: Long = System.currentTimeMillis(),
    ): ImportOutcome {
        val syncRunId = UUID.randomUUID().toString()
        val bound = bindSemester(candidate.state, semester.id)

        // A partially parsed page is not a trustworthy full snapshot. Applying it could
        // misclassify skipped records as deletions, so any error rejects the whole import.
        if (candidate.hasErrors) {
            recordSyncRun(syncRunId, semester.id, now, SyncRunStatus.FAILED, errorMessage = errorSummary(candidate))
            return ImportOutcome.Failed(syncRunId, candidate.diagnostics)
        }

        val currentQnu = state(semester.id).filterBySource(SourceType.QNU_EDU)
        val diff = TimetableDiffer.diff(currentQnu, bound)
        if (diff.isEmpty) {
            recordSyncRun(syncRunId, semester.id, now, SyncRunStatus.SUCCESS_NO_CHANGE)
            return ImportOutcome.NoChange(syncRunId)
        }

        val applied = TimetableDiffApplier.apply(currentQnu, diff)
        val conflictCount = OccurrenceExpander.findConflicts(OccurrenceExpander.expand(semester, applied)).size
        val changeSetId = UUID.randomUUID().toString()
        val autoApply = currentQnu.isEmpty

        db.withTransaction {
            // 新变更集出现后，旧的未处理变更集不再可应用，标记为已被取代。
            for (stale in syncDao.changeSetsByStatus(semester.id, ChangeSetStatus.PENDING.name)) {
                syncDao.updateChangeSetStatus(stale.id, ChangeSetStatus.SUPERSEDED.name, now)
            }
            syncDao.insertChangeSet(
                ChangeSetEntity(
                    id = changeSetId,
                    semesterId = semester.id,
                    syncRunId = syncRunId,
                    status = (if (autoApply) ChangeSetStatus.APPLIED else ChangeSetStatus.PENDING).name,
                    createdAt = now,
                    resolvedAt = if (autoApply) now else null,
                    diffJson = json.encodeToString(TimetableDiff.serializer(), diff),
                    snapshotJson = json.encodeToString(TimetableState.serializer(), currentQnu),
                    addedCount = diff.addedCount,
                    changedCount = diff.changedCount,
                    removedCount = diff.removedCount,
                    conflictCount = conflictCount,
                ),
            )
            if (autoApply) {
                writeQnuState(semester.id, applied)
            }
            ensurePeriodScheme(semester.id, candidate)
            recordSyncRun(
                syncRunId, semester.id, now,
                if (autoApply) SyncRunStatus.SUCCESS_APPLIED else SyncRunStatus.SUCCESS_PENDING,
                changeSetId = changeSetId,
            )
        }
        return if (autoApply) {
            ImportOutcome.Applied(syncRunId, changeSetId, diff)
        } else {
            ImportOutcome.Pending(syncRunId, changeSetId, diff)
        }
    }

    suspend fun changeSet(id: String): ChangeSetRecord? =
        syncDao.changeSet(id)?.let(::toRecord)

    suspend fun changeSets(semesterId: String): List<ChangeSetRecord> =
        syncDao.changeSets(semesterId).map(::toRecord)

    suspend fun pendingChangeSets(semesterId: String): List<ChangeSetRecord> =
        syncDao.changeSetsByStatus(semesterId, ChangeSetStatus.PENDING.name).map(::toRecord)

    /** 应用变更集：PENDING 或 IGNORED（用户回头再确认）都允许。 */
    suspend fun applyChangeSet(id: String, now: Long = System.currentTimeMillis()) {
        val record = requireChangeSet(id)
        check(record.status == ChangeSetStatus.PENDING || record.status == ChangeSetStatus.IGNORED) {
            "变更集 ${record.status} 状态下不能应用"
        }
        db.withTransaction {
            val currentQnu = state(record.semesterId).filterBySource(SourceType.QNU_EDU)
            writeQnuState(record.semesterId, TimetableDiffApplier.apply(currentQnu, record.diff))
            syncDao.updateChangeSetStatus(id, ChangeSetStatus.APPLIED.name, now)
        }
    }

    /** 忽略变更集：课表不动，之后仍可从记录里重新应用（可恢复）。 */
    suspend fun ignoreChangeSet(id: String, now: Long = System.currentTimeMillis()) {
        val record = requireChangeSet(id)
        check(record.status == ChangeSetStatus.PENDING) { "变更集 ${record.status} 状态下不能忽略" }
        syncDao.updateChangeSetStatus(id, ChangeSetStatus.IGNORED.name, now)
    }

    /** 恢复已应用的变更集：用导入前快照整体还原教务来源数据。 */
    suspend fun revertChangeSet(id: String, now: Long = System.currentTimeMillis()) {
        val record = requireChangeSet(id)
        check(record.status == ChangeSetStatus.APPLIED) { "变更集 ${record.status} 状态下不能恢复" }
        db.withTransaction {
            writeQnuState(record.semesterId, record.snapshot)
            syncDao.updateChangeSetStatus(id, ChangeSetStatus.REVERTED.name, now)
        }
    }

    suspend fun syncRuns(semesterId: String): List<SyncRunEntity> = syncDao.syncRuns(semesterId)

    suspend fun settings(): UserSettings =
        settingsDao.settings()?.let(EntityMappers::toDomain) ?: UserSettings()

    suspend fun updateSettings(settings: UserSettings) =
        settingsDao.upsertSettings(EntityMappers.toEntity(settings))

    /** 保存单门课程及其一条安排，保证改节次时旧安排不会残留。 */
    suspend fun updateCourseAndMeeting(
        course: Course,
        previousMeeting: CourseMeeting?,
        meeting: CourseMeeting?,
        location: ScheduleLocation? = null,
    ) {
        db.withTransaction {
            timetableDao.upsertCourse(EntityMappers.toEntity(course))
            if (previousMeeting != null && (meeting == null || previousMeeting.id != meeting.id)) {
                timetableDao.deleteMeeting(previousMeeting.id)
                timetableDao.deletePlaceBinding(previousMeeting.id)
            }
            if (meeting != null) {
                timetableDao.upsertMeeting(EntityMappers.toEntity(meeting, course.semesterId))
                if (location != null) savePlaceBinding(course.semesterId, meeting.id, location)
            }
        }
    }

    /** 删除课程和所有关联安排。 */
    suspend fun deleteCourse(courseId: String) {
        db.withTransaction {
            timetableDao.clearActivitySuppressions("course-coverage:$courseId")
            timetableDao.deleteMeetingsByCourse(courseId)
            timetableDao.deleteCourse(courseId)
        }
    }

    /** 删除指定上课安排；若某门课已无安排则一并删除该课程。 */
    suspend fun deleteMeetings(semesterId: String, meetingIds: List<String>) {
        if (meetingIds.isEmpty()) return
        val idSet = meetingIds.toSet()
        db.withTransaction {
            val current = state(semesterId)
            val removed = current.meetings.filter { it.id in idSet }
            if (removed.isEmpty()) return@withTransaction
            removed.forEach { timetableDao.deleteMeeting(it.id) }
            val remaining = current.meetings.filter { it.id !in idSet }
            removed.map { it.courseId }.toSet().forEach { courseId ->
                if (remaining.none { it.courseId == courseId }) {
                    timetableDao.deleteCourse(courseId)
                }
            }
        }
    }

    suspend fun activities(semesterId: String): List<ScheduleActivity> =
        timetableDao.activities(semesterId).map(EntityMappers::toDomain)

    suspend fun upsertActivity(activity: ScheduleActivity) =
        timetableDao.upsertActivity(EntityMappers.toEntity(activity))

    suspend fun saveActivityWithCoverage(activity: ScheduleActivity, replaceMeetingIds: List<String>) = db.withTransaction {
        val semester = requireNotNull(semester(activity.semesterId))
        val periods = (periodScheme(semester.id) ?: PeriodSchemeFactory.forSemester(semester.id)).periods
        val unhidden = state(semester.id).copy(suppressedOccurrences = emptyList())
        val replacing = replaceMeetingIds.toSet()
        val occurrences = OccurrenceExpander.effective(semester, unhidden, AcademicCalendar(academicDays(semester.id)))
        val next = occurrences.filter { occurrence ->
            occurrence.isActive && occurrence.meetingId in replacing &&
                activity.effectiveSessions().any { it.overlaps(occurrence, periods) }
        }.map { occurrence ->
            val key = OccurrenceExpander.key(occurrence)
            ActivitySuppressionEntity("${activity.id}:$key", semester.id, activity.id, key)
        }
        upsertActivity(activity)
        timetableDao.clearActivitySuppressions(activity.id)
        if (next.isNotEmpty()) timetableDao.upsertSuppressions(next)
    }

    /** 保存一次课程编辑时，同一课程的其他上课安排也同步共享字段。 */
    suspend fun updateCourseAndMeetings(
        course: Course,
        previousMeeting: CourseMeeting?,
        meeting: CourseMeeting?,
        location: ScheduleLocation? = null,
    ) {
        db.withTransaction {
            timetableDao.upsertCourse(EntityMappers.toEntity(course))
            val current = state(course.semesterId).meetings.filter { it.courseId == course.id }
            current.forEach { existing ->
                val next = if (previousMeeting != null && existing.id == previousMeeting.id && meeting != null) meeting
                else existing.copy(
                    teacher = meeting?.teacher,
                    room = meeting?.room,
                    weekRule = meeting?.weekRule ?: existing.weekRule,
                    overrideMask = existing.overrideMask + setOf(MeetingField.TEACHER, MeetingField.ROOM, MeetingField.WEEKS),
                )
                if (next.id != existing.id) {
                    timetableDao.deleteMeeting(existing.id)
                    timetableDao.deletePlaceBinding(existing.id)
                }
                timetableDao.upsertMeeting(EntityMappers.toEntity(next, course.semesterId))
                if (location != null) savePlaceBinding(course.semesterId, next.id, location)
            }
            if (current.isEmpty() && meeting != null) timetableDao.upsertMeeting(EntityMappers.toEntity(meeting, course.semesterId))
        }
    }

    /** Save all selected weekdays atomically. Coverage hides only overlapping occurrences
     * and can be undone from the existing hidden-course list or by moving/deleting this course. */
    suspend fun replaceCourseMeetings(
        course: Course,
        meetings: List<CourseMeeting>,
        location: ScheduleLocation? = null,
        overwriteConflicts: Boolean = false,
        locations: Map<String, ScheduleLocation> = emptyMap(),
    ) = db.withTransaction {
        require(meetings.isNotEmpty()) { "请至少选择一节课" }
        require(meetings.all { it.courseId == course.id })
        val semester = requireNotNull(semester(course.semesterId))
        val coverageOwner = "course-coverage:${course.id}"
        val previousCoverage = timetableDao.suppressions(course.semesterId)
            .filter { it.activityId == coverageOwner }.map { it.occurrenceKey }.toSet()
        timetableDao.clearActivitySuppressions(coverageOwner)
        val before = state(course.semesterId)
        val calendar = AcademicCalendar(academicDays(course.semesterId))
        val replacement = TimetableState(
            courses = listOf(course), meetings = meetings,
            exceptions = before.exceptions.filter { it.courseId == course.id },
        )
        val visibleOverlaps = OccurrenceExpander.replacementTargets(semester, before, replacement, calendar)
        check(overwriteConflicts || visibleOverlaps.filter { it.isActive }.all { OccurrenceExpander.key(it) in previousCoverage }) { "课程安排已变化，请重新检查冲突" }
        // Confirmed course replacement also owns dates currently hidden by an activity,
        // so removing that activity cannot resurrect the replaced class underneath it.
        val overlapping = if (overwriteConflicts) {
            OccurrenceExpander.replacementTargets(semester, before.copy(suppressedOccurrences = emptyList()), replacement, calendar)
        } else {
            OccurrenceExpander.replacementTargets(semester, before.copy(suppressedOccurrences = emptyList()), replacement, calendar)
                .filter { OccurrenceExpander.key(it) in previousCoverage || it in visibleOverlaps }
        }
        val oldBindings = placeBindings(course.semesterId)
        timetableDao.upsertCourse(EntityMappers.toEntity(course))
        before.meetings.filter { it.courseId == course.id }.forEach {
            timetableDao.deleteMeeting(it.id)
            timetableDao.deletePlaceBinding(it.id)
        }
        meetings.distinctBy { it.id }.forEach { meeting ->
            timetableDao.upsertMeeting(EntityMappers.toEntity(meeting, course.semesterId))
            val binding = locations[meeting.id] ?: location ?: oldBindings[meeting.id]
            if (binding != null) savePlaceBinding(course.semesterId, meeting.id, binding)
        }
        if (overlapping.isNotEmpty()) {
            timetableDao.upsertSuppressions(overlapping.map {
                val key = OccurrenceExpander.key(it)
                ActivitySuppressionEntity("$coverageOwner:$key", course.semesterId, coverageOwner, key)
            })
        }
    }

    suspend fun deleteActivity(id: String, restoreCourses: Boolean = true) = db.withTransaction {
        if (id.startsWith("calendar-event:")) {
            timetableDao.hideActivity(id)
            return@withTransaction
        }
        if (restoreCourses) timetableDao.clearActivitySuppressions(id) else timetableDao.detachActivitySuppressions(id)
        timetableDao.deleteActivity(id)
    }

    suspend fun hideCalendarActivity(id: String) {
        require(id.startsWith("calendar-event:")) { "只有校历活动可以隐藏" }
        timetableDao.hideActivity(id)
    }

    suspend fun hiddenCourses(semesterId: String): List<ActivitySuppressionEntity> = timetableDao.suppressions(semesterId)

    suspend fun restoreHiddenCourse(id: String) = timetableDao.restoreSuppression(id)

    suspend fun placeBindings(semesterId: String): Map<String, ScheduleLocation> = timetableDao.placeBindings(semesterId)
        .associate { it.ownerId to json.decodeFromString(ScheduleLocation.serializer(), it.locationJson) }

    suspend fun savePlaceBinding(semesterId: String, ownerId: String, location: ScheduleLocation) =
        timetableDao.upsertPlaceBinding(SchedulePlaceBindingEntity(ownerId, semesterId, json.encodeToString(ScheduleLocation.serializer(), location)))

    suspend fun academicDays(semesterId: String): List<AcademicDay> =
        timetableDao.academicDays(semesterId).map(EntityMappers::toDomain)

    suspend fun replaceAcademicDays(semesterId: String, days: List<AcademicDay>) {
        db.withTransaction {
            timetableDao.deleteAcademicDays(semesterId)
            if (days.isNotEmpty()) {
                timetableDao.insertAcademicDays(
                    days.map { EntityMappers.toEntity(it.copy(semesterId = semesterId)) },
                )
            }
        }
    }

    suspend fun saveCalendar(semester: Semester, days: List<AcademicDay>) = db.withTransaction {
        upsertSemester(semester)
        replaceAcademicDays(semester.id, days)
    }

    suspend fun saveCalendarActivities(semesterId: String, events: List<ScheduleActivity>) = db.withTransaction {
        val ids=events.map { it.id }.toSet()
        timetableDao.allActivities(semesterId).map(EntityMappers::toDomain).filter { it.id.startsWith("calendar-event:") && it.id !in ids }
            .forEach { timetableDao.upsertActivity(EntityMappers.toEntity(it.copy(hiddenFromSchedule = true))) }
        events.forEach { upsertActivity(it) }
    }

    private suspend fun requireChangeSet(id: String): ChangeSetRecord =
        checkNotNull(changeSet(id)) { "变更集不存在: $id" }

    /** 用 [newState] 中的 QNU 行整体替换该学期的 QNU 来源数据；手动数据不受影响。 */
    private suspend fun writeQnuState(semesterId: String, newState: TimetableState) {
        val qnu = newState.filterBySource(SourceType.QNU_EDU)
        val source = SourceType.QNU_EDU.name
        timetableDao.deleteExceptionsBySource(semesterId, source)
        timetableDao.deleteMeetingsBySource(semesterId, source)
        timetableDao.deleteCoursesBySource(semesterId, source)
        if (qnu.courses.isNotEmpty()) {
            timetableDao.insertCourses(qnu.courses.map(EntityMappers::toEntity))
        }
        if (qnu.meetings.isNotEmpty()) {
            timetableDao.insertMeetings(qnu.meetings.map { EntityMappers.toEntity(it, semesterId) })
        }
        if (qnu.exceptions.isNotEmpty()) {
            timetableDao.insertExceptions(qnu.exceptions.map { EntityMappers.toEntity(it, semesterId) })
        }
    }

    /** 用户已保存的作息不被后续导入覆盖。 */
    private suspend fun ensurePeriodScheme(semesterId: String, candidate: ImportedTimetable) {
        if (timetableDao.periodScheme(semesterId) != null) return
        timetableDao.upsertPeriodScheme(
            EntityMappers.toEntity(PeriodSchemeFactory.forSemester(semesterId, candidate.periods)),
        )
    }

    private suspend fun recordSyncRun(
        id: String,
        semesterId: String,
        now: Long,
        status: SyncRunStatus,
        errorMessage: String? = null,
        changeSetId: String? = null,
    ) {
        syncDao.insertSyncRun(
            SyncRunEntity(
                id = id,
                semesterId = semesterId,
                source = SourceType.QNU_EDU.name,
                startedAt = now,
                finishedAt = now,
                status = status.name,
                errorMessage = errorMessage,
                changeSetId = changeSetId,
            ),
        )
    }

    private fun bindSemester(state: TimetableState, semesterId: String) =
        state.copy(courses = state.courses.map { it.copy(semesterId = semesterId) })

    private fun errorSummary(candidate: ImportedTimetable): String =
        candidate.diagnostics
            .filter { it.severity == cn.qztc.timetable.core.domain.imports.DiagnosticSeverity.ERROR }
            .joinToString("; ") { "${it.code}: ${it.message}" }

    private fun toRecord(entity: ChangeSetEntity) = ChangeSetRecord(
        id = entity.id,
        semesterId = entity.semesterId,
        syncRunId = entity.syncRunId,
        status = ChangeSetStatus.valueOf(entity.status),
        createdAt = entity.createdAt,
        resolvedAt = entity.resolvedAt,
        diff = json.decodeFromString(TimetableDiff.serializer(), entity.diffJson),
        snapshot = json.decodeFromString(TimetableState.serializer(), entity.snapshotJson),
        addedCount = entity.addedCount,
        changedCount = entity.changedCount,
        removedCount = entity.removedCount,
        conflictCount = entity.conflictCount,
    )
}
