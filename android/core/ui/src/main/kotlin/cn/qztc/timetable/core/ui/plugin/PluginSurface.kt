package cn.qztc.timetable.core.ui.plugin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcCorners
import cn.qztc.timetable.core.ui.theme.QztcPageTopBar
import cn.qztc.timetable.core.ui.theme.QztcSpacing

@Composable
fun PluginPage(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalQztcColors.current
    Column(Modifier.fillMaxSize().background(colors.solid2)) {
        QztcPageTopBar(title = title, onBack = onBack, actions = actions)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(QztcSpacing.lg).navigationBarsPadding(),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(18.dp), content = content)
    }
}

@Composable
fun PluginCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = LocalQztcColors.current.solid, shape = RoundedCornerShape(QztcCorners.content)) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}
