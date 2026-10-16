package cn.qztc.timetable.feature.timetable

import cn.qztc.timetable.core.domain.model.ActivityMemoTime
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ActivityMemoEndpointTest {
    private val date=LocalDate.of(2026,10,15)
    @Test fun lateStartMovesEndToNextDayAndEarlyEndMovesStartToPreviousDay() {
        val entry=ActivityMemoTime(date,540,date,600)
        val start=adjustMemoEndpoint(entry,false,date,1080)
        assertEquals(date.plusDays(1),start.endDate)
        assertEquals(600,start.endMinute)
        val end=adjustMemoEndpoint(entry,true,date,480)
        assertEquals(date.minusDays(1),end.date)
        assertEquals(540,end.minute)
    }
    @Test fun distantDateSelectionCannotCrashTheEditor() {
        val entry=ActivityMemoTime(date,endDate=date.plusDays(1))
        val changed=adjustMemoEndpoint(entry,true,date.plusYears(2),null)
        assertEquals(366,java.time.temporal.ChronoUnit.DAYS.between(changed.date,changed.endDate).toInt())
    }
}
