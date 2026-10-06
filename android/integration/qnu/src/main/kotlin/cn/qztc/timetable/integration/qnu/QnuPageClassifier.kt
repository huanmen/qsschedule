package cn.qztc.timetable.integration.qnu

import java.io.ByteArrayInputStream
import org.jsoup.Jsoup

enum class QnuPageKind {
    TIMETABLE,
    AUTHENTICATION,
    UNKNOWN,
}

/** 只判断页面用途，不提取或回传页面中的个人内容。 */
object QnuPageClassifier {

    fun classify(bytes: ByteArray): QnuPageKind {
        if (bytes.isEmpty()) return QnuPageKind.UNKNOWN
        val document = runCatching {
            Jsoup.parse(ByteArrayInputStream(bytes), null, "")
        }.getOrElse { return QnuPageKind.UNKNOWN }

        if (document.selectFirst("table#timetable") != null) {
            return QnuPageKind.TIMETABLE
        }

        val hasPassword = document.selectFirst("input[type=password]") != null
        val hasLoginForm = document.select("form[action]").any { form ->
            form.attr("action").contains("login", ignoreCase = true)
        }
        val hasCaptcha = document.select("img[src], input[name], input[id]").any { element ->
            element.attr("src").contains("captcha", ignoreCase = true) ||
                element.attr("name").contains("captcha", ignoreCase = true) ||
                element.id().contains("captcha", ignoreCase = true)
        }
        val title = document.title()
        val looksLikeLogin = title.contains("登录") || title.contains("统一身份")

        return if (hasPassword || hasLoginForm || hasCaptcha || looksLikeLogin) {
            QnuPageKind.AUTHENTICATION
        } else {
            QnuPageKind.UNKNOWN
        }
    }
}
