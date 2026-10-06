package cn.qztc.timetable.feature.settings

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import cn.qztc.timetable.core.domain.calendar.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class CalendarCandidateStoreTest {
    @Test fun editingCatalogDoesNotIncludeAnItemAndRemovingSurvivesReopen() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val key="event:synthetic-calendar-choice"
        val choices=CalendarSelectionStore(context)
        val catalog=CalendarCandidateStore(context,"calendar-draft-only")
        catalog.save(listOf(AcademicDay("draft","calendar-draft-only",LocalDate.of(2027,5,1),AcademicDayKind.HOLIDAY,"劳动节")))
        assertNull(choices.load()[key])
        choices.set(key,true)
        assertEquals(true,CalendarSelectionStore(context).load()[key])
        choices.set(key,false)
        assertEquals(false,CalendarSelectionStore(context).load()[key])
        assertEquals(1,catalog.load().size)
    }
    @Test fun disabledMakeupIsNotRestoredByOfficialRefresh() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val store=CalendarCandidateStore(context,"custom-holiday-test")
        val holiday=AcademicDay("h","custom-holiday-test",LocalDate.of(2026,10,1),AcademicDayKind.HOLIDAY,"国庆节")
        val makeup=holiday.copy(id="m",date=LocalDate.of(2026,10,10),kind=AcademicDayKind.MAKEUP,name="国庆调休")
        store.save(listOf(holiday))
        store.keepCustomization(listOf(holiday,makeup))
        val reopened=CalendarCandidateStore(context,"custom-holiday-test")
        assertEquals(listOf(holiday),reopened.load())
        assertFalse(reopened.acceptsOfficial(makeup))
        assertTrue(reopened.acceptsOfficial(holiday.copy(name="中秋节")))
    }
}
