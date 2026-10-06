package cn.qztc.timetable.integration.qnu

import cn.qztc.timetable.core.domain.imports.ImportDiagnostic

/** 主网格一个单元格里的一条课程记录（原文形态，尚未映射领域模型）。 */
data class QnuGridEntry(
    val weekday: Int,
    val periodRow: Int,
    val courseName: String,
    val sequence: String?,
    val room: String?,
    val teacher: String?,
    val weekText: String,
    val typeText: String,
    val extraStatuses: List<String> = emptyList(),
) {
    /** 类型列与追加状态行里的全部状态词（如 讲课学时、补课、停课）。 */
    val statusTokens: Set<String>
        get() = (typeText.split(' ', '　').filter { it.isNotBlank() } +
            extraStatuses.flatMap { line -> line.split(' ', '　').filter { it.isNotBlank() } })
            .toSet()
}

/** 作息表头一行：节次标签与起止时间原文。 */
data class QnuPeriodHeader(
    val rowIndex: Int,
    val label: String,
    val startText: String?,
    val endText: String?,
)

/** 调停补明细表的一处时间地点。 */
data class QnuTimePlace(
    val dateText: String?,
    val week: Int?,
    val weekday: Int?,
    val periodStart: Int?,
    val periodEnd: Int?,
    val room: String?,
) {
    val isEmpty: Boolean
        get() = dateText == null && week == null && weekday == null &&
            periodStart == null && periodEnd == null && room == null
}

/** 调停补明细一条记录；续行继承上一条的课程上下文。 */
data class QnuChangeDetail(
    val typeText: String,
    val courseCode: String?,
    val courseName: String?,
    val sequence: String?,
    val teacher: String?,
    val substituteTeacher: String?,
    val hoursText: String?,
    val original: QnuTimePlace?,
    val makeup: QnuTimePlace?,
    val isContinuation: Boolean,
)

/** 一次页面解析的完整结果。结构异常通过 [diagnostics] 报告，不抛异常也不静默丢弃。 */
data class QnuParsedPage(
    val charset: String? = null,
    val yearText: String? = null,
    val termText: String? = null,
    val className: String? = null,
    val periods: List<QnuPeriodHeader> = emptyList(),
    val gridEntries: List<QnuGridEntry> = emptyList(),
    val changeDetails: List<QnuChangeDetail> = emptyList(),
    val courseProfiles: List<QnuCourseProfile> = emptyList(),
    val diagnostics: List<ImportDiagnostic> = emptyList(),
)

/** 课程安排表中的课程详情，按课程名和课序号挂到课程上。 */
data class QnuCourseProfile(
    val courseName: String,
    val sequence: String? = null,
    val courseCode: String? = null,
    val nature: String? = null,
    val credits: String? = null,
    val selectionAttribute: String? = null,
    val assessmentMethod: String? = null,
)

data class QnuGradeRecord(
    val courseCode: String?,
    val courseName: String,
    val score: String?,
    val gpa: String?,
    val credits: String?,
    val assessment: String?,
    val selection: String?,
    val status: String?,
    val year: String? = null,
    val term: String? = null,
    val examType: String? = null,
    val courseSequence: String? = null,
)

data class QnuEvaluationTask(
    val teacher: String,
    val course: String,
    val state: String?,
    val actionPath: String?,
)
