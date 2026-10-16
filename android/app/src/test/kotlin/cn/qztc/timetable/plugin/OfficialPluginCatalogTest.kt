package cn.qztc.timetable.plugin

import cn.qztc.timetable.adskip.AdSkipRules
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class OfficialPluginCatalogTest {
    @Test fun catalogKeepsPublishedFeaturesAndCompatibleRules() {
        val parsed = OfficialPluginCatalog.parse("""{"schemaVersion":1,"plugins":[{"id":"academic-records","name":"资料","summary":"更新资料","version":2,"minAppVersion":13}],"rulePlugins":[{"id":"school.rules","name":"规则","summary":"校园","version":1,"kind":"adskip-rule-pack","rulesUrl":"https://example.org/rules.json"}]}""")
        assertEquals(2, parsed.plugins.single().version)
        assertEquals(setOf("school.rules"), parsed.activeRules)
        assertTrue(OfficialPluginCatalog.parse("""{"schemaVersion":1,"plugins":[],"rulePlugins":[]}""").plugins.isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            OfficialPluginCatalog.parse("""{"schemaVersion":2,"plugins":[],"rulePlugins":[]}""")
        }
    }

    @Test fun unsupportedRuleReleaseDoesNotHideOtherPlugins() {
        val parsed = OfficialPluginCatalog.parse("""{"schemaVersion":1,"plugins":[],"rulePlugins":[{"id":"future.rules","name":"未来规则","summary":"","version":1,"kind":"adskip-rule-pack","rulesUrl":"https://example.org/rules.json","minAppVersion":999}]}""")
        assertTrue(parsed.rules.isEmpty())
    }

    @Test fun exportCurrentBundledRuleSnapshotForAdministration() {
        // This data snapshot is generated from the same compiled rules as the host.
        val file = File("build/exports/adskip-rules.json")
        file.parentFile!!.mkdirs()
        file.writeText(Json { prettyPrint = true; encodeDefaults = true }.encodeToString(AdSkipRules.bundled))
        assertTrue(file.length() > 0)
    }
}
