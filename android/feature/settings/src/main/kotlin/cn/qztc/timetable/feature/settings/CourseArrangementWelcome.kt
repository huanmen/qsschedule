package cn.qztc.timetable.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.ui.theme.*

@Composable
fun CourseArrangementWelcome(onBack: () -> Unit, onImport: () -> Unit) {
    val colors = LocalQztcColors.current
    Column(Modifier.fillMaxSize().background(colors.solid2)) {
        QztcPageTopBar(title = "课程安排", onBack = onBack)
        Button(onClick = onImport, modifier = Modifier.padding(20.dp).fillMaxWidth()) { Text("登录教务 / 导入课程") }
    }
}
