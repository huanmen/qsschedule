package cn.qztc.timetable.adskip

/** Binary user-facing result after verification; raw progress is never a successful skip. */
object AdSkipOutcome {
    fun label(result: AdSkipResult): String? = when (result) {
        AdSkipResult.TARGET_GONE, AdSkipResult.NORMAL_PAGE -> "成功"
        AdSkipResult.TARGET_PRESENT, AdSkipResult.CLICK_REJECTED, AdSkipResult.GESTURE_CANCELLED,
        AdSkipResult.UNCONFIRMED, AdSkipResult.TARGET_NOT_CLICKABLE -> "失败"
        else -> null
    }
}
