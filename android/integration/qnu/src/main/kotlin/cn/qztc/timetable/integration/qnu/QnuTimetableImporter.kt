package cn.qztc.timetable.integration.qnu

import cn.qztc.timetable.core.domain.imports.ImportDiagnostic
import cn.qztc.timetable.core.domain.imports.ImportedPeriod
import cn.qztc.timetable.core.domain.imports.ImportedTimetable
import cn.qztc.timetable.core.domain.imports.SemesterHint
import cn.qztc.timetable.core.domain.model.Course
import cn.qztc.timetable.core.domain.model.CourseMeeting
import cn.qztc.timetable.core.domain.model.ExceptionType
import cn.qztc.timetable.core.domain.model.ScheduleException
import cn.qztc.timetable.core.domain.model.SlotRef
import cn.qztc.timetable.core.domain.model.SourceType
import cn.qztc.timetable.core.domain.model.Term
import cn.qztc.timetable.core.domain.model.WeekRuleText
import cn.qztc.timetable.core.domain.schedule.MeetingMerger
import cn.qztc.timetable.core.domain.schedule.TimetableState

/**
 * 把泉师页面解析结果映射为标准领域候选数据：
 * 网格记录变成课程与长期安排（连堂归并、周次解析），
 * 网格状态词与调停补明细统一映射为 ScheduleException，未知内容全部走诊断。
 */
object QnuTimetableImporter {

    private const val STATUS_MAKEUP = "补课"
    private const val STATUS_CANCEL = "停课"
    private val regularTypeSuffixes = listOf("学时", "讲课", "实验", "上机", "实践")

    fun import(bytes: ByteArray, semesterId: String = ""): ImportedTimetable {
        val page = QnuTimetableParser.parse(bytes)
        return map(page, semesterId)
    }

    fun importHtml(html: String, semesterId: String = ""): ImportedTimetable {
        val page = QnuTimetableParser.parseHtml(html)
        return map(page, semesterId)
    }

    fun importChangesHtml(html: String, semesterId: String = ""): ImportedTimetable =
        map(QnuTimetableParser.parseChangesHtml(html), semesterId)

    fun applyCourseProfiles(imported: ImportedTimetable, profiles: List<QnuCourseProfile>): ImportedTimetable {
        val courses = imported.state.courses.map { course ->
            val profile = QnuTimetableParser.matchCourseProfile(course.name, course.sequence, profiles, course.code)
            course.copy(
                name = cn.qztc.timetable.core.domain.schedule.ScheduleCourseNames.fullest(course.name, profile?.courseName) ?: course.name,
                nature = profile?.nature ?: course.nature,
                credits = profile?.credits ?: course.credits,
                selectionAttribute = profile?.selectionAttribute ?: course.selectionAttribute,
                assessmentMethod = profile?.assessmentMethod ?: course.assessmentMethod,
                code = course.code ?: profile?.courseCode,
            )
        }
        val incomplete=courses.count { it.credits.isNullOrBlank() || it.selectionAttribute.isNullOrBlank() || it.assessmentMethod.isNullOrBlank() }
        val diagnostics=imported.diagnostics + if(incomplete>0) listOf(ImportDiagnostic.warning(
            "COURSE_DETAILS_INCOMPLETE", "有 $incomplete 门课程的详情未完整读取，已有详情会保留",
        )) else emptyList()
        return imported.copy(state = imported.state.copy(courses = courses), diagnostics=diagnostics)
    }

    fun map(page: QnuParsedPage, semesterId: String = ""): ImportedTimetable {
        val diagnostics = page.diagnostics.toMutableList()
        val registry = CourseRegistry(semesterId, page)

        val meetings = mutableListOf<CourseMeeting>()
        val exceptions = linkedMapOf<String, ScheduleException>()

        for (entry in page.gridEntries) {
            val hasScopedCancellation = page.changeDetails.any { detail ->
                detail.typeText.contains(STATUS_CANCEL) && detail.original?.week != null &&
                    detail.courseName == entry.courseName && (detail.sequence == null || detail.sequence == entry.sequence)
            }
            mapGridEntry(entry, registry, meetings, exceptions, diagnostics, hasScopedCancellation)
        }
        for (detail in page.changeDetails) {
            mapChangeDetail(detail, registry, exceptions, diagnostics)
        }

        val state = TimetableState(
            courses = registry.referencedCourses(),
            meetings = MeetingMerger.mergeConsecutive(meetings),
            exceptions = mergeAdjacentExceptions(exceptions.values.toList()).map { exception ->
                exception.copy(courseName = registry.referencedCourses().firstOrNull { it.id == exception.courseId }?.name)
            },
        )

        return ImportedTimetable(
            semesterHint = semesterHint(page),
            periods = page.periods.map { ImportedPeriod(it.rowIndex, it.label, it.startText, it.endText) },
            state = state,
            diagnostics = diagnostics,
        )
    }

