package cn.qztc.timetable.core.data.db

import cn.qztc.timetable.core.domain.calendar.AcademicDay
import cn.qztc.timetable.core.domain.calendar.AcademicDayKind
import cn.qztc.timetable.core.domain.model.ActivityKind
import cn.qztc.timetable.core.domain.model.ActivitySession
import cn.qztc.timetable.core.domain.model.Course
import cn.qztc.timetable.core.domain.model.CourseMeeting
import cn.qztc.timetable.core.domain.model.ExceptionType
import cn.qztc.timetable.core.domain.model.HomeView
import cn.qztc.timetable.core.domain.model.MeetingField
import cn.qztc.timetable.core.domain.model.Period
import cn.qztc.timetable.core.domain.model.PeriodScheme
import cn.qztc.timetable.core.domain.model.ScheduleActivity
import cn.qztc.timetable.core.domain.model.ScheduleException
import cn.qztc.timetable.core.domain.model.Semester
import cn.qztc.timetable.core.domain.model.SlotRef
import cn.qztc.timetable.core.domain.model.SourceType
import cn.qztc.timetable.core.domain.model.Term
import cn.qztc.timetable.core.domain.model.UserSettings
import cn.qztc.timetable.core.domain.model.WeekRule
import java.time.LocalDate
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

internal object EntityMappers {

    private val json = Json { ignoreUnknownKeys = true }

    fun toEntity(semester: Semester) = SemesterEntity(
        id = semester.id,
        name = semester.name,
        schoolYear = semester.schoolYear,
        term = semester.term.name,
        startDate = semester.startDate.toString(),
        totalWeeks = semester.totalWeeks,
        archived = semester.archived,
    )

    fun toDomain(entity: SemesterEntity) = Semester(
        id = entity.id,
        name = entity.name,
        schoolYear = entity.schoolYear,
        term = Term.valueOf(entity.term),
        startDate = LocalDate.parse(entity.startDate),
        totalWeeks = entity.totalWeeks,
        archived = entity.archived,
    )

    fun toEntity(course: Course) = CourseEntity(
        id = course.id,
        semesterId = course.semesterId,
        sourceType = course.sourceType.name,
        sourceKey = course.sourceKey,
        code = course.code,
        name = course.name,
        sequence = course.sequence,
        color = course.color,
        note = course.note,
        nature = course.nature,
        credits = course.credits,
        selectionAttribute = course.selectionAttribute,
        assessmentMethod = course.assessmentMethod,
    )

    fun toDomain(entity: CourseEntity) = Course(
        id = entity.id,
        semesterId = entity.semesterId,
        sourceType = SourceType.valueOf(entity.sourceType),
        sourceKey = entity.sourceKey,
        code = entity.code,
        name = entity.name,
        sequence = entity.sequence,
        color = entity.color,
        note = entity.note,
        nature = entity.nature,
        credits = entity.credits,
        selectionAttribute = entity.selectionAttribute,
        assessmentMethod = entity.assessmentMethod,
    )

    fun toEntity(meeting: CourseMeeting, semesterId: String) = MeetingEntity(
        id = meeting.id,
        semesterId = semesterId,
        courseId = meeting.courseId,
        weekday = meeting.weekday,
        periodStart = meeting.periodStart,
        periodEnd = meeting.periodEnd,
        weeksCsv = meeting.weekRule.weeks.joinToString(","),
        weekRaw = meeting.weekRule.rawText,
        teacher = meeting.teacher,
        room = meeting.room,
        sourceType = meeting.sourceType.name,
        overrideMaskCsv = meeting.overrideMask.joinToString(",") { it.name },
    )

    fun toDomain(entity: MeetingEntity) = CourseMeeting(
        id = entity.id,
        courseId = entity.courseId,
        weekday = entity.weekday,
        periodStart = entity.periodStart,
        periodEnd = entity.periodEnd,
        weekRule = WeekRule(entity.weeksCsv.split(',').map { it.toInt() }, entity.weekRaw),
        teacher = entity.teacher,
        room = entity.room,
        sourceType = SourceType.valueOf(entity.sourceType),
        overrideMask = entity.overrideMaskCsv.split(',')
            .filter { it.isNotEmpty() }
            .map { MeetingField.valueOf(it) }
            .toSet(),
    )

    fun toEntity(exception: ScheduleException, semesterId: String) = ScheduleExceptionEntity(
        id = exception.id,
        semesterId = semesterId,
        courseId = exception.courseId,
        type = exception.type.name,
        originalJson = exception.original?.let { json.encodeToString(SlotRef.serializer(), it) },
        replacementJson = exception.replacement?.let { json.encodeToString(SlotRef.serializer(), it) },
        substituteTeacher = exception.substituteTeacher,
        note = exception.note,
        sourceType = exception.sourceType.name,
    )

