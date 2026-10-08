package dev.montra.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

/**
 * A mensagem que a faixa mostra quando a verificação falha.
 *
 * Esta é a diferença entre a app dizer "não consegui ligar-me ao
 * raw.githubusercontent.com" — que é sobre o sítio errado e não se resolve em lado
 * nenhum — e dizer "sem ligação à internet", que é sobre o telemóvel de quem está a
 * ler e é a única coisa que ele pode resolver. Um erro técnico não chega ao ecrã, e
 * é isso que estes testes fixam.
 */
class RefreshFailureTest {

    private fun classify(error: Throwable, online: Boolean?, copy: Boolean = true) =
        RefreshFailure.classify(error, online, copy)

    @Test
    fun `sem rede a mensagem fala do telemovel e nao do servidor`() {
        val failure = classify(UnknownHostException("raw.githubusercontent.com"), online = false)

        assertTrue(failure is RefreshFailure.Offline)
        assertTrue(failure.message.startsWith("Sem ligação à internet"))
        assertTrue(failure.reportable)
        // A mensagem é sobre o estado do dispositivo, não sobre o host: o nome do
        // host não pode aparecer, senão estamos outra vez a acusar o GitHub.
        assertFalse(failure.message.contains("raw.githubusercontent.com"))
        assertFalse(failure.message.contains("nodename"))
    }

    @Test
    fun `sem rede e sem copia verificada a mensagem nao promete catalogo`() {
        val comCopia = classify(UnknownHostException("x"), online = false, copy = true)
        val semCopia = classify(UnknownHostException("x"), online = false, copy = false)

        assertTrue(comCopia.message.contains("catálogo verificado"))
        assertFalse(
            "não há catálogo nenhum para mostrar: prometê-lo seria falso",
            semCopia.message.contains("catálogo verificado"),
        )
    }

    @Test
    fun `com rede a falha e do servidor e nao da ligacao`() {
        val failure = classify(ConnectException("failed to connect"), online = true)

        assertTrue(failure is RefreshFailure.Server)
        assertFalse(failure.message.contains("Sem ligação"))
        assertTrue(failure.reportable)
    }

    @Test
    fun `timeout conta como rede e nao como servidor lento`() {
        val failure = classify(SocketTimeoutException("timeout"), online = false)

        assertTrue(failure is RefreshFailure.Offline)
    }

    @Test
    fun `erro de certificado nao se disfarca de offline`() {
        // A rede existe e o servidor falou: chamar-lhe "sem ligação à internet" era
        // mandar o utilizador procurar o problema no sítio errado.
        val failure = classify(SSLHandshakeException("chain validation failed"), online = true)

        assertTrue(failure is RefreshFailure.Tls)
        assertFalse(failure.message.contains("Sem ligação"))
        assertTrue(failure.reportable)
        // E a causa técnica não fica no ecrã: "chain validation failed" não é uma frase.
        assertFalse(failure.message.contains("chain"))
    }

    @Test
    fun `resposta HTTP e dita pelo codigo`() {
        val naoExiste = classify(IndexHttpException(404, "https://exemplo.test/index.json"), online = true)
        val avaria = classify(IndexHttpException(503, "https://exemplo.test/index.json"), online = true)
        val limite = classify(IndexHttpException(429, "https://exemplo.test/index.json"), online = true)

        assertTrue(naoExiste is RefreshFailure.Server)
        assertEquals("O catálogo não existe no endereço configurado", naoExiste.message)
        assertEquals("O servidor do catálogo está com problemas", avaria.message)
        assertTrue(limite.message.contains("limitar"))
        // O código é informação, não ruído: só os que têm frase própria é que o dispensam.
        val outro = classify(IndexHttpException(418, "https://exemplo.test/index.json"), online = true)
        assertTrue(outro.message.contains("418"))
    }

    @Test
    fun `sem permissao de rede a app nao afirma nada sobre a rede`() {
        // `online = null` é o sistema a não poder responder (permissão não dada).
        // Nesse caso a app não inventa: diz que não conseguiu verificar.
        val failure = classify(UnknownHostException("x"), online = null)

        assertTrue(failure is RefreshFailure.Unexplained)
        assertFalse(failure.message.contains("Sem ligação"))
    }

    @Test
    fun `erro desconhecido fica no log e nao no ecra`() {
        val failure = classify(IOException("<html>portal cativo</html>"), online = true)

        assertFalse("um HTML não é uma mensagem para o utilizador", failure.reportable)
        assertTrue(failure.reason.contains("portal cativo"))
    }

    @Test
    fun `a descricao tecnica nunca se perde`() {
        val failure = classify(UnknownHostException("raw.githubusercontent.com"), online = false)

        // Quem depura a partir de um log de release precisa do tipo e da mensagem.
        assertTrue(failure.reason.contains("UnknownHostException"))
        assertTrue(failure.reason.contains("raw.githubusercontent.com"))
        assertNotEquals(failure.reason, failure.message)
    }

    @Test
    fun `url invalida e dita ao utilizador`() {
        val failure = classify(
            IllegalArgumentException("o índice tem de ser servido por HTTPS"),
            online = true,
        )

        assertTrue(failure is RefreshFailure.InvalidIndexUrl)
        assertEquals("O endereço do catálogo não é válido (tem de ser HTTPS)", failure.message)
    }
}
