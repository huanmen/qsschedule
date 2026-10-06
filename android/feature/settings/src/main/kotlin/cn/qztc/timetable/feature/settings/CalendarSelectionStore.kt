package cn.qztc.timetable.feature.settings

import android.content.Context

/** Explicit choices never apply merely by opening or refreshing the calendar. */
internal class CalendarSelectionStore(context: Context) {
    private val prefs = context.getSharedPreferences("calendar-inclusion", Context.MODE_PRIVATE)
    fun load(): Map<String, Boolean> = prefs.all.mapNotNull { (key, value) -> (value as? Boolean)?.let { key to it } }.toMap()
    fun set(key: String, included: Boolean) { prefs.edit().putBoolean(key, included).apply() }
}
