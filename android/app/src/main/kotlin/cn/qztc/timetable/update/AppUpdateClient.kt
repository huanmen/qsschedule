package cn.qztc.timetable.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicReference
import cn.qztc.timetable.distribution.DistributionClient

/** Small, signed-by-the-APK update feed. The server never receives timetable data. */
object AppUpdateClient {
    const val MANIFEST_URL = "https://gitee.com/huandyu/qsschedule/raw/master/update.json"
    const val PRIMARY_MANIFEST_URL = DistributionClient.BASE_URL + "/api/update"
    // Gitee Release attachments redirect to this official file-delivery host.
    private val TRUSTED_DOWNLOAD_HOSTS = setOf("gitee.com", "giteeusercontent.com", "foruda.gitee.com", "huanmen.com.cn")
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val MAX_MANIFEST_BYTES = 64 * 1024
    private const val MAX_APK_BYTES = 150L * 1024L * 1024L
    private val json = Json { ignoreUnknownKeys = false }

    class DownloadControl {
        @Volatile var cancelled = false
            private set
        private val connection = AtomicReference<HttpURLConnection?>()

        fun cancel() {
            cancelled = true
            connection.getAndSet(null)?.disconnect()
        }

        internal fun ensureRunning() {
            if (cancelled) throw CancellationException("更新已取消")
        }

        internal fun attach(value: HttpURLConnection) {
            connection.set(value)
            if (cancelled) {
                connection.getAndSet(null)?.disconnect()
                ensureRunning()
            }
        }

        internal fun detach(value: HttpURLConnection) {
            connection.compareAndSet(value, null)
        }
    }

    @Serializable
    data class Manifest(
        val latestVersionCode: Int,
        val latestVersionName: String,
        val apkUrl: String,
        val sha256: String,
        val sizeBytes: Long = 0,
        val notes: List<String> = emptyList(),
    )

    suspend fun check(currentVersionCode: Int): Result<Manifest?> = withContext(Dispatchers.IO) {
        runCatching {
            val manifest = resolveManifest(::fetchManifest)
            require(manifest.latestVersionCode >= 1) { "更新清单版本号无效" }
            require(manifest.latestVersionName.isNotBlank()) { "更新清单版本名称为空" }
            validateDownloadUrl(manifest.apkUrl)
            require(manifest.sha256.matches(Regex("[A-Fa-f0-9]{64}"))) { "更新清单校验值无效" }
            require(manifest.sizeBytes in 1..MAX_APK_BYTES) { "更新包大小无效" }
            if (manifest.latestVersionCode <= currentVersionCode) null else manifest
        }
    }

    suspend fun download(
        context: Context,
        manifest: Manifest,
        control: DownloadControl = DownloadControl(),
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): Result<File> {
        val first = downloadFrom(context, manifest, control, onProgress)
        if (first.isSuccess) {
            DistributionClient.completed(context, "app", manifest.latestVersionCode)
            return first
        }
        control.ensureRunning()
        if (URI(manifest.apkUrl).host != "huanmen.com.cn") return first
        val fallback = withContext(Dispatchers.IO) { runCatching { fetchManifest(MANIFEST_URL).also(::validateManifest) }.getOrNull() }
        if (fallback == null || !sameRelease(manifest, fallback)) return first
        val result = downloadFrom(context, fallback, control, onProgress)
        if (result.isSuccess) DistributionClient.completed(context, "app", manifest.latestVersionCode)
        return result
    }

