package cn.qztc.timetable.feature.settings

import android.content.Context
import cn.qztc.timetable.core.domain.calendar.AcademicDay
import cn.qztc.timetable.core.domain.calendar.AcademicDayKind
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

internal class CalendarCandidateStore(context: Context, private val semesterId: String) {
    private val prefs = context.getSharedPreferences("calendar-candidates", Context.MODE_PRIVATE)
    private fun holidayKey(day: AcademicDay): String = "${day.date.year}:" + day.name.replace(Regex("调休|补班|补课|假期|放假|节"), "").trim()
    fun keepCustomization(days: List<AcademicDay>) {
        val key = "custom:$semesterId"
        prefs.edit().putStringSet(key, prefs.getStringSet(key, emptySet()).orEmpty() + days.map(::holidayKey)).apply()
    }
    fun acceptsOfficial(day: AcademicDay): Boolean = holidayKey(day) !in prefs.getStringSet("custom:$semesterId", emptySet()).orEmpty()
    fun load(): List<AcademicDay> = runCatching {
        val array = JSONArray(prefs.getString(semesterId, "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            AcademicDay(item.getString("id"), semesterId, LocalDate.parse(item.getString("date")),
                AcademicDayKind.valueOf(item.getString("kind")), item.getString("name"),
                item.optInt("week").takeIf { it > 0 }, item.optInt("weekday").takeIf { it > 0 },
                item.optBoolean("currentWeek"), item.optBoolean("currentWeekday"))
        }
    }.getOrDefault(emptyList())

    fun save(days: List<AcademicDay>) {
        val array = JSONArray()
        days.forEach { day -> array.put(JSONObject().put("id", day.id).put("date", day.date.toString())
            .put("kind",day.kind.name).put("name",day.name).put("week",day.sourceWeek).put("weekday",day.sourceWeekday)
            .put("currentWeek",day.useCurrentWeek).put("currentWeekday",day.useCurrentWeekday)) }
        prefs.edit().putString(semesterId, array.toString()).apply()
    }
}
