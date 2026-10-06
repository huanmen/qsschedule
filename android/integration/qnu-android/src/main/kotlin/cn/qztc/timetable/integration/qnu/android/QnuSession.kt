package cn.qztc.timetable.integration.qnu.android

import android.webkit.CookieManager

/** 仅在内存中传递的教务会话。toString 始终脱敏，避免被日志意外展开。 */
class QnuSessionSnapshot internal constructor(
    internal val timetableUrl: String,
    internal val cookieHeader: String,
) {
    override fun toString(): String = "QnuSessionSnapshot(redacted)"
}

sealed interface QnuSessionCaptureResult {
    data class Captured(val session: QnuSessionSnapshot) : QnuSessionCaptureResult
    data object NoActiveSession : QnuSessionCaptureResult
    data object RejectedUrl : QnuSessionCaptureResult
}

/** 用户在前台 WebView 到达个人课表页后调用。这里不保存密码，也不持久化 Cookie。 */
class QnuWebSessionCapture(
    private val cookieReader: (String) -> String? = { url ->
        CookieManager.getInstance().getCookie(url)
    },
) {
    fun capture(timetableUrl: String): QnuSessionCaptureResult {
        if (!QnuEndpointPolicy.isTimetableUrl(timetableUrl)) {
            return QnuSessionCaptureResult.RejectedUrl
        }
        val cookie = cookieReader(timetableUrl)?.trim().orEmpty()
        if (cookie.isEmpty()) return QnuSessionCaptureResult.NoActiveSession
        return QnuSessionCaptureResult.Captured(QnuSessionSnapshot(timetableUrl, cookie))
    }
}
