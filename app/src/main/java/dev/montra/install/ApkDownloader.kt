package dev.montra.install

import android.content.Context
import dev.montra.data.model.Asset
import dev.montra.util.toHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Download an APK and hash it *while* streaming.
 *
 * Hashing during the download matters: the catalogue contains APKs over 300 MB,
 * and reading a file that size twice on a phone is a waste of battery and time.
 * The file is only moved into place after the digest matches, so a half-written
 * or tampered download never exists under its final name.
 */
class ApkDownloader(private val context: Context, private val client: OkHttpClient) {

    data class Progress(val bytes: Long, val total: Long) {
        val fraction: Float get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
    }

    class Downloaded(val file: File, val sha256: String)

    private val dir: File get() = File(context.cacheDir, "apk").apply { mkdirs() }

    /**
     * The in-flight HTTP call, so a cancel is immediate.
     *
     * Cancelling the coroutine is not enough: the download runs on a blocking
     * OkHttp call, and suspending is not the same as interrupting. Only
     * Call.cancel() aborts the socket read that is actually in progress.
     */
    @Volatile
    private var activeCall: Call? = null

    @Volatile
    private var activePart: File? = null

    /**
     * Cancels the HTTP call *and* removes the partial file right away.
     *
     * Waiting for the download coroutine to unwind before cleaning up leaves a
     * 30 MB `.part` behind for as long as it takes the socket to notice, and the
     * user's cache has no way to know it is garbage.
     */
    fun cancel() {
        activeCall?.cancel()
        activeCall = null
        activePart?.delete()
        activePart = null
    }

    suspend fun download(
        asset: Asset,
        onProgress: (Progress) -> Unit = {},
    ): Downloaded = withContext(Dispatchers.IO) {
        val target = File(dir, target(asset))
        if (target.isFile && target.length() == asset.size) {
            // Already downloaded in a previous attempt; re-hash instead of re-fetching.
            val digest = sha256Of(target)
            if (digest.equals(asset.sha256, ignoreCase = true)) {
                onProgress(Progress(asset.size, asset.size))
                return@withContext Downloaded(target, digest)
            }
            target.delete()
        }

        val request = Request.Builder().url(asset.url).build()
        val digest = MessageDigest.getInstance("SHA-256")
        val call = client.newCall(request)
        activeCall = call
        try {
        call.execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} ao descarregar o APK")
            val body = response.body ?: throw IOException("resposta vazia")
            val total = if (body.contentLength() > 0) body.contentLength() else asset.size
            val part = File(dir, "${target.name}.part")
            activePart = part
            var received = 0L
            try {
                body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            received += read
                            onProgress(Progress(received, total))
                        }
                        output.flush()
                    }
                }
            } catch (error: Exception) {
                // Cancelar a meio (ou uma falha de rede) não pode deixar lixo no
                // cache: um .part de 30 MB fica lá para sempre.
                part.delete()
                activePart = null
                throw error
            }
            activePart = null
            val sha = digest.digest().toHex()
            if (!sha.equals(asset.sha256, ignoreCase = true)) {
                part.delete()
                throw IOException(
                    "O SHA-256 do download não corresponde ao índice.\nesperado: ${asset.sha256}\nobtido:   $sha",
                )
            }
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) throw IOException("não foi possível finalizar o download")
            return@withContext Downloaded(target, sha)
        }
        } finally {
            activeCall = null
        }
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun target(asset: Asset): String {
        val base = asset.url.substringAfterLast('/').ifBlank { "app.apk" }
        return "${asset.abi}-${asset.sha256.take(8)}-$base".replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }
}
