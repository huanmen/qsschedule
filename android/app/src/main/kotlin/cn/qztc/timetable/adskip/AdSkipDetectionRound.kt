package cn.qztc.timetable.adskip

/**
 * Serialises one visible advertising scene. A node can be reported by more
 * than one rule (or move while an animation is running), so a stable key is
 * attempted at most once. The round is re-armed only after a verified period
 * with no matching target.
 */
internal class AdSkipDetectionRound(
    private val maxTargets: Int = 3,
    private val retryGapMs: Long = 800,
    private val absenceMs: Long = 1200,
    private val rearmGapMs: Long = 2500,
) {
    private val tried = linkedSetOf<String>()
    private var lastAttemptAt = -10_000L
    private var absentSince = -1L

    fun allows(key: String, now: Long): Boolean =
        tried.size < maxTargets && key !in tried && now - lastAttemptAt >= retryGapMs

    fun attempted(key: String, now: Long) {
        tried += key
        lastAttemptAt = now
        absentSince = -1L
    }

    fun finished(): Boolean = tried.size >= maxTargets

    /** Returns true when the round was re-armed. */
    fun presence(targetPresent: Boolean, now: Long, complete: Boolean = true): Boolean {
        if (!complete) {
            absentSince = -1L
            return false
        }
        if (targetPresent) {
            absentSince = -1L
            return false
        }
        if (tried.isEmpty()) return false
        if (absentSince < 0L) absentSince = now
        if (now - absentSince >= absenceMs && now - lastAttemptAt >= rearmGapMs) {
            reset()
            return true
        }
        return false
    }

    fun reset() {
        tried.clear()
        lastAttemptAt = -10_000L
        absentSince = -1L
    }
}
