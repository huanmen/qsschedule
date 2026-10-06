package cn.qztc.timetable.sync

import android.content.Context
import cn.qztc.timetable.core.domain.model.ScheduleException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** One local inbox per term; checking it never changes the timetable. */
class ScheduleChangeInbox(context: Context, private val semesterId: String) {
    private val prefs = context.getSharedPreferences("upcoming-schedule-changes", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(ScheduleException.serializer())
    fun read(): List<ScheduleException> = runCatching {
        json.decodeFromString(serializer, prefs.getString(semesterId, "[]")!!)
    }.getOrDefault(emptyList())
    fun receive(values: List<ScheduleException>) {
        val old = read().associateBy { it.id }.toMutableMap()
        values.forEach { value -> if (!prefs.getBoolean("edited:$semesterId:${value.id}", false)) old[value.id] = value }
        prefs.edit().putString(semesterId, json.encodeToString(serializer, old.values.toList())).apply()
    }
    fun edit(value: ScheduleException) {
        val next = read().filterNot { it.id == value.id } + value
        prefs.edit().putString(semesterId, json.encodeToString(serializer, next))
            .putBoolean("edited:$semesterId:${value.id}", true).apply()
    }
}
