package dev.montra.install

import android.content.Context
import dev.montra.data.model.Asset
import dev.montra.util.Log
import dev.montra.util.toHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * Download an APK and hash it *while* streaming.
 *
 * Hashing during the download matters: the catalogue contains APKs over 300 MB,
 * and reading a file that size twice on a phone is a waste of battery and time.
 * The file is only moved into place after the digest matches, so a half-written
 * or tampered download never exists under its final name.
 *
 * A download that fails part-way is resumed, not restarted: 200 MB of someone's data
 * that already arrived is not something to throw away because the train went into a
 * tunnel. The digest is fed the bytes already on disk first, so the hash still covers
 * the whole file and the guarantee is unchanged.
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
     *
     * Apagar o `.part` aqui é deliberado, e é a diferença entre cancelar e falhar:
     * quem cancela não quer a transferência, e guardar 150 MB na esperança de que
     * mude de ideias é decidir por ele. Uma falha de rede faz o contrário — deixa
     * ficar, porque é de lá que a próxima tentativa retoma.
     */
    fun cancel() {
        // A referência é agarrada antes de cancelar a chamada: o `catch` do download
        // corre assim que o socket se fecha, e põe o `activePart` a null. Lê-lo depois
        // era uma corrida que deixava o `.part` no cache exatamente no caso — cancelar
        // — em que ele tem de sair.
        val part = activePart
        activeCall?.cancel()
        activeCall = null
        part?.delete()
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

        val part = partFile(asset)
        // O que já temos de uma tentativa anterior. Só serve se for menos do que o
        // ficheiro inteiro: um `.part` do tamanho certo, ou maior, não é um prefixo
        // disto e não vale a pena tentar perceber de onde veio.
        var resumed = part.takeIf { it.isFile && it.length() in 1 until asset.size }?.length() ?: 0L

        val digest = MessageDigest.getInstance("SHA-256")
        if (resumed > 0) {
            hashInto(digest, part)
            Log.i("a retomar ${asset.abi} a partir de $resumed bytes")
        }

        val request = Request.Builder()
            .url(asset.url)
            .apply { if (resumed > 0) header("Range", "bytes=$resumed-") }
            .build()
        val call = client.newCall(request)
        activeCall = call
        try {
        call.execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} ao descarregar o APK")

            // 206 é o servidor a aceitar retomar. Um 200 depois de um Range pedido é o
            // servidor a ignorá-lo: o que está em disco não é um prefixo do que ele vai
            // mandar, e a única saída honesta é recomeçar.
            val append = resumed > 0 && response.code == 206
            if (resumed > 0 && !append) {
                Log.i("o servidor não retomou; a transferência recomeça do princípio")
                part.delete()
                resumed = 0L
                digest.reset()
            }

            val body = response.body ?: throw IOException("resposta vazia")
            val remaining = if (body.contentLength() > 0) body.contentLength() else asset.size - resumed
            // O tamanho do índice manda quando existe: é o número contra o qual o hash
            // foi calculado, e é o que a barra de progresso deve mostrar.
            val total = if (asset.size > 0) asset.size else resumed + remaining
            activePart = part
            var received = resumed
            try {
                body.byteStream().use { input ->
                    FileOutputStream(part, append).use { output ->
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
                // Uma falha a meio deixa o `.part` onde está: é daqui que a próxima
                // tentativa retoma. Quem apaga é o `cancel()`, que é uma decisão do
                // utilizador, ou a poda, quando já ninguém vai retomar isto.
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
        dir.listFiles()?.forEach { if (it != activePart) it.delete() }
    }

    /** O que as transferências ocupam agora, para as Definições o poderem dizer. */
    fun sizeOnDisk(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    /**
     * Apaga o que já não serve para nada.
     *
     * Um `.part` deixou de ser sempre lixo quando a retoma passou a existir: o que
     * corresponde a uma entrada do catálogo é trabalho já pago, e a próxima tentativa
     * continua de lá. O que já não corresponde a entrada nenhuma — porque a app saiu do
     * catálogo, ou porque a versão mudou — não serve a ninguém. E um `.part` que
     * ninguém retomou numa semana já não vai ser retomado: guardar 150 MB com essa
     * esperança é pior do que os perder.
     *
     * Isto existe porque morrer a meio nunca passava pelo `catch`: o sistema a matar o
     * processo, ou o utilizador a arrastar a app para fora, deixavam o ficheiro lá. O
     * `clear()` já existia e não tinha um único chamador.
     */
    fun prune(assets: List<Asset>) {
        val keep = assets.mapTo(HashSet()) { target(it) }
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { file ->
            // Um download a meio não é lixo: apagá-lo era matá-lo. Isto acontece a
            // sério, porque a poda corre sempre que o catálogo é verificado.
            if (file == activePart) return@forEach
            val base = file.name.removeSuffix(PART_SUFFIX)
            val abandoned = file.name.endsWith(PART_SUFFIX) && now - file.lastModified() > STALE_PART_MILLIS
            if (base !in keep || abandoned) file.delete()
        }
    }

    private fun target(asset: Asset): String {
        val base = asset.url.substringAfterLast('/').ifBlank { "app.apk" }
        return "${asset.abi}-${asset.sha256.take(8)}-$base".replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    /**
     * Onde vive um download a meio deste asset.
     *
     * `internal` porque o teste da retoma precisa de o semear com os primeiros bytes
     * de uma transferência interrompida, e a alternativa era o teste copiar a regra do
     * nome — que é precisamente o que deixaria de estar testado no dia em que mudasse.
     */
    internal fun partFile(asset: Asset): File = File(dir, "${target(asset)}$PART_SUFFIX")

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        hashInto(digest, file)
        return digest.digest().toHex()
    }

    /** Alimenta o digest com o que já está em disco, para o hash cobrir o ficheiro todo. */
    private fun hashInto(digest: MessageDigest, file: File) {
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
    }

    private companion object {
        /** O sufixo de um download a meio, que a próxima tentativa retoma. */
        const val PART_SUFFIX = ".part"

        /** Uma semana. Depois disto, ninguém vai retomar aquilo. */
        const val STALE_PART_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
