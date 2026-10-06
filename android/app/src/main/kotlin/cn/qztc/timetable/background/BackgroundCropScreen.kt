package cn.qztc.timetable.background

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcPageTopBar
import cn.qztc.timetable.core.ui.theme.QztcSpacing
import cn.qztc.timetable.core.ui.theme.QztcTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

data class CropPosition(val x: Float = .5f, val y: Float = .5f)

fun updateCropPosition(
    current: CropPosition,
    drag: Offset,
    frame: IntSize,
    image: IntSize,
): CropPosition {
    if (frame.width <= 0 || frame.height <= 0 || image.width <= 0 || image.height <= 0) return current
    val scale = max(frame.width / image.width.toFloat(), frame.height / image.height.toFloat())
    val overflowX = image.width * scale - frame.width
    val overflowY = image.height * scale - frame.height
    return CropPosition(
        x = if (overflowX > .5f) (current.x - drag.x / overflowX).coerceIn(0f, 1f) else .5f,
        y = if (overflowY > .5f) (current.y - drag.y / overflowY).coerceIn(0f, 1f) else .5f,
    )
}

@Composable
fun BackgroundCropScreen(
    path: String,
    initialPosition: CropPosition,
    onCancel: () -> Unit,
    onApply: (CropPosition) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalQztcColors.current
    var bitmap by remember(path) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) { decodeBackgroundBitmap(path) }
    }
    var position by remember(path) { mutableStateOf(initialPosition) }
    var frameSize by remember { mutableStateOf(IntSize.Zero) }
    Column(
        modifier.fillMaxSize().background(colors.solid2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        QztcPageTopBar(title = "裁剪背景", onBack = onCancel)
        Text("拖动图片选择保留区域", style = QztcTypography.body, color = colors.mut)
        Box(
            Modifier
                .padding(vertical = QztcSpacing.lg)
                .fillMaxWidth(.62f)
                .aspectRatio(393f / 852f)
                .border(2.dp, colors.accent)
                .onSizeChanged { frameSize = it }
                .pointerInput(bitmap, frameSize) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        bitmap?.let {
                            position = updateCropPosition(position, dragAmount, frameSize, IntSize(it.width, it.height))
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "背景裁剪预览",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = BiasAlignment(position.x * 2f - 1f, position.y * 2f - 1f),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = QztcSpacing.xl),
            horizontalArrangement = Arrangement.spacedBy(QztcSpacing.md),
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("取消") }
            Button(onClick = { onApply(position) }, enabled = bitmap != null, modifier = Modifier.weight(1f)) { Text("应用") }
        }
    }
}
