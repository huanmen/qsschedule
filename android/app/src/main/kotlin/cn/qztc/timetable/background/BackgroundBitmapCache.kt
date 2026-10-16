package cn.qztc.timetable.background

import android.graphics.Bitmap
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Imported backgrounds have immutable paths. Never recycle a bitmap still used by Compose. */
internal object BackgroundBitmapCache {
    private val cache = object : LruCache<String, Bitmap>(
        minOf(24L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 12).toInt(),
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val decodeMutex = Mutex()

    fun peek(path: String): Bitmap? = cache.get(path)

    suspend fun load(path: String): Bitmap? = peek(path) ?: decodeMutex.withLock {
        peek(path) ?: withContext(Dispatchers.IO) { decodeBackgroundBitmap(path) }
            ?.also { cache.put(path, it) }
    }

    fun remove(path: String) { cache.remove(path) }
}
