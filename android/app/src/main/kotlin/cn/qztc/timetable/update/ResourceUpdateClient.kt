package cn.qztc.timetable.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/** Data-only content updates. Executable code and application settings are never loaded here. */
object ResourceUpdateClient {
    const val MANIFEST_URL = "https://huanmen.com.cn/qsschedule/api/resources"
    const val MAX_BYTES = 1024 * 1024L
    private val json = Json { ignoreUnknownKeys = false }
    @Serializable data class Manifest(
        val schemaVersion: Int = 1, val version: Int, val minVersionCode: Int = 15,
        val url: String, val sha256: String, val sizeBytes: Long, val notes: List<String> = emptyList(),
    )
    @Serializable data class Content(val schemaVersion: Int = 1, val version: Int, val electiveAdvice: String)

    class Control {
        @Volatile var stopped = false
            private set
        private val connection = AtomicReference<HttpURLConnection?>()
        fun stop() { stopped = true; connection.getAndSet(null)?.disconnect() }
        internal fun check() { if (stopped) throw IOException("下载已暂停") }
        internal fun attach(c: HttpURLConnection) { connection.set(c); if (stopped) { c.disconnect(); check() } }
        internal fun detach(c: HttpURLConnection) { connection.compareAndSet(c, null) }
    }

    fun parseManifest(text: String): Manifest = json.decodeFromString<Manifest>(text).also {
        require(it.schemaVersion == 1 && it.version > 0 && it.minVersionCode > 0) { "资源版本不支持" }
        val uri = URI(it.url)
        require(uri.scheme == "https" && uri.host == "huanmen.com.cn" && uri.port in listOf(-1, 443) &&
            uri.userInfo == null && uri.query == null && uri.fragment == null &&
            uri.rawPath.matches(Regex("/qsschedule/downloads/content-[a-f0-9]{64}\\.json"))) { "资源地址无效" }
        require(it.sha256.matches(Regex("[a-f0-9]{64}")) && it.url.endsWith("content-${it.sha256}.json")) { "资源校验值无效" }
        require(it.sizeBytes in 1..MAX_BYTES && it.notes.size <= 20 && it.notes.all { note -> note.length <= 500 }) { "资源大小无效" }
    }
    fun parseContent(text: String): Content = json.decodeFromString<Content>(text).also {
        require(it.schemaVersion == 1 && it.version > 0 && it.electiveAdvice.isNotBlank() && it.electiveAdvice.length <= 30_000) { "资料内容无效" }
    }
    private fun connection(url: String, control: Control, offset: Long = 0): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 8_000; readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Cache-Control", "no-cache")
            if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
            control.attach(this)
        }

    suspend fun check(): Manifest = withContext(Dispatchers.IO) {
        val control = Control()
        val c = connection(MANIFEST_URL, control)
        try {
            require(c.responseCode == 200) { "暂时无法检查更新资源（${c.responseCode}）" }
            val output = java.io.ByteArrayOutputStream()
            c.inputStream.use { AppUpdateClient.copyLimited(it, output, 32 * 1024L) }
            val bytes = output.toByteArray()
            parseManifest(bytes.toString(Charsets.UTF_8))
        } finally { c.disconnect() }
    }

    fun partial(directory: File, m: Manifest) = File(directory, "${m.sha256}.part")
    fun downloaded(directory: File, m: Manifest): Long = partial(directory, m).length().coerceAtMost(m.sizeBytes)
    fun clearDownload(directory: File, m: Manifest) {
        val file = partial(directory, m)
        check(!file.exists() || file.delete()) { "未能删除下载文件，请重试" }
    }

    /** A 200 response to Range deliberately restarts; a mismatched 206 must never append. */
    internal fun validateResponse(code: Int, range: String?, offset: Long, size: Long): Boolean {
        if (code == 200) return false
        require(code == 206 && offset > 0 && range == "bytes $offset-${size - 1}/$size") { "服务器返回的续传范围无效" }
        return true
    }

    internal suspend fun download(directory: File, m: Manifest, control: Control, onProgress: (Long) -> Unit,
        open: (String, Control, Long) -> HttpURLConnection = ::connection): Content = withContext(Dispatchers.IO) {
        parseManifest(json.encodeToString(Manifest.serializer(), m))
        directory.mkdirs()
        val part = partial(directory, m)
        if (part.length() > m.sizeBytes) check(part.delete())
        var offset = part.length()
        control.check(); onProgress(offset)
        if (offset < m.sizeBytes) {
            val c = open(m.url, control, offset)
            try {
                val append = validateResponse(c.responseCode, c.getHeaderField("Content-Range"), offset, m.sizeBytes)
                if (!append) offset = 0
                val expected = m.sizeBytes - offset
                require(c.contentLengthLong == -1L || c.contentLengthLong == expected) { "资源长度不匹配" }
                java.io.FileOutputStream(part, append).use { out ->
                    c.inputStream.use { input ->
                        val buffer = ByteArray(8 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive(); control.check()
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(offset + count <= m.sizeBytes) { "资源超过清单大小" }
                            out.write(buffer, 0, count); offset += count; onProgress(offset)
                        }
                    }
                    out.fd.sync()
                }
            } finally { control.detach(c); c.disconnect() }
        }
        control.check(); currentCoroutineContext().ensureActive()
        require(part.length() == m.sizeBytes) { "下载中断，可继续下载" }
        val digest = MessageDigest.getInstance("SHA-256").digest(part.readBytes()).joinToString("") { "%02x".format(it) }
        if (digest != m.sha256) { part.delete(); error("资源校验失败，请重新下载") }
        val content = parseContent(part.readText(Charsets.UTF_8))
        require(content.version == m.version) { "资料版本不匹配" }
        content
    }
}

internal fun formatResourceBytes(bytes: Long): String {
    val value = bytes.coerceAtLeast(0).toDouble()
    val unit = when { value >= 1024 * 1024 * 1024 -> 3; value >= 1024 * 1024 -> 2; value >= 1024 -> 1; else -> 0 }
    return if (unit == 0) "$bytes B" else String.format(Locale.ROOT, "%.2f %s", value / Math.pow(1024.0, unit.toDouble()), listOf("B", "KB", "MB", "GB")[unit])
}
