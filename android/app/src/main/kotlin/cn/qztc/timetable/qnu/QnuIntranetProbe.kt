package cn.qztc.timetable.qnu

import cn.qztc.timetable.integration.qnu.android.QnuEndpointPolicy
import java.net.HttpURLConnection
import java.net.URL

/** Reachability is advisory: a slow response does not prove the VPN is offline. */
object QnuIntranetProbe {
    fun isReachable(): Boolean = runCatching {
        val connection = URL(QnuEndpointPolicy.INITIAL_WEBVIEW_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 12_000
        connection.instanceFollowRedirects = false
        connection.requestMethod = "GET"
        try { connection.responseCode in 200..499 }
        finally { connection.disconnect() }
    }.getOrDefault(false)
}
