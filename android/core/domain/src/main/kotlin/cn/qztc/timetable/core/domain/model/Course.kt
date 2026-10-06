package cn.qztc.timetable.core.domain.model

import kotlinx.serialization.Serializable

enum class SourceType { QNU_EDU, MANUAL, JSON_IMPORT, ICS_IMPORT }

@Serializable
data class Course(
    val id: String,
    val semesterId: String,
    val sourceType: SourceType,
    /** 参与稳定去重的来源键：优先课程号，缺失时用课程名，再拼课序号。 */
    val sourceKey: String,
    val code: String? = null,
    val name: String,
    val sequence: String? = null,
    val color: Int? = null,
    val note: String? = null,
    val nature: String? = null,
    val credits: String? = null,
    /** 教务课程安排表中的选课属性，例如必修、选修或任选。 */
    val selectionAttribute: String? = null,
    /** 教务课程安排表中的考核方式，例如考试或考查。 */
    val assessmentMethod: String? = null,
)
