package cn.qztc.timetable.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.qztc.timetable.core.domain.model.Semester
import cn.qztc.timetable.core.ui.theme.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun SemesterProgressCard(semester: Semester?, teachingWeeks: Int?, actionLabel: String, onAction: () -> Unit,
    onStartDate: (() -> Unit)? = null, onSemesterSwitch: (() -> Unit)? = null) {
    val colors = LocalQztcColors.current
    val week = semester?.let {
        (ChronoUnit.DAYS.between(it.startDate, LocalDate.now()) / 7 + 1).toInt()
            .takeIf { _ -> !LocalDate.now().isBefore(it.startDate) }
    }
    val weeks = teachingWeeks?.takeIf { semester != null && it > 0 }
    val progress = if (weeks != null) ((week ?: 0).toFloat() / weeks).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.solid)
        .border(1.dp, colors.line, RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(semester?.name ?: "尚未导入课表", style = QztcTypography.section, fontSize = 24.sp, lineHeight = 28.sp, maxLines = 1, color = colors.ink)
                    onSemesterSwitch?.let { action ->
                        androidx.compose.material3.IconButton(onClick = action) {
                            Text("⇋", style = QztcTypography.section, color = colors.accentDeep,
                                modifier = Modifier.semantics { contentDescription = "切换学期" })
                        }
                    }
                }
                Text(when {
                    weeks == null -> "导入教务课表后显示实际教学进度"
                    week == null -> "尚未开学 · 课程排至第 $weeks 周"
                    week > weeks -> "本学期课程已结束 · 共 $weeks 周"
                    else -> "当前第 $week 周 · 还有 ${weeks - week} 周结课"
                }, style = QztcTypography.meta, color = colors.mut)
            }
            Text(actionLabel, Modifier.clip(RoundedCornerShape(8.dp)).background(colors.accentSoft).clickable(interactionSource = null, indication = null, onClick = onAction)
                .padding(horizontal = 10.dp, vertical = 8.dp), style = QztcTypography.meta, color = colors.accentDeep)
        }
        Box(Modifier.fillMaxWidth().height(5.dp).background(colors.accentSoft, RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth(progress).height(5.dp).clip(RoundedCornerShape(3.dp)).background(colors.accent))
            // 期中节点：固定在进度条中点，便于判断当前进度是否已过半。
            Box(Modifier.fillMaxWidth(.5f).fillMaxHeight().align(Alignment.CenterStart)) {
                Box(Modifier.requiredSize(8.dp).offset(x = 4.dp).align(Alignment.CenterEnd).clip(androidx.compose.foundation.shape.CircleShape).background(colors.accent))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("开学日期" to (semester?.startDate?.let { "${it.monthValue}月${it.dayOfMonth}日" } ?: "待导入"),
                "本周" to (week?.let { "第 $it 周" } ?: if (semester == null) "待导入" else "未开学"), "课程教学周" to (weeks?.let { "$it 周" } ?: "待导入")).forEachIndexed { index, (label, value) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(colors.solid2)
                    .then(if (index == 0 && onStartDate != null) Modifier.clickable(interactionSource = null, indication = null, onClick = onStartDate) else Modifier).padding(9.dp)) {
                    Text(label, style = QztcTypography.meta, color = colors.mut)
                    Text(value, style = QztcTypography.meta, color = colors.ink, maxLines = 1)
                }
            }
        }
    }
}
