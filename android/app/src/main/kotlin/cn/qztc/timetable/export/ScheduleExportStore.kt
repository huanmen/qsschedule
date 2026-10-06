package cn.qztc.timetable.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import cn.qztc.timetable.core.domain.model.OccurrenceState
import cn.qztc.timetable.core.domain.model.Period
import cn.qztc.timetable.core.domain.model.Semester
import cn.qztc.timetable.core.domain.schedule.OccurrenceExpander
import cn.qztc.timetable.core.domain.calendar.AcademicCalendar
import cn.qztc.timetable.core.domain.calendar.AcademicDay
import cn.qztc.timetable.core.domain.model.ScheduleActivity
import cn.qztc.timetable.core.domain.schedule.TimetableState
import cn.qztc.timetable.feature.timetable.ImportedSchedule
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Explicit, local export only. Credentials, cookies and session addresses are not part of either format. */
class ScheduleExportStore(private val context: Context) {
    private val exportDirectory = File(context.cacheDir, "exports").apply { mkdirs() }
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun writeJson(schedule: ImportedSchedule): File {
        val file = freshFile("课表_${schedule.semester.id}", "json")
        file.writeText(
            json.encodeToString(
                ScheduleExport(
                    format = "qztc-timetable/v1",
                    semester = schedule.semester,
                    periods = schedule.periods,
                    timetable = schedule.state,
                    activities = schedule.activities,
                    academicDays = schedule.academicDays,
                    placeBindings = schedule.placeBindings,
                ),
            ),
            Charsets.UTF_8,
        )
        return file
    }

    fun writeIcs(schedule: ImportedSchedule): File {
        val courses = schedule.state.courses.associateBy { it.id }
        val periods = schedule.periods.associateBy { it.no }
        val lines = buildList {
            add("BEGIN:VCALENDAR")
            add("VERSION:2.0")
            add("PRODID:-//QZTC//Timetable//CN")
            add("CALSCALE:GREGORIAN")
            OccurrenceExpander.effective(schedule.semester, schedule.state, AcademicCalendar(schedule.academicDays))
                .filter { it.state != OccurrenceState.CANCELLED }
                .forEach { occurrence ->
                    val course = courses[occurrence.courseId] ?: return@forEach
                    val start = periods[occurrence.periodStart] ?: return@forEach
                    val end = periods[occurrence.periodEnd] ?: return@forEach
                    add("BEGIN:VEVENT")
                    add("UID:${icsEscape(occurrence.courseId)}-${occurrence.date}-${occurrence.periodStart}@qztc")
                    add("DTSTART:${icsDate(LocalDateTime.of(occurrence.date, start.startTime))}")
                    add("DTEND:${icsDate(LocalDateTime.of(occurrence.date, end.endTime))}")
                    add("SUMMARY:${icsEscape(course.name)}")
                    occurrence.room?.takeIf { it.isNotBlank() }?.let { add("LOCATION:${icsEscape(it)}") }
                    occurrence.teacher?.takeIf { it.isNotBlank() }?.let { add("DESCRIPTION:${icsEscape("教师：$it")}") }
                    add("END:VEVENT")
                }
            add("END:VCALENDAR")
        }
        return freshFile("课表_${schedule.semester.id}", "ics").also { it.writeText(lines.joinToString("\r\n"), Charsets.UTF_8) }
    }

    fun share(file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType(mimeType)
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                "导出课表",
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun freshFile(prefix: String, extension: String): File {
        exportDirectory.listFiles().orEmpty().forEach(File::delete)
        return File(exportDirectory, "$prefix-${System.currentTimeMillis()}.$extension")
    }

    private fun icsDate(value: LocalDateTime): String = value.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))

    private fun icsEscape(value: String): String = value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\n", "\\n")
}

@Serializable
private data class ScheduleExport(
    val format: String,
    val semester: Semester,
    val periods: List<Period>,
    val timetable: TimetableState,
    val activities: List<ScheduleActivity> = emptyList(),
    val academicDays: List<AcademicDay> = emptyList(),
    val placeBindings: Map<String, cn.qztc.timetable.core.domain.model.ScheduleLocation> = emptyMap(),
)
