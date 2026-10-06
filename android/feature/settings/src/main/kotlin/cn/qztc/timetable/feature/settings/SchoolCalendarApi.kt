package cn.qztc.timetable.feature.settings

import android.content.Context
import cn.qztc.timetable.core.domain.calendar.AcademicDay
import cn.qztc.timetable.core.domain.calendar.AcademicDayKind
import cn.qztc.timetable.core.domain.model.Term
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import org.json.JSONObject

data class SchoolCalendarResult(
    val days: List<AcademicDay>,
    val firstSemesterStart: LocalDate?,
    val secondSemesterStart: LocalDate?,
    val firstSemesterEnd: LocalDate? = null,
    val secondSemesterEnd: LocalDate? = null,
    val sourceUrl: String? = null,
    val pdfUrl: String? = null,
    val schoolYear: String? = null,
    val events: List<SchoolCalendarEvent> = emptyList(),
) {
    /** A normalized semester extracted from the official calendar PDF. */
    fun detectedSemesters(): List<DetectedSchoolSemester> {
        val year = schoolYear ?: return emptyList()
        return listOfNotNull(
            firstSemesterStart?.let { start ->
                DetectedSchoolSemester(
                    schoolYear = year,
                    term = Term.AUTUMN,
                    label = "第一学期",
                    startDate = start,
                    endDate = firstSemesterEnd,
                )
            },
            secondSemesterStart?.let { start ->
                DetectedSchoolSemester(
                    schoolYear = year,
                    term = Term.SPRING,
                    label = "第二学期",
                    startDate = start,
                    endDate = secondSemesterEnd,
                )
            },
        )
    }

    /**
     * Identifies the semester for a date. During a winter/summer break it returns
     * the semester that most recently started, so the app keeps using the current
     * academic schedule without guessing from the calendar month.
     */
    fun semesterFor(date: LocalDate): DetectedSchoolSemester? {
        val semesters = detectedSemesters()
        return semesters.firstOrNull { it.contains(date) }
            ?: semesters.filter { !it.startDate.isAfter(date) }.maxByOrNull { it.startDate }
            ?: semesters.minByOrNull { kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(date, it.startDate)) }
    }
}

enum class SchoolCalendarEventType {
    REPORTING,
    TEACHING_START,
    BREAK,
    SPORTS_MEETING,
    EXAM,
    OTHER,
}

data class SchoolCalendarEvent(
    val id: String,
    val schoolYear: String,
    val semesterLabel: String,
    val type: SchoolCalendarEventType,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val detail: String = "",
) {
    fun displayLabel(): String {
        val dateText = if (endDate == null || endDate == startDate) {
            formatDate(startDate)
        } else {
            "${formatDate(startDate)} 至 ${formatDate(endDate)}"
        }
        return "$dateText · $name"
    }

    private fun formatDate(date: LocalDate): String =
        "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
}

data class DetectedSchoolSemester(
    val schoolYear: String,
    val term: Term,
    val label: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
) {
    fun contains(date: LocalDate): Boolean =
        !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate))

    fun displayLabel(): String = buildString {
        append(schoolYear).append(' ').append(label)
        append("（").append(formatDate(startDate))
        endDate?.let { append(" 至 ").append(formatDate(it)) }
        append('）')
    }

    private fun formatDate(date: LocalDate): String =
        "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
}

object SchoolCalendarApi {
    private const val ENDPOINT = "https://web.huanmen.com.cn/qztc/ditu/api/calendar/school"

