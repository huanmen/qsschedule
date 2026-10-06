package cn.qztc.timetable.core.domain.schedule

import cn.qztc.timetable.core.domain.model.*
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class UpcomingScheduleChangesTest {
    @Test fun syncKeepsCoursesAndOnlyAppliesUnfinishedCancellationAndMove() {
        val semester=Semester("s","测试",2026,Term.AUTUMN,LocalDate.of(2026,9,14),20)
        val course=Course("c","s",SourceType.QNU_EDU,"c",name="测试课")
        val meeting=CourseMeeting("m","c",1,1,2,WeekRule.of(listOf(1,2)),"教师","教室",SourceType.QNU_EDU)
        val current=TimetableState(listOf(course),listOf(meeting))
        fun cancel(id:String,week:Int,start:Int)=ScheduleException(id,"incoming",ExceptionType.CANCEL,SlotRef(week,1,start,start),sourceType=SourceType.QNU_EDU)
        val incoming=TimetableState(listOf(course.copy(id="incoming")),emptyList(),listOf(cancel("past",1,1),cancel("next",1,3),cancel("future",2,1)))
        val periods=listOf(Period(1,"1",LocalTime.of(8,0),LocalTime.of(8,45),DaySegment.MORNING),Period(3,"3",LocalTime.of(10,0),LocalTime.of(10,45),DaySegment.MORNING))
        val result=UpcomingScheduleChanges.merge(semester,current,incoming,periods,LocalDateTime.of(2026,9,14,9,0))
        assertEquals(current.courses,result.courses);assertEquals(current.meetings,result.meetings)
        assertEquals(2,result.exceptions.size);assertTrue(result.exceptions.all { it.courseId=="c" })
        assertFalse(result.exceptions.any { it.original==SlotRef(1,1,1,1) })
    }
}
