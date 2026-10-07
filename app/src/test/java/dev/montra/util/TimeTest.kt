package dev.montra.util

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
