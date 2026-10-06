package cn.qztc.timetable.core.domain.calendar

/**
 * 从系统日历事件标题识别法定放假或调休上班，忽略生日等私人事项。
 */
object PublicHolidayEventParser {
    private val makeup = Regex("补班|上班|工作日|调班|补课|work\\s*day|working\\s*day", RegexOption.IGNORE_CASE)
    private val dayOff = Regex("放假|休假|休息|不上班|不补班|[(（\\[]休[)）\\]]")
    private val workMark = Regex("[(（\\[]班[)）\\]]|^(班|补班|上班|补课)$")
    private val teachingWeekday = Regex("(?:补|上)(?:本周|当周)?(?:周|星期|礼拜)([一二三四五六日天1-7])(?:的)?课")
    private val holiday = Regex("元旦|春节|清明|劳动节|端午|中秋|国庆|放假|假期|休假|休息")
    private val holidayCalendar = Regex("节假日|假期|holiday|holidays|中国节假日|法定", RegexOption.IGNORE_CASE)
    private val personal = Regex("生日|纪念日|会议|婚礼|birthday|anniversary|meeting", RegexOption.IGNORE_CASE)

    private val genericHolidayTitle = Regex("^(休|假|放假|假期|休息|holiday|holidays)$", RegexOption.IGNORE_CASE)

    fun classify(title: String, calendarName: String = ""): AcademicDayKind? {
        val eventTitle = title.trim()
        if (eventTitle.isEmpty() || personal.containsMatchIn(eventTitle)) return null
        if (workMark.containsMatchIn(eventTitle)) return AcademicDayKind.MAKEUP
        if (dayOff.containsMatchIn(eventTitle)) return AcademicDayKind.HOLIDAY
        if (makeup.containsMatchIn(eventTitle)) return AcademicDayKind.MAKEUP
        if (eventTitle.contains("调休")) return AcademicDayKind.MAKEUP
        if (holiday.containsMatchIn(eventTitle)) return AcademicDayKind.HOLIDAY
        if (holidayCalendar.containsMatchIn(calendarName) && genericHolidayTitle.containsMatchIn(eventTitle)) {
            return AcademicDayKind.HOLIDAY
        }
        return null
    }

    fun displayName(title: String, kind: AcademicDayKind): String {
        val trimmed = title.trim().ifBlank {
            if (kind == AcademicDayKind.HOLIDAY) "放假" else "调休"
        }
        return trimmed.take(20)
    }

    fun sourceWeekday(title: String): Int? {
        val value = teachingWeekday.find(title)?.groupValues?.get(1) ?: return null
        return value.toIntOrNull() ?: when (value) {
            "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4; "五" -> 5; "六" -> 6; "日", "天" -> 7
            else -> null
        }
    }
}
