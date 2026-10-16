package cn.qztc.timetable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import cn.qztc.timetable.adskip.AdSkipScreen
import cn.qztc.timetable.adskip.PluginMarketScreen
import cn.qztc.timetable.announcement.AnnouncementScreen
import cn.qztc.timetable.announcement.AnnouncementStore
import cn.qztc.timetable.core.ui.theme.QztcTheme
import cn.qztc.timetable.update.AppUpdateClient
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Debug-only entry avoids opening personal timetable data during ad-skip QA. */
class AdSkipQaActivity : ComponentActivity() {
    private var page by mutableStateOf("detail")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = intent.getStringExtra("page") ?: "detail"
        setContent { QztcTheme {
            when (page) {
                "market" -> PluginMarketScreen({ finish() }, { page = "detail" }, { page = "review" })
                "review" -> cn.qztc.timetable.review.ReviewScreen { page = "market" }
                "announcements" -> AnnouncementScreen(remember { AnnouncementStore(this) }, { finish() }, {})
                "download-check" -> {
                    var result by remember { mutableStateOf("验证下载中") }
                    LaunchedEffect(Unit) {
                        val manifest = AppUpdateClient.check(0).getOrNull()
                        result = if (manifest == null) "清单获取失败" else {
                            val downloaded = AppUpdateClient.download(this@AdSkipQaActivity, manifest)
                            if (downloaded.isSuccess) "服务器下载及安装包校验成功" else "下载失败"
                        }
                    }
                    Column(Modifier.fillMaxSize().padding(32.dp)) { Text(result) }
                }
                else -> AdSkipScreen { page = "market" }
            }
        } }
    }
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        page = intent.getStringExtra("page") ?: "detail"
    }
}
