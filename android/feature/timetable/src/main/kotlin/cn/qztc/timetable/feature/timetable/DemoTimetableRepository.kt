package cn.qztc.timetable.feature.timetable

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import cn.qztc.timetable.core.domain.calendar.AcademicCalendar
import cn.qztc.timetable.core.domain.calendar.AcademicDay
import cn.qztc.timetable.core.domain.calendar.SemesterCalendar
import cn.qztc.timetable.core.domain.model.Course
import cn.qztc.timetable.core.domain.model.CourseMeeting
import cn.qztc.timetable.core.domain.model.OccurrenceState
import cn.qztc.timetable.core.domain.model.Period
import cn.qztc.timetable.core.domain.model.QnuPeriodDefaults
import cn.qztc.timetable.core.domain.model.ScheduleActivity
import cn.qztc.timetable.core.domain.model.Semester
import cn.qztc.timetable.core.domain.model.SourceType
import cn.qztc.timetable.core.domain.model.WeekRule
import cn.qztc.timetable.core.domain.schedule.ActivityWeekLayout
import cn.qztc.timetable.core.domain.schedule.OccurrenceExpander
import cn.qztc.timetable.core.domain.schedule.TimetableState
import cn.qztc.timetable.core.ui.theme.CoursePalette
import cn.qztc.timetable.core.ui.theme.CourseColor
import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.LocalTime

internal data class DisplayPeriodSlot(
    val label: String,
    val actualStart: Int,
    val actualEnd: Int = actualStart,
)

internal val timetableDisplayPeriodSlots = listOf(
    DisplayPeriodSlot("第1节", 1),
    DisplayPeriodSlot("第2节", 2),
    DisplayPeriodSlot("第3节", 3),
    DisplayPeriodSlot("第4节", 4),
    DisplayPeriodSlot("午间第1节", 5),
    DisplayPeriodSlot("午间第2节", 6),
    DisplayPeriodSlot("第5节", 7),
    DisplayPeriodSlot("第6节", 8),
    DisplayPeriodSlot("第7节", 9),
    DisplayPeriodSlot("第8节", 10),
    DisplayPeriodSlot("第9节", 11),
    DisplayPeriodSlot("第10节", 12),
    DisplayPeriodSlot("第11节", 13),
)

internal fun displaySlotIndexForActual(actualPeriod: Int): Int =
    timetableDisplayPeriodSlots.indexOfFirst { actualPeriod in it.actualStart..it.actualEnd }
        .coerceAtLeast(0)

internal fun displayPeriodRangeLabel(actualStart: Int, actualEnd: Int = actualStart): String {
    val first = timetableDisplayPeriodSlots[displaySlotIndexForActual(actualStart)]
    val last = timetableDisplayPeriodSlots[displaySlotIndexForActual(actualEnd)]
    if (first == last) return first.label
    if (first.label.startsWith("午间") && last.label.startsWith("午间")) return "午间第1-2节"
    val firstNo = first.label.removePrefix("第").removeSuffix("节")
    val lastNo = last.label.removePrefix("第").removeSuffix("节")
    return "第${firstNo}-${lastNo}节"
}
internal fun scheduleDateLabel(date: LocalDate): String =
    if (date.year == LocalDate.now().year) {
        "${date.monthValue}.${date.dayOfMonth}"
    } else {
        "${(date.year % 100).toString().padStart(2, '0')}.${date.monthValue}.${date.dayOfMonth}"
    }

data class DemoOccurrence(
    val course: Course,
    val paletteIndex: Int,
    val weekday: Int,
    val periodStart: Int,
    val periodEnd: Int,
    val room: String,
    val teacher: String,
    val cancelled: Boolean = false,
    val moved: Boolean = false,
    val ghost: Boolean = false,
    val changed: Boolean = false,
    /** 与教务页一致的显示节次；网格不再压缩或复用午间、下午的坐标。 */
    val layoutPeriodStart: Int = periodStart,
    val layoutPeriodEnd: Int = periodEnd,
    val meetingId: String? = null,
    val weeks: List<Int> = emptyList(),
    val location: cn.qztc.timetable.core.domain.model.ScheduleLocation = cn.qztc.timetable.core.domain.model.ScheduleLocation(name = room),
) {
    val id: String = "${course.id}-${meetingId ?: "exception"}-$weekday-$periodStart-$periodEnd"
}

