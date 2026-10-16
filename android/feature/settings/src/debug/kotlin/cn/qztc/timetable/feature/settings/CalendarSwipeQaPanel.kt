package cn.qztc.timetable.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Exercises the production gesture handler without changing saved calendar data. */
@Composable
fun CalendarSwipeQaPanel() {
    var included by remember { mutableStateOf(false) }
    var actions by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(top = 64.dp, start = 20.dp, end = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("校历右滑验收")
        Text("操作次数：$actions")
        CalendarSwipeRow(key = "isolated-qa-event", included = included, enabled = true,
            onToggle = { included = !included; actions++ }, onEdit = {}) {
            Column(Modifier.height(90.dp), verticalArrangement = Arrangement.Center) {
                Text("合成验收活动")
                Text("仅内存数据")
            }
        }
    }
}
