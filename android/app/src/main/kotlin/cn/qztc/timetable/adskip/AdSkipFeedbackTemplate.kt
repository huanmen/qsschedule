package cn.qztc.timetable.adskip

internal object AdSkipFeedbackTemplate {
    const val DEFAULT = "{图标} {应用名}：跳过成功"
    const val ICON = "{图标}"
    const val APP = "{应用名}"
    data class Part(val text: String = "", val icon: Boolean = false)
    private val tags = Regex("\\{图标\\}|\\{应用名\\}")
    fun parts(template: String, app: String): List<Part> {
        val result = mutableListOf<Part>()
        var start = 0
        for (match in tags.findAll(template)) {
            if (match.range.first > start) result += Part(template.substring(start, match.range.first))
            result += if (match.value == ICON) Part(icon = true) else Part(app)
            start = match.range.last + 1
        }
        if (start < template.length) result += Part(template.substring(start))
        return result
    }
    fun plain(template: String, app: String) = parts(template, app).joinToString("") { it.text }.trim()
}
