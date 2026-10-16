package cn.qztc.timetable.feature.timetable

import cn.qztc.timetable.core.domain.model.ActivityKind
import org.junit.Assert.*
import org.junit.Test

class ActivitySessionDraftTest {
    @Test fun blankPeriodDraftRequiresUserToChooseTimeAndWeeks() {
        val draft = ActivitySessionDraft("new", ActivityKind.PERIOD)
        assertNull(draft.weekday)
        assertNull(draft.periodStart)
        assertNull(draft.periodEnd)
        assertFalse(draft.valid)
        val time = draft.copy(weekday = 3, periodStart = 5, periodEnd = 6)
        assertFalse(time.valid)
        val ready = time.copy(weeks = listOf(2, 4))
        assertTrue(ready.valid)
        assertEquals(3, ready.toSession().weekday)
        assertEquals(listOf(2, 4), ready.toSession().weeks)
    }
}
