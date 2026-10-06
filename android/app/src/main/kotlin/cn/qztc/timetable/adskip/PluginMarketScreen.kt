package cn.qztc.timetable.adskip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcPageTopBar
import cn.qztc.timetable.core.ui.theme.QztcSpacing
import cn.qztc.timetable.core.ui.theme.QztcTypography

@Composable
fun PluginMarketScreen(
    onBack: () -> Unit,
    onManagePlugin: () -> Unit,
) {
    @Suppress("UNUSED_VARIABLE")
    val unusedManageAction = onManagePlugin
    val colors = LocalQztcColors.current
    Column(Modifier.fillMaxSize().background(colors.solid2)) {
        QztcPageTopBar(title = "插件市场", onBack = onBack)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(QztcSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(QztcSpacing.md),
        ) {
            Text(
                "这里以后会提供可选校园工具。当前市场尚未开放。",
                style = QztcTypography.body,
                color = colors.mut,
            )
            Surface(color = colors.solid, shape = MaterialTheme.shapes.medium) {
                Column(
                    Modifier.fillMaxWidth().padding(QztcSpacing.xl),
                    verticalArrangement = Arrangement.spacedBy(QztcSpacing.sm),
                ) {
                    Text("插件市场尚未开放", style = QztcTypography.courseTitle, color = colors.ink)
                    Text("待审核的校园工具目录准备完成后，会在这里统一提供。", style = QztcTypography.body, color = colors.mut)
                }
            }
        }
    }
}
