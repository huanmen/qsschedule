package cn.qztc.timetable.education

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Independent of semester coverage: calendar-only dates also participate in reminders. */
internal class AcademicAgendaStore(context: Context) {
    private val prefs = context.getSharedPreferences("academic-agenda", Context.MODE_PRIVATE)
    fun read(): List<AcademicCalendarEntry> = runCatching {
        readAcademicCalendarEntries(JSONArray(prefs.getString("entries", "[]")), maximumEntries = 5000)
    }.getOrDefault(emptyList())

    fun save(entries: List<AcademicCalendarEntry>) {
        val array = JSONArray().apply { entries.forEach { entry -> put(JSONObject().apply {
            put("id", entry.id); put("title", entry.title); put("date", entry.date.toString())
            put("time", entry.time?.toString().orEmpty()); put("stage", entry.stage)
            put("timetable", entry.timetable); put("kind", entry.kind)
        }) } }
        prefs.edit().putString("entries", array.toString()).apply()
    }
}
