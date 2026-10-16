package cn.qztc.timetable

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import cn.qztc.timetable.adskip.AdSkipImport
import cn.qztc.timetable.adskip.AdSkipStore
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prepares the separate emulator-only fixture for a real AccessibilityService click check. */
class AdSkipLiveFixtureSetupTest {
    private fun requireEmulator() {
        assertTrue("此测试只允许在模拟器运行", Build.FINGERPRINT.contains("generic", true) || Build.PRODUCT.contains("sdk", true))
    }

    @Test fun enableFixtureRule() {
        requireEmulator()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pkg = "org.example.adskipfixture"
        val source = """[{"${pkg.hashCode()}":"{\"popup_rules\":[{\"id\":\"ad_container\",\"action\":\"close_btn\"}]}"}]"""
        val pack = AdSkipImport.parse(source, mapOf(pkg to "广告规则测试")).pack!!
        val prefs = AdSkipStore.prefs(context)
        prefs.edit().putBoolean("fixture:was_installed", AdSkipStore.installed(context))
            .putBoolean("fixture:was_enabled", AdSkipStore.enabled(context)).commit()
        AdSkipStore.addRules(context, pack, pluginVersion = if (AdSkipStore.installed(context)) null else 2)
        AdSkipStore.setRuleEnabled(context, pack.rules.single(), true)
        AdSkipStore.setAppEnabled(context, pkg, true)
        AdSkipStore.setEnabled(context, true)
        assertTrue(AdSkipStore.activePackages(context).contains(pkg))
    }

    @Test fun disableFixtureRule() {
        requireEmulator()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = AdSkipStore.prefs(context)
        val wasInstalled = prefs.getBoolean("fixture:was_installed", false)
        val wasEnabled = prefs.getBoolean("fixture:was_enabled", false)
        AdSkipStore.setEnabled(context, false)
        AdSkipStore.localPack(context)?.rules?.filter { it.packageName == "org.example.adskipfixture" }
            ?.forEach { AdSkipStore.removeRule(context, it) }
        if (!wasInstalled) AdSkipStore.removeDownloadedPlugin(context)
        else AdSkipStore.setEnabled(context, wasEnabled)
        prefs.edit().remove("app:org.example.adskipfixture")
            .remove("fixture:was_installed").remove("fixture:was_enabled").commit()
    }
}
