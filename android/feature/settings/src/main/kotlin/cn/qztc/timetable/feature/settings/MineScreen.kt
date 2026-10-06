package cn.qztc.timetable.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.ImportExport
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import kotlin.math.atan2
import kotlin.math.hypot
import cn.qztc.timetable.core.domain.model.HomePreference
import cn.qztc.timetable.core.domain.model.PeriodScheme
import cn.qztc.timetable.core.domain.model.Semester
import cn.qztc.timetable.core.domain.model.WeekendDisplayMode
import cn.qztc.timetable.core.ui.background.BackgroundMotion
import cn.qztc.timetable.core.ui.background.BackgroundSource
import cn.qztc.timetable.core.ui.glass.GlassQuality
import cn.qztc.timetable.core.ui.glass.GlassRole
import cn.qztc.timetable.core.ui.glass.GlassSurface
import cn.qztc.timetable.core.ui.glass.LocalGlassSettings
import cn.qztc.timetable.core.ui.motion.pressable
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcCorners
import cn.qztc.timetable.core.ui.theme.QztcPageTopBar
import cn.qztc.timetable.core.ui.theme.QztcSpacing
import cn.qztc.timetable.core.ui.theme.QztcThemeMode
import cn.qztc.timetable.core.ui.theme.QztcPalette
import cn.qztc.timetable.core.ui.theme.FontSizeMode
import cn.qztc.timetable.core.ui.theme.QztcTypography
import cn.qztc.timetable.core.domain.model.ReminderDisplayMode

data class ImportedBackground(
    val path: String,
    val label: String,
)

data class MineUiState(
    val homePreference: HomePreference = HomePreference.TODAY,
    val themeMode: QztcThemeMode = QztcThemeMode.SYSTEM,
    val syncEnabled: Boolean = false,
    val syncIntervalHours: Int = 2,
    val remindersEnabled: Boolean = true,
    val reminderMinutes: Int = 15,
    val reminderDisplayMode: ReminderDisplayMode = ReminderDisplayMode.SCREEN_POPUP,
    val fontSizeMode: FontSizeMode = FontSizeMode.DEFAULT,
    val fontScale: Float = 1f,
    val pendingChanges: Int = 0,
    val backgroundLabel: String = "内置场景",
    val weekendDisplayMode: WeekendDisplayMode = WeekendDisplayMode.HIDE_EMPTY,
    val eduAccountConfigured: Boolean = false,
    val activeSemesterId: String? = null,
    val activeSemesterLabel: String = "尚未导入",
    val activeSemesterStartDateLabel: String = "未设置",
    val activeSemester: Semester? = null,
    val periodScheme: PeriodScheme? = null,
    val semesters: List<MineSemesterOption> = emptyList(),
    val updateLabel: String = "检查最新版本",
)

data class MineSemesterOption(val id: String, val label: String)

private data class SettingItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val value: String? = null,
    val switchValue: Boolean? = null,
    val onClick: () -> Unit,
    val onSwitch: ((Boolean) -> Unit)? = null,
    val enabled: Boolean = true,
)