/** 预设色仍以索引保存；超出预设范围的值按 ARGB 自定义色处理，透明度与色板一致。 */
internal fun DemoOccurrence.visualColor(dark: Boolean): CourseColor {
    val stored = course.color ?: paletteIndex
    return visualCourseColor(stored)
}

internal fun visualCourseColor(stored: Int): CourseColor {
    if (stored in CoursePalette.indices) return CoursePalette[stored]
    val custom = Color(stored).copy(alpha = 1f)
    val preset = CoursePalette.first()
    val luminance = custom.red * .2126f + custom.green * .7152f + custom.blue * .0722f
    return CourseColor(
        bar = custom,
        lightBg = androidx.compose.ui.graphics.lerp(Color.White, custom, 0.22f).copy(alpha = preset.lightBg.alpha),
        lightTx = Color(0xFF17352F),
        darkBg = custom.copy(alpha = preset.darkBg.alpha),
        darkTx = if (luminance > .58f) Color(0xFF17352F) else Color(0xFF9FE3CD),
    )
}

data class DemoChange(
    val id: String,
    val type: String,
    val courseId: String,
    val before: String,
    val after: String,
    val note: String,
)

data class ImportedSchedule(
    val semester: Semester,
    val periods: List<Period>,
    val state: TimetableState,
    val activities: List<ScheduleActivity> = emptyList(),
    val academicDays: List<AcademicDay> = emptyList(),
    val placeBindings: Map<String, cn.qztc.timetable.core.domain.model.ScheduleLocation> = emptyMap(),
)

enum class TodayScenario(val label: String, val time: LocalTime, val weekday: Int) {
    MORNING("上课前 07:40", LocalTime.of(7, 40), 5),
    IN_CLASS("上课中 09:30", LocalTime.of(9, 30), 5),
    BREAK("课间 10:05", LocalTime.of(10, 5), 5),
    EVENING("傍晚 18:20", LocalTime.of(18, 20), 5),
    DONE("全部结束 21:30", LocalTime.of(21, 30), 5),
    SUNDAY("无课日（周日）", LocalTime.of(10, 5), 7),
}

@Stable
class DemoScheduleState {
    /** 未导入课表时保持完全空白，不展示任何内置示例变化。 */
    val pending = mutableStateListOf<String>()
    val applied = mutableStateListOf<String>()
    val ignored = mutableStateListOf<String>()
    var scenario by mutableStateOf(TodayScenario.BREAK)
    var selectedDay by mutableStateOf(LocalDate.now().dayOfWeek.value)
    var syncPhase by mutableStateOf<String?>(null)
    var lastSync by mutableStateOf("尚未导入课表")
    var syncResult by mutableStateOf("尚未导入课表")
    var importedSchedule by mutableStateOf<ImportedSchedule?>(null)
    private var occurrenceLookupsSchedule: ImportedSchedule? = null
    private var coursePaletteById: Map<String, Int> = emptyMap()
    private var meetingsById: Map<String, CourseMeeting> = emptyMap()

    val currentWeek: Int
        get() = importedSchedule?.let { schedule ->
            SemesterCalendar.weekOf(schedule.semester, LocalDate.now())
                ?: firstScheduledWeek(schedule.state)
        } ?: 1

    val currentSemesterWeek: Int?
        get() = importedSchedule?.let { schedule ->
            SemesterCalendar.weekOf(schedule.semester, LocalDate.now())
        } ?: null

    /**
     * The day initially selected by a page. Old imports have no meaningful "today",
     * so open their first scheduled weekday instead of pinning the UI to the device date.
     */
    val referenceWeekday: Int
        get() = importedSchedule?.let { schedule ->
            if (isCurrentDateInImportedSemester) LocalDate.now().dayOfWeek.value
            else firstScheduledWeekday(schedule.state, currentWeek)
        } ?: LocalDate.now().dayOfWeek.value

