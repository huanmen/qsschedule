package cn.qztc.timetable.feature.timetable

/**
 * Ranks campus places as the user types. Hanzi, aliases and pinyin match together;
 * there is no separate pinyin mode.
 */
internal object CampusPlaceSearch {
    fun match(query: String, places: List<CampusPoint>, limit: Int = 8): List<CampusPoint> {
        val raw = query.trim()
        if (raw.isBlank()) return emptyList()
        val normalized = CampusPlaceResolver.normalize(raw)
        val building = CampusPlaceResolver.buildingName(normalized)
        val needles = linkedSetOf(
            raw.lowercase(),
            normalized.lowercase(),
            building.lowercase(),
            CampusPinyin.compact(raw),
        ).filter { it.isNotBlank() }

        return places
            .mapNotNull { place ->
                val score = needles.maxOf { needle -> score(needle, place) }
                if (score <= 0) null else place to score
            }
            .sortedWith(compareByDescending<Pair<CampusPoint, Int>> { it.second }.thenBy { it.first.name.length })
            .take(limit)
            .map { it.first }
    }

    internal fun score(needle: String, place: CampusPoint): Int {
        val names = (listOf(place.name) + place.aliases).distinct()
        return names.maxOf { candidate -> maxOf(fieldScore(needle, candidate.lowercase()), pinyinScore(needle, candidate)) }
    }

    private fun fieldScore(needle: String, candidate: String): Int = when {
        candidate == needle -> 100
        candidate.startsWith(needle) -> 90
        needle.startsWith(candidate) && candidate.length >= 2 -> 82
        candidate.contains(needle) -> 70
        needle.contains(candidate) && candidate.length >= 2 -> 60
        isSubsequence(needle, candidate) -> 40
        else -> 0
    }

    private fun pinyinScore(needle: String, candidate: String): Int {
        val compact = CampusPinyin.compact(needle)
        if (compact.isEmpty()) return 0
        val letters = compact.takeWhile { it.isLetter() }
        val full = CampusPinyin.full(candidate)
        val initials = CampusPinyin.initials(candidate)
        return when {
            full == compact || initials == compact -> 92
            full.startsWith(compact) || initials.startsWith(compact) -> 84
            letters.length >= 2 && (full.startsWith(letters) || initials.startsWith(letters)) -> 80
            CampusPinyin.matches(needle, candidate) -> 72
            else -> 0
        }
    }

    private fun isSubsequence(needle: String, candidate: String): Boolean {
        if (needle.length < 2) return false
        var index = 0
        for (character in candidate) {
            if (character == needle[index]) {
                index += 1
                if (index == needle.length) return true
            }
        }
        return false
    }
}

internal fun CampusPoint.asSearchResult(): CampusSearchResult = CampusSearchResult(
    id = id,
    name = name,
    address = aliases.firstOrNull().orEmpty(),
    type = "campus",
    latitude = coordinate.latitude,
    longitude = coordinate.longitude,
)

internal fun mergeCampusSearchResults(
    local: List<CampusPoint>,
    remote: List<CampusSearchResult>,
    limit: Int = 8,
): List<CampusSearchResult> {
    val seen = linkedSetOf<String>()
    val merged = ArrayList<CampusSearchResult>(limit)
    fun add(result: CampusSearchResult) {
        val key = CampusPlaceResolver.normalize(result.name).lowercase()
        if (key.isBlank() || !seen.add(key) || merged.size >= limit) return
        merged += result
    }
    local.forEach { add(it.asSearchResult()) }
    remote.forEach(::add)
    return merged
}
