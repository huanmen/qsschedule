package cn.qztc.timetable.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun QztcWeekPicker(selected: Int, totalWeeks: Int, markedWeeks: Set<Int> = emptySet(),
    onDismiss: () -> Unit, onChoose: (Int) -> Unit) {
    val colors = LocalQztcColors.current
    ModalBottomSheet(onDismissRequest=onDismiss, sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true), containerColor=colors.solid) {
        Column(Modifier.fillMaxWidth().semantics { testTagsAsResourceId = true }.verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("选择周次",style=QztcTypography.section,color=colors.ink)
            Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
                QztcWeekGrid(setOf(selected),totalWeeks,markedWeeks,columns=6,onChoose=onChoose)
            }
        }
    }
}

/** Shared compact grid for both single-week filtering and multiple-week arrangements. */
@Composable
fun QztcWeekGrid(selected: Set<Int>, totalWeeks: Int, markedWeeks: Set<Int> = emptySet(), columns:Int=5,isWeekEnabled:(Int)->Boolean={true},onChoose: (Int) -> Unit) {
    val colors=LocalQztcColors.current
    Column(Modifier.widthIn(max=280.dp).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            (1..totalWeeks.coerceIn(1,60)).chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    row.forEach { week ->
                        Column(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(8.dp))
                            .background(if(week in selected)colors.accent else colors.solid2).clickable(enabled=isWeekEnabled(week)||week in selected) {onChoose(week)},
                            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                            Text(week.toString(),style=QztcTypography.compact,color=if(!isWeekEnabled(week))colors.faint else if(week in selected)colors.onAccent else colors.ink)
                            Box(Modifier.size(4.dp).background(if(week in markedWeeks) {
                                if(week in selected)colors.onAccent else colors.accent
                            } else androidx.compose.ui.graphics.Color.Transparent,CircleShape)
                                .testTag(if(week in markedWeeks)"activity-week-dot-$week" else "empty-week-dot-$week"))
                        }
                    }
                    repeat(columns-row.size) {Spacer(Modifier.weight(1f))}
                }
            }
    }
}
