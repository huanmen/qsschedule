package cn.qztc.timetable.education

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.SystemClock
import android.view.View
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import cn.qztc.timetable.integration.qnu.QnuAcademicParser
import cn.qztc.timetable.integration.qnu.QnuGradeRecord
import cn.qztc.timetable.integration.qnu.QnuGradeSemesters
import cn.qztc.timetable.integration.qnu.android.QnuEndpointPolicy
import cn.qztc.timetable.qnu.QnuSavedCredentials
import cn.qztc.timetable.qnu.autofillCredentials
import cn.qztc.timetable.qnu.installPortalSsoBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.coroutines.resume

/** User-triggered foreground acquisition. The native UI never displays this WebView. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun GradeAcquisition(
    credentials: QnuSavedCredentials,
    onStatus: (String) -> Unit,
    onSuccess: (List<QnuGradeRecord>) -> Unit,
    onFailure: (String, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val statusCallback by rememberUpdatedState(onStatus)
    val successCallback by rememberUpdatedState(onSuccess)
    val failureCallback by rememberUpdatedState(onFailure)
    var webView by remember { mutableStateOf<WebView?>(null) }
    val session = remember { GradeSessionState() }
    // Never borrow the course-import profile: every lookup starts with fresh authentication.
    val profileName = remember { "grades-" + java.util.UUID.randomUUID().toString() }
    val profilesSupported = remember { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }
    if (!profilesSupported) {
        LaunchedEffect(Unit) { failureCallback("请更新系统 Android System WebView 后重试成绩获取", false) }
        return
    }
    Box(Modifier.size(1.dp).clipToBounds().alpha(0f)) {
        AndroidView(
            modifier = Modifier.requiredSize(360.dp, 600.dp),
            factory = {
                WebView(context).apply {
                    WebViewCompat.setProfile(this, profileName)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    isFocusable = false
                    isClickable = false
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.setSupportMultipleWindows(false)
                    WebViewCompat.getProfile(this).cookieManager.setAcceptCookie(true)
                    WebViewCompat.getProfile(this).cookieManager.setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = redirect(view, request.url.toString())
                        @Suppress("DEPRECATION")
                        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = redirect(view, url)
                        private fun redirect(view: WebView, url: String): Boolean {
                            QnuEndpointPolicy.upgradeInsecureAcademicCasCallback(url)?.let {
                                view.post { if (!session.disposed) view.loadUrl(it) }; return true
                            }
                            if (!QnuEndpointPolicy.isAllowedWebViewUrl(url)) {
                                session.error = "教务登录跳转异常，请重新登录"; session.needsLogin = true; return true
                            }
                            return false
                        }
                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) { session.loading = true }
                        override fun onPageFinished(view: WebView, url: String) { session.loading = false; repairAcademicHomeLayout(view) }
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) session.error = "无法连接教务，请检查校园网或校园 VPN 后重试"
                        }
                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                            if (request.isForMainFrame) session.error = "教务系统暂时无法访问，请稍后重试"
                        }
                        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                            handler.cancel(); session.error = "教务安全连接失败，请检查系统时间和校园网"
                        }
                        override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                            session.error = "成绩读取已中断，请重试"; session.rendererGone = true; return true
                        }
                    }
                    webView = this
                    loadUrl(QnuEndpointPolicy.INITIAL_WEBVIEW_URL)
                }
            },
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            session.disposed = true
            webView?.let { view ->
                view.stopLoading()
                (view.parent as? android.view.ViewGroup)?.removeView(view)
                view.destroy()
            }
            webView = null
            runCatching { ProfileStore.getInstance().deleteProfile(profileName) }
        }
    }
    LaunchedEffect(webView) {
        val view = webView ?: return@LaunchedEffect
        try {
            val source = withContext(Dispatchers.IO) { context.assets.open("academic/grades.js").bufferedReader().use { it.readText() } }
            withTimeout(75_000) {
                var queried = false
                var authStarted: Long? = null
                var bridged = false
                while (!session.disposed) {
                    session.error?.let { failureCallback(it, session.needsLogin); return@withTimeout }
                    if (session.loading) { delay(350); continue }
                    val url = view.url.orEmpty()
                    when {
                        QnuEndpointPolicy.isAuthenticationUrl(url) -> {
                            if (authStarted == null) {
                                authStarted = SystemClock.elapsedRealtime()
                                statusCallback("正在使用已保存的账号登录…")
                                autofillCredentials(view, credentials, force = true, autoSubmit = true)
                            }
                            if (SystemClock.elapsedRealtime() - authStarted > 18_000) {
                                failureCallback("登录未完成，请检查保存的账号或完成学校验证", true)
                                return@withTimeout
                            }
                        }
                        QnuEndpointPolicy.isPortalMainUrl(url) -> {
                            if (!bridged) { bridged = true; installPortalSsoBridge(view) }
                            statusCallback("正在进入教务系统…")
                        }
                        QnuEndpointPolicy.isTrustedAcademicUrl(url) -> {
                            // Data stays on-device. DOM is decoded by WebView using the response's original charset.
                            val payload = evaluateGradeScript(view, source, queried)
                            when (payload.optString("kind")) {
                                "opening" -> statusCallback("正在读取成绩学期…")
                                "querying" -> { queried = true; statusCallback("正在获取全部学期成绩…") }
                                "grades" -> {
                                    val records = withContext(Dispatchers.Default) { QnuAcademicParser.parseGrades(payload.getString("html")) }
                                    if (records.size != payload.getInt("count") || records.any { QnuGradeSemesters.order(it.year.orEmpty(), it.term.orEmpty()) == null }) {
                                        failureCallback("成绩表结构发生变化，未覆盖原有记录", false)
                                    } else successCallback(records)
                                    return@withTimeout
                                }
                                "schema" -> { failureCallback("未读到完整成绩表，请稍后重试", false); return@withTimeout }
                                "incomplete" -> { failureCallback("学校未返回完整成绩，原有记录已保留", false); return@withTimeout }
                            }
                        }
                    }
                    delay(450)
                }
            }
        } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
            if (!session.disposed) failureCallback("获取超时，请检查校园网后重试", false)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            if (!session.disposed) failureCallback("成绩获取失败，原有记录已保留", false)
        }
    }
}

private class GradeSessionState {
    var loading = true
    var disposed = false
    var rendererGone = false
    var needsLogin = false
    var error: String? = null
}

private suspend fun evaluateGradeScript(view: WebView, script: String, queried: Boolean): JSONObject =
    suspendCancellableCoroutine { continuation ->
        view.evaluateJavascript("$script($queried)") { raw ->
            if (continuation.isActive) continuation.resume(runCatching { JSONObject(raw) }.getOrElse { JSONObject().put("kind", "waiting") })
        }
    }
