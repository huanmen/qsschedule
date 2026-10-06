package cn.qztc.timetable.feature.timetable

internal data class CampusLiveFix(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val elapsedMillis: Long,
    val isMock: Boolean = false,
)

internal object CampusLiveFixPolicy {
    const val DISPLAY_MAX_AGE_MS = 60_000L
    const val NAVIGATION_MAX_AGE_MS = 15_000L

    fun canDisplay(fix: CampusLiveFix, now: Long): Boolean =
        !fix.isMock && fix.latitude.isFinite() && fix.latitude in -90.0..90.0 &&
            fix.longitude.isFinite() && fix.longitude in -180.0..180.0 &&
            fix.accuracy.isFinite() && fix.accuracy > 0f && fix.accuracy <= 10_000f &&
            now - fix.elapsedMillis in 0..DISPLAY_MAX_AGE_MS

    fun canNavigate(fix: CampusLiveFix, now: Long, precisePermission: Boolean): Boolean =
        precisePermission && canDisplay(fix, now) && fix.accuracy <= 50f &&
            now - fix.elapsedMillis <= NAVIGATION_MAX_AGE_MS

    fun shouldReplace(previous: CampusLiveFix?, next: CampusLiveFix, now: Long): Boolean {
        if (!canDisplay(next, now)) return false
        if (previous == null || !canDisplay(previous, now)) return true
        if (next.elapsedMillis < previous.elapsedMillis) return false
        // Do not let a later but much worse network fix jump over a fresh GPS or
        // fused fix. Once the old fix expires, the caller can accept a new source.
        // This matters on devices where the network provider briefly returns a
        // broad cell based estimate while GPS is still valid.
        return next.accuracy <= maxOf(50f, previous.accuracy * 2)
    }
}
