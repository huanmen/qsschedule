package cn.qztc.timetable.announcement

import org.junit.Assert.*
import org.junit.Test

class AnnouncementParagraphTest {
    @Test fun authoredSpacesMeanTwoCharacterIndentNotTwoLatinSpaces() {
        assertEquals(AnnouncementParagraph("这是正文", true), announcementParagraph("  这是正文"))
        assertEquals(AnnouncementParagraph("• 插件", true), announcementParagraph("  • 插件"))
        assertEquals(AnnouncementParagraph("感谢使用", false), announcementParagraph("感谢使用"))
        assertEquals(AnnouncementParagraph("——某23届泉师人", false), announcementParagraph("——某23届泉师人"))
    }
}
