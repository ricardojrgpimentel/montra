package dev.openshelf.security

import dev.openshelf.util.sha256Hex
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Verification of the catalogue itself.
 *
 * The app ships one public key. index.json is only trusted if a detached ECDSA
 * P-256 / SHA-256 signature over its exact bytes verifies against that key. This
 * is what makes "no backend" safe: an attacker who controls DNS, a mirror, or the
 * repository host can serve whatever they like, and the app will refuse it.
 *
 * The signing key is deliberately a plain java.security EC key rather than
 * Ed25519: SHA256withECDSA works on every Android version this app supports with
 * no extra crypto dependency, and the signature format matches what Node's
 * crypto.sign() produces on the publishing side.
 */
object IndexVerifier {

    const val ALGORITHM = "SHA256withECDSA"

    /** Parses a PEM-encoded SubjectPublicKeyInfo (what tools/keys.mjs writes). */
    fun loadPublicKey(pem: String): PublicKey {
        val base64 = pem.lineSequence()
            .filterNot { it.trim().startsWith("-----") }
            .joinToString("")
            .trim()
        require(base64.isNotEmpty()) { "chave pública vazia" }
        val der = Base64.getDecoder().decode(base64)
        return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(der))
    }

    /**
     * Short identifier of a key: the first 16 bytes of SHA-256 over its SPKI
     * encoding. The index publishes the same value as signingKeyId, so a client
     * can tell "this was signed by the key I trust" without needing the key.
     */
    fun keyId(publicKey: PublicKey): String = sha256Hex(publicKey.encoded).take(32)

    fun verify(indexBytes: ByteArray, signatureBase64: String, publicKey: PublicKey): Boolean {
        val signature = try {
            Base64.getDecoder().decode(signatureBase64.trim())
        } catch (_: IllegalArgumentException) {
            return false
        }
        return try {
            Signature.getInstance(ALGORITHM).run {
                initVerify(publicKey)
                update(indexBytes)
                verify(signature)
            }
        } catch (_: Exception) {
            false
        }
    }
}

/** Result of checking a downloaded catalogue against the bundled key. */
sealed interface SignatureCheck {
    data class Valid(val keyId: String) : SignatureCheck

    data class Invalid(val reason: String) : SignatureCheck

    /** The index declares a key id that differs from the bundled key: refuse. */
    data class KeyMismatch(val declared: String, val bundled: String) : SignatureCheck
}
