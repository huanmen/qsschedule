package cn.qztc.timetable.core.domain.schedule

import cn.qztc.timetable.core.domain.model.CourseMeeting
import cn.qztc.timetable.core.domain.model.SourceType
import cn.qztc.timetable.core.domain.model.WeekRule
import org.junit.Assert.assertEquals
import org.junit.Test

class MeetingMergerTest {

    private fun meeting(
        courseId: String = "c1",
        weekday: Int = 1,
        start: Int,
        end: Int = start,
        teacher: String? = "老师A",
        room: String? = "教室A",
        weeks: List<Int> = (1..16).toList(),
    ) = CourseMeeting(
        id = CourseMeeting.stableId(courseId, weekday, start, end),
        courseId = courseId,
        weekday = weekday,
        periodStart = start,
        periodEnd = end,
        weekRule = WeekRule.of(weeks, "1-16周"),
        teacher = teacher,
        room = room,
        sourceType = SourceType.QNU_EDU,
    )

    @Test
    fun `adjacent periods merge into one meeting`() {
        val merged = MeetingMerger.mergeConsecutive(listOf(meeting(start = 1), meeting(start = 2)))
        assertEquals(1, merged.size)
        assertEquals(1, merged.single().periodStart)
        assertEquals(2, merged.single().periodEnd)
        assertEquals("c1:d1:p1-2", merged.single().id)
    }

    @Test
    fun `three consecutive periods merge`() {
        val merged = MeetingMerger.mergeConsecutive(
            listOf(meeting(start = 7), meeting(start = 8), meeting(start = 9)),
        )
        assertEquals(1, merged.size)
        assertEquals(7..9, merged.single().periodStart..merged.single().periodEnd)
    }

    @Test
    fun `gap prevents merge`() {
        val merged = MeetingMerger.mergeConsecutive(listOf(meeting(start = 1), meeting(start = 3)))
        assertEquals(2, merged.size)
    }

    @Test
    fun `different room prevents merge`() {
        val merged = MeetingMerger.mergeConsecutive(
            listOf(meeting(start = 1, room = "教室A"), meeting(start = 2, room = "教室B")),
        )
        assertEquals(2, merged.size)
    }

    @Test
    fun `different weeks prevent merge`() {
        val merged = MeetingMerger.mergeConsecutive(
            listOf(meeting(start = 1, weeks = listOf(1, 3, 5)), meeting(start = 2, weeks = listOf(2, 4, 6))),
        )
        assertEquals(2, merged.size)
    }

    @Test
    fun `different weekday prevents merge`() {
        val merged = MeetingMerger.mergeConsecutive(
            listOf(meeting(weekday = 1, start = 1), meeting(weekday = 2, start = 2)),
        )
        assertEquals(2, merged.size)
    }
}
