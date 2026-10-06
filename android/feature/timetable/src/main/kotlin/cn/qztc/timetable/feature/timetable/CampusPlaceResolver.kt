package cn.qztc.timetable.feature.timetable

/**
 * Converts the abbreviations used by the academic system into stable campus names.
 * The resolver deliberately keeps the campus data local and does not contain map keys.
 */
object CampusPlaceResolver {
    private val roomPattern = Regex("(朝阳|荣茂|千墅)(?:楼)?\\s*(\\d{3,4})")
    private val compactRoomPattern = Regex("(朝阳|荣茂|千墅)(\\d{3,4})")

    fun normalize(raw: String): String {
        val value = raw.trim().replace(" ", "")
        if (value.isBlank()) return value
        (roomPattern.matchEntire(value) ?: compactRoomPattern.matchEntire(value))?.let { match ->
            val building = when (match.groupValues[1]) {
                "荣茂" -> "荣茂楼"
                "千墅" -> "千墅楼"
                else -> "朝阳楼"
            }
            val room = match.groupValues[2]
            return "$building${room.first()}楼${room}室"
        }
        return when {
            value == "朝阳" || value == "朝阳楼" || value == "黄朝阳教学楼" -> "朝阳楼"
            value == "荣茂" || value == "荣茂楼" || value == "荣茂综合楼" -> "荣茂楼"
            value == "千墅" || value == "千墅楼" || value == "苏千墅" || value == "苏千墅教学楼" -> "千墅楼"
            value.startsWith("朝阳楼") -> value
            value.startsWith("荣茂楼") -> value
            value.startsWith("千墅楼") -> value
            else -> value
        }
    }

    fun buildingName(raw: String): String {
        val value = normalize(raw)
        return when {
            value.startsWith("朝阳") || value.startsWith("黄朝阳") -> "朝阳楼"
            value.startsWith("荣茂") -> "荣茂楼"
            value.startsWith("千墅") || value.startsWith("苏千墅") -> "千墅楼"
            else -> value
        }
    }

    fun searchQuery(raw: String): String = "泉州师范学院 ${buildingName(raw)}"

}
