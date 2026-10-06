package cn.qztc.timetable.announcement

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.qztc.timetable.core.ui.theme.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AnnouncementScreen(store: AnnouncementStore, onBack: () -> Unit, onUpdate: () -> Unit) {
    val revision by store.revision.collectAsStateWithLifecycle()
    val items = remember(revision) { store.items() }
    val colors = LocalQztcColors.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<Announcement?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val date = remember { DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(ZoneId.systemDefault()) }
    fun refresh() { scope.launch {
        refreshing = true
        message = store.refresh(true).exceptionOrNull()?.message
        refreshing = false
    } }
    LaunchedEffect(Unit) { refreshing = true; message = store.refresh().exceptionOrNull()?.message; refreshing = false }
    BackHandler(selected != null) { selected = null }
    Column(Modifier.fillMaxSize().background(colors.solid2)) {
        QztcPageTopBar("公告", onBack = { if (selected != null) selected = null else onBack() }, actions = {
            if (selected == null) IconButton(onClick = ::refresh, enabled = !refreshing) {
                if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), color = colors.accentDeep, strokeWidth = 2.dp)
                else Icon(Icons.Outlined.Refresh, "刷新公告", tint = colors.mut)
            }
        })
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(18.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val item = selected
            if (item != null) {
                Surface(color = colors.solid, shape = RoundedCornerShape(QztcCorners.content)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(item.title, style = QztcTypography.courseTitle, color = colors.ink)
                        Text(date.format(Instant.parse(item.publishedAt)), style = QztcTypography.meta, color = colors.mut)
                        Text(item.body, style = QztcTypography.body, color = colors.ink)
                        if (item.action == "check_update") Button(onClick = onUpdate) { Text("检查更新") }
                    }
                }
            } else {
                message?.let { Text(it, color = colors.mut, style = QztcTypography.meta) }
                if (items.isEmpty() && !refreshing) Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) { Text("暂无公告", color = colors.mut) }
                items.forEach { announcement ->
                    Surface(color = colors.solid, shape = RoundedCornerShape(QztcCorners.content)) {
                        Column(Modifier.fillMaxWidth().clickable { store.markRead(announcement); selected = announcement }.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(announcement.title, Modifier.weight(1f), style = QztcTypography.courseTitle, color = colors.ink)
                                if (!store.read(announcement)) Text("未读", color = colors.accentDeep, style = QztcTypography.meta)
                            }
                            Text(date.format(Instant.parse(announcement.publishedAt)), style = QztcTypography.meta, color = colors.mut)
                            Text(announcement.body, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = QztcTypography.body, color = colors.mut)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImportantAnnouncementDialog(item: Announcement, onDismiss: () -> Unit, onOpen: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(item.title) },
        text = { Text(item.body, maxLines = 6, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
        confirmButton = { TextButton(onClick = onOpen) { Text("查看公告") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("稍后") } })
}
