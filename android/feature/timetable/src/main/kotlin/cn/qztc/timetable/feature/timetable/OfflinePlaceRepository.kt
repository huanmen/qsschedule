package cn.qztc.timetable.feature.timetable

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.annotation.Keep
import cn.qztc.timetable.core.domain.model.ScheduleLocation
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

data class OfflinePlace(val id: String, val name: String, val aliases: List<String>, val tags: List<String>, val latitude: Double, val longitude: Double) {
    fun binding(label: String = name) = ScheduleLocation(label, id, latitude, longitude)
}

/** The raw edit document also preserves roads and regions that the mobile UI does not expose. */
class OfflinePlaceRepository(context: Context) {
    private val context = context.applicationContext
    private val file = AtomicFile(File(this.context.filesDir, "offline-campus-edits.json"))
    private val backup = File(this.context.filesDir, "offline-campus-edits-before-migration.json")
    private val updatePrefs = this.context.getSharedPreferences("offline-map-updates", Context.MODE_PRIVATE)

    private fun applyBundledUpdate() = synchronized(lock) {
        val version="20260918-user-export-4"
        if(!file.baseFile.isFile || updatePrefs.getBoolean(version,false))return@synchronized
        val previous=file.openRead().bufferedReader().use { it.readText() }
        val current=JSONObject(previous)
        val bundle=JSONObject(context.assets.open("offline-campus/data/initial-edits.json").bufferedReader().use { it.readText() })
        val previousBundle=JSONObject(context.assets.open("offline-campus/data/previous-initial-edits.json").bufferedReader().use { it.readText() })
        fun merge(key:String) {
            val existing=current.optJSONArray(key)?:JSONArray()
            val known=(0 until existing.length()).associateBy { existing.getJSONObject(it).getString("id") }
            val old=previousBundle.optJSONArray(key)?:JSONArray()
            val oldById=(0 until old.length()).associate { old.getJSONObject(it).getString("id") to old.getJSONObject(it) }
            val incoming=bundle.optJSONArray(key)?:JSONArray()
            val deleted=strings(current.optJSONArray("deleted")).toSet()
            for(i in 0 until incoming.length()) {
                val record=incoming.getJSONObject(i);val id=record.getString("id")
                if(key=="places" && id in deleted)continue
                val index=known[id]
                if(index==null)existing.put(record)
                else if(oldById[id]?.toString()==existing.getJSONObject(index).toString()) existing.put(index,record)
                else if(key=="places") {
                    val local=existing.getJSONObject(index)
                    val localTags=strings(local.optJSONArray("tags")).filterNot { it=="默认展示" }
                    val authorDefault=strings(record.optJSONArray("tags")).filter { it=="默认展示" }
                    local.put("tags",JSONArray((localTags+authorDefault).distinct()))
                }
            }
            current.put(key,existing)
        }
        merge("places");merge("roads");merge("regions")
        val tags=strings(current.optJSONArray("tagCatalog"))+strings(bundle.optJSONArray("tagCatalog"))
        current.put("tagCatalog",JSONArray(tags.distinct()))
        val archive=File(context.filesDir,"offline-campus-before-$version.json")
        if(!archive.exists())archive.writeText(previous)
        saveEdits(current.toString())
        updatePrefs.edit().putBoolean(version,true).apply()
    }

    fun isMigrated(): Boolean = file.baseFile.isFile

    @Synchronized fun readEdits(): String = synchronized(lock) {
        applyBundledUpdate()
        if (file.baseFile.isFile) file.openRead().bufferedReader().use { it.readText() }
        else context.assets.open("offline-campus/data/initial-edits.json").bufferedReader().use { it.readText() }
    }

