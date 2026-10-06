package cn.qztc.timetable.core.domain.model

import cn.qztc.timetable.core.domain.imports.ImportedPeriod
import cn.qztc.timetable.core.domain.serialization.LocalTimeSerializer
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable

enum class DaySegment { MORNING, NOON, AFTERNOON, EVENING }

/**
 * 一个教学时段。[no] 与教务网格的行序一致（1..13），课程只引用节次编号，不复制时间。
 */
@Serializable
data class Period(
    val no: Int,
    val label: String,
    @Serializable(LocalTimeSerializer::class) val startTime: LocalTime,
    @Serializable(LocalTimeSerializer::class) val endTime: LocalTime,
    val segment: DaySegment,
)

@Serializable
data class PeriodScheme(
    val id: String,
    val semesterId: String,
    val name: String,
    val editable: Boolean = true,
    val periods: List<Period>,
)

/** 泉师实测默认作息（PRD F-PERIOD-01/02）。 */
object QnuPeriodDefaults {

    fun periods(): List<Period> = listOf(
        period(1, "第1节", "08:20", "09:05", DaySegment.MORNING),
        period(2, "第2节", "09:15", "10:00", DaySegment.MORNING),
        period(3, "第3节", "10:20", "11:05", DaySegment.MORNING),
        period(4, "第4节", "11:15", "12:00", DaySegment.MORNING),
        period(5, "午间1节", "12:20", "13:05", DaySegment.NOON),
        period(6, "午间2节", "13:15", "14:00", DaySegment.NOON),
        period(7, "第5节", "14:30", "15:15", DaySegment.AFTERNOON),
        period(8, "第6节", "15:25", "16:10", DaySegment.AFTERNOON),
        period(9, "第7节", "16:30", "17:15", DaySegment.AFTERNOON),
        period(10, "第8节", "17:25", "18:10", DaySegment.AFTERNOON),
        period(11, "第9节", "19:00", "19:45", DaySegment.EVENING),
        period(12, "第10节", "19:55", "20:40", DaySegment.EVENING),
        period(13, "第11节", "20:50", "21:35", DaySegment.EVENING),
    )

    private fun period(no: Int, label: String, start: String, end: String, segment: DaySegment) =
        Period(no, label, LocalTime.parse(start), LocalTime.parse(end), segment)
}

/**
 * 为学期生成作息方案：优先采用页面解析出的标签与时间，缺项回落到 [QnuPeriodDefaults]。
 * 导入不会覆盖用户已保存的方案，由仓库在方案缺失时才写入。
 */
object PeriodSchemeFactory {

    private val hourMinute = DateTimeFormatter.ofPattern("H:mm")

    fun forSemester(
        semesterId: String,
        imported: List<ImportedPeriod> = emptyList(),
        name: String = "泉师默认作息",
    ): PeriodScheme {
        val byNo = imported.associateBy { it.no }
        val periods = QnuPeriodDefaults.periods().map { def ->
            val raw = byNo[def.no]
            def.copy(
                label = raw?.label?.takeIf { it.isNotBlank() } ?: def.label,
                startTime = parseTime(raw?.startText) ?: def.startTime,
                endTime = parseTime(raw?.endText) ?: def.endTime,
            )
        }
        return PeriodScheme(
            id = "$semesterId:periods",
            semesterId = semesterId,
            name = name,
            editable = true,
            periods = periods,
        )
    }

    private fun parseTime(text: String?): LocalTime? {
        val value = text?.trim().orEmpty()
        if (value.isEmpty()) return null
        return runCatching { LocalTime.parse(value) }.getOrNull()
            ?: runCatching { LocalTime.parse(value, hourMinute) }.getOrNull()
    }
}
