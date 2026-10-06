package cn.qztc.timetable.feature.timetable

import cn.qztc.timetable.core.domain.model.*
import java.time.LocalDate

internal data class DemoCourse(val course: Course, val meetings: List<CourseMeeting>, val paletteIndex: Int)

internal object SyntheticTimetableFixture {
    const val semesterId = "2026-2027-1"
    val semesterStart: LocalDate = LocalDate.of(2026, 8, 17)
    val periods = QnuPeriodDefaults.periods()

    fun periodFor(periodNo: Int): Period = periods.first { it.no == periodNo }

    private fun weeks(from: Int, to: Int, step: Int = 1) = (from..to step step).toList()

    private data class Slot(
        val day: Int,
        val start: Int,
        val end: Int,
        val weeks: List<Int>,
        val weekText: String,
    )

    private fun course(
        id: String,
        name: String,
        teacher: String,
        room: String,
        palette: Int,
        vararg slots: Slot,
    ): DemoCourse {
        val domainCourse = Course(
            id = id,
            semesterId = semesterId,
            sourceType = SourceType.QNU_EDU,
            sourceKey = id,
            name = name,
            color = palette,
        )
        return DemoCourse(
            course = domainCourse,
            paletteIndex = palette,
            meetings = slots.mapIndexed { index, slot ->
                CourseMeeting(
                    id = "$id-$index",
                    courseId = id,
                    weekday = slot.day,
                    periodStart = slot.start,
                    periodEnd = slot.end,
                    weekRule = WeekRule.of(slot.weeks, slot.weekText),
                    teacher = teacher,
                    room = room,
                    sourceType = SourceType.QNU_EDU,
                )
            },
        )
    }

    val courses: List<DemoCourse> = listOf(
        course("hm", "鸿蒙应用开发", "廖作斌", "朝阳409", 0,
            Slot(3, 3, 4, weeks(1, 16), "1-16周"), Slot(5, 1, 2, weeks(1, 8), "1-8周")),
        course("os", "操作系统", "王敏", "慈山201", 1,
            Slot(1, 1, 2, weeks(1, 16), "1-16周"), Slot(5, 3, 4, weeks(1, 16), "1-16周")),
        course("pm", "软件项目管理", "林志强", "朝阳302", 2,
            Slot(5, 5, 6, weeks(1, 12), "1-12周")),
        course("career", "大学生就业指导", "陈静", "学术报告厅", 3,
            Slot(5, 9, 10, weeks(1, 8), "1-8周")),
        course("ds", "数据结构", "黄立", "慈山305", 4,
            Slot(1, 3, 4, weeks(1, 16), "1-16周"), Slot(4, 1, 2, weeks(1, 16), "1-16周")),
        course("en", "大学英语（三）", "李婷", "朝阳201", 5,
            Slot(2, 1, 2, weeks(1, 16), "1-16周"), Slot(4, 3, 4, weeks(1, 16), "1-16周")),
        course("pol", "形势与政策", "张伟", "慈山102", 6,
            Slot(3, 5, 6, weeks(2, 10, 2), "2-10周（双）")),
        course("pe", "体育（羽毛球）", "吴强", "风雨操场", 7,
            Slot(2, 5, 6, weeks(1, 16), "1-16周")),
        course("hmlab", "鸿蒙应用开发实验", "廖作斌", "慈山机房3", 0,
            Slot(3, 7, 8, weeks(2, 14), "2-14周")),
        // 只在第 3 周周六出现，用于验收“有周末课时恢复七列”的布局。
        course("weekend-demo", "周末实践课（示例）", "陈老师", "实验楼A201", 4,
            Slot(6, 3, 4, weeks(3, 3), "第3周")),
    )

    val changes = listOf(
        DemoChange("ch1", "调课", "pm", "周五 5-6节 · 朝阳302 · 第2-12周", "周四 5-6节 · 朝阳305 · 第2-12周", "来源：教务系统 · 8月28日 09:41 同步"),
        DemoChange("ch2", "停课", "career", "第2周 周五 9-10节 · 学术报告厅", "本周停课（其余周次不变）", "来源：教务系统 · 8月28日 09:41 同步"),
    )

    fun dateFor(week: Int, weekday: Int): LocalDate =
        semesterStart.plusDays(((week - 1) * 7L) + weekday - 1L)

    fun occurrences(
        week: Int,
        appliedChanges: Set<String> = emptySet(),
        includeMovedGhost: Boolean = false,
    ): List<DemoOccurrence> {
        val output = mutableListOf<DemoOccurrence>()
        courses.forEach { item ->
            item.meetings.filter { week in it.weekRule }.forEach { meeting ->
                var weekday = meeting.weekday
                var room = meeting.room.orEmpty()
                var moved = false
                var cancelled = false
                if (item.course.id == "pm" && "ch1" in appliedChanges && week >= 2) {
                    weekday = 4
                    room = "朝阳305"
                    moved = true
                }
                if (item.course.id == "career" && "ch2" in appliedChanges && week == 2) cancelled = true
                output += DemoOccurrence(
                    course = item.course,
                    paletteIndex = item.paletteIndex,
                    weekday = weekday,
                    periodStart = meeting.periodStart,
                    periodEnd = meeting.periodEnd,
                    room = room,
                    teacher = meeting.teacher.orEmpty(),
                    cancelled = cancelled,
                    moved = moved,
                )
                if (includeMovedGhost && moved) {
                    output += DemoOccurrence(
                        course = item.course,
                        paletteIndex = item.paletteIndex,
                        weekday = 5,
                        periodStart = meeting.periodStart,
                        periodEnd = meeting.periodEnd,
                        room = meeting.room.orEmpty(),
                        teacher = meeting.teacher.orEmpty(),
                        ghost = true,
                    )
                }
            }
        }
        return output.sortedWith(compareBy(DemoOccurrence::weekday, DemoOccurrence::periodStart))
    }
}
