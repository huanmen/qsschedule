package cn.qztc.timetable.feature.timetable

internal data class CampusMapMarker(
    val id: String,
    val name: String,
    val coordinate: CampusCoordinate,
    val place: CampusPoint? = null,
    val zone: String? = null,
)

internal object CampusDormZones {
    private val zoneInName = Regex("([A-D])区")
    private val zoneQuery = Regex("^([A-D])区$")

    fun zoneOf(place: CampusPoint): String? {
        val match = zoneInName.find(place.name) ?: zoneInName.find(place.aliases.joinToString())
        val zone = match?.groupValues?.get(1)?.let { "${it}区" } ?: return null
        return if (place.isDormitory || place.name.contains("号楼")) zone else null
    }

    fun zoneFromQuery(query: String): String? {
        val compact = query.trim().uppercase().replace(" ", "")
        return zoneQuery.find(compact)?.groupValues?.get(1)?.let { "${it}区" }
    }

    fun visibleMarkers(
        places: List<CampusPoint>,
        destination: CampusPoint? = null,
        revealedIds: Set<String> = emptySet(),
        expandedZones: Set<String> = emptySet(),
    ): List<CampusMapMarker> {
        val dorms = places.groupBy { zoneOf(it) }
        val markers = ArrayList<CampusMapMarker>(places.size)
        places.filter { zoneOf(it) == null }.forEach { place ->
            if (place.category != "other") {
                markers += markerFor(place)
            }
        }
        for (zone in listOf("A区", "B区", "C区", "D区")) {
            val members = dorms[zone].orEmpty()
            if (members.isEmpty()) continue
            val showBuildings = zone in expandedZones ||
                members.any { it.id == destination?.id || it.id in revealedIds }
            if (showBuildings) {
                val extra = members.filter { it.id == destination?.id || it.id in revealedIds }
                val shown = if (zone in expandedZones) members else extra.ifEmpty { members }
                shown.forEach { markers += markerFor(it) }
                if (zone in expandedZones) {
                    markers += zoneMarker(zone, members)
                }
            } else {
                markers += zoneMarker(zone, members)
            }
        }
        return markers
    }

    fun destinationAfterTap(current: CampusPoint?, tapped: CampusPoint): CampusPoint? =
        if (current?.id == tapped.id) null else tapped

    private fun markerFor(place: CampusPoint) = CampusMapMarker(
        id = place.id,
        name = place.name,
        coordinate = place.coordinate,
        place = place,
        zone = zoneOf(place),
    )

    private fun zoneMarker(zone: String, members: List<CampusPoint>) = CampusMapMarker(
        id = "zone-$zone",
        name = zone,
        coordinate = centroid(members),
        zone = zone,
    )

    private fun centroid(places: List<CampusPoint>): CampusCoordinate {
        val latitude = places.sumOf { it.coordinate.latitude } / places.size
        val longitude = places.sumOf { it.coordinate.longitude } / places.size
        return CampusCoordinate(latitude, longitude)
    }
}
