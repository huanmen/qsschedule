package cn.qztc.timetable.feature.timetable

import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

internal object CampusAmapProjection {
    private const val pi = Math.PI
    private const val axis = 6378245.0
    private const val eccentricity = 0.00669342162296594323

    fun wgs84ToGcj02(point: CampusCoordinate): CampusCoordinate {
        if (outOfChina(point)) return point
        val latitudeDelta = transformLatitude(point.longitude - 105.0, point.latitude - 35.0)
        val longitudeDelta = transformLongitude(point.longitude - 105.0, point.latitude - 35.0)
        val radians = point.latitude / 180.0 * pi
        val magic = 1 - eccentricity * sin(radians).pow(2)
        val sqrtMagic = kotlin.math.sqrt(magic)
        return CampusCoordinate(
            point.latitude + (latitudeDelta * 180.0) / ((axis * (1 - eccentricity)) / (magic * sqrtMagic) * pi),
            point.longitude + (longitudeDelta * 180.0) / (axis / sqrtMagic * cos(radians) * pi),
        )
    }

    fun gcj02ToWgs84(point: CampusCoordinate): CampusCoordinate {
        if (outOfChina(point)) return point
        val converted = wgs84ToGcj02(point)
        return CampusCoordinate(
            point.latitude * 2 - converted.latitude,
            point.longitude * 2 - converted.longitude,
        )
    }

    fun mercatorY(latitude: Double): Double = ln((1 + sin(Math.toRadians(latitude))) / (1 - sin(Math.toRadians(latitude)))) / (4 * pi)

    fun pixelsPerLongitude(zoom: Int): Double = 256.0 * 2.0.pow(zoom) / 360.0

    fun pixelsPerLatitude(latitude: Double, zoom: Int): Double = pixelsPerLongitude(zoom) / cos(Math.toRadians(latitude)).coerceAtLeast(.2)

    private fun outOfChina(point: CampusCoordinate): Boolean =
        point.longitude < 72.004 || point.longitude > 137.8347 || point.latitude < 0.8293 || point.latitude > 55.8271

    private fun transformLatitude(x: Double, y: Double): Double {
        var value = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * kotlin.math.sqrt(kotlin.math.abs(x))
        value += (20.0 * sin(6.0 * x * pi) + 20.0 * sin(2.0 * x * pi)) * 2.0 / 3.0
        value += (20.0 * sin(y * pi) + 40.0 * sin(y / 3.0 * pi)) * 2.0 / 3.0
        value += (160.0 * sin(y / 12.0 * pi) + 320.0 * sin(y * pi / 30.0)) * 2.0 / 3.0
        return value
    }

    private fun transformLongitude(x: Double, y: Double): Double {
        var value = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * kotlin.math.sqrt(kotlin.math.abs(x))
        value += (20.0 * sin(6.0 * x * pi) + 20.0 * sin(2.0 * x * pi)) * 2.0 / 3.0
        value += (20.0 * sin(x * pi) + 40.0 * sin(x / 3.0 * pi)) * 2.0 / 3.0
        value += (150.0 * sin(x / 12.0 * pi) + 300.0 * sin(x / 30.0 * pi)) * 2.0 / 3.0
        return value
    }
}
