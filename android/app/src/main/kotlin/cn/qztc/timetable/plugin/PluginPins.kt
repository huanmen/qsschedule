package cn.qztc.timetable.plugin

import android.content.Context

object PluginPins {
    private fun prefs(context: Context) = context.getSharedPreferences("personal_plugin_pins", Context.MODE_PRIVATE)
    fun contains(context: Context, id: String) = id in prefs(context).getStringSet("ids", emptySet()).orEmpty()
    fun set(context: Context, id: String, pinned: Boolean) {
        val ids = prefs(context).getStringSet("ids", emptySet()).orEmpty().toMutableSet()
        if (pinned) ids.add(id) else ids.remove(id)
        prefs(context).edit().putStringSet("ids", ids).apply()
    }
}
