package cn.qztc.timetable.feature.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcPageTopBar
import cn.qztc.timetable.core.ui.theme.QztcSpacing
import cn.qztc.timetable.core.ui.theme.QztcTypography
import java.util.UUID

@Composable
fun CampusAliasSettingsScreen(
    onBack: () -> Unit,
    initialCoordinate: CampusCoordinate? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalQztcColors.current
    val context = LocalContext.current
    val store = remember(context) { CampusAliasStore(context) }
    val catalog = remember(context) { CampusPlaceRepository.load(context) }
    var overrides by remember { mutableStateOf(store.loadOverrides()) }
    var query by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(initialCoordinate != null) }
    val visible = remember(query, catalog, overrides) {
        val applied = catalog.applyOverrides(overrides.copy(hiddenIds = emptySet()))
        if (query.isBlank()) applied.places else catalog.applyOverrides(overrides.copy(hiddenIds = emptySet())).search(query, limit = 40)
    }

    fun persist(next: CampusPlaceOverrides) {
        overrides = next
        store.saveOverrides(next)
    }

    Column(modifier.fillMaxSize().background(colors.solid2)) {
        QztcPageTopBar(
            title = "地点管理",
            subtitle = "本地隐藏、新增，以及同一地点的多个名称",
            onBack = onBack,
            actions = {
                TextButton(onClick = { adding = true }) { Text("新增") }
            },
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = QztcSpacing.lg),
            singleLine = true,
            placeholder = { Text("筛选地点") },
        )
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = QztcSpacing.lg, vertical = QztcSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(QztcSpacing.md),
        ) {
            items(visible, key = { it.id }) { place ->
                PlaceEditor(
                    place = place,
                    extraNames = overrides.aliases[place.id].orEmpty(),
                    hidden = place.id in overrides.hiddenIds,
                    custom = overrides.extras.any { it.id == place.id },
                    onHideChange = { hidden ->
                        persist(
                            overrides.copy(
                                hiddenIds = if (hidden) overrides.hiddenIds + place.id else overrides.hiddenIds - place.id,
                            ),
                        )
                    },
                    onAliasesChange = { names ->
                        persist(
                            overrides.copy(
                                aliases = overrides.aliases.toMutableMap().apply {
                                    if (names.isEmpty()) remove(place.id) else put(place.id, names)
                                },
                            ),
                        )
                    },
                    onDeleteCustom = {
                        persist(
                            overrides.copy(
                                extras = overrides.extras.filterNot { it.id == place.id },
                                hiddenIds = overrides.hiddenIds - place.id,
                                aliases = overrides.aliases - place.id,
                            ),
                        )
                    },
                )
            }
        }
    }

    if (adding) {
        AddPlaceDialog(
            initial = initialCoordinate,
            onDismiss = { adding = false },
            onSave = { name, latitude, longitude ->
                persist(
                    overrides.copy(
                        extras = overrides.extras + CampusCustomPlace(
                            id = "custom:${UUID.randomUUID()}",
                            name = name,
                            latitude = latitude,
                            longitude = longitude,
                        ),
                    ),
                )
                adding = false
            },
        )
    }
}

@Composable
private fun PlaceEditor(
    place: CampusPoint,
    extraNames: Set<String>,
    hidden: Boolean,
    custom: Boolean,
    onHideChange: (Boolean) -> Unit,
    onAliasesChange: (Set<String>) -> Unit,
    onDeleteCustom: () -> Unit,
) {
    val colors = LocalQztcColors.current
    var draft by remember(place.id) { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth().background(colors.solid, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).padding(QztcSpacing.md),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(place.name, style = QztcTypography.courseTitle, color = colors.ink)
                Text(
                    if (custom) "本地新增" else "目录地点",
                    style = QztcTypography.meta,
                    color = colors.mut,
                )
            }
            Text("显示", style = QztcTypography.meta, color = colors.mut)
            Switch(checked = !hidden, onCheckedChange = { onHideChange(!it) })
        }
        if (place.aliases.isNotEmpty()) {
            Text("目录别称：${place.aliases.joinToString("、")}", style = QztcTypography.meta, color = colors.mut)
        }
        extraNames.forEach { name ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = QztcTypography.body, color = colors.ink, modifier = Modifier.weight(1f))
                TextButton(onClick = { onAliasesChange(extraNames - name) }) { Text("移除") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QztcSpacing.sm)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("添加名称，如拼音或简称") },
            )
            TextButton(
                onClick = {
                    val value = draft.trim()
                    if (value.isNotEmpty()) {
                        onAliasesChange(extraNames + value)
                        draft = ""
                    }
                },
            ) { Text("添加") }
        }
        if (custom) {
            TextButton(onClick = onDeleteCustom) { Text("删除这个本地地点") }
        }
    }
}

@Composable
internal fun AddPlaceDialog(
    initial: CampusCoordinate?,
    onDismiss: () -> Unit,
    onSave: (String, Double, Double) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf(initial?.latitude?.toString().orEmpty()) }
    var lon by remember { mutableStateOf(initial?.longitude?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增本地地点") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("名称") })
                OutlinedTextField(value = lat, onValueChange = { lat = it }, singleLine = true, label = { Text("纬度") })
                OutlinedTextField(value = lon, onValueChange = { lon = it }, singleLine = true, label = { Text("经度") })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val latitude = lat.toDoubleOrNull()
                    val longitude = lon.toDoubleOrNull()
                    if (name.isNotBlank() && latitude != null && longitude != null) {
                        onSave(name.trim(), latitude, longitude)
                    }
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
