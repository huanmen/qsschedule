package cn.qztc.timetable.feature.timetable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.qztc.timetable.core.domain.calendar.AcademicCalendar
import cn.qztc.timetable.core.domain.calendar.SemesterCalendar
import cn.qztc.timetable.core.domain.model.ActivityKind
import cn.qztc.timetable.core.domain.model.ScheduleLocation
import cn.qztc.timetable.core.domain.schedule.OccurrenceExpander
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

internal object CampusScheduleOverlay {
    var current by mutableStateOf<ImportedSchedule?>(null)

    fun markers(schedule: ImportedSchedule?, places: List<OfflinePlace>, now: LocalDateTime = LocalDateTime.now()): String {
        if (schedule == null) return "[]"
        val result = JSONArray()
        val byId = places.associateBy { it.id }
        fun add(owner: String, title: String, locations: List<ScheduleLocation>, end: LocalDateTime?) {
            if (end == null || !end.isAfter(now)) return
            val grouped = linkedMapOf<Pair<Double, Double>, MutableList<Pair<String, String>>>()
            for (location in locations) {
                val place = location.placeId?.let(byId::get) ?: if (location.placeId == null && location.latitude == null) {
                    val key = location.name.trim()
                    val exact = places.filter { it.name == key || key in it.aliases }
                    val building = CampusPlaceResolver.buildingName(key)
                    exact.firstOrNull() ?: places.firstOrNull {
                        CampusPlaceResolver.buildingName(it.name) == building ||
                            it.aliases.any { alias -> CampusPlaceResolver.buildingName(alias) == building }
                    }
                } else null
                if (location.placeId != null && place == null) continue
                val lat = place?.latitude ?: location.latitude ?: continue
                val lng = place?.longitude ?: location.longitude ?: continue
                val placeName = place?.name ?: "课程地点"
                val room = location.name.ifBlank { placeName }
                grouped.getOrPut(lat to lng) { mutableListOf() }.add(placeName to "$title · $room")
            }
            grouped.entries.forEachIndexed { index, (coordinate, entries) ->
                val markerName = entries.firstOrNull()?.first ?: "课程地点"
                val details = entries.map { it.second }.distinct().joinToString("\n")
                result.put(JSONObject().put("id", "schedule-$owner-$index").put("name", markerName)
                    .put("detail", details).put("temporary", true).put("tags", JSONArray()).put("aliases", JSONArray())
                    .put("status", "user").put("color", "#087fe5")
                    .put("expiresAt", end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                    .put("coordinate", JSONObject().put("lat", coordinate.first).put("lng", coordinate.second)))
            }
        }
        val occurrences = OccurrenceExpander.effective(schedule.semester, schedule.state.copy(suppressedOccurrences = emptyList()), AcademicCalendar(schedule.academicDays))
            .filter { it.isActive }.groupBy { it.courseId }
        val periods = schedule.periods.associateBy { it.no }
        for (course in schedule.state.courses) {
            val rows = occurrences[course.id].orEmpty()
            val end = rows.mapNotNull { row -> periods[row.periodEnd]?.endTime?.let { LocalDateTime.of(row.date, it) } }.maxOrNull()
            val locations = rows.map { schedule.placeBindings[it.meetingId] ?: ScheduleLocation(it.room.orEmpty()) }.distinct()
            add(course.id, course.name, locations, end)
        }
        for (activity in schedule.activities) {
            val sessions = activity.effectiveSessions()
            val end = sessions.flatMap { session ->
                when (session.kind) {
                    ActivityKind.PERIOD -> session.weeks.filter { it <= schedule.semester.totalWeeks }.mapNotNull { week ->
                        periods[session.periodEnd]?.endTime?.let { LocalDateTime.of(SemesterCalendar.dateOf(schedule.semester, week, requireNotNull(session.weekday)), it) }
                    }
                    ActivityKind.DAY -> listOf(requireNotNull(session.date).atStartOfDay().plusMinutes((session.endMinute ?: 1440).toLong()))
                }
            }.maxOrNull()
            add(activity.id, activity.name, sessions.map { it.location }, end)
        }
        return result.toString()
    }
}
