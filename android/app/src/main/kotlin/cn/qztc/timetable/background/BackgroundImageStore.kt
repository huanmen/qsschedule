package cn.qztc.timetable.background

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackgroundImageStore(private val context: Context) {
    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, "backgrounds").apply { mkdirs() }
        val target = File.createTempFile("background-candidate-", ".image", directory)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法读取所选图片" }
            target.outputStream().use { output -> input.copyTo(output) }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(target.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) {
            target.delete()
            "所选文件不是可读取的图片"
        }
        target.absolutePath
    }

    suspend fun finalizeCandidate(path: String): String = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, "backgrounds").apply { mkdirs() }
        val source = File(path).canonicalFile
        require(source.parentFile == directory.canonicalFile && source.name.startsWith("background-candidate-")) {
            "背景图片来源无效"
        }
        val target = File(directory, "background-${System.currentTimeMillis()}-${System.nanoTime()}.image")
        check(source.renameTo(target) || run {
            source.copyTo(target, overwrite = false)
            source.delete()
        }) { "无法保存背景图片" }
        target.absolutePath
    }

    fun listImportedImages(): List<String> {
        val directory = File(context.filesDir, "backgrounds")
        return directory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith("background-") && !it.name.startsWith("background-candidate-") }
            .sortedByDescending(File::lastModified)
            .map(File::getAbsolutePath)
    }

    fun discardCandidate(path: String?) {
        val file = path?.let(::File) ?: return
        val directory = File(context.filesDir, "backgrounds")
        if (file.parentFile == directory && file.name.startsWith("background-candidate-")) {
            file.delete()
        }
    }

    fun deleteImportedImage(path: String) {
        val directory = File(context.filesDir, "backgrounds").canonicalFile
        val file = File(path).canonicalFile
        require(file.parentFile == directory && file.name.startsWith("background-") &&
            !file.name.startsWith("background-candidate-")) {
            "背景图片来源无效"
        }
        check(file.delete()) { "无法删除背景图片" }
        BackgroundBitmapCache.remove(path)
    }
}

internal fun decodeBackgroundBitmap(path: String, maxDimension: Int = 2048): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
        sample *= 2
    }
    return BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        },
    )
}
