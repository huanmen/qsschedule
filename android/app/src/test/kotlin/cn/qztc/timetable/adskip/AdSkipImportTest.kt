package cn.qztc.timetable.adskip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdSkipImportTest {
    @Test
    fun importsJson5NativeIdSubsetAndKeepsActivityScope() {
        val preview = AdSkipImport.parse(
            """
            {
              // JSON5 comments and trailing commas are accepted at the import boundary.
              name: 'Synthetic subscription',
              version: 7,
              apps: [{
                id: 'org.example.reader',
                name: '示例阅读',
                groups: [{
                  name: '开屏广告',
                  activityIds: '.SplashActivity',
                  rules: [{
                    matches: '[vid="skip_button"][text^="跳过"][clickable=true]',
                  }],
                }],
              }],
            }
            """.trimIndent(),
        )

        assertEquals(1, preview.appCount)
        assertEquals(emptyList<AdSkipImportIssue>(), preview.issues)
        val pack = preview.pack ?: error("expected one compatible rule")
        val rule = pack.rules.single()
        assertEquals("org.example.reader.SplashActivity", rule.activityNames.single())
        assertEquals("skip_button", rule.targetId)
        assertEquals("跳过", rule.textPrefix)
    }

    @Test
    fun rejectsUnsupportedSelectorWithoutDroppingItSilently() {
        val preview = AdSkipImport.parse(
            """
            {
              name: 'Unsupported subscription',
              version: 8,
              apps: [{
                id: 'org.example.reader',
                name: '示例阅读',
                groups: [{
                  name: '开屏广告',
                  activityIds: '.SplashActivity',
                  rules: [{ matches: '[text="立即购买"]' }],
                }],
              }],
            }
            """.trimIndent(),
        )

        assertNull(preview.pack)
        assertEquals(1, preview.issues.size)
        assertEquals("示例阅读", preview.issues.single().app)
    }
}