    private suspend fun downloadFrom(
        context: Context,
        manifest: Manifest,
        control: DownloadControl = DownloadControl(),
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): Result<File> = withContext(Dispatchers.IO) {
        val downloadContext = currentCoroutineContext()
        val checkCancellation = { downloadContext.ensureActive(); control.ensureRunning() }
        runCatching {
            checkCancellation()
            validateDownloadUrl(manifest.apkUrl)
            require(manifest.sizeBytes in 1..MAX_APK_BYTES) { "更新包大小无效" }
            val directory = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(directory, "qztc-timetable-${manifest.latestVersionCode}.apk")
            if (target.exists() && target.length() == manifest.sizeBytes && sha256(target) == manifest.sha256.lowercase()) {
                checkCancellation()
                onProgress(target.length(), manifest.sizeBytes)
                return@runCatching target
            }
            onProgress(0, manifest.sizeBytes)
            val connection = openTrustedConnection(
                manifest.apkUrl,
                "GET",
                "application/vnd.android.package-archive",
                control,
            )
            try {
                require(connection.responseCode in 200..299) { "下载更新包失败（HTTP ${connection.responseCode}）" }
                val contentLength = connection.contentLengthLong
                require(manifest.sizeBytes in 1..MAX_APK_BYTES) { "更新包大小无效" }
                require(contentLength == -1L || manifest.sizeBytes == contentLength) { "更新包大小与清单不一致" }
                val temporary = File(directory, ".${target.name}.part")
                try {
                    var lastPercent = -1L
                    connection.inputStream.use { input ->
                        temporary.outputStream().use { output ->
                            copyLimited(input, output, manifest.sizeBytes, onBytesCopied = { copied ->
                                val percent = copied * 100 / manifest.sizeBytes
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(copied, manifest.sizeBytes)
                                }
                            }, checkCancellation = checkCancellation)
                        }
                    }
                    checkCancellation()
                    require(temporary.length() == manifest.sizeBytes) { "更新包下载不完整" }
                    require(sha256(temporary) == manifest.sha256.lowercase()) { "更新包校验失败" }
                    val info = context.packageManager.getPackageArchiveInfo(temporary.absolutePath, 0)
                    require(info?.packageName == context.packageName) { "更新包不属于本应用" }
                    @Suppress("DEPRECATION")
                    require(info?.versionCode == manifest.latestVersionCode) { "更新包版本与清单不一致" }
                    checkCancellation()
                    if (!temporary.renameTo(target)) error("无法保存更新包")
                } finally {
                    temporary.delete()
                }
            } finally {
                control.detach(connection)
                connection.disconnect()
            }
            target
        }.onFailure { if (it is CancellationException) throw it }
    }

    suspend fun clearDownloads(context: Context): Int = withContext(Dispatchers.IO) {
        clearDownloadedFiles(File(context.cacheDir, "updates"))
    }

    internal fun clearDownloadedFiles(directory: File): Int {
        val ownedName = Regex("\\.?qztc-timetable-[0-9]+\\.apk(\\.part)?")
        var count = 0
        directory.listFiles().orEmpty().filter { it.isFile && ownedName.matches(it.name) }.forEach { file ->
            check(file.delete()) { "未能清除更新文件，请重试" }
            count++
        }
        return count
    }

    fun install(context: Context, apk: File) {
        require(apk.isFile) { "更新包不存在" }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun fetchManifest(url: String): Manifest {
        val connection = openTrustedConnection(url, "GET", "application/json")
        try {
            require(connection.responseCode in 200..299) { "检查更新失败（HTTP ${connection.responseCode}）" }
            val length = connection.contentLengthLong
            require(length == -1L || length in 1..MAX_MANIFEST_BYTES.toLong()) { "更新清单大小无效" }
            val output = ByteArrayOutputStream()
            connection.inputStream.use { copyLimited(it, output, MAX_MANIFEST_BYTES.toLong()) }
            val text = output.toString("UTF-8").removePrefix("\uFEFF")
            return json.decodeFromString(Manifest.serializer(), text)
        } finally {
            connection.disconnect()
        }
    }

    internal fun validateDownloadUrl(value: String) {
        val uri = URI(value)
        require(uri.scheme == "https") { "更新地址必须使用 HTTPS" }
        require(uri.host in TRUSTED_DOWNLOAD_HOSTS || uri.host?.endsWith(".giteeusercontent.com") == true) {
            "更新地址主机不受支持"
        }
        require(uri.userInfo == null && uri.fragment == null) { "更新地址包含不安全参数" }
        if (uri.host == "huanmen.com.cn") require(uri.path.startsWith("/qsschedule/") && uri.path.split('/').none { it == ".." || it == "." } && (uri.port == -1 || uri.port == 443)) { "更新地址路径不受支持" }
    }

    internal fun validateManifest(manifest: Manifest) {
        require(manifest.latestVersionCode > 0 && manifest.latestVersionName.isNotBlank())
        require(manifest.sizeBytes in 1..MAX_APK_BYTES && manifest.sha256.matches(Regex("[A-Fa-f0-9]{64}")))
        validateDownloadUrl(manifest.apkUrl)
    }
    internal fun resolveManifest(fetch: (String) -> Manifest): Manifest =
        listOf(PRIMARY_MANIFEST_URL, MANIFEST_URL).firstNotNullOfOrNull { url ->
            runCatching { fetch(url).also(::validateManifest) }.getOrNull()
        } ?: error("暂时无法检查更新")
    internal fun sameRelease(a: Manifest, b: Manifest) = a.latestVersionCode == b.latestVersionCode &&
        a.sha256.equals(b.sha256, ignoreCase = true) && a.sizeBytes == b.sizeBytes

    internal fun openTrustedConnection(value: String, method: String, accept: String, control: DownloadControl? = null): HttpURLConnection {
        var current = URL(value)
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            control?.ensureRunning()
            validateDownloadUrl(current.toExternalForm())
            val connection = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                requestMethod = method
                setRequestProperty("Accept", accept)
                setRequestProperty("User-Agent", "QSSchedule-Android")
                setRequestProperty("Cache-Control", "no-cache")
            }
            control?.attach(connection)
            val code = try {
                connection.responseCode
            } catch (error: Exception) {
                control?.detach(connection)
                connection.disconnect()
                throw error
            }
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                control?.detach(connection)
                connection.disconnect()
                require(redirectCount < MAX_REDIRECTS && !location.isNullOrBlank()) { "更新地址重定向次数过多" }
                current = URL(current, location)
                validateDownloadUrl(current.toExternalForm())
            } else {
                return connection
            }
        }
        error("无法打开更新地址")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private const val MAX_REDIRECTS = 5

    internal fun copyLimited(
        input: InputStream, output: OutputStream, limit: Long,
        onBytesCopied: (Long) -> Unit = {}, checkCancellation: () -> Unit = {},
    ) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            checkCancellation()
            val count = input.read(buffer)
            checkCancellation()
            if (count < 0) break
            total += count
            require(total <= limit) { "下载内容超出允许大小" }
            output.write(buffer, 0, count)
            onBytesCopied(total)
        }
    }
}