    fun saveEdits(text: String) = synchronized(lock) {
        validate(text)
        val output = file.startWrite()
        try { output.write(text.toByteArray(Charsets.UTF_8)); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
        revision++
    }

    fun migrate(text: String?) = synchronized(lock) {
        if (isMigrated()) return@synchronized
        val selected = text?.takeIf { it.isNotBlank() && it != "null" } ?: readEdits()
        validate(selected)
        if (!backup.isFile) backup.writeText(selected)
        saveEdits(selected)
    }

    fun places(): List<OfflinePlace> = synchronized(lock) {
        if (cachedRevision == revision && cachedPath == file.baseFile.path) return@synchronized cached
        val catalogue = JSONObject(context.assets.open("offline-campus/data/place-catalog.json").bufferedReader().use { it.readText() })
        val records = linkedMapOf<String, JSONObject>()
        fun merge(array: JSONArray) { for (i in 0 until array.length()) { val item = array.getJSONObject(i); records[item.getString("id")] = item } }
        merge(catalogue.getJSONArray("places")); merge(catalogue.getJSONArray("pendingPlaces"))
        val edits = JSONObject(readEdits())
        merge(edits.getJSONArray("places"))
        for (id in strings(edits.optJSONArray("deleted"))) records.remove(id)
        cached = records.values.filter { it.optString("status") != "unverified" }.mapNotNull { p ->
            val c = p.optJSONObject("coordinate") ?: return@mapNotNull null
            OfflinePlace(p.getString("id"), p.getString("name"), strings(p.optJSONArray("aliases")), strings(p.optJSONArray("tags")).filterNot { it=="默认展示" }, c.getDouble("lat"), c.getDouble("lng"))
        }
        cachedRevision = revision; cachedPath = file.baseFile.path
        cached
    }

    fun search(query: String): List<OfflinePlace> {
        val key = query.trim().lowercase().replace(" ", "")
        if (key.isEmpty()) return places().take(12)
        return places().filter { p -> (listOf(p.name) + p.aliases).any { it.lowercase().replace(" ", "").contains(key) } }
            .sortedBy { if (it.name == query.trim()) 0 else 1 }.take(12)
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun migrateWebStorage(activityContext: Context): Boolean = withContext(Dispatchers.Main) {
        if (isMigrated()) return@withContext true
        suspendCancellableCoroutine { continuation ->
            val web = WebView(activityContext)
            val main = Handler(Looper.getMainLooper())
            var finished = false
            fun finish(ok: Boolean) {
                if (finished) return
                finished = true
                web.removeJavascriptInterface("Migration"); web.destroy()
                if (continuation.isActive) continuation.resume(ok)
            }
            web.settings.javaScriptEnabled = true
            web.settings.domStorageEnabled = true
            web.settings.allowFileAccess = false
            web.settings.allowContentAccess = false
            web.settings.blockNetworkLoads = true
            web.addJavascriptInterface(MigrationBridge { text ->
                val ok = runCatching { migrate(text) }.isSuccess
                main.post { finish(ok) }
            }, "Migration")
            web.loadDataWithBaseURL(OfflineMapPolicy.ROOT, "<script>Migration.accept(localStorage.getItem('qnu-offline-map-edits-v1'))</script>", "text/html", "UTF-8", null)
            main.postDelayed({ finish(false) }, 5_000)
            continuation.invokeOnCancellation { main.post { finish(false) } }
        }
    }

    companion object {
        private val lock = Any()
        private var revision = 0L
        private var cachedRevision = -1L
        private var cachedPath = ""
        private var cached = emptyList<OfflinePlace>()
        private fun strings(array: JSONArray?): List<String> = if (array == null) emptyList() else (0 until array.length()).map { array.getString(it) }

        fun validate(text: String) {
            require(text.length <= 5 * 1024 * 1024) { "地图编辑文件超过 5 MB" }
            val root = JSONObject(text)
            require(root.getString("format") == "qnu-campus-place-edits" && root.getInt("version") == 1) { "地图编辑格式无效" }
            val places = root.getJSONArray("places")
            require(places.length() + root.getJSONArray("deleted").length() <= 10_000)
            val ids = mutableSetOf<String>()
            for (i in 0 until places.length()) {
                val p = places.getJSONObject(i)
                require(ids.add(p.getString("id")))
                require(p.getString("name").isNotBlank() && p.getString("name").length <= 80)
                val c = p.getJSONObject("coordinate")
                require(c.getDouble("lat") in 24.860..24.878 && c.getDouble("lng") in 118.659..118.682)
            }
        }
    }
}

@Keep
private class MigrationBridge(private val save: (String?) -> Unit) {
    @JavascriptInterface fun accept(text: String?) = save(text)
}
