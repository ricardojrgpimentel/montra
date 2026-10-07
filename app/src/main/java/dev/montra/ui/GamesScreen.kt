package dev.montra.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import dev.montra.data.model.IndexApp
import dev.montra.ui.components.AlertBlock
import dev.montra.ui.components.SearchBar
import dev.montra.ui.theme.Space

/**
 * The Games tab. One tab for games and emulators, because to the person browsing they
 * answer the same question.
 *
 * When it is empty it says why and what to do about it, instead of showing a blank
 * screen: the catalogue is edited by pull request, and that is worth saying.
 */
@Composable
fun GamesScreen(
    state: UiState,
    onOpenSearch: () -> Unit,
    onOpen: (IndexApp) -> Unit,
    onInstall: (IndexApp) -> Unit,
    onAuthorize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val games = state.games

    Column(modifier = modifier.fillMaxSize()) {
        SearchBar(
            onClick = onOpenSearch,
            placeholder = "Procurar jogos e emuladores",
            modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.md),
        )

        if (!state.canInstallPackages) {
            AlertBlock(
                title = "Falta uma autorização",
                text = "O Android ainda não autorizou a Montra a instalar aplicações.",
                action = {
                    androidx.compose.material3.Button(onClick = onAuthorize) { Text("Autorizar") }
                },
                modifier = Modifier.padding(horizontal = Space.lg),
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.index.loading && games.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                games.isEmpty() -> Column(
                    modifier = Modifier.fillMaxSize().padding(Space.xl),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Ainda não há jogos nem emuladores no catálogo.",
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = Space.sm))
                    Text(
                        text = "O catálogo é um repositório de ficheiros JSON: qualquer pessoa " +
                            "pode propor um jogo ou um emulador abrindo um pull request. As " +
                            "candidatas passam as mesmas verificações que as restantes apps — " +
                            "licença livre, APK publicado, sha256 e certificado fixados.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        start = Space.lg,
                        end = Space.lg,
                        top = Space.sm,
                        bottom = Space.xxl,
                    ),
                ) {
                    val updates = games.filter { it.updateAvailable }
                    val installed = games.filter { it.isInstalled && !it.updateAvailable }
                    val rest = games.filter { !it.isInstalled && !it.updateAvailable }

                    if (updates.isNotEmpty()) {
                        item {
                            AppSection(
                                title = "Atualizações disponíveis",
                                rows = updates,
                                needsPermission = !state.canInstallPackages,
                                onOpen = onOpen,
                                onInstall = onInstall,
                                onAuthorize = onAuthorize,
                            )
                        }
                    }
                    if (installed.isNotEmpty()) {
                        item {
                            AppSection(
                                title = "Instalados",
                                rows = installed,
                                needsPermission = !state.canInstallPackages,
                                onOpen = onOpen,
                                onInstall = onInstall,
                                onAuthorize = onAuthorize,
                            )
                        }
                    }
                    if (rest.isNotEmpty()) {
                        item {
                            AppSection(
                                title = "Jogos e emuladores",
                                rows = rest,
                                needsPermission = !state.canInstallPackages,
                                onOpen = onOpen,
                                onInstall = onInstall,
                                onAuthorize = onAuthorize,
                            )
                        }
                    }
                }
            }
        }
    }
}
