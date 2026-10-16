package cn.qztc.timetable.plugin

import org.junit.Assert.*
import org.junit.Test

class PluginHostServicesTest {
    @Test fun installedDeclaredAndGrantedAreAllRequired() {
        val read = PluginCapability.READ_CALENDAR
        val all = setOf(read)
        requirePluginCapability(true, all, all, read)
        listOf(Triple(false, all, all), Triple(true, emptySet(), all), Triple(true, all, emptySet())).forEach { (installed, declared, granted) ->
            assertThrows(SecurityException::class.java) { requirePluginCapability(installed, declared, granted, read) }
        }
    }

    @Test fun activityWritesCannotCrossPluginOrHostNamespace() {
        requirePluginActivityOwnership("org.demo", "plugin:org.demo:item")
        listOf("plugin:org.demo2:item", "academic:s:event", "course:item", "plugin:org.demo:").forEach { id ->
            assertThrows(IllegalArgumentException::class.java) { requirePluginActivityOwnership("org.demo", id) }
        }
    }
}
