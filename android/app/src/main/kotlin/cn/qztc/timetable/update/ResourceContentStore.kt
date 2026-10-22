package cn.qztc.timetable.update

import android.content.Context
import android.util.AtomicFile
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object ResourceContentStore {
    // This exact version is already shipped in assets/content/elective-advice.txt.
    const val BUNDLED_VERSION = 1
    private val changed = MutableStateFlow(0)
    val revision = changed.asStateFlow()
    fun directory(context: Context) = File(context.filesDir, "resource-updates")
    private fun installed(context: Context) = AtomicFile(File(directory(context), "installed.json"))
    fun content(context: Context): ResourceUpdateClient.Content? = runCatching {
        val file = installed(context)
        file.openRead().use { ResourceUpdateClient.parseContent(it.readBytes().toString(Charsets.UTF_8)) }
    }.getOrNull()
    fun apply(context: Context, m: ResourceUpdateClient.Manifest) {
        val part = ResourceUpdateClient.partial(directory(context), m)
        val validated = ResourceUpdateClient.parseContent(part.readText(Charsets.UTF_8))
        require(validated.version == m.version)
        val file = installed(context)
        val out = file.startWrite()
        try { out.write(part.readBytes()); file.finishWrite(out) }
        catch (error: Exception) { file.failWrite(out); throw error }
        part.delete()
        changed.value++
    }
    fun electiveAdvice(context: Context): String = content(context)?.electiveAdvice
        ?: context.assets.open("content/elective-advice.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }
}
