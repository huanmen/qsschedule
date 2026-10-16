package cn.qztc.timetable.education

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    val total = cross?.earned ?: return "尚未取得跨学科已获学分，请先更新修读进程。"
    val arts = art?.earned ?: return "艺术学分尚未取得，请更新修读进程后核对。"
    val pending = pendingElectiveCredits(cross.courses)
    val pendingArt = pendingElectiveCredits(art.courses)
    fun amount(value: Double) = if (value % 1 == 0.0) value.toInt().toString() else value.toString()
    val current = "已获跨学科 ${amount(total)} 分，其中艺术 ${amount(arts)} 分。"
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

internal val electiveRules = listOf(
    "毕业要求" to "本科阶段需完成跨学科选修学分，通常要求 7–8 学分；以本人的培养方案及修读进程为准。艺术类至少 2 学分，包含在跨学科总学分内，不额外相加。",
    "每学期额度" to "每学期最多选 4 学分，不限制课程门数。课程通常为 1 或 2 学分，可组合成 2+2、2+1+1 或 1+1+1+1；从零开始修满 7 学分至少需两个学期，例如 4+3。",
    "艺术类如何判断" to "以选课表的“艺术类”课程分类为准，课程名称不能代替分类。可选一门 2 学分艺术课，或两门 1 学分艺术课，凑足 2 学分。",
    "线上学分上限" to "纯线上课程累计最多计入 4 学分，超出部分不能抵扣毕业缺口。若已计入 4 分线上学分，剩余至少 3 分应选线下或线上线下结合的课程。",
    "混合授课" to "线上线下结合按线下课程计算，不占纯线上 4 学分额度。报名前核对课程的授课方式，不能只看课程名称。",
    "选课策略" to "先安排艺术学分，再补跨学科总缺口。结合兴趣、课程难度、考核方式和上课时间选择能按时完成的课；核对周二、周四节次，优先减少不必要的门数。",
    "在修与已获学分" to "“已选课程成绩未知”表示已选或在修，并非未选课；建议会先考虑这些课程预计可获得的学分。只有通过并获学校认定后，才计入已获学分；未选与未通过课程不能计入。",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ElectiveAdviceSheet(progress: QnuStudyProgress?, onDismiss: () -> Unit) {
    val colors = LocalQztcColors.current
    val advice = remember(progress) { currentElectiveAdvice(progress) }
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
                electiveRules.forEachIndexed { index, (title, description) -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("${index + 1}、$title", style = QztcTypography.section, color = colors.ink)
                        Text(description, style = QztcTypography.body, color = colors.mut)
                    }
                } }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("知道了") }
        }
    }
}
