package cn.qztc.timetable.core.domain.schedule

import cn.qztc.timetable.core.domain.model.CourseMeeting
import cn.qztc.timetable.core.domain.model.SourceType
import cn.qztc.timetable.core.domain.model.WeekRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TeachingProgressTest {
    private fun meeting(id: String, source: SourceType, weeks: List<Int>) = CourseMeeting(
        id = id, courseId = id, weekday = 1, periodStart = 1, periodEnd = 2,
        weekRule = WeekRule.of(weeks), sourceType = source,
    )

    @Test fun usesLastActualImportedWeekIncludingOddWeekRules() {
        val state = TimetableState(meetings = listOf(
            meeting("odd", SourceType.QNU_EDU, (1..17 step 2).toList()),
            meeting("short", SourceType.QNU_EDU, (3..12).toList()),
            meeting("manual", SourceType.MANUAL, listOf(20)),
        ))
        assertEquals(17, state.importedTeachingWeeks)
        assertEquals(18, state.copy(meetings = state.meetings +
            meeting("late", SourceType.QNU_EDU, listOf(18))).importedTeachingWeeks)
    }

    @Test fun absentImportedCoursesDoNotInventTwentyWeeks() {
        assertNull(TimetableState.EMPTY.importedTeachingWeeks)
        assertNull(TimetableState(meetings = listOf(meeting("manual", SourceType.MANUAL, listOf(18)))).importedTeachingWeeks)
    }
}
