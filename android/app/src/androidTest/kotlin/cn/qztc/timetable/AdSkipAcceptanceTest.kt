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
            compose.setContent { CompositionLocalProvider(LocalContext provides context) { MaterialTheme { AdSkipScreen {} } } }
            compose.onNodeWithText("广告辅助未安装").assertIsDisplayed()
            compose.onNodeWithText("安装基础包").performScrollTo().performClick()
            compose.onNodeWithText("安装并保持关闭").performClick()
            compose.onNodeWithText("广告辅助已暂停").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("无需 GKD、Shizuku 或 Root").performScrollTo().assertIsDisplayed()
            val screenshot = File(context.getExternalFilesDir(null), "adskip-qa.png")
            screenshot.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            compose.onAllNodes(isToggleable()).onFirst().performScrollTo().performClick()
            compose.onNodeWithText("开启广告辅助？").assertIsDisplayed()
            compose.onNodeWithText("取消").performClick()
            assertFalse(AdSkipStore.enabled(context))
            compose.onNodeWithText("菜鸟").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("启动广告仍待适配。").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("删除已安装插件").performScrollTo().performClick()
            compose.onNodeWithText("删除").performClick()
            compose.onNodeWithText("广告辅助未安装").performScrollTo().assertIsDisplayed()
            assertFalse(AdSkipStore.installed(context))
            assertTrue(AdSkipStore.rules(context).isEmpty())
        } finally { AdSkipStore.prefs(context).edit().clear().commit() }
    }
}
