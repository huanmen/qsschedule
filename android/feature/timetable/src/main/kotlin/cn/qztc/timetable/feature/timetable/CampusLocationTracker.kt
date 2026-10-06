package cn.qztc.timetable.feature.timetable

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationCompat
import androidx.core.os.CancellationSignal

internal fun Location.liveFix(
    nowElapsedMillis: Long = SystemClock.elapsedRealtime(),
    nowWallMillis: Long = System.currentTimeMillis(),
) = CampusLiveFix(
    latitude = latitude,
    longitude = longitude,
    accuracy = if (hasAccuracy()) accuracy else Float.NaN,
    // Some OEM providers and test providers leave elapsedRealtimeNanos unset.
    // Fall back to wall-clock age instead of discarding an otherwise valid fix.
    elapsedMillis = locationElapsedMillis(elapsedRealtimeNanos, time, nowElapsedMillis, nowWallMillis),
    isMock = LocationCompat.isMock(this),
)

internal fun locationElapsedMillis(
    elapsedRealtimeNanos: Long,
    wallTimeMillis: Long,
    nowElapsedMillis: Long,
    nowWallMillis: Long,
): Long = elapsedRealtimeNanos.takeIf { it > 0L }?.div(1_000_000L)
    ?: nowElapsedMillis - (nowWallMillis - wallTimeMillis).coerceAtLeast(0L)

/** Foreground-only system positioning. No coordinates are persisted or logged. */
@SuppressLint("MissingPermission")
internal class CampusLocationTracker(
    private val context: Context,
    private val onFix: (Location) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onUnavailable: () -> Unit,
) {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private val requests = mutableListOf<CancellationSignal>()
    private var running = false
    private var generation = 0
    private var last: Location? = null
    private var reportedMock = false
    private val retry = Runnable {
        if (running) refresh()
    }
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) { deliver(location) }
        override fun onProviderEnabled(provider: String) { handler.post { refresh() } }
        override fun onProviderDisabled(provider: String) { handler.post { refresh() } }
        @Deprecated("Deprecated by Android")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refresh() }
    }
    private val timeout = Runnable {
        if (running && last?.let { CampusLiveFixPolicy.canNavigate(it.liveFix(), SystemClock.elapsedRealtime(), precise()) } != true) {
            onStatus(if (!precise()) "当前仅允许大致位置，请在应用权限中开启精确定位" else
                "尚未取得精确位置，正在继续定位；可到开阔处重试或手动选择起点")
        }
        requests.toList().forEach { it.cancel() }; requests.clear()
        runCatching { manager.removeUpdates(listener) }
        // Keep retrying while the map is visible. A temporary lack of GNSS or
        // network data must not make the user press the locate button again.
        if (running) handler.postDelayed(retry, 4_000L)
    }

    private fun precise() = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun start() {
        if (running) return
        running = true
        ContextCompat.registerReceiver(context, receiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
        refresh()
    }

    fun refresh() {
        if (!running) return
        val current = ++generation
        handler.removeCallbacks(timeout)
        handler.removeCallbacks(retry)
        requests.toList().forEach { it.cancel() }; requests.clear()
        runCatching { manager.removeUpdates(listener) }
        val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!coarse && !precise()) {
            onUnavailable(); onStatus("未授予定位权限，可以手动选择起点"); return
        }
        if (!LocationManagerCompat.isLocationEnabled(manager)) {
            last = null; onUnavailable(); onStatus("系统定位已关闭，请开启后重试"); return
        }
        val providers = listOf("fused", LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { (it != LocationManager.GPS_PROVIDER || precise()) &&
                runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) {
            onUnavailable(); onStatus("没有可用的定位服务，请检查系统定位设置"); return
        }
        onStatus(if (precise()) "正在更新当前位置" else "当前仅允许大致位置，正在获取位置范围")
        providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .sortedBy { it.elapsedRealtimeNanos }.forEach(::deliver)
        var subscriptions = 0
        for (provider in providers) {
            runCatching { manager.requestLocationUpdates(provider, 1_000L, 0f, listener, Looper.getMainLooper()) }
                .onSuccess { subscriptions++ }
            val signal = CancellationSignal().also(requests::add)
            runCatching {
                LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) { fix ->
                    requests.remove(signal)
                    if (running && current == generation && fix != null) deliver(fix)
                }
            }.onFailure { requests.remove(signal); signal.cancel() }
        }
        if (subscriptions == 0) onStatus("无法订阅定位更新，请检查精确定位权限或系统定位服务")
        handler.postDelayed(timeout, 25_000L)
    }

    private fun deliver(location: Location) {
        val untrustedSystemFix = location.isMockOrUntrustedDerivedNetwork() ||
            (location.provider.equals("fused", ignoreCase = true) &&
                runCatching { manager.getLastKnownLocation("fused") }
                    .getOrNull()
                    ?.isMockOrUntrustedDerivedNetwork() == true)
        if (running && untrustedSystemFix) {
            // Ignore test-provider fixes inside the app. They can remain registered at
            // the system level even when their source app is no longer visible; users
            // should not have to change device-wide settings for campus positioning.
            if (!reportedMock && last == null) onStatus("正在获取可用的真实位置")
            reportedMock = true
            return
        }
        if (!running || !CampusLiveFixPolicy.shouldReplace(last?.liveFix(), location.liveFix(), SystemClock.elapsedRealtime())) return
        last = Location(location)
        onFix(Location(location))
    }

    fun stop() {
        if (!running) return
        running = false; generation++
        handler.removeCallbacksAndMessages(null)
        requests.toList().forEach { it.cancel() }; requests.clear()
        runCatching { manager.removeUpdates(listener) }
        runCatching { context.unregisterReceiver(receiver) }
    }
}
