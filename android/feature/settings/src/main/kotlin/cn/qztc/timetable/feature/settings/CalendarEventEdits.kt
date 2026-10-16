package cn.qztc.timetable.feature.settings

import android.content.Context
import java.time.LocalDate
import org.json.JSONObject

class CalendarEventEdits(context: Context) {
    private val prefs=context.getSharedPreferences("calendar-event-edits",Context.MODE_PRIVATE)
    fun merge(events: List<SchoolCalendarEvent>): List<SchoolCalendarEvent> = events.map { event ->
        runCatching { prefs.getString(event.id,null)?.let { JSONObject(it) }?.let { event.copy(
            startDate=LocalDate.parse(it.getString("start")),endDate=LocalDate.parse(it.getString("end"))) } }.getOrNull() ?: event
    }
    fun save(event: SchoolCalendarEvent) { prefs.edit().putString(event.id,JSONObject().put("start",event.startDate.toString()).put("end",(event.endDate?:event.startDate).toString()).toString()).apply() }
}
