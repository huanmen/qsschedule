package cn.qztc.timetable.adskip

import org.junit.Assert.assertEquals
import org.junit.Test

class AdSkipFeedbackTemplateTest {
    @Test fun defaultTemplatePlacesAppNameAfterIconAndColon() {
        val template = AdSkipFeedbackTemplate.DEFAULT
        assertEquals("掌上公交：跳过成功", AdSkipFeedbackTemplate.plain(template, "掌上公交"))
        assertEquals(true, AdSkipFeedbackTemplate.parts(template, "掌上公交").first().icon)
    }
    @Test fun iconAndAppTagsCanAppearAnywhereAndRepeat() {
        val parts = AdSkipFeedbackTemplate.parts("已跳过 {应用名} {图标} · {应用名}{图标}", "菜鸟")
        assertEquals("已跳过 菜鸟  · 菜鸟", AdSkipFeedbackTemplate.plain("已跳过 {应用名} {图标} · {应用名}{图标}", "菜鸟"))
        assertEquals(2, parts.count { it.icon })
        assertEquals(true, parts.last().icon)
        assertEquals("跳过成功", AdSkipFeedbackTemplate.plain("跳过成功", "菜鸟"))
    }
}
