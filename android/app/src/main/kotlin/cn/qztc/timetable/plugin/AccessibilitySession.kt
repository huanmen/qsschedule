package cn.qztc.timetable.plugin

/** Only one plugin may inspect and act on accessibility windows at a time. */
object AccessibilitySession {
    @Volatile private var owner: String? = null
    @Volatile var onChanged: (() -> Unit)? = null

    @Synchronized fun claim(pluginId: String): Boolean {
        if (owner != null && owner != pluginId) return false
        owner = pluginId
        onChanged?.invoke()
        return true
    }

    @Synchronized fun release(pluginId: String) {
        if (owner != pluginId) return
        owner = null
        onChanged?.invoke()
    }

    fun availableTo(pluginId: String): Boolean = owner == null || owner == pluginId
    fun occupiedBy(pluginId: String): Boolean = owner == pluginId
}
