package dev.montra

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.montra.data.IndexOrigin
import dev.montra.data.IndexRepository
import dev.montra.data.IndexSource
import dev.montra.data.Settings
import dev.montra.security.SignatureCheck
import dev.montra.security.TrustStore
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * The network path, on a real device, in the app's own process.
 *
 * The unit tests prove the crypto works on a JVM. This proves the whole chain
 * works where it matters: the app's UID fetches the live catalogue over HTTPS,
 * verifies the signature with the key baked into the APK, parses it, and only
 * then writes it to disk.
 *
 * It hits the real URL from BuildConfig, so it needs connectivity. Run it with:
 *
 *   ./gradlew :app:connectedDebugAndroidTest
 *
 * Note for CI: this is why an emulator job needs network access, and why it is a
 * separate test class from the offline unit tests.
 */
@RunWith(AndroidJUnit4::class)
class CatalogueNetworkSmokeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var source: IndexSource
    private lateinit var trust: TrustStore
    private lateinit var repository: IndexRepository

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Before
    fun setUp() {
        source = IndexSource(context, client)
        trust = TrustStore(context)
        repository = IndexRepository(context, source, Settings(context), trust)
        // Start from a clean slate so "it was written" means something.
        source.cachedIndexFile.delete()
        source.cachedSignatureFile.delete()
    }

    @Test
    fun fetchVerifyAndStore() = runBlocking {
        val url = BuildConfig.DEFAULT_INDEX_URL

        val payload = source.fetchRemote(url, etag = null)
        assertNotNull("o índice tem de ser descarregável de $url", payload)
        assertTrue("o índice parece truncado (${payload!!.bytes.size} bytes)", payload.bytes.size > 50_000)

        // 1. the signature verifies against the key inside this APK
        val check = trust.verify(
            indexBytes = payload.bytes,
            signatureBase64 = payload.signature,
            declaredKeyId = null,
        )
        assertTrue(
            "a assinatura do índice publicado não verifica: $check",
            check is SignatureCheck.Valid,
        )

        // 2. the repository ends up serving network data, not the bundled snapshot
        repository.refresh(force = true)
        val state = repository.state.value
        assertEquals(IndexOrigin.NETWORK, state.origin)
        assertTrue("esperava um catálogo com apps, obtive ${state.apps.size}", state.apps.size >= 20)
        assertTrue("o índice devia estar marcado como verificado", state.signatureValid)
        assertEquals("o key id do índice tem de ser o da chave incluída", trust.keyId, state.keyId)

        // 3. and it only landed on disk because it verified, byte for byte
        assertTrue("o índice verificado devia estar em cache", source.cachedIndexFile.isFile)
        assertEquals(
            "os bytes em cache têm de ser exatamente os que foram verificados",
            payload.bytes.sha256(),
            source.cachedIndexFile.readBytes().sha256(),
        )

        // 4. every indexed app is installable in principle: https, sha256, certificate
        for (app in state.apps) {
            val asset = app.bestAssetFor(listOf("arm64-v8a", "armeabi-v7a"))
            assertNotNull("${app.id}: sem APK compatível", asset)
            assertTrue("${app.id}: URL não é https", asset!!.url.startsWith("https://"))
            assertEquals("${app.id}: sha256 malformado", 64, asset.sha256.length)
            assertNotNull("${app.id}: sem certificado fixado", app.signingCertSha256)
        }
    }

    @Test
    fun tamperedIndexIsRejected() = runBlocking {
        val good = source.fetchRemote(BuildConfig.DEFAULT_INDEX_URL, etag = null)!!

        // Flip a byte inside the first download URL: the attack the signature stops.
        val tampered = good.bytes.copyOf()
        val marker = "https://github.com".toByteArray()
        val at = indexOf(tampered, marker)
        assertTrue("o catálogo devia conter URLs do GitHub", at >= 0)
        tampered[at + 8] = 'X'.code.toByte()

        // 1. the verifier refuses it outright
        val check = trust.verify(tampered, good.signature, declaredKeyId = null)
        assertTrue("bytes adulterados NUNCA podem verificar", check !is SignatureCheck.Valid)

        // 2. and a repository fed that response must publish nothing and say why
        val hostile = object : IndexSource(context, client) {
            override suspend fun fetchRemote(indexUrl: String, etag: String?): Payload =
                Payload(bytes = tampered, signature = good.signature, etag = null, fromNetwork = true)
        }
        val victim = IndexRepository(context, hostile, Settings(context), trust)
        victim.refresh(force = true)

        val state = victim.state.value
        assertTrue("nada pode ser publicado a partir de um índice adulterado", state.apps.isEmpty())
        assertFalse("o estado não pode dizer que a assinatura é válida", state.signatureValid)
        assertNotNull("a recusa tem de ser explicada ao utilizador", state.rejectedMessage)
        assertFalse("um índice recusado nunca vai para disco", source.cachedIndexFile.isFile)
    }

    /** The bundled snapshot must verify in-process too, for the offline first run. */
    @Test
    fun bundledSnapshotVerifiesOffline() {
        val payload = source.bundled()
        val check = trust.verify(payload.bytes, payload.signature, declaredKeyId = null)
        assertTrue("o snapshot incluído no APK não verifica: $check", check is SignatureCheck.Valid)
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

}
