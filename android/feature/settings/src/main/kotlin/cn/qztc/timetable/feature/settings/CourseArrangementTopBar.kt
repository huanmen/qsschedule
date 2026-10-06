package cn.qztc.timetable.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.domain.model.Semester
import cn.qztc.timetable.core.domain.calendar.SemesterCalendar
import cn.qztc.timetable.core.ui.theme.*
import cn.qztc.timetable.core.ui.glass.*
import java.time.LocalDate

@Composable
internal fun CourseArrangementTopBar(semester: Semester, onBack: () -> Unit, onChoose: () -> Unit) {
    val colors = LocalQztcColors.current
    GlassSurface(role = GlassRole.FUNCTIONAL, modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=14.dp,vertical=6.dp)) {
        Row(Modifier.fillMaxWidth().height(52.dp),verticalAlignment=Alignment.CenterVertically) {
            QztcTopIconButton(Icons.AutoMirrored.Outlined.ArrowBack,"返回",onBack)
            Text("课程安排",style=QztcTypography.courseTitle,color=colors.ink)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).padding(end=10.dp).clickable(onClick=onChoose),horizontalAlignment=Alignment.End) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(semester.name,style=QztcTypography.body,color=colors.ink,modifier=Modifier.weight(1f,fill=false),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    if(SemesterCalendar.weekOf(semester,LocalDate.now())!=null) Text(" · 本学期",style=QztcTypography.compact,color=colors.accent)
                    Icon(Icons.Outlined.ExpandMore,null,Modifier.size(16.dp),tint=colors.mut)
                }
            }
        }
    }
}