    suspend fun loadFromWebsite(context: Context, schoolYear: String, semesterId: String): SchoolCalendarResult =
        SchoolCalendarWebFetcher.load(context, schoolYear, semesterId).also { result ->
            val root = JSONObject().put("schoolYear", result.schoolYear).put("sourceUrl", result.sourceUrl).put("pdfUrl", result.pdfUrl)
            fun range(start: LocalDate?, end: LocalDate?) = JSONObject().put("startDate", start?.toString()).put("endDate", end?.toString())
            root.put("semester", JSONObject().put("first", range(result.firstSemesterStart, result.firstSemesterEnd)).put("second", range(result.secondSemesterStart, result.secondSemesterEnd)))
            root.put("events", org.json.JSONArray().apply { result.events.forEach { event ->
                put(JSONObject().put("id",event.id).put("schoolYear",event.schoolYear).put("semesterLabel",event.semesterLabel)
                    .put("type",event.type.name).put("name",event.name).put("startDate",event.startDate.toString())
                    .put("endDate",event.endDate?.toString()).put("detail",event.detail))
            } })
            context.getSharedPreferences("school-calendar", Context.MODE_PRIVATE).edit()
                .putString("year:$schoolYear",root.toString())
                .putLong("fetched:$schoolYear", System.currentTimeMillis()).apply()
        }

    fun cached(context: Context, schoolYear: String, semesterId: String): SchoolCalendarResult? = runCatching {
        val body = context.getSharedPreferences("school-calendar", Context.MODE_PRIVATE).getString("year:$schoolYear", null)
            ?: return@runCatching null
        parse(body, schoolYear, semesterId)
    }.getOrNull()

    fun load(context: Context, schoolYear: String, semesterId: String): SchoolCalendarResult {
        val prefs = context.getSharedPreferences("school-calendar", Context.MODE_PRIVATE)
        val key = "year:$schoolYear"
        val body = run {
            val connection = URL("$ENDPOINT?schoolYear=$schoolYear").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 8_000; connection.readTimeout = 8_000
                connection.useCaches = false
                connection.setRequestProperty("Cache-Control", "no-cache")
                if (connection.responseCode !in 200..299) error("学校校历尚未同步")
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.also { prefs.edit().putString(key, it).apply() }
            } finally { connection.disconnect() }
        }
        return parse(body, schoolYear, semesterId)
    }

    private fun parse(body: String, schoolYear: String, semesterId: String): SchoolCalendarResult {
        val root = JSONObject(body)
        require(root.optString("schoolYear") == schoolYear) { "校历学年不匹配，已保留上次结果" }
        val semester = root.optJSONObject("semester")
        fun start(name: String) = semester?.optJSONObject(name)?.optString("startDate")?.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
        fun end(name: String) = semester?.optJSONObject(name)?.optString("endDate")?.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
        val dayArray = root.optJSONArray("days") ?: org.json.JSONArray()
        val days = (0 until dayArray.length()).map { index ->
            val item = dayArray.getJSONObject(index)
            AcademicDay("school:$schoolYear:${item.getString("date")}", semesterId, LocalDate.parse(item.getString("date")),
                if (item.optString("kind") == "MAKEUP") AcademicDayKind.MAKEUP else AcademicDayKind.HOLIDAY, item.optString("name"),
                useCurrentWeek = item.optString("kind") == "MAKEUP")
        }
        val events = root.optJSONArray("events")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val startDate = item.optString("startDate").takeIf { it.isNotBlank() }?.let(LocalDate::parse)
                    ?: return@mapNotNull null
                val type = runCatching { SchoolCalendarEventType.valueOf(item.optString("type")) }
                    .getOrDefault(SchoolCalendarEventType.OTHER)
                SchoolCalendarEvent(
                    id = item.optString("id", "school-event:$schoolYear:$index"),
                    schoolYear = item.optString("schoolYear", schoolYear),
                    semesterLabel = item.optString("semesterLabel"),
                    type = type,
                    name = item.optString("name", "校历安排"),
                    startDate = startDate,
                    endDate = item.optString("endDate").takeIf { it.isNotBlank() }?.let(LocalDate::parse),
                    detail = item.optString("detail"),
                )
            }
        }.orEmpty()
        return SchoolCalendarResult(
            days = days,
            firstSemesterStart = start("first"),
            secondSemesterStart = start("second"),
            firstSemesterEnd = end("first"),
            secondSemesterEnd = end("second"),
            sourceUrl = root.optString("sourceUrl").takeIf { it.isNotBlank() },
            pdfUrl = root.optString("pdfUrl").takeIf { it.isNotBlank() },
            schoolYear = root.optString("schoolYear", schoolYear).ifBlank { schoolYear },
            events = events,
        )
    }
}
