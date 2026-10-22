package cn.qztc.timetable.education

import android.annotation.SuppressLint
import android.net.http.SslError
import android.os.SystemClock
import android.webkit.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import cn.qztc.timetable.qnu.QnuSavedCredentials
import cn.qztc.timetable.qnu.autofillCredentials
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URI
import kotlin.coroutines.resume

private const val HOMEWORK_SSO = "https://eol.qztc.edu.cn/meol/homepage/common/sso_login.jsp"
private const val HOMEWORK_HOME = "https://eol.qztc.edu.cn/meol/personal.do"

internal fun homeworkNavigationAllowed(url: String): Boolean = runCatching {
    val uri = URI(url)
    uri.scheme == "https" && uri.userInfo == null && uri.port in listOf(-1, 443) &&
        uri.host in setOf("eol.qztc.edu.cn", "id.qztc.edu.cn")
}.getOrDefault(false)

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun HomeworkAcquisition(
    credentials: QnuSavedCredentials,
    onProgress: suspend (JSONObject) -> Unit,
    onComplete: suspend (JSONObject) -> Unit,
    onFailure: (String) -> Unit,
) {
    val context = LocalContext.current
    val progress by rememberUpdatedState(onProgress)
    val complete by rememberUpdatedState(onComplete)
    val failure by rememberUpdatedState(onFailure)
    var view by remember { mutableStateOf<WebView?>(null) }
    val session = remember { HomeworkSession() }
    val profile = remember { "homework-${java.util.UUID.randomUUID()}" }
    val profilesSupported = remember { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }
    if (!profilesSupported) {
        LaunchedEffect(Unit) { failure("请更新系统 Android System WebView 后重试作业查询") }
        return
    }
    Box(Modifier.requiredSize(1.dp).alpha(0f)) {
        AndroidView(modifier = Modifier.requiredSize(1.dp), factory = {
            WebView(context).apply {
                WebViewCompat.setProfile(this, profile)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                settings.allowFileAccess = false; settings.allowContentAccess = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(web: WebView, request: WebResourceRequest): Boolean {
                        // The school's registered CAS service callback still advertises HTTP.
                        val uri = request.url
                        if (uri.scheme == "http" && uri.host == "eol.qztc.edu.cn" &&
                            uri.path == "/meol/homepage/common/sso_login.jsp") {
                            web.loadUrl(uri.buildUpon().scheme("https").build().toString()); return true
                        }
                        if (homeworkNavigationAllowed(request.url.toString())) return false
                        session.error = "教学平台要求打开其他地址，请在网页登录后重试"
                        return true
                    }
                    override fun onPageStarted(web: WebView, url: String, icon: android.graphics.Bitmap?) { session.loading = true }
                    override fun onPageFinished(web: WebView, url: String) { session.loading = false }
                    override fun onReceivedError(web: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame) session.error = "教学平台连接失败，请检查校园网或校园 VPN"
                    }
                    override fun onReceivedSslError(web: WebView, handler: SslErrorHandler, error: SslError) {
                        handler.cancel(); session.error = "教学平台安全连接失败"
                    }
                    override fun onRenderProcessGone(web: WebView, detail: RenderProcessGoneDetail): Boolean {
                        session.error = "作业读取中断，请重新查询"; return true
                    }
                }
                view = this; loadUrl(HOMEWORK_SSO)
            }
        })
    }
    DisposableEffect(Unit) {
        onDispose {
            session.disposed = true
            view?.let { it.stopLoading(); it.clearCache(true); it.destroy() }
            view = null
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) runCatching { ProfileStore.getInstance().deleteProfile(profile) }
        }
    }
    LaunchedEffect(view) {
        val web = view ?: return@LaunchedEffect
        try {
            val script = withContext(Dispatchers.IO) { context.assets.open("academic/homework.js").bufferedReader().use { it.readText() } }
            withTimeout(600_000) {
                var authStarted: Long? = null
                var personalOpened = false
                var loginRecovery = false
                while (!session.disposed) {
                    session.error?.let { failure(it); return@withTimeout }
                    if (session.loading) { delay(350); continue }
                    val url = web.url.orEmpty()
                    if (!homeworkNavigationAllowed(url)) { failure("教学平台地址变化，已停止读取"); return@withTimeout }
                    if (URI(url).host == "id.qztc.edu.cn") {
                        if (authStarted == null) {
                            authStarted = SystemClock.elapsedRealtime()
                            progress(JSONObject().put("status", "正在使用已保存的账号登录…"))
                            autofillCredentials(web, credentials, force = true, autoSubmit = true)
                        }
                        if (SystemClock.elapsedRealtime() - authStarted > 25_000) {
                            failure("登录未完成，请检查保存的账号密码或在学校页面完成验证"); return@withTimeout
                        }
                    } else if (!url.contains("/personal.do")) {
                        if (!personalOpened) { personalOpened = true; web.loadUrl(HOMEWORK_HOME) }
                    } else {
                        val payload = suspendCancellableCoroutine<JSONObject> { continuation ->
                            web.evaluateJavascript(script) { raw ->
                                val decoded = runCatching {
                                    val value = org.json.JSONTokener(raw).nextValue()
                                    if (value is String) JSONObject(value) else value as JSONObject
                                }.getOrElse { JSONObject().put("kind", "waiting") }
                                if (continuation.isActive) continuation.resume(decoded)
                            }
                        }
                        when (payload.optString("kind")) {
                            "login" -> {
                                if (loginRecovery) { failure("教学平台登录已过期，请重新查询"); return@withTimeout }
                                loginRecovery = true; authStarted = null; personalOpened = false
                                progress(JSONObject().put("status", "正在重新确认教学平台登录…"))
                                web.loadUrl(HOMEWORK_SSO)
                            }
                            "done" -> { complete(payload); return@withTimeout }
                            else -> progress(payload)
                        }
                    }
                    delay(700)
                }
            }
        } catch (_: TimeoutCancellationException) { if (!session.disposed) failure("查询超时，已读取的作业保留，请稍后重试") }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { if (!session.disposed) failure("作业读取失败，原有记录保留") }
    }
}

private class HomeworkSession {
    var loading = true
    var disposed = false
    var error: String? = null
}
