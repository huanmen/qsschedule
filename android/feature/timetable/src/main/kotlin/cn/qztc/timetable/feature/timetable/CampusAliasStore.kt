package cn.qztc.timetable.feature.timetable

import android.content.Context
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class CampusPlaceOverrides(
    val aliases: Map<String, Set<String>> = emptyMap(),
    val hiddenIds: Set<String> = emptySet(),
    val extras: List<CampusCustomPlace> = emptyList(),
    val names: Map<String, String> = emptyMap(),
    val coordinates: Map<String, CampusCoordinate> = emptyMap(),
)

data class CampusCustomPlace(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val aliases: Set<String> = emptySet(),
    val category: String = "other",
) {
    fun toPoint(): CampusPoint = CampusPoint(
        id = id,
        name = name,
        coordinate = CampusCoordinate(latitude, longitude),
        aliases = aliases,
        category = category,
    )
}

/** User-defined names, hidden places and extra markers. Stored only on this device. */
class CampusAliasStore(context: Context) {
    private val filesDir = context.applicationContext.filesDir
    private val aliasFile = File(filesDir, "campus-place-aliases.json")
    private val overrideFile = File(filesDir, "campus-place-overrides.json")
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): Map<String, Set<String>> = loadOverrides().aliases

    fun loadOverrides(): CampusPlaceOverrides {
        if (overrideFile.isFile) {
            return runCatching {
                json.decodeFromString(OverrideFile.serializer(), overrideFile.readText(Charsets.UTF_8)).toOverrides()
            }.getOrDefault(CampusPlaceOverrides())
        }
        val aliases = runCatching {
            if (!aliasFile.isFile) return CampusPlaceOverrides()
            json.decodeFromString(AliasFile.serializer(), aliasFile.readText(Charsets.UTF_8))
                .aliases
                .mapValues { (_, names) -> names.map { it.trim() }.filter { it.isNotEmpty() }.toSet() }
                .filterValues { it.isNotEmpty() }
        }.getOrDefault(emptyMap())
        return CampusPlaceOverrides(aliases = aliases)
    }

    fun save(aliases: Map<String, Set<String>>) {
        val current = loadOverrides()
        saveOverrides(current.copy(aliases = aliases))
    }

    fun saveOverrides(overrides: CampusPlaceOverrides) {
        val payload = OverrideFile(
            aliases = overrides.aliases.mapValues { (_, names) ->
                names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            }.filterValues { it.isNotEmpty() },
            hiddenIds = overrides.hiddenIds.filter { it.isNotBlank() }.distinct(),
            names = overrides.names.mapValues { (_, name) -> name.trim() }.filterValues { it.isNotEmpty() },
            coordinates = overrides.coordinates.mapValues { (_, point) ->
                CoordinateFile(point.latitude, point.longitude)
            },
            extras = overrides.extras.map { place ->
                CustomPlaceFile(
                    id = place.id,
                    name = place.name.trim(),
                    latitude = place.latitude,
                    longitude = place.longitude,
                    aliases = place.aliases.map { it.trim() }.filter { it.isNotEmpty() },
                    category = place.category,
                )
            }.filter { it.name.isNotEmpty() },
        )
        overrideFile.writeText(json.encodeToString(OverrideFile.serializer(), payload), Charsets.UTF_8)
        aliasFile.writeText(
            json.encodeToString(AliasFile.serializer(), AliasFile(payload.aliases)),
            Charsets.UTF_8,
        )
    }
}

