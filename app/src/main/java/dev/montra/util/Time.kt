package dev.montra.util

import dev.montra.R
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Meses sem lançamento a partir dos quais a app merece um aviso. */
const val STALE_RELEASE_MONTHS = 6L

/** Dias desde a entrada no catálogo a partir dos quais ela deixa de ser novidade. */
const val NEW_IN_CATALOGUE_DAYS = 30L

/**
 * Há quanto tempo, em palavras. Para a faixa do catálogo poder dizer quando é que o
 * servidor confirmou o índice sem obrigar ninguém a comparar datas.
 *
 * Separado da interface para poder ser testado: o texto de uma frase destas é
 * exactamente o género de coisa que fica errada numa borda e ninguém dá por isso.
 */
fun elapsedLabel(instant: Long?, now: Long): UiText {
    if (instant == null || instant <= 0L) return UiText.Resource(R.string.text_never)
    val seconds = ((now - instant) / 1000L).coerceAtLeast(0L)
    return when {
        seconds < 45 -> UiText.Resource(R.string.text_just_now)
        seconds < 90 -> UiText.Resource(R.string.text_1_minute_ago)
        seconds < 3600 -> UiText.Resource(R.string.text_1_s_minutes_ago, listOf(seconds / 60))
        seconds < 5400 -> UiText.Resource(R.string.text_1_hour_ago)
        seconds < 86400 -> UiText.Resource(R.string.text_1_s_hours_ago, listOf(seconds / 3600))
        seconds < 172800 -> UiText.Resource(R.string.text_1_day_ago)
        else -> UiText.Resource(R.string.text_1_s_days_ago, listOf(seconds / 86400))
    }
}

/** "verificado há 4 minutos" / "nunca verificado". */
fun verifiedLabel(instant: Long?, now: Long): UiText =
    if (instant == null || instant <= 0L) UiText.Resource(R.string.text_never_verified) else UiText.Resource(R.string.verified_time, listOf(elapsedLabel(instant, now)))

/**
 * A data do último lançamento, em palavras: "23 de fevereiro de 2026".
 *
 * Na tabela da ficha a data sai em ISO, porque ali é metadado e compara-se de
 * relance; aqui é uma frase, e uma frase com "2026-02-23" lá dentro não se lê.
 */
fun releaseDateLabel(publishedAt: String?, locale: Locale = Locale.getDefault()): String? {
    val instant = parseReleaseInstant(publishedAt) ?: return null
    return DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG).withLocale(locale).withZone(ZoneOffset.UTC).format(instant)
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

private fun parseReleaseInstant(publishedAt: String?): Instant? = try {
    publishedAt?.takeIf { it.isNotBlank() }?.let { OffsetDateTime.parse(it).toInstant() }
} catch (_: DateTimeParseException) {
    null
}

/**
 * A app entrou no catálogo há pouco?
 *
 * `addedAt` é uma data ("2026-10-07"), não um instante — mas aceita-se também o
 * instante completo, porque o índice já mudou de forma uma vez e não vale a pena
 * depender disso. Sem data não há novidade: uma entrada sem data não se anuncia como
 * nova, pela mesma razão que não se acusa uma app de estar parada sem saber quando
 * lançou.
 *
 * Trinta dias é generoso de propósito: o catálogo cresce por pull requests, e uma
 * semana era curto para alguém que abre a loja ao fim de semana.
 */
fun isNewInCatalogue(addedAt: String?, now: Long, days: Long = NEW_IN_CATALOGUE_DAYS): Boolean {
    val date = parseCatalogueDate(addedAt) ?: return false
    val deadline = date.plusDays(days).atStartOfDay(ZoneOffset.UTC).toInstant()
    return deadline.toEpochMilli() > now
}

private fun parseCatalogueDate(addedAt: String?): LocalDate? {
    val text = addedAt?.takeIf { it.isNotBlank() } ?: return null
    return try {
        LocalDate.parse(text)
    } catch (_: DateTimeParseException) {
        try {
            OffsetDateTime.parse(text).toLocalDate()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

