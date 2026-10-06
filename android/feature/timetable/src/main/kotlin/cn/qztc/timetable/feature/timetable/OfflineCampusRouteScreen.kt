package cn.qztc.timetable.feature.timetable

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.Keep
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcPageTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@Keep
internal class LegacyOfflineMapBridge(
    private val locate: () -> Unit,
    private val importAction: () -> Unit,
    private val export: (String) -> Unit,
    private val navigate: (String, Double, Double) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    @JavascriptInterface fun requestLocation() { main.post { locate() } }
    @JavascriptInterface fun importEdits() { main.post { importAction() } }
    @JavascriptInterface fun exportEdits(text: String) {
        if (text.length <= 5 * 1024 * 1024 && runCatching { JSONObject(text).optString("format") == "qnu-campus-place-edits" }.getOrDefault(false)) {
            main.post { export(text) }
        }
    }
    @JavascriptInterface fun openNavigation(name: String, latitude: Double, longitude: Double) {
        if (name.length <= 80 && latitude.isFinite() && longitude.isFinite() && latitude in 24.85..24.89 && longitude in 118.65..118.69) {
            main.post { navigate(name, latitude, longitude) }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "MissingPermission")
@Composable
internal fun LegacyEmbeddedCampusRouteScreen(
    rawDestination: String,
    onBack: () -> Unit,
    onCoverBottomBar: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = LocalQztcColors.current
    val scope = rememberCoroutineScope()
    var view by remember { mutableStateOf<WebView?>(null) }
    var locationTick by remember { mutableStateOf(0) }
    var hasLocation by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) }
    var lastLocation by remember { mutableStateOf<Location?>(null) }
    var pendingExport by remember { mutableStateOf("") }
    val destination by rememberUpdatedState(rawDestination)
    val legacyAliases = remember(context) {
        val overrides = CampusAliasStore(context).loadOverrides()
        val catalog = CampusPlaceRepository.load(context)
        JSONArray().apply {
            for ((id, aliases) in overrides.aliases) {
                catalog.point(id)?.let { place ->
                    put(JSONObject().put("name", place.name).put("aliases", JSONArray(aliases.toList())))
                }
            }
        }.toString()
    }
    fun call(script: String) { view?.evaluateJavascript("window.CampusApp && $script", null) }
    fun status(message: String) { call("CampusApp.status(${JSONObject.quote(message)})") }
    fun sendLocation(location: Location) {
        val point = CampusAmapProjection.wgs84ToGcj02(CampusCoordinate(location.latitude, location.longitude))
        val payload = JSONObject().put("lat", point.latitude).put("lng", point.longitude).put("accuracy", location.accuracy.toDouble())
        call("CampusApp.setLocation($payload)")
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        hasLocation = granted.values.any { it }
        if (hasLocation) locationTick++ else status("未授予定位权限，可以手动选择起点")
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= 5 * 1024 * 1024) { "编辑文件不能超过 5 MB" }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    } ?: error("无法读取编辑文件")
                    bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
                }
            }.onSuccess { call("CampusApp.importText(${JSONObject.quote(it)})") }
                .onFailure { status(it.message ?: "导入失败，已有数据保留") }
        }
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val text = pendingExport
            runCatching { withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    ?: error("无法写入编辑文件")
            } }.onSuccess { status("编辑文件已导出") }.onFailure { status("导出失败") }
        }
    }
    val back = { if (view?.canGoBack() == true) view?.goBack() else onBack(); Unit }
    BackHandler(onBack = back)
    DisposableEffect(Unit) {
        onCoverBottomBar(true)
        onDispose { onCoverBottomBar(false); view?.removeJavascriptInterface("CampusNative"); view?.destroy(); view = null }
    }
    DisposableEffect(hasLocation, locationTick) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (System.currentTimeMillis() - location.time !in 0L..120_000L) return
                val previous = lastLocation
                if (previous != null && previous.provider == LocationManager.GPS_PROVIDER &&
                    System.currentTimeMillis() - previous.time < 20_000 && previous.accuracy <= 40 &&
                    location.provider != LocationManager.GPS_PROVIDER && location.accuracy > previous.accuracy) return
                lastLocation = location
                sendLocation(location)
            }
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
            @Deprecated("Deprecated by Android") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        if (hasLocation) {
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            for (provider in providers) runCatching {
                manager.getLastKnownLocation(provider)?.takeIf { System.currentTimeMillis() - it.time in 0L..120_000L }?.let(listener::onLocationChanged)
                manager.requestLocationUpdates(provider, 5_000L, 3f, listener, Looper.getMainLooper())
            }
            if (providers.isEmpty()) status("请开启系统定位，或手动选择起点")
        }
        onDispose { runCatching { manager.removeUpdates(listener) } }
    }
    Column(modifier.fillMaxSize().background(colors.solid)) {
        QztcPageTopBar(title = "校园地图", onBack = back)
        AndroidView(
            modifier = Modifier.weight(1f),
            factory = { ctx ->
                val loader = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx)).build()
                WebView(ctx).apply {
                    view = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.blockNetworkLoads = true
                    addJavascriptInterface(LegacyOfflineMapBridge(
                        locate = {
                            if (hasLocation) { locationTick++; lastLocation?.let(::sendLocation) }
                            else permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        },
                        importAction = { importFile.launch(arrayOf("application/json", "text/plain")) },
                        export = { pendingExport = it; exportFile.launch("泉师地图-地点编辑.json") },
                        navigate = { name, lat, lng ->
                            val point = CampusAmapProjection.gcj02ToWgs84(CampusCoordinate(lat, lng))
                            runCatching { openCampusExternalNavigation(ctx, lastLocation?.let { CampusCoordinate(it.latitude, it.longitude) }, CampusPoint("offline-destination", name, point)) }
                                .onFailure { status("无法打开外部地图") }
                        },
                    ), "CampusNative")
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                            if (OfflineMapPolicy.allows(request.url.toString())) {
                                loader.shouldInterceptRequest(request.url)?.let { response ->
                                    if (request.url.path?.endsWith(".webp") == true) response.mimeType = "image/webp"
                                    return response
                                }
                            }
                            return WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                        }
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = !OfflineMapPolicy.allows(request.url.toString())
                        override fun onPageFinished(view: WebView, url: String) {
                            if (url == OfflineMapPolicy.START) {
                                call("CampusApp.setAliases($legacyAliases)")
                                call("CampusApp.setDestination(${JSONObject.quote(destination)})")
                                lastLocation?.let(::sendLocation)
                            }
                        }
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) status("本地地图加载失败，请返回后重试")
                        }
                    }
                    loadUrl(OfflineMapPolicy.START)
                }
            },
        )
    }
}
