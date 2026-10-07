package dev.montra.util

/**
 * Há quanto tempo, em palavras. Para a faixa do catálogo poder dizer quando é que o
 * servidor confirmou o índice sem obrigar ninguém a comparar datas.
 *
 * Separado da interface para poder ser testado: o texto de uma frase destas é
 * exactamente o género de coisa que fica errada numa borda e ninguém dá por isso.
 */
fun elapsedLabel(instant: Long?, now: Long): String {
    if (instant == null || instant <= 0L) return "nunca"
    val seconds = ((now - instant) / 1000L).coerceAtLeast(0L)
    return when {
        seconds < 45 -> "agora mesmo"
        seconds < 90 -> "há 1 minuto"
        seconds < 3600 -> "há ${seconds / 60} minutos"
        seconds < 5400 -> "há 1 hora"
        seconds < 86400 -> "há ${seconds / 3600} horas"
        seconds < 172800 -> "há 1 dia"
        else -> "há ${seconds / 86400} dias"
    }
}

/** "verificado há 4 minutos" / "nunca verificado". */
fun verifiedLabel(instant: Long?, now: Long): String =
    if (instant == null || instant <= 0L) "nunca verificado" else "verificado ${elapsedLabel(instant, now)}"