    val todayWeekday: Int
        get() = referenceWeekday

    /** 导入旧学期时，当前日期不属于该学期；界面须以该学期日期展示而不是设备日期。 */
    val isCurrentDateInImportedSemester: Boolean
        get() = importedSchedule?.let { schedule ->
            val today = LocalDate.now()
            val first = SemesterCalendar.dateOf(schedule.semester, 1, 1)
            val last = SemesterCalendar.dateOf(schedule.semester, schedule.semester.totalWeeks, 7)
            today in first..last
        } ?: true

    fun academicCalendar(): AcademicCalendar =
        AcademicCalendar(importedSchedule?.academicDays.orEmpty())

    fun academicDayOn(week: Int, weekday: Int): AcademicDay? =
        academicCalendar().dayOn(dateFor(week, weekday))

    fun academicCaption(week: Int, weekday: Int): String? {
        val schedule = importedSchedule ?: return null
        return academicCalendar().caption(schedule.semester, dateFor(week, weekday))
    }

    fun academicHeaderMark(week: Int, weekday: Int): String? =
        academicCalendar().headerMark(dateFor(week, weekday))

    /** 显示层直接使用教务节次，避免午间、下午和晚间发生坐标或时间错位。 */
    fun periodFor(periodNo: Int): Period = actualPeriodFor(periodNo)

    fun actualPeriodFor(no: Int): Period =
        importedSchedule?.periods?.firstOrNull { it.no == no }
            ?: DemoTimetableRepository.periods.firstOrNull { it.no == no }
            ?: DemoTimetableRepository.periodFor(no)

    fun dateFor(week: Int, weekday: Int): LocalDate =
        importedSchedule?.let { SemesterCalendar.dateOf(it.semester, week, weekday) }
            ?: LocalDate.now()
                .minusDays((LocalDate.now().dayOfWeek.value - 1).toLong())
                .plusDays(((week - 1) * 7L) + weekday - 1L)

    fun occurrences(
        week: Int,
        appliedChanges: Set<String> = emptySet(),
        includeMovedGhost: Boolean = false,
        respectAcademicCalendar: Boolean = true,
        includeHolidayCourses: Boolean = false,
        includeScheduleChanges: Boolean = true,
    ): List<DemoOccurrence> = importedSchedule?.let { schedule ->
        val courses = schedule.state.courses.associateBy { it.id }
        val (importedPalette, meetings) = occurrenceLookups(schedule)
        val state = if (includeScheduleChanges) schedule.state else schedule.state.copy(
            exceptions = schedule.state.exceptions.filterNot { exception ->
                exception.original?.week == week || exception.replacement?.week == week
            },
        )
        OccurrenceExpander.expandVisibleWeek(
            schedule.semester,
            state,
            week,
            if (respectAcademicCalendar) academicCalendar() else cn.qztc.timetable.core.domain.calendar.AcademicCalendar.EMPTY,
            includeHolidayCourses = includeHolidayCourses,
        )
            .mapNotNull { occurrence ->
                val course = courses[occurrence.courseId] ?: return@mapNotNull null
                DemoOccurrence(
                    course = course,
                    paletteIndex = importedPalette[course.id] ?: 0,
                    weekday = occurrence.weekday,
                    periodStart = occurrence.periodStart,
                    periodEnd = occurrence.periodEnd,
                    room = occurrence.room.orEmpty(),
                    teacher = occurrence.teacher.orEmpty(),
                    cancelled = occurrence.state == OccurrenceState.CANCELLED,
                    moved = occurrence.state == OccurrenceState.MOVED_IN,
                    changed = occurrence.state in setOf(OccurrenceState.MOVED_IN, OccurrenceState.MAKEUP, OccurrenceState.SUBSTITUTED),
                    layoutPeriodStart = occurrence.periodStart,
                    layoutPeriodEnd = occurrence.periodEnd,
                    meetingId = occurrence.meetingId,
                    weeks = occurrence.meetingId?.let { meetings[it]?.weekRule?.weeks }.orEmpty(),
                    location = schedule.placeBindings[occurrence.meetingId] ?: cn.qztc.timetable.core.domain.model.ScheduleLocation(name = occurrence.room.orEmpty()),
                )
            }
            .sortedWith(compareBy(DemoOccurrence::weekday, DemoOccurrence::periodStart))
    } ?: emptyList()