    private fun semesterHint(page: QnuParsedPage): SemesterHint? {
        if (page.yearText == null && page.termText == null) return null
        val term = when (page.termText) {
            "春" -> Term.SPRING
            "夏" -> Term.SUMMER
            "秋" -> Term.AUTUMN
            else -> null
        }
        return SemesterHint(
            year = page.yearText?.toIntOrNull(),
            term = term,
            termText = page.termText,
            raw = listOfNotNull(page.yearText, page.termText).joinToString(" "),
        )
    }

    private fun mapGridEntry(
        entry: QnuGridEntry,
        registry: CourseRegistry,
        meetings: MutableList<CourseMeeting>,
        exceptions: MutableMap<String, ScheduleException>,
        diagnostics: MutableList<ImportDiagnostic>,
        hasScopedCancellation: Boolean,
    ) {
        val position = "${entry.weekday}-${entry.periodRow}"
        val weekRule = WeekRuleText.parse(entry.weekText)
        if (weekRule == null) {
            diagnostics += ImportDiagnostic.error(
                "WEEK_TEXT_UNRECOGNIZED",
                "课程格 $position 《${entry.courseName}》周次无法识别: '${entry.weekText}'，该记录未导入",
            )
            return
        }

        val tokens = entry.statusTokens
        val unknown = tokens.filterNot { token ->
            token == STATUS_MAKEUP || token == STATUS_CANCEL || regularTypeSuffixes.any { token.contains(it) }
        }
        if (unknown.isNotEmpty()) {
            diagnostics += ImportDiagnostic.warning(
                "STATUS_UNRECOGNIZED",
                "课程格 $position 《${entry.courseName}》出现未知状态: $unknown",
            )
        }

        val courseId = registry.courseId(entry.courseName, entry.sequence)
        val isMakeup = STATUS_MAKEUP in tokens
        // Detailed cancellation dates override a semester cell's status label.
        val unscopedBadge = STATUS_CANCEL in tokens && weekRule.weeks.size > 1
        val isCancelled = STATUS_CANCEL in tokens && !hasScopedCancellation && !unscopedBadge
        if (unscopedBadge && !hasScopedCancellation) diagnostics += ImportDiagnostic.warning(
            "CANCEL_SCOPE_UNCONFIRMED",
            "《${entry.courseName}》显示停课，但教务系统未提供具体周次，未按整学期停课处理",
        )

        if (isMakeup) {
            // 补课：按周生成指定日期的新增课次，不形成长期安排。
            for (week in weekRule.weeks) {
                val slot = SlotRef(
                    week = week, weekday = entry.weekday,
                    periodStart = entry.periodRow, periodEnd = entry.periodRow,
                    room = entry.room, teacher = entry.teacher,
                )
                putException(exceptions, exception(courseId, ExceptionType.MAKEUP, replacement = slot))
                if (isCancelled) {
                    putException(exceptions, exception(courseId, ExceptionType.CANCEL, original = slot))
                }
            }
            return
        }

        meetings += CourseMeeting(
            id = CourseMeeting.stableId(courseId, entry.weekday, entry.periodRow, entry.periodRow),
            courseId = courseId,
            weekday = entry.weekday,
            periodStart = entry.periodRow,
            periodEnd = entry.periodRow,
            weekRule = weekRule,
            teacher = entry.teacher,
            room = entry.room,
            sourceType = SourceType.QNU_EDU,
        )
        if (isCancelled) {
            for (week in weekRule.weeks) {
                val slot = SlotRef(
                    week = week, weekday = entry.weekday,
                    periodStart = entry.periodRow, periodEnd = entry.periodRow,
                )
                putException(exceptions, exception(courseId, ExceptionType.CANCEL, original = slot))
            }
        }
    }

