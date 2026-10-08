package dev.montra.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.montra.data.RefreshOutcome
import dev.montra.ui.theme.Motion
import dev.montra.ui.theme.Space
import dev.montra.util.verifiedLabel
import kotlinx.coroutines.delay

/** Quanto tempo o resultado de uma verificação fica à vista antes de voltar ao "há X". */
private const val OUTCOME_LINGER_MS = 8_000L

/**
 * A faixa da montra: o estado do catálogo numa linha, sempre à vista.
 *
 * Existe porque a pergunta "isto está a atualizar-se?" não tinha resposta em lado
 * nenhum da lista — havia um ícone de refresh no canto, sem estado, que não dizia se
 * estava a fazer alguma coisa, e uma mensagem de erro que só aparecia nas
 * definições. A faixa responde às três perguntas ao mesmo tempo: o que é que este
 * catálogo tem, quando foi verificado pela última vez, e o que está a acontecer agora.
 *
 * É também a única superfície com a cor da casa em vez de um cinzento. A cor aqui
 * não é decorativa: enquanto a faixa estiver no verde da marca o catálogo está
 * confirmado, e passa a vermelho quando a última tentativa falhou. Quando o
 * utilizador entra na lista, a faixa recolhe — perde a cor e o relevo — para deixar
 * de competir com o conteúdo que ele foi ali ver.
 *
 * Estar offline não é uma falha — é a razão de ser do catálogo verificado em disco —
 * e por isso [offline] tem a sua própria cor: o cinzento calmo da faixa recolhida,
 * com o motivo e um "Tentar de novo" de baixo relevo. O vermelho fica para o que
 * exige atenção: uma assinatura inválida é um ataque ou uma build quebrada; não ter
 * rede é terça-feira.
 */
@Composable
fun CatalogueRibbon(
    counts: String,
    updates: Int,
    refreshing: Boolean,
    lastCheckedAt: Long?,
    outcome: RefreshOutcome,
    outcomeAt: Long,
    error: String?,
    offline: Boolean,
    onRetry: () -> Unit,
    collapsed: Boolean,
    modifier: Modifier = Modifier,
) {
    val now = rememberTickingNow()
    val scheme = MaterialTheme.colorScheme
    // `offline` também traz mensagem (é ela que se lê), mas não é uma falha: se ficasse
    // a contar como tal, a faixa ia para vermelho por não haver rede.
    val failed = error != null && !offline

    val band by animateColorAsState(
        targetValue = when {
            failed -> scheme.errorContainer
            offline -> scheme.surfaceContainerHigh
            collapsed -> scheme.surfaceContainerLow
            // Misturado com a superfície em vez de `primaryContainer` a todo o gás:
            // uma faixa da largura do ecrã com o contentor primário puro lia-se como
            // um aviso, não como o estado calmo de um catálogo confirmado.
            else -> lerp(scheme.surface, scheme.primaryContainer, 0.55f)
        },
        animationSpec = Motion.standard(),
        label = "ribbonBand",
    )
    val ink by animateColorAsState(
        targetValue = when {
            failed -> scheme.onErrorContainer
            offline -> scheme.onSurfaceVariant
            collapsed -> scheme.onSurfaceVariant
            else -> scheme.onPrimaryContainer
        },
        animationSpec = Motion.standard(),
        label = "ribbonInk",
    )

    Surface(modifier = modifier.fillMaxWidth(), color = band) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .padding(horizontal = Space.lg, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                failed -> {
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRetry) { Text("Tentar de novo", color = ink) }
                }

                // Sem alarme e sem esconder: o mesmo sítio da faixa, sem a cor de erro,
                // para se perceber num relance que o catálogo continua a funcionar.
                offline -> {
                    Text(
                        text = OFFLINE_RIBBON,
                        style = MaterialTheme.typography.labelMedium,
                        color = ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRetry) { Text("Tentar de novo", color = ink) }
                }

                else -> {
                    val accent = scheme.tertiary
                    Text(
                        text = buildAnnotatedString {
                            // O âmbar é o "há novidades": a única razão para esta linha
                            // merecer ser lida duas vezes.
                            if (updates > 0) {
                                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) {
                                    append(if (updates == 1) "1 atualização" else "$updates atualizações")
                                }
                                append(" · ")
                            }
                            append(counts)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(Space.md))
                    StatusText(
                        refreshing = refreshing,
                        outcome = outcome,
                        outcomeAt = outcomeAt,
                        lastCheckedAt = lastCheckedAt,
                        now = now,
                        color = if (refreshing) scheme.tertiary else ink,
                    )
                }
            }
        }
    }
}

/**
 * A frase da faixa quando não há rede. Curta de propósito: a faixa tem uma linha e
 * meio, e a segunda metade — "a mostrar o catálogo verificado" — é a informação que
 * interessa, porque diz que a app não está avariada.
 */
private const val OFFLINE_RIBBON = "Sem ligação à internet · a mostrar o catálogo verificado"

@Composable
private fun StatusText(
    refreshing: Boolean,
    outcome: RefreshOutcome,
    outcomeAt: Long,
    lastCheckedAt: Long?,
    now: Long,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (refreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = color,
            )
            Spacer(Modifier.width(Space.sm))
        }
        // O resultado de uma verificação diz-se enquanto é novidade; passados uns
        // segundos volta a ser só "quando é que isto foi confirmado".
        val fresh = outcomeAt > 0 && now - outcomeAt < OUTCOME_LINGER_MS
        val text = when {
            refreshing -> "a verificar…"
            fresh && outcome == RefreshOutcome.UPDATED -> "catálogo atualizado"
            fresh && outcome == RefreshOutcome.CURRENT -> "já estava atualizado"
            else -> verifiedLabel(lastCheckedAt, now)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * Um relógio que bate de 30 em 30 segundos, para "verificado há 4 minutos" não ficar
 * congelado enquanto o ecrã está aberto.
 */
@Composable
private fun rememberTickingNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = System.currentTimeMillis()
        }
    }
    return now
}
