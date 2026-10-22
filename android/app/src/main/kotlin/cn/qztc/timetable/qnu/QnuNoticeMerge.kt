package cn.qztc.timetable.qnu

import cn.qztc.timetable.core.domain.imports.ImportedTimetable
import cn.qztc.timetable.core.domain.model.ExceptionType
import cn.qztc.timetable.core.domain.schedule.UpcomingScheduleChanges
import java.time.LocalDateTime

/** Keep class notices for the student's courses, without importing classmates' courses. */
internal fun mergeClassNotices(personal: ImportedTimetable, classTable: ImportedTimetable): ImportedTimetable {
    val merged = UpcomingScheduleChanges.merge(
        QnuSemesterFactory.from(personal), personal.state, classTable.state,
        emptyList(), LocalDateTime.now(), includeExpired = true,
    )
    val confirmedNames = merged.exceptions.filter { it.type in setOf(ExceptionType.CANCEL, ExceptionType.MOVE) && it.original != null }
        .mapNotNull { notice -> merged.courses.firstOrNull { it.id == notice.courseId }?.name }.toSet()
    return personal.copy(state = merged, diagnostics = personal.diagnostics.filterNot { diagnostic ->
        diagnostic.code == "CANCEL_SCOPE_UNCONFIRMED" && confirmedNames.any { diagnostic.message.contains("《$it》") }
    })
}
