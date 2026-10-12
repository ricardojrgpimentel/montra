package dev.montra.security

import java.security.PublicKey

/**
 * As chaves em que um build confia, e a decisão de aceitar um catálogo.
 *
 * Existe separado do [TrustStore] — que as vai buscar aos assets — para a decisão
 * poder ser testada sem aparelho, que é onde ela importa.
 *
 * **Porque é que há mais do que uma chave.** A chave de assinatura do índice está
 * fixada no APK, e uma chave fixada não se pode substituir sem uma atualização da app.
 * Com uma chave só, uma chave comprometida não tinha substituição possível: quem a
 * tivesse podia servir índices válidos a toda a base instalada, para sempre. Com uma
 * lista, a rotação passa a ser um procedimento — publica-se uma versão que aceite as
 * duas chaves, espera-se que a base instalada atualize, e só então se passa a assinar
 * com a nova.
 */
class TrustedKeys(private val keys: List<PublicKey>) {

    init {
        require(keys.isNotEmpty()) { "sem chaves de confiança" }
    }

    /** A que assina em condições normais. As outras só existem durante uma rotação. */
    val primary: PublicKey get() = keys.first()

    val primaryId: String by lazy { IndexVerifier.keyId(primary) }

    val ids: List<String> by lazy { keys.map { IndexVerifier.keyId(it) } }

    fun verify(indexBytes: ByteArray, signatureBase64: String, declaredKeyId: String?): SignatureCheck {
        // A principal primeiro: é a que assina fora de uma rotação, e assim o caso comum
        // não paga por o caso raro existir.
        val signer = keys.firstOrNull { IndexVerifier.verify(indexBytes, signatureBase64, it) }
            ?: return SignatureCheck.Invalid("a assinatura não corresponde a nenhuma chave de confiança")

        val signerId = IndexVerifier.keyId(signer)
        // O índice diz por que chave foi assinado. Se disser uma que não é a que assinou,
        // há aqui uma contradição — e uma contradição não se resolve escolhendo a versão
        // mais conveniente.
        if (declaredKeyId != null && declaredKeyId != signerId) {
            return SignatureCheck.KeyMismatch(declared = declaredKeyId, trusted = ids)
        }
        return SignatureCheck.Valid(signerId, primary = signerId == primaryId)
    }
}
