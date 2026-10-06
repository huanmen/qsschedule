package cn.qztc.timetable.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.ui.glass.GlassRole
import cn.qztc.timetable.core.ui.glass.GlassSurface
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcPageTopBar
import cn.qztc.timetable.core.ui.theme.QztcSpacing
import cn.qztc.timetable.core.ui.theme.QztcTypography

private const val REPOSITORY_URL = "https://github.com/huanmen/qsschedule"

@Composable
fun PrivacyAboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalQztcColors.current
    val context = LocalContext.current
    val locationPreferences = remember(context) { context.getSharedPreferences("campus_location_privacy", android.content.Context.MODE_PRIVATE) }
    var amapEnabled by remember { mutableStateOf(locationPreferences.getBoolean("amap_consent_v1", false)) }
    Column(modifier.fillMaxSize().background(colors.solid2)) {
        QztcPageTopBar(title = "隐私与关于", onBack = onBack)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(QztcSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(QztcSpacing.md),
        ) {
            GlassSurface(role = GlassRole.CONTENT, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(QztcSpacing.xl), verticalArrangement = Arrangement.spacedBy(QztcSpacing.sm)) {
                    Text("本地优先", style = QztcTypography.courseTitle, color = colors.ink)
                    Text("课表、导入记录和快照默认保存在本机。教务登录信息使用 Android Keystore 保护，不会写入日志或分享内容。", style = QztcTypography.body, color = colors.mut)
                    Text("后台同步只在用户开启后运行，学校页面和网络请求仍受系统权限与学校网络条件影响。", style = QztcTypography.body, color = colors.mut)
                    Text("定位仅用于校内路线起点和路线更新，不会写入课表、快照或日志。启用高德定位后，高德 SDK 会处理位置、设备及网络信息并连接高德服务；仅在地图前台运行，退出或锁屏后停止。关闭后使用系统定位。", style = QztcTypography.body, color = colors.mut)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("高德定位", modifier = Modifier.weight(1f), color = colors.ink)
                        Switch(checked = amapEnabled, onCheckedChange = { enabled ->
                            // Enabling is confirmed by the disclosure in the map, before SDK initialization.
                            if (!enabled) {
                                locationPreferences.edit().putBoolean("amap_consent_v1", false).apply()
                                amapEnabled = false
                            }
                        }, enabled = amapEnabled)
                    }
                    if (!amapEnabled) Text("进入校园地图，点击定位可选择启用。", style = QztcTypography.meta, color = colors.mut)
                    TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://lbs.amap.com/pages/privacy/"))) } }) { Text("高德隐私政策") }
                    Text("校历日期来自年度校历服务或用户手动设置。应用不会读取或写入系统日历。", style = QztcTypography.body, color = colors.mut)
                    Text("AI 好评助手仅在点击生成时，将所填素材发送至你配置的模型服务。API 密钥使用 Android Keystore 加密保存在本机，不经开发者服务器；不会读取课表、其他应用或自动发布评价。删除插件会清除草稿和模型配置。", style = QztcTypography.body, color = colors.mut)
                    Text("公告和更新优先从开发者服务器获取，更新失败时使用 Gitee 备用源。下载完成后仅上报随机安装标识、包类型与版本用于去重统计；不含账号、课表、屏幕内容或硬件编号，服务器不保存原标识与访问 IP。", style = QztcTypography.body, color = colors.mut)
                }
            }
            GlassSurface(
                role = GlassRole.CONTENT,
                modifier = Modifier.fillMaxWidth().clickable {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPOSITORY_URL))) }
                },
            ) {
                Row(Modifier.fillMaxWidth().padding(QztcSpacing.xl), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Code, contentDescription = null, tint = colors.accent)
                    Column(Modifier.padding(start = QztcSpacing.md).weight(1f)) {
                        Text("项目代码仓库", style = QztcTypography.courseTitle, color = colors.ink)
                        Text("GitHub · huanmen/qsschedule", style = QztcTypography.meta, color = colors.mut)
                    }
                    Spacer(Modifier.width(QztcSpacing.lg))
                    Text("打开", style = QztcTypography.meta, color = colors.accentDeep)
                }
            }
            Spacer(Modifier.height(QztcSpacing.sm))
            Text("泉师课程表 · Android", style = QztcTypography.meta, color = colors.mut, modifier = Modifier.padding(horizontal = QztcSpacing.sm))
        }
    }
}