fun CampusPlaceCatalog.applyOverrides(overrides: CampusPlaceOverrides): CampusPlaceCatalog {
    val aliased = places.map { place ->
        val added = overrides.aliases[place.id].orEmpty()
        val renamed = overrides.names[place.id]?.trim()?.takeIf { it.isNotEmpty() }
        val moved = overrides.coordinates[place.id]
        place.copy(
            name = renamed ?: place.name,
            coordinate = moved ?: place.coordinate,
            aliases = if (added.isEmpty()) place.aliases else place.aliases + added,
        )
    }.filter { it.id !in overrides.hiddenIds }
    val extras = overrides.extras
        .filter { it.id !in overrides.hiddenIds }
        .map { extra ->
            val renamed = overrides.names[extra.id]?.trim()?.takeIf { it.isNotEmpty() }
            val moved = overrides.coordinates[extra.id]
            extra.copy(
                name = renamed ?: extra.name,
                latitude = moved?.latitude ?: extra.latitude,
                longitude = moved?.longitude ?: extra.longitude,
            ).toPoint()
        }
    return CampusPlaceCatalog.fromPoints(aliased + extras)
}

fun CampusPlaceOverrides.renamePlace(id: String, name: String): CampusPlaceOverrides {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return this
    val extraIndex = extras.indexOfFirst { it.id == id }
    return if (extraIndex >= 0) {
        copy(extras = extras.toMutableList().also { it[extraIndex] = extras[extraIndex].copy(name = trimmed) })
    } else {
        copy(names = names + (id to trimmed))
    }
}

fun CampusPlaceOverrides.movePlace(id: String, coordinate: CampusCoordinate): CampusPlaceOverrides {
    val extraIndex = extras.indexOfFirst { it.id == id }
    return if (extraIndex >= 0) {
        copy(
            extras = extras.toMutableList().also {
                it[extraIndex] = extras[extraIndex].copy(latitude = coordinate.latitude, longitude = coordinate.longitude)
            },
        )
    } else {
        copy(coordinates = coordinates + (id to coordinate))
    }
}

fun CampusPlaceOverrides.removePlace(id: String): CampusPlaceOverrides {
    val custom = extras.any { it.id == id }
    return copy(
        extras = extras.filterNot { it.id == id },
        aliases = aliases - id,
        names = names - id,
        coordinates = coordinates - id,
        hiddenIds = if (custom) hiddenIds - id else hiddenIds + id,
    )
}

fun CampusPlaceOverrides.addAlias(id: String, alias: String): CampusPlaceOverrides {
    val trimmed = alias.trim()
    if (trimmed.isEmpty()) return this
    val extraIndex = extras.indexOfFirst { it.id == id }
    return if (extraIndex >= 0) {
        copy(
            extras = extras.toMutableList().also {
                it[extraIndex] = extras[extraIndex].copy(aliases = extras[extraIndex].aliases + trimmed)
            },
        )
    } else {
        copy(aliases = aliases + (id to (aliases[id].orEmpty() + trimmed)))
    }
}

@Serializable
private data class AliasFile(val aliases: Map<String, List<String>> = emptyMap())

@Serializable
private data class OverrideFile(
    val aliases: Map<String, List<String>> = emptyMap(),
    val hiddenIds: List<String> = emptyList(),
    val extras: List<CustomPlaceFile> = emptyList(),
    val names: Map<String, String> = emptyMap(),
    val coordinates: Map<String, CoordinateFile> = emptyMap(),
) {
    fun toOverrides() = CampusPlaceOverrides(
        aliases = aliases
            .mapValues { (_, names) -> names.map { it.trim() }.filter { it.isNotEmpty() }.toSet() }
            .filterValues { it.isNotEmpty() },
        hiddenIds = hiddenIds.map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
        names = names.mapValues { (_, name) -> name.trim() }.filterValues { it.isNotEmpty() },
        coordinates = coordinates.mapValues { (_, point) -> CampusCoordinate(point.latitude, point.longitude) },
        extras = extras.mapNotNull { record ->
            val name = record.name.trim()
            if (name.isEmpty()) null
            else CampusCustomPlace(
                id = record.id.ifBlank { "custom:${name.hashCode()}" },
                name = name,
                latitude = record.latitude,
                longitude = record.longitude,
                aliases = record.aliases.map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
                category = record.category.ifBlank { "other" },
            )
        },
    )
}

@Serializable
private data class CoordinateFile(
    val latitude: Double,
    val longitude: Double,
)

@Serializable
private data class CustomPlaceFile(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val aliases: List<String> = emptyList(),
    val category: String = "other",
)
