package cn.qztc.timetable.feature.timetable

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

/** Foreground-only Google/system fused positioning. Failures are handled by sibling providers. */
@SuppressLint("MissingPermission")
internal class CampusFusedLocationTracker(
    context: Context,
    private val onFix: (Location) -> Unit,
    private val onCachedFix: (Location) -> Unit,
) {
    private val context = context.applicationContext
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }
    private var running = false
    private var currentRequest: CancellationTokenSource? = null
    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            if (!running) return
            result.locations.forEach(onFix)
        }
    }

    fun start() {
        if (running) return
        val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return
        running = true

        runCatching {
            client.lastLocation.addOnSuccessListener { location ->
                if (running && location != null) onCachedFix(location)
            }

            val priority = if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
            val currentLocationRequest = CurrentLocationRequest.Builder()
                .setPriority(priority)
                .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                .setMaxUpdateAgeMillis(0L)
                .setDurationMillis(15_000L)
                .build()
            currentRequest = CancellationTokenSource().also { source ->
                client.getCurrentLocation(currentLocationRequest, source.token).addOnSuccessListener { location ->
                    if (running && location != null) onFix(location)
                }
            }

            val request = LocationRequest.Builder(priority, 2_000L)
                .setMinUpdateIntervalMillis(1_000L)
                .setMaxUpdateDelayMillis(4_000L)
                .setMinUpdateDistanceMeters(0f)
                .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                .setWaitForAccurateLocation(true)
                .build()
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        }.onFailure { stop() }
    }

    fun stop() {
        if (!running) return
        running = false
        currentRequest?.cancel()
        currentRequest = null
        runCatching { client.removeLocationUpdates(callback) }
    }
}
