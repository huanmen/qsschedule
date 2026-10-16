package cn.qztc.timetable.education

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CreditScore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Snackbar
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.layout.Layout
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cn.qztc.timetable.core.domain.model.Course
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcCorners
import cn.qztc.timetable.core.ui.theme.QztcPageTopBar
import cn.qztc.timetable.core.ui.theme.QztcSpacing
import cn.qztc.timetable.core.ui.theme.QztcTypography
import cn.qztc.timetable.integration.qnu.android.QnuEndpointPolicy
import cn.qztc.timetable.qnu.QnuSavedCredentials
import cn.qztc.timetable.qnu.autofillCredentials
import cn.qztc.timetable.qnu.installPortalSsoBridge

@Composable
fun StudyScreen(
    semesterLabel: String,
    courses: List<Course>,
    credentials: QnuSavedCredentials?,
    onBack: () -> Unit,
    onConfigureAccount: () -> Unit,
    initialTab: Int = 0,
) {
    val colors = LocalQztcColors.current
    val history = rememberGradeHistory(credentials)
    var gradeMessage by remember { mutableStateOf("") }
    LaunchedEffect(history.status, history.attempt) {
        if (history.attempt > 0 && history.status.isNotBlank()) {
            gradeMessage = history.status; delay(3500); gradeMessage = ""
        }
    }
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    val tabs = listOf("成绩" to Icons.Outlined.Assessment, "学分" to Icons.Outlined.CreditScore)
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(colors.solid2)) {
        QztcPageTopBar("学习情况", onBack = onBack)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = QztcSpacing.lg, vertical = QztcSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(QztcSpacing.xs),
        ) {
            tabs.forEachIndexed { index, (label, icon) ->
                TextButton(
                    onClick = { tab = index },
                    modifier = Modifier.weight(1f).background(
                        if (tab == index) colors.accentSoft else Color.Transparent,
                        RoundedCornerShape(QztcSpacing.md),
                    ),
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (tab == index) colors.accentDeep else colors.mut)
                    Spacer(Modifier.width(5.dp))
                    Text(label, color = if (tab == index) colors.accentDeep else colors.mut)
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            RetainedStudyTab(tab == 0) { GradeHistoryPanel(history, onConfigureAccount) }
            RetainedStudyTab(tab == 1) { CreditProgressPanel(credentials, onConfigureAccount, active = tab == 1) }
        }
    }
    GradeHistoryLoader(history, credentials)
    if (tab == 0 && gradeMessage.isNotBlank()) Snackbar(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)) {
        Text(gradeMessage)
    }
    }
}

/** Keep data, filters and list positions alive without placing the inactive page. */
@Composable
internal fun RetainedStudyTab(visible: Boolean, content: @Composable () -> Unit) {
    Layout(content = content, modifier = if (visible) Modifier.fillMaxSize() else Modifier.size(0.dp)) { children, constraints ->
        if (visible) {
            val pages = children.map { it.measure(constraints) }
            layout(constraints.maxWidth, constraints.maxHeight) { pages.forEach { it.placeRelative(0, 0) } }
        } else {
            layout(0, 0) {}
        }
    }
}

@Composable
private fun GradeTab(semesterLabel: String, courses: List<Course>, onOpenGrades: () -> Unit) {
    val colors = LocalQztcColors.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(QztcSpacing.lg).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(QztcSpacing.lg),
    ) {
        StudyCard {
            Text("成绩查询", style = QztcTypography.pageTitle, color = colors.ink)
            Text(semesterLabel, style = QztcTypography.meta, color = colors.mut)
            Spacer(Modifier.height(QztcSpacing.sm))
            Text("成绩从教务系统读取，未读取到的项目不会显示为 0。", style = QztcTypography.body, color = colors.mut)
            Button(onClick = onOpenGrades, modifier = Modifier.fillMaxWidth()) { Text("打开教务成绩") }
        }
        StudyCard {
            Text("当前课表", style = QztcTypography.section, color = colors.ink)
            if (courses.isEmpty()) {
                Text("尚未导入当前学期课表", style = QztcTypography.body, color = colors.mut)
            } else {
                courses.take(8).forEach { course ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(course.name, Modifier.weight(1f), style = QztcTypography.body, color = colors.ink)
                        Text(course.credits?.takeIf { it.isNotBlank() }?.let { "$it 学分" } ?: "学分未获取", style = QztcTypography.meta, color = colors.mut)
                    }
                }
                if (courses.size > 8) Text("还有 ${courses.size - 8} 门课程", style = QztcTypography.meta, color = colors.mut)
            }
        }
    }
}

@Composable
private fun EvaluationTab(onOpenEvaluation: () -> Unit) {
    val colors = LocalQztcColors.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(QztcSpacing.lg).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(QztcSpacing.lg),
    ) {
        StudyCard {
            Text("快速评教", style = QztcTypography.pageTitle, color = colors.ink)
            Text("从教务系统打开待评课程，填写内容后由你本人确认提交。", style = QztcTypography.body, color = colors.mut)
            Button(onClick = onOpenEvaluation, modifier = Modifier.fillMaxWidth()) { Text("打开评教页面") }
        }
        StudyCard {
            Text("提交前确认", style = QztcTypography.section, color = colors.ink)
            Text("应用只提供进入页面的快捷入口，不会代替你提交评教。", style = QztcTypography.body, color = colors.mut)
        }
    }
}


