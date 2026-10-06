package cn.qztc.timetable.feature.timetable

import java.util.PriorityQueue
import kotlin.math.roundToInt

/**
 * Walks a snapped campus road/path graph. Nearby junctions are merged so
 * incomplete OSM extracts still form a walkable offline network.
 */
internal object CampusWalkRouter {
    private const val snapMeters = 140.0
    private const val joinMeters = 48.0
    private const val gridPerDegree = 12_000.0

    @Volatile
    private var cached: Pair<Int, WalkGraph>? = null

    fun route(
        dataset: CampusMapDataset,
        origin: CampusCoordinate,
        destination: CampusCoordinate,
    ): CampusMapRoute {
        val direct = CampusRouteGraph.distanceMeters(origin, destination)
        if (direct < 8) {
            return CampusMapRoute(
                points = listOf(origin, destination),
                distanceMeters = 0,
                durationSeconds = 0,
                steps = listOf(CampusRouteStep("已在目的地附近")),
            )
        }
        val graph = graphFor(dataset)
        val path = graph.shortest(origin, destination)
        val points = path ?: listOf(origin, destination)
        val distance = points.zipWithNext().sumOf { CampusRouteGraph.distanceMeters(it.first, it.second) }
            .toInt()
            .coerceAtLeast(1)
        val duration = (distance / 1.25).toInt().coerceAtLeast(1)
        return CampusMapRoute(
            points = points,
            distanceMeters = distance,
            durationSeconds = duration,
            steps = listOf(
                CampusRouteStep(
                    instruction = if (path != null) "沿校园道路步行前往" else "校园路网暂未连通，按直线示意",
                    distanceMeters = distance,
                    durationSeconds = duration,
                ),
            ),
        )
    }

    internal fun graphFor(dataset: CampusMapDataset): WalkGraph {
        val key = dataset.features.size * 31 + dataset.bounds.hashCode()
        cached?.let { if (it.first == key) return it.second }
        val graph = WalkGraph.build(dataset)
        cached = key to graph
        return graph
    }

    internal class WalkGraph(
        private val nodes: Map<String, CampusCoordinate>,
        private val adjacency: Map<String, List<Pair<String, Double>>>,
    ) {
        fun shortest(origin: CampusCoordinate, destination: CampusCoordinate): List<CampusCoordinate>? {
            val start = nearest(origin) ?: return null
            val end = nearest(destination) ?: return null
            if (start == end) return listOf(origin, destination)
            val previous = linkedMapOf<String, String>()
            val best = hashMapOf(start to 0.0)
            val queue = PriorityQueue<Pair<Double, String>>(compareBy { it.first })
            queue.add(0.0 to start)
            while (queue.isNotEmpty()) {
                val current = queue.poll() ?: break
                val (cost, node) = current
                if (node == end) break
                if (cost > (best[node] ?: Double.POSITIVE_INFINITY)) continue
                adjacency[node].orEmpty().forEach { (next, meters) ->
                    val nextCost = cost + meters
                    if (nextCost < (best[next] ?: Double.POSITIVE_INFINITY)) {
                        best[next] = nextCost
                        previous[next] = node
                        queue.add(nextCost to next)
                    }
                }
            }
            if (end !in previous && start != end) return null
            val ids = ArrayDeque<String>()
            var cursor: String? = end
            while (cursor != null) {
                ids.addFirst(cursor)
                if (cursor == start) break
                cursor = previous[cursor]
            }
            if (ids.firstOrNull() != start) return null
            val coordinates = ids.mapNotNull(nodes::get).toMutableList()
            if (coordinates.firstOrNull() != origin) coordinates.add(0, origin)
            if (coordinates.lastOrNull() != destination) coordinates.add(destination)
            return coordinates
        }

        private fun nearest(point: CampusCoordinate): String? {
            var bestId: String? = null
            var bestDistance = snapMeters
            nodes.forEach { (id, coordinate) ->
                val distance = CampusRouteGraph.distanceMeters(point, coordinate)
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestId = id
                }
            }
            if (bestId != null) return bestId
            return nodes.minByOrNull { CampusRouteGraph.distanceMeters(point, it.value) }
                ?.takeIf { CampusRouteGraph.distanceMeters(point, it.value) <= 220.0 }
                ?.key
        }

        companion object {
            fun build(dataset: CampusMapDataset): WalkGraph {
                val nodes = linkedMapOf<String, CampusCoordinate>()
                val edges = linkedMapOf<String, MutableList<Pair<String, Double>>>()

                fun nodeId(coordinate: CampusCoordinate): String {
                    val lat = (coordinate.latitude * gridPerDegree).roundToInt()
                    val lon = (coordinate.longitude * gridPerDegree).roundToInt()
                    return "$lat,$lon"
                }

                fun addNode(coordinate: CampusCoordinate): String {
                    val id = nodeId(coordinate)
                    nodes.putIfAbsent(id, coordinate)
                    return id
                }

                fun addEdge(from: String, to: String) {
                    if (from == to) return
                    val fromPoint = nodes[from] ?: return
                    val toPoint = nodes[to] ?: return
                    val meters = CampusRouteGraph.distanceMeters(fromPoint, toPoint)
                    if (meters <= 0.2) return
                    edges.getOrPut(from) { mutableListOf() }.add(to to meters)
                    edges.getOrPut(to) { mutableListOf() }.add(from to meters)
                }

                dataset.features
                    .filter { it.kind == "road" || it.kind == "path" }
                    .forEach { feature ->
                        if (feature.points.size < 2) return@forEach
                        val ids = feature.points.map(::addNode)
                        for (index in 0 until ids.lastIndex) {
                            addEdge(ids[index], ids[index + 1])
                        }
                    }

                val indexed = nodes.entries.toList()
                val buckets = linkedMapOf<Long, MutableList<Int>>()
                indexed.forEachIndexed { index, (_, coordinate) ->
                    buckets.getOrPut(bucketKey(coordinate)) { mutableListOf() }.add(index)
                }
                indexed.forEachIndexed { index, (id, coordinate) ->
                    nearbyBuckets(coordinate).forEach { key ->
                        buckets[key].orEmpty().forEach { otherIndex ->
                            if (otherIndex <= index) return@forEach
                            val (otherId, other) = indexed[otherIndex]
                            if (CampusRouteGraph.distanceMeters(coordinate, other) <= joinMeters) {
                                addEdge(id, otherId)
                            }
                        }
                    }
                }

                return WalkGraph(nodes, edges)
            }

            private fun bucketKey(coordinate: CampusCoordinate): Long {
                val lat = (coordinate.latitude * 4000).toLong()
                val lon = (coordinate.longitude * 4000).toLong()
                return (lat shl 32) xor lon
            }

            private fun nearbyBuckets(coordinate: CampusCoordinate): List<Long> {
                val lat = (coordinate.latitude * 4000).toLong()
                val lon = (coordinate.longitude * 4000).toLong()
                return listOf(
                    (lat shl 32) xor lon,
                    ((lat + 1) shl 32) xor lon,
                    ((lat - 1) shl 32) xor lon,
                    (lat shl 32) xor (lon + 1),
                    (lat shl 32) xor (lon - 1),
                )
            }
        }
    }
}
