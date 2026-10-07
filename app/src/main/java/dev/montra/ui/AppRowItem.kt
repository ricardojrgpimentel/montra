package dev.montra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.montra.data.model.IndexApp
import dev.montra.install.InstallManager
import dev.montra.install.InstallState
import dev.montra.ui.components.AppIcon
import dev.montra.ui.components.Badge
import dev.montra.ui.components.SectionTitle
import dev.montra.ui.theme.Shapes
import dev.montra.ui.theme.Space
import dev.montra.util.formatBytes

/**
 * One app in a list. Shared by the Apps, Games and Search tabs so a row means the
 * same thing everywhere, and so there is one place to fix when it does not.
 *
 * The row is a single surface with no border and no shadow: either the edge or the
 * shadow defines a card, not both.
 */
@Composable
fun AppRowItem(
    row: AppRow,
    needsPermission: Boolean,
    onOpen: () -> Unit,
    onInstall: () -> Unit,
    onAuthorize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Shapes.row))
            .clickable(onClick = onOpen),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(Shapes.row),
    ) {
        Row(modifier = Modifier.padding(Space.md), verticalAlignment = Alignment.Top) {
            AppIcon(
                url = row.iconUrl,
                seed = row.app.packageName,
                text = row.app.name.take(1).uppercase(),
            )
            Spacer(Modifier.width(Space.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.app.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    text = row.app.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Space.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        // A versão como o APK a declara. Prefixar "v" dava "vv2.7" em
                        // quem já a publica com prefixo, e a loja não deve inventar
                        // identificadores de versão.
                        text = "${row.app.release?.versionName ?: "?"} · ${formatBytes(row.size)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val badge = statusBadge(row)
                    if (badge != null) {
                        Spacer(Modifier.width(Space.sm))
                        Badge(badge.first, badge.second)
                    }
                }
                InstallProgress(row)
            }
            Spacer(Modifier.width(Space.sm))
            InstallAction(
                row = row,
                needsPermission = needsPermission,
                onInstall = onInstall,
                onAuthorize = onAuthorize,
            )
        }
    }
}

/**
 * At most one badge per row: a line with four badges says nothing. The order is the
 * order of importance to the person deciding whether to install.
 */
private fun statusBadge(row: AppRow): Pair<String, androidx.compose.ui.graphics.Color>? = when {
    row.incompatible != null -> "não corre aqui" to androidx.compose.ui.graphics.Color(0xFFBA1A1A)
    row.signatureConflict -> "assinatura diferente" to androidx.compose.ui.graphics.Color(0xFFBA1A1A)
    // Uma licença restritiva é mais importante de saber do que a ausência na Play Store.
    row.app.hasRestrictedLicense() -> "licença restritiva" to androidx.compose.ui.graphics.Color(0xFF8A5A00)
    // Só o que o botão não consegue dizer. "atualizar" e "instalada" saíam também na
    // ação, à direita, e o mesmo texto repetido no mesmo contentor não acrescenta.
    row.app.playStore?.present == false -> "fora da Play" to androidx.compose.ui.graphics.Color(0xFF3B6470)
    else -> null
}

@Composable
private fun InstallProgress(row: AppRow) {
    when (val state = row.installState) {
        is InstallState.Downloading -> {
            Spacer(Modifier.height(Space.sm))
            LinearProgressIndicator(
                progress = { state.fraction },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        is InstallState.Failed -> {
            Spacer(Modifier.height(Space.xs))
            Text(
                text = state.reason.lineSequence().first(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        else -> Unit
    }
}

@Composable
private fun InstallAction(
    row: AppRow,
    needsPermission: Boolean,
    onInstall: () -> Unit,
    onAuthorize: () -> Unit,
) {
    if (row.incompatible != null) {
        // Nada a fazer aqui: dizer "Instalar" seria mentir.
        return
    }
    if (needsPermission && row.installState is InstallState.Idle) {
        // O botão passa a ser a solução, não um caminho para um erro.
        TextButton(onClick = onAuthorize) { Text("Autorizar") }
        return
    }
    when (row.installState) {
        is InstallState.Downloading, is InstallState.Verifying -> Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        }
        // O instalador do sistema está a pedir confirmação: a ação já não é nossa.
        InstallState.AwaitingUser -> Badge("no instalador", MaterialTheme.colorScheme.tertiary)
        InstallState.NeedsPermission -> TextButton(onClick = onAuthorize) { Text("Autorizar") }
        is InstallState.Installed -> Badge("instalada", MaterialTheme.colorScheme.primary)
        is InstallState.Failed -> TextButton(onClick = onInstall) { Text("Repetir") }
        InstallState.Idle -> when {
            !row.isInstalled -> TextButton(onClick = onInstall, enabled = row.canInstall) { Text("Instalar") }
            row.updateAvailable -> TextButton(onClick = onInstall, enabled = row.canInstall) { Text("Atualizar") }
            // Instalada e atual: a ação útil é abri-la, não repetir um distintivo.
            else -> {
                val context = LocalContext.current
                TextButton(
                    onClick = {
                        InstallManager.launchIntent(context, row.app.packageName)
                            ?.let { intent -> runCatching { context.startActivity(intent) } }
                    },
                ) { Text("Abrir") }
            }
        }
    }
}

/** Uma secção da lista: um título e as linhas que lhe pertencem. */
data class AppSectionSpec(
    val title: String,
    val rows: List<AppRow>,
    /** Pinta o título com a cor de atenção: só para o que exige uma decisão. */
    val accent: Boolean = false,
)

/**
 * Emite as secções como itens soltos na LazyColumn, em vez de um único item com uma
 * Column lá dentro.
 *
 * Não é uma questão de estilo: com uma Column, as quarenta linhas eram compostas
 * todas de uma vez, e o `animateItem` não tinha linhas para animar — uma lista que
 * salta quando se muda de filtro. Assim cada linha é um item, composta à medida do
 * scroll e animada para o lugar quando a lista muda.
 */
fun LazyListScope.appSections(
    sections: List<AppSectionSpec>,
    needsPermission: Boolean,
    onOpen: (IndexApp) -> Unit,
    onInstall: (IndexApp) -> Unit,
    onAuthorize: () -> Unit,
) {
    val visible = sections.filter { it.rows.isNotEmpty() }
    visible.forEachIndexed { index, section ->
        item(key = "section:${section.title}") {
            SectionTitle(
                text = section.title,
                trailing = section.rows.size.toString(),
                divider = index > 0,
                accent = section.accent,
                modifier = Modifier.padding(horizontal = Space.lg),
            )
        }
        items(items = section.rows, key = { it.app.id }) { row ->
            AppRowItem(
                row = row,
                needsPermission = needsPermission,
                onOpen = { onOpen(row.app) },
                onInstall = { onInstall(row.app) },
                onAuthorize = onAuthorize,
                modifier = Modifier
                    .padding(horizontal = Space.lg)
                    .animateItem(),
            )
        }
    }
}
