package dev.montra.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.montra.data.model.IndexApp
import dev.montra.data.model.antiFeatureLabel
import dev.montra.data.model.categoryLabel
import dev.montra.data.model.statusLabel
import dev.montra.install.InstallManager
import dev.montra.install.InstallState
import dev.montra.security.ApkVerifier
import dev.montra.ui.components.AppIcon
import dev.montra.ui.components.KeyValue
import dev.montra.ui.components.SectionTitle
import dev.montra.ui.components.ScreenshotRow
import dev.montra.util.formatBytes
import java.util.Locale

@Composable
fun AppDetailScreen(
    row: AppRow,
    keyId: String?,
    onInstall: (IndexApp) -> Unit,
    onClearError: (String) -> Unit,
    onOpenSource: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = row.app
    val language = Locale.getDefault().language

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(url = row.iconUrl, seed = app.packageName, text = app.name.take(1).uppercase(), size = 72)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(app.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                app.author?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(app.packageName, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(app.summary, style = MaterialTheme.typography.bodyLarge)

        Spacer(Modifier.height(16.dp))
        InstallSection(row = row, onInstall = onInstall, onClearError = onClearError)

        if (!row.signatureConflict && row.installed != null && row.app.signingCertSha256 != null) {
            val installedCert = ApkVerifier.installedSigningCertificateSha256(context, app.packageName)
            if (installedCert != null && !dev.montra.util.fingerprintsMatch(installedCert, app.signingCertSha256)) {
                WarningCard(
                    "A versão instalada está assinada por outra chave. O Android vai recusar a atualização: " +
                        "desinstala primeiro e volta a instalar a partir daqui.",
                )
            }
        }

        if (app.antiFeatures.isNotEmpty()) {
            SectionTitle("Avisos")
            app.antiFeatures.forEach { feature ->
                WarningCard(antiFeatureLabel(feature))
            }
        }

        SectionTitle("Versão")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(12.dp)) {
                KeyValue("versão", app.release?.versionName ?: "—")
                KeyValue("versionCode", (row.asset?.versionCode ?: app.release?.versionCode)?.toString() ?: "—")
                KeyValue("tamanho", formatBytes(row.size))
                KeyValue("ABI", row.asset?.abi?.replace("universal", "universal (todas)") ?: "—")
                KeyValue("Android mínimo", row.asset?.minSdk?.let { "API $it" } ?: "não declarado")
                KeyValue("publicado", app.release?.publishedAt?.take(10) ?: "—")
                row.asset?.nativeAbis?.takeIf { it.isNotEmpty() }?.let {
                    KeyValue("bibliotecas nativas", it.joinToString(", "))
                }
                app.downloadCount?.let { KeyValue("descarregamentos", "%,d".format(it)) }
                KeyValue("licença", app.license)
                KeyValue("estado", statusLabel(app.status))
            }
        }

        SectionTitle("Verificação")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    "Esta app foi verificada contra o catálogo assinado antes de ser instalada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                KeyValue("sha256 do APK", row.asset?.sha256?.chunked(32)?.joinToString("\n") ?: "—", mono = true)
                KeyValue(
                    "certificado de assinatura",
                    app.signingCertSha256?.chunked(24)?.joinToString("\n") ?: "não fixado",
                    mono = true,
                )
                if (keyId != null) {
                    KeyValue("chave do catálogo", keyId, mono = true)
                }
                row.installed?.certSha256?.let {
                    KeyValue("certificado instalado", it.chunked(24).joinToString("\n"), mono = true)
                }
            }
        }

        app.descriptionFor(language)?.let { text ->
            SectionTitle("Sobre")
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }

        app.release?.changelog?.takeIf { it.isNotBlank() }?.let { changelog ->
            SectionTitle("Novidades em ${app.release.versionName}")
            Text(stripMarkdown(changelog), style = MaterialTheme.typography.bodySmall)
        }

        if (row.screenshotUrls.isNotEmpty()) {
            SectionTitle("Imagens")
            ScreenshotRow(row.screenshotUrls)
        }

        SectionTitle("Ligações")
        Column {
            LinkRow("Código-fonte", app.sourceCode, onOpenSource)
            app.links["website"]?.let { LinkRow("Site", it, onOpenSource) }
            app.links["docs"]?.let { LinkRow("Documentação", it, onOpenSource) }
            app.links["changelog"]?.let { LinkRow("Registo de alterações", it, onOpenSource) }
            app.links["translate"]?.let { LinkRow("Traduzir", it, onOpenSource) }
            app.links["donate"]?.let { LinkRow("Doar", it, onOpenSource) }
            app.links["issues"]?.let { LinkRow("Problemas", it, onOpenSource) }
            app.playStore?.takeIf { it.present }?.url?.let { LinkRow("Também na Play Store", it, onOpenSource) }
        }

        if (app.warnings.isNotEmpty()) {
            SectionTitle("Notas do catálogo")
            app.warnings.forEach { WarningCard(it) }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun InstallSection(row: AppRow, onInstall: (IndexApp) -> Unit, onClearError: (String) -> Unit) {
    val context = LocalContext.current
    when (val state = row.installState) {
        is InstallState.Downloading -> Column(Modifier.fillMaxWidth()) {
            Text("A descarregar ${formatBytes(state.bytes)} de ${formatBytes(state.total)}")
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth())
        }

        is InstallState.Verifying -> Text("A verificar sha256 e certificado…")
        is InstallState.AwaitingUser -> Text("A aguardar a confirmação do instalador do Android…")
        is InstallState.Installed -> Column {
            Text("Instalado.", color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            InstallManager.launchIntent(context, row.app.packageName)?.let { intent ->
                OutlinedButton(onClick = { context.startActivity(intent) }) { Text("Abrir") }
            }
        }
        is InstallState.Failed -> Column {
            WarningCard(state.reason)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onInstall(row.app) }) { Text("Tentar de novo") }
                TextButton(onClick = { onClearError(row.app.id) }) { Text("Dispensar") }
            }
            if (row.signatureConflict) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { context.startActivity(InstallManager.of(context).uninstallIntent(row.app.packageName)) },
                ) { Text("Desinstalar a versão atual") }
            }
        }
        InstallState.Idle -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (row.asset == null) {
                Text("Sem APK compatível com a arquitetura deste dispositivo.", color = MaterialTheme.colorScheme.error)
            } else if (row.incompatible != null) {
                Text(row.incompatible!!, color = MaterialTheme.colorScheme.error)
            } else if (!row.isInstalled) {
                Button(onClick = { onInstall(row.app) }) { Text("Instalar ${formatBytes(row.size)}") }
            } else if (row.updateAvailable) {
                Button(onClick = { onInstall(row.app) }) {
                    Text("Atualizar para ${row.app.release?.versionName}")
                }
            } else {
                Column {
                    Text("Já tens a versão mais recente.", color = MaterialTheme.colorScheme.primary)
                    InstallManager.launchIntent(context, row.app.packageName)?.let { intent ->
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { context.startActivity(intent) }) { Text("Abrir") }
                    }
                }
            }
        }
    }
}

@Composable
private fun WarningCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun LinkRow(label: String, url: String, onOpen: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { onOpen(url) }) {
            Text(url.removePrefix("https://").take(38), maxLines = 1)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
}

/** Changelogs are markdown; this strips the syntax we cannot render yet. */
private fun stripMarkdown(text: String): String = text
    .replace(Regex("(?m)^#{1,6}\\s*"), "")
    .replace(Regex("\\[([^\\]]+)]\\(([^)]+)\\)"), "$1")
    .replace(Regex("[*_`]{1,3}"), "")
    .trim()