@Composable
private fun StudyCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalQztcColors.current
    Column(
        Modifier.fillMaxWidth().background(colors.solid, RoundedCornerShape(QztcCorners.content)).padding(QztcSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(QztcSpacing.sm),
        content = content,
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AcademicPortalScreen(title: String, credentials: QnuSavedCredentials?, onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalQztcColors.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var status by remember { mutableStateOf("正在打开教务系统…") }
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(colors.solid2)) {
        QztcPageTopBar(title, onBack = onBack, actions = {
            IconButton(onClick = { webView?.reload() }, enabled = !loading) {
                if (loading) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp, color = colors.accentDeep)
                else Icon(Icons.Outlined.Refresh, contentDescription = "刷新", tint = colors.mut)
            }
        })
        Text(status, style = QztcTypography.meta, color = colors.mut, modifier = Modifier.padding(horizontal = QztcSpacing.lg, vertical = QztcSpacing.sm))
        OutlinedButton(
            onClick = {
            val keyword = if (title.contains("成绩")) "成绩" else "评教"
                webView?.let { findAcademicEntry(it, keyword) { found ->
                    status = if (found) "正在进入所选页面…" else "请在下方教务页面中选择${if (keyword == "成绩") "成绩查询" else "教学评价"}"
                } }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().padding(horizontal = QztcSpacing.lg),
        ) { Text(if (title.contains("成绩")) "进入成绩查询" else "进入教学评价") }
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = {
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadsImagesAutomatically = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                    android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    addOnLayoutChangeListener { view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                        if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                            (view as WebView).post { repairAcademicHomeLayout(view) }
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                            QnuEndpointPolicy.upgradeInsecureAcademicCasCallback(url)?.let { secureUrl ->
                                view.post { view.loadUrl(secureUrl) }
                                return true
                            }
                            if (!QnuEndpointPolicy.isAllowedWebViewUrl(url)) {
                                status = "已阻止离开泉师教务系统"
                                return true
                            }
                            return false
                        }

                        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                            loading = true
                            loadFailed = false
                            status = "正在加载教务页面…"
                        }

                        override fun onPageCommitVisible(view: WebView, url: String) {
                            repairAcademicHomeLayout(view)
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            if (view !== webView) return
                            loading = false
                            if (loadFailed) return
                            repairAcademicHomeLayout(view)
                            when {
                                QnuEndpointPolicy.isAuthenticationUrl(url) -> {
                                    status = "请完成教务登录"
                                    credentials?.let { autofillCredentials(view, it, force = true, autoSubmit = false) }
                                }
                                QnuEndpointPolicy.isPortalMainUrl(url) -> { status = "正在进入教务系统…"; installPortalSsoBridge(view) }
                                QnuEndpointPolicy.isTrustedAcademicUrl(url) -> status = "教务页面已加载"
                                else -> status = "请返回教务系统"
                            }
                        }
                        override fun onReceivedError(view: WebView, request: android.webkit.WebResourceRequest, error: android.webkit.WebResourceError) {
                            if (request.isForMainFrame) { loadFailed = true; loading = false; status = "教务页面加载失败，请检查校园网后重试" }
                        }
                        override fun onReceivedHttpError(view: WebView, request: android.webkit.WebResourceRequest, response: android.webkit.WebResourceResponse) {
                            if (request.isForMainFrame) { loadFailed = true; loading = false; status = "教务系统暂时无法访问，请稍后重试（HTTP ${response.statusCode}）" }
                        }
                        override fun onReceivedSslError(view: WebView, handler: android.webkit.SslErrorHandler, error: android.net.http.SslError) {
                            handler.cancel()
                            loadFailed = true; loading = false
                            status = "学校页面安全连接失败，请检查系统时间和校园网"
                        }
                    }
                    webView = this
                    loadUrl(QnuEndpointPolicy.INITIAL_WEBVIEW_URL)
                }
            },
            update = { webView = it },
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            (webView?.parent as? android.view.ViewGroup)?.removeView(webView)
            webView?.destroy()
            webView = null
        }
    }
}

/** The user explicitly starts this lookup; it only opens a matching portal entry. */
private fun findAcademicEntry(view: WebView, keyword: String, onResult: (Boolean) -> Unit) {
    val quoted = org.json.JSONObject.quote(keyword)
    view.evaluateJavascript(
        """
        (() => {
          const docs = [];
          const visit = doc => {
            if (!doc || docs.includes(doc) || docs.length > 32) return;
            docs.push(doc);
            for (const frame of doc.querySelectorAll('iframe,frame')) {
              try { visit(frame.contentDocument); } catch (_) {}
            }
          };
          const find = () => {
            visit(document);
            const wanted = $quoted;
            for (const doc of docs) for (const element of doc.querySelectorAll('a,button,li,[role="button"]')) {
              const text = (element.title || element.textContent || '').replace(/\s+/g, '');
              const labels = wanted === '成绩' ? ['个人成绩查询', '成绩查询', '课程成绩查询', '学生成绩', '学业成绩', '我的成绩'] : ['教学评价', '教学评估', '学生评教', '课程评教', '网上评教', '评教'];
              if (labels.includes(text) && element.getClientRects().length) { element.click(); return true; }
            }
            return false;
          };
          return find();
        })();
        """.trimIndent(),
    ) { result -> onResult(result == "true") }
}
