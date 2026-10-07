package dev.openshelf.data

import dev.openshelf.data.model.IndexApp
import dev.openshelf.data.model.IndexFile
import dev.openshelf.data.model.IndexJson
import dev.openshelf.security.IndexVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end check of the publish → consume chain, using the *real* catalogue
 * bytes that ship with the app.
 *
 * This is the test that matters most in the whole project: it proves that what
 * tools/sign-index.mjs produced on a build machine is accepted by the same
 * verification code the app runs on a phone. If someone changes the signature
 * algorithm, the JSON encoding or the key format on either side, this fails.
 */
class RealIndexTest {

    private fun resource(name: String): ByteArray =
        javaClass.getResourceAsStream("/$name")?.readBytes()
            ?: error("recurso de teste em falta: $name (corre scripts/sync-index-assets.sh)")

    @Test
    fun `o catalogo incluido na app tem assinatura valida`() {
        val bytes = resource("index.json")
        val signature = resource("index.json.sig").decodeToString().trim()
        val publicKey = IndexVerifier.loadPublicKey(resource("index-signing.pub.pem").decodeToString())

        assertTrue(
            "a assinatura do catálogo incluído na app não verifica — o build está partido",
            IndexVerifier.verify(bytes, signature, publicKey),
        )
    }

    @Test
    fun `o key id do indice corresponde a chave incluida`() {
        val bytes = resource("index.json")
        val index = IndexJson.decodeFromString(IndexFile.serializer(), bytes.decodeToString())
        val publicKey = IndexVerifier.loadPublicKey(resource("index-signing.pub.pem").decodeToString())

        assertNotNull("o índice tem de declarar signingKeyId", index.signingKeyId)
        assertEquals(index.signingKeyId, IndexVerifier.keyId(publicKey))
    }

    @Test
    fun `bytes adulterados sao recusados`() {
        val bytes = resource("index.json").copyOf()
        val signature = resource("index.json.sig").decodeToString().trim()
        val publicKey = IndexVerifier.loadPublicKey(resource("index-signing.pub.pem").decodeToString())

        // Flip a byte inside a download URL: exactly the attack the signature stops.
        val marker = "https://github.com".toByteArray()
        var index = -1
        outer@ for (i in 0..bytes.size - marker.size) {
            for (j in marker.indices) if (bytes[i + j] != marker[j]) continue@outer
            index = i
            break
        }
        assertTrue("o catálogo real devia conter um URL do GitHub", index >= 0)
        bytes[index + 8] = 'X'.code.toByte()

        assertFalse(IndexVerifier.verify(bytes, signature, publicKey))
    }

    @Test
    fun `o indice real tem apps completas e instalaveis`() {
        val index = IndexJson.decodeFromString(
            IndexFile.serializer(),
            resource("index.json").decodeToString(),
        )
        assertEquals(1, index.schemaVersion)
        assertTrue("esperava um catálogo com dezenas de apps", index.apps.size >= 15)

        val arm64 = listOf("arm64-v8a", "armeabi-v7a")
        for (app in index.apps) {
            assertNotNull("${app.id}: sem release", app.release)
            assertTrue("${app.id}: sem assets", app.release!!.assets.isNotEmpty())
            val asset = app.bestAssetFor(arm64)
            assertNotNull("${app.id}: nenhum APK compatível com arm64", asset)
            assertEquals("${app.id}: sha256 inválido", 64, asset!!.sha256.length)
            assertTrue("${app.id}: tamanho inválido", asset.size > 0)
            assertTrue("${app.id}: URL não é https", asset.url.startsWith("https://"))
            assertTrue("${app.id}: todos os APKs deviam ter certificado fixado", app.signingCertSha256 != null)
            assertEquals("${app.id}: pin do certificado difere do asset", app.signingCertSha256, asset.signingCertSha256)
            assertTrue("${app.id}: ícone devia ser um caminho relativo", app.icon == null || !app.icon!!.startsWith("http"))
        }
    }

    @Test
    fun `apps so fora da play store sao identificadas`() {
        val index = IndexJson.decodeFromString(
            IndexFile.serializer(),
            resource("index.json").decodeToString(),
        )
        val offPlay = index.apps.filter { it.playStore?.present == false }
        assertTrue(
            "o ponto do projeto é haver apps que não estão na Play Store; encontrei ${offPlay.size}",
            offPlay.size >= 10,
        )
    }
}

/** Model-level behaviour: defaults, tolerance, and ABI selection. */
class IndexModelTest {

    private fun app(json: String): IndexApp = IndexJson.decodeFromString(IndexApp.serializer(), json)

    @Test
    fun `campos desconhecidos sao ignorados`() {
        val parsed = app(
            """{"id":"x","name":"X","summary":"resumo","packageName":"a.b.c","license":"MIT","sourceCode":"https://example.com","campoNovo":123}""",
        )
        assertEquals("x", parsed.id)
        assertEquals("active", parsed.status)
        assertTrue(parsed.release == null)
    }

    @Test
    fun `descricao cai para ingles quando falta o idioma`() {
        val parsed = app(
            """{"id":"x","name":"X","summary":"s","packageName":"a.b.c","license":"MIT","sourceCode":"https://e.com","description":{"en":"hello","pt":"olá"}}""",
        )
        assertEquals("olá", parsed.descriptionFor("pt"))
        assertEquals("olá", parsed.descriptionFor("pt-BR"))
        assertEquals("hello", parsed.descriptionFor("de"))
        assertEquals("hello", parsed.descriptionFor(null))
    }

    @Test
    fun `escolha de asset prefere a abi do dispositivo e depois universal`() {
        val parsed = app(
            """
            {"id":"x","name":"X","summary":"s","packageName":"a.b.c","license":"MIT","sourceCode":"https://e.com",
             "release":{"versionName":"1","versionCode":1,"tag":"v1","assets":[
               {"abi":"armeabi-v7a","url":"https://e.com/a.apk","sha256":"${"a".repeat(64)}","size":1},
               {"abi":"universal","url":"https://e.com/u.apk","sha256":"${"b".repeat(64)}","size":2},
               {"abi":"arm64-v8a","url":"https://e.com/64.apk","sha256":"${"c".repeat(64)}","size":3}]}}
            """,
        )
        assertEquals("arm64-v8a", parsed.bestAssetFor(listOf("arm64-v8a", "armeabi-v7a"))?.abi)
        assertEquals("armeabi-v7a", parsed.bestAssetFor(listOf("armeabi-v7a"))?.abi)
        assertEquals("universal", parsed.bestAssetFor(listOf("x86"))?.abi)
        assertEquals("universal", parsed.bestAssetFor(emptyList())?.abi)
    }

    @Test
    fun `sem asset para a abi do dispositivo nao ha sugestao`() {
        val parsed = app(
            """
            {"id":"x","name":"X","summary":"s","packageName":"a.b.c","license":"MIT","sourceCode":"https://e.com",
             "release":{"versionName":"1","versionCode":1,"tag":"v1","assets":[
               {"abi":"x86_64","url":"https://e.com/x.apk","sha256":"${"d".repeat(64)}","size":1}]}}
            """,
        )
        assertNull(parsed.bestAssetFor(listOf("arm64-v8a")))
    }
}
