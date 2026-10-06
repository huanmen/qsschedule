package cn.qztc.timetable.feature.timetable

internal data class CampusLocationSample(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float = 25f,
    val timeMillis: Long = 0L,
    val provider: String = "gps",
) {
    fun coordinate(): CampusCoordinate = CampusCoordinate(latitude, longitude)
}

internal object CampusLocationFix {
    private const val freshMs = 3 * 60_000L
    private const val usableMs = 30 * 60_000L
    private const val nearCampusMeters = 1_800.0

    fun toMapCoordinate(
        sample: CampusLocationSample,
        bounds: CampusMapBounds,
        boundary: List<CampusCoordinate> = emptyList(),
    ): CampusCoordinate {
        val raw = sample.coordinate()
        val fromGcj = CampusAmapProjection.gcj02ToWgs84(raw)
        val gps = sample.provider.equals("gps", ignoreCase = true)
        val rawRank = containmentRank(raw, bounds, boundary)
        val convertedRank = containmentRank(fromGcj, bounds, boundary)
        return when {
            convertedRank < rawRank -> fromGcj
            rawRank < convertedRank -> raw
            gps -> raw
            else -> fromGcj
        }
    }

    fun pick(
        samples: List<CampusLocationSample>,
        bounds: CampusMapBounds,
        nowMillis: Long,
        boundary: List<CampusCoordinate> = emptyList(),
    ): CampusLocationSample? {
        if (samples.isEmpty()) return null
        val fresh = samples.filter { ageMillis(it, nowMillis) in 0..freshMs }
        val pool = fresh.ifEmpty { samples.filter { ageMillis(it, nowMillis) in 0..usableMs } }
        if (pool.isEmpty()) return null
        val mapped = pool.map { it to toMapCoordinate(it, bounds, boundary) }
        val onCampus = mapped.filter { (_, coordinate) ->
            containmentRank(coordinate, bounds, boundary) <= 1
        }
        val ranked = (onCampus.ifEmpty { mapped }).sortedWith(
            compareBy<Pair<CampusLocationSample, CampusCoordinate>> { (_, coordinate) ->
                containmentRank(coordinate, bounds, boundary)
            }.thenBy { (sample, _) -> if (sample.provider.equals("gps", ignoreCase = true)) 0 else 1 }
                .thenBy { (sample, _) -> sample.accuracyMeters }
                .thenBy { (_, coordinate) -> distanceToCampus(coordinate, bounds) },
        )
        return ranked.firstOrNull()?.first
    }

    fun isNearCampus(coordinate: CampusCoordinate, bounds: CampusMapBounds): Boolean {
        if (bounds.contains(coordinate, paddingRatio = 0.25)) return true
        return distanceToCampus(coordinate, bounds) <= nearCampusMeters
    }

    internal fun pointInPolygon(point: CampusCoordinate, polygon: List<CampusCoordinate>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var previous = polygon.lastIndex
        for (index in polygon.indices) {
            val current = polygon[index]
            val last = polygon[previous]
            val crosses = (current.latitude > point.latitude) != (last.latitude > point.latitude)
            if (crosses) {
                val longitudeAtLat = (last.longitude - current.longitude) *
                    (point.latitude - current.latitude) /
                    (last.latitude - current.latitude).let { if (kotlin.math.abs(it) < 1e-12) 1e-12 else it } +
                    current.longitude
                if (point.longitude < longitudeAtLat) inside = !inside
            }
            previous = index
        }
        return inside
    }

    private fun containmentRank(
        coordinate: CampusCoordinate,
        bounds: CampusMapBounds,
        boundary: List<CampusCoordinate>,
    ): Int {
        if (boundary.size >= 3 && pointInPolygon(coordinate, boundary)) return 0
        if (bounds.contains(coordinate, paddingRatio = 0.0)) return 1
        if (bounds.contains(coordinate, paddingRatio = 0.25)) return 2
        if (isNearCampus(coordinate, bounds)) return 3
        return 4
    }

    private fun ageMillis(sample: CampusLocationSample, nowMillis: Long): Long {
        if (sample.timeMillis <= 0L) return Long.MAX_VALUE
        return nowMillis - sample.timeMillis
    }

    private fun distanceToCampus(coordinate: CampusCoordinate, bounds: CampusMapBounds): Double {
        val center = CampusCoordinate(
            (bounds.minLatitude + bounds.maxLatitude) / 2,
            (bounds.minLongitude + bounds.maxLongitude) / 2,
        )
        return CampusRouteGraph.distanceMeters(coordinate, center)
    }
}
