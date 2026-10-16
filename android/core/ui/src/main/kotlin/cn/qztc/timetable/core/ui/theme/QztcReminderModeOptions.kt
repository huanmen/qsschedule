package cn.qztc.timetable.core.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

data class ReminderPreviewState(val running:Boolean=false,val message:String="",
    val needsSystemSettings:Boolean=false,val needsConfirmation:Boolean=false)

/** Course and activity reminders share selection, real preview and receipt feedback. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QztcReminderModeOptions(popup:Boolean,onChoose:(Boolean)->Unit,state:ReminderPreviewState,
    onPreview:()->Unit,onSystemSettings:()->Unit) {
    val colors=LocalQztcColors.current
    var missing by remember(state) {mutableStateOf(false)}
    var received by remember(state) {mutableStateOf(false)}
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(true,false).forEachIndexed {index,value ->
                SegmentedButton(selected=popup==value,onClick={onChoose(value)},enabled=!state.running,
                    shape=SegmentedButtonDefaults.itemShape(index,2),
                    modifier=Modifier.testTag(if(value)"reminder-mode-popup"else"reminder-mode-notification"),
                    label={Text(if(value)"弹窗提醒"else"仅通知栏")})
            }
        }
        TextButton(onClick=onPreview,enabled=!state.running,modifier=Modifier.testTag("reminder-mode-preview")) {Text("预览提醒")}
        if(state.running)LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(when {missing->"未收到测试提醒，请调整系统通知设置后重试。";received->"已确认收到提醒"
            state.message.isNotBlank()->state.message;else->"点击显示方式或预览提醒，会发送测试提醒。"},
            style=QztcTypography.meta,color=colors.mut)
        if(state.needsConfirmation&&!missing&&!received)Row {
            TextButton(onClick={received=true}) {Text("已收到")}
            TextButton(onClick={missing=true}) {Text("没有收到")}
        }
        if(state.needsSystemSettings||missing)Row {
            TextButton(onClick=onSystemSettings) {Text("系统通知设置")}
            TextButton(onClick=onPreview,enabled=!state.running) {Text("重新测试")}
        }
    }
}
