package cn.qztc.timetable.feature.timetable

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.*
import android.location.*
import android.os.*
import android.view.Surface
import android.view.WindowManager
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.Keep
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.WebViewAssetLoader
import cn.qztc.timetable.core.domain.model.ScheduleLocation
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcPageTopBar
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.ByteArrayInputStream

@Keep
internal class OfflineMapBridge(
    private val repository: OfflinePlaceRepository,
    private val locate: () -> Unit,
    private val importAction: () -> Unit,
    private val export: (String) -> Unit,
    private val navigate: (String, Double, Double) -> Unit,
    private val pick: (ScheduleLocation) -> Unit,
    private val requestKeyboard: () -> Unit,
    private val requestRedraw: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    @JavascriptInterface fun readEdits(): String = repository.readEdits()
    @JavascriptInterface fun writeEdits(text: String): String = runCatching { repository.saveEdits(text); "" }.getOrDefault("保存失败，原数据保留")
    @JavascriptInterface fun requestLocation() { main.post(locate) }
    @JavascriptInterface fun importEdits() { main.post(importAction) }
    @JavascriptInterface fun requestKeyboard() { main.post(requestKeyboard) }
    @JavascriptInterface fun requestRedraw() { main.post(requestRedraw) }
    @JavascriptInterface fun exportEdits(text: String) {
        if (runCatching { OfflinePlaceRepository.validate(text) }.isSuccess) main.post { export(text) }
    }
    @JavascriptInterface fun chooseLocation(text: String) {
        if (text.length > 4096) return
        runCatching {
            val p = JSONObject(text); val c = p.getJSONObject("coordinate")
            val lat = c.getDouble("lat"); val lng = c.getDouble("lng")
            require(lat in 24.860..24.878 && lng in 118.659..118.682)
            ScheduleLocation(p.optString("name").take(160), p.optString("placeId").takeIf { it.isNotBlank() }, lat, lng)
        }.onSuccess { point -> main.post { pick(point) } }
    }
    @JavascriptInterface fun openNavigation(name: String, latitude: Double, longitude: Double) {
        if (name.length <= 160 && latitude in 24.85..24.89 && longitude in 118.65..118.69) main.post { navigate(name,latitude,longitude) }
    }
}
@SuppressLint("SetJavaScriptEnabled", "MissingPermission")
@Composable
fun CampusRouteScreen(
    rawDestination: String, onBack: () -> Unit, onCoverBottomBar: (Boolean) -> Unit = {}, modifier: Modifier = Modifier,
    initialMode: String = "view", destinationLocation: ScheduleLocation? = null, onLocationPicked: (ScheduleLocation) -> Unit = {},
) {
    val context = LocalContext.current
    val colors = LocalQztcColors.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { OfflinePlaceRepository(context) }
    var ready by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf<WebView?>(null) }
    var mapFrame by remember { mutableIntStateOf(0) }
    var pageReady by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var foreground by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var permission by remember { mutableStateOf(false) }
    var precisePermission by remember { mutableStateOf(false) }
    var locationRetry by remember { mutableIntStateOf(0) }
    var lastLocation by remember { mutableStateOf<Location?>(null) }
    var lastLocationCached by remember { mutableStateOf(false) }
    var locationSystem by remember { mutableStateOf(CampusCoordinateSystem.WGS84) }
    val locationPreferences = remember(context) { context.getSharedPreferences("campus_location_privacy", Context.MODE_PRIVATE) }
    val amapConfigured = remember(context) { CampusPositionTracker.hasKey(context) }
    var useAmap by remember { mutableStateOf(locationPreferences.getBoolean("amap_consent_v1", false)) }
    var showLocationConsent by remember { mutableStateOf(amapConfigured && !locationPreferences.contains("amap_consent_v1")) }
    var lastHeading by remember { mutableStateOf<Double?>(null) }
    var pendingExport by remember { mutableStateOf("") }
    val legacyAliases = remember(context) {
        val overrides = CampusAliasStore(context).loadOverrides()
        val catalog = CampusPlaceRepository.load(context)
        org.json.JSONArray().apply {
            for ((id, names) in overrides.aliases) catalog.point(id)?.let { put(JSONObject().put("name", it.name).put("aliases", org.json.JSONArray(names.toList()))) }
            for (extra in overrides.extras) put(JSONObject().put("name", extra.name).put("aliases", org.json.JSONArray(extra.aliases.toList())))
        }.toString()
    }
    val destination by rememberUpdatedState(rawDestination)
    val picked by rememberUpdatedState(onLocationPicked)
    val schedule = CampusScheduleOverlay.current
    fun call(script: String) { if(pageReady) view?.post { view?.evaluateJavascript("window.CampusApp && $script", null) } }
    fun status(text: String) { message = text; call("CampusApp.status(${JSONObject.quote(text)})") }
    fun setLocationPending(pending: Boolean) { call("CampusApp.setLocationPending($pending)") }
    fun sendLocation(location: Location, cached: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        val fix = location.liveFix()
        if (!cached && !CampusLiveFixPolicy.canDisplay(fix, now)) {
            call("CampusApp.clearLocation()"); return
        }
        val point = CampusCoordinate(location.latitude, location.longitude).forCampusMap(locationSystem)
        val age = now - fix.elapsedMillis
        val payload=JSONObject().put("lat",point.latitude).put("lng",point.longitude)
            .put("accuracy",location.accuracy.toDouble()).put("ageMillis",age)
            .put("timestamp",System.currentTimeMillis()-age)
            .put("cached",cached)
            .put("precisePermission",precisePermission)
            .put("canNavigate",!cached && CampusLiveFixPolicy.canNavigate(fix,now,precisePermission))
        // A previous provider may have reported an out-of-campus/mock fix. Clear
        // that transient warning as soon as a fresh trusted fix is accepted.
        status("")
        call("CampusApp.setLocation($payload)")
    }
    LaunchedEffect(repository) {
        ready = repository.migrateWebStorage(context)
        if (!ready) message = "地图数据暂未读取成功，请返回重试，原编辑已保留"
    }
    DisposableEffect(owner) {
        fun refresh() {
            useAmap = locationPreferences.getBoolean("amap_consent_v1", false)
            precisePermission = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            permission = precisePermission || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
        refresh()
        val observer = LifecycleEventObserver { _, event ->
            when(event) {
                Lifecycle.Event.ON_RESUME -> { refresh(); foreground = true; view?.onResume() }
                Lifecycle.Event.ON_PAUSE -> { foreground = false; view?.onPause() }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permission = result.values.any { it }
        precisePermission = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!permission) setLocationPending(false)
        locationRetry++
        if (!permission) status("未授予定位权限，可以手动选择起点")
    }
    fun requestFreshLocation() {
        setLocationPending(true)
        if (amapConfigured && !useAmap) showLocationConsent = true
        if (!permission) {
            permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        if (!precisePermission) {
            status("当前是大致位置，正在显示位置范围；开启精确定位后才能从当前位置导航")
        } else if (lastLocation?.hasAccuracy() == true && lastLocation!!.accuracy > 50f) {
            status("当前定位精度约 ${lastLocation!!.accuracy.toInt()} 米，正在继续更新")
        }
        locationRetry++
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val output = java.io.ByteArrayOutputStream(); val bytes = ByteArray(8192)
                    while(true) { val count=input.read(bytes); if(count<0) break; require(output.size()+count<=5*1024*1024); output.write(bytes,0,count) }
                    output.toString("UTF-8").removePrefix("\uFEFF")
                } ?: error("无法读取编辑文件")
            } }.onSuccess { call("CampusApp.importText(${JSONObject.quote(it)})") }.onFailure { status("导入失败，已有数据保留") }
        }
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(pendingExport.toByteArray()) } ?: error("无法写入") } }
                .onSuccess { status("编辑数据已导出") }.onFailure { status("导出失败") }
        }
    }
    BackHandler(onBack = onBack)
    DisposableEffect(Unit) {
        onCoverBottomBar(true)
        onDispose { onCoverBottomBar(false); view?.removeJavascriptInterface("CampusNative"); view?.destroy(); view=null }
    }
    DisposableEffect(permission,precisePermission,foreground,pageReady,locationRetry,useAmap) {
        val tracker = CampusPositionTracker(context, useAmap, onFix = { location, system, cached ->
            setLocationPending(cached)
            locationSystem=system; lastLocation=location; lastLocationCached=cached; sendLocation(location, cached)
            if(lastHeading == null && location.hasBearing() && location.hasSpeed() && location.speed >= 1f)
                call("CampusApp.setHeading(${location.bearing})")
        }, onStatus = { text ->
            if (text.startsWith("未授予定位权限") || text.startsWith("系统定位已关闭")) setLocationPending(false)
            if (text.startsWith("正在更新当前位置") ||
                text.startsWith("正在获取可用的真实位置") ||
                text.startsWith("尚未取得当前位置") ||
                text.startsWith("尚未取得精确位置")
            ) {
                status("")
            } else {
                status(text)
            }
        }, onUnavailable = { lastLocation=null; lastLocationCached=false; call("CampusApp.clearLocation()") })
        if(permission && foreground && pageReady) {
            setLocationPending(true)
            lastLocation=null; call("CampusApp.clearLocation()")
            tracker.start()
        }
        else if(pageReady && !permission) { setLocationPending(false); call("CampusApp.clearLocation()"); status("未授予定位权限，点击定位可授权") }
        onDispose { tracker.stop() }
    }
    LaunchedEffect(pageReady,foreground) {
        if(pageReady && foreground) while(isActive) {
            lastLocation?.let { sendLocation(it, lastLocationCached) }
            delay(5_000)
        }
    }
    DisposableEffect(foreground) {
        val sensors=context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor=sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val matrix=FloatArray(9);val rotated=FloatArray(9);val orientation=FloatArray(3)
        var lastSent=0L
        val listener=object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if(event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) { lastHeading=null;call("CampusApp.setHeading(null)");return }
                val now=SystemClock.elapsedRealtime();if(now-lastSent<100) return;lastSent=now
                SensorManager.getRotationMatrixFromVector(matrix,event.values)
                @Suppress("DEPRECATION") val rotation=(context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
                val axes=when(rotation) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(matrix,axes.first,axes.second,rotated)
                SensorManager.getOrientation(rotated,orientation)
                val declination=lastLocation?.let { GeomagneticField(it.latitude.toFloat(),it.longitude.toFloat(),it.altitude.toFloat(),it.time).declination } ?: 0f
                lastHeading=(Math.toDegrees(orientation[0].toDouble())+declination+360)%360
                call("CampusApp.setHeading($lastHeading)")
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if(foreground && sensor != null) sensors.registerListener(listener,sensor,SensorManager.SENSOR_DELAY_UI)
        onDispose { sensors.unregisterListener(listener) }
    }
    LaunchedEffect(pageReady,schedule,foreground) {
        if(!pageReady || !foreground) return@LaunchedEffect
        while(isActive) {
            val overlays=withContext(Dispatchers.Default) { CampusScheduleOverlay.markers(schedule,repository.places()) }
            call("CampusApp.setScheduleMarkers($overlays)"); delay(30_000)
        }
    }
    // The edge-to-edge host does not otherwise pass the IME bottom inset to
    // the Compose child. Apply it at the single host boundary so the WebView
    // ends above the keyboard while its internal map resize remains debounced.
    DisposableEffect(context) {
        var host = context
        while (host is android.content.ContextWrapper && host !is android.app.Activity) host = host.baseContext
        val window = (host as? android.app.Activity)?.window
        val previous = window?.attributes?.softInputMode
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        onDispose { if (previous != null) window?.setSoftInputMode(previous) }
    }
    Column(
        modifier.fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.White)
            .navigationBarsPadding(),
    ) {
        QztcPageTopBar(
            title = when(initialMode) { "edit" -> "地图编辑设置"; "pick" -> "选择地点"; else -> "校园地图" },
            onBack = onBack,
            actions = {
                if (initialMode == "view") {
                    cn.qztc.timetable.core.ui.theme.QztcTopIconButton(
                        icon = Icons.Outlined.Edit,
                        contentDescription = "编辑地图",
                        onClick = { call("CampusApp.openSettings()") },
                    )
                }
            },
        )
        if(!ready && message.isNotBlank()) Text(message)
        if(ready) AndroidView(
            modifier=Modifier.fillMaxWidth().weight(1f).drawWithContent {
                if (mapFrame >= 0) drawContent()
            },
            factory={ ctx ->
            val loader=WebViewAssetLoader.Builder().addPathHandler("/assets/",WebViewAssetLoader.AssetsPathHandler(ctx)).build()
            CampusMapWebView(ctx, onFrame = { mapFrame = (mapFrame + 1) and Int.MAX_VALUE }).apply {
                view=this
                setBackgroundColor(android.graphics.Color.WHITE)
                isFocusable = true
                isFocusableInTouchMode = true
                setOnTouchListener { touched, _ ->
                    if (!touched.hasFocus()) touched.requestFocusFromTouch()
                    false
                }
                // The map owns long presses; do not enter WebView's native
                // selection/context-menu mode (which can suspend its surface).
                setOnLongClickListener { true }
                layoutParams=ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                minimumHeight=1
                settings.javaScriptEnabled=true;settings.domStorageEnabled=true
                settings.allowFileAccess=false;settings.allowContentAccess=false;settings.blockNetworkLoads=true
                addJavascriptInterface(OfflineMapBridge(repository,
                    locate={ requestFreshLocation() },
                    importAction={ importFile.launch(arrayOf("application/json","text/plain")) },
                    export={ pendingExport=it;exportFile.launch("泉师地图-地点编辑.json") },
                    navigate={ name,lat,lng ->
                        val coordinate=CampusAmapProjection.gcj02ToWgs84(CampusCoordinate(lat,lng))
                        val origin = lastLocation?.takeIf { CampusLiveFixPolicy.canNavigate(it.liveFix(), SystemClock.elapsedRealtime(), precisePermission) }
                            ?.let { CampusCoordinate(it.latitude,it.longitude).forExternalNavigation(locationSystem) }
                        runCatching { openCampusExternalNavigation(ctx,origin,CampusPoint("offline-destination",name,coordinate)) }.onFailure { status("无法打开外部地图") }
                    },pick={ picked(it) },requestKeyboard={
                        view?.let { web ->
                            if (!web.hasFocus()) web.requestFocusFromTouch()
                            web.postDelayed({
                                (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                                    .showSoftInput(web, InputMethodManager.SHOW_IMPLICIT)
                            }, 80L)
                        }
                    }, requestRedraw={ (view as? CampusMapWebView)?.refreshFrames() }),"CampusNative")
                webViewClient=object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView,request: WebResourceRequest): WebResourceResponse {
                        if(OfflineMapPolicy.allows(request.url.toString())) loader.shouldInterceptRequest(request.url)?.let { response ->
                            if(request.url.path?.endsWith(".webp")==true) response.mimeType="image/webp"
                            return response
                        }
                        return WebResourceResponse("text/plain","UTF-8",404,"Not Found",emptyMap(),ByteArrayInputStream(ByteArray(0)))
                    }
                    override fun shouldOverrideUrlLoading(view: WebView,request: WebResourceRequest)=!OfflineMapPolicy.allows(request.url.toString())
                    override fun onPageFinished(view: WebView,url: String) {
                        if(url != OfflineMapPolicy.START) return
                        view.post {
                            view.requestLayout()
                            view.invalidate()
                        }
                        pageReady=true;call("CampusApp.setMode(${JSONObject.quote(initialMode)})")
                        call("CampusApp.setAliases($legacyAliases)")
                        fun hex(color: androidx.compose.ui.graphics.Color) = "#%06x".format(color.toArgb() and 0xffffff)
                        val theme=JSONObject().put("accent",hex(colors.accent)).put("ink",hex(colors.ink))
                            .put("muted",hex(colors.mut)).put("surface",hex(colors.solid)).put("on-accent",hex(colors.onAccent))
                        call("CampusApp.setTheme($theme)")
                        // The mobile map owns the calibrated coordinates. For a room such as
                        // "朝阳409", pass the text through and let its catalog resolve the
                        // building instead of injecting the older native catalog coordinate.
                        val binding=destinationLocation?.takeIf { !it.placeId.isNullOrBlank() || it.latitude != null }
                        if(binding != null) {
                            val p=JSONObject().put("name",binding.name).put("placeId",binding.placeId)
                            if(binding.latitude != null) p.put("coordinate",JSONObject().put("lat",binding.latitude).put("lng",binding.longitude))
                            call("CampusApp.setDestination($p)")
                        } else call("CampusApp.setDestination(${JSONObject.quote(destination)})")
                        lastLocation?.let { sendLocation(it, lastLocationCached) };call("CampusApp.setHeading($lastHeading)")
                    }
                    override fun onReceivedError(view: WebView,request: WebResourceRequest,error: WebResourceError) {
                        if(request.isForMainFrame) status("地图加载失败，请返回重试")
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                            status("地图脚本错误：${message.message().take(140)}")
                        }
                        return true
                    }
                }
                loadUrl(OfflineMapPolicy.START)
            }.let { web ->
                // Keep WebView in a native ViewGroup so its compositor can
                // propagate descendant invalidations through AndroidView.
                android.widget.FrameLayout(ctx).apply { addView(web) }
            }
        })
    }
    if (showLocationConsent) {
        fun choose(enabled: Boolean) {
            locationPreferences.edit().putBoolean("amap_consent_v1", enabled).apply()
            useAmap = enabled
            showLocationConsent = false
        }
        AlertDialog(
            onDismissRequest = { choose(false) },
            title = { Text("启用高德定位") },
            text = { Column {
                Text("为改善校园定位，高德定位 SDK 会处理位置信息、设备及网络信息（包括 Wi-Fi、基站信息），并连接高德服务。仅在地图前台使用，退出后停止；不同意仍可使用系统定位和手动选点。可在隐私与关于中关闭。")
                TextButton(onClick = {
                    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://lbs.amap.com/pages/privacy/"))) }
                }) { Text("高德隐私政策") }
            } },
            confirmButton = { TextButton(onClick = { choose(true) }) { Text("同意并启用") } },
            dismissButton = { TextButton(onClick = { choose(false) }) { Text("使用系统定位") } },
        )
    }
}
/** Drive a short native redraw burst after WebView DOM changes so AndroidView
 * does not retain the previous software display list. */
private class CampusMapWebView(context: Context, private val onFrame: () -> Unit) : WebView(context) {
    private var redrawUntil = 0L
    private var redrawScheduled = false
    private val redraw = object : Runnable {
        override fun run() {
            redrawScheduled = false
            if (!isAttachedToWindow || windowVisibility != android.view.View.VISIBLE) return
            onFrame()
            invalidate()
            (parent as? android.view.View)?.invalidate()
            // A software WebView embedded in AndroidView may keep the previous
            // display list until a property invalidation reaches the host.
            // A sub-percent alpha pulse is visually imperceptible but forces
            // that list to be submitted again.
            alpha = 0.999f
            post { alpha = 1f }
            if (SystemClock.uptimeMillis() < redrawUntil) {
                redrawScheduled = true
                postOnAnimation(this)
            }
        }
    }
    fun refreshFrames() {
        redrawUntil = SystemClock.uptimeMillis() + 500L
        if (!redrawScheduled) { redrawScheduled = true; postOnAnimation(redraw) }
    }
    override fun onDetachedFromWindow() {
        removeCallbacks(redraw)
        redrawScheduled = false
        super.onDetachedFromWindow()
    }
}
