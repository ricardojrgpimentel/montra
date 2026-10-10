package dev.montra.data

import dev.montra.R
import dev.montra.util.UiText
import dev.montra.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * A falha de uma verificação, já traduzida para o que a pessoa pode fazer.
 *
 * Antes disto, a mensagem que chegava ao ecrã era a do OkHttp —
 * `UnknownHostException: raw.githubusercontent.com: nodename nor servname provided`
 * — que fala do sítio errado: o problema é não haver rede neste telemóvel, não o
 * GitHub. Pior: `IOException: timeout` não distingue "estás offline" de "o servidor
 * está lento", e as duas coisas pedem ações diferentes.
 *
 * A decisão importante é [reportable]. Só mensagens que nós escrevemos vão para o
 * ecrã; tudo o resto (o HTML de um portal cativo, um código do OkHttp) fica na
 * [reason] e no log. Um erro técnico à frente de quem quer instalar uma app não é
 * informação: é ruído com ar de avaria.
 */
sealed interface RefreshFailure {

    /** O que se pode dizer a quem está a ler. */
    val message: UiText

    /** Como registar a falha no log, com a causa técnica. */
    val reason: String

    /** Se [message] é texto nosso — e portanto digno de aparecer no ecrã. */
    val reportable: Boolean

    /** O telemóvel não tem rede. Não é uma avaria: nada a resolver do lado do servidor. */
    data class Offline(
        override val reason: String,
        val hasVerifiedCopy: Boolean,
    ) : RefreshFailure {
        override val message: UiText = if (hasVerifiedCopy) {
            UiText.Resource(R.string.text_no_internet_connection_showing_verified_catalogue)
        } else {
            UiText.Resource(R.string.text_no_internet_connection)
        }
        override val reportable: Boolean = true
    }

    /** O servidor respondeu, e a resposta não serve. */
    data class Server(
        override val message: UiText,
        override val reason: String,
    ) : RefreshFailure {
        override val reportable: Boolean = true
    }

    /** A URL do índice não serve — dito ao utilizador, porque é ele que a escreveu. */
    data class InvalidIndexUrl(override val reason: String) : RefreshFailure {
        override val message: UiText = UiText.Resource(R.string.text_the_catalogue_address_is_invalid_https_required)
        override val reportable: Boolean = true
    }

    /**
     * A rede existe e o servidor falou, mas não de forma fiável — um certificado
     * inválido, um aperto de mão que não se completou. Não é offline, e é por isso que
     * tem texto próprio: chamar-lhe "sem ligação à internet" mandava a pessoa procurar
     * o problema no sítio errado.
     */
    data class Tls(override val reason: String) : RefreshFailure {
        override val message: UiText = UiText.Resource(R.string.text_could_not_establish_a_secure_connection_to)
        override val reportable: Boolean = true
    }

    /** Nada que se possa afirmar com verdade: só o log fica a saber o que se passou. */
    data class Unexplained(override val reason: String) : RefreshFailure {
        override val message: UiText = UiText.Resource(R.string.text_could_not_verify_the_catalogue)
        override val reportable: Boolean = false
    }

    companion object {

        /**
         * Classifica uma exceção que abortou a verificação.
         *
         * @param online o que o sistema diz da rede. `null` quando a permissão
         *   ACCESS_NETWORK_STATE não está dada: nesse caso a app não afirma nada
         *   sobre a rede e cai no texto genérico — é a resposta honesta para uma
         *   pergunta que não pode ser feita.
         */
        fun classify(
            error: Throwable,
            online: Boolean?,
            hasVerifiedCopy: Boolean,
        ): RefreshFailure {
            val reason = describe(error)
            return when {
                error is IndexHttpException -> Server(
                    message = error.code.describe(),
                    reason = reason,
                )

                // Vem do OkHttp e é sobre a rede que existe entre o telemóvel e o
                // servidor — a única família de erros que a pergunta "estás online?"
                // consegue separar em duas mensagens diferentes.
                isReachability(error) -> when (online) {
                    false -> Offline(reason, hasVerifiedCopy)
                    true -> Server(
                        message = UiText.Resource(R.string.text_the_catalogue_server_did_not_respond),
                        reason = reason,
                    )
                    null -> Unexplained(reason)
                }

                error is SSLException -> Tls(reason)

                // URL inválida: quem a escreveu foi o utilizador, e a mensagem do
                // IndexSource já é dirigida a ele.
                error is IllegalArgumentException && error.message?.startsWith("o índice") == true ->
                    InvalidIndexUrl(reason)

                else -> Unexplained(reason)
            }
        }

        /** A falha é de rede? Decide só o *texto*, nunca o resultado da verificação. */
        fun isReachability(error: Throwable): Boolean = when {
            error is UnknownHostException -> true
            error is ConnectException -> true
            error is NoRouteToHostException -> true
            error is PortUnreachableException -> true
            error is SocketTimeoutException -> true
            // A rede existe: o aperto de mão é que não se completou.
            error is SSLException -> false
            error is IOException -> false
            else -> false
        }

        /** A causa nunca se perde: em release não há stack traces fiáveis, mas a linha fica. */
        private fun describe(error: Throwable): String {
            val detail = error.message?.takeIf { it.isNotBlank() } ?: "(sem mensagem)"
            val line = "${error::class.java.simpleName}: $detail"
            return if (BuildConfig.DEBUG) "$line\n${error.stackTraceToString()}" else line
        }

        private fun Int.describe(): UiText = when (this) {
            // Os códigos que merecem uma frase. O resto é só o número: é honesto e
            // diz mais a quem depura do que "houve um erro".
            404 -> UiText.Resource(R.string.text_the_catalogue_does_not_exist_at_the)
            403 -> UiText.Resource(R.string.text_the_server_refused_the_catalogue_request)
            429 -> UiText.Resource(R.string.text_the_server_is_limiting_requests_try_again)
            in 500..599 -> UiText.Resource(R.string.text_the_catalogue_server_is_having_problems)
            else -> UiText.Resource(R.string.text_the_server_returned_1_s_for_the, listOf(this))
        }
    }
}

/**
 * O servidor respondeu, mas não com o que era preciso. Tem tipo próprio — e não um
 * `IOException` com "HTTP 404" na mensagem — para que a classificação acima possa
 * reconhecê-lo sem andar a ler texto, e para o `code` chegar à mensagem sem parsing.
 */
class IndexHttpException(val code: Int, url: String) : IOException("HTTP $code em ${url.label()}")

/**
 * O que fica no log. Não inclui o host: a URL do índice é configurável, e um caminho
 * identifica a fonte sem arrastar domínios para um registo.
 */
private fun String.label(): String =
    toHttpUrlOrNull()?.encodedPath?.takeIf { it.isNotEmpty() } ?: this
