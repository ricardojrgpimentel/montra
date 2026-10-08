package dev.montra.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import dev.montra.data.model.IndexApp
import dev.montra.ui.components.AlertBlock
import dev.montra.ui.components.CatalogueRibbon
import dev.montra.ui.components.SearchBar
import dev.montra.ui.theme.Space

/**
 * The Games tab. One tab for games and emulators, because to the person browsing they
 * answer the same question.
 *
 * When it is empty it says why and what to do about it, instead of showing a blank
 * screen: the catalogue is edited by pull request, and that is worth saying.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpen: (IndexApp) -> Unit,
    onInstall: (IndexApp) -> Unit,
    onAuthorize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val games = state.games
    val listState = rememberLazyListState()
    val collapsed by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 48
        }
    }
    val installedGames = games.count { it.isInstalled }

    Column(modifier = modifier.fillMaxSize()) {
        CatalogueRibbon(
            counts = buildString {
                append(games.size)
                append(if (games.size == 1) " jogo ou emulador" else " jogos e emuladores")
                if (installedGames > 0) append(" · $installedGames instalados")
            },
            updates = games.count { it.updateAvailable },
            refreshing = state.index.refreshing,
            lastCheckedAt = state.index.lastCheckedAt,
            outcome = state.index.outcome,
            outcomeAt = state.index.outcomeAt,
            error = state.index.error ?: state.index.rejectedMessage,
            offline = state.index.offline,
            onRetry = onRefresh,
            collapsed = collapsed,
        )

        if (!state.canInstallPackages) {
            AlertBlock(
                title = "Falta uma autorização",
                text = "O Android ainda não autorizou a Montra a instalar aplicações.",
                action = {
                    Button(onClick = onAuthorize) { Text("Autorizar") }
                },
                modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.index.loading && games.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                games.isEmpty() -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Space.xl),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Ainda não há jogos nem emuladores no catálogo.",
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "O catálogo é um repositório de ficheiros JSON: qualquer pessoa " +
                            "pode propor um jogo ou um emulador abrindo um pull request. As " +
                            "candidatas passam as mesmas verificações que as restantes apps — " +
                            "licença livre, APK publicado, sha256 e certificado fixados.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = Space.sm),
                    )
                }

                else -> PullToRefreshBox(
                    isRefreshing = state.index.refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(Space.md),
                        contentPadding = PaddingValues(top = Space.sm, bottom = Space.xxl),
                    ) {
                        item(key = "search") {
                            SearchBar(
                                onClick = onOpenSearch,
                                placeholder = "Procurar jogos e emuladores",
                                modifier = Modifier.padding(horizontal = Space.lg),
                            )
                        }

                        val updates = games.filter { it.updateAvailable }
                        val installed = games.filter { it.isInstalled && !it.updateAvailable }
                        val rest = games.filter { !it.isInstalled && !it.updateAvailable }

                        appSections(
                            sections = buildList {
                                if (updates.isNotEmpty()) {
                                    add(AppSectionSpec("Atualizações disponíveis", updates, accent = true))
                                }
                                if (installed.isNotEmpty()) {
                                    add(AppSectionSpec("Instalados", installed))
                                }
                                if (rest.isNotEmpty()) {
                                    add(AppSectionSpec("Jogos e emuladores", rest))
                                }
                            },
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
