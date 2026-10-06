package cn.qztc.timetable.integration.qnu.android

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class QnuFetchFailure {
    NETWORK,
    HTTP,
    RESPONSE_TOO_LARGE,
    UNTRUSTED_REDIRECT,
}

sealed interface QnuFetchResult {
    class Page internal constructor(
        internal val bytes: ByteArray,
        internal val contentType: String?,
    ) : QnuFetchResult

    data object ReauthenticationRequired : QnuFetchResult
    data class Failed(val reason: QnuFetchFailure, val statusCode: Int? = null) : QnuFetchResult
}

fun interface QnuPageFetcher {
    suspend fun fetch(session: QnuSessionSnapshot): QnuFetchResult
}

/**
 * 复用前台 WebView 已建立的会话获取原始字节。重定向只允许留在教务 HTTPS 域名内，
 * 避免把教务 Cookie 发送给身份站或其他主机。
 */
class HttpsQnuPageFetcher(
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 20_000,
    private val maxResponseBytes: Int = 4 * 1024 * 1024,
) : QnuPageFetcher {

    override suspend fun fetch(session: QnuSessionSnapshot): QnuFetchResult = withContext(Dispatchers.IO) {
        if (!QnuEndpointPolicy.isTimetableUrl(session.timetableUrl)) {
            return@withContext QnuFetchResult.Failed(QnuFetchFailure.UNTRUSTED_REDIRECT)
        }

        var currentUrl = session.timetableUrl
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val connection = runCatching { open(currentUrl, session.cookieHeader) }
                .getOrElse { return@withContext QnuFetchResult.Failed(QnuFetchFailure.NETWORK) }
            try {
                val status = runCatching { connection.responseCode }
                    .getOrElse { return@withContext QnuFetchResult.Failed(QnuFetchFailure.NETWORK) }

                if (status == HttpURLConnection.HTTP_UNAUTHORIZED || status == HttpURLConnection.HTTP_FORBIDDEN) {
                    return@withContext QnuFetchResult.ReauthenticationRequired
                }

                if (status in REDIRECT_CODES) {
                    val location = connection.getHeaderField("Location")
                        ?: return@withContext QnuFetchResult.Failed(QnuFetchFailure.HTTP, status)
                    val redirectUrl = QnuEndpointPolicy.resolve(currentUrl, location)
                        ?: return@withContext QnuFetchResult.Failed(QnuFetchFailure.UNTRUSTED_REDIRECT)
                    if (QnuEndpointPolicy.isAuthenticationUrl(redirectUrl)) {
                        return@withContext QnuFetchResult.ReauthenticationRequired
                    }
                    if (!QnuEndpointPolicy.isTrustedAcademicUrl(redirectUrl) || redirectCount == MAX_REDIRECTS) {
                        return@withContext QnuFetchResult.Failed(QnuFetchFailure.UNTRUSTED_REDIRECT)
                    }
                    currentUrl = redirectUrl
                    return@repeat
                }

                if (status !in 200..299) {
                    return@withContext QnuFetchResult.Failed(QnuFetchFailure.HTTP, status)
                }

                val bytes = try {
                    connection.inputStream.use { it.readLimited(maxResponseBytes) }
                } catch (_: ResponseTooLargeException) {
                    return@withContext QnuFetchResult.Failed(QnuFetchFailure.RESPONSE_TOO_LARGE)
                } catch (_: Exception) {
                    return@withContext QnuFetchResult.Failed(QnuFetchFailure.NETWORK)
                }
                return@withContext QnuFetchResult.Page(bytes, connection.contentType)
            } finally {
                connection.disconnect()
            }
        }
        QnuFetchResult.Failed(QnuFetchFailure.UNTRUSTED_REDIRECT)
    }

    private fun open(url: String, cookieHeader: String): HttpsURLConnection =
        (URL(url).openConnection() as HttpsURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            requestMethod = "GET"
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
            setRequestProperty("Cookie", cookieHeader)
            setRequestProperty("User-Agent", "QztcTimetable/0.1 Android")
        }

    private fun InputStream.readLimited(limit: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(limit, DEFAULT_BUFFER_SIZE * 4))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw ResponseTooLargeException()
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private class ResponseTooLargeException : Exception()

    private companion object {
        const val MAX_REDIRECTS = 5
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
