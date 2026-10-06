package cn.qztc.timetable.feature.timetable

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CampusMapBounds(
    val minLatitude: Double,
    val minLongitude: Double,
    val maxLatitude: Double,
    val maxLongitude: Double,
) {
    fun contains(point: CampusCoordinate, paddingRatio: Double = 0.0): Boolean {
        val latPad = (maxLatitude - minLatitude) * paddingRatio
        val lonPad = (maxLongitude - minLongitude) * paddingRatio
        return point.latitude in (minLatitude - latPad)..(maxLatitude + latPad) &&
            point.longitude in (minLongitude - lonPad)..(maxLongitude + lonPad)
    }

    fun clamp(point: CampusCoordinate): CampusCoordinate = CampusCoordinate(
        latitude = point.latitude.coerceIn(minLatitude, maxLatitude),
        longitude = point.longitude.coerceIn(minLongitude, maxLongitude),
    )
}

@Serializable
data class CampusMapFeature(
    val id: String,
    val kind: String,
    val subtype: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val placeId: String = "",
    val buildingClass: String = "",
    val source: String = "",
    val labelPoint: CampusCoordinate? = null,
    val nodeIds: List<String> = emptyList(),
    val points: List<CampusCoordinate> = emptyList(),
)

@Serializable
data class CampusMapDataset(
    val bounds: CampusMapBounds,
    val campusBoundary: List<CampusCoordinate> = emptyList(),
    val features: List<CampusMapFeature> = emptyList(),
    val attribution: String = "OpenStreetMap contributors",
) {
    val buildings: List<CampusMapFeature> get() = features.filter { it.kind == "building" }
    val roads: List<CampusMapFeature> get() = features.filter { it.kind == "road" }
    val paths: List<CampusMapFeature> get() = features.filter { it.kind == "path" }
    val namedPois: List<CampusMapFeature> get() = features.filter { it.kind == "poi" && it.name.isNotBlank() }

    companion object {
        fun empty(): CampusMapDataset = CampusMapDataset(
            bounds = CampusMapBounds(
                minLatitude = CampusRouteGraph.points.minOf { it.coordinate.latitude },
                minLongitude = CampusRouteGraph.points.minOf { it.coordinate.longitude },
                maxLatitude = CampusRouteGraph.points.maxOf { it.coordinate.latitude },
                maxLongitude = CampusRouteGraph.points.maxOf { it.coordinate.longitude },
            ),
            campusBoundary = emptyList(),
            features = emptyList(),
            attribution = "地图数据暂不可用",
        )
    }
}

object CampusMapRepository {
    private const val assetPath = "campus/campus-map.json"
    private val parserJson = Json { ignoreUnknownKeys = true }

    fun load(context: Context): CampusMapDataset = runCatching {
        context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { reader ->
            parse(reader.readText())
        }
    }.getOrElse { CampusMapDataset.empty() }

    internal fun parse(json: String): CampusMapDataset {
        return parserJson.decodeFromString<CampusMapDataset>(json.removePrefix("\uFEFF"))
    }
}

@Serializable
data class CampusPlaceRecord(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val category: String = "other",
    val isDormitory: Boolean = false,
    val latitude: Double,
    val longitude: Double,
)

@Serializable
data class CampusPlaceDatabase(
    val version: Int = 1,
    val places: List<CampusPlaceRecord> = emptyList(),
)

class CampusPlaceCatalog private constructor(
    val places: List<CampusPoint>,
) {
    private val byAlias = places
        .flatMap { point -> (point.aliases + point.name).map { it to point } }
        .associateBy({ CampusPlaceResolver.normalize(it.first) }, { it.second })

    fun resolve(raw: String): CampusPoint? {
        val normalized = CampusPlaceResolver.normalize(raw)
        val building = CampusPlaceResolver.buildingName(normalized)
        return byAlias[normalized]
            ?: byAlias[building]
            ?: byAlias.entries.firstOrNull { normalized.contains(it.key) }?.value
    }

    fun nearest(coordinate: CampusCoordinate): CampusPoint? {
        val candidate = places.minByOrNull { CampusRouteGraph.distanceMeters(it.coordinate, coordinate) }
            ?: return null
        return candidate.takeIf { CampusRouteGraph.distanceMeters(it.coordinate, coordinate) <= 2_500.0 }
    }

    fun point(id: String): CampusPoint? = places.firstOrNull { it.id == id }

    fun search(query: String, limit: Int = 8): List<CampusPoint> =
        CampusPlaceSearch.match(query, places, limit)

    fun withAliases(extra: Map<String, Set<String>>): CampusPlaceCatalog {
        if (extra.isEmpty()) return this
        return CampusPlaceCatalog(
            places.map { place ->
                val added = extra[place.id].orEmpty()
                if (added.isEmpty()) place else place.copy(aliases = place.aliases + added)
            },
        )
    }

    fun groupedNavigablePlaces(): List<Pair<String, List<CampusPoint>>> {
        val order = listOf("building", "landmark", "service", "entrance", "other")
        val labels = mapOf(
            "entrance" to "出入口",
            "building" to "楼馆",
            "landmark" to "场地",
            "service" to "生活服务",
            "other" to "其他",
        )
        return places
            .groupBy { place ->
                CampusDormZones.zoneOf(place) ?: place.category.ifBlank { "other" }
            }
            .toList()
            .sortedBy { (key, _) ->
                val zones = listOf("A区", "B区", "C区", "D区")
                val zoneIndex = zones.indexOf(key)
                if (zoneIndex >= 0) order.size + zoneIndex else {
                    val index = order.indexOf(key)
                    if (index >= 0) index else order.lastIndex
                }
            }
            .map { (key, group) ->
                val label = when (key) {
                    "A区", "B区", "C区", "D区" -> "宿舍$key"
                    else -> labels[key] ?: "其他"
                }
                label to group.sortedBy(CampusPoint::name)
            }
    }

    companion object {
        fun fromPoints(places: List<CampusPoint>) = CampusPlaceCatalog(places)

        fun from(database: CampusPlaceDatabase): CampusPlaceCatalog = fromPoints(
            database.places.map { place ->
                CampusPoint(
                    id = place.id,
                    name = place.name,
                    coordinate = CampusCoordinate(place.latitude, place.longitude),
                    aliases = place.aliases.toSet(),
                    category = place.category.ifBlank { "other" },
                    isDormitory = place.isDormitory,
                )
            },
        )

        fun empty(): CampusPlaceCatalog = fromPoints(emptyList())
    }
}

object CampusPlaceRepository {
    private const val assetPath = "campus/places.json"
    private val parserJson = Json { ignoreUnknownKeys = true }

    fun load(context: Context): CampusPlaceCatalog = runCatching {
        context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { reader ->
            fromJson(reader.readText())
        }
    }.getOrElse { CampusPlaceCatalog.empty() }

    internal fun fromJson(json: String): CampusPlaceCatalog = CampusPlaceCatalog.from(
        parserJson.decodeFromString<CampusPlaceDatabase>(json.removePrefix("\uFEFF")),
    )
}

data class CampusMapRoute(
    val points: List<CampusCoordinate>,
    val distanceMeters: Int,
    val durationSeconds: Int = 0,
    val steps: List<CampusRouteStep> = emptyList(),
)
