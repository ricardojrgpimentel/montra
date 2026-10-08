package dev.montra.util

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Meses sem lançamento a partir dos quais a app merece um aviso. */
const val STALE_RELEASE_MONTHS = 6L

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

/**
 * A data do último lançamento, em palavras: "23 de fevereiro de 2026".
 *
 * Na tabela da ficha a data sai em ISO, porque ali é metadado e compara-se de
 * relance; aqui é uma frase, e uma frase com "2026-02-23" lá dentro não se lê.
 */
fun releaseDateLabel(publishedAt: String?): String? {
    val instant = parseReleaseInstant(publishedAt) ?: return null
    return RELEASE_DATE_FORMAT.withZone(ZoneOffset.UTC).format(instant)
}

/**
 * O último lançamento foi há mais de [months] meses?
 *
 * Sem data, ou com uma data que não se percebe, a resposta é não: não se acusa uma
 * app de estar parada sem saber quando é que ela lançou. O limiar é estrito — seis
 * meses certos ainda não é "mais de seis meses".
 */
fun isStaleRelease(
    publishedAt: String?,
    now: Long,
    months: Long = STALE_RELEASE_MONTHS,
): Boolean {
    val instant = parseReleaseInstant(publishedAt) ?: return false
    val deadline = instant.atZone(ZoneOffset.UTC).plusMonths(months).toInstant()
    return deadline.toEpochMilli() < now
}

private val RELEASE_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("pt-PT"))

private fun parseReleaseInstant(publishedAt: String?): Instant? = try {
    publishedAt?.takeIf { it.isNotBlank() }?.let { OffsetDateTime.parse(it).toInstant() }
} catch (_: DateTimeParseException) {
    null
}