    private fun mapChangeDetail(
        detail: QnuChangeDetail,
        registry: CourseRegistry,
        exceptions: MutableMap<String, ScheduleException>,
        diagnostics: MutableList<ImportDiagnostic>,
    ) {
        val courseId = registry.courseIdForDetail(detail)
        val label = "《${detail.courseName ?: "?"}》(${detail.typeText.trim()})"
        val original = detail.original?.let { toSlotRef(it) }
        val replacement = detail.makeup?.let { toSlotRef(it) }
        val type = detail.typeText.trim()

        when {
            type.contains("补课") -> {
                if (replacement == null) {
                    diagnostics += ImportDiagnostic.error("DETAIL_MAKEUP_INCOMPLETE", "$label 缺少可用的补课时间地点")
                    return
                }
                putException(
                    exceptions,
                    exception(courseId, ExceptionType.MAKEUP, replacement = replacement, substitute = detail.substituteTeacher),
                )
            }
            type.contains("调课") -> {
                if (original == null || replacement == null) {
                    diagnostics += ImportDiagnostic.error("DETAIL_MOVE_INCOMPLETE", "$label 缺少原时间或新时间")
                    return
                }
                putException(
                    exceptions,
                    exception(courseId, ExceptionType.MOVE, original = original, replacement = replacement, substitute = detail.substituteTeacher),
                )
            }
            type.contains("停课") -> {
                if (original == null) {
                    diagnostics += ImportDiagnostic.error("DETAIL_CANCEL_INCOMPLETE", "$label 缺少停课时间")
                    return
                }
                putException(exceptions, exception(courseId, ExceptionType.CANCEL, original = original))
            }
            type.contains("代课") -> {
                if (original == null) {
                    diagnostics += ImportDiagnostic.error("DETAIL_SUBSTITUTE_INCOMPLETE", "$label 缺少代课时间")
                    return
                }
                putException(
                    exceptions,
                    exception(courseId, ExceptionType.SUBSTITUTE, original = original, substitute = detail.substituteTeacher),
                )
            }
            else -> diagnostics += ImportDiagnostic.warning("DETAIL_TYPE_UNRECOGNIZED", "$label 类型无法识别")
        }
    }

    /** 明细里的时间地点缺字段时返回 null，由调用方报诊断。 */
    private fun toSlotRef(place: QnuTimePlace): SlotRef? {
        val week = place.week ?: return null
        val weekday = place.weekday ?: return null
        val sourcePeriodStart = place.periodStart ?: return null
        val sourcePeriodEnd = place.periodEnd ?: sourcePeriodStart
        return SlotRef(
            week = week, weekday = weekday,
            periodStart = detailPeriodToInternal(sourcePeriodStart),
            periodEnd = detailPeriodToInternal(sourcePeriodEnd),
            room = place.room, dateText = place.dateText,
        )
    }

    /** 变更明细不含课表网格预留的午间两格，下午及晚间节次需后移。 */
    private fun detailPeriodToInternal(period: Int): Int =
        if (period in 5..11) period + 2 else period

    private fun exception(
        courseId: String,
        type: ExceptionType,
        original: SlotRef? = null,
        replacement: SlotRef? = null,
        substitute: String? = null,
    ): ScheduleException {
        val anchor = original ?: replacement ?: error("exception needs a slot")
        return ScheduleException(
            id = ScheduleException.stableId(type, courseId, anchor),
            courseId = courseId,
            type = type,
            original = original,
            replacement = replacement,
            substituteTeacher = substitute?.ifBlank { null },
            sourceType = SourceType.QNU_EDU,
        )
    }

    /** 明细数据（含课程号、教室）优先于网格推导出的同位置异常。 */
    private fun putException(map: MutableMap<String, ScheduleException>, exception: ScheduleException) {
        val existing = map[exception.id]
        map[exception.id] = if (existing == null) {
            exception
        } else {
            existing.copy(
                original = exception.original ?: existing.original,
                replacement = mergeSlot(existing.replacement, exception.replacement),
                substituteTeacher = exception.substituteTeacher ?: existing.substituteTeacher,
            )
        }
    }

