package cn.qztc.timetable.feature.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.data.db.SyncRunEntity
import cn.qztc.timetable.core.data.repository.SyncRunStatus
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcCorners
import cn.qztc.timetable.core.ui.theme.QztcPageTopBar
import cn.qztc.timetable.core.ui.theme.QztcSpacing
import cn.qztc.timetable.core.ui.theme.QztcTypography
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 课表同步：只保留检查结果和最近一个可恢复版本。 */
@Composable
fun SyncCenterScreen(
    syncEnabled: Boolean,
    syncIntervalHours: Int,
    pendingCount: Int,
    syncRuns: List<SyncRunEntity>,
    onBack: () -> Unit,
    onSyncEnabledChange: (Boolean) -> Unit,
    onSyncIntervalChange: (Int) -> Unit,
    onOpenChangeCenter: () -> Unit,
    onOpenForegroundCheck: () -> Unit,
    onRunBackgroundCheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalQztcColors.current
    val latest = syncRuns.firstOrNull()
    var intervalDialogOpen by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().background(colors.appBg)) {
        QztcPageTopBar(
            title = "课表同步",
            subtitle = "仅检查从现在起的停课、调课",
            onBack = onBack,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(QztcSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(QztcSpacing.md),
        ) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(QztcCorners.content))
                        .background(colors.solid),
                ) {
                    SyncSettingRow(
                        title = "自动同步",
                        subtitle = "检查尚未结束的课次，包括下一节课",
                        onClick = { onSyncEnabledChange(!syncEnabled) },
                    ) {
                        Switch(checked = syncEnabled, onCheckedChange = onSyncEnabledChange)
                    }
                    SyncSettingRow(
                        title = "同步频率",
                        subtitle = if (syncEnabled) "连接网络后检查教务变化" else "开启自动同步后可调整",
                        enabled = syncEnabled,
                        onClick = { if (syncEnabled) intervalDialogOpen = true },
                    ) {
                        Text("每 $syncIntervalHours 小时", style = QztcTypography.meta, color = colors.mut)
                    }
                    SyncSettingRow(
                        title = "变化预览",
                        subtitle = "有变化时到周课表逐周对比",
                        onClick = onOpenChangeCenter,
                    ) {
                        Text(
                            if (pendingCount > 0) "$pendingCount 项变化" else "无变化",
                            style = QztcTypography.meta,
                            color = if (pendingCount > 0) colors.warn else colors.mut,
                        )
                    }
                }
            }
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(QztcCorners.content))
                        .background(colors.solid)
                        .padding(QztcSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(QztcSpacing.sm),
                ) {
                    Text("立即检查", style = QztcTypography.courseTitle, color = colors.ink)
                    Text(
                        "最近记录：${latest?.finishedAt?.let(::formatSyncTime) ?: "暂无"} · ${latest?.statusLabel() ?: "尚未检查"}",
                        style = QztcTypography.body,
                        color = colors.mut,
                    )
                    if (pendingCount > 0) {
                        Text("发现 $pendingCount 项变化，请到周课表预览后保存。", style = QztcTypography.body, color = colors.warn)
                    }
                    Button(onClick = onOpenForegroundCheck, modifier = Modifier.fillMaxWidth()) {
                        Text("登录教务 / 更新会话")
                    }
                    run {
                        Button(onClick = onRunBackgroundCheck, modifier = Modifier.fillMaxWidth()) {
                            Text("立即检查停课与调课")
                        }
                    }
                }
            }
            item { Text("同步记录", style = QztcTypography.section, color = colors.mut) }
            if (syncRuns.isEmpty()) {
                item { Text("还没有同步记录", style = QztcTypography.body, color = colors.mut) }
            } else {
                items(syncRuns.take(1), key = { it.id }) { run ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(QztcCorners.content))
                            .background(colors.solid)
                            .padding(QztcSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(run.statusLabel(), style = QztcTypography.courseTitle, color = run.statusColor(colors))
                        Text(formatSyncTime(run.startedAt), style = QztcTypography.meta, color = colors.mut)
                        run.errorMessage?.takeIf { it.isNotBlank() }?.let {
                            Text("原因：$it", style = QztcTypography.meta, color = colors.warn)
                        }
                    }
                }
            }
        }
    }

    if (intervalDialogOpen) {
        val options = listOf(1, 2, 4, 8, 12, 24)
        AlertDialog(
            onDismissRequest = { intervalDialogOpen = false },
            title = { Text("自动同步频率") },
            text = {
                Column {
                    options.forEach { hours ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .clickable {
                                    onSyncIntervalChange(hours)
                                    intervalDialogOpen = false
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = hours == syncIntervalHours,
                                onClick = {
                                    onSyncIntervalChange(hours)
                                    intervalDialogOpen = false
                                },
                            )
                            Text("每 $hours 小时", style = QztcTypography.body)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { intervalDialogOpen = false }) { Text("关闭") }
            },
        )
    }
}

@Composable
private fun SyncSettingRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit,
) {
    val colors = LocalQztcColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = QztcSpacing.lg, vertical = QztcSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = QztcTypography.courseTitle, color = if (enabled) colors.ink else colors.faint)
            Text(subtitle, style = QztcTypography.meta, color = colors.mut)
        }
        Spacer(Modifier.width(QztcSpacing.lg))
        trailing()
    }
}

private fun SyncRunEntity.statusLabel(): String = when (runCatching { SyncRunStatus.valueOf(status) }.getOrNull()) {
    SyncRunStatus.SUCCESS_NO_CHANGE -> "课表没有变化"
    SyncRunStatus.SUCCESS_PENDING -> "发现待确认变化"
    SyncRunStatus.SUCCESS_APPLIED -> "已写入本机课表"
    SyncRunStatus.FAILED -> "同步或解析失败"
    null -> status
}

private fun SyncRunEntity.statusColor(colors: cn.qztc.timetable.core.ui.theme.QztcColors): Color = when (
    runCatching { SyncRunStatus.valueOf(status) }.getOrNull()
) {
    SyncRunStatus.FAILED -> colors.warn
    else -> colors.ink
}

private fun formatSyncTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
