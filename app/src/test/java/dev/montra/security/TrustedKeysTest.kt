package dev.montra.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

/**
 * A decisão de confiança com mais do que uma chave — que é o que a rotação exige.
 *
 * Testada com chaves geradas e sem aparelho: o que aqui se prova é a *regra*, e a
 * regra é o que decide se uma chave comprometida tem ou não substituição.
 */
class TrustedKeysTest {

    private fun keyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()

    private fun sign(bytes: ByteArray, pair: KeyPair): String {
        val signature = Signature.getInstance(IndexVerifier.ALGORITHM)
        signature.initSign(pair.private)
        signature.update(bytes)
        return Base64.getEncoder().encodeToString(signature.sign())
    }

    private val payload = """{"schemaVersion":1,"apps":[]}""".toByteArray()

    @Test fun `a chave principal assina em condicoes normais`() {
        val primary = keyPair()
        val keys = TrustedKeys(listOf(primary.public))

        val check = keys.verify(payload, sign(payload, primary), IndexVerifier.keyId(primary.public))

        assertTrue(check is SignatureCheck.Valid)
        assertEquals(IndexVerifier.keyId(primary.public), (check as SignatureCheck.Valid).keyId)
        assertTrue(check.primary)
    }

    /** É isto que a rotação compra: um índice assinado com a chave nova continua a valer. */
    @Test fun `uma chave secundaria fidedigna e aceite e diz que nao e a principal`() {
        val primary = keyPair()
        val next = keyPair()
        val keys = TrustedKeys(listOf(primary.public, next.public))

        val check = keys.verify(payload, sign(payload, next), IndexVerifier.keyId(next.public))

        assertTrue(check is SignatureCheck.Valid)
        assertFalse("quem assinou não é a principal, e isso diz-se", (check as SignatureCheck.Valid).primary)
        assertEquals(IndexVerifier.keyId(next.public), check.keyId)
    }

    @Test fun `uma chave que nao esta na lista e recusada`() {
        val primary = keyPair()
        val stranger = keyPair()
        val keys = TrustedKeys(listOf(primary.public))

        val check = keys.verify(payload, sign(payload, stranger), IndexVerifier.keyId(stranger.public))

        assertTrue(check is SignatureCheck.Invalid)
    }

    /** O índice dizer uma chave e a assinatura ser de outra é uma contradição. */
    @Test fun `um key id declarado que nao e o de quem assinou e recusado`() {
        val primary = keyPair()
        val next = keyPair()
        val keys = TrustedKeys(listOf(primary.public, next.public))

        val check = keys.verify(payload, sign(payload, next), IndexVerifier.keyId(primary.public))

        assertTrue(check is SignatureCheck.KeyMismatch)
        assertEquals(IndexVerifier.keyId(primary.public), (check as SignatureCheck.KeyMismatch).declared)
        assertEquals(2, check.trusted.size)
    }

    @Test fun `sem key id declarado a assinatura vale pela chave que a fez`() {
        val primary = keyPair()
        val next = keyPair()
        val keys = TrustedKeys(listOf(primary.public, next.public))

        val check = keys.verify(payload, sign(payload, next), declaredKeyId = null)

        assertTrue(check is SignatureCheck.Valid)
        assertFalse((check as SignatureCheck.Valid).primary)
    }

    @Test fun `uma lista sem chaves nao se aceita`() {
        val error = runCatching { TrustedKeys(emptyList()) }.exceptionOrNull()
        assertTrue("sem chaves não há confiança nenhuma para dar", error is IllegalArgumentException)
    }
}
