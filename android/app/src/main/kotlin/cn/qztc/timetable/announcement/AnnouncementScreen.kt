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
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.qztc.timetable.core.ui.theme.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AnnouncementScreen(store: AnnouncementStore, onBack: () -> Unit, onUpdate: (Announcement) -> Unit) {
    val revision by store.revision.collectAsStateWithLifecycle()
    val items = remember(revision) { store.items() }
    val colors = LocalQztcColors.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<Announcement?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(revision) {
        selected = selected?.let { chosen -> items.firstOrNull { it.id == chosen.id } }
    }
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
            // A refresh can withdraw or replace the currently open notice.
            val item = selected?.let { chosen -> items.firstOrNull { it.id == chosen.id } }
            if (item != null) {
                Surface(color = colors.solid, shape = RoundedCornerShape(QztcCorners.content)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(item.title, style = QztcTypography.courseTitle, color = colors.ink)
                        Text(date.format(Instant.parse(item.publishedAt)), style = QztcTypography.meta, color = colors.mut)
                        AnnouncementBody(item.body.removePrefix(item.title + "\n"))
                        if (item.isUpdateNotice) {
                            val available = item.hasUpdate(cn.qztc.timetable.BuildConfig.VERSION_CODE)
                            OutlinedButton(onClick = { onUpdate(item) }, enabled = available) {
                                Text(if (available) "更新下载" else "已更新")
                            }
                        }
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
private fun AnnouncementBody(body: String) {
    val colors = LocalQztcColors.current
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        body.lines().forEach { line ->
            val paragraph = announcementParagraph(line)
            val url = Regex("https://[^\\s]+").find(line)?.value
            Text(paragraph.text, modifier = Modifier.fillMaxWidth().then(if (url != null) Modifier.clickable { uri.openUri(url) } else Modifier),
                style = QztcTypography.body.copy(textIndent = TextIndent(firstLine = if (paragraph.indent) 2.em else 0.em)),
                color = if (url == null) colors.ink else colors.accentDeep,
                textAlign = if (line.trimStart().startsWith("——")) TextAlign.End else TextAlign.Start)
        }
    }
}

@Composable
fun ImportantAnnouncementDialog(item: Announcement, onDismiss: () -> Unit, onOpen: () -> Unit, onUpdate: (() -> Unit)? = null) {
    val colors = LocalQztcColors.current
    val date = remember(item.publishedAt) {
        DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneId.systemDefault()).format(Instant.parse(item.publishedAt))
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth(.92f).widthIn(max = 480.dp)) {
            Surface(Modifier.matchParentSize().padding(top = 9.dp, start = 7.dp, end = 7.dp),
                shape = RoundedCornerShape(24.dp), color = colors.accentSoftBorder) {}
            Surface(Modifier.fillMaxWidth().padding(bottom = 9.dp), shape = RoundedCornerShape(24.dp),
                color = colors.solid2, border = BorderStroke(1.dp, colors.line2)) {
                Column(Modifier.fillMaxWidth().heightIn(max = 660.dp)) {
                    Row(Modifier.fillMaxWidth().background(colors.accentSoft).padding(horizontal = 22.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.Campaign, null, tint = colors.accentDeep, modifier = Modifier.size(22.dp))
                        Text("公告栏", style = QztcTypography.section, color = colors.accentDeep, modifier = Modifier.weight(1f))
                        Text(date, style = QztcTypography.meta, color = colors.mut)
                    }
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(item.title, style = QztcTypography.courseTitle, color = colors.ink)
                        val body = item.body.removePrefix(item.title + "\n")
                        AnnouncementBody(body)
                    }
                    HorizontalDivider(color = colors.line, modifier = Modifier.padding(horizontal = 22.dp))
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("稍后") }
                        OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("查看公告") }
                        if (item.isUpdateNotice) {
                            val available = item.hasUpdate(cn.qztc.timetable.BuildConfig.VERSION_CODE) && onUpdate != null
                            OutlinedButton(onClick = { onUpdate?.invoke() }, enabled = available,
                                modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                                Text(if (available) "更新下载" else "已更新")
                            }
                        }
                    }
                }
            }
            listOf(Alignment.TopStart, Alignment.TopEnd).forEach { alignment ->
                Surface(Modifier.align(alignment).padding(horizontal = 27.dp).offset(y = (-4).dp).size(9.dp),
                    shape = CircleShape, color = colors.accentDeep, shadowElevation = 2.dp) {}
            }
        }
    }
}
