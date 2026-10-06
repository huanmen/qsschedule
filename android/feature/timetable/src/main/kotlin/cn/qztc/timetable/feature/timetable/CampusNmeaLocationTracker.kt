package cn.qztc.timetable.feature.timetable

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.location.OnNmeaMessageListener
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat

/** Reads a hardware GNSS fix without trusting a replaced Location provider. */
@SuppressLint("MissingPermission")
internal class CampusNmeaLocationTracker(
    context: Context,
    private val onFix: (Location) -> Unit,
) {
    private val context = context.applicationContext
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var running = false
    private var messageSeen = false
    private val listener = OnNmeaMessageListener { message, _ ->
        if (!running) return@OnNmeaMessageListener
        if (!messageSeen) {
            messageSeen = true
            Log.d(TAG, "received first hardware GNSS sentence")
        }
        parseNmeaFix(message)?.let { fix ->
            Log.d(TAG, "received valid hardware GNSS fix accuracy=${fix.accuracyMeters}")
            onFix(Location("nmea").apply {
                latitude = fix.latitude
                longitude = fix.longitude
                accuracy = fix.accuracyMeters
                fix.altitudeMeters?.let { altitude = it }
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            })
        }
    }

    fun start() {
        if (running || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        messageSeen = false
        running = runCatching { manager.addNmeaListener(listener, mainHandler) }
            .onFailure { Log.d(TAG, "hardware GNSS listener registration failed", it) }
            .getOrDefault(false)
        Log.d(TAG, "hardware GNSS listener registered=$running")
    }

    fun stop() {
        if (!running) return
        running = false
        runCatching { manager.removeNmeaListener(listener) }
    }

    private companion object {
        const val TAG = "CampusNmeaTracker"
    }
}

internal data class NmeaFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val altitudeMeters: Double?,
)

internal fun parseNmeaFix(sentence: String): NmeaFix? {
    val trimmed = sentence.trim()
    if (!hasValidNmeaChecksum(trimmed)) return null
    val fields = trimmed.substringBefore('*').split(',')
    if (fields.firstOrNull()?.takeLast(3) != "GGA" || fields.size < 10) return null
    val quality = fields[6].toIntOrNull() ?: return null
    if (quality <= 0) return null
    val latitude = parseNmeaCoordinate(fields[2], fields[3], latitude = true) ?: return null
    val longitude = parseNmeaCoordinate(fields[4], fields[5], latitude = false) ?: return null
    val hdop = fields[8].toFloatOrNull()?.takeIf { it.isFinite() && it > 0f } ?: 10f
    val altitude = fields[9].toDoubleOrNull()?.takeIf(Double::isFinite)
    return NmeaFix(latitude, longitude, (hdop * 5f).coerceIn(3f, 100f), altitude)
}

private fun parseNmeaCoordinate(value: String, hemisphere: String, latitude: Boolean): Double? {
    val raw = value.toDoubleOrNull() ?: return null
    val degrees = kotlin.math.floor(raw / 100.0)
    val coordinate = degrees + (raw - degrees * 100.0) / 60.0
    val signed = when (hemisphere.uppercase()) {
        "N", "E" -> coordinate
        "S", "W" -> -coordinate
        else -> return null
    }
    val range = if (latitude) -90.0..90.0 else -180.0..180.0
    return signed.takeIf { it in range }
}

private fun hasValidNmeaChecksum(sentence: String): Boolean {
    if (!sentence.startsWith('$')) return false
    val star = sentence.indexOf('*')
    if (star <= 1 || star + 2 >= sentence.length) return false
    val expected = sentence.substring(star + 1, star + 3).toIntOrNull(16) ?: return false
    var actual = 0
    for (index in 1 until star) actual = actual xor sentence[index].code
    return actual == expected
}
