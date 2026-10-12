package dev.montra.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferPolicyTest {

    @Test fun `uma rede medida com a barreira ligada segura a transferencia`() {
        assertTrue(TransferPolicy.holdForMeteredNetwork(wifiOnly = true, metered = true, allowMetered = false))
    }

    @Test fun `uma transferencia ja autorizada passa`() {
        assertFalse(TransferPolicy.holdForMeteredNetwork(wifiOnly = true, metered = true, allowMetered = true))
    }

    @Test fun `com a barreira desligada nada segura`() {
        assertFalse(TransferPolicy.holdForMeteredNetwork(wifiOnly = false, metered = true, allowMetered = false))
    }

    @Test fun `uma rede nao medida passa sempre`() {
        assertFalse(TransferPolicy.holdForMeteredNetwork(wifiOnly = true, metered = false, allowMetered = false))
    }

    @Test fun `com espaco de sobra nao falta nada`() {
        assertEquals(0L, TransferPolicy.missingBytes(requiredBytes = 10L * 1024 * 1024, freeBytes = 10L * 1024 * 1024 * 100))
    }

    @Test fun `o espaco exigido conta as duas copias e a margem`() {
        val size = 100L * 1024 * 1024
        // Cabe exactamente o dobro mais a margem: não falta nada.
        assertEquals(0L, TransferPolicy.missingBytes(size, freeBytes = size * 2 + TransferPolicy.HEADROOM_BYTES))
        // Um byte a menos já falta.
        assertEquals(1L, TransferPolicy.missingBytes(size, freeBytes = size * 2 + TransferPolicy.HEADROOM_BYTES - 1))
    }

    @Test fun `sem espaco nenhum falta o dobro mais a margem`() {
        val size = 50L * 1024 * 1024
        assertEquals(size * 2 + TransferPolicy.HEADROOM_BYTES, TransferPolicy.missingBytes(size, freeBytes = 0))
    }
}
