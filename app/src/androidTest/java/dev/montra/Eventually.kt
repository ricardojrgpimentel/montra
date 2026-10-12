package dev.montra

import org.junit.Assert.fail

/**
 * Espera que algo do sistema se torne verdade, até um limite, em vez de assumir que
 * ficou verdade no instante seguinte ao pedido.
 *
 * `abandonSession` e `cancel` são pedidos assíncronos ao sistema. A asserção imediata
 * passa quando a máquina está calma e falha quando a suite a carrega — e um teste que
 * só passa às vezes não é um teste, é uma armadilha para quem o correr a seguir.
 */
internal fun eventually(
    message: String,
    timeoutMillis: Long = 3_000,
    condition: () -> Boolean,
) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (System.currentTimeMillis() < deadline) {
        if (condition()) return
        Thread.sleep(50)
    }
    fail(message)
}
