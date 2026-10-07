package dev.openshelf.data

import android.content.Context
import dev.openshelf.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Where index.json comes from. Three sources, in order of preference:
 *
 *  1. the network (whatever URL the user configured),
 *  2. the last verified copy on disk,
 *  3. a snapshot bundled in the APK as an asset, so a first launch with no
 *     connectivity still shows a catalogue instead of an empty screen.
 *
 * Nothing here trusts anything: this class only moves bytes. Verification
 * happens in [IndexRepository].
 */
class IndexSource(private val context: Context, private val client: OkHttpClient) {

    class Payload(
        val bytes: ByteArray,
        val signature: String,
        val etag: String?,
        val fromNetwork: Boolean,
    )

    private val cacheDir: File get() = File(context.filesDir, "index").apply { mkdirs() }
    val cachedIndexFile: File get() = File(cacheDir, "index.json")
    val cachedSignatureFile: File get() = File(cacheDir, "index.json.sig")

    /** @return null when the server answered 304 Not Modified. */
    suspend fun fetchRemote(indexUrl: String, etag: String?): Payload? = withContext(Dispatchers.IO) {
        val url = indexUrl.toHttpUrlOrNull() ?: throw IOException("URL do índice inválido: $indexUrl")
        require(url.isHttps || url.host == "localhost" || url.host == "10.0.2.2") {
            "o índice tem de ser servido por HTTPS"
        }

        var newEtag: String? = null
        val bytes = client.newCall(
            Request.Builder()
                .url(url)
                .apply { if (etag != null) header("If-None-Match", etag) }
                .build(),
        ).execute().use { response ->
            if (response.code == 304) return@withContext null
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} ao obter o índice")
            newEtag = response.header("ETag")
            response.body?.bytes() ?: throw IOException("resposta vazia do índice")
        }

        val signature = client.newCall(Request.Builder().url("$indexUrl.sig").build())
            .execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("assinatura indisponível (HTTP ${response.code})")
                }
                response.body?.string() ?: throw IOException("assinatura vazia")
            }

        Payload(bytes = bytes, signature = signature, etag = newEtag, fromNetwork = true)
    }

    /** The snapshot shipped in the APK. Always available, never null. */
    fun bundled(): Payload {
        val bytes = context.assets.open(BuildConfig.BUNDLED_INDEX_ASSET).use { it.readBytes() }
        val signature = context.assets.open("${BuildConfig.BUNDLED_INDEX_ASSET}.sig").use {
            it.bufferedReader().readText()
        }
        return Payload(bytes, signature, etag = null, fromNetwork = false)
    }

    /** The last verified copy, if the app managed to store one. */
    fun cached(): Payload? {
        if (!cachedIndexFile.isFile || !cachedSignatureFile.isFile) return null
        return try {
            Payload(
                bytes = cachedIndexFile.readBytes(),
                signature = cachedSignatureFile.readText(),
                etag = null,
                fromNetwork = false,
            )
        } catch (_: IOException) {
            null
        }
    }

    /** Only ever called with bytes that already passed verification. */
    fun storeVerified(payload: Payload) {
        cachedIndexFile.writeBytes(payload.bytes)
        cachedSignatureFile.writeText(payload.signature)
    }

    /**
     * Resolve a path that is relative to the index (icons/x.png) against the URL
     * the index came from. This is why the index stores relative paths: mirrors,
     * forks and local test servers all work with no configuration.
     */
    fun resolveAgainst(indexUrl: String, relative: String): String {
        if (relative.startsWith("http://") || relative.startsWith("https://")) return relative
        val base = indexUrl.toHttpUrlOrNull() ?: return relative
        return base.resolve(relative)?.toString() ?: relative
    }
}
