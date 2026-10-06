package cn.qztc.timetable.feature.timetable

import java.net.URI

internal object OfflineMapPolicy {
    const val ROOT = "https://appassets.androidplatform.net/assets/offline-campus/"
    const val START = ROOT + "mobile.html"

    fun allows(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "appassets.androidplatform.net" &&
            uri.rawUserInfo == null && uri.port in listOf(-1, 443) &&
            uri.normalize().path.startsWith("/assets/offline-campus/") &&
            !uri.rawPath.contains("%2e", true) && !uri.rawPath.contains("%2f", true)
    }.getOrDefault(false)
}
