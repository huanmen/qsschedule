package cn.qztc.timetable.update

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import cn.qztc.timetable.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class ResourceUpdateState(
    val stage: String = "idle", val manifest: ResourceUpdateClient.Manifest? = null,
    val bytes: Long = 0, val error: String? = null,
)

/** Process-wide session: navigation and Activity recreation do not own the transfer. */
internal object ResourceUpdateSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(ResourceUpdateState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var control: ResourceUpdateClient.Control? = null

    fun check(context: Context, onNoUpdate: () -> Unit) {
        if (mutable.value.stage != "idle") return
        val app = context.applicationContext
        mutable.value = ResourceUpdateState(stage = "checking")
        job = scope.launch {
            try {
                val m = ResourceUpdateClient.check()
                val installed = withContext(Dispatchers.IO) { ResourceContentStore.content(app)?.version ?: ResourceContentStore.BUNDLED_VERSION }
                if (m.minVersionCode > BuildConfig.VERSION_CODE || m.version <= installed) {
                    mutable.value = ResourceUpdateState(); onNoUpdate()
                } else {
                    val bytes = withContext(Dispatchers.IO) { ResourceUpdateClient.downloaded(ResourceContentStore.directory(app), m) }
                    mutable.value = ResourceUpdateState(if (bytes > 0) "paused" else "available", m, bytes)
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { mutable.value = ResourceUpdateState("error", error = error.message ?: "检查失败，请重试") }
        }
    }

    fun requestDownload(context: Context) {
        if (mutable.value.manifest == null || job?.isActive == true) return
        try {
            ContextCompat.startForegroundService(context, Intent(context, ResourceDownloadService::class.java))
        } catch (error: Exception) { mutable.value = mutable.value.copy(stage = "error", error = "无法启动后台更新：${error.message}") }
    }

    fun download(context: Context, onFinished: () -> Unit,
        transfer: suspend (Context, ResourceUpdateClient.Manifest, ResourceUpdateClient.Control, (Long) -> Unit) -> Unit = { app, m, active, progress ->
            ResourceUpdateClient.download(ResourceContentStore.directory(app), m, active, onProgress = progress)
        }) {
        if (job?.isActive == true) { onFinished(); return }
        val m = mutable.value.manifest ?: run { onFinished(); return }
        val app = context.applicationContext
        val active = ResourceUpdateClient.Control()
        control = active
        mutable.value = mutable.value.copy(stage = "downloading", error = null)
        job = scope.launch {
            try {
                transfer(app, m, active) { bytes ->
                    scope.launch { if (control === active && mutable.value.stage == "downloading") mutable.value = mutable.value.copy(bytes = bytes) }
                }
                active.check()
                mutable.value = mutable.value.copy(stage = "applying")
                withContext(Dispatchers.IO) { ResourceContentStore.apply(app, m) }
                mutable.value = mutable.value.copy(stage = "done", bytes = m.sizeBytes)
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                if (!active.stopped) mutable.value = mutable.value.copy(stage = "error", error = error.message ?: "下载中断，可继续下载")
            } finally { onFinished() }
        }
    }

    fun stop(context: Context, clear: Boolean, onCleared: () -> Unit = {}) {
        if (mutable.value.stage in listOf("pausing", "clearing", "applying", "checking")) return
        val previous = job
        val active = control
        val m = mutable.value.manifest ?: return
        mutable.value = mutable.value.copy(stage = if (clear) "clearing" else "pausing")
        scope.launch {
            withContext(Dispatchers.IO) { active?.stop() }
            previous?.join()
            try {
                val directory = ResourceContentStore.directory(context.applicationContext)
                if (clear) {
                    withContext(Dispatchers.IO) { ResourceUpdateClient.clearDownload(directory, m) }
                    mutable.value = ResourceUpdateState(); onCleared()
                } else {
                    val bytes = withContext(Dispatchers.IO) { ResourceUpdateClient.downloaded(directory, m) }
                    mutable.value = mutable.value.copy(stage = "paused", bytes = bytes)
                }
            } catch (error: Exception) { mutable.value = mutable.value.copy(stage = "error", error = error.message) }
        }
    }

    fun reset() { if (job?.isActive != true) mutable.value = ResourceUpdateState() }
}
