package dev.openshelf.security

import android.content.Context
import java.security.PublicKey

/**
 * The set of keys this build trusts to sign a catalogue.
 *
 * Rotation is deliberately manual: adding a key means shipping an app update.
 * That is the same trade-off F-Droid makes, and it is the reason a compromised
 * index cannot be replaced silently by anyone who cannot also push an app update.
 */
class TrustStore(private val context: Context) {

    val publicKey: PublicKey by lazy {
        IndexVerifier.loadPublicKey(context.assets.open(PUBLIC_KEY_ASSET).bufferedReader().use { it.readText() })
    }

    val keyId: String by lazy { IndexVerifier.keyId(publicKey) }

    fun verify(indexBytes: ByteArray, signatureBase64: String, declaredKeyId: String?): SignatureCheck {
        if (!IndexVerifier.verify(indexBytes, signatureBase64, publicKey)) {
            return SignatureCheck.Invalid("assinatura não corresponde à chave pública incluída na app")
        }
        if (declaredKeyId != null && declaredKeyId != keyId) {
            return SignatureCheck.KeyMismatch(declared = declaredKeyId, bundled = keyId)
        }
        return SignatureCheck.Valid(keyId)
    }

    companion object {
        const val PUBLIC_KEY_ASSET = "index-signing.pub.pem"
    }
}
