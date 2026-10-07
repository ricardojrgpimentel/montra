package dev.openshelf.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Icons and screenshots, loaded with the few hundred kilobytes of code this
 * actually needs: a memory LRU, an HTTP disk cache, and no image library.
 *
 * The index stores media paths relative to itself and the build re-hosts them,
 * so these are plain GETs from one host — the same host as the index.
 */
class ImageStore(context: Context, baseClient: OkHttpClient) {

    private val client: OkHttpClient = baseClient.newBuilder()
        .cache(Cache(File(context.cacheDir, "http"), 32L * 1024 * 1024))
        .build()

    private val memory = object : LruCache<String, Bitmap>(24) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    private val locks = mutableMapOf<String, Mutex>()

    suspend fun load(url: String): Bitmap? {
        memory.get(url)?.let { return it }
        val mutex = synchronized(locks) { locks.getOrPut(url) { Mutex() } }
        return mutex.withLock {
            memory.get(url)?.let { return@withLock it }
            val bitmap = withContext(Dispatchers.IO) { fetch(url) }
            if (bitmap != null) memory.put(url, bitmap)
            synchronized(locks) { locks.remove(url) }
            bitmap
        }
    }

    private fun fetch(url: String): Bitmap? = try {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val bytes = response.body?.bytes() ?: return null
            // Decode at a sane size: a 1000x1000 source icon does not need 4 MB of heap.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, 512)
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
    } catch (_: Exception) {
        null
    }

    private fun sampleSize(width: Int, height: Int, target: Int): Int {
        var sample = 1
        var largest = maxOf(width, height)
        while (largest / 2 >= target) {
            largest /= 2
            sample *= 2
        }
        return sample
    }
}
