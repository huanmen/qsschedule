package cn.qztc.timetable

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cn.qztc.timetable.adskip.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File

class AdSkipAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<FeatureAcceptanceActivity>()
    private fun isolatedContext(): Context = object : ContextWrapper(compose.activity) {
        private val name = "adskip-test-${UUID.randomUUID()}"
        override fun getSharedPreferences(ignored: String?, mode: Int): SharedPreferences = super.getSharedPreferences(name, mode)
    }
    @Test fun installDeleteAndReinstallAreAtomicAndDefaultOff() {
        val context = isolatedContext()
        try {
            assertFalse(AdSkipStore.enabled(context))
            assertFalse(AdSkipStore.appEnabled(context, "unlisted.app"))
            AdSkipStore.installPack(context, AdSkipRules.bundled.copy(name = "Synthetic local pack"))
            assertTrue(AdSkipStore.installed(context))
            assertFalse(AdSkipStore.enabled(context))
            AdSkipStore.rules(context).forEach { assertFalse(AdSkipStore.ruleEnabled(context, it)) }
            val rule = AdSkipStore.rules(context).first()
            AdSkipStore.setEnabled(context, true)
            AdSkipStore.setAppEnabled(context, rule.packageName, true)
            AdSkipStore.setRuleEnabled(context, rule, true)
            assertEquals(listOf(rule.packageName), AdSkipStore.activePackages(context))
            val original = AdSkipStore.localPack(context)
            assertThrows(IllegalArgumentException::class.java) {
                AdSkipStore.installPack(context, AdSkipRules.bundled.copy(schemaVersion = 99))
            }
            assertEquals(original, AdSkipStore.localPack(context))
            AdSkipStore.removeDownloadedPlugin(context)
            assertNull(AdSkipStore.localPack(context))
            assertFalse(AdSkipStore.installed(context))
            assertFalse(AdSkipStore.enabled(context))
            assertTrue(AdSkipStore.rules(context).isEmpty())
            assertTrue(AdSkipStore.activePackages(context).isEmpty())
            AdSkipStore.record(context, rule, AdSkipResult.UNCONFIRMED)
            assertTrue(AdSkipStore.records(context).isEmpty())
            AdSkipStore.installPack(context, AdSkipRules.bundled)
            assertFalse(AdSkipStore.enabled(context))
            AdSkipStore.rules(context).forEach { assertFalse(AdSkipStore.ruleEnabled(context, it)) }
        } finally { AdSkipStore.prefs(context).edit().clear().commit() }
    }
    @Test fun diagnosticsAreBoundedAndContainOnlyApprovedMetadata() {
        val context = isolatedContext()
        try {
            AdSkipStore.installPack(context, AdSkipRules.bundled)
            repeat(75) { AdSkipStore.record(context, AdSkipRules.bundled.rules.first(), AdSkipResult.CLICK_ACCEPTED) }
            assertEquals(60, AdSkipStore.records(context).size)
            assertEquals("易班", AdSkipStore.records(context).first().app)
            assertEquals(AdSkipResult.CLICK_ACCEPTED.label, AdSkipStore.records(context).first().result)
            AdSkipStore.clearRecords(context)
            assertTrue(AdSkipStore.records(context).isEmpty())
        } finally { AdSkipStore.prefs(context).edit().clear().commit() }
    }
    @Test fun screenStartsOffExplainsServiceAndCancelDoesNotEnable() {
        val context = isolatedContext()
        try {
            AdSkipStore.restoreBundled(context)
            compose.setContent { CompositionLocalProvider(LocalContext provides context) { MaterialTheme { AdSkipScreen {} } } }
            compose.onNodeWithText("服务未开启").assertIsDisplayed()
            compose.onNodeWithText("应用与规则").assertDoesNotExist()
            compose.onNodeWithText("删除").assertDoesNotExist()
            compose.onNodeWithText("开启服务").performClick()
            compose.onNodeWithText("取消").performClick()
            assertFalse(AdSkipStore.enabled(context))
            compose.onNodeWithText("运行日志").performScrollTo().performClick()
            compose.onNodeWithText("暂无日志").assertIsDisplayed()
            compose.onNodeWithContentDescription("返回").performClick()
            compose.onNodeWithText("内置规则").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("内置规则").performScrollTo().performClick()
            compose.runOnIdle { assertFalse(AdSkipStore.genericEnabled(context)) }
        } finally { AdSkipStore.prefs(context).edit().clear().commit() }
    }

    @Test fun additionsPreserveBuiltinsAndChoicesAndLogOnlyFinalResults() {
        val context = isolatedContext()
        try {
            AdSkipStore.restoreBundled(context)
            AdSkipStore.setEnabled(context, true)
            val builtin = AdSkipRules.bundled.rules.first()
            AdSkipStore.setRuleEnabled(context, builtin, false)
            val added = builtin.copy(id = "custom-example", packageName = "example.shopping", appLabel = "示例应用")
            AdSkipStore.addRules(context, AdSkipRules.bundled.copy(rules = listOf(added)))
            assertTrue(AdSkipStore.enabled(context))
            assertTrue(AdSkipStore.genericEnabled(context))
            assertFalse(AdSkipStore.ruleEnabled(context, builtin))
            assertFalse(AdSkipStore.ruleEnabled(context, added))
            assertTrue(AdSkipStore.rules(context).containsAll(AdSkipRules.bundled.rules))
            AdSkipStore.setRuleEnabled(context, added, true)
            AdSkipStore.addRules(context, AdSkipRules.bundled.copy(rules = listOf(added)))
            assertTrue(AdSkipStore.ruleEnabled(context, added))
            AdSkipStore.recordOutcome(context, added, AdSkipResult.CLICK_ACCEPTED)
            assertTrue(AdSkipStore.outcomes(context).isEmpty())
            AdSkipStore.recordOutcome(context, added, AdSkipResult.UNCONFIRMED)
            AdSkipStore.recordOutcome(context, added, AdSkipResult.TARGET_GONE)
            assertEquals(listOf("成功", "失败"), AdSkipStore.outcomes(context).map { it.result })
            AdSkipStore.removeRule(context, added)
            assertEquals(AdSkipRules.bundled.rules, AdSkipStore.rules(context))
        } finally { AdSkipStore.prefs(context).edit().clear().commit() }
    }

    @Test fun pluginInstallAndDeleteBelongToMarket() {
        val context = isolatedContext()
        try {
            AdSkipStore.restoreBundled(context)
            compose.setContent { CompositionLocalProvider(LocalContext provides context) { MaterialTheme { PluginMarketScreen({}, {}) } } }
            compose.onNodeWithText("打开").assertDoesNotExist()
            compose.onNodeWithText("已安装").assertDoesNotExist()
            compose.onNodeWithText("删除").performClick()
            compose.onNodeWithText("删除广告辅助？").assertIsDisplayed()
            compose.onAllNodesWithText("删除").onLast().performClick()
            compose.runOnIdle { assertFalse(AdSkipStore.installed(context)) }
            compose.onAllNodesWithText("下载").onFirst().assertIsDisplayed()
        } finally { AdSkipStore.prefs(context).edit().clear().commit() }
    }
}
