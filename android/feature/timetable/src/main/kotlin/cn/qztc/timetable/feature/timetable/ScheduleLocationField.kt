package cn.qztc.timetable.feature.timetable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.qztc.timetable.core.domain.model.ScheduleLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun ScheduleLocationField(value: ScheduleLocation, onChange: (ScheduleLocation) -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { OfflinePlaceRepository(context) }
    var ready by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf(emptyList<OfflinePlace>()) }
    var linkedName by remember(value.placeId) { mutableStateOf<String?>(null) }
    LaunchedEffect(repository) { ready = repository.migrateWebStorage(context) }
    LaunchedEffect(ready, value.name, value.placeId, focused) {
        if (!ready) return@LaunchedEffect
        delay(120)
        withContext(Dispatchers.IO) {
            repository.search(value.name) to repository.places().find { it.id == value.placeId }?.name
        }.let { (found, name) -> results = found; linkedName = name }
    }
    Column {
        OutlinedTextField(value = value.name, onValueChange = { focused = true; onChange(value.copy(name = it.take(160))) },
            label = { Text("地点") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) focused = true },
            trailingIcon = { IconButton(onClick = { picking = true }) { Icon(Icons.Outlined.Map, "在地图上选择地点") } })
        if (value.placeId != null || value.latitude != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(linkedName?.let { "关联：$it" } ?: if (value.placeId != null) "关联地点不存在，请重新选择" else "已设置临时位置",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(top = 12.dp))
                IconButton(onClick = { onChange(ScheduleLocation(value.name)) }) { Icon(Icons.Outlined.Close, "解除地点关联") }
            }
        }
        if (focused && value.name.isNotBlank()) results.take(5).forEach { place ->
            Text(place.name, modifier = Modifier.fillMaxWidth().clickable {
                focused = false
                onChange(place.binding(value.name.ifBlank { place.name }))
            }.padding(vertical = 12.dp, horizontal = 8.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (picking) Dialog(onDismissRequest = { picking = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CampusRouteScreen(rawDestination = value.name, onBack = { picking = false }, initialMode = "pick",
            onLocationPicked = { picked -> onChange(picked.copy(name = value.name.ifBlank { picked.name })); focused = false; picking = false })
    }
}
