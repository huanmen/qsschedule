package cn.qztc.timetable.education

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.ui.theme.QztcPageTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

private val PDF_HOSTS = setOf(
    "www.qztc.edu.cn", "cet.neea.edu.cn", "ncre.neea.edu.cn", "www.eeafj.cn", "eeafj.cn",
    "jingsaitongzhi.com", "www.jingsaitongzhi.com", "huanmen.com.cn",
)

@Composable
internal fun AcademicPdfPreviewScreen(url: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var pages by remember(url) { mutableStateOf(emptyList<Bitmap>()) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }
    BackHandler(onBack = onBack)
    LaunchedEffect(url, retry) {
        error = null
        runCatching {
            withContext(Dispatchers.IO) {
                val uri = URI(url)
                require(uri.scheme == "https" && uri.host in PDF_HOSTS) { "附件地址不受信任" }
                require(uri.path?.lowercase()?.endsWith(".pdf") == true) { "仅支持 PDF 附件预览" }
                val file = File(context.cacheDir, "academic-pdf-${url.hashCode()}.pdf")
                if (!file.isFile) {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.connectTimeout = 12000
                    connection.readTimeout = 20000
                    connection.instanceFollowRedirects = false
                    try {
                        require(connection.responseCode in 200..299)
                        val bytes = connection.inputStream.use { input ->
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                require(output.size() + count <= 20 * 1024 * 1024) { "附件过大" }
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        }
                        require(bytes.size >= 5 && bytes.copyOfRange(0, 5).toString(Charsets.US_ASCII) == "%PDF-") { "附件不是有效 PDF" }
                        file.writeBytes(bytes)
                    } finally {
                        connection.disconnect()
                    }
                }
                PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                    require(renderer.pageCount in 1..20) { "PDF 页数过多" }
                    var totalPixels = 0L
                    (0 until renderer.pageCount).map { index ->
                        renderer.openPage(index).use { page ->
                            val width = 1200
                            val height = (width.toFloat() * page.height / page.width).toInt()
                            totalPixels += width.toLong() * height
                            require(height in 1..7000 && totalPixels <= 20_000_000) { "PDF 页面过大" }
                            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                                bitmap.eraseColor(android.graphics.Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                    }
                }
            }
        }.onSuccess { pages = it }.onFailure { error = it.message ?: "附件读取失败" }
    }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        QztcPageTopBar(title = "官方 PDF 附件", onBack = onBack)
        if (pages.isEmpty() && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {
            Text(it, Modifier.padding(20.dp))
            androidx.compose.material3.TextButton(onClick = { retry++ }) { Text("重试") }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(pages) { bitmap ->
                Image(bitmap.asImageBitmap(), "PDF 页面", Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height).padding(bottom = 8.dp))
            }
        }
    }
}
