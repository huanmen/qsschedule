package cn.qztc.timetable.feature.timetable

internal data class CourseMenuState(
    val revealedId: String? = null,
) {
    fun reveal(courseId: String): CourseMenuState = copy(revealedId = courseId)
    fun dismiss(): CourseMenuState = copy(revealedId = null)
}
