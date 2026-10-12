package dev.montra.security

import android.content.Context
import java.security.PublicKey

/**
 * As chaves de confiança deste build, lidas dos assets.
 *
 * A principal é `index-signing.pub.pem`; as outras — `index-signing-<nome>.pub.pem` —
 * existem só para a rotação. O procedimento e o porquê estão em [TrustedKeys].
 *
 * Aqui só há chaves públicas. A privada nunca esteve no APK, e uma chave a mais nesta
 * lista é uma chave a mais em que se confia — é por isso que acrescentar uma aparece
 * num diff e se revê.
 */
class TrustStore(private val context: Context) {

    val trusted: TrustedKeys by lazy { TrustedKeys(loadKeys(context)) }

    /** A chave que assina em condições normais. */
    val keyId: String get() = trusted.primaryId

    fun verify(indexBytes: ByteArray, signatureBase64: String, declaredKeyId: String?): SignatureCheck =
        trusted.verify(indexBytes, signatureBase64, declaredKeyId)

    companion object {
        const val PRIMARY_KEY_ASSET = "index-signing.pub.pem"

        /** `index-signing.pub.pem` e `index-signing-<nome>.pub.pem`. */
        private val KEY_NAME = Regex("""index-signing(-[A-Za-z0-9._-]+)?\.pub\.pem""")

        /**
         * A principal primeiro, e as outras por ordem de nome. A ordem importa: a
         * verificação tenta-as assim, e o caso comum — a principal — não deve pagar por
         * o caso raro existir.
         */
        private fun loadKeys(context: Context): List<PublicKey> {
            val names = context.assets.list("").orEmpty().filter { KEY_NAME.matches(it) }
            val ordered = buildList {
                if (PRIMARY_KEY_ASSET in names) add(PRIMARY_KEY_ASSET)
                addAll(names.filter { it != PRIMARY_KEY_ASSET }.sorted())
            }
            require(ordered.isNotEmpty()) {
                "nenhuma chave de confiança nos assets (esperava $PRIMARY_KEY_ASSET)"
            }
            return ordered.map { name ->
                IndexVerifier.loadPublicKey(
                    context.assets.open(name).bufferedReader().use { it.readText() },
                )
            }
        }
    }
}
