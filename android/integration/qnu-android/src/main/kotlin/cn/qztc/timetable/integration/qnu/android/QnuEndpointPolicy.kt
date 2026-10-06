package cn.qztc.timetable.integration.qnu.android

import java.net.URI

object QnuEndpointPolicy {

    private const val ACADEMIC_HOST = "jw.qztc.edu.cn"
    private const val IDENTITY_HOST = "id.qztc.edu.cn"
    private const val TIMETABLE_PATH = "/academic/manager/coursearrange/showTimetable.do"
    private const val COURSE_SELECTION_PATH = "/academic/student/currcourse/currcourse.jsdo"
    private const val PORTAL_MAIN_PATH = "/portal/main.html"
    private const val ACADEMIC_CAS_CALLBACK_PATH = "/academic/login/qztc/loginIds6Valid.jsp"

    const val INITIAL_WEBVIEW_URL = "https://jw.qztc.edu.cn/academic/index_frame.jsp"
    const val CURRENT_TIMETABLE_ENTRY_URL =
        "https://jw.qztc.edu.cn/academic/student/currcourse/currcourse.jsdo?groupId=&moduleId=2000"

    /** 前台登录只允许在泉师教务与统一身份认证两个 HTTPS 主机之间跳转。 */
    fun isAllowedWebViewUrl(url: String): Boolean = parse(url)?.let { uri ->
        uri.scheme.equals("https", ignoreCase = true) &&
            (uri.host.equals(ACADEMIC_HOST, ignoreCase = true) ||
                uri.host.equals(IDENTITY_HOST, ignoreCase = true))
    } ?: false

    fun upgradeInsecureAcademicCasCallback(url: String): String? = parse(url)?.let { uri ->
        if (
            !uri.scheme.equals("http", ignoreCase = true) ||
            !uri.host.equals(ACADEMIC_HOST, ignoreCase = true) ||
            uri.path != ACADEMIC_CAS_CALLBACK_PATH
        ) {
            return@let null
        }
        URI(
            "https",
            uri.userInfo,
            uri.host,
            uri.port,
            uri.path,
            uri.query,
            uri.fragment,
        ).toString()
    }

    fun isTrustedAcademicUrl(url: String): Boolean = parse(url)?.let { uri ->
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(ACADEMIC_HOST, ignoreCase = true)
    } ?: false

    fun isTimetableUrl(url: String): Boolean = parse(url)?.let { uri ->
        isTrustedAcademicUrl(url) && uri.path == TIMETABLE_PATH
    } ?: false

    fun isSnapshotCaptureUrl(url: String): Boolean =
        isTrustedAcademicUrl(url) && !isAuthenticationUrl(url)

    fun isCourseSelectionUrl(url: String): Boolean = parse(url)?.let { uri ->
        isTrustedAcademicUrl(url) && uri.path == COURSE_SELECTION_PATH
    } ?: false

    fun courseSelectionUrl(yearId: String, termId: String): String? {
        if (!yearId.all(Char::isDigit) || !termId.all(Char::isDigit)) return null
        return "https://$ACADEMIC_HOST$COURSE_SELECTION_PATH?year=$yearId&term=$termId"
    }

    fun isPortalMainUrl(url: String): Boolean = parse(url)?.let { uri ->
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(IDENTITY_HOST, ignoreCase = true) &&
            uri.path == PORTAL_MAIN_PATH
    } ?: false

    fun isAcademicHomeUrl(url: String): Boolean = parse(url)?.let { uri ->
        if (!isTrustedAcademicUrl(url)) return@let false
        uri.path in setOf(
            "/academic/index_frame.jsp",
            "/academic/frameset_index.jsp",
        )
    } ?: false

    fun isAuthenticationUrl(url: String): Boolean = parse(url)?.let { uri ->
        if (!uri.scheme.equals("https", ignoreCase = true)) return@let false
        val path = uri.path.orEmpty().lowercase()
        when {
            uri.host.equals(IDENTITY_HOST, ignoreCase = true) ->
                path.contains("/login/") || path.substringAfterLast('/').startsWith("login")
            uri.host.equals(ACADEMIC_HOST, ignoreCase = true) ->
                path.contains("/login/") || path.endsWith("login.jsp") || path.endsWith("login.do")
            else -> false
        }
    } ?: false

    fun resolve(currentUrl: String, location: String): String? = runCatching {
        URI(currentUrl).resolve(location).toString()
    }.getOrNull()

    private fun parse(url: String): URI? = runCatching { URI(url) }.getOrNull()
}
