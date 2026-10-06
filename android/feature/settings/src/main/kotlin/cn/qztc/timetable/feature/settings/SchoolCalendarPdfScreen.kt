package cn.qztc.timetable.feature.settings

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.qztc.timetable.core.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@Composable
internal fun SchoolCalendarPdfScreen(url: String, onBack: () -> Unit, onDownload: () -> Unit) {
    val context=LocalContext.current
    var pages by remember(url) { mutableStateOf(emptyList<Bitmap>()) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }
    BackHandler(onBack=onBack)
    LaunchedEffect(url,retry) {
        error=null
        runCatching { withContext(Dispatchers.IO) {
            val link=URL(url)
            require(link.protocol=="https" && link.host=="www.qztc.edu.cn") { "原文件地址不受信任" }
            val file=File(context.cacheDir,"school-calendar-${url.hashCode()}.pdf")
            if(!file.isFile) {
                val connection=link.openConnection() as HttpURLConnection
                connection.connectTimeout=12000;connection.readTimeout=20000
                try {
                    require(connection.responseCode in 200..299)
                    val bytes=connection.inputStream.use { input ->
                        val output=java.io.ByteArrayOutputStream()
                        val buffer=ByteArray(8192)
                        while(true) {
                            val count=input.read(buffer)
                            if(count<0) break
                            require(output.size()+count<=12*1024*1024)
                            output.write(buffer,0,count)
                        }
                        output.toByteArray()
                    }
                    require(bytes.size<=12*1024*1024 && bytes.take(5).toByteArray().toString(Charsets.US_ASCII)=="%PDF-") { "校历原文件无效或过大" }
                    file.writeBytes(bytes)
                } finally { connection.disconnect() }
            }
            PdfRenderer(ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                require(renderer.pageCount<=8) { "校历页数过多" }
                var totalPixels=0L
                (0 until renderer.pageCount).map { index -> renderer.openPage(index).use { page ->
                    val width=1200
                    val height=(width.toFloat()*page.height/page.width).toInt()
                    totalPixels+=width.toLong()*height
                    require(height in 1..6000 && totalPixels<=12_000_000) { "校历页面过大" }
                    Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).also { bitmap ->
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                } }
            }
        } }.onSuccess { pages=it }.onFailure { error="原文件读取失败，请重试" }
    }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        QztcPageTopBar(title="校历原文件",onBack=onBack,actions={QztcTopIconButton(Icons.Outlined.Download,"下载原文件",onDownload)})
        if(pages.isEmpty()&&error==null) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it,Modifier.padding(20.dp));TextButton(onClick={retry++}){Text("重试")} }
        LazyColumn(Modifier.weight(1f)) {
            items(pages) { bitmap -> Image(bitmap.asImageBitmap(),"校历 PDF 页面",Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat()/bitmap.height)) }
        }
    }
}