    private fun mergeSlot(base: SlotRef?, update: SlotRef?): SlotRef? = when {
        base == null -> update
        update == null -> base
        else -> base.copy(
            room = update.room ?: base.room,
            teacher = update.teacher ?: base.teacher,
            dateText = update.dateText ?: base.dateText,
        )
    }

    /** 同一课程同周同星期、节次相邻的补课/停课合并成一条（连堂补课在网格中按节重复）。 */
    private fun mergeAdjacentExceptions(exceptions: List<ScheduleException>): List<ScheduleException> {
        val (mergeable, others) = exceptions.partition {
            (it.type == ExceptionType.MAKEUP && it.replacement != null && it.original == null) ||
                (it.type == ExceptionType.CANCEL && it.original != null)
        }
        val merged = mergeable
            .groupBy { exc ->
                val slot = slotOf(exc)
                listOf(exc.courseId, exc.type, slot.week, slot.weekday, slot.room, exc.substituteTeacher)
            }
            .flatMap { (_, group) ->
                val sorted = group.sortedBy { slotOf(it).periodStart }
                val result = mutableListOf<ScheduleException>()
                var current = sorted.first()
                for (next in sorted.drop(1)) {
                    val currentSlot = slotOf(current)
                    val nextSlot = slotOf(next)
                    current = if (nextSlot.periodStart <= currentSlot.periodEnd + 1) {
                        withSlot(current, currentSlot.copy(periodEnd = maxOf(currentSlot.periodEnd, nextSlot.periodEnd)))
                    } else {
                        result += current
                        next
                    }
                }
                result += current
                result
            }
            .map { exc -> exc.copy(id = ScheduleException.stableId(exc.type, exc.courseId, slotOf(exc))) }
        return (merged + others).sortedBy { it.id }
    }

    private fun slotOf(exception: ScheduleException): SlotRef =
        exception.original ?: exception.replacement!!

    private fun withSlot(exception: ScheduleException, slot: SlotRef): ScheduleException =
        if (exception.original != null) exception.copy(original = slot) else exception.copy(replacement = slot)

    /** 课程注册表：把网格与明细的课程线索合并成稳定课程记录。 */
    private class CourseRegistry(private val semesterId: String, private val page: QnuParsedPage) {

        private data class Key(val name: String, val sequence: String?)

        private val codeByKey: Map<Key, String> = page.changeDetails
            .filter { it.courseCode != null && it.courseName != null }
            .associate { Key(it.courseName!!, it.sequence) to it.courseCode!! }

        private val courses = linkedMapOf<String, Course>()

        fun courseId(name: String, sequence: String?): String {
            val key = Key(name, sequence)
            val profile = QnuTimetableParser.matchCourseProfile(name, sequence, page.courseProfiles)
            val code = codeByKey[key] ?: profile?.courseCode
            val semesterPrefix = semesterId.takeIf(String::isNotBlank)?.let { "$it:" }.orEmpty()
            val id = "qnu:$semesterPrefix${code ?: name}#${sequence ?: "-"}"
            val course = courses.getOrPut(id) {
                Course(
                    id = id,
                    semesterId = semesterId,
                    sourceType = SourceType.QNU_EDU,
                    sourceKey = id,
                    code = code,
                    name = name,
                    sequence = sequence,
                    nature = profile?.nature,
                    credits = profile?.credits,
                    selectionAttribute = profile?.selectionAttribute,
                    assessmentMethod = profile?.assessmentMethod,
                )
            }
            val fullName = cn.qztc.timetable.core.domain.schedule.ScheduleCourseNames.fullest(course.name, name, profile?.courseName) ?: name
            if (fullName != course.name) courses[id] = course.copy(name = fullName)
            return id
        }

        fun courseIdForDetail(detail: QnuChangeDetail): String =
            courseId(detail.courseName ?: detail.courseCode ?: "未知课程", detail.sequence)

        fun referencedCourses(): List<Course> = courses.values.toList()
    }
}
