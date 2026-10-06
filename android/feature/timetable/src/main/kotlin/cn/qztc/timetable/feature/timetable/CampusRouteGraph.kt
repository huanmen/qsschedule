package cn.qztc.timetable.feature.timetable

import kotlin.math.cos
import kotlin.math.sqrt
import kotlinx.serialization.Serializable

@Serializable
data class CampusCoordinate(val latitude: Double, val longitude: Double)

data class CampusPoint(
    val id: String,
    val name: String,
    val coordinate: CampusCoordinate,
    val aliases: Set<String> = emptySet(),
    val category: String = "other",
    val isDormitory: Boolean = false,
)

/** Reference points used to resolve campus locations and draw the local map. */
object CampusRouteGraph {
    private fun point(id: String, name: String, latitude: Double, longitude: Double, vararg aliases: String) =
        CampusPoint(id, name, CampusCoordinate(latitude, longitude), aliases.toSet())

    val points: List<CampusPoint> = listOf(
        point("main-entrance", "东海校区主入口", 24.8670560, 118.6626915, "东海校区大门", "校门", "泉州师范学院"),
        point("east-gate", "东门接驳点", 24.8708996, 118.6673956, "东门"),
        point("north-junction", "明德路北口", 24.8719233, 118.6680193, "明德路"),
        point("chaoyang", "朝阳楼", 24.8684750, 118.6630160, "朝阳", "朝阳楼", "黄朝阳教学楼"),
        point("baolong-lab", "宝龙实验楼", 24.8713997, 118.6678557, "实验楼", "宝龙实验楼", "实验楼A"),
        point("wangai-lab", "王爱约实验楼", 24.8726488, 118.6676949, "王爱约实验楼"),
        point("science", "理工学院", 24.8712473, 118.6685932, "理工学院", "慈山楼", "慈山"),
        point("central-square", "中山广场", 24.8687077, 118.6638441, "中山广场", "求是路", "行知路"),
        point("wenke", "文科楼", 24.8693002, 118.6628959, "文科楼", "文科楼A栋"),
        point("rongmao", "荣茂楼", 24.8681494, 118.6634778, "荣茂", "荣茂楼", "荣茂综合楼"),
        point("qianshu", "千墅楼", 24.8679521, 118.6640736, "千墅", "苏千墅", "苏千墅教学楼"),
        point("gym", "风雨操场", 24.8686042, 118.6651225, "风雨操场", "操场", "体育馆"),
        point("education", "教育科学学院", 24.8672652, 118.6636725, "教育科学学院", "教育学院"),
    )

    private val pointById = points.associateBy(CampusPoint::id)
    private val pointByAlias = points.flatMap { point ->
        (point.aliases + point.name).map { alias -> alias to point }
    }.toMap()

    fun point(id: String): CampusPoint = pointById.getValue(id)

    fun resolve(raw: String): CampusPoint? {
        val normalized = CampusPlaceResolver.normalize(raw)
        val building = CampusPlaceResolver.buildingName(normalized)
        return pointByAlias[normalized]
            ?: pointByAlias[building]
            ?: pointByAlias.entries.firstOrNull { normalized.contains(it.key) }?.value
    }

    fun nearest(coordinate: CampusCoordinate): CampusPoint? {
        val candidate = points.minByOrNull { distanceMeters(it.coordinate, coordinate) } ?: return null
        return candidate.takeIf { distanceMeters(it.coordinate, coordinate) <= 2_500 }
    }

    fun distanceMeters(a: CampusCoordinate, b: CampusCoordinate): Double {
        val latScale = 111_320.0
        val lonScale = latScale * cos(Math.toRadians((a.latitude + b.latitude) / 2))
        val dy = (a.latitude - b.latitude) * latScale
        val dx = (a.longitude - b.longitude) * lonScale
        return sqrt(dx * dx + dy * dy)
    }
}
