package cn.qztc.timetable.core.domain.schedule

import cn.qztc.timetable.core.domain.model.Course

/** Only call for names already matched to the same school course code/sequence. */
object ScheduleCourseNames {
    fun fullest(vararg names: String?): String? = names.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        .maxWithOrNull(compareBy<String>({ !it.contains('…') && !it.contains("...") }, { it.length }))

    /** Legacy imports used a name in the ID, while notice-only imports use the school code. */
    fun findCourse(courseId: String, courses: List<Course>): Course? {
        courses.firstOrNull { it.id == courseId || it.sourceKey == courseId }?.let { return it }
        val key = courseId.removePrefix("qnu:").substringAfterLast(':')
        fun sequence(value: String?) = value?.toIntOrNull()?.toString() ?: value?.trim()
        val code = key.substringBeforeLast('#')
        val section = key.substringAfterLast('#', "-").takeUnless { it == "-" }
        return courses.filter { it.id.substringAfterLast(':') == key ||
            (it.code == code && sequence(it.sequence) == sequence(section)) }.singleOrNull()
    }
}
