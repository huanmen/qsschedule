package cn.qztc.timetable.feature.timetable

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.location.LocationCompat
import androidx.core.location.LocationManagerCompat
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption

/** Runs independent foreground providers together and publishes only the best trustworthy fix. */
internal class CampusPositionTracker(
    context: Context,
    private val useAmap: Boolean,
    private val onFix: (Location, CampusCoordinateSystem, Boolean) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onUnavailable: () -> Unit,
) {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var running = false
    private var client: AMapLocationClient? = null
    private var native: CampusLocationTracker? = null
    private var fused: CampusFusedLocationTracker? = null
    private var nmea: CampusNmeaLocationTracker? = null
    private var last: CampusLiveFix? = null
    private var mockSeen = false
    private var cachedShown = false
    private val locationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }
    private val timeout = Runnable {
        if (!running || last != null || cachedShown) return@Runnable
        onUnavailable()
        onStatus(
            if (mockSeen) "正在获取可用的真实位置；也可以手动选择起点"
            else "尚未取得当前位置，正在继续定位；也可以手动选择起点",
        )
    }

    fun start() {
        if (running) return
        running = true
        val manager = locationManager
        val precise = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!precise && !coarse) {
            onUnavailable()
            onStatus("未授予定位权限，可以手动选择起点")
            return
        }
        if (!LocationManagerCompat.isLocationEnabled(manager)) {
            onUnavailable()
            onStatus("系统定位已关闭，可以手动选择起点")
            return
        }
        onStatus("正在更新当前位置")
        native = CampusLocationTracker(
            context = context,
            onFix = { deliver(it, CampusCoordinateSystem.WGS84) },
            onStatus = { message -> if (running && last == null && !cachedShown) onStatus(message) },
            onUnavailable = {},
        ).also { it.start() }
        fused = CampusFusedLocationTracker(
            context = context,
            onFix = { deliver(it, CampusCoordinateSystem.WGS84) },
            onCachedFix = { deliver(it, CampusCoordinateSystem.WGS84, allowStale = true) },
        ).also { it.start() }
        if (precise) {
            nmea = CampusNmeaLocationTracker(context) { deliver(it, CampusCoordinateSystem.WGS84) }
                .also { it.start() }
        }
        if (useAmap && precise && hasKey(context)) startAmap()
        main.postDelayed(timeout, 25_000L)
    }

    private fun startAmap() {
        runCatching {
            // Only reached after the map's explicit, persisted privacy choice.
            AMapLocationClient.updatePrivacyShow(context, true, true)
            AMapLocationClient.updatePrivacyAgree(context, true)
            AMapLocationClientOption.setLocationProtocol(AMapLocationClientOption.AMapLocationProtocol.HTTPS)
            val active = AMapLocationClient(context)
            client = active
            active.setLocationOption(AMapLocationClientOption().apply {
                // Ask AMap for GNSS-assisted positioning when the device can provide it.
                // Battery-saving mode consistently returned a broad ~100 m network fix
                // indoors, which made the marker look plausible but disabled reliable
                // campus route snapping.
                setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy)
                setInterval(2_000L)
                setOnceLocation(false)
                setOnceLocationLatest(true)
                setWifiScan(true)
                setLBSLocationEnable(true)
                setSysNetworkLocEnable(false)
                setNeedAddress(false)
                setLocationCacheEnable(false)
                setHttpTimeOut(15_000L)
                setMockEnable(false)
            })
            active.setLocationListener { result ->
                main.post {
                    if (!running || client !== active) return@post
                    Log.d(
                        TAG,
                        "amap callback code=${result?.errorCode ?: -2} " +
                            "type=${result?.locationType ?: -1} coord=${result?.coordType ?: "unknown"} " +
                            "mock=${result?.isMock() == true || (result != null && LocationCompat.isMock(result))} " +
                            "accuracy=${result?.accuracy ?: Float.NaN}",
                    )
                    if (result == null || result.errorCode != 0) {
                        val code = result?.errorCode
                        releaseAmap()
                        if (last == null && !cachedShown) onStatus("高德定位暂不可用，正在使用系统定位")
                        return@post
                    }
                    if (result.isMock() || LocationCompat.isMock(result) || isSameAsSystemMock(result)) {
                        mockSeen = true
                        releaseAmap()
                        if (last == null && !cachedShown) onStatus("正在获取可用的真实位置")
                        return@post
                    }
                    val system = when (result.coordType) {
                        "GCJ02" -> CampusCoordinateSystem.GCJ02
                        "WGS84" -> CampusCoordinateSystem.WGS84
                        else -> {
                            releaseAmap()
                            if (last == null && !cachedShown) onStatus("高德坐标异常，正在使用系统定位")
                            return@post
                        }
                    }
                    val location = result.toFrameworkLocation()
                    // AMap may return a successful but unrelated network/mock
                    // fix while the system fused provider has the campus fix.
                    // Do not let that result clear or replace the campus marker.
                    deliver(location, system, rejectOutsideCampus = true)
                }
            }
            active.startLocation()
        }.onFailure {
            releaseAmap()
            if (last == null && !cachedShown) onStatus("高德定位启动失败，正在使用系统定位")
        }
    }

    @SuppressLint("MissingPermission")
    private fun isSameAsSystemMock(location: Location): Boolean {
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }
            .any { candidate ->
                LocationCompat.isMock(candidate) &&
                    runCatching { candidate.distanceTo(location) <= 1_000f }.getOrDefault(false)
            }
    }

    @SuppressLint("MissingPermission")
    private fun isUntrustedSystemFix(location: Location): Boolean {
        if (location.isMockOrUntrustedDerivedNetwork()) return true
        if (!location.provider.equals("fused", ignoreCase = true)) return false
        // Some OEM callbacks strip the extras before delivering the fix. Read
        // the provider snapshot as a second source of trust metadata.
        return runCatching { locationManager.getLastKnownLocation("fused") }
            .getOrNull()
            ?.isMockOrUntrustedDerivedNetwork() == true
    }

    private fun deliver(
        location: Location,
        system: CampusCoordinateSystem,
        allowStale: Boolean = false,
        rejectOutsideCampus: Boolean = false,
    ) {
        if (!running) return
        if (rejectOutsideCampus && !isInsideCampusBounds(location)) {
            Log.d(TAG, "amap result rejected outsideCampus=true")
            if (last == null && !cachedShown) onStatus("正在获取可用的真实位置")
            return
        }
        if (isUntrustedSystemFix(location)) {
            mockSeen = true
            Log.d(TAG, "rejected provider=${location.provider ?: "unknown"} untrustedDerivedNetwork=true")
            if (last == null && !cachedShown) onStatus("正在获取可用的真实位置")
            return
        }
        val fix = location.liveFix()
        Log.d(TAG, "candidate provider=${location.provider ?: "unknown"} mock=${fix.isMock} cached=$allowStale age=${(nowAge(fix))} accuracy=${fix.accuracy}")
        if (fix.isMock) {
            mockSeen = true
            if (last == null && !cachedShown) onStatus("正在获取可用的真实位置")
            return
        }
        if (allowStale) {
            if (!isInsideCampusBounds(location) || !fix.accuracy.isFinite() || fix.accuracy <= 0f || fix.accuracy > 10_000f) return
            cachedShown = true
            onFix(Location(location), system, true)
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (!CampusLiveFixPolicy.shouldReplace(last, fix, now)) {
            return
        }
        last = fix
        cachedShown = false
        main.removeCallbacks(timeout)
        Log.d(TAG, "accepted provider=${location.provider ?: "unknown"} cached=false")
        onFix(Location(location), system, false)
    }

    private fun nowAge(fix: CampusLiveFix): Long =
        (SystemClock.elapsedRealtime() - fix.elapsedMillis).coerceAtLeast(0L)

    private fun isInsideCampusBounds(location: Location): Boolean =
        location.latitude in 24.85..24.89 && location.longitude in 118.65..118.69

    private fun Location.toFrameworkLocation(): Location {
        val source = this
        return Location("amap").apply {
            // AMapLocation keeps several values outside Location's private fields on
            // some SDK/device combinations, so Location(source) can silently copy 0,0.
            latitude = source.latitude
            longitude = source.longitude
            if (source.hasAccuracy()) accuracy = source.accuracy
            if (source.hasAltitude()) altitude = source.altitude
            if (source.hasBearing()) bearing = source.bearing
            if (source.hasSpeed()) speed = source.speed
            time = source.time.takeIf { it > 0L } ?: System.currentTimeMillis()
            extras = source.extras?.let(::Bundle)
            // Network fixes may not populate elapsedRealtimeNanos. Preserve the
            // observation time rather than marking old data as fresh.
            elapsedRealtimeNanos = locationElapsedMillis(
                source.elapsedRealtimeNanos,
                time,
                SystemClock.elapsedRealtime(),
                System.currentTimeMillis(),
            ) * 1_000_000L
        }
    }

    private fun releaseAmap() {
        val previous = client
        client = null
        runCatching { previous?.stopLocation() }
        runCatching { previous?.onDestroy() }
    }

    fun stop() {
        running = false
        releaseAmap()
        native?.stop()
        native = null
        fused?.stop()
        fused = null
        nmea?.stop()
        nmea = null
        last = null
        mockSeen = false
        cachedShown = false
        main.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val TAG = "CampusPositionTracker"
        @Suppress("DEPRECATION")
        fun hasKey(context: Context): Boolean = runCatching {
            context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                .metaData?.getString("com.amap.api.v2.apikey").orEmpty().isNotBlank()
        }.getOrDefault(false)
    }
}