@Composable
fun MineScreen(
    state: MineUiState,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onHomePreferenceChange: (HomePreference) -> Unit,
    onWeekendDisplayModeChange: (WeekendDisplayMode) -> Unit,
    onPersonalizationClick: () -> Unit,
    onSyncCenterClick: () -> Unit,
    onReminderSettingsClick: () -> Unit,
    onFontSizeModeChange: (FontSizeMode) -> Unit = {},
    onFontScaleChange: (Float) -> Unit = {},
    onEduImportClick: () -> Unit,
    onStudyClick: () -> Unit = {},
    onEvaluationClick: () -> Unit = {},
    onCampusAliasClick: () -> Unit,
    onCampusMapClick: () -> Unit = onCampusAliasClick,
    onStartDateClick: () -> Unit,
    onSemesterSelect: (String) -> Unit,
    onDataClick: () -> Unit,
    onStorageClick: () -> Unit = {},
    onAdSkipClick: () -> Unit = {},
    onPluginMarketClick: () -> Unit = onAdSkipClick,
    onAnnouncementsClick: () -> Unit = {},
    unreadAnnouncements: Int = 0,
    onPrivacyAboutClick: () -> Unit,
    onCheckUpdateClick: () -> Unit = {},
) {
    val colors = LocalQztcColors.current
    var homeSheetOpen by remember { mutableStateOf(false) }
    var weekendSheetOpen by remember { mutableStateOf(false) }
    var semesterSheetOpen by remember { mutableStateOf(false) }

    val homeLabel = when (state.homePreference) {
        HomePreference.TODAY -> "今日"
        HomePreference.WEEK -> "周课表"
        HomePreference.LAST -> "记住上次"
    }
    val weekendLabel = when (state.weekendDisplayMode) {
        WeekendDisplayMode.SHOW_ALL -> "始终显示"
        WeekendDisplayMode.FOLD_EMPTY -> "无课时收窄"
        WeekendDisplayMode.HIDE_EMPTY -> "无课时隐藏"
    }
    val reminderLabel = when {
        !state.remindersEnabled || state.reminderMinutes == 0 -> "已关闭"
        else -> "提前 ${state.reminderMinutes} 分钟"
    }

    val groups = listOf(
        "学期" to listOf(
            SettingItem(
                "课程安排",
                "教务账号、学期、开学日期与校历",
                Icons.Outlined.CalendarMonth,
                value = state.activeSemesterLabel,
                onClick = onStartDateClick,
            ),
            SettingItem(
                "校园地图",
                "本地地图、校内路线和地点编辑",
                Icons.Outlined.Place,
                // Opening the map is a view action. Editing is an explicit
                // mode behind the pencil in the map header.
                onClick = onCampusMapClick,
            ),
        ),
        "提醒" to listOf(
            SettingItem(
                "提醒设置",
                "开关、时间、显示方式与单课提醒",
                Icons.Outlined.Notifications,
                value = reminderLabel,
                onClick = onReminderSettingsClick,
            ),
        ),
        "课表显示" to listOf(
            SettingItem("周末列显示", "周六、周日无课时的显示方式", Icons.Outlined.ViewWeek, value = weekendLabel, onClick = { weekendSheetOpen = true }),
        ),
        "学习" to listOf(
            SettingItem("学习情况", "查看成绩与当前学分", Icons.Outlined.Assessment, onClick = onStudyClick),
        ),
        "个性化" to listOf(
            SettingItem("首页视图", "启动时先打开的页面", Icons.Outlined.Home, value = homeLabel, onClick = { homeSheetOpen = true }),
            SettingItem("显示与外观", "深色模式、课程颜色、背景与玻璃", Icons.Outlined.Palette, value = state.backgroundLabel, onClick = onPersonalizationClick),
        ),
        "扩展工具" to listOf(
            SettingItem("插件市场", "安装与管理扩展工具", Icons.Outlined.AutoAwesome, onClick = onPluginMarketClick),
            SettingItem("公告", "更新与重要通知", Icons.Outlined.Notifications, value = if (unreadAnnouncements > 0) unreadAnnouncements.toString() + " 条未读" else null, onClick = onAnnouncementsClick),
        ),
        "数据" to listOf(
            SettingItem("数据与存储", "快照、导出文件与可清理缓存", Icons.Outlined.Storage, onClick = onStorageClick),
            SettingItem("检查更新", "检查并安装新版本", Icons.Outlined.SystemUpdate, value = state.updateLabel, onClick = onCheckUpdateClick),
            SettingItem("隐私与关于", "本地优先与版本信息", Icons.Outlined.Info, onClick = onPrivacyAboutClick),
        ),
    )

    Column(modifier.fillMaxSize().background(colors.solid2).statusBarsPadding()) {
        LazyColumn(
        modifier = Modifier.weight(1f),
        state = listState,
        contentPadding = PaddingValues(
            start = QztcSpacing.lg,
            top = QztcSpacing.sm,
            end = QztcSpacing.lg,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(QztcSpacing.lg),
    ) {
        groups.forEach { (group, items) ->
            item(key = group) {
                SettingGroup(title = group, items = items)
            }
            if (group == "个性化") {
                item(key = "font-size-setting") {
                    FontSizeSetting(
                        mode = state.fontSizeMode,
                        scale = state.fontScale,
                        onModeChange = onFontSizeModeChange,
                        onScaleChange = onFontScaleChange,
                    )
                }
            }
        }
    }
    }

    if (homeSheetOpen) {
        ChoiceSheet(
            title = "首页视图",
            options = listOf(
                HomePreference.TODAY to "今日",
                HomePreference.WEEK to "周课表",
                HomePreference.LAST to "记住上次",
            ),
            selected = state.homePreference,
            onSelect = { value ->
                homeSheetOpen = false
                onHomePreferenceChange(value)
            },
            onDismiss = { homeSheetOpen = false },
        )
    }

    if (weekendSheetOpen) {
        ChoiceSheet(
            title = "周末列显示",
            options = listOf(
                WeekendDisplayMode.SHOW_ALL to "始终显示",
                WeekendDisplayMode.FOLD_EMPTY to "无课时收窄",
                WeekendDisplayMode.HIDE_EMPTY to "无课时隐藏",
            ),
            selected = state.weekendDisplayMode,
            onSelect = { value ->
                weekendSheetOpen = false
                onWeekendDisplayModeChange(value)
            },
            onDismiss = { weekendSheetOpen = false },
        )
    }

    if (semesterSheetOpen && state.semesters.isNotEmpty()) {
        ChoiceSheet(
            title = "切换查看学期",
            options = state.semesters.map { it.id to it.label },
            selected = state.activeSemesterId.orEmpty(),
            onSelect = { semesterId ->
                semesterSheetOpen = false
                onSemesterSelect(semesterId)
            },
            onDismiss = { semesterSheetOpen = false },
        )
    }


}

@Composable
private fun FontSizeSetting(
    mode: FontSizeMode,
    scale: Float,
    onModeChange: (FontSizeMode) -> Unit,
    onScaleChange: (Float) -> Unit,
) {
    val colors = LocalQztcColors.current
    var draftScale by remember(scale) { androidx.compose.runtime.mutableFloatStateOf(scale) }
    Column(verticalArrangement = Arrangement.spacedBy(QztcSpacing.sm)) {
        Text("字体大小", style = QztcTypography.section, color = colors.mut, modifier = Modifier.padding(start = QztcSpacing.sm))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(QztcCorners.content)).background(colors.solid)
                .padding(QztcSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(QztcSpacing.sm),
        ) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(QztcSpacing.md)).background(colors.solid2).padding(3.dp)) {
                listOf(
                    FontSizeMode.AUTO_FILL to "自动铺满",
                    FontSizeMode.DEFAULT to "默认",
                    FontSizeMode.CUSTOM to "自定义",
                ).forEach { (option, label) ->
                    Text(
                        label,
                        style = QztcTypography.meta,
                        color = if (mode == option) colors.onAccent else colors.mut,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(QztcSpacing.sm))
                            .background(if (mode == option) colors.accent else colors.solid2)
                            .clickable { onModeChange(option) }
                            .padding(vertical = QztcSpacing.sm),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QztcSpacing.md)) {
                Text(
                    "课程示例\n泉师",
                    style = QztcTypography.body,
                    fontSize = (14f * scale.coerceIn(.75f, 1.6f)).sp,
                    lineHeight = (19f * scale.coerceIn(.75f, 1.6f)).sp,
                    color = colors.ink,
                    modifier = Modifier.width(78.dp),
                )
                Column(Modifier.weight(1f)) {
                    Slider(
                        value = draftScale.coerceIn(.75f, 1.6f),
                        onValueChange = { draftScale = it },
                        onValueChangeFinished = { onScaleChange(draftScale) },
                        valueRange = .75f..1.6f,
                        steps = 16,
                        enabled = mode == FontSizeMode.CUSTOM,
                    )
                    Text(
                        if (mode == FontSizeMode.CUSTOM) "${(draftScale * 100).toInt()}%" else "自定义模式可调整",
                        style = QztcTypography.compact,
                        color = colors.mut,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingGroup(title: String, items: List<SettingItem>) {
    val colors = LocalQztcColors.current
    Column {
        Text(
            text = title,
            style = QztcTypography.section,
            color = colors.mut,
            modifier = Modifier.padding(start = QztcSpacing.sm, bottom = QztcSpacing.sm),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(QztcCorners.content))
                .background(colors.solid),
        ) {
            items.forEachIndexed { index, item ->
                SettingRow(item)
                if (index != items.lastIndex) {
                    Spacer(
                        Modifier
                            .padding(start = 58.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colors.line),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingRow(item: SettingItem) {
    val colors = LocalQztcColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clickable(enabled = item.enabled, onClick = item.onClick)
            .padding(horizontal = QztcSpacing.lg, vertical = QztcSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            item.icon,
            contentDescription = null,
            tint = if (item.enabled) colors.mut else colors.faint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(QztcSpacing.lg))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = QztcTypography.courseTitle, color = if (item.enabled) colors.ink else colors.faint)
            Text(item.subtitle, style = QztcTypography.meta, color = colors.mut)
        }
        Spacer(Modifier.width(QztcSpacing.lg))
        when {
            item.switchValue != null && item.onSwitch != null -> Switch(
                checked = item.switchValue,
                onCheckedChange = item.onSwitch,
                enabled = item.enabled,
            )
            item.value != null -> Text(
                item.value,
                style = QztcTypography.meta,
                color = colors.mut,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            else -> Text("›", style = QztcTypography.pageTitle, color = colors.faint)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceSheet(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(bottom = QztcSpacing.xxl)) {
            Text(
                title,
                style = QztcTypography.pageTitle,
                modifier = Modifier.padding(horizontal = QztcSpacing.xxl, vertical = QztcSpacing.sm),
            )
            options.forEach { (value, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp)
                        .clickable { onSelect(value) }
                        .padding(horizontal = QztcSpacing.xl),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected == value, onClick = { onSelect(value) })
                    Text(label, style = QztcTypography.body, modifier = Modifier.padding(start = QztcSpacing.md))
                }
            }
        }
    }
}

@Composable
fun PersonalizationScreen(
    themeMode: QztcThemeMode,
    onThemeModeChange: (QztcThemeMode) -> Unit,
    palette: QztcPalette = QztcPalette.ORIGINAL,
    onPaletteChange: (QztcPalette) -> Unit = {},
    glassQuality: GlassQuality,
    onGlassQualityChange: (GlassQuality) -> Unit,
    functionalAlpha: Float,
    functionalBlur: Float,
    contentAlpha: Float,
    contentBlur: Float,
    widgetBackgroundAlpha: Float,
    glassWidgetBackgroundAlpha: Float,
    onWidgetBackgroundAlphaChange: (Float, Boolean) -> Unit,
    onFunctionalAlphaChange: (Float) -> Unit,
    onFunctionalBlurChange: (Float) -> Unit,
    onContentAlphaChange: (Float) -> Unit,
    onContentBlurChange: (Float) -> Unit,
    onResetFunctionalGlassParameters: () -> Unit,
    onResetContentGlassParameters: () -> Unit,
    backgroundSource: BackgroundSource,
    backgroundMotion: BackgroundMotion,
    backgroundCarouselEnabled: Boolean,
    backgroundCarouselIntervalSeconds: Int,
    personalizationScrollState: ScrollState,
    importedBackgrounds: List<ImportedBackground>,
    selectedBackgroundPath: String?,
    onBackgroundSourceChange: (BackgroundSource) -> Unit,
    onBackgroundMotionChange: (BackgroundMotion) -> Unit,
    onBackgroundCarouselEnabledChange: (Boolean) -> Unit,
    onBackgroundCarouselIntervalSecondsChange: (Int) -> Unit,
    onBackgroundImageSelect: (String) -> Unit,
    onBackgroundImageDelete: (String) -> Unit = {},
    onChooseBackgroundImage: () -> Unit,
    presetColors: List<Int> = emptyList(),
    onPresetColorsChange: (List<Int>) -> Unit = {},
    onEditCurrentCourseColors: () -> Unit = {},
    textPrimaryColor: Int? = null,
    textSecondaryColor: Int? = null,
    textTertiaryColor: Int? = null,
    onTextPrimaryColorChange: (Int?) -> Unit = {},
    onTextSecondaryColorChange: (Int?) -> Unit = {},
    onTextTertiaryColorChange: (Int?) -> Unit = {},
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    val colors = LocalQztcColors.current
    val appliedGlass = LocalGlassSettings.current
    var backgroundSheetOpen by remember { mutableStateOf(false) }
    var draftFunctionalAlpha by remember { mutableFloatStateOf(functionalAlpha) }
    var draftFunctionalBlur by remember { mutableFloatStateOf(functionalBlur) }
    var draftContentAlpha by remember { mutableFloatStateOf(contentAlpha) }
    var draftContentBlur by remember { mutableFloatStateOf(contentBlur) }
    var widgetTransparency by remember(widgetBackgroundAlpha) { mutableFloatStateOf(1f - widgetBackgroundAlpha) }
    var glassWidgetTransparency by remember(glassWidgetBackgroundAlpha) { mutableFloatStateOf(1f - glassWidgetBackgroundAlpha) }

    CompositionLocalProvider(
        LocalGlassSettings provides appliedGlass.copy(
            functionalAlpha = draftFunctionalAlpha,
            functionalBlur = draftFunctionalBlur.dp,
            contentAlpha = draftContentAlpha,
            contentBlur = draftContentBlur.dp,
        ),
    ) {
    Column(
        modifier = modifier.fillMaxSize().background(colors.solid2),
    ) {
        QztcPageTopBar(title = "显示与外观", onBack = onBack)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(personalizationScrollState).padding(QztcSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(QztcSpacing.lg),
        ) {
            Text("外观预览", style = QztcTypography.section, color = colors.mut)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(QztcSpacing.xs)) {
                listOf(QztcPalette.ORIGINAL to "森林青苔", QztcPalette.SODA to "海盐汽水", QztcPalette.CITRUS to "青柠晴空")
                    .forEach { (option, name) ->
                        Text(
                            name,
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                .background(if (palette == option) colors.accentSoft else colors.solid)
                                .clickable { onPaletteChange(option) }.padding(vertical = 12.dp),
                            textAlign = TextAlign.Center,
                            style = QztcTypography.meta,
                            color = if (palette == option) colors.accentDeep else colors.ink,
                        )
                    }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(QztcCorners.content))
                    .background(colors.solid)
                    .padding(QztcSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(QztcSpacing.xs),
            ) {
                listOf(
                    QztcThemeMode.SYSTEM to "跟随系统",
                    QztcThemeMode.LIGHT to "浅色",
                    QztcThemeMode.DARK to "深色",
                ).forEach { (mode, label) ->
                    Text(
                        label,
                        style = QztcTypography.meta,
                        color = if (themeMode == mode) colors.onAccent else colors.mut,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(QztcSpacing.md))
                            .background(if (themeMode == mode) colors.accent else colors.solid)
                            .clickable { onThemeModeChange(mode) }
                            .padding(vertical = QztcSpacing.md),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Text("课程颜色", style = QztcTypography.section, color = colors.mut)
            CourseColorEditor(
                presetColors = presetColors,
                onColorsChange = onPresetColorsChange,
            )
            TextButton(onClick = onEditCurrentCourseColors) { Text("调整当前课程颜色") }
            Text("小部件文字颜色", style = QztcTypography.section, color = colors.mut)
            Text("分别调整主要文字、说明文字和辅助信息；恢复默认会跟随主题。", style = QztcTypography.meta, color = colors.mut)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(QztcCorners.content)).background(colors.solid)
                    .padding(QztcSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(QztcSpacing.md),
            ) {
                TextColorChoice("主要文字", "课程名、标题", textPrimaryColor, colors.ink, onTextPrimaryColorChange)
                TextColorChoice("说明文字", "地点、教师、副标题", textSecondaryColor, colors.mut, onTextSecondaryColorChange)
                TextColorChoice("辅助信息", "箭头、次要标注", textTertiaryColor, colors.faint, onTextTertiaryColorChange)
            }
            Text("小部件透明度", style = QztcTypography.section, color = colors.mut)
            Text("0% 为不透明，100% 为完全透明。仅调整背景，文字与课程颜色保持清晰；松手即保存并更新桌面。", style = QztcTypography.meta, color = colors.mut)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(QztcCorners.content)).background(colors.solid)
                    .padding(horizontal = QztcSpacing.lg, vertical = QztcSpacing.sm),
            ) {
                GlassParameterSlider(
                    title = "普通小部件",
                    valueLabel = "${kotlin.math.round(widgetTransparency * 100).toInt()}%",
                    value = widgetTransparency, range = 0f..1f,
                    onValueChange = { widgetTransparency = it },
                    onValueChangeFinished = { onWidgetBackgroundAlphaChange(1f - widgetTransparency, false) },
                )
                GlassParameterSlider(
                    title = "玻璃小部件",
                    valueLabel = "${kotlin.math.round(glassWidgetTransparency * 100).toInt()}%",
                    value = glassWidgetTransparency, range = 0f..1f,
                    onValueChange = { glassWidgetTransparency = it },
                    onValueChangeFinished = { onWidgetBackgroundAlphaChange(1f - glassWidgetTransparency, true) },
                )
                TextButton(onClick = {
                    widgetTransparency = .04f
                    glassWidgetTransparency = .15f
                    onWidgetBackgroundAlphaChange(.96f, false)
                    onWidgetBackgroundAlphaChange(.85f, true)
                }) { Text("恢复小部件默认") }
            }
            Text("玻璃档位", style = QztcTypography.section, color = colors.mut)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(QztcCorners.content))
                    .background(colors.solid)
                    .padding(QztcSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(QztcSpacing.xs),
            ) {
                listOf(
                    GlassQuality.FULL_LENS to "完整",
                    GlassQuality.BLUR_TINT to "模糊",
                    GlassQuality.SOLID_SURFACE to "实色",
                ).forEach { (quality, label) ->
                    Text(
                        label,
                        style = QztcTypography.meta,
                        color = if (glassQuality == quality) colors.onAccent else colors.mut,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(QztcSpacing.md))
                            .background(if (glassQuality == quality) colors.accent else colors.solid)
                            .clickable { onGlassQualityChange(quality) }
                            .pressable()
                            .padding(vertical = QztcSpacing.md),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Text(
                "完整档包含背景模糊与光学边缘，模糊档保留色调，实色档提供最高对比度。",
                style = QztcTypography.body,
                color = colors.mut,
            )
            Text("导航栏玻璃", style = QztcTypography.section, color = colors.mut)
            Text(
                "用在顶部栏和底部今日 / 周课表 / 我的。这块玻璃会浮在页面上面，方便点按。",
                style = QztcTypography.meta,
                color = colors.mut,
            )
            GlassSurface(
                role = GlassRole.FUNCTIONAL,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(QztcSpacing.lg), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("导航栏预览", style = QztcTypography.courseTitle, color = colors.ink)
                    Text("拖动下方滑杆，上方导航栏和这块预览会跟着变。松手后再保存。", style = QztcTypography.meta, color = colors.mut)
                }
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(QztcCorners.content)).background(colors.solid)
                    .padding(horizontal = QztcSpacing.lg, vertical = QztcSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(QztcSpacing.xs),
            ) {
                GlassParameterSlider(
                    title = "导航栏透明度",
                    valueLabel = "${(draftFunctionalAlpha * 100).toInt()}%",
                    value = draftFunctionalAlpha,
                    range = .15f..0.80f,
                    onValueChange = { value ->
                        draftFunctionalAlpha = value
                    },
                    onValueChangeFinished = {
                        onFunctionalAlphaChange(draftFunctionalAlpha)
                    },
                )
                GlassParameterSlider(
                    title = "导航栏模糊",
                    valueLabel = "${draftFunctionalBlur.toInt()} dp",
                    value = draftFunctionalBlur,
                    range = 0f..30f,
                    onValueChange = { value ->
                        draftFunctionalBlur = value
                    },
                    onValueChangeFinished = {
                        onFunctionalBlurChange(draftFunctionalBlur)
                    },
                )
                OutlinedButton(
                    onClick = {
                        draftFunctionalAlpha = 0.45f
                        draftFunctionalBlur = 13f
                        onResetFunctionalGlassParameters()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("恢复导航栏默认")
                }
            }
            Text("课程卡片玻璃", style = QztcTypography.section, color = colors.mut)
            Text(
                "用在课程卡片、课程详情这类要长时间阅读的信息上，优先保证字能看清。",
                style = QztcTypography.meta,
                color = colors.mut,
            )
            GlassSurface(
                role = GlassRole.CONTENT,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(QztcSpacing.lg), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("课程卡片预览", style = QztcTypography.courseTitle, color = colors.ink)
                    Text("拖动下方滑杆，这块预览会跟着变。松手后再保存。", style = QztcTypography.meta, color = colors.mut)
                }
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(QztcCorners.content)).background(colors.solid)
                    .padding(horizontal = QztcSpacing.lg, vertical = QztcSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(QztcSpacing.xs),
            ) {
                GlassParameterSlider(
                    title = "课程卡片透明度",
                    valueLabel = "${(draftContentAlpha * 100).toInt()}%",
                    value = draftContentAlpha,
                    range = .20f..0.95f,
                    onValueChange = { value ->
                        draftContentAlpha = value
                    },
                    onValueChangeFinished = {
                        onContentAlphaChange(draftContentAlpha)
                    },
                )
                GlassParameterSlider(
                    title = "课程卡片模糊",
                    valueLabel = "${draftContentBlur.toInt()} dp",
                    value = draftContentBlur,
                    range = 0f..30f,
                    onValueChange = { value ->
                        draftContentBlur = value
                    },
                    onValueChangeFinished = {
                        onContentBlurChange(draftContentBlur)
                    },
                )
                OutlinedButton(
                    onClick = {
                        draftContentAlpha = 0.50f
                        draftContentBlur = 14f
                        onResetContentGlassParameters()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("恢复课程卡片默认")
                }
            }
            Text("背景", style = QztcTypography.section, color = colors.mut)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(QztcCorners.content)).background(colors.solid),
            ) {
                BackgroundChoiceRow(
                    title = "内置晨空 / 夜空",
                    selected = backgroundSource == BackgroundSource.SCENE && backgroundMotion != BackgroundMotion.OFF,
                ) { onBackgroundSourceChange(BackgroundSource.SCENE) }
                BackgroundChoiceRow(
                    title = if (importedBackgrounds.isEmpty()) "导入背景图片" else "已导入图片",
                    selected = backgroundSource == BackgroundSource.IMAGE && backgroundMotion != BackgroundMotion.OFF,
                ) {
                    if (importedBackgrounds.isEmpty()) onChooseBackgroundImage() else backgroundSheetOpen = true
                }
                BackgroundChoiceRow(
                    title = "关闭背景",
                    selected = backgroundMotion == BackgroundMotion.OFF,
                ) { onBackgroundMotionChange(BackgroundMotion.OFF) }
            }
            if (backgroundMotion != BackgroundMotion.OFF) {
                Text("场景运动", style = QztcTypography.section, color = colors.mut)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(QztcCorners.content)).background(colors.solid).padding(QztcSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(QztcSpacing.xs),
                ) {
                    listOf(BackgroundMotion.DYNAMIC to "动态", BackgroundMotion.STATIC to "静态").forEach { (motion, label) ->
                        Text(
                            label,
                            style = QztcTypography.meta,
                            color = if (backgroundMotion == motion) colors.onAccent else colors.mut,
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(QztcSpacing.md))
                                .background(if (backgroundMotion == motion) colors.accent else colors.solid)
                                .clickable { onBackgroundMotionChange(motion) }.pressable()
                                .padding(vertical = QztcSpacing.md),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
    }

    if (backgroundSheetOpen) {
        BackgroundImageSheet(
            images = importedBackgrounds,
            selectedPath = selectedBackgroundPath,
            onSelect = { path ->
                backgroundSheetOpen = false
                onBackgroundImageSelect(path)
            },
            onDelete = onBackgroundImageDelete,
            carouselEnabled = backgroundCarouselEnabled,
            carouselIntervalSeconds = backgroundCarouselIntervalSeconds,
            onCarouselEnabledChange = onBackgroundCarouselEnabledChange,
            onCarouselIntervalChange = onBackgroundCarouselIntervalSecondsChange,
            onImport = {
                backgroundSheetOpen = false
                onChooseBackgroundImage()
            },
            onDismiss = { backgroundSheetOpen = false },
        )
    }
}

@Composable
private fun TextColorChoice(
    label: String,
    description: String,
    selectedColor: Int?,
    previewColor: androidx.compose.ui.graphics.Color,
    onChange: (Int?) -> Unit,
) {
    val colors = LocalQztcColors.current
    val current = selectedColor?.let(::Color) ?: previewColor
    val currentHex = String.format("#%06X", current.toArgb() and 0xFFFFFF)
    var colorPickerOpen by remember { mutableStateOf(false) }
    var optionsOpen by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(QztcSpacing.xs)) {
        Row(
            Modifier.fillMaxWidth().clickable { optionsOpen = true }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(QztcSpacing.md),
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(current)
                    .border(1.dp, colors.line, RoundedCornerShape(10.dp)),
            )
            Column(Modifier.weight(1f)) {
                Text(label, style = QztcTypography.body, color = colors.ink)
                Text(
                    if (selectedColor == null) "跟随主题 · $description" else "$currentHex · $description",
                    style = QztcTypography.meta,
                    color = colors.mut,
                )
            }
        }
    }
    if (optionsOpen) AlertDialog(
        onDismissRequest = { optionsOpen = false },
        title = { Text(label) },
        text = {
            Column {
                Text("当前 $currentHex", style = QztcTypography.meta)
                Text("跟随主题", Modifier.fillMaxWidth().clickable { onChange(null); optionsOpen = false }.padding(vertical = 14.dp))
                Text("打开调色盘", Modifier.fillMaxWidth().clickable { optionsOpen = false; colorPickerOpen = true }.padding(vertical = 14.dp))
            }
        },
        confirmButton = { TextButton(onClick = { optionsOpen = false }) { Text("取消") } },
    )
    if (colorPickerOpen) {
        ColorWheelDialog(
            label = label,
            initialColor = current.toArgb(),
            onApply = { onChange(it); colorPickerOpen = false },
            onDismiss = { colorPickerOpen = false },
        )
    }
}

@Composable
private fun ColorWheelDialog(
    label: String,
    initialColor: Int,
    onApply: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalQztcColors.current
    var selectedArgb by remember(initialColor) { mutableIntStateOf(initialColor) }
    val hsv = remember(selectedArgb) { FloatArray(3).also { android.graphics.Color.colorToHSV(selectedArgb, it) } }
    var hex by remember(initialColor) {
        mutableStateOf(String.format("#%06X", selectedArgb and 0xFFFFFF))
    }

    fun selectColor(argb: Int) {
        selectedArgb = argb
        hex = String.format("#%06X", argb and 0xFFFFFF)
    }

    fun selectWheel(position: Offset, width: Float, height: Float) {
        val radius = minOf(width, height) / 2f
        val dx = position.x - width / 2f
        val dy = position.y - height / 2f
        val hue = ((Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 360f) % 360f)
        val saturation = (hypot(dx, dy) / radius).coerceIn(0f, 1f)
        val currentHsv = FloatArray(3)
        android.graphics.Color.colorToHSV(selectedArgb, currentHsv)
        selectColor(Color.hsv(hue, saturation, currentHsv[2]).toArgb())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(QztcSpacing.md),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Canvas(
                        Modifier
                            .size(216.dp)
                            .pointerInput(Unit) {
                                detectTapGestures { selectWheel(it, size.width.toFloat(), size.height.toFloat()) }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { selectWheel(it, size.width.toFloat(), size.height.toFloat()) },
                                    onDrag = { change, _ ->
                                        selectWheel(change.position, size.width.toFloat(), size.height.toFloat())
                                        change.consume()
                                    },
                                )
                            },
                    ) {
                        val radius = size.minDimension / 2f
                        val center = Offset(size.width / 2f, size.height / 2f)
                        drawCircle(
                            brush = Brush.sweepGradient(
                                listOf(
                                    Color.Red,
                                    Color.Yellow,
                                    Color.Green,
                                    Color.Cyan,
                                    Color.Blue,
                                    Color.Magenta,
                                    Color.Red,
                                ),
                                center = center,
                            ),
                            radius = radius,
                            center = center,
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.White, Color.Transparent),
                                center = center,
                                radius = radius,
                            ),
                            radius = radius,
                            center = center,
                        )
                        if (hsv[2] < .999f) {
                            drawCircle(Color.Black.copy(alpha = 1f - hsv[2]), radius, center)
                        }
                        val angle = Math.toRadians(hsv[0].toDouble())
                        val markerRadius = radius * hsv[1]
                        val marker = Offset(
                            center.x + kotlin.math.cos(angle).toFloat() * markerRadius,
                            center.y + kotlin.math.sin(angle).toFloat() * markerRadius,
                        )
                        drawCircle(Color.White, 9.dp.toPx(), marker)
                        drawCircle(Color.Black, 9.dp.toPx(), marker, style = Stroke(2.dp.toPx()))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QztcSpacing.md)) {
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(Color(selectedArgb))
                            .border(1.dp, colors.line, RoundedCornerShape(9.dp)),
                    )
                    Column(Modifier.weight(1f)) {
                        Text("亮度", style = QztcTypography.meta, color = colors.mut)
                        Slider(
                            value = hsv[2],
                            onValueChange = { value -> selectColor(Color.hsv(hsv[0], hsv[1], value).toArgb()) },
                            valueRange = 0f..1f,
                        )
                    }
                }
                OutlinedTextField(
                    value = hex,
                    onValueChange = { input ->
                        val cleaned = input.filter { it == '#' || it.isDigit() || it.lowercaseChar() in 'a'..'f' }.take(9)
                        hex = cleaned
                        val parsed = parseColorCode(cleaned)
                        if (parsed != null) selectedArgb = parsed
                    },
                    label = { Text("色号") },
                    placeholder = { Text("#RRGGBB 或 #AARRGGBB") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onApply(selectedArgb) }, enabled = parseColorCode(hex) != null) {
                Text("应用颜色")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun parseColorCode(value: String): Int? {
    val digits = value.trim().removePrefix("#")
    val expanded = if (digits.length == 3) digits.map { "$it$it" }.joinToString("") else digits
    return when (expanded.length) {
        6 -> expanded.toLongOrNull(16)?.let { (0xFF000000L or it).toInt() }
        8 -> expanded.toLongOrNull(16)?.toInt()
        else -> null
    }
}

@Composable
private fun GlassParameterSlider(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    val colors = LocalQztcColors.current
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = QztcTypography.body, color = colors.ink, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(QztcSpacing.lg))
            Text(valueLabel, style = QztcTypography.meta, color = colors.mut)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            modifier = Modifier.fillMaxWidth().height(30.dp),
        )
    }
}

