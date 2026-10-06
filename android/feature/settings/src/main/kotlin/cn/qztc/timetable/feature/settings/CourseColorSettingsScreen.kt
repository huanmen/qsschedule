package cn.qztc.timetable.feature.settings

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.ui.glass.GlassRole
import cn.qztc.timetable.core.ui.glass.GlassSurface
import cn.qztc.timetable.core.ui.theme.CoursePalette
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcPageTopBar
import cn.qztc.timetable.core.ui.theme.QztcSpacing
import cn.qztc.timetable.core.ui.theme.QztcTypography
import cn.qztc.timetable.core.domain.model.Course

@Composable
fun CourseColorSettingsScreen(
    presetColors: List<Int>,
    onColorsChange: (List<Int>) -> Unit,
    courses: List<Course> = emptyList(),
    onCourseColorChange: (String, Int) -> Unit = { _, _ -> },
    onBack: () -> Unit,
) {
    val colors = LocalQztcColors.current
    var selectedCourse by remember { mutableStateOf<Course?>(null) }
    var customColorCourse by remember { mutableStateOf<Course?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.solid2),
        contentPadding = PaddingValues(bottom = QztcSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(QztcSpacing.lg),
    ) {
        item {
            QztcPageTopBar(title = "课程颜色", onBack = onBack)
        }
        item {
            Column(
                modifier = Modifier.padding(horizontal = QztcSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(QztcSpacing.sm),
            ) {
                Text("预选颜色", style = QztcTypography.section, color = colors.mut)
                Text(
                    "编辑课程时可以直接从这里选择。颜色的增删会立即保存。",
                    style = QztcTypography.meta,
                    color = colors.mut,
                )
            }
        }
        item {
            CourseColorEditor(
                presetColors = presetColors,
                onColorsChange = onColorsChange,
            )
        }
        item {
            Text("调整当前课程颜色", style = QztcTypography.section, color = colors.ink,
                modifier = Modifier.padding(horizontal = QztcSpacing.lg))
        }
        if (courses.isEmpty()) item {
            Text("当前学期暂无课程", style = QztcTypography.meta, color = colors.mut,
                modifier = Modifier.padding(horizontal = QztcSpacing.lg))
        }
        courses.forEach { course -> item(key = course.id) {
            Row(Modifier.fillMaxWidth().clickable { selectedCourse = course }
                .padding(horizontal = QztcSpacing.lg, vertical = QztcSpacing.md),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(currentSwatchColor(course.color ?: 0)))
                Text(course.name, Modifier.weight(1f).padding(start = 12.dp), style = QztcTypography.body, color = colors.ink)
            }
        } }
    }
    selectedCourse?.let { course ->
        AlertDialog(onDismissRequest = { selectedCourse = null }, title = { Text(course.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    presetColors.chunked(6).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { value ->
                                Box(Modifier.size(38.dp).clip(CircleShape).background(currentSwatchColor(value))
                                    .clickable { onCourseColorChange(course.id, value); selectedCourse = null })
                            }
                        }
                    }
                    Text("打开调色盘", Modifier.fillMaxWidth().clickable {
                        customColorCourse = course; selectedCourse = null
                    }.padding(vertical = 12.dp), color = colors.accentDeep)
                }
            }, confirmButton = { TextButton(onClick = { selectedCourse = null }) { Text("取消") } })
    }
    customColorCourse?.let { course ->
        CourseColorPickerDialog(initialColor = currentSwatchArgb(course.color ?: 0),
            confirmLabel = "应用到课程",
            onDismiss = { customColorCourse = null },
            onConfirm = { value -> onCourseColorChange(course.id, value); customColorCourse = null })
    }
}

