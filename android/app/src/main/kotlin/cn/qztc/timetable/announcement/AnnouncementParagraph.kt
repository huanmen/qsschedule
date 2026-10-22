package cn.qztc.timetable.announcement

internal data class AnnouncementParagraph(val text: String, val indent: Boolean)

/** Leading spaces in authored notices mean a two-Chinese-character first-line indent. */
internal fun announcementParagraph(line: String): AnnouncementParagraph {
    val text = line.trimStart()
    return AnnouncementParagraph(text, line.startsWith("  ") || line.startsWith("　"))
}