@Composable
private fun BackgroundChoiceRow(title: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalQztcColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable(onClick = onClick).padding(horizontal = QztcSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(title, style = QztcTypography.body, color = colors.ink, modifier = Modifier.padding(start = QztcSpacing.md))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackgroundImageSheet(
    images: List<ImportedBackground>,
    selectedPath: String?,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    carouselEnabled: Boolean,
    carouselIntervalSeconds: Int,
    onCarouselEnabledChange: (Boolean) -> Unit,
    onCarouselIntervalChange: (Int) -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalQztcColors.current
    var pendingDelete by remember { mutableStateOf<ImportedBackground?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = QztcSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(QztcSpacing.sm),
        ) {
            Text("已导入图片", style = QztcTypography.pageTitle, color = colors.ink)
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(images, key = { it.path }) { image ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            BackgroundChoiceRow(
                                title = image.label,
                                selected = image.path == selectedPath,
                                onClick = { onSelect(image.path) },
                            )
                        }
                        IconButton(onClick = { pendingDelete = image }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "删除${image.label}", tint = colors.mut)
                        }
                    }
                }
            }
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Text("导入背景图片")
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("背景图片轮播", style = QztcTypography.body, color = colors.ink)
                    Text(
                        if (images.size < 2) "再导入一张图片即可轮播" else "在已导入的图片间自动切换",
                        style = QztcTypography.meta,
                        color = colors.mut,
                    )
                }
                Switch(
                    checked = carouselEnabled,
                    enabled = images.size >= 2,
                    onCheckedChange = onCarouselEnabledChange,
                )
            }
            if (images.size >= 2 && carouselEnabled) {
                val safeInterval = carouselIntervalSeconds.coerceIn(1, 86_400)
                var hoursText by remember(carouselIntervalSeconds) { mutableStateOf((safeInterval / 3600).toString()) }
                var minutesText by remember(carouselIntervalSeconds) { mutableStateOf(((safeInterval % 3600) / 60).toString()) }
                var secondsText by remember(carouselIntervalSeconds) { mutableStateOf((safeInterval % 60).toString()) }
                fun commitInterval() {
                    val hours = (hoursText.toIntOrNull() ?: 0).coerceIn(0, 24)
                    val minutes = (minutesText.toIntOrNull() ?: 0).coerceIn(0, 59)
                    val seconds = (secondsText.toIntOrNull() ?: 0).coerceIn(0, 59)
                    onCarouselIntervalChange((hours * 3600 + minutes * 60 + seconds).coerceIn(1, 86_400))
                }
                Text("切换间隔", style = QztcTypography.meta, color = colors.mut)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(QztcSpacing.sm),
                ) {
                    OutlinedTextField(
                        value = hoursText,
                        onValueChange = { hoursText = it.filter(Char::isDigit).take(2); commitInterval() },
                        modifier = Modifier.weight(1f),
                        label = { Text("时") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = { minutesText = it.filter(Char::isDigit).take(2); commitInterval() },
                        modifier = Modifier.weight(1f),
                        label = { Text("分") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = secondsText,
                        onValueChange = { secondsText = it.filter(Char::isDigit).take(2); commitInterval() },
                        modifier = Modifier.weight(1f),
                        label = { Text("秒") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                Text("请输入 0-24 小时、0-59 分、0-59 秒", style = QztcTypography.meta, color = colors.mut)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    pendingDelete?.let { image ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除背景图片") },
            text = { Text("要删除${image.label}吗？") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(image.path)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

private fun formatCarouselInterval(seconds: Int): String {
    val safe = seconds.coerceAtLeast(60)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}小时${minutes}分钟"
        hours > 0 -> "${hours}小时"
        else -> "${minutes}分钟"
    }
}