    fun toDomain(entity: ScheduleExceptionEntity) = ScheduleException(
        id = entity.id,
        courseId = entity.courseId,
        type = ExceptionType.valueOf(entity.type),
        original = entity.originalJson?.let { json.decodeFromString(SlotRef.serializer(), it) },
        replacement = entity.replacementJson?.let { json.decodeFromString(SlotRef.serializer(), it) },
        substituteTeacher = entity.substituteTeacher,
        sourceType = SourceType.valueOf(entity.sourceType),
        note = entity.note,
    )

    fun toEntity(scheme: PeriodScheme) = PeriodSchemeEntity(
        semesterId = scheme.semesterId,
        id = scheme.id,
        name = scheme.name,
        editable = scheme.editable,
        periodsJson = json.encodeToString(ListSerializer(Period.serializer()), scheme.periods),
    )

    fun toDomain(entity: PeriodSchemeEntity) = PeriodScheme(
        id = entity.id,
        semesterId = entity.semesterId,
        name = entity.name,
        editable = entity.editable,
        periods = json.decodeFromString(ListSerializer(Period.serializer()), entity.periodsJson),
    )

    fun toEntity(settings: UserSettings) = UserSettingsEntity(
        homeView = settings.homeView.name,
        activeSemesterId = settings.activeSemesterId,
        syncEnabled = settings.syncEnabled,
        syncIntervalHours = settings.syncIntervalHours,
    )

    fun toDomain(entity: UserSettingsEntity) = UserSettings(
        homeView = HomeView.valueOf(entity.homeView),
        activeSemesterId = entity.activeSemesterId,
        syncEnabled = entity.syncEnabled,
        syncIntervalHours = entity.syncIntervalHours,
    )

    fun toEntity(activity: ScheduleActivity) = ActivityEntity(
        id = activity.id,
        semesterId = activity.semesterId,
        name = activity.name,
        note = activity.note,
        timeText = activity.timeText,
        kind = activity.kind.name,
        color = activity.color,
        weekday = activity.weekday,
        periodStart = activity.periodStart,
        periodEnd = activity.periodEnd,
        weeksCsv = activity.weeks.joinToString(","),
        startDate = activity.startDate?.toString(),
        endDate = activity.endDate?.toString(),
        sessionsJson = json.encodeToString(ListSerializer(ActivitySession.serializer()), activity.effectiveSessions()),
        hiddenFromSchedule = activity.hiddenFromSchedule,
        calendarTimetable = activity.calendarTimetable,
        memoTimeJson = activity.memoTime?.let { json.encodeToString(cn.qztc.timetable.core.domain.model.ActivityMemoTime.serializer(), it) },
    )

    fun toDomain(entity: ActivityEntity) = ScheduleActivity(
        id = entity.id,
        semesterId = entity.semesterId,
        name = entity.name,
        note = entity.note,
        timeText = entity.timeText,
        kind = ActivityKind.valueOf(entity.kind),
        color = entity.color,
        weekday = entity.weekday,
        periodStart = entity.periodStart,
        periodEnd = entity.periodEnd,
        weeks = entity.weeksCsv.split(',').mapNotNull { it.toIntOrNull() },
        startDate = entity.startDate?.takeIf { it.isNotBlank() }?.let(LocalDate::parse),
        endDate = entity.endDate?.takeIf { it.isNotBlank() }?.let(LocalDate::parse),
        sessions = json.decodeFromString(ListSerializer(ActivitySession.serializer()), entity.sessionsJson),
        hiddenFromSchedule = entity.hiddenFromSchedule,
        calendarTimetable = entity.calendarTimetable,
        memoTime = entity.memoTimeJson?.let { json.decodeFromString(cn.qztc.timetable.core.domain.model.ActivityMemoTime.serializer(), it) },
    )

    fun toEntity(day: AcademicDay) = AcademicDayEntity(
        id = day.id,
        semesterId = day.semesterId,
        date = day.date.toString(),
        kind = day.kind.name,
        name = day.name,
        sourceWeek = day.sourceWeek,
        sourceWeekday = day.sourceWeekday,
        useCurrentWeek = day.useCurrentWeek,
        useCurrentWeekday = day.useCurrentWeekday,
    )

    fun toDomain(entity: AcademicDayEntity) = AcademicDay(
        id = entity.id,
        semesterId = entity.semesterId,
        date = LocalDate.parse(entity.date),
        kind = AcademicDayKind.valueOf(entity.kind),
        name = entity.name,
        sourceWeek = entity.sourceWeek,
        sourceWeekday = entity.sourceWeekday,
        useCurrentWeek = entity.useCurrentWeek,
        useCurrentWeekday = entity.useCurrentWeekday,
    )
}
