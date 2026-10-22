package cn.qztc.timetable.education

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.qztc.timetable.update.ResourceContentStore
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.ui.theme.*
import cn.qztc.timetable.integration.qnu.QnuCreditCourse
import cn.qztc.timetable.integration.qnu.QnuStudyProgress

internal fun pendingElectiveCredits(courses: List<QnuCreditCourse>): Double = courses
    .filter { creditCourseStatus(it.status) == "已选课程成绩未知" }
    .distinctBy { it.code?.takeIf(String::isNotBlank) ?: it.name }
    .sumOf { it.credits ?: 0.0 }

internal fun currentElectiveAdvice(progress: QnuStudyProgress?): String {
    val cross = progress?.groups?.firstOrNull { it.name.contains("跨学科") }
    val art = progress?.groups?.firstOrNull { it.name.contains("艺术") }
    val crossCredits = cross?.earned ?: return "尚未取得跨学科已获学分，请先更新修读进程。"
    val arts = art?.earned ?: return "艺术学分尚未取得，请更新修读进程后核对。"
    val total = crossCredits + arts
    val pending = pendingElectiveCredits(cross.courses + art.courses)
    val pendingArt = pendingElectiveCredits(art.courses)
    fun amount(value: Double) = if (value % 1 == 0.0) value.toInt().toString() else value.toString()
    val current = "已获选修学分 ${amount(total)} 分，其中艺术 ${amount(arts)} 分。"
    if (pending == 0.0 && pendingArt == 0.0) return current + "\n" + electiveRecommendation(total, arts)
    val waiting = "另有已选、成绩未出课程 ${amount(pending)} 分，其中艺术 ${amount(pendingArt)} 分；这些课程暂未计入已获学分。"
    val advice = if (total + pending >= 7 && arts + pendingArt >= 2) {
        "现有已选课程全部通过后预计可达标，建议先完成在修课程、等待成绩确认，暂不重复选课。"
    } else {
        "按已选课程全部通过估算后续缺口：" + electiveRecommendation(total + pending, arts + pendingArt) +
            " 如在修课程未通过，再重新核对缺口。"
    }
    return "$current\n$waiting\n$advice"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ElectiveAdviceSheet(progress: QnuStudyProgress?, onDismiss: () -> Unit) {
    val colors = LocalQztcColors.current
    val advice = remember(progress) { currentElectiveAdvice(progress) }
    val context = LocalContext.current
    val revision by ResourceContentStore.revision.collectAsStateWithLifecycle()
    val rules = remember(revision) { ResourceContentStore.electiveAdvice(context) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.solid,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.84f).navigationBarsPadding().padding(horizontal = 20.dp)) {
            Text("选修学分解读", style = QztcTypography.pageTitle, color = colors.ink)
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp).background(colors.accentSoft, MaterialTheme.shapes.medium).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("当前进程建议", style = QztcTypography.section, color = colors.accentDeep)
                Text(advice, style = QztcTypography.body, color = colors.ink)
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                item { Text(rules, style = QztcTypography.body, color = colors.ink) }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("知道了") }
        }
    }
}
