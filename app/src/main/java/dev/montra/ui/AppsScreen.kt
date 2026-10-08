package dev.montra.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
 * The Apps tab: everything in the catalogue, grouped by what the person needs to do
 * about it — updates first, then what is already installed, then the rest.
 *
 * The page has three fixed pieces above the list, in this order: the ribbon (what
 * this catalogue is and when it was last confirmed), the filter bar (what is being
 * shown), and the active filters. Only the list scrolls. That is deliberate: the
 * complaint that started this was not being able to tell what was selected once the
 * list moved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    state: UiState,
    onCategory: (String?) -> Unit,
    onFilter: (AppFilter?) -> Unit,
    onSort: (SortOrder) -> Unit,
    onRefresh: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpen: (IndexApp) -> Unit,
    onInstall: (IndexApp) -> Unit,
    onAuthorize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered = remember(state.rows, state.category, state.filter) {
        var base = state.rows
        when (state.filter) {
            AppFilter.OFF_PLAY -> base = base.filter { it.app.playStore?.present == false }
            AppFilter.RESTRICTED -> base = base.filter { it.app.hasRestrictedLicense() }
            null -> Unit
        }
        state.category?.let { category -> base.filter { it.app.categories.contains(category) } } ?: base
    }

    val listState = rememberLazyListState()
    // A faixa recolhe assim que a lista anda: em cima é a assinatura do catálogo, a
    // partir do primeiro scroll é só mais uma barra a competir com o conteúdo.
    val collapsed by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 48
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        CatalogueRibbon(
            counts = catalogueCounts(state),
            updates = state.updateCount,
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
                text = "O Android ainda não autorizou a Montra a instalar aplicações. " +
                    "É uma autorização por app, dada nas definições do sistema.",
                action = {
                    Button(onClick = onAuthorize) { Text("Autorizar instalação") }
                },
                modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
            )
        }

        FilterBar(
            state = state,
            onSort = onSort,
            onFilter = onFilter,
            onCategory = onCategory,
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.index.loading && filtered.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                filtered.isEmpty() -> EmptyState(
                    state = state,
                    onRefresh = onRefresh,
                    onClear = {
                        onCategory(null)
                        onFilter(null)
                    },
                )

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
                        // A procura vive dentro da lista: é um atalho, não a única
                        // porta (há um separador só para isso), e sai da frente assim
                        // que se começa a ler.
                        item(key = "search") {
                            SearchBar(
                                onClick = onOpenSearch,
                                placeholder = "Procurar por nome, etiqueta ou pacote",
                                modifier = Modifier.padding(horizontal = Space.lg),
                            )
                        }

                        val updates = filtered.filter { it.updateAvailable }
                        val installed = filtered.filter { it.isInstalled && !it.updateAvailable }
                        val rest = filtered.filter { !it.isInstalled && !it.updateAvailable }

                        appSections(
                            sections = buildList {
                                if (updates.isNotEmpty()) {
                                    add(AppSectionSpec("Atualizações disponíveis", updates, accent = true))
                                }
                                if (installed.isNotEmpty()) {
                                    add(AppSectionSpec("Instaladas", installed))
                                }
                                if (rest.isNotEmpty()) {
                                    add(
                                        AppSectionSpec(
                                            if (updates.isEmpty() && installed.isEmpty()) "Tudo" else "Descobrir",
                                            rest,
                                        ),
                                    )
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

/** "42 apps · 6 instaladas" — o que este catálogo tem, dito em números. */
private fun catalogueCounts(state: UiState): String = buildString {
    append(state.rows.size)
    append(if (state.rows.size == 1) " app" else " apps")
    if (state.installedCount > 0) {
        append(" · ")
        append(state.installedCount)
        append(if (state.installedCount == 1) " instalada" else " instaladas")
    }
}

@Composable
private fun EmptyState(state: UiState, onRefresh: () -> Unit, onClear: () -> Unit) {
    val offline = state.index.offline
    val failed = state.index.error != null
    // As duas razões para não haver lista nenhuma: um filtro, ou um catálogo que não
    // chegou. São ecrãs vazios com saídas diferentes.
    val filteredOut = state.rows.isNotEmpty() && (state.filter != null || state.category != null)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Space.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = when {
                filteredOut -> if (state.category != null) {
                    "Nada nesta categoria."
                } else {
                    "Nada corresponde a este filtro."
                }
                // Offline primeiro: é a razão mais provável e a mais fácil de resolver.
                offline -> "Sem ligação à internet"
                failed -> state.index.error.orEmpty()
                else -> "O catálogo está vazio."
            },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (offline) {
            Text(
                text = "A Montra precisa de internet para confirmar o catálogo. " +
                    "As apps que já instalaste continuam a funcionar.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (failed) {
            Text(
                text = "O último catálogo verificado continua a ser usado.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Space.lg))
        // Um ecrã vazio sem saída é um beco. Ou se tira o filtro, ou se tenta outra vez.
        if (filteredOut) {
            OutlinedButton(onClick = onClear) { Text("Limpar filtros") }
        } else if (failed || offline) {
            Button(onClick = onRefresh) { Text("Tentar de novo") }
        }
    }
}
