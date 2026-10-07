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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.montra.data.model.IndexApp
import dev.montra.data.model.antiFeatureLabel
import dev.montra.data.model.categoryLabel
import dev.montra.data.model.statusLabel
import dev.montra.install.InstallManager
import dev.montra.install.InstallState
import dev.montra.ui.components.AlertBlock
import dev.montra.ui.components.AppIcon
import dev.montra.ui.components.Badge
import dev.montra.ui.components.Block
import dev.montra.ui.components.KeyValue
import dev.montra.ui.components.ScreenshotRow
import dev.montra.ui.components.SectionTitle
import dev.montra.ui.theme.Space
import dev.montra.util.formatBytes
import java.util.Locale

/**
 * The app page.
 *
 * The order of the information is the order of the decision: what it is, whether it
 * runs here, whether I want it, then the evidence, then everything else. Nothing is
 * nested inside anything else: the page has one surface and the blocks on it are the
 * page's own sections.
 */
@Composable
fun AppDetailScreen(
    row: AppRow,
    keyId: String?,
    canInstallPackages: Boolean,
    onInstall: (IndexApp) -> Unit,
    onClearError: (String) -> Unit,
    onAuthorize: () -> Unit,
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
            .padding(horizontal = Space.lg),
    ) {
        Spacer(Modifier.height(Space.sm))

        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(
                url = row.iconUrl,
                seed = app.packageName,
                text = app.name.take(1).uppercase(),
                size = 72.dp,
            )
            Spacer(Modifier.width(Space.lg))
            Column(modifier = Modifier.weight(1f)) {
                Text(app.name, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(Space.xs))
                Text(
                    text = app.author ?: app.packageName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(Space.lg))
        Text(app.summary, style = MaterialTheme.typography.bodyLarge)

        if (app.categories.isNotEmpty() || app.status != "active") {
            Spacer(Modifier.height(Space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                app.categories.take(2).forEach { category ->
                    Badge(categoryLabel(category), MaterialTheme.colorScheme.secondary)
                }
                if (app.status != "active") {
                    Badge(statusLabel(app.status), MaterialTheme.colorScheme.tertiary)
                }
            }
        }

        // Antes do botão, não depois e não escondido numa etiqueta: quem vai instalar
        // tem o direito de saber que a licença não é livre antes de decidir.
        if (app.hasRestrictedLicense()) {
            Spacer(Modifier.height(Space.lg))
            AlertBlock(
                title = "Licença restritiva: ${app.license.removePrefix("LicenseRef-")}",
                text = app.licenseNoteFor(language)
                    ?: "Esta aplicação tem o código público, mas a licença impõe limitações de uso.",
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }

        Spacer(Modifier.height(Space.lg))
        InstallSection(
            row = row,
            canInstallPackages = canInstallPackages,
            onInstall = onInstall,
            onClearError = onClearError,
            onAuthorize = onAuthorize,
        )

        // A signature conflict is not our error and retrying does not fix it, so it
        // gets its own explanation and its own exit.
        if (row.signatureConflict) {
            Spacer(Modifier.height(Space.md))
            AlertBlock(
                title = "Assinada por outra chave",
                text = "A versão instalada neste telemóvel foi assinada por uma chave diferente " +
                    "da que o catálogo fixa, por isso o Android vai recusar a atualização. " +
                    "Desinstala primeiro e volta a instalar a partir daqui.",
                action = {
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                InstallManager.of(context).uninstallIntent(app.packageName),
                            )
                        },
                    ) { Text("Desinstalar a versão atual") }
                },
            )
        }

        if (app.antiFeatures.isNotEmpty()) {
            Spacer(Modifier.height(Space.md))
            Block {
                Text("Avisos", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Space.sm))
                app.antiFeatures.forEach { feature ->
                    Text(
                        text = "• ${antiFeatureLabel(feature)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        SectionTitle("Versão")
        Block {
            KeyValue("versão", app.release?.versionName ?: "—")
            KeyValue("versionCode", (row.asset?.versionCode ?: app.release?.versionCode)?.toString() ?: "—")
            KeyValue("tamanho", formatBytes(row.size))
            KeyValue("arquitetura", row.asset?.abi?.replace("universal", "todas") ?: "—")
            KeyValue("Android mínimo", row.asset?.minSdk?.let { "API $it" } ?: "não declarado")
            KeyValue("publicado", app.release?.publishedAt?.take(10) ?: "—")
            app.downloadCount?.let { KeyValue("descarregamentos", "%,d".format(it)) }
            KeyValue("licença", app.license)
        }

        SectionTitle("Verificação")
        Block {
            Text(
                text = "Este APK foi confirmado byte a byte contra o catálogo assinado antes de " +
                    "ser entregue ao instalador do Android.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.sm))
            KeyValue("sha256 do APK", row.asset?.sha256?.chunked(32)?.joinToString("\n") ?: "—", mono = true)
            KeyValue(
                "certificado",
                app.signingCertSha256?.chunked(24)?.joinToString("\n") ?: "não fixado",
                mono = true,
            )
            keyId?.let { KeyValue("chave do catálogo", it, mono = true) }
            row.installed?.certSha256?.let {
                KeyValue("certificado instalado", it.chunked(24).joinToString("\n"), mono = true)
            }
        }

        app.descriptionFor(language)?.let { text ->
            SectionTitle("Sobre")
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }

        app.release?.changelog?.takeIf { it.isNotBlank() }?.let { changelog ->
            SectionTitle("Novidades em ${app.release.versionName}")
            Text(stripMarkdown(changelog), style = MaterialTheme.typography.bodyMedium)
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
            app.links["changelog"]?.let { LinkRow("Alterações", it, onOpenSource) }
            app.links["translate"]?.let { LinkRow("Traduzir", it, onOpenSource) }
            app.links["donate"]?.let { LinkRow("Doar", it, onOpenSource) }
            app.playStore?.takeIf { it.present }?.url?.let { LinkRow("Também na Play Store", it, onOpenSource) }
        }

        if (app.warnings.isNotEmpty()) {
            SectionTitle("Notas do catálogo")
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                app.warnings.forEach { warning -> AlertBlock(text = warning) }
            }
        }

        Spacer(Modifier.height(Space.xxl))
    }
}

@Composable
private fun InstallSection(
    row: AppRow,
    canInstallPackages: Boolean,
    onInstall: (IndexApp) -> Unit,
    onClearError: (String) -> Unit,
    onAuthorize: () -> Unit,
) {
    val context = LocalContext.current
    when (val state = row.installState) {
        is InstallState.Downloading -> Column(Modifier.fillMaxWidth()) {
            Text(
                text = "A descarregar ${formatBytes(state.bytes)} de ${formatBytes(state.total)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(Space.sm))
            LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth())
        }

        is InstallState.Verifying -> Text(
            text = "A verificar o sha256 e o certificado…",
            style = MaterialTheme.typography.bodyMedium,
        )

        is InstallState.AwaitingUser -> Text(
            text = "Confirma a instalação no diálogo do sistema.",
            style = MaterialTheme.typography.bodyMedium,
        )

        is InstallState.Installed -> Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            InstallManager.launchIntent(context, row.app.packageName)?.let { intent ->
                Button(onClick = { context.startActivity(intent) }) { Text("Abrir") }
            }
            OutlinedButton(onClick = { onClearError(row.app.id) }) { Text("Concluir") }
        }

        InstallState.NeedsPermission -> AlertBlock(
            title = "Falta uma autorização",
            text = "O Android ainda não autorizou a Montra a instalar aplicações. Liga a " +
                "autorização, volta aqui, e só então o download começa.",
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Button(onClick = onAuthorize) { Text("Abrir autorização") }
                    TextButton(onClick = { onClearError(row.app.id) }) { Text("Dispensar") }
                }
            },
        )

        is InstallState.Failed -> AlertBlock(
            title = "Não foi possível",
            text = state.reason,
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Button(onClick = { onInstall(row.app) }) { Text("Tentar de novo") }
                    TextButton(onClick = { onClearError(row.app.id) }) { Text("Dispensar") }
                }
            },
        )

        InstallState.Idle -> when {
            row.incompatible != null -> {
                val reason = row.incompatible ?: ""
                AlertBlock(title = "Não corre aqui", text = reason)
            }
            row.asset == null -> AlertBlock(
                text = "Esta app não publica um APK para a arquitetura deste dispositivo.",
            )
            !canInstallPackages -> Button(
                onClick = onAuthorize,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Autorizar instalação") }
            !row.isInstalled -> Button(
                onClick = { onInstall(row.app) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Instalar · ${formatBytes(row.size)}") }
            row.updateAvailable -> Button(
                onClick = { onInstall(row.app) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Atualizar para ${row.app.release?.versionName}") }
            else -> Row(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InstallManager.launchIntent(context, row.app.packageName)?.let { intent ->
                    Button(onClick = { context.startActivity(intent) }) { Text("Abrir") }
                }
                Badge("versão mais recente", MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun LinkRow(label: String, url: String, onOpen: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { onOpen(url) }) {
            Text(
                text = url.removePrefix("https://").removePrefix("http://").take(34),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
    }
}

/** Changelogs are markdown; this strips the syntax we cannot render yet. */
private fun stripMarkdown(text: String): String = text
    .replace(Regex("(?m)^#{1,6}\\s*"), "")
    .replace(Regex("\\[([^\\]]+)]\\(([^)]+)\\)"), "$1")
    .replace(Regex("[*_`]{1,3}"), "")
    .trim()