@Composable
fun CourseColorEditor(
    presetColors: List<Int>,
    onColorsChange: (List<Int>) -> Unit,
) {
    val colors = LocalQztcColors.current
    var pickerOpen by remember { mutableStateOf(false) }
    var pickerInitialColor by remember { mutableStateOf(currentSwatchArgb(presetColors.firstOrNull() ?: 0)) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        presetColors.chunked(3).forEach { rowColors ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowColors.forEach { value ->
                    PresetColorTile(
                        value = value,
                        modifier = Modifier.weight(1f),
                        onDelete = {
                            onColorsChange(presetColors.filterNot { it == value })
                        },
                    )
                }
                repeat(3 - rowColors.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (presetColors.isEmpty()) {
            Text("还没有预选颜色，添加一个开始使用。", style = QztcTypography.meta, color = colors.mut)
        }
        OutlinedButton(
            onClick = {
                pickerInitialColor = currentSwatchArgb(presetColors.firstOrNull() ?: 0)
                pickerOpen = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("添加预选颜色")
        }
    }
    if (pickerOpen) {
        CourseColorPickerDialog(
            initialColor = pickerInitialColor,
            onDismiss = { pickerOpen = false },
            onConfirm = { color ->
                if (color !in presetColors) onColorsChange(presetColors + color)
                pickerOpen = false
            },
        )
    }
}

@Composable
private fun PresetColorTile(
    value: Int,
    modifier: Modifier,
    onDelete: () -> Unit,
) {
    val colors = LocalQztcColors.current
    val swatch = currentSwatchColor(value)
    val foreground = if (swatch.luminance() > .48f) Color(0xFF173B35) else Color.White
    Box(
        modifier
            .aspectRatio(1.55f)
            .clip(RoundedCornerShape(14.dp))
            .background(swatch),
    ) {
        Text(
            text = "课程色",
            style = QztcTypography.compact,
            color = foreground.copy(alpha = .9f),
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
        )
        IconButton(onClick = onDelete, modifier = Modifier.align(Alignment.TopEnd).size(36.dp)) {
            Icon(Icons.Outlined.Delete, contentDescription = "删除预选颜色", tint = foreground.copy(alpha = .88f))
        }
    }
}

@Composable
private fun CourseColorPickerDialog(
    initialColor: Int,
    confirmLabel: String = "加入预选",
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val hsv = remember(initialColor) {
        FloatArray(3).also { AndroidColor.colorToHSV(initialColor, it) }
    }
    var hue by remember(initialColor) { mutableFloatStateOf(hsv[0]) }
    var saturation by remember(initialColor) { mutableFloatStateOf(hsv[1]) }
    var value by remember(initialColor) { mutableFloatStateOf(hsv[2].coerceAtLeast(.12f)) }
    val selected = Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value)))
    val colors = LocalQztcColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("调色盘") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 430.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SaturationValuePicker(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onChange = { s, v -> saturation = s; value = v },
                )
                HuePicker(hue = hue, onChange = { hue = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(selected))
                    Spacer(Modifier.size(10.dp))
                    Text("拖动上方区域选择颜色", style = QztcTypography.meta, color = colors.mut)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))) },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SaturationValuePicker(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (Float, Float) -> Unit,
) {
    val hueColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(14.dp))
            .pointerInput(hue) {
                detectTapGestures { position ->
                    onChange(
                        (position.x / size.width).coerceIn(0f, 1f),
                        (1f - position.y / size.height).coerceIn(.02f, 1f),
                    )
                }
            }
            .pointerInput(hue) {
                detectDragGestures(
                    onDragStart = { position ->
                        onChange(
                            (position.x / size.width).coerceIn(0f, 1f),
                            (1f - position.y / size.height).coerceIn(.02f, 1f),
                        )
                    },
                ) { change, _ ->
                    change.consume()
                    onChange(
                        (change.position.x / size.width).coerceIn(0f, 1f),
                        (1f - change.position.y / size.height).coerceIn(.02f, 1f),
                    )
                }
            },
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        drawCircle(
            color = Color.White,
            radius = 9.dp.toPx(),
            center = Offset(saturation * size.width, (1f - value) * size.height),
        )
        drawCircle(
            color = Color.Black.copy(alpha = .55f),
            radius = 10.dp.toPx(),
            center = Offset(saturation * size.width, (1f - value) * size.height),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
        )
    }
}

@Composable
private fun HuePicker(hue: Float, onChange: (Float) -> Unit) {
    val hues = listOf(
        Color.Red,
        Color.Yellow,
        Color.Green,
        Color.Cyan,
        Color.Blue,
        Color.Magenta,
        Color.Red,
    )
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .pointerInput(Unit) {
                detectTapGestures { position -> onChange((position.x / size.width * 360f).coerceIn(0f, 360f)) }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { position -> onChange((position.x / size.width * 360f).coerceIn(0f, 360f)) },
                ) { change, _ ->
                    change.consume()
                    onChange((change.position.x / size.width * 360f).coerceIn(0f, 360f))
                }
            },
    ) {
        drawRect(Brush.horizontalGradient(hues))
        drawCircle(
            color = Color.White,
            radius = 10.dp.toPx(),
            center = Offset(hue / 360f * size.width, size.height / 2f),
        )
        drawCircle(
            color = Color.Black.copy(alpha = .55f),
            radius = 11.dp.toPx(),
            center = Offset(hue / 360f * size.width, size.height / 2f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
        )
    }
}

private fun currentSwatchArgb(value: Int): Int =
    if (value in CoursePalette.indices) CoursePalette[value].bar.toArgb() else value

private fun currentSwatchColor(value: Int): Color =
    if (value in CoursePalette.indices) CoursePalette[value].bar else Color(value)
