package cn.qztc.timetable.core.ui.theme
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test
class DateTimeBoundsTest {
    @Test fun inclusiveBoundsRejectTimesOutsideTheRange() {
        val min=LocalDateTime.of(2026,10,15,9,1)
        val max=LocalDateTime.of(2026,10,15,9,59)
        assertFalse(dateTimeAllowed(min.minusMinutes(1),min,max))
        assertTrue(dateTimeAllowed(min,min,max))
        assertTrue(dateTimeAllowed(max,min,max))
        assertFalse(dateTimeAllowed(max.plusMinutes(1),min,max))
        assertFalse(dateTimeAllowed(max.plusDays(1),min,max))
    }
    @Test fun anUnboundedSideDoesNotDisableDates() {
        val value=LocalDateTime.of(2026,10,15,12,0)
        assertTrue(dateTimeAllowed(value,null,null))
        assertTrue(dateTimeAllowed(value,value.minusYears(1),null))
        assertTrue(dateTimeAllowed(value,null,value.plusYears(1)))
    }
}

