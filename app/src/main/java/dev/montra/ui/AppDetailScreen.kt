package dev.montra.ui

import dev.montra.R
import dev.montra.util.asString
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
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.montra.data.model.IndexApp
import dev.montra.data.model.antiFeatureLabel
import dev.montra.data.model.categoryLabel
import dev.montra.data.model.statusLabel
import dev.montra.install.InstallManager
import dev.montra.install.InstallState
import dev.montra.install.holdReason
import dev.montra.ui.components.AlertBlock
import dev.montra.ui.components.AppIcon
import dev.montra.ui.components.Badge
import dev.montra.ui.components.Block
import dev.montra.ui.components.KeyValue
import dev.montra.ui.components.LinkRow
import dev.montra.ui.components.ScreenshotRow
import dev.montra.ui.components.SectionTitle
import dev.montra.ui.theme.Space
import dev.montra.util.formatBytes
import dev.montra.util.releaseDateLabel

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
    onCancel: (String) -> Unit,
    onInstallAnyway: (IndexApp) -> Unit,
    onAuthorize: () -> Unit,
    onOpenSource: (String) -> Unit,
    onOpenApp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalContext.current
    val context = LocalContext.current
    val app = row.app
    val locale = LocalConfiguration.current.locales[0]
    val language = locale.toLanguageTag()

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
        Text(app.summaryFor(language), style = MaterialTheme.typography.bodyLarge)

        if (app.categories.isNotEmpty() || app.status != "active") {
            Spacer(Modifier.height(Space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                app.categories.take(2).forEach { category ->
                    Badge(categoryLabel(category).asString(strings), MaterialTheme.colorScheme.secondary)
                }
                if (app.status != "active") {
                    Badge(statusLabel(app.status).asString(strings), MaterialTheme.colorScheme.tertiary)
                }
            }
        }

        // Parentesco é contexto para decidir: "isto é o NewPipe com mais coisas" muda
        // a escolha. Quando o original está no catálogo, a linha leva lá — que é como
        // alguém compara os dois sem sair da loja.
        app.forkOf?.let { fork ->
            Spacer(Modifier.height(Space.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.getString(R.string.text_based_on_1_s, fork.name),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    fork.note?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(
                    onClick = {
                        if (fork.appId != null) onOpenApp(fork.appId) else fork.url?.let(onOpenSource)
                    },
                    enabled = fork.appId != null || fork.url != null,
                ) { Text(if (fork.appId != null) strings.getString(R.string.text_view_original) else strings.getString(R.string.text_repository)) }
            }
        }

        // Antes do botão, não depois e não escondido numa etiqueta: quem vai instalar
        // tem o direito de saber que a licença não é livre antes de decidir.
        if (app.hasRestrictedLicense()) {
            Spacer(Modifier.height(Space.lg))
            AlertBlock(
                title = strings.getString(R.string.text_restricted_licence_1_s, app.license.removePrefix("LicenseRef-")),
                text = app.licenseNoteFor(language)
                    ?: strings.getString(R.string.text_this_app_has_public_source_code_but),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }

        // Uma app parada é uma condição, não um erro: âmbar, como a licença
        // restritiva, e antes do botão porque muda a decisão de quem vai instalar.
        // A data é o que torna isto verificável — quem duvidar vai ao repositório.
        if (row.staleRelease) {
            releaseDateLabel(app.release?.publishedAt, locale)?.let { since ->
                Spacer(Modifier.height(Space.lg))
                AlertBlock(
                    title = strings.getString(R.string.text_no_catalogue_releases_since_1_s, since),
                    text = strings.getString(R.string.text_the_catalogue_only_sees_releases_published_on),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }

        app.accessRequirements?.let { access ->
            Spacer(Modifier.height(Space.lg))
            AlertBlock(
                title = access.label.asString(strings),
                text = access.noteFor(language)
                    ?: if (access.isRequired) strings.getString(R.string.text_set_up_the_required_access_before_using)
                    else strings.getString(R.string.text_you_can_use_this_app_without_this),
                containerColor = if (access.isRequired) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = if (access.isRequired) MaterialTheme.colorScheme.onTertiaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                action = {
                    access.guideUrl?.takeIf { it.startsWith("https://") }?.let { url ->
                        TextButton(onClick = { onOpenSource(url) }) { Text(strings.getString(R.string.text_setup_guide)) }
                    }
                },
            )
        }

        Spacer(Modifier.height(Space.lg))
        InstallSection(
            row = row,
            canInstallPackages = canInstallPackages,
            onInstall = onInstall,
            onClearError = onClearError,
            onCancel = onCancel,
            onInstallAnyway = onInstallAnyway,
            onAuthorize = onAuthorize,
        )

        // A signature conflict is not our error and retrying does not fix it, so it
        // gets its own explanation and its own exit.
        if (row.signatureConflict) {
            Spacer(Modifier.height(Space.md))
            AlertBlock(
                title = strings.getString(R.string.text_signed_with_another_key),
                text = strings.getString(R.string.text_the_installed_version_was_signed_with_a),
                action = {
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                InstallManager.of(context).uninstallIntent(app.packageName),
                            )
                        },
                    ) { Text(strings.getString(R.string.text_uninstall_current_version)) }
                },
            )
        }

        // A licença restritiva já tem o seu bloco acima: repeti-la aqui em baixo
        // seria o mesmo texto duas vezes no mesmo ecrã.
        val otherAntiFeatures = app.antiFeatures.filter { it != "restrictedLicense" }
        if (otherAntiFeatures.isNotEmpty()) {
            Spacer(Modifier.height(Space.md))
            Block {
                Text(strings.getString(R.string.text_warnings), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Space.sm))
                otherAntiFeatures.forEach { feature ->
                    Text(
                        text = "• ${antiFeatureLabel(feature).asString(strings)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // As permissões vêm à frente de tudo o resto nos detalhes: são a única coisa
        // aqui dentro que muda a decisão de instalar. E só se mostram quando o catálogo
        // as traz — um índice anterior a este campo não as tem, e "não pede nada" seria
        // uma afirmação que ninguém verificou.
        if (app.permissions.isNotEmpty()) {
            val permissions = remember(app.permissions) { Permissions.lines(context, app.permissions) }
            SectionTitle(strings.getString(R.string.text_permissions))
            Block {
                if (permissions.any { it.sensitive }) {
                    Text(
                        text = strings.getString(R.string.text_these_reach_personal_data_what),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(Modifier.height(Space.sm))
                }
                permissions.forEach { permission -> PermissionRow(permission) }
            }
        }

        SectionTitle(strings.getString(R.string.text_version))
        Block {
            KeyValue(strings.getString(R.string.text_version_2), app.release?.versionName ?: "—")
            KeyValue("versionCode", (row.asset?.versionCode ?: app.release?.versionCode)?.toString() ?: "—")
            KeyValue(strings.getString(R.string.text_size), formatBytes(row.size))
            KeyValue(strings.getString(R.string.text_architecture), row.asset?.abi?.replace("universal", strings.getString(R.string.all_architectures)) ?: "—")
            KeyValue(strings.getString(R.string.text_minimum_android), row.asset?.minSdk?.let { "API $it" } ?: strings.getString(R.string.text_not_declared))
            KeyValue(strings.getString(R.string.text_published), app.release?.publishedAt?.take(10) ?: "—")
            app.downloadCount?.let { KeyValue(strings.getString(R.string.text_downloads), "%,d".format(it)) }
            KeyValue(strings.getString(R.string.text_licence), app.license)
        }

        SectionTitle(strings.getString(R.string.text_verification))
        Block {
            Text(
                text = strings.getString(R.string.text_this_apk_is_checked_byte_for_byte),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.sm))
            KeyValue(strings.getString(R.string.text_apk_sha_256), row.asset?.sha256?.chunked(32)?.joinToString("\n") ?: "—", mono = true)
            KeyValue(
                strings.getString(R.string.text_certificate),
                app.signingCertSha256?.chunked(24)?.joinToString("\n") ?: strings.getString(R.string.text_not_pinned),
                mono = true,
            )
            keyId?.let { KeyValue(strings.getString(R.string.text_catalogue_key), it, mono = true) }
            row.installed?.certSha256?.let {
                KeyValue(strings.getString(R.string.text_installed_certificate), it.chunked(24).joinToString("\n"), mono = true)
            }
        }

        app.descriptionFor(language)?.let { text ->
            SectionTitle(strings.getString(R.string.text_about))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }

        app.release?.changelog?.takeIf { it.isNotBlank() }?.let { changelog ->
            SectionTitle(strings.getString(R.string.text_whats_new_in_1_s, app.release.versionName))
            Text(stripMarkdown(changelog), style = MaterialTheme.typography.bodyMedium)
        }

        if (row.screenshotUrls.isNotEmpty()) {
            SectionTitle(strings.getString(R.string.text_screenshots))
            ScreenshotRow(row.screenshotUrls)
        }

        SectionTitle(strings.getString(R.string.text_links))
        Column {
            LinkRow(strings.getString(R.string.text_source_code), app.sourceCode, onOpenSource)
            app.links["website"]?.let { LinkRow(strings.getString(R.string.text_website), it, onOpenSource) }
            app.links["docs"]?.let { LinkRow(strings.getString(R.string.text_documentation), it, onOpenSource) }
            app.links["changelog"]?.let { LinkRow(strings.getString(R.string.text_changelog), it, onOpenSource) }
            app.links["translate"]?.let { LinkRow(strings.getString(R.string.text_translate), it, onOpenSource) }
            app.links["donate"]?.let { LinkRow(strings.getString(R.string.text_donate), it, onOpenSource) }
            app.playStore?.takeIf { it.present }?.url?.let { LinkRow(strings.getString(R.string.text_also_on_the_play_store), it, onOpenSource) }
        }

        if (app.warnings.isNotEmpty()) {
            SectionTitle(strings.getString(R.string.text_catalogue_notes))
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
    onCancel: (String) -> Unit,
    onInstallAnyway: (IndexApp) -> Unit,
    onAuthorize: () -> Unit,
) {
    val strings = LocalContext.current
    val context = LocalContext.current
    when (val state = row.installState) {
        is InstallState.Downloading -> Column(Modifier.fillMaxWidth()) {
            Text(
                text = strings.getString(R.string.text_downloading_1_s_of_2_s, formatBytes(state.bytes), formatBytes(state.total)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(Space.sm))
            LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth())
        }

        is InstallState.Verifying -> Text(
            text = strings.getString(R.string.text_verifying_sha_256_and_certificate),
            style = MaterialTheme.typography.bodyMedium,
        )

        // À espera da sua vez. Diz que está à espera e deixa sair: um estado sem
        // saída é um beco, e quem tocou em Instalar tem de poder voltar atrás.
        InstallState.Queued -> Column {
            Text(
                text = strings.getString(R.string.text_waiting_for_the_install_running_now),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(Space.sm))
            TextButton(onClick = { onCancel(row.app.id) }) {
                Text(strings.getString(R.string.text_cancel))
            }
        }

        // Segurada antes de começar. Diz porquê e oferece a saída quando ela existe:
        // quem tocou em Instalar tem de poder continuar ou desistir, e não ficar a
        // olhar para um botão que não faz nada.
        InstallState.OnMeteredNetwork -> AlertBlock(
            title = strings.getString(R.string.text_mobile_data),
            text = state.holdReason()?.asString(strings).orEmpty(),
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Button(onClick = { onInstallAnyway(row.app) }) {
                        Text(strings.getString(R.string.text_download_anyway))
                    }
                    TextButton(onClick = { onClearError(row.app.id) }) {
                        Text(strings.getString(R.string.text_cancel))
                    }
                }
            },
        )

        // Repetir não resolve nada enquanto não houver espaço: o que a ficha pode
        // fazer é dizer quanto falta, e deixar a pessoa voltar quando o tiver.
        is InstallState.NoSpace -> AlertBlock(
            title = strings.getString(R.string.text_no_space),
            text = state.holdReason()?.asString(strings).orEmpty(),
            action = {
                TextButton(onClick = { onClearError(row.app.id) }) {
                    Text(strings.getString(R.string.text_dismiss))
                }
            },
        )

        is InstallState.AwaitingUser -> Column {
            Text(
                text = strings.getString(R.string.text_confirm_the_installation_in_the_system_dialog),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Button(
                    enabled = state.confirmation != null,
                    onClick = { InstallManager.of(context).confirmInstall(row.app.id) },
                ) { Text(strings.getString(R.string.text_confirm_installation)) }
                TextButton(
                    enabled = state.confirmation != null,
                    onClick = { InstallManager.of(context).cancelPendingInstall(row.app.id) },
                ) { Text(strings.getString(R.string.text_cancel)) }
            }
        }

        is InstallState.Installed -> Column {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                InstallManager.launchIntent(context, row.app.packageName)?.let { intent ->
                    Button(onClick = { context.startActivity(intent) }) { Text(strings.getString(R.string.text_open)) }
                }
                OutlinedButton(onClick = { onClearError(row.app.id) }) { Text(strings.getString(R.string.text_done)) }
            }
            // Desinstalar é a ação que faltava: uma loja que instala e não desinstala
            // obriga a sair dela para resolver o que ela própria fez. O diálogo é o do
            // sistema — a app não desinstala nada por si.
            TextButton(onClick = { uninstall(context, row.app.packageName) }) {
                Text(strings.getString(R.string.text_uninstall))
            }
        }

        InstallState.NeedsPermission -> AlertBlock(
            title = strings.getString(R.string.text_permission_required),
            text = strings.getString(R.string.text_android_has_not_authorised_montra_to_install),
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Button(onClick = onAuthorize) { Text(strings.getString(R.string.text_open_permission_settings)) }
                    TextButton(onClick = { onClearError(row.app.id) }) { Text(strings.getString(R.string.text_dismiss)) }
                }
            },
        )

        is InstallState.Failed -> AlertBlock(
            title = strings.getString(R.string.text_could_not_complete),
            text = state.reason.asString(strings),
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Button(onClick = { onInstall(row.app) }) { Text(strings.getString(R.string.text_try_again)) }
                    TextButton(onClick = { onClearError(row.app.id) }) { Text(strings.getString(R.string.text_dismiss)) }
                }
            },
        )

        InstallState.Idle -> when {
            row.incompatible != null -> {
                val reason = row.incompatible?.asString(strings).orEmpty()
                AlertBlock(title = strings.getString(R.string.text_cannot_run_on_this_device), text = reason)
            }
            row.asset == null -> AlertBlock(
                text = strings.getString(R.string.text_this_app_does_not_publish_an_apk),
            )
            !canInstallPackages -> Button(
                onClick = onAuthorize,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(strings.getString(R.string.text_allow_installation)) }
            !row.isInstalled -> Button(
                onClick = { onInstall(row.app) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(strings.getString(R.string.install_with_size, formatBytes(row.size))) }
            row.updateAvailable -> Button(
                onClick = { onInstall(row.app) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(strings.getString(R.string.text_update_to_1_s, row.app.release?.versionName)) }
            else -> Row(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InstallManager.launchIntent(context, row.app.packageName)?.let { intent ->
                    Button(onClick = { context.startActivity(intent) }) { Text(strings.getString(R.string.text_open)) }
                }
                Badge(strings.getString(R.string.text_latest_version), MaterialTheme.colorScheme.primary)
                TextButton(onClick = { uninstall(context, row.app.packageName) }) {
                    Text(strings.getString(R.string.text_uninstall))
                }
            }
        }
    }
}

/**
 * O diálogo de desinstalação é o do sistema (`ACTION_DELETE`): a app pede, quem decide
 * é quem está a segurar no telemóvel. Não há caminho em que a Montra desinstale algo
 * sem ser mostrada essa pergunta.
 */
private fun uninstall(context: Context, packageName: String) {
    runCatching { context.startActivity(InstallManager.of(context).uninstallIntent(packageName)) }
}

/**
 * Uma permissão: o nome que o sistema lhe dá e, quando existe, a frase que o sistema
 * usa para a explicar. Âmbar é atenção, e é o mesmo âmbar de uma licença que limita —
 * uma condição que muda a decisão, não um erro.
 */
@Composable
private fun PermissionRow(permission: Permissions.Line) {
    Column(modifier = Modifier.padding(vertical = Space.xs)) {
        Text(
            text = permission.label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (permission.sensitive) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        permission.description?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
