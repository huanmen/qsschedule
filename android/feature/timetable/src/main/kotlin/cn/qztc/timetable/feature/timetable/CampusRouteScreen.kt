package cn.qztc.timetable.feature.timetable

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.ui.glass.GlassRole
import cn.qztc.timetable.core.ui.glass.GlassSurface
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcPageTopBar
import cn.qztc.timetable.core.ui.theme.QztcSpacing
import cn.qztc.timetable.core.ui.theme.QztcTopIconButton
import cn.qztc.timetable.core.ui.theme.QztcTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@SuppressLint("MissingPermission")
@Composable
internal fun LegacyCampusRouteScreen(
    rawDestination: String,
    onBack: () -> Unit,
    onCoverBottomBar: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val colors = LocalQztcColors.current
    val imeVisible = WindowInsets.isImeVisible
    DisposableEffect(Unit) {
        onCoverBottomBar(true)
        onDispose { onCoverBottomBar(false) }
    }
    val aliasStore = remember(context) { CampusAliasStore(context) }
    val extraOverrides = remember(context) { aliasStore.loadOverrides() }
    val sourcePlaces = remember(context) { CampusPlaceRepository.load(context) }
    val placeCatalog = remember(sourcePlaces, extraOverrides) {
        sourcePlaces.applyOverrides(extraOverrides)
    }
    val mapDataset = remember(context) { CampusMapRepository.load(context) }
    val campusBounds = mapDataset.bounds
    var destination by remember(rawDestination, placeCatalog) {
        mutableStateOf(placeCatalog.resolve(rawDestination))
    }
    var searchQuery by remember(rawDestination) { mutableStateOf(rawDestination) }
    var committedSearchQuery by remember(rawDestination) { mutableStateOf(rawDestination.trim()) }
    var searchResults by remember { mutableStateOf<List<CampusSearchResult>>(emptyList()) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var browsePlaces by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(context.hasLocationPermission()) }
    var currentLocation by remember { mutableStateOf<CampusCoordinate?>(null) }
    var lastSample by remember { mutableStateOf<CampusLocationSample?>(null) }
    var followUser by remember { mutableStateOf(true) }
    var headingDegrees by remember { mutableStateOf<Float?>(null) }
    var expandedZones by remember { mutableStateOf(setOf<String>()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(locationPermissions)
    }

    fun applyLocation(location: Location) {
        val sample = location.toCampusSample()
        val previous = lastSample
        if (previous != null) {
            val previousIsFreshGps = previous.provider.equals("gps", true) &&
                System.currentTimeMillis() - previous.timeMillis < 20_000 &&
                previous.accuracyMeters <= 40f
            val nextIsNetwork = !sample.provider.equals("gps", true)
            if (previousIsFreshGps && nextIsNetwork) return
        }
        lastSample = sample
        currentLocation = CampusLocationFix.toMapCoordinate(sample, campusBounds, mapDataset.campusBoundary)
    }

    fun requestCurrentLocation() {
        if (!hasPermission) return
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val now = System.currentTimeMillis()
        val providers = enabledLocationProviders(manager)
        val samples = providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()?.toCampusSample()
        }
        CampusLocationFix.pick(samples, campusBounds, now, mapDataset.campusBoundary)?.let { picked ->
            val mapped = CampusLocationFix.toMapCoordinate(picked, campusBounds, mapDataset.campusBoundary)
            if (CampusLocationFix.isNearCampus(mapped, campusBounds)) {
                lastSample = picked
                currentLocation = mapped
            }
        }
        val targets = providers.ifEmpty { locationProviders() }
        if (Build.VERSION.SDK_INT >= 30) {
            val executor = ContextCompat.getMainExecutor(context)
            targets.forEach { provider ->
                runCatching {
                    manager.getCurrentLocation(provider, null, executor) { loc ->
                        loc?.let(::applyLocation)
                    }
                }
            }
        } else {
            targets.forEach { provider ->
                runCatching {
                    @Suppress("DEPRECATION")
                    manager.requestSingleUpdate(provider, { loc -> applyLocation(loc) }, Looper.getMainLooper())
                }
            }
        }
    }

    LaunchedEffect(hasPermission) { if (hasPermission) requestCurrentLocation() }
    DisposableEffect(hasPermission) {
        if (!hasPermission) return@DisposableEffect onDispose { }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                applyLocation(location)
            }

            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
            @Deprecated("Deprecated by Android")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        if (context.hasLocationPermission()) {
            enabledLocationProviders(manager).ifEmpty { locationProviders() }.forEach { provider ->
                runCatching {
                    manager.requestLocationUpdates(provider, 1_000L, 2f, listener, Looper.getMainLooper())
                }
            }
        }
        onDispose { runCatching { manager.removeUpdates(listener) } }
    }

    DisposableEffect(Unit) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: return@DisposableEffect onDispose { }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val matrix = FloatArray(9)
                val remapped = FloatArray(9)
                val orientation = FloatArray(3)
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                val rotation = if (Build.VERSION.SDK_INT >= 30) {
                    context.display?.rotation ?: Surface.ROTATION_0
                } else {
                    @Suppress("DEPRECATION")
                    (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
                }
                val axisX: Int
                val axisY: Int
                when (rotation) {
                    Surface.ROTATION_90 -> {
                        axisX = SensorManager.AXIS_Y
                        axisY = SensorManager.AXIS_MINUS_X
                    }
                    Surface.ROTATION_180 -> {
                        axisX = SensorManager.AXIS_MINUS_X
                        axisY = SensorManager.AXIS_MINUS_Y
                    }
                    Surface.ROTATION_270 -> {
                        axisX = SensorManager.AXIS_MINUS_Y
                        axisY = SensorManager.AXIS_X
                    }
                    else -> {
                        axisX = SensorManager.AXIS_X
                        axisY = SensorManager.AXIS_Y
                    }
                }
                SensorManager.remapCoordinateSystem(matrix, axisX, axisY, remapped)
                SensorManager.getOrientation(remapped, orientation)
                headingDegrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { manager.unregisterListener(listener) }
    }

    val trackedLocation = currentLocation
    val nearCampus = trackedLocation?.let { CampusLocationFix.isNearCampus(it, campusBounds) } == true
    val onCampus = trackedLocation?.let { campusBounds.contains(it, paddingRatio = 0.2) } == true
    val entrance = placeCatalog.point("qnu-main-entrance")
    val originCoordinate = if (onCampus) trackedLocation else entrance?.coordinate
    val originLabel = if (onCampus) "当前位置" else entrance?.name ?: "校门"
    val selectedDestination = destination
    var route by remember { mutableStateOf<CampusMapRoute?>(null) }
    var routeError by remember { mutableStateOf<String?>(null) }

    fun dismissPlaceOverlay() {
        searchResults = emptyList()
        searchError = null
        browsePlaces = false
        focusManager.clearFocus()
    }

    LaunchedEffect(searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank() || query == committedSearchQuery) {
            searchResults = emptyList()
            searchError = null
            return@LaunchedEffect
        }
        val local = placeCatalog.search(query)
        searchResults = local.map { it.asSearchResult() }
        searchError = if (local.isEmpty()) "校园范围内没有找到相关地点" else null
    }

    LaunchedEffect(originCoordinate, selectedDestination, mapDataset) {
        if (originCoordinate == null || selectedDestination == null) {
            route = null
            routeError = null
            return@LaunchedEffect
        }
        val next = withContext(Dispatchers.Default) {
            CampusWalkRouter.route(mapDataset, originCoordinate, selectedDestination.coordinate)
        }
        route = next
        routeError = null
    }
    val currentRoute = route
    val mapCurrent = if (nearCampus) trackedLocation else originCoordinate ?: selectedDestination?.coordinate ?: entrance?.coordinate
    val currentLabel = if (nearCampus) "当前位置" else "起点"
    val revealedIds = remember(searchResults, selectedDestination, searchQuery) {
        buildSet {
            selectedDestination?.id?.let(::add)
            if (CampusDormZones.zoneFromQuery(searchQuery) == null) {
                searchResults.forEach { result -> if (result.id.isNotBlank()) add(result.id) }
            }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.solid2),
    ) {
        QztcPageTopBar(
            title = "校内导航",
            subtitle = when {
                selectedDestination != null -> selectedDestination.name
                else -> "选择可导航地点"
            },
            onBack = onBack,
            actions = {
                QztcTopIconButton(
                    icon = Icons.Outlined.MyLocation,
                    contentDescription = if (followUser && nearCampus) "正在跟随当前位置" else "定位到我的位置",
                    tint = if (followUser && nearCampus) colors.accentDeep else null,
                    onClick = {
                        if (hasPermission) {
                            followUser = true
                            requestCurrentLocation()
                        } else {
                            permissionLauncher.launch(locationPermissions)
                        }
                    },
                )
            },
        )

        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.solid2.copy(alpha = 0.94f))
                .padding(horizontal = QztcSpacing.lg, vertical = QztcSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(QztcSpacing.sm),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    if (it.trim() != committedSearchQuery) committedSearchQuery = ""
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("汉字或拼音，如 rml、荣茂楼") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        searchQuery = searchQuery.trim()
                        focusManager.clearFocus()
                    },
                ),
            )
            Icon(Icons.Outlined.Search, contentDescription = "搜索地点", tint = colors.accent)
            Text(
                if (browsePlaces) "收起" else "地点",
                style = QztcTypography.meta,
                color = colors.accent,
                modifier = Modifier.clickable { browsePlaces = !browsePlaces },
            )
        }

        val showOverlay = searchResults.isNotEmpty() || searchError != null || browsePlaces
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = QztcSpacing.lg, vertical = QztcSpacing.sm),
        ) {
            if (mapCurrent != null) {
                CampusMapCanvas(
                    dataset = mapDataset,
                    route = currentRoute?.points.orEmpty(),
                    current = mapCurrent,
                    currentLabel = currentLabel,
                    destination = selectedDestination,
                    landmarks = placeCatalog.places,
                    modifier = Modifier.fillMaxSize(),
                    headingDegrees = headingDegrees.takeIf { nearCampus },
                    followUser = followUser && nearCampus,
                    revealedIds = revealedIds,
                    expandedZones = expandedZones,
                    onUserMove = { followUser = false },
                    onZoneTap = { zone ->
                        expandedZones = if (zone in expandedZones) expandedZones - zone else expandedZones + zone
                    },
                    onPlaceTap = { place ->
                        val next = CampusDormZones.destinationAfterTap(destination, place)
                        followUser = false
                        destination = next
                        if (next == null) {
                            searchQuery = ""
                            committedSearchQuery = ""
                        } else {
                            searchQuery = place.name
                            committedSearchQuery = place.name
                        }
                    },
                )
            }
            if (mapDataset.features.isEmpty()) {
                Text(
                    mapDataset.attribution.ifBlank { "校园底图暂不可用" },
                    style = QztcTypography.meta,
                    color = colors.mut,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (showOverlay) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(colors.ink.copy(alpha = 0.08f))
                        .clickable { dismissPlaceOverlay() },
                )
                CampusPlacePicker(
                    groupedPlaces = placeCatalog.groupedNavigablePlaces(),
                    searchResults = searchResults,
                    searchError = searchError,
                    browsing = searchResults.isEmpty(),
                    onSelect = { place ->
                        destination = place
                        searchQuery = place.name
                        committedSearchQuery = place.name
                        dismissPlaceOverlay()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(colors.solid)
                        .clickable(onClick = {}),
                )
            }
        }

        if (!imeVisible) {
            GlassSurface(GlassRole.CONTENT, Modifier.fillMaxWidth().padding(start = QztcSpacing.lg, end = QztcSpacing.lg, bottom = QztcSpacing.sm)) {
                Column(Modifier.padding(QztcSpacing.lg), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.NearMe, contentDescription = null, tint = colors.accent)
                        Spacer(Modifier.width(QztcSpacing.sm))
                        Text("步行路线", style = QztcTypography.courseTitle, color = colors.ink)
                    }
                    Text(
                        when {
                            selectedDestination == null -> "从列表或搜索中选择校内地点，即可看到步行路线。"
                            currentRoute == null -> "暂未获取路线"
                            else -> "$originLabel → ${selectedDestination.name} · 约 ${currentRoute.distanceMeters} 米"
                        },
                        style = QztcTypography.body,
                        color = colors.ink,
                    )
                    currentRoute?.steps.orEmpty().take(3).forEach { step ->
                        if (step.instruction.isNotBlank()) {
                            Text(step.instruction, style = QztcTypography.meta, color = colors.mut)
                        }
                    }
                    Text(
                        when {
                            trackedLocation != null && !nearCampus -> "当前定位不在东海校区附近。点右上角定位会重新取点；校内步行仍以校门为起点。"
                            trackedLocation != null -> "离线校园图沿路网规划步行。宿舍先显示 A/B/C/D 区，搜到具体楼栋后再展开。"
                            else -> "未取得校内定位时，以东海校区大门为起点。点右上角定位到你的位置。"
                        },
                        style = QztcTypography.meta,
                        color = colors.mut,
                    )
                    if (selectedDestination != null) {
                        Button(
                            onClick = {
                                if (!openCampusExternalNavigation(context, originCoordinate, selectedDestination)) {
                                    routeError = "当前设备没有可用的地图软件或浏览器"
                                }
                            },
                        ) {
                            Text("地图软件导航")
                        }
                    }
                    if (!hasPermission) {
                        Text(
                            "允许定位后，可用当前位置更新路线。",
                            style = QztcTypography.meta,
                            color = colors.accentDeep,
                            modifier = Modifier.clickable { permissionLauncher.launch(locationPermissions) }.padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun locationProviders(): List<String> = buildList {
    add(LocationManager.GPS_PROVIDER)
    add(LocationManager.NETWORK_PROVIDER)
    if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
}

private fun enabledLocationProviders(manager: LocationManager): List<String> =
    locationProviders().filter { provider ->
        runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
    }

@Composable
private fun CampusPlacePicker(
    groupedPlaces: List<Pair<String, List<CampusPoint>>>,
    searchResults: List<CampusSearchResult>,
    searchError: String?,
    browsing: Boolean,
    onSelect: (CampusPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalQztcColors.current
    LazyColumn(
        modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .padding(horizontal = QztcSpacing.sm, vertical = QztcSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        searchError?.let {
            item { Text(it, style = QztcTypography.meta, color = colors.mut) }
        }
        if (searchResults.isNotEmpty()) {
            items(searchResults, key = { it.id.ifBlank { it.name } }) { result ->
                CampusPlaceRow(
                    title = result.name,
                    subtitle = result.address,
                    onClick = { onSelect(result.point()) },
                )
            }
        } else if (browsing) {
            groupedPlaces.forEach { (group, places) ->
                item(key = "group-$group") {
                    Text(
                        group,
                        style = QztcTypography.meta,
                        color = colors.mut,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                    )
                }
                items(places, key = { it.id }) { place ->
                    CampusPlaceRow(
                        title = place.name,
                        subtitle = place.aliases.firstOrNull().orEmpty(),
                        onClick = { onSelect(place) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CampusPlaceRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val colors = LocalQztcColors.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = QztcTypography.body, color = colors.ink)
            if (subtitle.isNotBlank()) Text(subtitle, style = QztcTypography.meta, color = colors.mut)
        }
        Text("校园内", style = QztcTypography.meta, color = colors.accent)
    }
}

private val locationPermissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

private fun Location.toCampusSample(): CampusLocationSample {
    val elapsed = elapsedRealtimeNanos
    val ageMs = if (elapsed > 0L) {
        (SystemClock.elapsedRealtimeNanos() - elapsed).coerceAtLeast(0L) / 1_000_000L
    } else {
        null
    }
    val timeMillis = when {
        ageMs != null -> System.currentTimeMillis() - ageMs
        time > 0L -> time
        else -> System.currentTimeMillis()
    }
    return CampusLocationSample(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else 50f,
        timeMillis = timeMillis,
        provider = provider ?: "gps",
    )
}

private fun Context.hasLocationPermission(): Boolean =
    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
