package dev.montra.ui

import android.content.Context
import dev.montra.R
import dev.montra.util.asString
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
import dev.montra.data.AppLanguage
import dev.montra.data.AutoRefresh
import dev.montra.data.IndexOrigin
import dev.montra.data.ThemeMode
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
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    onRefresh: () -> Unit,
    onSetIndexUrl: (String) -> Unit,
    onAuthorize: () -> Unit,
    onHideRestricted: (Boolean) -> Unit,
    onAutoRefresh: (AutoRefresh) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalContext.current
    var draft by remember(state.index.indexUrl) { mutableStateOf(state.index.indexUrl) }
    val now = remember(state.index.lastCheckedAt) { System.currentTimeMillis() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Column(modifier = Modifier.padding(horizontal = Space.lg)) {
            SectionTitle(strings.getString(R.string.language), divider = false)
            Text(
                strings.getString(R.string.language_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.sm))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                AppLanguage.entries.forEach { option ->
                    FilterChip(
                        selected = language == option,
                        onClick = { onLanguage(option) },
                        label = { Text(if (option == AppLanguage.SYSTEM) strings.getString(R.string.language_system) else option.nativeName) },
                    )
                }
            }

            SectionTitle(strings.getString(R.string.appearance))
            Text(
                text = strings.getString(R.string.text_choose_a_light_or_dark_appearance_system),
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
                        label = { Text(strings.getString(option.labelRes)) },
                    )
                }
            }

            SectionTitle(strings.getString(R.string.text_catalogue))
            Block {
                KeyValue(strings.getString(R.string.text_origin), originLabel(state.index.origin, strings))
                KeyValue(strings.getString(R.string.text_verified), verifiedLabel(state.index.lastCheckedAt, now).asString(strings))
                KeyValue(strings.getString(R.string.text_generated_on), state.index.generatedAt?.take(19)?.replace('T', ' ') ?: "—")
                KeyValue(strings.getString(R.string.text_apps), state.index.apps.size.toString())
                KeyValue(strings.getString(R.string.text_installed_from_here), state.installedCount.toString())
                KeyValue(strings.getString(R.string.text_updates), state.updateCount.toString())
                KeyValue(
                    label = strings.getString(R.string.text_signature),
                    value = if (state.index.signatureValid) strings.getString(R.string.text_valid) else strings.getString(R.string.refused),
                    // O estado é do valor, não do rótulo: a linha inteira a vermelho
                    // faria da chave de confiança um erro também.
                    valueColor = if (state.index.signatureValid) {
                        Color.Unspecified
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                KeyValue(strings.getString(R.string.text_trusted_key), state.index.keyId ?: "—", mono = true)
            }

            Spacer(Modifier.height(Space.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Button(onClick = onRefresh, enabled = !state.index.refreshing) {
                    Text(if (state.index.refreshing) strings.getString(R.string.text_updating) else strings.getString(R.string.text_refresh_now))
                }
                OutlinedButton(onClick = { onSetIndexUrl(BuildConfig.DEFAULT_INDEX_URL) }) {
                    Text(strings.getString(R.string.text_reset_url))
                }
            }

            state.index.error?.let {
                Spacer(Modifier.height(Space.sm))
                // Offline não é vermelho: é a mesma condição calma que a faixa mostra.
                Text(
                    text = it.asString(strings),
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
                    strings.getString(R.string.text_index_rejected_1_s, it.asString(strings)),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            SectionTitle(strings.getString(R.string.text_catalogue_source))
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(strings.getString(R.string.text_index_json_url)) },
                singleLine = false,
                supportingText = {
                    Text(
                        strings.getString(R.string.text_https_is_required_with_index_json_sig),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
            Spacer(Modifier.height(Space.sm))
            Button(
                onClick = { onSetIndexUrl(draft.trim()) },
                enabled = draft.trim().startsWith("https://") && draft.trim() != state.index.indexUrl,
            ) { Text(strings.getString(R.string.text_use_this_catalogue)) }
            Spacer(Modifier.height(Space.sm))
            Text(
                text = strings.getString(R.string.text_a_catalogue_fork_can_become_a_new),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle(strings.getString(R.string.text_automatic_checks))
            Text(
                text = strings.getString(R.string.text_the_catalogue_is_a_repository_file_while),
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
                        label = { Text(strings.getString(option.labelRes)) },
                    )
                }
            }

            SectionTitle(strings.getString(R.string.text_installation))
            Block {
                Text(
                    if (state.canInstallPackages) {
                        strings.getString(R.string.text_montra_is_allowed_to_install_apps_each)
                    } else {
                        strings.getString(R.string.text_android_has_not_authorised_montra_to_install_4)
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!state.canInstallPackages) {
                    Spacer(Modifier.height(Space.sm))
                    OutlinedButton(onClick = onAuthorize) { Text(strings.getString(R.string.text_open_permissions)) }
                }
            }

            SectionTitle(strings.getString(R.string.text_licences))
            Block {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.getString(R.string.text_hide_apps_with_restricted_licences),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = strings.getString(R.string.text_1_s_in_the_catalogue_public_source, state.filterCounts[AppFilter.RESTRICTED] ?: 0),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.hideRestricted, onCheckedChange = onHideRestricted)
                }
            }

            SectionTitle(strings.getString(R.string.text_how_it_works))
            Text(
                strings.getString(R.string.text_montra_downloads_a_json_file_from_a),
                style = MaterialTheme.typography.bodySmall,
            )

            SectionTitle(strings.getString(R.string.text_about))
            Block {
                MontraBrand()
                Spacer(Modifier.height(Space.xs))
                // A versão e o código ficam juntos porque é isto que se lê em voz alta
                // quando se reporta um problema, e é a única coisa nesta app que o
                // utilizador não consegue ver sem vir aqui.
                KeyValue(strings.getString(R.string.text_version_2), BuildConfig.VERSION_NAME)
                KeyValue("versionCode", BuildConfig.VERSION_CODE.toString())
                KeyValue(strings.getString(R.string.text_licence), "AGPL-3.0-or-later")
                Spacer(Modifier.height(Space.sm))
                Text(
                    text = strings.getString(R.string.text_open_source_the_catalogue_is_data_in),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(Space.sm))
            LinkRow(strings.getString(R.string.text_website), "https://ricardopimen.tel", onOpenUrl)
            LinkRow(strings.getString(R.string.text_code), "https://github.com/ricardojrgpimentel/montra", onOpenUrl)
            LinkRow(strings.getString(R.string.text_catalogue), "https://github.com/ricardojrgpimentel/montra-index", onOpenUrl)

            SectionTitle(strings.getString(R.string.text_credits))
            Block {
                Text(strings.getString(R.string.text_made_by_ricardo_pimentel), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(Space.sm))
                Text(
                    text = strings.getString(R.string.text_interface_with_jetpack_compose_and_material_3),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Space.sm))
                Text(
                    text = strings.getString(R.string.text_catalogue_apps_belong_to_their_creators_icons),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinkRow(strings.getString(R.string.text_design_rules), "https://impeccable.style/slop", onOpenUrl)
            }

            Spacer(Modifier.height(Space.xl))
        }
    }
}

private fun originLabel(origin: IndexOrigin?, strings: Context): String = when (origin) {
    IndexOrigin.NETWORK -> strings.getString(R.string.text_just_downloaded)
    IndexOrigin.CACHED -> strings.getString(R.string.text_verified_copy_on_disk)
    IndexOrigin.BUNDLED -> strings.getString(R.string.text_bundled_snapshot)
    null -> "—"
}
