package dev.montra.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.montra.BuildConfig
import dev.montra.data.AutoRefresh
import dev.montra.data.IndexOrigin
import dev.montra.data.ThemeMode
import dev.montra.install.InstallManager
import dev.montra.ui.components.Block
import dev.montra.ui.components.KeyValue
import dev.montra.ui.components.LinkRow
import dev.montra.ui.components.MontraBrand
import dev.montra.ui.components.SectionTitle
import dev.montra.ui.theme.Space
import dev.montra.util.verifiedLabel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: UiState,
    themeMode: ThemeMode,
    onRefresh: () -> Unit,
    onSetIndexUrl: (String) -> Unit,
    onAuthorize: () -> Unit,
    onHideRestricted: (Boolean) -> Unit,
    onAutoRefresh: (AutoRefresh) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var draft by remember(state.index.indexUrl) { mutableStateOf(state.index.indexUrl) }
    val now = remember(state.index.lastCheckedAt) { System.currentTimeMillis() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Column(modifier = Modifier.padding(horizontal = Space.lg)) {
            SectionTitle("Aspeto", divider = false)
            Text(
                text = "A paleta é sempre a da Montra — o que se escolhe aqui é só se ela " +
                    "aparece clara ou escura. «Sistema» segue o que o telemóvel estiver a usar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.sm))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                ThemeMode.entries.forEach { option ->
                    FilterChip(
                        selected = themeMode == option,
                        onClick = { onThemeMode(option) },
                        label = { Text(option.label) },
                    )
                }
            }

            SectionTitle("Catálogo")
            Block {
                KeyValue("origem", originLabel(state.index.origin))
                KeyValue("verificado", verifiedLabel(state.index.lastCheckedAt, now))
                KeyValue("gerado em", state.index.generatedAt?.take(19)?.replace('T', ' ') ?: "—")
                KeyValue("apps", state.index.apps.size.toString())
                KeyValue("instaladas daqui", state.installedCount.toString())
                KeyValue("atualizações", state.updateCount.toString())
                KeyValue(
                    label = "assinatura",
                    value = if (state.index.signatureValid) "válida" else "recusada",
                    // O estado é do valor, não do rótulo: a linha inteira a vermelho
                    // faria da chave de confiança um erro também.
                    valueColor = if (state.index.signatureValid) {
                        Color.Unspecified
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                KeyValue("chave de confiança", state.index.keyId ?: "—", mono = true)
            }

            Spacer(Modifier.height(Space.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Button(onClick = onRefresh, enabled = !state.index.refreshing) {
                    Text(if (state.index.refreshing) "A atualizar…" else "Atualizar agora")
                }
                OutlinedButton(onClick = { onSetIndexUrl(BuildConfig.DEFAULT_INDEX_URL) }) {
                    Text("Repor URL")
                }
            }

            state.index.error?.let {
                Spacer(Modifier.height(Space.sm))
                // Offline não é vermelho: é a mesma condição calma que a faixa mostra.
                Text(
                    text = it,
                    color = if (state.index.offline) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.index.rejectedMessage?.let {
                Spacer(Modifier.height(Space.sm))
                Text(
                    "Índice recusado: $it",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            SectionTitle("Fonte do catálogo")
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("URL do index.json") },
                singleLine = false,
                supportingText = {
                    Text(
                        "Tem de ser HTTPS, e o index.json.sig tem de estar ao lado: um " +
                            "índice sem assinatura que a app reconheça é recusado.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
            Spacer(Modifier.height(Space.sm))
            Button(
                onClick = { onSetIndexUrl(draft.trim()) },
                enabled = draft.trim().startsWith("https://") && draft.trim() != state.index.indexUrl,
            ) { Text("Usar este catálogo") }
            Spacer(Modifier.height(Space.sm))
            Text(
                text = "Qualquer URL serve: um fork do catálogo é uma loja nova, com a sua " +
                    "própria chave de confiança.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle("Verificação automática")
            Text(
                text = "O catálogo é um ficheiro num repositório: não há servidor para nos " +
                    "avisar quando sai uma versão nova. A app pergunta sozinha, com a lista " +
                    "aberta, e usa o ETag — quando nada mudou, custa uma resposta de uns bytes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.sm))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                AutoRefresh.entries.forEach { option ->
                    FilterChip(
                        selected = state.autoRefresh == option,
                        onClick = { onAutoRefresh(option) },
                        label = { Text(option.label) },
                    )
                }
            }

            SectionTitle("Instalação")
            Block {
                Text(
                    if (state.canInstallPackages) {
                        "A Montra está autorizada a instalar aplicações. Cada instalação " +
                            "continua a passar pelo diálogo do Android."
                    } else {
                        "O Android ainda não autorizou a Montra a instalar aplicações " +
                            "desconhecidas. Sem isto, nenhum download começa."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!state.canInstallPackages) {
                    Spacer(Modifier.height(Space.sm))
                    OutlinedButton(onClick = onAuthorize) { Text("Abrir permissões") }
                }
            }

            SectionTitle("Licenças")
            Block {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Esconder apps com licença restritiva",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "${state.filterCounts[AppFilter.RESTRICTED] ?: 0} no catálogo. Código " +
                                "público, mas com limitações de uso — nunca são apresentadas como " +
                                "software livre.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.hideRestricted, onCheckedChange = onHideRestricted)
                }
            }

            SectionTitle("Como funciona")
            Text(
                "Este catálogo não tem servidor. A app descarrega um ficheiro JSON de um repositório git, " +
                    "verifica a assinatura digital com a chave pública incluída na app, e a seguir verifica " +
                    "cada APK por SHA-256 e pelo certificado de assinatura antes de o entregar ao instalador " +
                    "do Android. Se qualquer verificação falhar, nada é instalado.",
                style = MaterialTheme.typography.bodySmall,
            )

            SectionTitle("Sobre")
            Block {
                MontraBrand()
                Spacer(Modifier.height(Space.xs))
                // A versão e o código ficam juntos porque é isto que se lê em voz alta
                // quando se reporta um problema, e é a única coisa nesta app que o
                // utilizador não consegue ver sem vir aqui.
                KeyValue("versão", BuildConfig.VERSION_NAME)
                KeyValue("versionCode", BuildConfig.VERSION_CODE.toString())
                KeyValue("licença", "AGPL-3.0-or-later")
                Spacer(Modifier.height(Space.sm))
                Text(
                    text = "Código aberto. O catálogo é dados em git: contribuir é abrir um " +
                        "pull request.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(Space.sm))
            LinkRow("Site", "https://ricardopimen.tel", onOpenUrl)
            LinkRow("Código", "https://github.com/ricardojrgpimentel/montra", onOpenUrl)
            LinkRow("Catálogo", "https://github.com/ricardojrgpimentel/montra-index", onOpenUrl)

            SectionTitle("Créditos")
            Block {
                Text("Feito por Ricardo Pimentel.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(Space.sm))
                Text(
                    text = "Interfaces com Jetpack Compose e Material 3, do Android Open " +
                        "Source Project; rede com OkHttp, da Square. Tudo sob Apache-2.0.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Space.sm))
                Text(
                    text = "Cada aplicação do catálogo pertence a quem a faz: os ícones e as " +
                        "capturas são re-alojados a partir do repositório dela, e a ficha liga " +
                        "lá. As regras de desenho partiram do catálogo Impeccable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinkRow("Regras de desenho", "https://impeccable.style/slop", onOpenUrl)
            }

            Spacer(Modifier.height(Space.xl))
        }
    }
}

private fun originLabel(origin: IndexOrigin?): String = when (origin) {
    IndexOrigin.NETWORK -> "descarregado agora"
    IndexOrigin.CACHED -> "cópia verificada em disco"
    IndexOrigin.BUNDLED -> "snapshot incluído na app"
    null -> "—"
}
