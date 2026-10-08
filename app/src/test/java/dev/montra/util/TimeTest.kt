package dev.montra.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

/**
 * A faixa do catálogo diz "verificado há 4 minutos" em vez de mostrar uma data, e
 * este é o texto que uma pessoa lê para decidir se vale a pena puxar a lista. Uma
 * borda errada aqui é uma mentira pequena mas é uma mentira.
 */
class TimeTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    @Test
    fun `sem verificacao diz nunca`() {
        assertEquals("nunca", elapsedLabel(null, 1_000_000L))
        assertEquals("nunca verificado", verifiedLabel(null, 1_000_000L))
        assertEquals("nunca", elapsedLabel(0L, 1_000_000L))
    }

    @Test
    fun `nos primeiros segundos e agora mesmo`() {
        val now = 1_700_000_000_000L
        assertEquals("agora mesmo", elapsedLabel(now - 5_000L, now))
        assertEquals("agora mesmo", elapsedLabel(now - 44_000L, now))
    }

    @Test
    fun `um minuto so no singular`() {
        val now = 1_700_000_000_000L
        assertEquals("há 1 minuto", elapsedLabel(now - 60_000L, now))
        assertEquals("há 2 minutos", elapsedLabel(now - 2 * minute, now))
        assertEquals("há 59 minutos", elapsedLabel(now - 59 * minute, now))
    }

    @Test
    fun `horas e dias passam a singular na primeira unidade`() {
        val now = 1_700_000_000_000L
        assertEquals("há 1 hora", elapsedLabel(now - hour, now))
        assertEquals("há 5 horas", elapsedLabel(now - 5 * hour, now))
        assertEquals("há 1 dia", elapsedLabel(now - day, now))
        assertEquals("há 3 dias", elapsedLabel(now - 3 * day, now))
    }

    @Test
    fun `verificado prefixa o tempo`() {
        val now = 1_700_000_000_000L
        assertEquals("verificado há 1 hora", verifiedLabel(now - hour, now))
    }

    @Test
    fun `relogio adiantado nao da tempo negativo`() {
        // O instante pode vir de disco e o relógio do sistema andar para trás.
        val now = 1_700_000_000_000L
        assertEquals("agora mesmo", elapsedLabel(now + hour, now))
    }

    @Test
    fun `a data do lancamento sai em palavras`() {
        assertEquals("23 de fevereiro de 2026", releaseDateLabel("2026-02-23T14:05:11Z"))
        assertEquals("1 de janeiro de 2025", releaseDateLabel("2025-01-01T00:00:00Z"))
    }

    @Test
    fun `sem data nao ha data para mostrar`() {
        // Não se inventa uma data, e "null" nunca chega ao ecrã.
        assertEquals(null, releaseDateLabel(null))
        assertEquals(null, releaseDateLabel(""))
        assertEquals(null, releaseDateLabel("ontem"))
        assertEquals(null, releaseDateLabel("2026-02-31T00:00:00Z"))
    }

    @Test
    fun `parada so depois de seis meses`() {
        val agora = OffsetDateTime.parse("2026-10-08T12:00:00Z").toInstant().toEpochMilli()

        // Seis meses certos ainda não é "mais de seis meses".
        assertFalse(isStaleRelease("2026-04-08T12:00:00Z", agora))
        // Um segundo além dos seis meses já é.
        assertTrue(isStaleRelease("2026-04-08T11:59:59Z", agora))
        // E o que é antigo é antigo.
        assertTrue(isStaleRelease("2024-01-24T00:00:00Z", agora))
        // O que saiu no mês passado não é.
        assertFalse(isStaleRelease("2026-10-01T00:00:00Z", agora))
    }

    @Test
    fun `sem data nao se acusa ninguem de estar parado`() {
        val agora = OffsetDateTime.parse("2026-10-08T12:00:00Z").toInstant().toEpochMilli()
        assertFalse(isStaleRelease(null, agora))
        assertFalse(isStaleRelease("", agora))
        assertFalse(isStaleRelease("v1.2.3", agora))
    }

    @Test
    fun `o limiar dos seis meses esta num sitio so`() {
        // O DESIGN.md diz seis meses. Se o número mudar, muda aqui e o texto da
        // ficha vai atrás — mas tem de ser uma decisão, não um número solto.
        assertEquals(6L, STALE_RELEASE_MONTHS)
    }
}
