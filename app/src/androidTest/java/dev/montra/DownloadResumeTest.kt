package dev.montra

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.montra.install.ApkDownloader
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicLong

/**
 * A retoma, provada a sério: semeia-se o `.part` com os primeiros megabytes — que é
 * exatamente o que uma transferência interrompida a meio deixa em disco — e verifica-se
 * que a transferência continua de lá e que o hash final cobre o ficheiro todo.
 *
 * O que distingue retomar de recomeçar é o primeiro progresso: retomando, o primeiro
 * pedaço chega depois dos bytes que já estavam em disco; recomeçando, chega logo no
 * princípio.
 */
@RunWith(AndroidJUnit4::class)
class DownloadResumeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as MontraApp).container

    @Test
    fun aPartialDownloadIsResumedInsteadOfRestarted() = runBlocking {
        container.indexRepository.load()
        val candidate = container.indexRepository.state.value.apps
            .mapNotNull { app -> app.bestAssetFor(listOf("arm64-v8a", "armeabi-v7a"))?.let { app to it } }
            .filter { (_, asset) -> asset.size in 15_000_000..40_000_000 }
            // O mais pequeno da janela: o teste não precisa de gastar 40 MB de dados de
            // ninguém para provar uma regra.
            .minByOrNull { (_, asset) -> asset.size }
            ?: error("o catálogo devia ter um APK entre 15 e 40 MB para este teste")
        val asset = candidate.second

        val downloader = ApkDownloader(context, OkHttpClient())
        val seedBytes = 2L * 1024 * 1024

        try {
            downloader.clear()
            val seeded = fetchPrefix(asset.url, seedBytes)
            assumeTrue(
                "o servidor não respondeu 206 ao pedido de prefixo; não há retoma para testar",
                seeded != null,
            )
            downloader.partFile(asset).writeBytes(seeded!!)

            val firstProgress = AtomicLong(-1)
            val result = downloader.download(asset) { progress ->
                firstProgress.compareAndSet(-1L, progress.bytes)
            }

            assertEquals("o tamanho final é o do índice", asset.size, result.file.length())
            assertEquals(
                "o hash do ficheiro retomado tem de bater certo com o índice",
                asset.sha256.lowercase(),
                result.sha256.lowercase(),
            )
            assertTrue(
                "devia retomar a partir de $seedBytes bytes, mas o primeiro progresso foi ${firstProgress.get()}",
                firstProgress.get() >= seedBytes,
            )
        } finally {
            downloader.clear()
        }
    }

    /** Os primeiros bytes do APK, por um pedido `Range` próprio. */
    private fun fetchPrefix(url: String, count: Long): ByteArray? {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-${count - 1}")
            .build()
        return OkHttpClient().newCall(request).execute().use { response ->
            if (response.code != 206) return null
            response.body?.bytes()
        }
    }
}
