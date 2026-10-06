package cn.qztc.timetable.feature.timetable

import android.location.Location
import androidx.core.location.LocationCompat

/**
 * Some Xiaomi builds expose a fused fix as non-mock even when it is actually
 * an AMap/network-derived result. The extras are the only indication available
 * to an app that this fused value is not an independent live fix.
 */
internal fun Location.isMockOrUntrustedDerivedNetwork(): Boolean {
    if (LocationCompat.isMock(this)) return true
    val metadata = extras
    return isDerivedFromAmapNetwork(
        provider = provider,
        flpProvider = metadata?.getString("flpProvider"),
        hasAmapMetadata = metadata?.containsKey("amap") == true ||
            metadata?.containsKey("amap_location_resultCode") == true ||
            metadata?.containsKey("amap_location_result_info") == true,
    )
}

internal fun isDerivedFromAmapNetwork(
    provider: String?,
    flpProvider: String?,
    hasAmapMetadata: Boolean,
): Boolean {
    if (!provider.equals("fused", ignoreCase = true)) return false
    return flpProvider?.lowercase() == "network" && hasAmapMetadata
}
