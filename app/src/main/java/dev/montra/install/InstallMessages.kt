package dev.montra.install

import dev.montra.R
import dev.montra.util.UiText
import dev.montra.util.formatBytes

/**
 * O que se diz quando uma transferência é segurada *antes* de começar.
 *
 * Num sítio só, porque a notificação e a ficha têm de dizer exatamente a mesma coisa:
 * "faltam 812 MB" só é útil se for o mesmo número nos dois sítios.
 */
fun InstallState.holdReason(): UiText? = when (this) {
    InstallState.OnMeteredNetwork -> UiText.Resource(R.string.text_montra_only_downloads_over_wi_fi)
    is InstallState.NoSpace -> UiText.Resource(
        R.string.text_not_enough_space_1_s_free_on_the_phone,
        listOf(formatBytes(missingBytes)),
    )
    else -> null
}
