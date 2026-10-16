package cn.qztc.timetable.feature.timetable
import cn.qztc.timetable.core.domain.model.ActivityMemoTime
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ActivityEndpointBoundsTest {
    @Test fun startPickerNeverAllowsADateBeforeToday() {
        val today=LocalDate.of(2026,10,16)
        assertEquals(today.atStartOfDay(),memoEndpointBounds(ActivityMemoTime(today.plusDays(2)),false,true,today).first)
        val ranged=ActivityMemoTime(today,540,today.plusDays(3),600)
        assertEquals(today.atStartOfDay(),memoEndpointBounds(ranged,false,false,today).first)
    }
    private val day=LocalDate.of(2026,10,15)
    @Test fun timedEndpointsCannotCrossOrEqualEachOther() {
        val slot=ActivityMemoTime(day,540,day,600)
        val start=memoEndpointBounds(slot,false,false)
        val end=memoEndpointBounds(slot,true,false)
        assertEquals(day.atTime(9,59),start.second)
        assertEquals(day.atTime(9,1),end.first)
        assertEquals(day.minusDays(366).atStartOfDay(),start.first)
        assertEquals(day.plusDays(366).atTime(java.time.LocalTime.MAX),end.second)
    }
    @Test fun allDayAllowsTheSameDateAndMissingEndDoesNotLimitStart() {
        val slot=ActivityMemoTime(day,endDate=day.plusDays(3))
        assertEquals(day.plusDays(3).atTime(java.time.LocalTime.MAX),memoEndpointBounds(slot,false,true).second)
        assertEquals(day.atStartOfDay(),memoEndpointBounds(slot,true,true).first)
        assertEquals(null to null,memoEndpointBounds(ActivityMemoTime(day),false,true))
    }
}
