package dev.montra.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.montra.BuildConfig
import dev.montra.data.IndexOrigin
import dev.montra.install.InstallManager
import dev.montra.ui.components.KeyValue
import dev.montra.ui.components.SectionTitle

@Composable
fun SettingsScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onSetIndexUrl: (String) -> Unit,
    onAuthorize: () -> Unit,
    onHideRestricted: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var draft by remember(state.index.indexUrl) { mutableStateOf(state.index.indexUrl) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        SectionTitle("Catálogo")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(12.dp)) {
                KeyValue("origem", originLabel(state.index.origin))
                KeyValue("gerado em", state.index.generatedAt?.take(19)?.replace('T', ' ') ?: "—")
                KeyValue("apps", state.index.apps.size.toString())
                KeyValue("instaladas daqui", state.installedCount.toString())
                KeyValue("atualizações", state.updateCount.toString())
                KeyValue("assinatura", if (state.index.signatureValid) "válida" else "NÃO VERIFICADA")
                KeyValue("chave de confiança", state.index.keyId ?: "—", mono = true)
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRefresh, enabled = !state.index.refreshing) {
                Text(if (state.index.refreshing) "A atualizar…" else "Atualizar agora")
            }
            OutlinedButton(onClick = { onSetIndexUrl(BuildConfig.DEFAULT_INDEX_URL) }) {
                Text("Repor URL")
            }
        }

        state.index.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        state.index.rejectedMessage?.let {
            Spacer(Modifier.height(8.dp))
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
                Text("Tem de ser HTTPS. O ficheiro index.json.sig tem de estar ao lado.", style = MaterialTheme.typography.labelSmall)
            },
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onSetIndexUrl(draft.trim()) },
            enabled = draft.trim().startsWith("https://") && draft.trim() != state.index.indexUrl,
        ) { Text("Usar este catálogo") }

        SectionTitle("Instalação")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    if (state.canInstallPackages) {
                        "Esta app pode instalar aplicações."
                    } else {
                        "O Android ainda não autorizou esta app a instalar aplicações desconhecidas."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!state.canInstallPackages) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onAuthorize) { Text("Abrir permissões") }
                }
            }
        }

        SectionTitle("Licenças")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(12.dp)) {
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
                            text = "${state.restrictedCount} no catálogo. Código público, mas com " +
                                "limitações de uso — nunca são apresentadas como software livre.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.hideRestricted, onCheckedChange = onHideRestricted)
                }
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

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Text(
            "Montra ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Código aberto. O catálogo é dados em git: contribuir é abrir um pull request.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}

private fun originLabel(origin: IndexOrigin?): String = when (origin) {
    IndexOrigin.NETWORK -> "descarregado agora"
    IndexOrigin.CACHED -> "cópia verificada em disco"
    IndexOrigin.BUNDLED -> "snapshot incluído na app"
    null -> "—"
}
