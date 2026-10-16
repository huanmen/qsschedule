package cn.qztc.timetable.education

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.ui.theme.*

@Composable
internal fun CourseImportToolsScreen(onBack: () -> Unit, onImport: () -> Unit, onCheckChanges: () -> Unit, canCheckChanges: Boolean) {
    val colors = LocalQztcColors.current
    Column(Modifier.fillMaxSize().background(colors.solid2)) {
        QztcPageTopBar("教务课表", onBack = onBack)
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("教务课表导入") }
        }
    }
}
