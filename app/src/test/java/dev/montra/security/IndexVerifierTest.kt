package dev.montra.security

import dev.montra.util.fingerprintsMatch
import dev.montra.util.hexToColon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

/**
 * The trust anchor, tested against generated keys: what the app accepts, what it
 * refuses, and the exact shape of the identifier a client prints to a user.
 */
class IndexVerifierTest {

    private fun keyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()

    private fun sign(bytes: ByteArray, pair: KeyPair): String {
        val signature = Signature.getInstance(IndexVerifier.ALGORITHM)
        signature.initSign(pair.private)
        signature.update(bytes)
        return Base64.getEncoder().encodeToString(signature.sign())
    }

    private fun pem(pair: KeyPair): String {
        val base64 = Base64.getEncoder().encodeToString(pair.public.encoded)
        return "-----BEGIN PUBLIC KEY-----\n$base64\n-----END PUBLIC KEY-----\n"
    }

    @Test
    fun `assinatura válida é aceite`() {
        val pair = keyPair()
        val payload = """{"schemaVersion":1,"apps":[]}""".toByteArray()
        assertTrue(IndexVerifier.verify(payload, sign(payload, pair), IndexVerifier.loadPublicKey(pem(pair))))
    }

    @Test
    fun `um byte alterado invalida a assinatura`() {
        val pair = keyPair()
        val payload = """{"schemaVersion":1,"apps":[]}""".toByteArray()
        val signature = sign(payload, pair)
        payload[payload.size - 2] = (payload[payload.size - 2] + 1).toByte()
        assertFalse(IndexVerifier.verify(payload, signature, IndexVerifier.loadPublicKey(pem(pair))))
    }

    @Test
    fun `outra chave não valida a assinatura`() {
        val signer = keyPair()
        val attacker = keyPair()
        val payload = "conteúdo".toByteArray()
        assertFalse(
            IndexVerifier.verify(payload, sign(payload, signer), IndexVerifier.loadPublicKey(pem(attacker))),
        )
    }

    @Test
    fun `assinatura malformada não rebenta a app`() {
        val pair = keyPair()
        assertFalse(IndexVerifier.verify("x".toByteArray(), "não é base64!!", IndexVerifier.loadPublicKey(pem(pair))))
        assertFalse(IndexVerifier.verify("x".toByteArray(), Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)), IndexVerifier.loadPublicKey(pem(pair))))
    }

    @Test
    fun `key id é estável e depende da chave`() {
        val a = keyPair()
        val b = keyPair()
        val idA = IndexVerifier.keyId(a.public)
        assertEquals(32, idA.length)
        assertEquals(idA, IndexVerifier.keyId(IndexVerifier.loadPublicKey(pem(a))))
        assertNotEquals(idA, IndexVerifier.keyId(b.public))
    }

    @Test
    fun `comparação de fingerprints ignora maiúsculas e dois pontos`() {
        val withColons = "ab:cd:ef:01".hexToColon()
        assertTrue(fingerprintsMatch(withColons, "ABCDEF01"))
        assertTrue(fingerprintsMatch("ab:cd:ef:01", "ab:cd:ef:01"))
        assertFalse(fingerprintsMatch("ab:cd:ef:01", "ab:cd:ef:02"))
        assertFalse(fingerprintsMatch(null, "ab:cd:ef:01"))
    }
}