    fun hasScheduleChange(week: Int): Boolean = importedSchedule?.state?.exceptions.orEmpty().any { exception ->
        exception.original?.week == week || exception.replacement?.week == week
    }

    fun activities(): List<ScheduleActivity> = importedSchedule?.activities.orEmpty()

    fun activitiesOn(week: Int, weekday: Int): List<ScheduleActivity> {
        val date = dateFor(week, weekday)
        return activities().filter { it.appliesOn(week, weekday, date) }
    }

    fun activityBlocks(
        week: Int,
        visibleDays: List<Int>,
        includeLunchPeriods: Boolean = true,
    ) = ActivityWeekLayout.blocksForWeek(
        activities = activities(),
        week = week,
        visibleDays = visibleDays,
        dateOf = { weekday -> dateFor(week, weekday) },
        includeLunchPeriods = includeLunchPeriods,
        periods = importedSchedule?.periods ?: DemoTimetableRepository.periods,
    )

    fun loadImported(schedule: ImportedSchedule) {
        importedSchedule = schedule
        CampusScheduleOverlay.current = schedule
        selectedDay = referenceWeekday
        pending.clear()
        applied.clear()
        ignored.clear()
        syncResult = "已加载本机课表"
        lastSync = "已导入 · ${schedule.state.courses.size} 门课程"
        occurrenceLookups(schedule)
    }

    private fun occurrenceLookups(schedule: ImportedSchedule): Pair<Map<String, Int>, Map<String, CourseMeeting>> {
        if (occurrenceLookupsSchedule === schedule) {
            return coursePaletteById to meetingsById
        }
        coursePaletteById = schedule.state.courses
            .sortedBy { it.id }
            .mapIndexed { index, course -> course.id to automaticCourseColorToken(index) }
            .toMap()
        meetingsById = schedule.state.meetings.associateBy { it.id }
        occurrenceLookupsSchedule = schedule
        return coursePaletteById to meetingsById
    }

    fun displayPeriodLabel(actualStart: Int, actualEnd: Int = actualStart): String =
        displayPeriodRangeLabel(actualStart, actualEnd)

    private fun firstScheduledWeek(state: TimetableState): Int =
        state.meetings
            .flatMap { it.weekRule.weeks }
            .minOrNull()
            ?: 1

    private fun firstScheduledWeekday(state: TimetableState, week: Int): Int =
        state.meetings
            .filter { week in it.weekRule }
            .minOfOrNull { it.weekday }
            ?: state.meetings.minOfOrNull { it.weekday }
            ?: 1

    fun apply(ids: Collection<String>) {
        ids.forEach { id ->
            pending.remove(id)
            ignored.remove(id)
            if (id !in applied) applied += id
        }
    }

    fun ignore(ids: Collection<String>) {
        ids.forEach { id ->
            pending.remove(id)
            applied.remove(id)
            if (id !in ignored) ignored += id
        }
    }

    fun restorePending(ids: Collection<String>) {
        ids.forEach { id ->
            applied.remove(id)
            ignored.remove(id)
            if (id !in pending) pending += id
        }
    }

    fun finishSync(foundChanges: Boolean = true) {
        syncPhase = null
        syncResult = if (foundChanges) "刚刚 · 发现 ${pending.size} 项待处理变化" else "刚刚 · 课表没有变化"
        lastSync = syncResult
    }
}

/** Runtime period defaults only; sample courses live in the test source set. */
object DemoTimetableRepository {
    val periods = QnuPeriodDefaults.periods()
    fun periodFor(periodNo: Int): Period = periods.first { it.no == periodNo }
}
