package cn.qztc.timetable.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cn.qztc.timetable.core.ui.glass.GlassRole
import cn.qztc.timetable.core.ui.glass.GlassSurface
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcPageTopBar
import cn.qztc.timetable.core.ui.theme.QztcSpacing
import cn.qztc.timetable.core.ui.theme.QztcTypography

private const val REPOSITORY_URL = "https://github.com/huanmen/qsschedule-public"

@Composable
fun PrivacyAboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalQztcColors.current
    val context = LocalContext.current
    fun open(path: String = "") {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPOSITORY_URL + path))) }
    }
    Column(modifier.fillMaxSize().background(colors.solid2)) {
        QztcPageTopBar(title = "隐私与关于", onBack = onBack)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(QztcSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(QztcSpacing.md),
        ) {
            AboutSection("关于应用", "泉师课程表帮助你查看课程、安排活动和管理学习提醒。本项目由个人开发，与学校官方无隶属关系。")
            AboutSection("数据与存储", "课表、活动和设置保存在本机。选择保存的教务账号及成绩缓存会加密存储，教务认证信息仅用于访问学校服务，不上传到项目服务器。")
            AboutSection("权限与服务", "通知用于课程和活动提醒；定位用于校园地图；无障碍权限仅供主动启用的广告辅助使用。相关权限按需申请，可在系统设置中关闭。第三方服务的说明会在对应功能中提供。")
            AboutSection("联网与插件", "公告、校历、更新及插件资料通过网络获取。下载完成后，会向项目服务器发送随机安装标识、包类型和版本号，用于去重统计。使用 AI 功能时，你填写的素材会发送至自行配置的模型服务。")
            AboutSection("数据管理", "你可以在应用中修改或删除已保存的资料。导出和分享的文件由你自行管理，分享前请检查其中的个人信息；清除应用数据会移除本机保存的资料。")
            GlassSurface(role = GlassRole.CONTENT, modifier = Modifier.fillMaxWidth().clickable { open() }) {
                Row(Modifier.fillMaxWidth().padding(QztcSpacing.xl), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Code, contentDescription = null, tint = colors.accent)
                    Column(Modifier.padding(start = QztcSpacing.md).weight(1f)) {
                        Text("开源项目", style = QztcTypography.courseTitle, color = colors.ink)
                        Text("GitHub · huanmen/qsschedule-public", style = QztcTypography.meta, color = colors.mut)
                    }
                    Text("查看", style = QztcTypography.meta, color = colors.accentDeep)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("隐私政策", color = colors.accentDeep, style = QztcTypography.meta,
                    modifier = Modifier.clickable { open("/blob/main/PRIVACY.md") }.padding(QztcSpacing.sm))
                Text("问题反馈", color = colors.accentDeep, style = QztcTypography.meta,
                    modifier = Modifier.clickable { open("/issues") }.padding(QztcSpacing.sm))
            }
            Text("隐私说明更新于 2026 年 10 月 16 日", style = QztcTypography.meta, color = colors.mut,
                modifier = Modifier.padding(horizontal = QztcSpacing.sm))
        }
    }
}

@Composable
private fun AboutSection(title: String, body: String) {
    val colors = LocalQztcColors.current
    GlassSurface(role = GlassRole.CONTENT, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(QztcSpacing.xl), verticalArrangement = Arrangement.spacedBy(QztcSpacing.sm)) {
            Text(title, style = QztcTypography.courseTitle, color = colors.ink)
            Text(body, style = QztcTypography.body, color = colors.mut)
        }
    }
}
