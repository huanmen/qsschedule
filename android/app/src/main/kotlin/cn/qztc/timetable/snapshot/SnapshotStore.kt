package cn.qztc.timetable.snapshot

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Only user-requested local web page captures are retained as snapshots. */
enum class SnapshotKind { WEB_PAGE }

data class SnapshotRecord(
    val file: File,
    val kind: SnapshotKind,
    val pending: Boolean,
    val createdAt: Long,
) {
    val label: String get() = "教务课表页面"
}

/**
 * Stores only user-requested web page images under filesDir. Import diffs are transient data and
 * are deliberately kept out of the local snapshot gallery.
 */
class SnapshotStore(private val context: Context) {
    private val directory = File(context.filesDir, "snapshots").apply { mkdirs() }

    fun saveWebPage(bitmap: Bitmap): File {
        val file = File(directory, "web-${System.currentTimeMillis()}.png")
        bitmap.writePng(file)
        trim()
        return file
    }

    fun records(): List<SnapshotRecord> {
        // Remove previews created by older builds so they do not remain hidden in storage.
        directory.listFiles().orEmpty()
            .filter { it.name.startsWith("pending-") || it.name.startsWith("resolved-") }
            .forEach { it.delete() }
        return directory.listFiles()
            .orEmpty()
            .mapNotNull(::recordFor)
            .sortedByDescending(SnapshotRecord::createdAt)
    }

    fun remove(record: SnapshotRecord) {
        record.file.delete()
    }

    fun share(record: SnapshotRecord) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", record.file)
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType("image/png")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                "分享课表快照",
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun saveToGallery(record: SnapshotRecord): Result<Unit> = runCatching {
        val displayName = "泉师课程表_${record.createdAt}.png"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/泉师课程表")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("无法创建相册文件")
        try {
            FileInputStream(record.file).use { input ->
                resolver.openOutputStream(uri)?.use { output -> input.copyTo(output) }
                    ?: error("无法写入相册文件")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            }
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun recordFor(file: File): SnapshotRecord? {
        val name = file.name
        if (!file.isFile || !name.endsWith(".png")) return null
        return when {
            name.startsWith("web-") -> SnapshotRecord(file, SnapshotKind.WEB_PAGE, pending = false, createdAt = file.lastModified())
            else -> null
        }
    }

    private fun trim() {
        records()
            .filter { it.kind == SnapshotKind.WEB_PAGE }
            .drop(MAX_RESOLVED_PER_KIND)
            .forEach { it.file.delete() }
    }

    private fun Bitmap.writePng(file: File) {
        FileOutputStream(file).use { output ->
            check(compress(Bitmap.CompressFormat.PNG, 92, output)) { "快照写入失败" }
        }
        recycle()
    }

    private companion object {
        const val MAX_RESOLVED_PER_KIND = 5
    }
}
