package cn.qztc.timetable.core.domain.imports

import cn.qztc.timetable.core.domain.model.Term
import cn.qztc.timetable.core.domain.schedule.TimetableState
import kotlinx.serialization.Serializable

enum class DiagnosticSeverity { INFO, WARNING, ERROR }

/**
 * 导入过程中的可诊断事件。页面出现未知结构时必须生成诊断而不是静默丢弃。
 */
@Serializable
data class ImportDiagnostic(
    val severity: DiagnosticSeverity,
    val code: String,
    val message: String,
) {
    companion object {
        fun info(code: String, message: String) = ImportDiagnostic(DiagnosticSeverity.INFO, code, message)
        fun warning(code: String, message: String) = ImportDiagnostic(DiagnosticSeverity.WARNING, code, message)
        fun error(code: String, message: String) = ImportDiagnostic(DiagnosticSeverity.ERROR, code, message)
    }
}

/** 从页面识别出的学期线索；无法完整识别时由用户确认（F-TERM-02）。 */
@Serializable
data class SemesterHint(
    val year: Int? = null,
    val term: Term? = null,
    val termText: String? = null,
    val raw: String = "",
)

/** 页面作息行（原文时间，由上层决定是否生成 PeriodScheme）。 */
@Serializable
data class ImportedPeriod(
    val no: Int,
    val label: String,
    val startText: String?,
    val endText: String?,
)

/**
 * 一次导入的标准候选数据：已归一化为领域状态（确定性 ID、来源 QNU_EDU），
 * 附带学期线索、作息与诊断信息。
 */
data class ImportedTimetable(
    val semesterHint: SemesterHint?,
    val periods: List<ImportedPeriod>,
    val state: TimetableState,
    val diagnostics: List<ImportDiagnostic>,
) {
    val hasErrors: Boolean
        get() = diagnostics.any { it.severity == DiagnosticSeverity.ERROR }
}
