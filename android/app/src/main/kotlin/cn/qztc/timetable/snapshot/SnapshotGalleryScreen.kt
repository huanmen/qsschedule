package cn.qztc.timetable.snapshot

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.qztc.timetable.core.data.db.SyncRunEntity
import cn.qztc.timetable.core.data.repository.SyncRunStatus
import cn.qztc.timetable.core.ui.glass.GlassRole
import cn.qztc.timetable.core.ui.glass.GlassSurface
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcCorners
import cn.qztc.timetable.core.ui.theme.QztcPageTopBar
import cn.qztc.timetable.core.ui.theme.QztcSpacing
import cn.qztc.timetable.core.ui.theme.QztcTypography
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SnapshotGalleryScreen(
    records: List<SnapshotRecord>,
    syncRuns: List<SyncRunEntity>,
    onBack: () -> Unit,
    onShare: (SnapshotRecord) -> Unit,
    onSave: (SnapshotRecord) -> Unit,
    onDelete: (SnapshotRecord) -> Unit,
    hasSchedule: Boolean,
    onExportJson: () -> Unit,
    onExportIcs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalQztcColors.current
    Column(modifier.fillMaxSize().background(colors.solid2)) {
        QztcPageTopBar(title = "数据与快照", onBack = onBack)
        if (hasSchedule) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = QztcSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(QztcSpacing.sm),
            ) {
                Text("导出本学期", style = QztcTypography.meta, color = colors.mut, modifier = Modifier.weight(1f).padding(top = 12.dp))
                TextButton(onClick = onExportJson) { Text("JSON") }
                TextButton(onClick = onExportIcs) { Text("ICS") }
            }
        }
        if (records.isEmpty() && syncRuns.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("还没有导入或保存记录", style = QztcTypography.body, color = colors.mut)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(QztcSpacing.md),
            ) {
                item {
                    Text(
                        "导入记录与快照仅保存在本机。截图默认收起，需要时再放大查看。",
                        style = QztcTypography.meta,
                        color = colors.mut,
                        modifier = Modifier.padding(horizontal = QztcSpacing.lg, vertical = QztcSpacing.sm),
                    )
                }
                if (syncRuns.isNotEmpty()) {
                    item {
                        Text(
                            "导入与同步记录",
                            style = QztcTypography.section,
                            color = colors.mut,
                            modifier = Modifier.padding(horizontal = QztcSpacing.lg),
                        )
                    }
                    items(syncRuns.take(20), key = { "sync-${it.id}" }) { run ->
                        SyncRunRow(run)
                    }
                }
                if (records.isNotEmpty()) {
                    item {
                        Text(
                            "截图记录",
                            style = QztcTypography.section,
                            color = colors.mut,
                            modifier = Modifier.padding(horizontal = QztcSpacing.lg),
                        )
                    }
                    items(records, key = { it.file.absolutePath }) { record ->
                        SnapshotRow(record, onShare, onSave, onDelete)
                    }
                }
                item { Spacer(Modifier.height(QztcSpacing.xxl)) }
            }
        }
    }
}

@Composable
internal fun SnapshotRow(
    record: SnapshotRecord,
    onShare: (SnapshotRecord) -> Unit,
    onSave: (SnapshotRecord) -> Unit,
    onDelete: (SnapshotRecord) -> Unit,
) {
    val colors = LocalQztcColors.current
    var viewerOpen by remember(record.file.absolutePath) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = QztcSpacing.lg)
            .background(colors.solid, RoundedCornerShape(QztcCorners.content))
            .padding(QztcSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(record.label, style = QztcTypography.courseTitle, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")
                        .format(Instant.ofEpochMilli(record.createdAt).atZone(ZoneId.systemDefault())),
                    style = QztcTypography.meta,
                    color = if (record.pending) colors.accentDeep else colors.mut,
                )
            }
            IconButton(onClick = { viewerOpen = true }) {
                Icon(Icons.Outlined.Fullscreen, contentDescription = "放大查看", tint = colors.mut)
            }
            IconButton(onClick = { onShare(record) }) {
                Icon(Icons.Outlined.Share, contentDescription = "分享快照", tint = colors.mut)
            }
            IconButton(onClick = { onSave(record) }) {
                Icon(Icons.Outlined.Download, contentDescription = "保存到相册", tint = colors.mut)
            }
            IconButton(onClick = { onDelete(record) }) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除快照", tint = colors.mut)
            }
        }
    }
    if (viewerOpen) {
        val bitmap = remember(record.file.absolutePath, record.file.lastModified()) {
            BitmapFactory.decodeFile(record.file.absolutePath)
        }
        var viewerScale by remember(record.file.absolutePath) { mutableStateOf(1f) }
        var viewerOffset by remember(record.file.absolutePath) { mutableStateOf(Offset.Zero) }
        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            viewerScale = (viewerScale * zoomChange).coerceIn(1f, 6f)
            viewerOffset = if (viewerScale <= 1f) Offset.Zero else viewerOffset + panChange
        }
        Dialog(
            onDismissRequest = { viewerOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = .92f)),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = record.label,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .transformable(transformState)
                            .graphicsLayer {
                                scaleX = viewerScale
                                scaleY = viewerScale
                                translationX = viewerOffset.x
                                translationY = viewerOffset.y
                                transformOrigin = TransformOrigin.Center
                            },
                    )
                } else {
                    Text("截图文件不可用", color = Color.White)
                }
                IconButton(
                    onClick = { viewerOpen = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                ) {
                    Text("关闭", color = Color.White, style = QztcTypography.meta)
                }
            }
        }
    }
}

@Composable
private fun SyncRunRow(run: SyncRunEntity) {
    val colors = LocalQztcColors.current
    GlassSurface(GlassRole.CONTENT, Modifier.fillMaxWidth().padding(horizontal = QztcSpacing.lg)) {
        Column(Modifier.padding(QztcSpacing.md), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(run.syncStatusLabel(), style = QztcTypography.body, color = colors.ink)
            Text(
                "${run.source} · ${formatSnapshotTime(run.startedAt)}",
                style = QztcTypography.meta,
                color = colors.mut,
            )
            run.errorMessage?.takeIf { it.isNotBlank() }?.let {
                Text("原因：$it", style = QztcTypography.meta, color = colors.warn)
            }
        }
    }
}

private fun SyncRunEntity.syncStatusLabel(): String = when (runCatching { SyncRunStatus.valueOf(status) }.getOrNull()) {
    SyncRunStatus.SUCCESS_NO_CHANGE -> "导入完成 · 课表没有变化"
    SyncRunStatus.SUCCESS_PENDING -> "同步完成 · 有待确认变化"
    SyncRunStatus.SUCCESS_APPLIED -> "导入完成 · 已写入本机"
    SyncRunStatus.FAILED -> "导入失败 · 原课表已保留"
    null -> "导入记录 · $status"
}

private fun formatSnapshotTime(epochMillis: Long): String = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")
    .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
